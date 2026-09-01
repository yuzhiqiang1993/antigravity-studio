package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.usage.DailyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.HourlyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioGlassCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 每日/每小时消耗走势：与插件 V2 dashboard 体验 1:1 对齐。
 * 鼠标悬浮时展示浮动 Tooltip 气泡与半透明指示条，不挤占或撑大图表下方版面。
 */
@Composable
fun UsageTrendChart(
    dailyBuckets: List<DailyUsageBucket>,
    hourlyBuckets: List<HourlyUsageBucket> = emptyList(),
    timeRange: UsageTimeRange = UsageTimeRange.CALENDAR_TODAY,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val colors = usageTokenColors()
    val isHourlyTrend = timeRange == UsageTimeRange.ROLLING_24H || timeRange == UsageTimeRange.CALENDAR_TODAY
    val sourceBuckets = remember(dailyBuckets, hourlyBuckets, timeRange) {
        if (isHourlyTrend) {
            toHourlyTrendBuckets(hourlyBuckets, timeRange)
        } else {
            dailyBuckets
        }
    }
    val buckets = remember(sourceBuckets, isHourlyTrend) {
        if (isHourlyTrend) sourceBuckets else downsampleDailyBuckets(sourceBuckets)
    }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = UsageVisualTokens.cardPadding,
                    vertical = UsageVisualTokens.cardVerticalPadding
                ),
            verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.cardGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHourlyTrend) s.usageHourlyTrendChartTitle else s.usageTrendChartTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = UsageVisualTokens.Typography.cardTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = s.usageTokensCount(UsageNumberFormatter.formatTokens(buckets.sumOf { it.totalTokens })),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = UsageVisualTokens.Typography.legendText
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UsageVisualTokens.Chart.legendSpacing)
            ) {
                ChartLegendIndicator(s.usageTokenPromptInput, colors.input)
                ChartLegendIndicator(s.usageTokenCacheRead, colors.cacheRead)
                ChartLegendIndicator(s.usageTokenCacheWrite, colors.cacheWrite)
                ChartLegendIndicator(s.usageTokenModelOutput, colors.output)
                ChartLegendIndicator(s.usageTokenThinking, colors.reasoning)
                ChartLegendIndicator(s.usageTokenUnattributed, colors.unattributed)
            }

            if (buckets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.usageTrendChartEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxTokens = remember(buckets) {
                    maxOf(1L, buckets.maxOfOrNull { it.totalTokens } ?: 1L)
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(UsageVisualTokens.Chart.containerHeight)
                        .pointerInput(buckets) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val position = event.changes.firstOrNull()?.position
                                    when (event.type) {
                                        PointerEventType.Exit -> {
                                            if (selectedIndex != null) selectedIndex = null
                                        }

                                        PointerEventType.Move, PointerEventType.Enter -> {
                                            if (position != null) {
                                                val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
                                                val plotEnd =
                                                    size.width - UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
                                                val plotWidth = (plotEnd - plotStart).coerceAtLeast(1f)
                                                val fraction = ((position.x - plotStart) / plotWidth).coerceIn(0f, 1f)
                                                val newIndex =
                                                    (fraction * (buckets.size - 1).coerceAtLeast(0)).roundToInt()
                                                if (selectedIndex != newIndex) {
                                                    selectedIndex = newIndex
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val containerWidth = maxWidth
                    SmoothUsagePlot(
                        buckets = buckets,
                        maxTokens = maxTokens,
                        colors = colors,
                        selectedIndex = selectedIndex,
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedIndex?.let { index ->
                        buckets.getOrNull(index)?.let { bucket ->
                            val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding
                            val plotEnd = containerWidth - UsageVisualTokens.Chart.plotHorizontalPadding
                            val plotWidth = plotEnd - plotStart
                            val xStep = if (buckets.size <= 1) 0.dp else plotWidth / (buckets.size - 1)
                            val xPos = if (buckets.size <= 1) (plotStart + plotEnd) / 2 else plotStart + xStep * index
                            FloatingTrendTooltip(
                                bucket = bucket,
                                colors = colors,
                                anchorX = xPos,
                                containerWidth = containerWidth
                            )
                        }
                    }
                }
                UsageAxisLabels(buckets = buckets, selectedIndex = selectedIndex)
            }
        }
    }
}

@Composable
private fun SmoothUsagePlot(
    buckets: List<DailyUsageBucket>,
    maxTokens: Long,
    colors: UsageTokenColors,
    selectedIndex: Int?,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    val highlightSliceColor = UsageVisualTokens.Tooltip.sliceHighlightColor

    Canvas(modifier = modifier) {
        val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
        val plotEnd = size.width - UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
        val plotTop = UsageVisualTokens.Chart.plotTop.toPx()
        val plotBottom = size.height - UsageVisualTokens.Chart.plotBottomPadding.toPx()
        val plotWidth = (plotEnd - plotStart).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val xStep = if (buckets.size <= 1) 0f else plotWidth / (buckets.size - 1)
        val sliceWidth = (plotWidth / maxOf(1, buckets.size)).coerceAtLeast(4f)

        // 1. 水平参考线
        for (line in 0..3) {
            val y = plotTop + plotHeight * line / 3f
            drawLine(
                color = gridColor,
                start = Offset(plotStart, y),
                end = Offset(plotEnd, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 2. 坐标点
        val points = buckets.mapIndexed { index, bucket ->
            val x = if (buckets.size <= 1) (plotStart + plotEnd) / 2f else plotStart + xStep * index
            val y = plotBottom - (bucket.totalTokens.toFloat() / maxTokens.toFloat()) * plotHeight
            Offset(x, y.coerceIn(plotTop, plotBottom))
        }

        if (points.isNotEmpty()) {
            // 3. 悬浮选中的浅色时间柱（100% 对齐插件 .deep-heatmap-slice:hover）
            selectedIndex?.let { index ->
                points.getOrNull(index)?.let { point ->
                    drawRect(
                        color = highlightSliceColor,
                        topLeft = Offset(point.x - sliceWidth / 2f, plotTop),
                        size = Size(sliceWidth, plotHeight)
                    )
                }
            }

            // 4. 贝塞尔渐变面积与曲线
            val linePath = buildSmoothPath(points)
            val areaPath = Path().apply {
                moveTo(points.first().x, plotBottom)
                lineTo(points.first().x, points.first().y)
                appendSmoothSegments(points)
                lineTo(points.last().x, plotBottom)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.output.copy(alpha = 0.26f), colors.output.copy(alpha = 0.01f)),
                    startY = plotTop,
                    endY = plotBottom
                )
            )
            drawPath(
                path = linePath,
                color = colors.output,
                style = Stroke(
                    width = UsageVisualTokens.Chart.strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 5. 悬浮点高亮圆点
            selectedIndex?.let { index ->
                points.getOrNull(index)?.let { point ->
                    drawCircle(
                        colors.output.copy(alpha = 0.22f),
                        radius = UsageVisualTokens.Chart.dotHaloRadius.toPx(),
                        center = point
                    )
                    drawCircle(Color.White, radius = UsageVisualTokens.Chart.dotRadius.toPx(), center = point)
                    drawCircle(
                        colors.output,
                        radius = UsageVisualTokens.Chart.dotRadius.toPx(),
                        center = point,
                        style = Stroke(UsageVisualTokens.Chart.dotStrokeWidth.toPx())
                    )
                }
            }
        }
    }
}

/**
 * 悬浮气泡 Tooltip：位于更上方并带有向下指示小箭头，与插件模式 100% 对齐。
 */
@Composable
private fun FloatingTrendTooltip(
    bucket: DailyUsageBucket,
    colors: UsageTokenColors,
    anchorX: Dp,
    containerWidth: Dp,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tooltipWidth = UsageVisualTokens.Tooltip.width
    val leftOffset =
        (anchorX - tooltipWidth / 2).coerceIn(8.dp, (containerWidth - tooltipWidth - 8.dp).coerceAtLeast(8.dp))
    val costText = usageBucketCostLabel(
        bucket.costUsd,
        bucket.pricingMatched,
        bucket.costLowerBound,
        s
    )
    val cacheTotal = bucket.cacheRead + bucket.cacheWrite
    val arrowWidth = UsageVisualTokens.Tooltip.arrowWidth
    val arrowHeight = UsageVisualTokens.Tooltip.arrowHeight
    val arrowOffsetX = (anchorX - leftOffset - arrowWidth / 2).coerceIn(8.dp, tooltipWidth - 8.dp - arrowWidth)

    Column(
        modifier = modifier
            .offset(x = leftOffset, y = 2.dp)
            .width(tooltipWidth)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = UsageVisualTokens.Tooltip.borderWidth,
                    color = UsageVisualTokens.Tooltip.borderColor,
                    shape = RoundedCornerShape(UsageVisualTokens.Tooltip.cornerRadius)
                ),
            shape = RoundedCornerShape(UsageVisualTokens.Tooltip.cornerRadius),
            color = UsageVisualTokens.Tooltip.backgroundColor,
            shadowElevation = UsageVisualTokens.Tooltip.elevation
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = UsageVisualTokens.Tooltip.paddingHorizontal,
                    vertical = UsageVisualTokens.Tooltip.paddingVertical
                ),
                verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.Tooltip.rowSpacing)
            ) {
                // 时间标题
                Text(
                    text = formatDateLabel(bucket.date),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = UsageVisualTokens.Typography.tooltipTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                // Token 使用量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${s.usageKpiTotalTokensTitle}:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.tooltipLabel),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = UsageNumberFormatter.formatTokens(bucket.totalTokens),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.tooltipValue,
                            fontWeight = FontWeight.Bold
                        ),
                        color = UsageVisualTokens.Tooltip.tokenHighlightColor
                    )
                }

                // 调用次数
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${s.usageKpiCallsTitle}:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.tooltipLabel),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = UsageNumberFormatter.formatCount(bucket.calls),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.tooltipLabel,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }

                if (bucket.totalTokens > 0L) {
                    // 输入
                    TooltipLine(
                        label = s.usageTokenPromptInput,
                        value = UsageNumberFormatter.formatTokens(bucket.input)
                    )

                    // 缓存总计
                    TooltipLine(label = s.usageTokenCacheTotal, value = UsageNumberFormatter.formatTokens(cacheTotal))

                    // 缓存读取 (缩进)
                    TooltipLine(
                        label = "  ${s.usageTokenCacheRead}",
                        value = UsageNumberFormatter.formatTokens(bucket.cacheRead),
                        subdued = true
                    )

                    // 缓存写入 (缩进)
                    TooltipLine(
                        label = "  ${s.usageTokenCacheWrite}",
                        value = UsageNumberFormatter.formatTokens(bucket.cacheWrite),
                        subdued = true
                    )

                    // 输出
                    TooltipLine(
                        label = s.usageTokenModelOutput,
                        value = UsageNumberFormatter.formatTokens(bucket.output)
                    )

                    // 思考推理 (if > 0)
                    if (bucket.reasoning > 0L) {
                        TooltipLine(
                            label = s.usageTokenThinking,
                            value = UsageNumberFormatter.formatTokens(bucket.reasoning)
                        )
                    }

                    if (bucket.unattributed > 0L) {
                        TooltipLine(
                            label = s.usageTokenUnattributed,
                            value = UsageNumberFormatter.formatTokens(bucket.unattributed)
                        )
                    }
                }

                // 费用
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${s.usageKpiCostTitle}:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.tooltipLabel),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = costText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.tooltipLabel,
                            fontWeight = FontWeight.Bold
                        ),
                        color = UsageVisualTokens.Tooltip.costHighlightColor
                    )
                }
            }
        }

        // 气泡底部向下小三角形箭头
        Canvas(
            modifier = Modifier
                .offset(x = arrowOffsetX, y = (-1).dp)
                .size(width = arrowWidth, height = arrowHeight)
        ) {
            val arrowPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path = arrowPath, color = UsageVisualTokens.Tooltip.backgroundColor)
        }
    }
}

@Composable
private fun TooltipLine(
    label: String,
    value: String,
    subdued: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.tooltipLabel),
            color = if (subdued) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = UsageVisualTokens.Typography.tooltipLabel,
                fontWeight = if (subdued) FontWeight.Normal else FontWeight.Medium
            ),
            color = if (subdued) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.95f)
        )
    }
}

private fun buildSmoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    appendSmoothSegments(points)
}

private fun Path.appendSmoothSegments(points: List<Offset>) {
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        val middleX = (start.x + end.x) / 2f
        cubicTo(middleX, start.y, middleX, end.y, end.x, end.y)
    }
}

@Composable
private fun UsageAxisLabels(
    buckets: List<DailyUsageBucket>,
    selectedIndex: Int?
) {
    if (buckets.isEmpty()) return

    val targetLabelCount = when {
        buckets.size <= 8 -> buckets.size
        buckets.size <= 14 -> 7
        buckets.size <= 24 -> 8
        buckets.size <= 31 -> 8
        else -> 10
    }
    val labelStep = maxOf(1, buckets.size / targetLabelCount)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UsageVisualTokens.Chart.axisTopPadding)
            .height(UsageVisualTokens.Chart.axisHeight)
    ) {
        val containerWidth = maxWidth
        val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding
        val plotEnd = containerWidth - UsageVisualTokens.Chart.plotHorizontalPadding
        val plotWidth = plotEnd - plotStart
        val xStep = if (buckets.size <= 1) 0.dp else plotWidth / (buckets.size - 1)
        val itemApproxWidth = UsageVisualTokens.Chart.axisItemWidth

        val minIndexGap = maxOf(2, labelStep)
        buckets.forEachIndexed { index, bucket ->
            val isFirst = index == 0
            val isLast = index == buckets.lastIndex
            val show = isFirst || isLast || (index % labelStep == 0 && (buckets.lastIndex - index) >= minIndexGap && index >= minIndexGap)
            if (show) {
                val isSelected = selectedIndex == index
                val xPos = if (buckets.size <= 1) (plotStart + plotEnd) / 2 else plotStart + xStep * index
                val leftOffset = when {
                    isFirst -> plotStart
                    isLast -> (plotEnd - itemApproxWidth).coerceAtLeast(plotStart)
                    else -> (xPos - itemApproxWidth / 2).coerceIn(
                        plotStart,
                        (plotEnd - itemApproxWidth).coerceAtLeast(plotStart)
                    )
                }
                val alignment = when {
                    isFirst -> Alignment.Start
                    isLast -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                Column(
                    modifier = Modifier
                        .offset(x = leftOffset)
                        .width(itemApproxWidth),
                    horizontalAlignment = alignment,
                    verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.Chart.axisSpacing)
                ) {
                    Text(
                        text = formatDateLabel(bucket.date),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.axisTime,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = UsageNumberFormatter.formatTokens(bucket.totalTokens),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.axisTokens,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendIndicator(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(UsageVisualTokens.Chart.legendDotSize)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = UsageVisualTokens.Typography.legendText),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDateLabel(date: String): String {
    val range = date.split('~')
    return if (range.size == 2) {
        "${UsageNumberFormatter.formatShortDate(range.first())}~${UsageNumberFormatter.formatShortDate(range.last())}"
    } else {
        UsageNumberFormatter.formatShortDate(date)
    }
}

internal fun toHourlyTrendBuckets(
    buckets: List<HourlyUsageBucket>,
    timeRange: UsageTimeRange
): List<DailyUsageBucket> {
    val currentHour = java.time.LocalTime.now().hour
    val sorted = buckets.sortedBy { it.hour }
    val ordered = if (timeRange == UsageTimeRange.ROLLING_24H) {
        sorted.sortedBy { (it.hour - (currentHour + 1) + 24) % 24 }
    } else {
        sorted.filter { it.hour <= currentHour }
    }
    return ordered
        .asSequence()
        .map { bucket ->
            DailyUsageBucket(
                date = "${bucket.hour.toString().padStart(2, '0')}:00",
                input = bucket.input,
                output = bucket.output,
                cacheRead = bucket.cacheRead,
                cacheWrite = bucket.cacheWrite,
                reasoning = bucket.reasoning,
                unattributed = bucket.unattributed,
                calls = bucket.calls,
                costUsd = bucket.costUsd,
                pricingMatched = bucket.pricingMatched,
                costLowerBound = bucket.costLowerBound
            )
        }
        .toList()
}

internal fun downsampleDailyBuckets(buckets: List<DailyUsageBucket>): List<DailyUsageBucket> {
    val sorted = buckets.sortedBy { it.date }
    if (sorted.size <= 60) return sorted
    val bucketSize = ceil(sorted.size / 60.0).toInt()
    return sorted.chunked(bucketSize).map { chunk ->
        DailyUsageBucket(
            date = if (chunk.size == 1) chunk.first().date else "${chunk.first().date}~${chunk.last().date}",
            input = chunk.sumOf { it.input },
            output = chunk.sumOf { it.output },
            cacheRead = chunk.sumOf { it.cacheRead },
            cacheWrite = chunk.sumOf { it.cacheWrite },
            reasoning = chunk.sumOf { it.reasoning },
            unattributed = chunk.sumOf { it.unattributed },
            calls = chunk.sumOf { it.calls },
            costUsd = chunk.sumOf { it.costUsd },
            savingsUsd = chunk.sumOf { it.savingsUsd },
            pricingMatched = chunk.all { it.pricingMatched },
            costLowerBound = chunk.any { it.costLowerBound }
        )
    }
}
