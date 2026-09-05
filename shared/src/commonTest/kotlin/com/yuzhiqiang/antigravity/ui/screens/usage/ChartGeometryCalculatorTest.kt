package com.yuzhiqiang.antigravity.ui.screens.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartGeometryCalculatorTest {

    @Test
    fun testVisibleAxisIndicesBoundaryCounts() {
        assertEquals(emptySet(), ChartGeometryCalculator.calculateVisibleAxisIndices(0, 800f))
        assertEquals(setOf(0), ChartGeometryCalculator.calculateVisibleAxisIndices(1, 800f))
        assertEquals(setOf(0, 1), ChartGeometryCalculator.calculateVisibleAxisIndices(2, 800f))
    }

    @Test
    fun testVisibleAxisIndicesShowsAllWhenWidthIsSufficient() {
        val indices = ChartGeometryCalculator.calculateVisibleAxisIndices(
            bucketCount = 5,
            plotWidthDp = 500f,
            minLabelSpacingDp = 70f
        )
        // 5 个点跨度 4，单步 125dp >= 70dp，应全量展示
        assertEquals(setOf(0, 1, 2, 3, 4), indices)
    }

    @Test
    fun testVisibleAxisIndicesAnchorsFirstAndLastWhenDense() {
        val indices = ChartGeometryCalculator.calculateVisibleAxisIndices(
            bucketCount = 30,
            plotWidthDp = 600f,
            minLabelSpacingDp = 76f
        )
        assertTrue(indices.contains(0), "首节点 0 必须保留")
        assertTrue(indices.contains(29), "尾节点 29 必须保留")
        assertTrue(indices.size in 3..9, "采样后点数应在合理视觉范围内")
    }

    @Test
    fun testCalculateCubicBezierMidpointControl() {
        val start = ChartGeometryCalculator.Point(0f, 100f)
        val end = ChartGeometryCalculator.Point(100f, 200f)
        val segment = ChartGeometryCalculator.calculateCubicBezier(start, end)

        assertEquals(0f, segment.start.x)
        assertEquals(100f, segment.start.y)
        assertEquals(50f, segment.control1.x, "控制点 1 横坐标必须居中")
        assertEquals(100f, segment.control1.y, "控制点 1 纵坐标保持水平起始切线")
        assertEquals(50f, segment.control2.x, "控制点 2 横坐标必须居中")
        assertEquals(200f, segment.control2.y, "控制点 2 纵坐标保持水平终止切线")
        assertEquals(100f, segment.end.x)
        assertEquals(200f, segment.end.y)
    }

    @Test
    fun testProjectValuesToPointsSingleAndEmpty() {
        assertTrue(ChartGeometryCalculator.projectValuesToPoints(emptyList(), 400f, 200f).isEmpty())

        val singlePoint = ChartGeometryCalculator.projectValuesToPoints(listOf(50.0), 400f, 200f).single()
        assertEquals(200f, singlePoint.x, "单点横向居中")
        assertEquals(100f, singlePoint.y, "单点纵向居中")
    }

    @Test
    fun testProjectValuesToPointsScalingAndInversion() {
        val values = listOf(0.0, 50.0, 100.0)
        val points = ChartGeometryCalculator.projectValuesToPoints(
            values = values,
            plotWidth = 200f,
            plotHeight = 100f,
            paddingHorizontal = 0f,
            paddingVertical = 0f
        )

        assertEquals(3, points.size)
        assertEquals(0f, points[0].x)
        assertEquals(100f, points[0].y, "最小值 0.0 在屏幕底部 (y = 100)")
        assertEquals(100f, points[1].x)
        assertEquals(50f, points[1].y, "中间值 50.0 在屏幕中间 (y = 50)")
        assertEquals(200f, points[2].x)
        assertEquals(0f, points[2].y, "最大值 100.0 在屏幕顶部 (y = 0)")
    }
}
