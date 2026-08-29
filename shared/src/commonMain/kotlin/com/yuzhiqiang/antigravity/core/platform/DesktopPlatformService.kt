package com.yuzhiqiang.antigravity.core.platform

import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * 桌面平台底层抽象，提供安全的浏览器调用、目录浏览与文件定位服务。
 */
internal object DesktopPlatformService {

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isMac = osName.contains("mac") || osName.contains("darwin")
    private val isWindows = !isMac && osName.contains("win")

    /**
     * 在默认系统浏览器中打开指定 URL
     */
    internal fun openBrowser(url: String): Boolean {
        if (url.isBlank()) return false
        if (tryDesktop(Desktop.Action.BROWSE) { desktop -> desktop.browse(URI(url)) }) {
            return true
        }
        return runCatching {
            when {
                isMac -> ProcessBuilder("open", url).start()
                isWindows -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                else -> ProcessBuilder("xdg-open", url).start()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 在系统默认程序中打开指定文件
     */
    internal fun openFile(file: File): Boolean {
        if (tryDesktop(Desktop.Action.OPEN) { desktop -> desktop.open(file) }) {
            return true
        }
        return runCatching {
            when {
                isMac -> ProcessBuilder("open", file.absolutePath).start()
                isWindows -> ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                else -> ProcessBuilder("xdg-open", file.absolutePath).start()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 使用与当前 JVM 进程解耦的系统启动器打开安装包。
     *
     * Windows 必须通过 `cmd /c start` 派生独立进程；macOS 则短暂等待
     * LaunchServices 接管文件，避免调用方随即退出时安装器尚未完成拉起。
     */
    internal fun launchInstaller(file: File): Boolean {
        return runCatching {
            when {
                isMac -> {
                    val process = ProcessBuilder("open", file.absolutePath).start()
                    process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                }

                isWindows -> {
                    ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                }

                !tryDesktop(Desktop.Action.OPEN) { desktop -> desktop.open(file) } -> {
                    ProcessBuilder("xdg-open", file.absolutePath).start()
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 在系统文件管理器中打开文件夹
     */
    internal fun openDirectory(directory: File): Boolean {
        return openFile(directory)
    }

    /**
     * 在系统文件管理器中定位并选中文件
     */
    internal fun revealInFileManager(file: File): Boolean {
        return runCatching {
            when {
                isMac -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                    true
                }

                isWindows -> {
                    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                    true
                }

                else -> {
                    val parent = file.parentFile ?: file
                    openDirectory(parent)
                }
            }
        }.getOrDefault(false)
    }

    private fun tryDesktop(action: Desktop.Action, operation: (Desktop) -> Unit): Boolean {
        return runCatching {
            if (!Desktop.isDesktopSupported()) return@runCatching false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(action)) return@runCatching false
            operation(desktop)
            true
        }.getOrDefault(false)
    }
}
