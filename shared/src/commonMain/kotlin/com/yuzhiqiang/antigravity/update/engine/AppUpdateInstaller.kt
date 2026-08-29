package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            val launched = DesktopPlatformService.launchInstaller(file)
            if (!launched) {
                throw IllegalStateException("Failed to launch installer for file: ${file.absolutePath}")
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
            val revealed = DesktopPlatformService.revealInFileManager(file)
            if (!revealed) {
                throw IllegalStateException("Failed to reveal file in file manager: ${file.absolutePath}")
            }
        }
    }
}
