package com.yuzhiqiang.antigravity.ui.screens

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ActivityScreenDataTest {

    @Test
    fun testCalculatePercentiles() {
        val longs = listOf(100L, 200L, 300L, 400L, 500L)
        assertEquals(300L, calculatePercentile(longs, 50.0))
        assertEquals(500L, calculatePercentile(longs, 95.0))
        assertEquals(0L, calculatePercentile(emptyList(), 50.0))

        val doubles = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        assertEquals(30.0, calculateDoublePercentile(doubles, 50.0))
        assertEquals(50.0, calculateDoublePercentile(doubles, 95.0))
        assertNull(calculateDoublePercentile(emptyList(), 50.0))
    }

    @Test
    fun testCalculateModelLatencyStatsWithSpeedAndStability() {
        val logs = listOf(
            ActivityLog(
                id = "1",
                method = "POST",
                path = "/v1/chat",
                modelId = "claude-3-7-sonnet",
                statusCode = 200,
                durationMs = 4000L,
                firstByteMs = 600L,
                firstTokenMs = 1000L,
                lastTokenMs = 3500L,
                generationDurationMs = 2500L,
                tokensPerSecond = 50.0,
                timePerOutputTokenMs = 20.0,
                maxChunkGapMs = 300L,
                stallCount = 0,
                stallDurationMs = 0L,
                queueWaitMs = 20L
            ),
            ActivityLog(
                id = "2",
                method = "POST",
                path = "/v1/chat",
                modelId = "claude-3-7-sonnet",
                statusCode = 200,
                durationMs = 6000L,
                firstByteMs = 900L,
                firstTokenMs = 2000L,
                lastTokenMs = 5000L,
                generationDurationMs = 3000L,
                tokensPerSecond = 30.0,
                timePerOutputTokenMs = 33.3,
                maxChunkGapMs = 2500L,
                stallCount = 1,
                stallDurationMs = 2500L,
                queueWaitMs = 80L
            )
        )

        val stats = calculateModelLatencyStats(logs)
        assertEquals(1, stats.size)
        val stat = stats.first()
        assertEquals("claude-3-7-sonnet", stat.modelId)
        assertEquals(2, stat.sampleCount)
        assertEquals(600L, stat.p50FirstByteMs)
        assertEquals(900L, stat.p95FirstByteMs)
        assertEquals(1500L, stat.averageFirstTokenMs)
        assertEquals(1000L, stat.minFirstTokenMs)
        assertEquals(2000L, stat.maxFirstTokenMs)
        assertEquals(1000L, stat.p50FirstTokenMs)
        assertEquals(2000L, stat.p95FirstTokenMs)
        assertEquals(5000L, stat.averageDurationMs)
        assertEquals(4000L, stat.minDurationMs)
        assertEquals(6000L, stat.maxDurationMs)
        assertEquals(40.0, stat.averageTps)
        assertEquals(30.0, stat.minTps)
        assertEquals(50.0, stat.maxTps)
        assertEquals(1, stat.totalStallCount)
        assertEquals(2500L, stat.p95MaxChunkGapMs)
        assertEquals(50L, stat.averageQueueWaitMs)
    }
}
