package com.yuzhiqiang.antigravity.host.app

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.process.HostProcessManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * App 宿主跨平台集成管理器（支持 macOS 与 Windows 双平台）。
 */
object AppHostManager {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    /**
     * 检测 Antigravity App 是否已安装。
     */
    fun isInstalled(customInstallation: String? = null): Boolean {
        if (!customInstallation.isNullOrBlank()) {
            val file = File(customInstallation.trim())
            if (file.exists()) {
                if (file.isFile) return true
                if (file.isDirectory && (File(file, "Contents/MacOS/Antigravity").isFile || File(file, "Antigravity.exe").isFile)) return true
            }
        }
        val customRoot = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
            ?.let { if (it.isFile) it.parentFile else it }
        return if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val paths = buildList {
                customRoot?.let(::add)
                add(File(localAppData, "Programs/Antigravity"))
                add(File(programFiles, "Antigravity"))
                System.getenv("ProgramFiles(x86)")?.let { add(File(it, "Antigravity")) }
                addAll(discoverWindowsInstallations("Antigravity.exe"))
            }
            paths.any { root ->
                root.isDirectory &&
                        File(root, "Antigravity.exe").isFile &&
                        (File(root, "resources/bin/language_server.exe").isFile ||
                         File(root, "resources/bin/language_server.original.exe").isFile)
            }
        } else {
            val appPaths = buildList {
                customRoot?.let(::add)
                add(File("/Applications/Antigravity.app"))
                add(File("${System.getProperty("user.home")}/Applications/Antigravity.app"))
                addAll(discoverMacApplications("com.google.antigravity"))
            }
            appPaths.any { root ->
                root.isDirectory &&
                        File(root, "Contents/MacOS/Antigravity").isFile &&
                        (File(root, "Contents/Resources/bin/language_server").isFile ||
                         File(root, "Contents/Resources/bin/language_server.original").isFile)
            }
        }
    }

    private val appMatchPatterns = if (isWindows) {
        listOf("Antigravity.exe")
    } else {
        listOf("Antigravity.app/", "/Antigravity.app", "/MacOS/Antigravity")
    }

    private val appExcludePatterns = listOf(
        "Antigravity IDE",
        "Antigravity-IDE",
        "Antigravity IDE.exe",
        "Antigravity Studio",
        "antigravity-studio",
        "Antigravity Studio.app",
        "antigravity-ide-cockpit"
    )

    private val appLanguageServerPatterns = if (isWindows) {
        listOf(
            "Programs\\Antigravity\\resources\\bin\\language_server.exe",
            "Programs/Antigravity/resources/bin/language_server.exe",
            "Programs\\Antigravity\\resources\\bin\\language_server.original.exe",
            "Programs/Antigravity/resources/bin/language_server.original.exe"
        )
    } else {
        listOf(
            "Antigravity.app/Contents/Resources/bin/language_server",
            "Antigravity.app/Contents/Resources/bin/language_server.original"
        )
    }

    /**
     * 获取候选安装根目录列表。
     */
    fun getCandidateInstallations(customInstallation: String? = null): List<File> {
        val customRoot = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
            ?.let { if (it.isFile) it.parentFile else it }
        if (customRoot != null) {
            return listOf(customRoot)
        }
        return if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            buildList {
                add(File(localAppData, "Programs/Antigravity"))
                add(File(programFiles, "Antigravity"))
                System.getenv("ProgramFiles(x86)")?.let { add(File(it, "Antigravity")) }
                addAll(discoverWindowsInstallations("Antigravity.exe"))
            }
        } else {
            buildList {
                add(File("/Applications/Antigravity.app"))
                add(File("${System.getProperty("user.home")}/Applications/Antigravity.app"))
                addAll(discoverMacApplications("com.google.antigravity"))
            }
        }
    }

    /**
     * 获取当前 language_server 可执行文件。
     */
    fun getLanguageServerFile(customInstallation: String? = null): File? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val lsFile = if (isWindows) {
                File(root, "resources/bin/language_server.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server")
            }
            val origFile = if (isWindows) {
                File(root, "resources/bin/language_server.original.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server.original")
            }
            if (lsFile.exists() || origFile.exists()) {
                return lsFile
            }
        }
        return null
    }

    /**
     * 获取被备份的原始 language_server 二进制文件。
     */
    fun getOriginalLanguageServerFile(customInstallation: String? = null): File? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val origFile = if (isWindows) {
                File(root, "resources/bin/language_server.original.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server.original")
            }
            if (origFile.exists()) {
                return origFile
            }
        }
        return null
    }

    /**
     * 检测是否已安装 Language Server Shim 包装脚本。
     */
    fun isShimInstalled(customInstallation: String? = null): Boolean {
        val orig = getOriginalLanguageServerFile(customInstallation)
        if (orig != null && orig.exists()) return true
        val ls = getLanguageServerFile(customInstallation) ?: return false
        if (!ls.exists()) return false
        val content = runCatching { ls.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        return content.contains("ANTIGRAVITY_STUDIO_MANAGED_SHIM")
    }

    /**
     * 安装 Language Server Shim 包装脚本，将写死的 --cloud_code_endpoint 动态重写为本地代理端口。
     */
    fun installLanguageServerShim(proxyPort: Int, customInstallation: String? = null): Boolean {
        val ls = getLanguageServerFile(customInstallation) ?: return false
        val binDir = ls.parentFile ?: return false
        val origFile = if (isWindows) {
            File(binDir, "language_server.original.exe")
        } else {
            File(binDir, "language_server.original")
        }

        // 1. 如果 original 不存在，将当前二进制备份重命名为 original
        if (!origFile.exists()) {
            if (!ls.exists()) return false
            val renamed = ls.renameTo(origFile)
            if (!renamed) return false
        }

        // 2. 写入包装脚本
        val targetEndpoint = "http://127.0.0.1:$proxyPort"
        return if (isWindows) {
            val scriptContent = buildString {
                appendLine("@echo off")
                appendLine("rem ANTIGRAVITY_STUDIO_MANAGED_SHIM")
                appendLine("setlocal enabledelayedexpansion")
                appendLine("set \"DIR=%~dp0\"")
                appendLine("set \"ORIGINAL=%DIR%language_server.original.exe\"")
                appendLine("set \"TARGET_URL=$targetEndpoint\"")
                appendLine("if defined CLOUD_CODE_URL set \"TARGET_URL=%CLOUD_CODE_URL%\"")
                appendLine("set \"NEW_ARGS=\"")
                appendLine("set \"SKIP_NEXT=0\"")
                appendLine("for %%A in (%*) do (")
                appendLine("    if !SKIP_NEXT! equ 1 (")
                appendLine("        set \"NEW_ARGS=!NEW_ARGS! !TARGET_URL!\"")
                appendLine("        set \"SKIP_NEXT=0\"")
                appendLine("    ) else if \"%%~A\"==\"--cloud_code_endpoint\" (")
                appendLine("        set \"NEW_ARGS=!NEW_ARGS! %%~A\"")
                appendLine("        set \"SKIP_NEXT=1\"")
                appendLine("    ) else (")
                appendLine("        set \"ARG=%%~A\"")
                appendLine("        if \"!ARG:~0,22!\"==\"--cloud_code_endpoint=\" (")
                appendLine("            set \"NEW_ARGS=!NEW_ARGS! --cloud_code_endpoint=!TARGET_URL!\"")
                appendLine("        ) else (")
                appendLine("            set \"NEW_ARGS=!NEW_ARGS! %%~A\"")
                appendLine("        )")
                appendLine("    )")
                appendLine(")")
                appendLine("\"%ORIGINAL%\" %NEW_ARGS%")
            }
            runCatching {
                ls.writeText(scriptContent, Charsets.UTF_8)
                true
            }.getOrDefault(false)
        } else {
            val scriptContent = """
                #!/bin/bash
                # ANTIGRAVITY_STUDIO_MANAGED_SHIM
                DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
                ORIGINAL_BIN="${'$'}DIR/language_server.original"
                
                TARGET_URL="${'$'}{CLOUD_CODE_URL}"
                if [ -z "${'$'}TARGET_URL" ]; then
                    TARGET_URL="$(launchctl getenv CLOUD_CODE_URL 2>/dev/null)"
                fi
                if [ -z "${'$'}TARGET_URL" ]; then
                    TARGET_URL="$targetEndpoint"
                fi
                
                ARGS=()
                SKIP_NEXT=0
                HAS_ENDPOINT=0
                
                for arg in "${'$'}@"; do
                    if [ "${'$'}SKIP_NEXT" -eq 1 ]; then
                        ARGS+=("${'$'}TARGET_URL")
                        SKIP_NEXT=0
                        HAS_ENDPOINT=1
                        continue
                    fi
                    if [ "${'$'}arg" = "--cloud_code_endpoint" ]; then
                        ARGS+=("${'$'}arg")
                        SKIP_NEXT=1
                        continue
                    fi
                    if [[ "${'$'}arg" =~ ^--cloud_code_endpoint= ]]; then
                        ARGS+=("--cloud_code_endpoint=${'$'}TARGET_URL")
                        HAS_ENDPOINT=1
                        continue
                    fi
                    ARGS+=("${'$'}arg")
                done
                
                if [ "${'$'}HAS_ENDPOINT" -eq 0 ] && [ "${'$'}SKIP_NEXT" -eq 0 ]; then
                    ARGS+=("--cloud_code_endpoint" "${'$'}TARGET_URL")
                fi
                
                exec "${'$'}ORIGINAL_BIN" "${'$'}{ARGS[@]}"
            """.trimIndent()
            runCatching {
                ls.writeText(scriptContent + "\n", Charsets.UTF_8)
                ls.setExecutable(true, false)
                true
            }.getOrDefault(false)
        }
    }

    /**
     * 还原原始 language_server 二进制文件（彻底清除包装器脚本）。
     */
    fun restoreOriginalLanguageServer(customInstallation: String? = null): Boolean {
        val origFile = getOriginalLanguageServerFile(customInstallation)
        val ls = getLanguageServerFile(customInstallation)
        if (origFile != null && origFile.exists()) {
            if (ls != null && ls.exists()) {
                ls.delete()
            }
            val target = ls ?: File(origFile.parentFile, if (isWindows) "language_server.exe" else "language_server")
            return origFile.renameTo(target)
        }
        return true
    }

    /**
     * 检测 Antigravity App 是否正在运行（精确匹配 App 进程并排除 IDE 进程）。
     */
    fun isRunning(customInstallation: String? = null): Boolean {
        val patterns = buildList {
            addAll(appMatchPatterns)
            if (!customInstallation.isNullOrBlank()) {
                val file = File(customInstallation.trim())
                add(file.name)
                if (file.name.endsWith(".app", ignoreCase = true)) {
                    add(file.name + "/")
                }
            }
        }.distinct()
        return HostProcessManager.isProcessRunning(patterns, appExcludePatterns)
    }

    /**
     * 检测是否已设置代理（包含环境变量与 Shim 包装器）。
     */
    fun isActive(proxyPort: Int, customInstallation: String? = null): Boolean {
        return HostOwnershipStore.isEnvironmentConfigured(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        ) || isShimInstalled(customInstallation)
    }

    fun detectVersion(customInstallation: String? = null): String? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val infoPlist = File(root, "Contents/Info.plist")
            if (infoPlist.exists()) {
                val content = runCatching { infoPlist.readText() }.getOrNull() ?: continue
                val match = Regex("<key>CFBundleShortVersionString</key>\\s*<string>([^<]+)</string>").find(content)
                    ?: Regex("<key>CFBundleVersion</key>\\s*<string>([^<]+)</string>").find(content)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            val packageJson = File(root, "resources/app/package.json")
            if (packageJson.exists()) {
                val content = runCatching { packageJson.readText() }.getOrNull() ?: continue
                val match = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(content)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
        }
        return null
    }

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val running = installed && isRunning(customInstallation)
        val version = if (installed) runCatching { detectVersion(customInstallation) }.getOrNull() else null
        val inspect = HostOwnershipStore.inspectEnvironmentIntegration(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        )
        val shimInstalled = isShimInstalled(customInstallation)
        val isManaged = inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED || shimInstalled
        val finalState = if (isManaged && inspect.endpointMatches) {
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED
        } else if (shimInstalled && !inspect.endpointMatches) {
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH
        } else {
            inspect.state
        }

        val configState = when (finalState) {
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_ENABLED
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.CONFLICT,
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
            else -> when {
                !inspect.endpointMatches -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NEEDS_UPDATE
                !isProxyRunning -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.SERVICE_STOPPED
                !running -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_RUNNING
                else -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.MATCHED
            }
        }
        val target = "http://127.0.0.1:$proxyPort"
        return com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            type = com.yuzhiqiang.antigravity.host.model.HostType.APP,
            isInstalled = installed,
            isRunning = running,
            integrationState = finalState,
            configurationState = configState,
            configuredEndpoint = inspect.configuredEndpoint ?: target.takeIf { shimInstalled },
            targetEndpoint = target,
            configPath = "CLOUD_CODE_URL & language_server",
            canEnable = installed,
            canDisable = inspect.canDisable || shimInstalled,
            canLaunch = installed && (finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL || (finalState.isReady && isProxyRunning)),
            customPath = customInstallation,
            version = version
        )
    }

    /**
     * 启用 App 代理接入：设置环境变量并安装 Language Server Shim 包装脚本。
     */
    fun enable(proxyPort: Int, customInstallation: String? = null): Boolean {
        val envOk = HostOwnershipStore.enableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort = proxyPort
        ).isSuccess
        val shimOk = installLanguageServerShim(proxyPort, customInstallation)
        return envOk && shimOk
    }

    /**
     * 禁用 App 代理接入：移除环境变量并还原原始 Language Server 二进制。
     */
    fun disable(customInstallation: String? = null): Boolean {
        val envOk = HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP
        ).isSuccess
        val shimOk = restoreOriginalLanguageServer(customInstallation)
        return envOk && shimOk
    }

    /**
     * 强制重置 App 代理接入至纯净官方模式。
     */
    fun forceReset(customInstallation: String? = null): Boolean {
        val shimOk = restoreOriginalLanguageServer(customInstallation)
        val envOk = HostOwnershipStore.forceResetEnvironment().isSuccess
        return envOk && shimOk
    }

    /**
     * 跨平台启动 Antigravity App。
     */
    fun launch(customInstallation: String? = null, proxyPort: Int? = null): Boolean {
        val env = if (proxyPort != null && isActive(proxyPort)) {
            mapOf("CLOUD_CODE_URL" to ("http://127.0.0.1:" + proxyPort))
        } else {
            null
        }
        return HostProcessManager.launch(
            installationPath = customInstallation,
            defaultMacApp = "Antigravity",
            defaultWinExe = "Antigravity.exe",
            environment = env
        )
    }

    /**
     * 跨平台重启 Antigravity App（仅终止与重启 App 自身，绝不干扰 IDE）。
     */
    suspend fun restart(customInstallation: String? = null, proxyPort: Int? = null): Boolean {
        val terminated = HostProcessManager.terminateApplication(
            bundleId = "com.google.antigravity",
            matchPatterns = appMatchPatterns,
            excludePatterns = appExcludePatterns,
            languageServerPatterns = appLanguageServerPatterns,
            label = "Antigravity App"
        )
        if (!terminated) return false
        delay(300)
        return launch(customInstallation, proxyPort)
    }

    private fun discoverMacApplications(bundleId: String): List<File> {
        return try {
            val process = ProcessBuilder(
                "/usr/bin/mdfind", "-0", "kMDItemCFBundleIdentifier == '$bundleId'"
            ).start()
            val output = process.inputStream.readBytes()
            process.waitFor()
            output.toString(Charsets.UTF_8)
                .split('\u0000')
                .filter { it.isNotBlank() }
                .map(::File)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun discoverWindowsInstallations(executableName: String): List<File> {
        if (!isWindows) return emptyList()
        return try {
            listOf(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\$executableName",
                "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\$executableName"
            ).mapNotNull { key ->
                val process = ProcessBuilder("reg", "query", key, "/ve").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.lineSequence()
                    .firstOrNull { it.contains("REG_SZ") }
                    ?.substringAfter("REG_SZ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::File)
                    ?.let { if (it.isFile) it.parentFile else it }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
