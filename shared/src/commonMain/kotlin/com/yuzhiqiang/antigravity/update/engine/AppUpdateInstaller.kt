package com.yuzhiqiang.antigravity.update.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * 跨平台更新安装器调度工具
 */
object AppUpdateInstaller {

    /**
     * 打开安装包文件并根据需要退出当前应用。
     *
     * @param file 安装包文件 (.dmg / .pkg / .exe / .msi / .deb / .appimage)
     * @param exitCurrentApp 是否在拉起安装器后退出当前应用以避免覆盖安装文件被占用（Windows/macOS必须）
     * @param delayBeforeExitMs 退出前的微小缓冲时间，确保外部独立安装进程已成功派生
     */
    suspend fun launchInstaller(
        file: File,
        exitCurrentApp: Boolean = true,
        delayBeforeExitMs: Long = 500L
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) {
                throw IllegalArgumentException("Installer file does not exist: ${file.absolutePath}")
            }

            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("mac") || os.contains("darwin") -> {
                    // macOS 下调用 open 指令，由系统 LaunchServices / Finder 独立挂载 DMG 或打开 PKG
                    val process = ProcessBuilder("open", file.absolutePath).start()
                    process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                }
                os.contains("win") -> {
                    // Windows 下使用 cmd.exe /c start "" "filepath" 彻底脱离 JVM 进程树
                    ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                }
                else -> {
                    // Linux 或其他
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(file)
                    } else {
                        ProcessBuilder("xdg-open", file.absolutePath).start()
                    }
                }
            }

            if (exitCurrentApp) {
                kotlinx.coroutines.delay(delayBeforeExitMs)
                kotlin.system.exitProcess(0)
            }
            Unit
        }
    }

    /**
     * 在系统的文件管理器中高亮显示下载的安装包
     */
    suspend fun showInFolder(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching Unit

            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("mac") || os.contains("darwin") -> {
                    // macOS 选中并高亮显示文件
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                }
                os.contains("win") -> {
                    // Windows 选中并高亮显示文件
                    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                }
                else -> {
                    val parent = file.parentFile ?: file
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(parent)
                    }
                }
            }
            Unit
        }
    }
}
