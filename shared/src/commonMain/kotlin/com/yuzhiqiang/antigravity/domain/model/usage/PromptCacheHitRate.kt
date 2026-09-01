package com.yuzhiqiang.antigravity.domain.model.usage

/**
 * Prompt Token 缓存命中率：缓存读取 /（普通输入 + 缓存读取 + 缓存创建）。
 *
 * `cacheReadTokens` 与 `uncachedInputTokens` 必须由数据源明确提供；缓存创建未单独提供时，
 * 其 Token 已包含在普通输入中，因此可按 0 处理。
 */
fun calculatePromptCacheHitRatio(
    cacheReadTokens: Long?,
    uncachedInputTokens: Long?,
    cacheWriteTokens: Long? = 0L
): Double? {
    val cacheRead = cacheReadTokens?.takeIf { it >= 0L } ?: return null
    val uncachedInput = uncachedInputTokens?.takeIf { it >= 0L } ?: return null
    if (cacheWriteTokens != null && cacheWriteTokens < 0L) return null
    val cacheWrite = cacheWriteTokens ?: 0L
    val totalPromptTokens = cacheRead.toDouble() + uncachedInput.toDouble() + cacheWrite.toDouble()
    if (totalPromptTokens <= 0.0) return null

    return (cacheRead.toDouble() / totalPromptTokens).coerceIn(0.0, 1.0)
}
