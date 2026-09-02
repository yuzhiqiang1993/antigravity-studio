package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 跨平台更新安装器调度工具
 */
object AppUpdateInstaller {

    /** 用户确认后再次校验，再启动安装器；当前应用保持运行。 */
    suspend fun launchInstaller(
        artifact: VerifiedUpdateArtifact
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            UpdateArtifactVerifier.verifyVerifiedArtifact(artifact)
            val file = artifact.file
            val launched = DesktopPlatformService.launchInstaller(file)
            if (!launched) {
                throw IllegalStateException("Failed to launch installer for file: ${file.absolutePath}")
            }
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
