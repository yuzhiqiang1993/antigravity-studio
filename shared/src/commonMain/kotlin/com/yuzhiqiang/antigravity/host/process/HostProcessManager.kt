package com.yuzhiqiang.antigravity.host.process

import kotlinx.coroutines.delay
import java.io.File

/**
 * 跨平台宿主进程管理工具，对标 agy-byok 的 host/process 实现。
 * 提供精准的进程检测、优雅退出、超时强制终止与干净启动能力。
 */
object HostProcessManager {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
    private val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

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
     * 查找符合匹配特征的所有进程 PID（排除 studio 自身与 grep 过滤进程，且支持排除特定模式）。
     */
    fun findProcessPids(
        matchPatterns: List<String>,
        excludePatterns: List<String> = emptyList()
    ): List<Long> {
        if (matchPatterns.isEmpty()) return emptyList()
        return try {
            if (isWindows) {
                val process = ProcessBuilder("tasklist", "/FO", "CSV", "/NH").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val pids = mutableListOf<Long>()
                output.lineSequence().forEach { line ->
                    val parts = line.split("\",\"").map { it.replace("\"", "").trim() }
                    if (parts.size >= 2) {
                        val imageName = parts[0]
                        val pidStr = parts[1]
                        val matches = matchPatterns.any { pattern ->
                            if (pattern.endsWith(".exe", ignoreCase = true)) {
                                imageName.equals(pattern, ignoreCase = true)
                            } else {
                                imageName.contains(pattern, ignoreCase = true)
                            }
                        }
                        val excluded = excludePatterns.any { exclude ->
                            imageName.contains(exclude, ignoreCase = true)
                        }
                        if (matches && !excluded) {
                            pidStr.toLongOrNull()?.let { pids.add(it) }
                        }
                    }
                }
                pids
            } else {
                val process = ProcessBuilder("ps", "-axo", "pid,command").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val pids = mutableListOf<Long>()
                val studioPid = ProcessHandle.current().pid()
                output.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val spaceIdx = trimmed.indexOf(' ')
                    if (spaceIdx > 0) {
                        val pidStr = trimmed.substring(0, spaceIdx).trim()
                        val cmd = trimmed.substring(spaceIdx).trim()
                        val pid = pidStr.toLongOrNull() ?: return@forEach
                        if (pid == studioPid) return@forEach
                        if (cmd.contains("grep", ignoreCase = true)) return@forEach
                        if (cmd.contains("antigravity-studio", ignoreCase = true) ||
                            cmd.contains("Antigravity Studio", ignoreCase = true) ||
                            cmd.contains("AntigravityStudio", ignoreCase = true)) {
                            return@forEach
                        }

                        val excluded = excludePatterns.any { exclude ->
                            cmd.contains(exclude, ignoreCase = true)
                        }
                        if (excluded) return@forEach

                        val matches = matchPatterns.any { pattern ->
                            cmd.contains(pattern, ignoreCase = true)
                        }
                        if (matches) {
                            pids.add(pid)
                        }
                    }
                }
                pids
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 优雅终止宿主及其子进程树，超时则强制 SIGKILL。
     */
    suspend fun terminateApplication(
        bundleId: String?,
        matchPatterns: List<String>,
        excludePatterns: List<String> = emptyList(),
        languageServerPatterns: List<String> = emptyList(),
        label: String
    ): Boolean {
        try {
            if (isWindows) {
                matchPatterns.forEach { pattern ->
                    runCatching {
                        ProcessBuilder("taskkill", "/IM", pattern).start().waitFor()
                    }
                }
                delay(500)
                if (isProcessRunning(matchPatterns, excludePatterns)) {
                    matchPatterns.forEach { pattern ->
                        runCatching {
                            ProcessBuilder("taskkill", "/F", "/T", "/IM", pattern).start().waitFor()
                        }
                    }
                }
                if (languageServerPatterns.isNotEmpty()) {
                    stopLanguageServer(languageServerPatterns, excludePatterns)
                }
                return !isProcessRunning(matchPatterns, excludePatterns)
            }

            // macOS 处理流程
            if (bundleId != null) {
                runCatching {
                    ProcessBuilder("/usr/bin/osascript", "-e", "tell application id \"" + bundleId + "\" to quit")
                        .start()
                        .waitFor()
                }
            }

            // 轮询等待优雅退出，最多 1.5 秒
            var remainingPids = findProcessPids(matchPatterns, excludePatterns)
            var waited = 0
            while (remainingPids.isNotEmpty() && waited < 1500) {
                delay(200)
                waited += 200
                remainingPids = findProcessPids(matchPatterns, excludePatterns)
            }

            // 若仍有残留 PID，发送 SIGTERM (kill -15)
            if (remainingPids.isNotEmpty()) {
                remainingPids.forEach { pid ->
                    runCatching { ProcessBuilder("kill", "-15", pid.toString()).start().waitFor() }
                }
                delay(500)
                remainingPids = findProcessPids(matchPatterns, excludePatterns)
            }

            // 若仍有残留 PID，强杀 SIGKILL (kill -9)
            if (remainingPids.isNotEmpty()) {
                remainingPids.forEach { pid ->
                    runCatching { ProcessBuilder("kill", "-9", pid.toString()).start().waitFor() }
                }
                delay(300)
            }

            // 仅按宿主专属模式清理孤儿 language_server，严防误伤其他宿主
            if (languageServerPatterns.isNotEmpty()) {
                stopLanguageServer(languageServerPatterns, excludePatterns)
            }
            return findProcessPids(matchPatterns, excludePatterns).isEmpty()
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * 深度清理指定宿主专属的 language_server 进程。
     */
    fun stopLanguageServer(
        patterns: List<String>,
        excludePatterns: List<String> = emptyList()
    ) {
        if (patterns.isEmpty()) return
        try {
            if (isWindows) {
                val serverPids = findProcessPids(patterns, excludePatterns)
                serverPids.forEach { pid ->
                    runCatching { ProcessBuilder("taskkill", "/F", "/PID", pid.toString()).start().waitFor() }
                }
            } else {
                val serverPids = findProcessPids(patterns, excludePatterns)
                serverPids.forEach { pid ->
                    runCatching { ProcessBuilder("kill", "-9", pid.toString()).start().waitFor() }
                }
            }
        } catch (_: Exception) {
            // 忽略清理异常
        }
    }

    /**
     * 跨平台启动应用，并支持环境隔离（防止旧环境或污染变量继承）。
     */
    fun launch(
        installationPath: String?,
        defaultMacApp: String,
        defaultWinExe: String,
        environment: Map<String, String>? = null
    ): Boolean {
        return try {
            if (isWindows) {
                val userHome = System.getProperty("user.home") ?: ""
                val localAppData = System.getenv("LOCALAPPDATA") ?: (userHome + "/AppData/Local")
                val customTarget = installationPath?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { File(it).let { root -> if (root.isFile) root else File(root, defaultWinExe) } }
                val target = customTarget?.takeIf(File::isFile)
                    ?: File(localAppData, "Programs/" + defaultMacApp + "/" + defaultWinExe)
                val pb = if (target.exists()) {
                    ProcessBuilder(target.absolutePath)
                } else {
                    ProcessBuilder("cmd.exe", "/c", "start", "", defaultWinExe)
                }
                pb.environment().remove("CLOUD_CODE_URL")
                environment?.forEach { (k, v) -> pb.environment()[k] = v }
                pb.start()
                true
            } else {
                val app = installationPath?.trim()?.takeIf { it.isNotEmpty() }
                val targetApp = if (app != null && File(app).isDirectory) app else "/Applications/" + defaultMacApp + ".app"
                val command = mutableListOf("/usr/bin/env", "-u", "CLOUD_CODE_URL", "/usr/bin/open", "-n")
                if (File(targetApp).isDirectory) {
                    command.addAll(listOf("-a", targetApp))
                } else {
                    command.addAll(listOf("-a", defaultMacApp))
                }
                environment?.forEach { (k, v) ->
                    command.add("--env")
                    command.add(k + "=" + v)
                }
                val pb = ProcessBuilder(command)
                pb.environment().remove("CLOUD_CODE_URL")
                pb.start()
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
