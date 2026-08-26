package com.yuzhiqiang.antigravity.domain.model.quota

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.currentStrings

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

    fun displayName(s: Strings = currentStrings()): String = when (this) {
        FIVE_HOUR -> s.quotaWindowFiveHour
        WEEKLY -> s.quotaWindowWeekly
        DAILY -> s.quotaWindowDaily
        UNKNOWN -> s.quotaWindowGeneral
    }

    val displayName: String
        get() = displayName(currentStrings())
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
     * 格式化紧凑倒计时文本 (如 "2小时 12分钟" / "2h 12m" 或 "2天 2小时" / "2d 2h")
     * 当剩余时间 <= 0 时返回 null，由调用方展示“即将重置”状态
     */
    fun formattedCountdown(s: Strings = currentStrings()): String? {
        val seconds = secondsUntilReset() ?: return null
        if (seconds <= 0L) return null
        val totalMinutes = seconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        val days = hours / 24L
        val remHours = hours % 24L

        return when {
            days > 0L -> if (remHours > 0L) s.formatCountdownDaysHours(days, remHours) else s.formatCountdownDays(days)
            hours > 0L -> if (minutes > 0L) s.formatCountdownHoursMinutes(hours, minutes) else s.formatCountdownHours(hours)
            minutes > 0L -> s.formatCountdownMinutes(minutes)
            else -> s.formatCountdownLessThanMinute
        }
    }

    /**
     * 获取多语言友好的槽位显示标题
     */
    fun displayTitle(s: Strings = currentStrings()): String = when (window) {
        QuotaWindow.FIVE_HOUR -> s.accountsQuotaFiveHour
        QuotaWindow.WEEKLY -> s.accountsQuotaWeekly
        QuotaWindow.DAILY -> s.quotaWindowDaily
        QuotaWindow.UNKNOWN -> displayName.ifBlank { s.quotaWindowGeneral }
    }

    /**
     * 对齐 Cockpit 插件的标准自然语言配额描述文案
     */
    fun naturalLanguageDescription(s: Strings = currentStrings()): String {
        if (percentage >= 100) {
            return when (window) {
                QuotaWindow.FIVE_HOUR -> s.quotaDescFiveHourFull
                QuotaWindow.WEEKLY -> s.quotaDescWeeklyFull
                else -> s.quotaDescGeneralFull
            }
        }

        val countdown = formattedCountdown(s)
        val timeStr = countdown ?: s.accountsQuotaResetSoon
        return when (window) {
            QuotaWindow.FIVE_HOUR -> s.quotaDescFiveHourResetting(timeStr)
            QuotaWindow.WEEKLY -> s.quotaDescWeeklyResetting(timeStr)
            else -> s.quotaDescGeneralResetting(timeStr)
        }
    }
}

