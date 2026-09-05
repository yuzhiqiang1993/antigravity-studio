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
            configPath = defaultLauncherFile().absolutePath,
            canEnable = installed,
            canDisable = inspect.canDisable,
            canLaunch = false,
            customPath = customInstallation,
            version = version,
            externalEndpoint = HostOwnershipStore.sharedEnvironmentEndpoint().getOrNull()
        )
    }

    internal var launcherDirectoryOverride: File? = null

    /**
     * 获取推荐的 agy-studio 启动器脚本文件。
     */
    fun defaultLauncherFile(): File {
        launcherDirectoryOverride?.let { dir ->
            val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
            return File(dir, if (isWindows) "agy-studio.cmd" else "agy-studio")
        }
        val userHome = System.getProperty("user.home", "")
        val os = System.getProperty("os.name", "").lowercase()
        return if (os.contains("win")) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
            val preferredDir = File(localAppData, "agy/bin")
            if (preferredDir.exists() && preferredDir.isDirectory) {
                File(preferredDir, "agy-studio.cmd")
            } else {
                File(userHome, ".local/bin/agy-studio.cmd")
            }
        } else {
            File(userHome, ".local/bin/agy-studio")
        }
    }

    /**
     * 检查启动器脚本是否已安装且可执行。
     */
    fun isLauncherInstalled(targetFile: File? = null): Boolean {
        val file = targetFile ?: defaultLauncherFile()
        if (!file.exists() || !file.isFile) return false
        val os = System.getProperty("os.name", "").lowercase()
        return if (os.contains("win")) true else file.canExecute()
    }

    /**
     * 生成 POSIX (macOS / Linux) 启动器脚本内容。
     */
    fun generatePosixLauncherScript(realAgyPath: String, receiptFile: File): String {
        return """
            |#!/usr/bin/env bash
            |# Antigravity Studio CLI Launcher
            |set -e
            |
            |RECEIPT_FILE="${'$'}{ANTIGRAVITY_STUDIO_RECEIPT_OVERRIDE:-${receiptFile.absolutePath}}"
            |REAL_AGY="${'$'}{ANTIGRAVITY_REAL_AGY_PATH:-$realAgyPath}"
            |
            |if [ ! -f "${'$'}RECEIPT_FILE" ]; then
            |  echo "[Studio] CLI 代理未启用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。" >&2
            |  exit 1
            |fi
            |
            |ENDPOINT=${'$'}(grep -o '"cliEndpoint": *"[^"]*"' "${'$'}RECEIPT_FILE" 2>/dev/null | head -n 1 | cut -d'"' -f4)
            |
            |if [ -z "${'$'}ENDPOINT" ]; then
            |  echo "[Studio] CLI 代理已停用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。" >&2
            |  exit 1
            |fi
            |
            |if [ ! -x "${'$'}REAL_AGY" ]; then
            |  FALLBACK_AGY=${'$'}(command -v agy 2>/dev/null || true)
            |  if [ -n "${'$'}FALLBACK_AGY" ] && [ "${'$'}FALLBACK_AGY" != "${'$'}0" ] && [ -x "${'$'}FALLBACK_AGY" ]; then
            |    REAL_AGY="${'$'}FALLBACK_AGY"
            |  else
            |    echo "[Studio] 未找到原生 agy 可执行文件，请检查 CLI 安装路径。" >&2
            |    exit 1
            |  fi
            |fi
            |
            |exec env \
            |  -u ANTIGRAVITY_LS_ADDRESS \
            |  -u ANTIGRAVITY_CSRF_TOKEN \
            |  -u ANTIGRAVITY_AGENT \
            |  -u ANTIGRAVITY_AGENTAPI_EXE \
            |  CLOUD_CODE_URL="${'$'}ENDPOINT" \
            |  "${'$'}REAL_AGY" "${'$'}@"
            |""".trimMargin()
    }

    /**
     * 生成 Windows (CMD) 启动器脚本内容。
     */
    fun generateWindowsLauncherScript(realAgyPath: String, receiptFile: File): String {
        return """
            |@echo off
            |rem Antigravity Studio CLI Launcher (CMD)
            |setlocal enabledelayedexpansion
            |
            |set "RECEIPT_FILE=${receiptFile.absolutePath}"
            |if defined ANTIGRAVITY_STUDIO_RECEIPT_OVERRIDE set "RECEIPT_FILE=%ANTIGRAVITY_STUDIO_RECEIPT_OVERRIDE%"
            |
            |set "REAL_AGY=$realAgyPath"
            |if defined ANTIGRAVITY_REAL_AGY_PATH set "REAL_AGY=%ANTIGRAVITY_REAL_AGY_PATH%"
            |
            |if not exist "!RECEIPT_FILE!" (
            |  echo [Studio] CLI 代理未启用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。 1>&2
            |  exit /b 1
            |)
            |
            |set "ENDPOINT="
            |for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "(Get-Content -Raw -LiteralPath '!RECEIPT_FILE!' | ConvertFrom-Json).cliEndpoint" 2^>nul`) do set "ENDPOINT=%%i"
            |
            |if not defined ENDPOINT (
            |  echo [Studio] CLI 代理已停用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。 1>&2
            |  exit /b 1
            |)
            |
            |if not exist "!REAL_AGY!" (
            |  echo [Studio] 未找到原生 agy 可执行文件，请检查 CLI 安装路径。 1>&2
            |  exit /b 1
            |)
            |
            |set "ANTIGRAVITY_LS_ADDRESS="
            |set "ANTIGRAVITY_CSRF_TOKEN="
            |set "ANTIGRAVITY_AGENT="
            |set "ANTIGRAVITY_AGENTAPI_EXE="
            |set "CLOUD_CODE_URL=!ENDPOINT!"
            |call "!REAL_AGY!" %*
            |set "EXIT_CODE=%ERRORLEVEL%"
            |endlocal & exit /b %EXIT_CODE%
            |""".trimMargin()
    }

    /**
     * 生成 Windows (PowerShell) 启动器脚本内容。
     */
    fun generateWindowsPowerShellScript(realAgyPath: String, receiptFile: File): String {
        return """
            |# Antigravity Studio CLI Launcher (PowerShell)
            |[CmdletBinding()]
            |param(
            |    [Parameter(ValueFromRemainingArguments = ${'$'}true)]
            |    [string[]]${'$'}ScriptArgs
            |)
            |
            |${'$'}receiptFile = if (${'$'}env:ANTIGRAVITY_STUDIO_RECEIPT_OVERRIDE) { ${'$'}env:ANTIGRAVITY_STUDIO_RECEIPT_OVERRIDE } else { '${receiptFile.absolutePath.replace("'", "''")}' }
            |${'$'}realAgy = if (${'$'}env:ANTIGRAVITY_REAL_AGY_PATH) { ${'$'}env:ANTIGRAVITY_REAL_AGY_PATH } else { '${realAgyPath.replace("'", "''")}' }
            |
            |if (-not (Test-Path -LiteralPath ${'$'}receiptFile)) {
            |    [Console]::Error.WriteLine("[Studio] CLI 代理未启用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。")
            |    exit 1
            |}
            |
            |try {
            |    ${'$'}receipt = Get-Content -LiteralPath ${'$'}receiptFile -Raw | ConvertFrom-Json
            |    ${'$'}endpoint = ${'$'}receipt.cliEndpoint
            |} catch {
            |    ${'$'}endpoint = ${'$'}null
            |}
            |
            |if ([string]::IsNullOrWhiteSpace(${'$'}endpoint)) {
            |    [Console]::Error.WriteLine("[Studio] CLI 代理已停用。如需官方直连，请直接运行 agy；如需使用 Studio 代理，请在 Studio 中启用 CLI 接入。")
            |    exit 1
            |}
            |
            |if (-not (Test-Path -LiteralPath ${'$'}realAgy)) {
            |    [Console]::Error.WriteLine("[Studio] 未找到原生 agy 可执行文件，请检查 CLI 安装路径。")
            |    exit 1
            |}
            |
            |${'$'}origEndpoint = ${'$'}env:CLOUD_CODE_URL
            |${'$'}origLs = ${'$'}env:ANTIGRAVITY_LS_ADDRESS
            |${'$'}origCsrf = ${'$'}env:ANTIGRAVITY_CSRF_TOKEN
            |${'$'}origAgent = ${'$'}env:ANTIGRAVITY_AGENT
            |${'$'}origAgentExe = ${'$'}env:ANTIGRAVITY_AGENTAPI_EXE
            |
            |try {
            |    ${'$'}env:CLOUD_CODE_URL = ${'$'}endpoint
            |    Remove-Item Env:ANTIGRAVITY_LS_ADDRESS -ErrorAction SilentlyContinue
            |    Remove-Item Env:ANTIGRAVITY_CSRF_TOKEN -ErrorAction SilentlyContinue
            |    Remove-Item Env:ANTIGRAVITY_AGENT -ErrorAction SilentlyContinue
            |    Remove-Item Env:ANTIGRAVITY_AGENTAPI_EXE -ErrorAction SilentlyContinue
            |
            |    & ${'$'}realAgy @ScriptArgs
            |    exit ${'$'}LASTEXITCODE
            |} finally {
            |    if (${'$'}origEndpoint -ne ${'$'}null) { ${'$'}env:CLOUD_CODE_URL = ${'$'}origEndpoint } else { Remove-Item Env:CLOUD_CODE_URL -ErrorAction SilentlyContinue }
            |    if (${'$'}origLs -ne ${'$'}null) { ${'$'}env:ANTIGRAVITY_LS_ADDRESS = ${'$'}origLs } else { Remove-Item Env:ANTIGRAVITY_LS_ADDRESS -ErrorAction SilentlyContinue }
            |    if (${'$'}origCsrf -ne ${'$'}null) { ${'$'}env:ANTIGRAVITY_CSRF_TOKEN = ${'$'}origCsrf } else { Remove-Item Env:ANTIGRAVITY_CSRF_TOKEN -ErrorAction SilentlyContinue }
            |    if (${'$'}origAgent -ne ${'$'}null) { ${'$'}env:ANTIGRAVITY_AGENT = ${'$'}origAgent } else { Remove-Item Env:ANTIGRAVITY_AGENT -ErrorAction SilentlyContinue }
            |    if (${'$'}origAgentExe -ne ${'$'}null) { ${'$'}env:ANTIGRAVITY_AGENTAPI_EXE = ${'$'}origAgentExe } else { Remove-Item Env:ANTIGRAVITY_AGENTAPI_EXE -ErrorAction SilentlyContinue }
            |}
            |""".trimMargin()
    }

    /**
     * 安装或更新 agy-studio 启动器脚本。
     */
    fun installLauncher(
        customInstallation: String? = null,
        targetFile: File? = null
    ): Result<File> = runCatching {
        val candidates = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            listOf(File(path).let { if (it.isDirectory) File(it, "agy") else it })
        } ?: getCandidateInstallations()
        val executable = candidates.firstOrNull { it.isAbsolute && it.isFile && it.canExecute() }
            ?: error("未找到可执行的 agy 绝对路径，请检查 CLI 安装路径")

        val destination = targetFile ?: defaultLauncherFile()
        destination.parentFile?.let { parent ->
            if (!parent.exists()) {
                check(parent.mkdirs()) { "无法创建启动器目录: ${parent.absolutePath}" }
            }
        }

        val os = System.getProperty("os.name", "").lowercase()
        val receiptFile = HostOwnershipStore.launchReceiptFile()

        if (os.contains("win")) {
            val cmdScript = generateWindowsLauncherScript(executable.absolutePath, receiptFile)
            destination.writeText(cmdScript, Charsets.UTF_8)
            // 在 Windows 环境下同时提供原生的 PowerShell 启动器
            val ps1File = File(destination.parentFile, "agy-studio.ps1")
            val ps1Script = generateWindowsPowerShellScript(executable.absolutePath, receiptFile)
            ps1File.writeText(ps1Script, Charsets.UTF_8)
        } else {
            val posixScript = generatePosixLauncherScript(executable.absolutePath, receiptFile)
            destination.writeText(posixScript, Charsets.UTF_8)
            destination.setExecutable(true, false)
        }
        destination
    }

    /**
     * 卸载已安装的 agy-studio 启动器。
     */
    fun uninstallLauncher(targetFile: File? = null): Result<Unit> = runCatching {
        val destination = targetFile ?: defaultLauncherFile()
        if (destination.exists()) {
            check(destination.delete()) { "无法删除启动器文件: ${destination.absolutePath}" }
        }
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) {
            val ps1File = File(destination.parentFile, "agy-studio.ps1")
            if (ps1File.exists()) {
                ps1File.delete()
            }
        }
    }

    /** 启用 CLI 的 Studio 专属启动配置，并同步安装启动器。 */
    fun enable(proxyPort: Int, customInstallation: String? = null): Boolean {
        val success = HostOwnershipStore.enableLaunchIntegration(
            owner = HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort = proxyPort
        ).isSuccess
        if (success) {
            runCatching { installLauncher(customInstallation) }
        }
        return success
    }

    /** 停用 CLI 的 Studio 专属启动配置，不影响已运行的进程。 */
    fun disable(): Boolean {
        return HostOwnershipStore.disableLaunchIntegration(
            owner = HostOwnershipStore.EnvironmentOwner.CLI
        ).isSuccess
    }

    /** 重置仅清除 CLI 启动意图，不触碰 App 或外部环境变量。 */
    fun forceReset(): Boolean = disable()

    /**
     * 生成启动命令。
     *
     * 默认优先确保并返回简洁的 `agy-studio` 启动器命令；
     * 若环境受限无法安装启动器或显式要求，则回退生成单次完整环境变量命令。
     */
    fun buildLaunchCommand(
        proxyPort: Int,
        customInstallation: String? = null,
        preferShortLauncher: Boolean = true
    ): Result<String> = runCatching {
        require(proxyPort in 1..65535) { "代理端口必须在 1..65535 范围内" }
        check(HostOwnershipStore.configuredLaunchEndpoint(HostOwnershipStore.EnvironmentOwner.CLI).getOrThrow() != null) {
            "请先启用 CLI 的 Studio 专属启动配置"
        }

        if (preferShortLauncher) {
            val launcher = defaultLauncherFile()
            val installResult = installLauncher(customInstallation, launcher)
            if (installResult.isSuccess && isLauncherInstalled(launcher)) {
                return@runCatching "agy-studio"
            }
            if (!customInstallation.isNullOrBlank()) {
                installResult.getOrThrow()
            }
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
