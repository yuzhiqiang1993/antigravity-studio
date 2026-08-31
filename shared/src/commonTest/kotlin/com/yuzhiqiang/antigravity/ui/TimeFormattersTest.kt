package com.yuzhiqiang.antigravity.ui

import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatTpot
import com.yuzhiqiang.antigravity.ui.utils.formatTps
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormattersTest {

    @Test
    fun testFormatDurationNullAndNegative() {
        assertEquals("--", formatDuration(null))
        assertEquals("0 ms", formatDuration(-10L))
    }

    @Test
    fun testFormatDurationMilliseconds() {
        assertEquals("0 ms", formatDuration(0L))
        assertEquals("362 ms", formatDuration(362L))
        assertEquals("999 ms", formatDuration(999L))
    }

    @Test
    fun testFormatDurationSeconds() {
        assertEquals("1s", formatDuration(1000L))
        assertEquals("1.4s", formatDuration(1360L))
        assertEquals("6.4s", formatDuration(6352L))
        assertEquals("7.5s", formatDuration(7505L))
        assertEquals("57.1s", formatDuration(57130L))
        assertEquals("59.9s", formatDuration(59900L))
    }

    @Test
    fun testFormatDurationMinutes() {
        assertEquals("1m", formatDuration(60000L))
        assertEquals("1m 5s", formatDuration(65000L))
        assertEquals("2m", formatDuration(120000L))
        assertEquals("2m 15s", formatDuration(135000L))
        assertEquals("10m", formatDuration(600000L))
    }

    @Test
    fun testFormatTokens() {
        assertEquals("—", com.yuzhiqiang.antigravity.ui.utils.formatTokens(null))
        assertEquals("0", com.yuzhiqiang.antigravity.ui.utils.formatTokens(0L))
        assertEquals("292", com.yuzhiqiang.antigravity.ui.utils.formatTokens(292L))
        assertEquals("999", com.yuzhiqiang.antigravity.ui.utils.formatTokens(999L))
        assertEquals("1,000", com.yuzhiqiang.antigravity.ui.utils.formatTokens(1000L))
        assertEquals("16,512", com.yuzhiqiang.antigravity.ui.utils.formatTokens(16512L))
        assertEquals("55,532", com.yuzhiqiang.antigravity.ui.utils.formatTokens(55532L))
        assertEquals("89,988", com.yuzhiqiang.antigravity.ui.utils.formatTokens(89988L))
        assertEquals("1,000,000", com.yuzhiqiang.antigravity.ui.utils.formatTokens(1000000L))
    }

    @Test
    fun testLatencyTiers() {
        // 首字响应延迟 (TTFT)
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(null))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(0L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.FAST, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(450L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.FAST, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(2000L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.MODERATE, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(2001L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.MODERATE, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(5000L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.SLOW, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(5001L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.SLOW, com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier(12000L))

        // 总耗时 (Duration)
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(null))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(0L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.FAST, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(1200L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.FAST, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(5000L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.MODERATE, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(5001L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.MODERATE, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(15000L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.SLOW, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(15001L))
        assertEquals(com.yuzhiqiang.antigravity.ui.utils.LatencyTier.SLOW, com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier(35000L))
    }

    @Test
    fun testTpsAndTpotFormatting() {
        assertEquals("—", formatTps(null))
        assertEquals("—", formatTps(0.0))
        assertEquals("45 t/s", formatTps(45.0))
        assertEquals("42.5 t/s", formatTps(42.5))
        assertEquals("120 t/s", formatTps(120.0))

        assertEquals("—", formatTpot(null))
        assertEquals("—", formatTpot(0.0))
        assertEquals("22 ms/t", formatTpot(22.0))
        assertEquals("23.5 ms/t", formatTpot(23.5))
    }

    @Test
    fun testCacheHitRate() {
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(null, 1000L))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(0L, 1000L))

        // 1. 无未命中输入，全命中 (100%)
        assertEquals(100.0, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(1000L, 0L))
        assertEquals(100.0, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(1000L, null))
        assertEquals("100%", com.yuzhiqiang.antigravity.ui.utils.formatHitRate(100.0))

        // 2. 50% 命中 (500 缓存读取 + 500 未缓存输入 = 1000 总 Prompt)
        assertEquals(50.0, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(500L, 500L))
        assertEquals("50%", com.yuzhiqiang.antigravity.ui.utils.formatHitRate(50.0))

        // 3. 82.5% 命中 (8250 缓存读取 + 1750 未缓存输入 = 10000 总 Prompt)
        assertEquals(82.5, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(8250L, 1750L))
        assertEquals("82.5%", com.yuzhiqiang.antigravity.ui.utils.formatHitRate(82.5))

        // 4. Anthropic 混合场景：6000 缓存读取 + 1000 未缓存输入 + 3000 缓存写入 = 10000 总 Prompt (60%)
        assertEquals(60.0, com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate(6000L, 1000L, 3000L))
        assertEquals("60%", com.yuzhiqiang.antigravity.ui.utils.formatHitRate(60.0))

        assertEquals("—", com.yuzhiqiang.antigravity.ui.utils.formatHitRate(null))
    }

    @Test
    fun testSplitFunctions() {
        assertEquals("2.7" to "s", com.yuzhiqiang.antigravity.ui.utils.splitDuration(2700L))
        assertEquals("360" to "ms", com.yuzhiqiang.antigravity.ui.utils.splitDuration(360L))
        assertEquals("1m 5" to "s", com.yuzhiqiang.antigravity.ui.utils.splitDuration(65000L))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.splitDuration(null))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.splitDuration(0L))

        assertEquals("488.7" to "t/s", com.yuzhiqiang.antigravity.ui.utils.splitTps(488.7))
        assertEquals("120" to "t/s", com.yuzhiqiang.antigravity.ui.utils.splitTps(120.0))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.splitTps(null))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.splitTps(0.0))

        assertEquals("74.6" to "%", com.yuzhiqiang.antigravity.ui.utils.splitHitRate(74.6))
        assertEquals("100" to "%", com.yuzhiqiang.antigravity.ui.utils.splitHitRate(100.0))
        assertEquals(null, com.yuzhiqiang.antigravity.ui.utils.splitHitRate(null))
    }
}

