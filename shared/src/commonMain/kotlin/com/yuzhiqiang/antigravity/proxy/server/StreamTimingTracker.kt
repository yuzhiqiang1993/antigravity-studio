package com.yuzhiqiang.antigravity.proxy.server

internal const val STREAM_STALL_THRESHOLD_MS = 2_000L

internal data class StreamTimingSnapshot(
    val firstByteMs: Long?,
    val firstTokenMs: Long?,
    val lastTokenMs: Long?,
    val maxChunkGapMs: Long?,
    val stallCount: Int,
    val stallDurationMs: Long?
)

/** 统一采集 BYOK 与官方透传流的首包、有效内容间隔和卡顿指标。 */
internal class StreamTimingTracker(
    private val requestStartTimeMs: Long,
    private val stallThresholdMs: Long = STREAM_STALL_THRESHOLD_MS
) {
    private var firstByteMs: Long? = null
    private var firstTokenMs: Long? = null
    private var lastTokenMs: Long? = null
    private var lastMeaningfulAtMs: Long? = null
    private var maxChunkGapMs: Long? = null
    private var meaningfulEventCount = 0
    private var stallCount = 0
    private var stallDurationMs = 0L

    fun recordFirstByte(atMs: Long) {
        if (firstByteMs == null) {
            firstByteMs = elapsed(atMs)
        }
    }

    /** 返回当前内容耗时及它是否为首个有效内容事件。 */
    fun recordMeaningfulContent(atMs: Long): Pair<Long, Boolean> {
        recordFirstByte(atMs)
        val elapsedMs = elapsed(atMs)
        val isFirst = firstTokenMs == null
        if (isFirst) {
            firstTokenMs = elapsedMs
        } else {
            val gapMs = maxOf(0L, atMs - (lastMeaningfulAtMs ?: atMs))
            maxChunkGapMs = maxOf(maxChunkGapMs ?: 0L, gapMs)
            if (gapMs >= stallThresholdMs) {
                stallCount++
                stallDurationMs += gapMs
            }
        }
        meaningfulEventCount++
        lastMeaningfulAtMs = atMs
        lastTokenMs = elapsedMs
        return elapsedMs to isFirst
    }

    fun snapshot(): StreamTimingSnapshot = StreamTimingSnapshot(
        firstByteMs = firstByteMs,
        firstTokenMs = firstTokenMs,
        lastTokenMs = lastTokenMs,
        maxChunkGapMs = maxChunkGapMs.takeIf { meaningfulEventCount >= 2 },
        stallCount = stallCount,
        stallDurationMs = stallDurationMs.takeIf { meaningfulEventCount >= 2 }
    )

    private fun elapsed(atMs: Long): Long = maxOf(0L, atMs - requestStartTimeMs)
}
