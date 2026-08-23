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

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val running = installed && isRunning(customInstallation)
        val settingsFile = getSettingsFile()
        val inspect = HostOwnershipStore.inspectIdeIntegration(settingsFile, proxyPort)
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
            type = com.yuzhiqiang.antigravity.host.model.HostType.IDE,
            isInstalled = installed,
            isRunning = running,
            integrationState = inspect.state,
            configurationState = configState,
            configuredEndpoint = inspect.configuredEndpoint,
            targetEndpoint = target,
            configPath = settingsFile.absolutePath,
            canEnable = installed,
            canDisable = inspect.canDisable,
            canLaunch = installed && (inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL || (inspect.state.isReady && isProxyRunning)),
            customPath = customInstallation
        )
    }

    fun enable(proxyPort: Int): Boolean {
        return HostOwnershipStore.enableIde(getSettingsFile(), proxyPort).isSuccess
    }

    fun disable(proxyPort: Int): Boolean {
        return HostOwnershipStore.disableIde(getSettingsFile(), proxyPort).isSuccess
    }

    fun forceReset(): Boolean {
        return HostOwnershipStore.forceResetIde(getSettingsFile()).isSuccess
    }

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    private val ideMatchPatterns = if (isWindows) {
        listOf("Antigravity IDE.exe", "Antigravity-IDE.exe", "AntigravityIDE.exe")
    } else {
        listOf(
            "Antigravity IDE.app/",
            "/Antigravity IDE.app",
            "Antigravity-IDE.app/",
            "/Antigravity-IDE.app",
            "/MacOS/Antigravity IDE"
        )
    }

    private val ideExcludePatterns = listOf(
        "Antigravity Studio",
        "antigravity-studio",
        "Antigravity Studio.app",
        "Antigravity.app",
        "antigravity-ide-cockpit"
    )

    private val ideLanguageServerPatterns = if (isWindows) {
        listOf("Antigravity IDE\\resources\\app\\extensions\\antigravity\\bin", "Antigravity IDE/resources/app/extensions/antigravity/bin")
    } else {
        listOf("Antigravity IDE.app/Contents/Resources/app/extensions/antigravity/bin")
    }

    /**
     * 检测 Antigravity IDE 进程是否正在运行（精确匹配 IDE 进程）。
     */
    fun isRunning(customInstallation: String? = null): Boolean {
        val patterns = buildList {
            addAll(ideMatchPatterns)
            if (!customInstallation.isNullOrBlank()) {
                val file = File(customInstallation.trim())
                add(file.name)
                if (file.name.endsWith(".app", ignoreCase = true)) {
                    add(file.name + "/")
                }
            }
        }.distinct()
        return HostProcessManager.isProcessRunning(patterns, ideExcludePatterns)
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
