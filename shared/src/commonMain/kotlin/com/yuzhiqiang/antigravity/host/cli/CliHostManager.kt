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
    fun isInstalled(): Boolean {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            val userHome = System.getProperty("user.home")
            if (os.contains("win")) {
                val whereProcess = ProcessBuilder("where", "agy").start()
                if (whereProcess.waitFor() == 0) return true

                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
                listOf(
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
        return HostOwnershipStore.isEnvironmentConfigured(proxyPort)
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
        return HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.CLI
        ).isSuccess
    }
}
