package com.yuzhiqiang.antigravity.update.model

/**
 * 版本更新检测状态封装
 */
sealed class UpdateState {
    data object Idle : UpdateState()

    data class Checking(
        val isManual: Boolean = false
    ) : UpdateState()

    data class Available(
        val release: ReleaseInfo,
        val currentVersion: String,
        val isManual: Boolean = false
    ) : UpdateState()

    data class UpToDate(
        val currentVersion: String,
        val lastCheckedTimestamp: Long,
        val isManual: Boolean = false
    ) : UpdateState()

    data class Error(
        val message: String,
        val isManual: Boolean = false
    ) : UpdateState()
}
