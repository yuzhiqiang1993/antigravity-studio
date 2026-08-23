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
                        File(root, "resources/bin/language_server.exe").isFile
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
                        File(root, "Contents/Resources/bin/language_server").isFile
            }
        }
    }

    private val appMatchPatterns = if (isWindows) {
        listOf("Antigravity.exe")
    } else {
        listOf("Antigravity.app", "/MacOS/Antigravity")
    }

    private val appExcludePatterns = listOf("Antigravity IDE", "Antigravity-IDE", "Antigravity IDE.exe")

    private val appLanguageServerPatterns = if (isWindows) {
        listOf("Programs\\Antigravity\\resources\\bin\\language_server.exe", "Programs/Antigravity/resources/bin/language_server.exe")
    } else {
        listOf("Antigravity.app/Contents/Resources/bin/language_server")
    }

    /**
     * 检测 Antigravity App 是否正在运行（精确匹配 App 进程并排除 IDE 进程）。
     */
    fun isRunning(): Boolean {
        return HostProcessManager.isProcessRunning(appMatchPatterns, appExcludePatterns)
    }

    /**
     * 检测是否已设置代理环境变量。
     */
    fun isActive(proxyPort: Int): Boolean {
        return HostOwnershipStore.isEnvironmentConfigured(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        )
    }

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val running = installed && isRunning()
        val inspect = HostOwnershipStore.inspectEnvironmentIntegration(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        )
        val configState = when (inspect.state) {
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
            integrationState = inspect.state,
            configurationState = configState,
            configuredEndpoint = inspect.configuredEndpoint,
            targetEndpoint = target,
            configPath = "CLOUD_CODE_URL",
            canEnable = installed,
            canDisable = inspect.canDisable,
            canLaunch = installed && (inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL || (inspect.state.isReady && isProxyRunning)),
            customPath = customInstallation
        )
    }

    /**
     * 启用 App 代理接入：设置环境变量。
     */
    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort = proxyPort
        ).isSuccess
    }

    /**
     * 禁用 App 代理接入：移除环境变量。
     */
    fun disable(): Boolean {
        return HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP
        ).isSuccess
    }

    /**
     * 强制重置 App 代理接入至官方模式。
     */
    fun forceReset(): Boolean {
        return HostOwnershipStore.forceResetEnvironment().isSuccess
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
