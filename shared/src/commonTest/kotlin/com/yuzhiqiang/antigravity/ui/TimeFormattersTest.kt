package com.yuzhiqiang.antigravity.ui

import com.yuzhiqiang.antigravity.ui.utils.formatDuration
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
}
