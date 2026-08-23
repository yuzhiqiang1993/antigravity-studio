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

   /**
    * 检测 Antigravity App 是否正在运行（零子进程开销内存查询）。
    */
   fun isRunning(): Boolean {
       return if (isWindows) {
           HostProcessManager.isProcessRunning(listOf("Antigravity.exe"))
       } else {
           HostProcessManager.isProcessRunning(listOf("Antigravity.app", "/MacOS/Antigravity"))
       }
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
    * 跨平台重启 Antigravity App。
    */
   suspend fun restart(customInstallation: String? = null, proxyPort: Int? = null): Boolean {
       val terminated = HostProcessManager.terminateApplication(
           bundleId = "com.google.antigravity",
           matchPatterns = if (isWindows) listOf("Antigravity.exe") else listOf("Antigravity.app", "/MacOS/Antigravity"),
           label = "Antigravity App"
       )
       if (!terminated) return false
       delay(300)
       return launch(customInstallation, proxyPort)
   }

   private fun stopLanguageServer() {
       HostProcessManager.stopLanguageServer()
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
