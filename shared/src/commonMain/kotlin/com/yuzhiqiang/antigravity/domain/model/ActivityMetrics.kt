package com.yuzhiqiang.antigravity.domain.model

internal const val MIN_STREAM_GENERATION_DURATION_MS = 100L
internal const val MAX_REASONABLE_TPS = 2_000.0

internal data class SpeedMetrics(
    val generationDurationMs: Long?,
    val tokensPerSecond: Double?,
    val timePerOutputTokenMs: Double?
)

internal fun calculateSpeedMetrics(
    outputTokens: Long?,
    firstTokenMs: Long?,
    lastTokenMs: Long?,
    durationMs: Long? = null
): SpeedMetrics {
    if (outputTokens == null || outputTokens <= 0L) {
        val rawGenMs = when {
            firstTokenMs != null && lastTokenMs != null && lastTokenMs >= firstTokenMs -> lastTokenMs - firstTokenMs
            firstTokenMs != null && durationMs != null && durationMs >= firstTokenMs -> durationMs - firstTokenMs
            durationMs != null && durationMs > 0L -> durationMs
            else -> null
        }
        return SpeedMetrics(rawGenMs, null, null)
    }

    val rawGenMs = when {
        firstTokenMs != null && lastTokenMs != null &&
                lastTokenMs - firstTokenMs >= MIN_STREAM_GENERATION_DURATION_MS -> lastTokenMs - firstTokenMs

        firstTokenMs != null && durationMs != null &&
                durationMs - firstTokenMs >= MIN_STREAM_GENERATION_DURATION_MS -> durationMs - firstTokenMs

        firstTokenMs != null && lastTokenMs == firstTokenMs &&
                (durationMs == null || firstTokenMs >= durationMs) -> 0L

        durationMs != null && durationMs > 0L -> durationMs
        firstTokenMs != null && lastTokenMs != null && lastTokenMs > firstTokenMs -> lastTokenMs - firstTokenMs
        else -> null
    }

    if (rawGenMs == null || rawGenMs <= 0L) return SpeedMetrics(rawGenMs, null, null)
    val rawTps = outputTokens.toDouble() / (rawGenMs / 1000.0)
    val fallbackTps = durationMs
        ?.takeIf { it >= MIN_STREAM_GENERATION_DURATION_MS }
        ?.let { outputTokens.toDouble() / (it / 1000.0) }
        ?.takeIf { it <= MAX_REASONABLE_TPS }
    val finalTps = if (rawTps <= MAX_REASONABLE_TPS) rawTps else fallbackTps
    val finalGenMs = if (finalTps != null && finalTps != rawTps && durationMs != null) durationMs else rawGenMs
    val divisor = if (outputTokens > 1L) outputTokens - 1L else 1L
    val tpot = finalGenMs.toDouble() / divisor.toDouble()
    return SpeedMetrics(finalGenMs, finalTps, tpot)
}
