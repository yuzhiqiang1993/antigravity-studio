package com.yuzhiqiang.antigravity.update.model

import java.io.File

/**
 * 客户端下载更新的状态模型
 */
sealed class AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState()

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressRatio: Float,
        val speedBytesPerSec: Long
    ) : AppUpdateDownloadState()

    data class Completed(
        val artifact: com.yuzhiqiang.antigravity.update.engine.VerifiedUpdateArtifact
    ) : AppUpdateDownloadState() {
        val targetFile: File get() = artifact.file
    }

    data class Failed(
        val error: String
    ) : AppUpdateDownloadState()
}
