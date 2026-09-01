package com.yuzhiqiang.antigravity.ui.screens.usage

import com.yuzhiqiang.antigravity.domain.model.usage.DailyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.HourlyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageTrendChartTest {

    @Test
    fun hourlyTrendMappingKeepsUnattributedTokens() {
        val bucket = toHourlyTrendBuckets(
            buckets = listOf(HourlyUsageBucket(hour = 12, input = 10, unattributed = 5)),
            timeRange = UsageTimeRange.ROLLING_24H
        ).single()

        assertEquals(5L, bucket.unattributed)
        assertEquals(15L, bucket.totalTokens)
    }

    @Test
    fun dailyDownsamplingKeepsUnattributedTokensAndTotal() {
        val source = (1..61).map { index ->
            DailyUsageBucket(
                date = index.toString().padStart(2, '0'),
                input = 2,
                unattributed = 1
            )
        }

        val sampled = downsampleDailyBuckets(source)

        assertEquals(61L, sampled.sumOf { it.unattributed })
        assertEquals(183L, sampled.sumOf { it.totalTokens })
    }
}
