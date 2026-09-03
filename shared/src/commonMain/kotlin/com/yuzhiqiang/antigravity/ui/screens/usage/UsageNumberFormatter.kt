package com.yuzhiqiang.antigravity.ui.screens.usage

import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.round

/**
 * 用量页面唯一的数值展示入口。
 *
 * 中文规则与插件 shared/helpers.ts 的 fmtBig 保持一致：小于 1 万显示原数，
 * 1 万以上使用“万”，1 亿以上使用“亿”，最多保留两位小数并去掉尾零。
 */
object UsageNumberFormatter {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US)

    fun formatTokens(value: Long, language: AppLanguage = I18nManager.currentLanguage): String {
        val safeValue = value.coerceAtLeast(0L)
        return if (language == AppLanguage.ZH_CN) {
            when {
                safeValue >= 100_000_000L -> "${trimFixed(safeValue / 100_000_000.0, 2)} 亿"
                safeValue >= 10_000L -> "${trimFixed(safeValue / 10_000.0, 2)} 万"
                else -> safeValue.toString()
            }
        } else {
            when {
                safeValue >= 1_000_000_000L -> "${fixed(safeValue / 1_000_000_000.0, 2)}B"
                safeValue >= 1_000_000L -> "${fixed(safeValue / 1_000_000.0, 1)}M"
                safeValue >= 1_000L -> "${fixed(safeValue / 1_000.0, 1)}K"
                else -> safeValue.toString()
            }
        }
    }

    /** 格式化人民币金额（不带货币符号） */
    fun formatCnyAmount(value: Double): String {
        val safeValue = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when {
            safeValue < 0.01 -> "0"
            safeValue < 1.0 -> fixed(safeValue, 2)
            safeValue < 10.0 -> fixed(safeValue, 1)
            else -> formatCount(round(safeValue).toLong())
        }
    }

    /** 调用次数等整数使用本地化千位分组，但不使用 Token 缩写。 */
    fun formatCount(value: Long): String =
        DecimalFormat("#,##0", decimalSymbols).format(value.coerceAtLeast(0L))

    /** 百分比默认保留一位小数，与插件的 cache-rate 展示精度一致。 */
    fun formatPercent(value: Double, decimals: Int = 1): String {
        val safeValue = if (value.isFinite()) value else 0.0
        return fixed(safeValue.coerceAtLeast(0.0), decimals)
    }

    /** 返回不带美元符号的金额文本，由 i18n 文案负责放置货币符号。 */
    fun formatUsdAmount(value: Double): String {
        val safeValue = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when {
            safeValue < 0.01 -> "0"
            safeValue < 1.0 -> fixed(safeValue, 2)
            safeValue < 10.0 -> fixed(safeValue, 1)
            else -> formatCount(round(safeValue).toLong())
        }
    }

    fun formatShortDate(isoDate: String, language: AppLanguage = I18nManager.currentLanguage): String {
        val parts = isoDate.split('-')
        if (parts.size < 3) return isoDate
        val month = parts[1].toIntOrNull() ?: return isoDate
        val day = parts[2].take(2).toIntOrNull() ?: return isoDate
        return if (language == AppLanguage.ZH_CN) {
            "${month}月${day}日"
        } else {
            val months = arrayOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            "${months.getOrNull(month - 1) ?: month} $day"
        }
    }

    private fun fixed(value: Double, decimals: Int): String {
        val pattern = if (decimals <= 0) "0" else "0.${"0".repeat(decimals)}"
        return DecimalFormat(pattern, decimalSymbols).format(value)
    }

    private fun trimFixed(value: Double, decimals: Int): String =
        fixed(value, decimals).trimEnd('0').trimEnd('.')
}
