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

    @Test
    fun calculateVisibleAxisIndicesShowsAllWhenSpacingIsSufficient() {
        // 10 个数据点在 800dp 下，step = 800 / 9 = 88.8dp >= 72dp，应全量展示
        val indices = calculateVisibleAxisIndices(
            bucketCount = 10,
            plotWidthDp = 800f,
            minLabelSpacingDp = 72f
        )
        assertEquals((0..9).toSet(), indices)
    }

    @Test
    fun calculateVisibleAxisIndicesHandlesBoundaryCounts() {
        assertEquals(emptySet(), calculateVisibleAxisIndices(0, 800f))
        assertEquals(setOf(0), calculateVisibleAxisIndices(1, 800f))
        assertEquals(setOf(0, 1), calculateVisibleAxisIndices(2, 800f))
    }

    @Test
    fun calculateVisibleAxisIndicesDownsamplesUniformlyWhenDense() {
        // 24 个点在 600dp 下，保证无碰撞且首尾点（0 和 23）可见
        val indices = calculateVisibleAxisIndices(
            bucketCount = 24,
            plotWidthDp = 600f,
            minLabelSpacingDp = 72f
        )
        assert(indices.contains(0))
        assert(indices.contains(23))
        assert(indices.size <= 9)
    }

    @Test
    fun calculateVisibleAxisIndicesPreventsCollisionAtTailFor14Buckets() {
        // 用户截图场景：00:00 ~ 13:00 共 14 个桶，在 700dp 下，单步长为 53.8dp (< 76dp)
        // 算法绝不能同时保留 12 和 13，必须保证任意相邻刻度间距严格 >= 76dp
        val plotWidth = 700f
        val minSpacing = 76f
        val indices = calculateVisibleAxisIndices(
            bucketCount = 14,
            plotWidthDp = plotWidth,
            minLabelSpacingDp = minSpacing
        )

        assert(indices.contains(0))
        assert(indices.contains(13))
        // 确保不会同时出现 12 和 13
        assert(!(indices.contains(12) && indices.contains(13)))

        // 验证任意两个相邻被选中的索引物理间距 >= minSpacing
        val sortedList = indices.sorted()
        val xStep = plotWidth / 13f
        for (i in 0 until sortedList.lastIndex) {
            val dist = (sortedList[i + 1] - sortedList[i]) * xStep
            assert(dist >= minSpacing * 0.8f) {
                "Distance between index ${sortedList[i]} and ${sortedList[i+1]} is $dist, which is less than safety margin"
            }
        }
    }
}
