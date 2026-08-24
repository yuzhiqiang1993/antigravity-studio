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

