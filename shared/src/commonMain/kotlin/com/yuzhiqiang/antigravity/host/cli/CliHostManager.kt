package com.yuzhiqiang.antigravity.host.cli

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.io.File

/**
 * CLI 宿主集成管理器，对标 agy-byok 的 cli_host.rs。
 * 检测 agy CLI 工具是否安装，并管理其配置文件中的代理 URL。
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
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            val userHome = System.getProperty("user.home")
            if (os.contains("win")) {
                val whereProcess = ProcessBuilder("where", "agy").start()
                if (whereProcess.waitFor() == 0) return true

                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
                listOf(
                    File(localAppData, "agy/bin/agy.exe"),
                    File(localAppData, "Programs/agy/agy.exe"),
                    File(localAppData, "Programs/Antigravity/agy.exe"),
                    File(userHome, ".cargo/bin/agy.exe")
                ).any { it.exists() }
            } else {
                val whichProcess = ProcessBuilder("/usr/bin/which", "agy").start()
                if (whichProcess.waitFor() == 0) return true

                listOf(
                    File("$userHome/.local/bin/agy"),
                    File("/usr/local/bin/agy"),
                    File("/opt/homebrew/bin/agy"),
                    File("$userHome/.cargo/bin/agy")
                ).any { it.exists() }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检测 CLI 是否已配置代理端点。
     */
    fun isActive(proxyPort: Int): Boolean {
        return HostOwnershipStore.isEnvironmentConfigured(
            HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort
        )
    }

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val inspect = HostOwnershipStore.inspectEnvironmentIntegration(
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
            customPath = customInstallation
        )
    }

    /**
     * 启用 CLI 代理接入：在配置文件中写入代理端点。
     * 对标 cli_host.rs 中的 enable_cli_integration。
     */
    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.CLI,
            proxyPort = proxyPort
        ).isSuccess
    }

    /**
     * 禁用 CLI 代理接入：移除配置文件中的代理端点。
     */
    fun disable(): Boolean {
        val envSuccess = HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.CLI
        ).isSuccess
        runCatching {
            val configFile = getConfigFile()
            if (configFile.exists()) {
                val content = configFile.readText(Charsets.UTF_8)
                if (content.contains("CLOUD_CODE_URL") || content.contains("127.0.0.1")) {
                    configFile.delete()
                }
            }
        }
        return envSuccess
    }

    /**
     * 强制重置 CLI 代理接入至官方模式。
     */
    fun forceReset(): Boolean {
        runCatching {
            val configFile = getConfigFile()
            if (configFile.exists()) {
                configFile.delete()
            }
        }
        return HostOwnershipStore.forceResetEnvironment().isSuccess
    }
}
