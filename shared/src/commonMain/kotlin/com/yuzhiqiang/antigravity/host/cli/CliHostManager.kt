package com.yuzhiqiang.antigravity.host.cli

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.io.File

/**
 * CLI 宿主集成管理器，对标 agy-byok 的 cli_host.rs。
 * 检测 agy CLI 工具是否安装，并管理 Studio 专属的单次启动配置。
 */
object CliHostManager {


    /**
     * 获取 CLI 配置文件路径。
     * agy CLI 使用 ~/.config/antigravity/cli-settings.json 存储配置。
     */
    fun getConfigFile(): File {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> File(userHome, ".config/antigravity/cli-settings.json")
            os.contains("win") -> {
                val appData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
                File(appData, "antigravity/cli-settings.json")
            }

            else -> File(userHome, ".config/antigravity/cli-settings.json")
        }
    }

    /**
     * 获取 CLI 工具候选路径列表。
     */
    fun getCandidateInstallations(customInstallation: String? = null): List<File> {
        val customBin = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)?.let {
            if (it.isDirectory) {
                if (File(it, "agy").isFile) File(it, "agy")
                else if (File(it, "agy.exe").isFile) File(it, "agy.exe")
                else if (File(it, "agy.cmd").isFile) File(it, "agy.cmd")
                else it
            } else it
        }
        val userHome = System.getProperty("user.home", "")
        val os = System.getProperty("os.name", "").lowercase()
        val candidates = if (os.contains("win")) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            buildList {
                customBin?.let(::add)
                findExecutableInPath("agy")?.let(::add)
                findExecutableInPath("agy.exe")?.let(::add)
                add(File(localAppData, "Programs/Antigravity/agy.exe"))
                add(File(localAppData, "Programs/agy/agy.exe"))
                add(File(localAppData, "agy/bin/agy.exe"))
                add(File(programFiles, "Antigravity/agy.exe"))
                add(File(userHome, ".cargo/bin/agy.exe"))
            }
        } else {
            buildList {
                customBin?.let(::add)
                findExecutableInPath("agy")?.let(::add)
                add(File("/usr/local/bin/agy"))
                add(File("/opt/homebrew/bin/agy"))
                add(File(userHome, ".local/bin/agy"))
                add(File(userHome, ".cargo/bin/agy"))
                add(File(userHome, "bin/agy"))
                add(File("/usr/bin/agy"))
            }
        }
        return candidates.filterNot { file ->
            val path = file.absolutePath.replace('\\', '/')
            path.contains("/Antigravity IDE", ignoreCase = true) ||
                    path.contains("/Antigravity-IDE", ignoreCase = true) ||
                    path.contains("/Antigravity Studio", ignoreCase = true)
        }.distinct()
    }

    private fun findExecutableInPath(command: String): File? {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            val proc = if (os.contains("win")) {
                ProcessBuilder("where", command).start()
            } else {
                ProcessBuilder("/usr/bin/which", command).start()
            }
            if (proc.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS) && proc.exitValue() == 0) {
                val output = proc.inputStream.bufferedReader().readText().lineSequence().firstOrNull()?.trim()
                if (!output.isNullOrBlank()) {
                    val file = File(output)
                    if (file.exists()) file else null
                } else null
            } else {
                proc.destroyForcibly()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 检测 CLI 工具是否已安装（跨平台支持 macOS/Linux 和 Windows）。
     */
    fun isInstalled(customInstallation: String? = null): Boolean {
        if (!customInstallation.isNullOrBlank()) {
            val file = File(customInstallation.trim())
            if (file.exists()) {
                if (file.isFile) return true
                if (file.isDirectory && (File(file, "agy").isFile || File(file, "agy.exe").isFile)) return true
            }
        }
        return getCandidateInstallations(customInstallation).any { it.exists() && it.isFile }
    }

    /**
     * 检测 CLI 的 Studio 专属启动配置是否匹配当前端口。
     */
    fun isActive(proxyPort: Int): Boolean {
        return HostOwnershipStore.inspectLaunchIntegration(
            HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort
        ).endpointMatches
    }

    fun detectVersion(customInstallation: String? = null): String? {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val customBin = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)?.let {
            if (it.isDirectory) {
                if (File(it, "agy").isFile) File(it, "agy")
                else if (File(it, "agy.exe").isFile) File(it, "agy.exe")
                else if (File(it, "agy.cmd").isFile) File(it, "agy.cmd")
                else null
            } else if (it.isFile) it else null
        }
        val commands = buildList {
            customBin?.let {
                if (isWindows && (it.name.endsWith(".cmd") || it.name.endsWith(".bat"))) {
                    add(listOf("cmd.exe", "/c", it.absolutePath, "--version"))
                } else {
                    add(listOf(it.absolutePath, "--version"))
                }
            }
            if (isWindows) {
                add(listOf("cmd.exe", "/c", "agy", "--version"))
                add(listOf("cmd.exe", "/c", "antigravity", "--version"))
            } else {
                add(listOf("agy", "--version"))
                add(listOf("antigravity", "--version"))
            }
        }
        for (cmd in commands) {
            val version = runCatching {
                val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val completed = proc.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (completed && proc.exitValue() == 0) {
                    val text = proc.inputStream.bufferedReader().use { it.readText() }
                    val line = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                    val match = Regex("(\\d+\\.\\d+\\.\\d+)").find(line)
                    match?.value ?: line.takeIf { it.isNotBlank() }
                } else {
                    proc.destroyForcibly()
                    null
                }
            }.getOrNull()
            if (!version.isNullOrBlank()) return version
        }
        return null
    }

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val version = if (installed) runCatching { detectVersion(customInstallation) }.getOrNull() else null
        val inspect = HostOwnershipStore.inspectLaunchIntegration(
            HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort
        )
        val configState = when (inspect.state) {
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_ENABLED
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.CONFLICT,
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
            else -> when {
                !inspect.endpointMatches -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NEEDS_UPDATE
                !isProxyRunning -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.SERVICE_STOPPED
                else -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.MATCHED
            }
        }
        val target = "http://127.0.0.1:$proxyPort"
        return com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            type = com.yuzhiqiang.antigravity.host.model.HostType.CLI,
            isInstalled = installed,
            isRunning = false,
            integrationState = inspect.state,
            configurationState = configState,
            configuredEndpoint = inspect.configuredEndpoint,
            targetEndpoint = target,
            configPath = getConfigFile().absolutePath,
            canEnable = installed,
            canDisable = inspect.canDisable,
            canLaunch = false,
            customPath = customInstallation,
            version = version,
            externalEndpoint = HostOwnershipStore.sharedEnvironmentEndpoint().getOrNull()
        )
    }

    /** 启用 CLI 的 Studio 专属启动配置，不修改用户环境或终端配置。 */
    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableLaunchIntegration(
            owner = HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort = proxyPort
        ).isSuccess
    }

    /** 停用 CLI 的 Studio 专属启动配置，不影响已运行的进程。 */
    fun disable(): Boolean {
        return HostOwnershipStore.disableLaunchIntegration(
            owner = HostOwnershipStore.EnvironmentOwner.CLI
        ).isSuccess
    }

    /** 重置仅清除 CLI 启动意图，不触碰 App 或外部环境变量。 */
    fun forceReset(): Boolean = disable()

    /** 生成单次启动命令，仅对由该命令启动的 CLI 进程注入代理地址。 */
    fun buildLaunchCommand(proxyPort: Int, customInstallation: String? = null): Result<String> = runCatching {
        require(proxyPort in 1..65535) { "代理端口必须在 1..65535 范围内" }
        check(HostOwnershipStore.configuredLaunchEndpoint(HostOwnershipStore.EnvironmentOwner.CLI).getOrThrow() != null) {
            "请先启用 CLI 的 Studio 专属启动配置"
        }
        val os = System.getProperty("os.name", "").lowercase()
        check(os.contains("mac") || os.contains("linux")) { "复制 CLI 启动命令目前仅支持 macOS / Linux" }
        val candidates = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            listOf(File(path).let { if (it.isDirectory) File(it, "agy") else it })
        } ?: getCandidateInstallations()
        val executable = candidates.firstOrNull { it.isAbsolute && it.isFile && it.canExecute() }
            ?: error("未找到可执行的 agy 绝对路径，请检查 CLI 安装路径")
        val quotedExecutable = "'${executable.absolutePath.replace("'", "'\"'\"'")}'"
        "env -u ANTIGRAVITY_LS_ADDRESS -u ANTIGRAVITY_CSRF_TOKEN -u ANTIGRAVITY_AGENT " +
            "-u ANTIGRAVITY_AGENTAPI_EXE CLOUD_CODE_URL='http://127.0.0.1:$proxyPort' $quotedExecutable"
    }
}
