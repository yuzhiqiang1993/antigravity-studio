package com.yuzhiqiang.antigravity.host.ide

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.io.File

object IdeHostManager {

    private fun getCandidateInstallations(customInstallation: String? = null): List<File> {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val customRoot = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
            ?.let { if (it.isFile) it.parentFile else it }
        return when {
            os.contains("mac") -> buildList {
                customRoot?.let(::add)
                add(File("/Applications/Antigravity IDE.app"))
                add(File(home, "Applications/Antigravity IDE.app"))
                addAll(discoverMacApplications("com.google.antigravity-ide"))
            }

            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local"
                val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                buildList {
                    customRoot?.let(::add)
                    add(File(localAppData, "Programs/Antigravity IDE"))
                    add(File(programFiles, "Antigravity IDE"))
                    System.getenv("ProgramFiles(x86)")?.let { add(File(it, "Antigravity IDE")) }
                    addAll(discoverWindowsInstallations("Antigravity IDE.exe"))
                }
            }

            else -> emptyList()
        }
    }

    private fun isInstallationComplete(root: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("mac")) {
            root.isDirectory && File(root, "Contents/MacOS/Electron").isFile
        } else {
            root.isDirectory && File(root, "Antigravity IDE.exe").isFile
        }
    }

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

    fun isInstalled(customInstallation: String? = null): Boolean {
        return getCandidateInstallations(customInstallation).any(::isInstallationComplete)
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
    fun launch(customInstallation: String? = null): Boolean {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("mac") -> {
                    val app = customInstallation?.trim()?.takeIf { it.isNotEmpty() }
                    if (app != null && File(app).isDirectory) {
                        ProcessBuilder("/usr/bin/open", app).start()
                    } else {
                        ProcessBuilder("/usr/bin/open", "-a", "Antigravity IDE").start()
                    }
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
                    val customExe = customInstallation?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { File(it).let { root -> if (root.isFile) root else File(root, "Antigravity IDE.exe") } }
                    val target = customExe?.takeIf(File::isFile) ?: exeCandidates.firstOrNull { it.exists() }
                    if (target != null) {
                        ProcessBuilder(target.absolutePath).start()
                    } else {
                        ProcessBuilder("cmd.exe", "/c", "start", "", "antigravity-ide").start()
                    }
                    true
                }

                else -> {
                    val app = customInstallation?.trim()?.takeIf { it.isNotEmpty() }
                    if (app != null && File(app).canExecute()) ProcessBuilder(app).start()
                    else ProcessBuilder("antigravity-ide").start()
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
    fun restart(customInstallation: String? = null): Boolean {
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
            launch(customInstallation)
        } catch (_: Exception) {
            false
        }
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
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return emptyList()
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
