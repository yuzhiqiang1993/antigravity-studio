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
        val targetFile: File
    ) : AppUpdateDownloadState()

    data class Failed(
        val error: String
    ) : AppUpdateDownloadState()
}
