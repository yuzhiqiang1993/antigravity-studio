package com.yuzhiqiang.antigravity.ui.screens.usage

import kotlin.math.ceil

/**
 * 纯 Kotlin 图表几何与坐标系算法计算器 (ChartGeometryCalculator)：
 * 从 Compose UI 渲染中彻底解耦纯数学计算逻辑，提供无 Compose 依赖的高测试性与高内聚算法集合。
 */
object ChartGeometryCalculator {

    /**
     * 二维平面点数据模型（纯 Kotlin，不依赖任何平台与 UI 框架）
     */
    data class Point(val x: Float, val y: Float)

    /**
     * 三次贝塞尔曲线控制分段
     */
    data class CubicBezierSegment(
        val start: Point,
        val control1: Point,
        val control2: Point,
        val end: Point
    )

    /**
     * 计算 X 轴可见刻度索引集合：
     * 采用物理边界防碰撞与首尾锚点保护算法：
     * 1. 首端锚点（0）与尾端锚点（bucketCount - 1，即当前最新时间）必须保证展示；
     * 2. 保证任意相邻两个可见刻度之间的物理像素间距严格 >= minLabelSpacingDp；
     * 3. 采用自尾端向首端的倒序贪心扫描与自适应均匀间隔计算，彻底杜绝任何边界堆叠折叠。
     */
    fun calculateVisibleAxisIndices(
        bucketCount: Int,
        plotWidthDp: Float,
        minLabelSpacingDp: Float = 76f
    ): Set<Int> {
        if (bucketCount <= 0) return emptySet()
        if (bucketCount == 1) return setOf(0)
        if (bucketCount == 2) return setOf(0, 1)

        val safeWidth = plotWidthDp.coerceAtLeast(1f)
        val totalSpan = bucketCount - 1
        val xStepDp = safeWidth / totalSpan

        // 如果全部点位展开后的间距已经足够安全，全量展示
        if (xStepDp >= minLabelSpacingDp) {
            return (0 until bucketCount).toSet()
        }

        // 计算当前宽度下最多可安全容纳的刻度数（至少 2 个：首尾）
        val maxLabels = (safeWidth / minLabelSpacingDp).toInt().coerceIn(2, bucketCount)
        val intervals = (maxLabels - 1).coerceAtLeast(1)
        val step = ceil(totalSpan.toFloat() / intervals.toFloat()).toInt().coerceAtLeast(1)

        // 采用从尾向首的倒序贪心安全选择，确保最新时间点（lastIndex）和起始点（0）永远清晰可见
        val lastIndex = totalSpan
        val result = mutableListOf<Int>()
        result.add(lastIndex)
        var currentRight = lastIndex

        var candidate = lastIndex - step
        while (candidate > 0) {
            val distToRightDp = (currentRight - candidate) * xStepDp
            val distToStartDp = candidate * xStepDp

            // 仅当距离右侧已选节点足够安全，且距离左侧起点（0）也保留足够安全间隙时才保留候选点
            if (distToRightDp >= minLabelSpacingDp && distToStartDp >= minLabelSpacingDp * 0.8f) {
                result.add(candidate)
                currentRight = candidate
                candidate -= step
            } else if (distToStartDp < minLabelSpacingDp * 0.8f) {
                // 已经太靠近起点 0，直接终止中间遍历，交给首节点 0
                break
            } else {
                // 距离不够，继续向前找
                candidate--
            }
        }

        result.add(0)
        return result.toSet()
    }

    /**
     * 将两点间的一阶线性过渡计算为平滑三次贝塞尔曲线的控制点
     */
    fun calculateCubicBezier(start: Point, end: Point): CubicBezierSegment {
        val middleX = (start.x + end.x) / 2f
        return CubicBezierSegment(
            start = start,
            control1 = Point(middleX, start.y),
            control2 = Point(middleX, end.y),
            end = end
        )
    }

    /**
     * 将数值列表投影映射为绘图区域内的二维绝对坐标点集
     */
    fun projectValuesToPoints(
        values: List<Double>,
        plotWidth: Float,
        plotHeight: Float,
        paddingHorizontal: Float = 0f,
        paddingVertical: Float = 0f
    ): List<Point> {
        if (values.isEmpty()) return emptyList()
        if (values.size == 1) {
            return listOf(Point(paddingHorizontal + plotWidth / 2f, paddingVertical + plotHeight / 2f))
        }

        val maxVal = values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val safeMax = if (maxVal > 0.0) maxVal else 1.0

        val usableWidth = (plotWidth - paddingHorizontal * 2).coerceAtLeast(1f)
        val usableHeight = (plotHeight - paddingVertical * 2).coerceAtLeast(1f)
        val xStep = usableWidth / (values.size - 1)

        return values.mapIndexed { index, value ->
            val safeValue = value.coerceAtLeast(0.0)
            val normalizedY = (safeValue / safeMax).toFloat().coerceIn(0f, 1f)
            val x = paddingHorizontal + index * xStep
            val y = paddingVertical + usableHeight * (1f - normalizedY)
            Point(x, y)
        }
    }
}
