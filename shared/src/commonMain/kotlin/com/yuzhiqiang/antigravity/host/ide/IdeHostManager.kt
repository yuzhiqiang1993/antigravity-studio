package com.yuzhiqiang.antigravity.host.ide

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.process.HostProcessManager
import kotlinx.coroutines.delay
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
        if (!customInstallation.isNullOrBlank()) {
            val file = File(customInstallation.trim())
            if (file.exists()) {
                if (file.isFile) return true
                if (isInstallationComplete(file)) return true
            }
        }
        return getCandidateInstallations(customInstallation).any(::isInstallationComplete)
    }

    fun isActive(proxyPort: Int): Boolean {
        val candidates = getCandidateSettingsFiles()
        return candidates.any { HostOwnershipStore.isIdeConfigured(it, proxyPort) }
    }

    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableIde(getSettingsFile(), proxyPort).isSuccess
    }

    fun disable(proxyPort: Int): Boolean {
        return HostOwnershipStore.disableIde(getSettingsFile(), proxyPort).isSuccess
    }

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    private val ideMatchPatterns = if (isWindows) {
        listOf("Antigravity IDE.exe")
    } else {
        listOf("Antigravity IDE.app", "Antigravity IDE")
    }

    private val ideExcludePatterns = emptyList<String>()

    private val ideLanguageServerPatterns = if (isWindows) {
        listOf("Antigravity IDE\\resources\\app\\extensions\\antigravity\\bin", "Antigravity IDE/resources/app/extensions/antigravity/bin")
    } else {
        listOf("Antigravity IDE.app/Contents/Resources/app/extensions/antigravity/bin")
    }

    /**
     * 检测 Antigravity IDE 进程是否正在运行（精确匹配 IDE 进程）。
     */
    fun isRunning(): Boolean {
        return HostProcessManager.isProcessRunning(ideMatchPatterns, ideExcludePatterns)
    }

    /**
     * 一键启动 Antigravity IDE 客户端。
     */
    fun launch(customInstallation: String? = null): Boolean {
        return HostProcessManager.launch(
            installationPath = customInstallation,
            defaultMacApp = "Antigravity IDE",
            defaultWinExe = "Antigravity IDE.exe"
        )
    }

    /**
     * 重启 Antigravity IDE 客户端（仅终止与重启 IDE 自身，绝不干扰 App）。
     */
    suspend fun restart(customInstallation: String? = null): Boolean {
        val terminated = HostProcessManager.terminateApplication(
            bundleId = "com.google.antigravity-ide",
            matchPatterns = ideMatchPatterns,
            excludePatterns = ideExcludePatterns,
            languageServerPatterns = ideLanguageServerPatterns,
            label = "Antigravity IDE"
        )
        if (!terminated) return false
        delay(300)
        return launch(customInstallation)
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
