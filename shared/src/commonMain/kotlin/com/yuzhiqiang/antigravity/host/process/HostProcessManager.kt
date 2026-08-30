package com.yuzhiqiang.antigravity.host.process

import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 跨平台宿主进程管理工具。
 *
 * 进程终止只作用于已匹配的宿主进程、其子进程，以及能由宿主专属路径证明归属的进程。
 */
object HostProcessManager {

    private const val GRACEFUL_EXIT_TIMEOUT_MILLIS = 8_000L
    private const val FORCE_EXIT_TIMEOUT_MILLIS = 1_500L
    private const val POLL_INTERVAL_MILLIS = 200L
    private const val COMMAND_TIMEOUT_MILLIS = 3_000L

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac")

    /**
     * 检测指定特征的进程是否在运行。
     */
    fun isProcessRunning(
        matchPatterns: List<String>,
        excludePatterns: List<String> = emptyList()
    ): Boolean {
        return findProcessPids(matchPatterns, excludePatterns).isNotEmpty()
    }

    /**
     * 查找符合匹配特征的所有进程 PID。
     */
    fun findProcessPids(
        matchPatterns: List<String>,
        excludePatterns: List<String> = emptyList()
    ): List<Long> {
        if (matchPatterns.isEmpty()) return emptyList()
        return (readProcessSnapshots() ?: return emptyList())
            .filter { snapshot -> snapshot.matches(matchPatterns, excludePatterns) }
            .map(ProcessSnapshot::pid)
    }

    /**
     * 优雅终止宿主及能证明归属的子进程。
     *
     * 默认超时后返回 `false`，不会自动强杀。只有调用方明确传入 [force] 时，才会对本次
     * 已确认归属的 PID 集合执行强制终止，不会按进程名扩大终止范围。
     */
    suspend fun terminateApplication(
        bundleId: String?,
        matchPatterns: List<String>,
        excludePatterns: List<String> = emptyList(),
        languageServerPatterns: List<String> = emptyList(),
        label: String,
        force: Boolean = false
    ): Boolean {
        val snapshots = readProcessSnapshots() ?: return false
        val hostPids = snapshots
            .filter { snapshot -> snapshot.matches(matchPatterns, excludePatterns) }
            .mapTo(linkedSetOf(), ProcessSnapshot::pid)
        val descendantPids = collectDescendantPids(hostPids, snapshots)
        val scopedServerPatterns = languageServerPatterns.filter(::isPathScopedPattern)
        val pathOwnedServerPids = snapshots
            .filter { snapshot -> snapshot.matches(scopedServerPatterns, excludePatterns) }
            .mapTo(linkedSetOf(), ProcessSnapshot::pid)
        val ownedPids = linkedSetOf<Long>().apply {
            addAll(hostPids)
            addAll(descendantPids)
            addAll(pathOwnedServerPids)
        }
        if (ownedPids.isEmpty()) return true
        val rootPids = ownedPids.filterTo(linkedSetOf()) { pid ->
            snapshots.firstOrNull { it.pid == pid }?.parentPid !in ownedPids
        }

        val gracefulRequestSent = requestGracefulExit(bundleId, rootPids)
        if (gracefulRequestSent && waitUntilStopped(ownedPids, GRACEFUL_EXIT_TIMEOUT_MILLIS)) {
            return areProcessesStopped(matchPatterns + scopedServerPatterns, excludePatterns)
        }
        if (!force) return false

        // 优雅退出请求可能因宿主 GUI 无响应或权限问题失败；force=true 时仍只对
        // 本次快照中已确认归属的 PID 执行强制终止，不能提前返回而留下文件占用。
        forceStopOwnedProcesses(ownedPids, snapshots)
        val stopped = waitUntilStopped(ownedPids, FORCE_EXIT_TIMEOUT_MILLIS)
        return stopped && areProcessesStopped(matchPatterns + scopedServerPatterns, excludePatterns)
    }

    private fun areProcessesStopped(
        matchPatterns: List<String>,
        excludePatterns: List<String>
    ): Boolean {
        val snapshots = readProcessSnapshots() ?: return false
        return snapshots.none { snapshot -> snapshot.matches(matchPatterns, excludePatterns) }
    }

    private fun requestGracefulExit(bundleId: String?, rootPids: Set<Long>): Boolean {
        if (rootPids.isEmpty()) return true
        return when {
            isMac && !bundleId.isNullOrBlank() -> runCommand(
                "/usr/bin/osascript",
                "-e",
                "tell application id \"$bundleId\" to quit"
            )

            isWindows -> rootPids.all { pid ->
                runCommand("taskkill", "/PID", pid.toString())
            }

            else -> rootPids.all { pid ->
                runCommand("kill", "-15", pid.toString())
            }
        }
    }

    private fun forceStopOwnedProcesses(ownedPids: Set<Long>, snapshots: List<ProcessSnapshot>) {
        val depthByPid = buildProcessDepths(ownedPids, snapshots)
        ownedPids.sortedByDescending { depthByPid[it] ?: 0 }.forEach { pid ->
            if (!isPidAlive(pid)) return@forEach
            if (isWindows) {
                runCommand("taskkill", "/F", "/PID", pid.toString())
            } else {
                runCommand("kill", "-9", pid.toString())
            }
        }
    }

    private suspend fun waitUntilStopped(pids: Set<Long>, timeoutMillis: Long): Boolean {
        var waitedMillis = 0L
        while (waitedMillis < timeoutMillis) {
            if (pids.none(::isPidAlive)) return true
            delay(POLL_INTERVAL_MILLIS)
            waitedMillis += POLL_INTERVAL_MILLIS
        }
        return pids.none(::isPidAlive)
    }

    private fun isPidAlive(pid: Long): Boolean {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
    }

    private fun collectDescendantPids(
        parentPids: Set<Long>,
        snapshots: List<ProcessSnapshot>
    ): Set<Long> {
        val descendants = linkedSetOf<Long>()
        var frontier = parentPids.toSet()
        while (frontier.isNotEmpty()) {
            val children = snapshots
                .filter { snapshot -> snapshot.parentPid in frontier }
                .mapTo(linkedSetOf(), ProcessSnapshot::pid)
                .filterNotTo(linkedSetOf()) { it in parentPids || it in descendants }
            descendants.addAll(children)
            frontier = children
        }
        return descendants
    }

    private fun buildProcessDepths(
        ownedPids: Set<Long>,
        snapshots: List<ProcessSnapshot>
    ): Map<Long, Int> {
        val parentByPid = snapshots.associate { it.pid to it.parentPid }
        return ownedPids.associateWith { pid ->
            var depth = 0
            var parentPid = parentByPid[pid]
            val visited = mutableSetOf<Long>()
            while (parentPid != null && parentPid in ownedPids && visited.add(parentPid)) {
                depth++
                parentPid = parentByPid[parentPid]
            }
            depth
        }
    }

    private fun isPathScopedPattern(pattern: String): Boolean {
        return pattern.contains('/') || pattern.contains('\\')
    }

    private fun readProcessSnapshots(): List<ProcessSnapshot>? {
        val studioPid = ProcessHandle.current().pid()
        return try {
            ProcessHandle.allProcesses().use { processes ->
                processes.map { handle ->
                    val info = handle.info()
                    val command = info.command().orElse("")
                    val arguments = info.arguments().orElse(emptyArray()).joinToString(" ")
                    val commandLine = info.commandLine().orElse("").ifBlank {
                        listOf(command, arguments).filter(String::isNotBlank).joinToString(" ")
                    }
                    ProcessSnapshot(
                        pid = handle.pid(),
                        parentPid = handle.parent().map(ProcessHandle::pid).orElse(null),
                        executablePath = command,
                        commandLine = commandLine
                    )
                }.filter { snapshot ->
                    snapshot.pid != studioPid &&
                        (snapshot.commandLine.isNotBlank() || snapshot.executablePath.isNotBlank()) &&
                        !snapshot.isStudioProcess()
                }.toList()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 跨平台启动应用，并支持自定义安装路径与环境隔离。
     */
    fun launch(
        installationPath: String?,
        defaultMacApp: String,
        defaultWinExe: String,
        environment: Map<String, String>? = null
    ): Boolean {
        return when {
            isWindows -> launchWindows(installationPath, defaultMacApp, defaultWinExe, environment)
            isMac -> launchMac(installationPath, defaultMacApp, environment)
            else -> launchLinux(installationPath, defaultMacApp, defaultWinExe, environment)
        }
    }

    private fun launchWindows(
        installationPath: String?,
        defaultAppName: String,
        defaultExecutableName: String,
        environment: Map<String, String>?
    ): Boolean {
        val customPath = installationPath?.trim()?.takeIf(String::isNotEmpty)
        val target = if (customPath != null) {
            resolveExecutable(File(customPath), defaultExecutableName) ?: return false
        } else {
            val userHome = System.getProperty("user.home").orEmpty()
            val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
            resolveExecutable(
                File(localAppData, "Programs/$defaultAppName"),
                defaultExecutableName
            ) ?: resolveExecutable(
                File(localAppData, "Programs/${defaultAppName.lowercase()}"),
                defaultExecutableName
            ) ?: findExecutableOnPath(defaultExecutableName) ?: return false
        }
        return runLaunchCommand(listOf(target.absolutePath), environment, waitForExit = false, workingDir = target.parentFile)
    }

    private fun launchMac(
        installationPath: String?,
        defaultAppName: String,
        environment: Map<String, String>?
    ): Boolean {
        val customPath = installationPath?.trim()?.takeIf(String::isNotEmpty)
        if (customPath != null && File(customPath).isFile) {
            val file = File(customPath)
            return runLaunchCommand(listOf(file.absolutePath), environment, waitForExit = false, workingDir = file.parentFile)
        }
        val command = if (customPath != null) {
            val target = File(customPath)
            if (!target.isDirectory) return false
            listOf("/usr/bin/open", "-n", target.absolutePath)
        } else {
            val systemApp = File("/Applications/$defaultAppName.app")
            val userApp = File(System.getProperty("user.home"), "Applications/$defaultAppName.app")
            val target = listOf(systemApp, userApp).firstOrNull(File::isDirectory)
            if (target != null) {
                listOf("/usr/bin/open", "-n", target.absolutePath)
            } else {
                listOf("/usr/bin/open", "-n", "-a", defaultAppName)
            }
        }
        return runLaunchCommand(command, environment, waitForExit = true)
    }

    private fun launchLinux(
        installationPath: String?,
        defaultAppName: String,
        defaultWinExe: String,
        environment: Map<String, String>?
    ): Boolean {
        val executableName = defaultWinExe.removeSuffix(".exe")
        val customPath = installationPath?.trim()?.takeIf(String::isNotEmpty)
        val command = if (customPath != null) {
            val target = resolveExecutable(File(customPath), executableName)
                ?: resolveExecutable(File(customPath), defaultAppName)
                ?: return false
            listOf(target.absolutePath)
        } else {
            listOf(defaultAppName.lowercase().replace(' ', '-'))
        }
        return runLaunchCommand(command, environment, waitForExit = false)
    }

    private fun resolveExecutable(path: File, executableName: String): File? {
        return when {
            path.isFile -> path
            path.isDirectory -> File(path, executableName).takeIf(File::isFile)
                ?: File(path, "_/$executableName").takeIf(File::isFile)
            else -> null
        }
    }

    private fun findExecutableOnPath(executableName: String): File? {
        val pathValue = System.getenv("PATH").orEmpty()
        return pathValue.split(File.pathSeparatorChar)
            .asSequence()
            .map { directory -> File(directory, executableName) }
            .firstOrNull(File::isFile)
    }

    private fun runLaunchCommand(
        command: List<String>,
        environment: Map<String, String>?,
        waitForExit: Boolean,
        workingDir: File? = null
    ): Boolean {
        return try {
            val processBuilder = ProcessBuilder(command)
            if (workingDir != null && workingDir.isDirectory) {
                processBuilder.directory(workingDir)
            }
            processBuilder.environment().remove("CLOUD_CODE_URL")
            environment?.forEach { (key, value) -> processBuilder.environment()[key] = value }
            val process = processBuilder.start()
            if (waitForExit) {
                val exited = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                if (!exited) {
                    process.destroy()
                    false
                } else {
                    process.exitValue() == 0
                }
            } else {
                val exited = process.waitFor(300, TimeUnit.MILLISECONDS)
                !exited || process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun runCommand(vararg command: String): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder(*command).start()
            val completed = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            process?.destroyForcibly()
            false
        }
    }

    private data class ProcessSnapshot(
        val pid: Long,
        val parentPid: Long?,
        val executablePath: String,
        val commandLine: String
    ) {
        fun matches(matchPatterns: List<String>, excludePatterns: List<String>): Boolean {
            if (matchPatterns.isEmpty()) return false
            val normalizedCommand = commandLine.replace('\\', '/').lowercase()
            val normalizedExe = executablePath.replace('\\', '/').lowercase()
            val exeName = File(executablePath).name.lowercase()

            val excluded = excludePatterns.any { pattern ->
                val normPat = pattern.replace('\\', '/').lowercase()
                normalizedCommand.contains(normPat) || normalizedExe.contains(normPat)
            }
            if (excluded) return false

            return matchPatterns.any { pattern ->
                val normalizedPattern = pattern.replace('\\', '/').lowercase()
                if (normalizedPattern.endsWith(".exe") && !normalizedPattern.contains('/')) {
                    exeName.equals(normalizedPattern, ignoreCase = true) ||
                            File(commandLine.substringBefore(' ').trim('"', '\'')).name.equals(normalizedPattern, ignoreCase = true) ||
                            normalizedCommand.contains("/$normalizedPattern") ||
                            normalizedCommand.startsWith(normalizedPattern) ||
                            normalizedCommand.contains(" $normalizedPattern")
                } else {
                    normalizedCommand.contains(normalizedPattern) || normalizedExe.contains(normalizedPattern)
                }
            }
        }

        fun isStudioProcess(): Boolean {
            return commandLine.contains("antigravity-studio", ignoreCase = true) ||
                commandLine.contains("Antigravity Studio", ignoreCase = true) ||
                commandLine.contains("AntigravityStudio", ignoreCase = true) ||
                executablePath.contains("antigravity-studio", ignoreCase = true) ||
                executablePath.contains("Antigravity Studio", ignoreCase = true) ||
                executablePath.contains("AntigravityStudio", ignoreCase = true)
        }
    }
}
