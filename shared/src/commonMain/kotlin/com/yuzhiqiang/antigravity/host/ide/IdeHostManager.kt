package com.yuzhiqiang.antigravity.host.ide

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.io.File

object IdeHostManager {


    fun getCandidateSettingsFiles(): List<File> {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> listOf(
                File(userHome, "Library/Application Support/Antigravity IDE/User/settings.json"),
                File(userHome, "Library/Application Support/Antigravity/User/settings.json")
            )

            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                listOf(
                    File(appData, "Antigravity IDE/User/settings.json"),
                    File(appData, "Antigravity/User/settings.json")
                )
            }

            else -> listOf(
                File(userHome, ".config/Antigravity IDE/User/settings.json"),
                File(userHome, ".config/Antigravity/User/settings.json")
            )
        }
    }

    fun getSettingsFile(): File {
        val candidates = getCandidateSettingsFiles()
        return candidates.firstOrNull { it.exists() || it.parentFile?.exists() == true } ?: candidates.first()
    }

    fun isInstalled(): Boolean {
        val candidates = getCandidateSettingsFiles()
        return candidates.any { it.exists() || it.parentFile?.exists() == true }
    }

    fun isActive(proxyPort: Int): Boolean {
        return HostOwnershipStore.isIdeConfigured(getSettingsFile(), proxyPort)
    }

    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableIde(getSettingsFile(), proxyPort).isSuccess
    }

    fun disable(proxyPort: Int): Boolean {
        return HostOwnershipStore.disableIde(getSettingsFile(), proxyPort).isSuccess
    }

    /**
     * 检测 Antigravity IDE 进程是否正在运行（零子进程开销内存查询）。
     */
    fun isRunning(): Boolean {
        return try {
            ProcessHandle.allProcesses().anyMatch { handle ->
                val cmd = handle.info().command().orElse("")
                val cmdLine = handle.info().commandLine().orElse("")
                cmd.contains("Antigravity IDE", ignoreCase = true) || cmdLine.contains(
                    "Antigravity IDE",
                    ignoreCase = true
                )
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 一键启动 Antigravity IDE 客户端。
     */
    fun launch(): Boolean {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("mac") -> {
                    ProcessBuilder("/usr/bin/open", "-a", "Antigravity IDE").start()
                    true
                }

                os.contains("win") -> {
                    val localAppData =
                        System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
                    val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                    val exeCandidates = listOf(
                        File(localAppData, "Programs/Antigravity IDE/Antigravity IDE.exe"),
                        File(localAppData, "Programs/Antigravity/Antigravity IDE.exe"),
                        File(programFiles, "Antigravity IDE/Antigravity IDE.exe")
                    )
                    val target = exeCandidates.firstOrNull { it.exists() }
                    if (target != null) {
                        ProcessBuilder(target.absolutePath).start()
                    } else {
                        ProcessBuilder("cmd.exe", "/c", "start", "", "antigravity-ide").start()
                    }
                    true
                }

                else -> {
                    ProcessBuilder("antigravity-ide").start()
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 重启 Antigravity IDE 客户端。
     */
    fun restart(): Boolean {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("win")) {
                ProcessBuilder("taskkill", "/F", "/IM", "Antigravity IDE.exe").start().waitFor()
            } else {
                ProcessBuilder(
                    "/usr/bin/osascript", "-e",
                    """tell application "Antigravity IDE" to quit"""
                ).start().waitFor()
            }
            Thread.sleep(300)
            launch()
        } catch (_: Exception) {
            false
        }
    }
}
