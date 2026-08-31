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

    @Test
    fun testCalculateActivityStatistics() {
        val logs = listOf(
            ActivityLog(
                id = "1",
                method = "POST",
                path = "/v1/chat",
                statusCode = 200,
                durationMs = 3000L,
                firstTokenMs = 1000L,
                tokensPerSecond = 60.0,
                inputTokens = 1000L,
                cacheReadTokens = 800L,
                cacheWriteTokens = 0L,
                outputTokens = 120L
            ),
            ActivityLog(
                id = "2",
                method = "POST",
                path = "/v1/chat",
                statusCode = 500,
                durationMs = 1000L,
                firstTokenMs = null,
                tokensPerSecond = null,
                inputTokens = 500L,
                cacheReadTokens = 0L,
                cacheWriteTokens = 0L,
                outputTokens = 0L
            ),
            ActivityLog(
                id = "3",
                method = "POST",
                path = "/v1/chat",
                statusCode = 200,
                durationMs = 5000L,
                firstTokenMs = 2000L,
                tokensPerSecond = 40.0,
                inputTokens = 1000L,
                cacheReadTokens = 200L,
                cacheWriteTokens = 0L,
                outputTokens = 120L
            )
        )

        val stats = calculateActivityStatistics(logs)
        assertEquals(1, stats.failedCount)
        assertEquals(3000L, stats.averageDuration) // (3000 + 1000 + 5000) / 3 = 3000
        assertEquals(1500L, stats.averageFirstTokenMs) // (1000 + 2000) / 2 = 1500
        assertEquals(50.0, stats.averageTps) // (60 + 40) / 2 = 50
        assertNotNull(stats.overallCacheHitRate)
        // 总输入: (800+1000) + 500 + (200+1000) = 3500; 命中: 1000; 1000/3500 * 100 = 28.57%
        val hitRate = stats.overallCacheHitRate ?: 0.0
        assertEquals(28.57, kotlin.math.round(hitRate * 100) / 100.0)
    }

    @Test
    fun testCalculateActivityStatisticsEmpty() {
        val stats = calculateActivityStatistics(emptyList())
        assertEquals(0, stats.failedCount)
        assertEquals(0L, stats.averageDuration)
        assertEquals(0L, stats.averageFirstTokenMs)
        assertNull(stats.averageTps)
        assertNull(stats.overallCacheHitRate)
    }
}

