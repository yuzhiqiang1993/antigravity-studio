package com.yuzhiqiang.antigravity.host.app

import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
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
       return try {
            val matched = ProcessHandle.allProcesses().anyMatch { handle ->
               val cmd = handle.info().command().orElse("")
               val cmdLine = handle.info().commandLine().orElse("")
               if (isWindows) {
                   cmd.contains("Antigravity.exe", ignoreCase = true) || cmdLine.contains(
                       "Antigravity.exe",
                       ignoreCase = true
                   )
               } else {
                    (cmd.contains("Antigravity.app", ignoreCase = true) || cmd.contains("/MacOS/Antigravity", ignoreCase = true)) &&
                            !cmd.contains("Antigravity IDE", ignoreCase = true)
               }
           }
            if (matched) return true
            if (!isWindows) {
                val pgrep = ProcessBuilder("pgrep", "-f", "Antigravity.app/Contents/MacOS/Antigravity").start()
                return pgrep.waitFor() == 0
            }
            false
       } catch (_: Exception) {
           false
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
    fun launch(customInstallation: String? = null): Boolean {
        return try {
            if (isWindows) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local"
                val customExe = customInstallation?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { File(it).let { root -> if (root.isFile) root else File(root, "Antigravity.exe") } }
                val exe = customExe?.takeIf(File::isFile)
                    ?: File(localAppData, "Programs/Antigravity/Antigravity.exe")
                if (exe.exists()) {
                    ProcessBuilder(exe.absolutePath).start()
                } else {
                    ProcessBuilder("cmd.exe", "/c", "start", "", "Antigravity.exe").start()
                }
                true
            } else {
                val app = customInstallation?.trim()?.takeIf { it.isNotEmpty() }
                if (app != null && File(app).isDirectory) {
                    ProcessBuilder("/usr/bin/open", app).start()
                } else {
                    ProcessBuilder("/usr/bin/open", "-a", "Antigravity").start()
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

   /**
    * 跨平台重启 Antigravity App。
    */
   suspend fun restart(customInstallation: String? = null): Boolean {
       return try {
           stopLanguageServer()
           if (isWindows) {
               ProcessBuilder("taskkill", "/F", "/IM", "Antigravity.exe").start().waitFor()
               delay(500)
               launch(customInstallation)
           } else {
               val quit = ProcessBuilder(
                   "/usr/bin/osascript", "-e",
                   """tell application "Antigravity" to quit"""
               ).start()
               quit.waitFor()
               delay(600)
                if (isRunning()) {
                    ProcessBuilder("pkill", "-f", "Antigravity.app/Contents/MacOS/Antigravity").start().waitFor()
                    delay(400)
                }
                stopLanguageServer()
               launch(customInstallation)
           }
       } catch (_: Exception) {
           false
       }
   }

    private fun stopLanguageServer() {
        try {
            ProcessHandle.allProcesses().forEach { handle ->
                val command = handle.info().command().orElse("")
                val commandLine = handle.info().commandLine().orElse("")
                if (commandLine.contains("language_server", ignoreCase = true) &&
                    (command.contains("Antigravity", ignoreCase = true) ||
                            commandLine.contains("Antigravity", ignoreCase = true))
                ) {
                    handle.destroyForcibly()
                }
            }
        } catch (_: Exception) {
            // 语言服务已退出时无需阻断宿主重启。
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
