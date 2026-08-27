package com.yuzhiqiang.antigravity.host.ide

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.process.HostProcessManager
import kotlinx.coroutines.delay
import java.io.File

object IdeHostManager {

    fun getCandidateInstallations(customInstallation: String? = null): List<File> {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val customRoot = customInstallation?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
            ?.let { if (it.isFile) it.parentFile else it }
        val candidates = when {
            os.contains("mac") -> buildList {
                customRoot?.let(::add)
                add(File("/Applications/Antigravity IDE.app"))
                add(File(home, "Applications/Antigravity IDE.app"))
                add(File("/Applications/Antigravity-IDE.app"))
                add(File(home, "Applications/Antigravity-IDE.app"))
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

            else -> buildList {
                customRoot?.let(::add)
                add(File("/opt/antigravity-ide"))
                add(File("/usr/share/antigravity-ide"))
                add(File(home, ".local/share/antigravity-ide"))
            }
        }
        return candidates.filterNot { file ->
            val path = file.absolutePath.replace('\\', '/')
            path.endsWith("/Antigravity.app") || path.endsWith("/Antigravity App.app") || path.endsWith("/Antigravity Studio.app")
        }.distinct()
    }

    private fun isInstallationComplete(root: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> root.isDirectory && File(root, "Contents/MacOS/Electron").isFile
            os.contains("win") -> root.isDirectory && File(root, "Antigravity IDE.exe").isFile
            else -> {
                // Linux Electron 应用：检查二进制或 package.json 存在性
                root.isDirectory && (
                    File(root, "antigravity-ide").isFile ||
                    File(root, "antigravity_ide").isFile ||
                    File(root, "resources/app/package.json").isFile
                )
            }
        }
    }

    fun getCandidateSettingsFiles(): List<File> {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> listOf(
                File(userHome, "Library/Application Support/Antigravity IDE/User/settings.json"),
                File(userHome, "Library/Application Support/Antigravity-IDE/User/settings.json")
            )

            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                listOf(
                    File(appData, "Antigravity IDE/User/settings.json"),
                    File(appData, "Antigravity-IDE/User/settings.json")
                )
            }

            else -> listOf(
                File(userHome, ".config/Antigravity IDE/User/settings.json"),
                File(userHome, ".config/Antigravity-IDE/User/settings.json")
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
            customPath = customInstallation,
            version = version
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

    /** IDE 专属 Language Server 安装路径特征，不接受裸进程名。 */
    val ideLanguageServerPatterns = if (isWindows) {
        listOf(
            "Antigravity IDE\\resources\\app\\extensions\\antigravity\\bin",
            "Antigravity-IDE\\resources\\app\\extensions\\antigravity\\bin",
            "Antigravity IDE/resources/app/extensions/antigravity/bin"
        )
    } else {
        listOf(
            "Antigravity IDE.app/Contents/Resources/app/extensions/antigravity/bin",
            "Antigravity-IDE.app/Contents/Resources/app/extensions/antigravity/bin"
        )
    }

    /**
     * 检测 Antigravity IDE 进程是否正在运行（精确匹配 IDE 进程）。
     */
    fun isRunning(customInstallation: String?): Boolean {
        return HostProcessManager.isProcessRunning(
            buildProcessPatterns(customInstallation),
            ideExcludePatterns
        )
    }

    fun isRunning(): Boolean {
        return HostProcessManager.isProcessRunning(ideMatchPatterns, ideExcludePatterns)
    }

    fun findPids(customInstallation: String? = null): List<Long> {
        return HostProcessManager.findProcessPids(
            buildProcessPatterns(customInstallation),
            ideExcludePatterns
        )
    }

    /**
     * 一键启动 Antigravity IDE 客户端。
     */
    fun launch(customInstallation: String? = null): Boolean {
        val installationPath = customInstallation?.trim()?.takeIf(String::isNotEmpty)
            ?: getCandidateInstallations().firstOrNull(::isInstallationComplete)?.absolutePath
        return HostProcessManager.launch(
            installationPath = installationPath,
            defaultMacApp = "Antigravity IDE",
            defaultWinExe = "Antigravity IDE.exe"
        )
    }

    /**
     * 终止 Antigravity IDE 及其 language_server 子进程。
     */
    suspend fun terminate(customInstallation: String? = null, force: Boolean = true): Boolean {
        return HostProcessManager.terminateApplication(
            bundleId = "com.google.antigravity-ide",
            matchPatterns = buildProcessPatterns(customInstallation),
            excludePatterns = ideExcludePatterns,
            languageServerPatterns = buildLanguageServerPatterns(customInstallation),
            label = "Antigravity IDE",
            force = force
        )
    }

    /**
     * 重启 Antigravity IDE 客户端（仅终止与重启 IDE 自身，绝不干扰 App）。
     */
    suspend fun restart(customInstallation: String? = null): Boolean {
        if (!terminate(customInstallation, force = true)) return false
        delay(300)
        if (!launch(customInstallation)) return false
        return waitUntilRunning(customInstallation)
    }

    private fun buildProcessPatterns(customInstallation: String?): List<String> {
        val customPath = customInstallation?.trim()?.takeIf(String::isNotEmpty)
        return if (customPath == null) {
            ideMatchPatterns
        } else {
            listOf(resolveCanonicalPath(File(customPath)))
        }
    }

    private fun buildLanguageServerPatterns(customInstallation: String?): List<String> {
        val customRoot = findApplicationRoot(customInstallation)
        if (customRoot == null) return ideLanguageServerPatterns
        val relativePath = if (isWindows) {
            "resources/app/extensions/antigravity/bin"
        } else {
            "Contents/Resources/app/extensions/antigravity/bin"
        }
        return listOf(File(customRoot, relativePath).absolutePath)
    }

    private suspend fun waitUntilRunning(customInstallation: String?): Boolean {
        repeat(25) {
            if (isRunning(customInstallation)) return true
            delay(200)
        }
        return false
    }

    private fun findApplicationRoot(customInstallation: String?): File? {
        val customFile = customInstallation?.trim()?.takeIf(String::isNotEmpty)?.let(::File)
            ?: return null
        if (customFile.isDirectory) return customFile
        return generateSequence(customFile.parentFile, File::getParentFile)
            .firstOrNull { it.name.endsWith(".app", ignoreCase = true) }
            ?: customFile.parentFile
    }

    private fun resolveCanonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
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
