package com.yuzhiqiang.antigravity.ui.utils

import kotlin.math.round

/**
 * 格式化耗时展示：
 * - < 1000 ms: 显示为 "xxx ms"（如 "362 ms"）
 * - 1s ~ 60s: 换算为秒，保留 1 位有效小数（如 "7.5s", "57.1s", "1s"）
 * - >= 60s: 换算为分秒（如 "1m 5s", "2m"）
 */
fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return "--"
    if (durationMs < 0L) return "0 ms"
    if (durationMs < 1000L) {
        return "$durationMs ms"
    }
    if (durationMs < 60_000L) {
        val seconds = durationMs / 1000.0
        val rounded = round(seconds * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()}s"
        } else {
            "${rounded}s"
        }
    }
    val minutes = durationMs / 60_000L
    val remainingSeconds = (durationMs % 60_000L) / 1000L
    return if (remainingSeconds > 0L) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${minutes}m"
    }
}

/**
 * 将上次刷新时间戳转换为自然语言相对时间描述 (刚刚更新、上次刷新 2 分钟前、上次刷新 3 小时前等)
 */
fun formatLastRefreshedText(lastRefreshedAt: Long, now: Long = System.currentTimeMillis()): String {
    if (lastRefreshedAt <= 0L) return "从未刷新"
    val diffMs = (now - lastRefreshedAt).coerceAtLeast(0L)
    val diffSec = diffMs / 1000L
    if (diffSec < 60L) return "刚刚更新"
    val diffMin = diffSec / 60L
    if (diffMin < 60L) return "上次刷新 $diffMin 分钟前"
    val diffHours = diffMin / 60L
    if (diffHours < 24L) return "上次刷新 $diffHours 小时前"
    val diffDays = diffHours / 24L
    return "上次刷新 $diffDays 天前"
}

/**
 * 格式化当前时分秒 (如 "13:04:50")
 */
fun formatCurrentTimeClock(timestamp: Long = System.currentTimeMillis()): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(date)
}


/**
 * 格式化 Token 数量（添加千分位逗号）：
 * - 例如: 292 -> "292", 16512 -> "16,512", 55532 -> "55,532", 1000000 -> "1,000,000"
 */
fun formatTokens(tokens: Long?): String {
    if (tokens == null) return "—"
    if (tokens < 0L) return tokens.toString()
    val s = tokens.toString()
    val len = s.length
    if (len <= 3) return s
    val sb = StringBuilder()
    val remainder = len % 3
    if (remainder > 0) {
        sb.append(s.substring(0, remainder))
        if (len > remainder) sb.append(',')
    }
    for (i in remainder until len step 3) {
        sb.append(s.substring(i, i + 3))
        if (i + 3 < len) sb.append(',')
    }
    return sb.toString()
}

/**
 * 接口与会话延迟等级（正常、有点慢、非常慢 三级标准）
 */
enum class LatencyTier {
    FAST,      // 正常 (绿色)
    MODERATE,  // 有点慢 (琥珀黄/橙色)
    SLOW       // 非常慢 (红色)
}

/**
 * 评估首字/首包响应时间 (TTFT / Time To First Token) 等级：
 * - <= 2000 ms: FAST (正常，极速流畅)
 * - 2001 ms ~ 5000 ms: MODERATE (有点慢，轻微等待)
 * - > 5000 ms: SLOW (非常慢，延迟较高)
 */
fun getFirstTokenLatencyTier(ttftMs: Long?): LatencyTier? {
    if (ttftMs == null || ttftMs <= 0L) return null
    return when {
        ttftMs <= 2000L -> LatencyTier.FAST
        ttftMs <= 5000L -> LatencyTier.MODERATE
        else -> LatencyTier.SLOW
    }
}

/**
 * 评估总请求耗时 (Duration) 等级：
 * - <= 5000 ms: FAST (正常，快速完成)
 * - 5001 ms ~ 15000 ms: MODERATE (有点慢，常规生成耗时)
 * - > 15000 ms: SLOW (非常慢，超长深度推理或网络阻塞)
 */
fun getDurationLatencyTier(durationMs: Long?): LatencyTier? {
    if (durationMs == null || durationMs <= 0L) return null
    return when {
        durationMs <= 5000L -> LatencyTier.FAST
        durationMs <= 15000L -> LatencyTier.MODERATE
        else -> LatencyTier.SLOW
    }
}

/**
 * 将延迟等级映射为当前主题的语义状态色彩
 */
@androidx.compose.runtime.Composable
fun LatencyTier?.toColor(defaultColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant): androidx.compose.ui.graphics.Color {
    val statusColors = com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
    return when (this) {
        LatencyTier.FAST -> statusColors.success
        LatencyTier.MODERATE -> statusColors.warning
        LatencyTier.SLOW -> statusColors.error
        null -> defaultColor
    }
}

/**
 * 计算上下文缓存命中率（百分比数值 0.0 ~ 100.0%）：
 * - 如果 cacheReadTokens 为空或 <= 0，返回 null；
 * - 分母为实际总输入量：cacheReadTokens + uncachedInputTokens + cacheWriteTokens；
 * - 命中率 = (cacheReadTokens / totalPromptTokens) * 100.0。
 */
fun calculateCacheHitRate(
    cacheReadTokens: Long?,
    uncachedInputTokens: Long?,
    cacheWriteTokens: Long? = 0L
): Double? {
    if (cacheReadTokens == null || cacheReadTokens <= 0L) return null
    val uncached = uncachedInputTokens?.coerceAtLeast(0L) ?: 0L
    val write = cacheWriteTokens?.coerceAtLeast(0L) ?: 0L
    val totalPrompt = cacheReadTokens + uncached + write
    if (totalPrompt <= 0L) return null
    val percentage = (cacheReadTokens.toDouble() / totalPrompt.toDouble()) * 100.0
    return percentage.coerceIn(0.0, 100.0)
}

/**
 * 格式化缓存命中率展示（如 "82.5%", "100%", "0%"）
 */
fun formatHitRate(rate: Double?): String {
    if (rate == null) return "—"
    val rounded = kotlin.math.round(rate * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}%"
    } else {
        "${rounded}%"
    }
}

/**
 * 获取缓存命中率的健康色彩展示：
 * - >= 60%: 绿色（极高命中，大幅提速降本）
 * - >= 20%: 琥珀黄色（中等命中）
 * - > 0%: 次要高亮色（低命中）
 * - 其他/null: 默认中性色
 */
@androidx.compose.runtime.Composable
fun getCacheHitRateColor(rate: Double?, defaultColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant): androidx.compose.ui.graphics.Color {
    val statusColors = com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
    return when {
        rate == null -> defaultColor
        rate >= 60.0 -> statusColors.success
        rate >= 20.0 -> statusColors.warning
        rate > 0.0 -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
        else -> defaultColor
    }
}

