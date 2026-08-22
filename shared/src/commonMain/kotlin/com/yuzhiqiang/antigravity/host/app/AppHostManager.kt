package com.yuzhiqiang.antigravity.host.app

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.io.File

/**
 * App 宿主跨平台集成管理器（支持 macOS 与 Windows 双平台）。
 */
object AppHostManager {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    /**
     * 检测 Antigravity App 是否已安装。
     */
    fun isInstalled(): Boolean {
        return if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val paths = listOf(
                File(localAppData, "Programs/Antigravity/Antigravity.exe"),
                File(programFiles, "Antigravity/Antigravity.exe")
            )
            paths.any { it.exists() }
        } else {
            val appPaths = listOf(
                File("/Applications/Antigravity.app"),
                File("${System.getProperty("user.home")}/Applications/Antigravity.app")
            )
            appPaths.any { it.exists() }
        }
    }

    /**
     * 检测 Antigravity App 是否正在运行（零子进程开销内存查询）。
     */
    fun isRunning(): Boolean {
        return try {
            ProcessHandle.allProcesses().anyMatch { handle ->
                val cmd = handle.info().command().orElse("")
                val cmdLine = handle.info().commandLine().orElse("")
                if (isWindows) {
                    cmd.contains("Antigravity.exe", ignoreCase = true) || cmdLine.contains(
                        "Antigravity.exe",
                        ignoreCase = true
                    )
                } else {
                    cmd.contains("Antigravity.app", ignoreCase = true) || cmdLine.contains(
                        "Antigravity.app",
                        ignoreCase = true
                    )
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检测是否已设置代理环境变量。
     */
    fun isActive(proxyPort: Int): Boolean {
        return HostOwnershipStore.isEnvironmentConfigured(proxyPort)
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
     * 跨平台启动 Antigravity App。
     */
    fun launch(): Boolean {
        return try {
            if (isWindows) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
                val exe = File(localAppData, "Programs/Antigravity/Antigravity.exe")
                if (exe.exists()) {
                    ProcessBuilder(exe.absolutePath).start()
                } else {
                    ProcessBuilder("cmd.exe", "/c", "start", "", "Antigravity.exe").start()
                }
                true
            } else {
                ProcessBuilder("/usr/bin/open", "-a", "Antigravity").start()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 跨平台重启 Antigravity App。
     */
    fun restart(): Boolean {
        return try {
            if (isWindows) {
                ProcessBuilder("taskkill", "/F", "/IM", "Antigravity.exe").start().waitFor()
                Thread.sleep(300)
                launch()
            } else {
                val quit = ProcessBuilder(
                    "/usr/bin/osascript", "-e",
                    """tell application "Antigravity" to quit"""
                ).start()
                quit.waitFor()
                Thread.sleep(300)
                launch()
            }
        } catch (_: Exception) {
            false
        }
    }
}
