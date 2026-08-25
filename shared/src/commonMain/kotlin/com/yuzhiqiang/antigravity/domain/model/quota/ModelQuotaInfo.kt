package com.yuzhiqiang.antigravity.domain.model.quota

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QuotaWindow {
    @SerialName("5h")
    FIVE_HOUR,

    @SerialName("weekly")
    WEEKLY,

    @SerialName("daily")
    DAILY,

    @SerialName("unknown")
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            FIVE_HOUR -> "5 小时额度"
            WEEKLY -> "周度额度"
            DAILY -> "每日额度"
            UNKNOWN -> "周期额度"
        }
}

/**
 * 单个模型或模型族的配额槽位
 */
@Serializable
data class ModelQuotaInfo(
    val id: String,
    val displayName: String,
    val family: String? = null, // claude, gemini, gpt
    val window: QuotaWindow = QuotaWindow.FIVE_HOUR,
    /**
     * 剩余额度比例 (0.0 ~ 1.0)
     */
    val remainingFraction: Double = 1.0,
    /**
     * 剩余额度百分比 (0 ~ 100)
     */
    val percentage: Int = (remainingFraction * 100).toInt().coerceIn(0, 100),
    /**
     * 额度重置时间 ISO 字符串
     */
    val resetTimeIso: String? = null,
    /**
     * 重置时间戳 (秒级)
     */
    val resetTimeEpochSeconds: Long? = null,
    val isExhausted: Boolean = remainingFraction <= 0.0
) {
    /**
     * 计算距重置剩余秒数
     */
    fun secondsUntilReset(): Long? {
        val resetSec = resetTimeEpochSeconds ?: return null
        val nowSec = System.currentTimeMillis() / 1000L
        return (resetSec - nowSec).coerceAtLeast(0L)
    }

    /**
     * 格式化紧凑倒计时文本 (如 "2小时 12分钟" 或 "2天 2小时")
     */
    fun formattedCountdown(): String? {
        val seconds = secondsUntilReset() ?: return null
        if (seconds <= 0) return "已重置"
        val totalMinutes = seconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val days = hours / 24
        val remHours = hours % 24

        return when {
            days > 0 -> if (remHours > 0) "${days}天 ${remHours}小时" else "${days}天"
            hours > 0 -> "${hours}小时 ${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "< 1分钟"
        }
    }

    /**
     * 对齐 Cockpit 插件的标准自然语言配额描述文案
     */
    fun naturalLanguageDescription(): String {
        if (percentage >= 100) {
            return when (window) {
                QuotaWindow.FIVE_HOUR -> "您的五小时额度目前处于完全可用状态。"
                QuotaWindow.WEEKLY -> "您的周额度目前处于完全可用状态。"
                else -> "您的额度目前处于完全可用状态。"
            }
        }

        val countdown = formattedCountdown()
        val timeStr = countdown ?: "稍后"
        return when (window) {
            QuotaWindow.FIVE_HOUR -> "您已消耗部分五小时额度，将在 $timeStr 后完全重置。"
            QuotaWindow.WEEKLY -> "您已消耗部分周额度，将在 $timeStr 后完全重置。"
            else -> "额度将在 $timeStr 后完全重置。"
        }
    }
}

