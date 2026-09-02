package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.usage.DailyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.HourlyUsageBucket
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import kotlin.math.roundToLong
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioGlassCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import androidx.compose.ui.graphics.luminance
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(buckets) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Exit -> {
                                if (selectedIndex != null) {
                                    selectedIndex = null
                                }
                            }

                            PointerEventType.Move, PointerEventType.Enter -> {
                                if (position != null && buckets.isNotEmpty()) {
                                    if (position.x in 0f..size.width.toFloat() && position.y in 0f..size.height.toFloat()) {
                                        val cardPadding = 20.dp.toPx()
                                        val plotStart =
                                            cardPadding + UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
                                        val plotEnd =
                                            size.width - cardPadding - UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
                                        val plotWidth = (plotEnd - plotStart).coerceAtLeast(1f)
                                        val fraction = ((position.x - plotStart) / plotWidth).coerceIn(0f, 1f)
                                        val newIndex =
                                            (fraction * (buckets.size - 1).coerceAtLeast(0)).roundToInt()
                                        if (selectedIndex != newIndex) {
                                            selectedIndex = newIndex
                                        }
                                    } else {
                                        selectedIndex = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val containerWidth = maxWidth

        StudioGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
            borderColor = StudioGlassTokens.cleanBorderColor(isDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHourlyTrend) s.usageHourlyTrendChartTitle else s.usageTrendChartTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(UsageVisualTokens.Chart.containerHeight)
                    ) {
                        SmoothUsagePlot(
                            buckets = buckets,
                            maxTokens = maxTokens,
                            colors = colors,
                            selectedIndex = selectedIndex,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    UsageAxisLabels(buckets = buckets, selectedIndex = selectedIndex)
                }
            }
        }

        // 顶层最高层级浮动 Tooltip（置于 StudioGlassCard 外部同级，拥有最高绘制层级，不被卡片裁剪，杜绝闪烁）
        if (buckets.isNotEmpty()) {
            selectedIndex?.let { index ->
                buckets.getOrNull(index)?.let { bucket ->
                    val cardPadding = 20.dp
                    val plotStart = cardPadding + UsageVisualTokens.Chart.plotHorizontalPadding
                    val plotEnd = containerWidth - cardPadding - UsageVisualTokens.Chart.plotHorizontalPadding
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
    // 提取数据内容指纹：只在时间范围、点数量或数据总和实质变动时触发动效
    val dataFingerprint = remember(buckets, maxTokens) {
        "${buckets.size}_${maxTokens}_${buckets.firstOrNull()?.date}_${buckets.lastOrNull()?.date}_${buckets.sumOf { it.totalTokens }}"
    }
    val animProgress = remember { Animatable(1f) }
    val lastFingerprint = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dataFingerprint) {
        if (lastFingerprint.value != null && lastFingerprint.value != dataFingerprint) {
            animProgress.snapTo(0.2f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        }
        lastFingerprint.value = dataFingerprint
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val yLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
    val highlightSliceColor = UsageVisualTokens.Tooltip.sliceHighlightColor
    val textMeasurer = rememberTextMeasurer()
    val yAxisTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = yLabelColor
    )
    val nodeBadgeStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = colors.output
    )
    val peakBadgeStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = colors.output
    )

    Canvas(modifier = modifier) {
        val currentProgress = animProgress.value
        val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
        val plotEnd = size.width - UsageVisualTokens.Chart.plotHorizontalPadding.toPx()
        val plotTop = 12.dp.toPx()
        val plotBottom = size.height - UsageVisualTokens.Chart.plotBottomPadding.toPx()
        val plotWidth = (plotEnd - plotStart).coerceAtLeast(1f)
        val chartTop = plotTop + 28.dp.toPx()
        val chartHeight = (plotBottom - chartTop).coerceAtLeast(1f)
        val xStep = if (buckets.size <= 1) 0f else plotWidth / (buckets.size - 1)
        val sliceWidth = (plotWidth / maxOf(1, buckets.size)).coerceAtLeast(4f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)

        for (line in 0..3) {
            val y = chartTop + chartHeight * line / 3f
            val isBaseline = line == 3
            drawLine(
                color = if (isBaseline) baselineColor else gridColor,
                start = Offset(plotStart, y),
                end = Offset(plotEnd, y),
                strokeWidth = if (isBaseline) 1.dp.toPx() else 0.8.dp.toPx(),
                pathEffect = if (isBaseline) null else dashEffect
            )

            if (line > 0) {
                val lineTokens = when (line) {
                    1 -> (maxTokens * 2.0 / 3.0).roundToLong()
                    2 -> (maxTokens * 1.0 / 3.0).roundToLong()
                    else -> 0L
                }
                val labelText = UsageNumberFormatter.formatTokens(lineTokens)
                val measuredText = textMeasurer.measure(
                    text = labelText,
                    style = yAxisTextStyle
                )
                val labelY = y - measuredText.size.height - 2.dp.toPx()
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(plotStart + 4.dp.toPx(), labelY)
                )
            }
        }

        val points = buckets.mapIndexed { index, bucket ->
            val x = if (buckets.size <= 1) (plotStart + plotEnd) / 2f else plotStart + xStep * index
            val rawRatio =
                if (maxTokens > 0L) (bucket.totalTokens.toFloat() / maxTokens.toFloat()).coerceIn(0f, 1f) else 0f
            val ratio = rawRatio * currentProgress
            val y = plotBottom - ratio * chartHeight
            Offset(x, y)
        }

        if (points.isNotEmpty()) {
            selectedIndex?.let { index ->
                points.getOrNull(index)?.let { point ->
                    drawRect(
                        color = highlightSliceColor,
                        topLeft = Offset(point.x - sliceWidth / 2f, plotTop),
                        size = Size(sliceWidth, plotBottom - plotTop)
                    )
                    drawLine(
                        color = colors.output.copy(alpha = 0.5f),
                        start = Offset(point.x, plotTop),
                        end = Offset(point.x, plotBottom),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
                    )
                }
            }

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
                    colors = listOf(
                        colors.output.copy(alpha = 0.22f * currentProgress),
                        colors.output.copy(alpha = 0.01f * currentProgress)
                    ),
                    startY = chartTop,
                    endY = plotBottom
                )
            )
            drawPath(
                path = linePath,
                color = colors.output.copy(alpha = currentProgress.coerceIn(0.2f, 1f)),
                style = Stroke(
                    width = UsageVisualTokens.Chart.strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            val peakIndex = if (maxTokens > 0L) {
                buckets.indices.maxByOrNull { buckets[it].totalTokens }?.takeIf { buckets[it].totalTokens > 0L }
            } else null

            val dotAlpha = if (currentProgress > 0.3f) ((currentProgress - 0.3f) / 0.7f).coerceIn(0f, 1f) else 0f
            if (dotAlpha > 0f) {
                buckets.forEachIndexed { idx, bucket ->
                    if (bucket.totalTokens > 0L) {
                        val pt = points[idx]
                        val isPeak = idx == peakIndex
                        val isSelected = idx == selectedIndex

                        if (isPeak || isSelected) {
                            drawCircle(
                                colors.output.copy(alpha = 0.25f * dotAlpha),
                                radius = 5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(Color.White.copy(alpha = dotAlpha), radius = 3.2.dp.toPx(), center = pt)
                            drawCircle(
                                colors.output.copy(alpha = dotAlpha),
                                radius = 3.2.dp.toPx(),
                                center = pt,
                                style = Stroke(1.4.dp.toPx())
                            )
                        } else {
                            drawCircle(Color.White.copy(alpha = dotAlpha), radius = 2.4.dp.toPx(), center = pt)
                            drawCircle(
                                colors.output.copy(alpha = dotAlpha),
                                radius = 2.4.dp.toPx(),
                                center = pt,
                                style = Stroke(1.2.dp.toPx())
                            )
                        }
                    }
                }
            }

            val labelAlpha = if (currentProgress > 0.6f) ((currentProgress - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
            if (labelAlpha > 0f) {
                val activeIndices = buckets.indices
                    .filter { buckets[it].totalTokens > 0L }
                    .sortedByDescending { buckets[it].totalTokens }

                val occupiedRanges = mutableListOf<ClosedFloatingPointRange<Float>>()

                for (idx in activeIndices) {
                    val pt = points[idx]
                    val tokens = buckets[idx].totalTokens
                    val isPeak = idx == peakIndex
                    val badgeText = UsageNumberFormatter.formatTokens(tokens)
                    val measured = textMeasurer.measure(
                        text = badgeText,
                        style = if (isPeak) peakBadgeStyle.copy(color = colors.output.copy(alpha = labelAlpha)) else nodeBadgeStyle.copy(color = colors.output.copy(alpha = labelAlpha))
                    )
                    val paddingH = if (isPeak) 6.dp.toPx() else 4.5.dp.toPx()
                    val paddingV = if (isPeak) 2.dp.toPx() else 1.5.dp.toPx()
                    val badgeW = measured.size.width + paddingH * 2
                    val badgeH = measured.size.height + paddingV * 2

                    val badgeLeft = (pt.x - badgeW / 2f).coerceIn(plotStart, plotEnd - badgeW)
                    val badgeRight = badgeLeft + badgeW
                    val clearance = 4.dp.toPx() // 节点间的防重叠安全距离
                    val candidateRange = (badgeLeft - clearance)..(badgeRight + clearance)

                    val collides = occupiedRanges.any { range ->
                        candidateRange.start < range.endInclusive && candidateRange.endInclusive > range.start
                    }

                    if (!collides) {
                        occupiedRanges.add(candidateRange)
                        val badgeTop = (pt.y - badgeH - 7.dp.toPx()).coerceAtLeast(plotTop)

                        val cornerRadius = if (isPeak) 5.dp.toPx() else 4.dp.toPx()

                        // 1. 底层高不透明度实体底色（彻底遮盖背后穿过的曲线、面积渐变与网格线）
                        drawRoundRect(
                            color = surfaceColor.copy(alpha = 0.96f * labelAlpha),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )

                        // 2. 中层品牌色微弱发光填充
                        val tintAlpha = (if (isPeak) 0.18f else 0.10f) * labelAlpha
                        drawRoundRect(
                            color = colors.output.copy(alpha = tintAlpha),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )

                        // 3. 顶层药丸精细边框
                        val borderAlpha = (if (isPeak) 0.65f else 0.40f) * labelAlpha
                        drawRoundRect(
                            color = colors.output.copy(alpha = borderAlpha),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                            style = Stroke(if (isPeak) 1.2.dp.toPx() else 0.9.dp.toPx())
                        )

                        // 4. 徽标文字（最高层级，字迹清晰锐利）
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(badgeLeft + paddingH, badgeTop + paddingV)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶层悬浮气泡 Tooltip：
 * 位于 StudioGlassCard 外部，拥有最高层级并可超出卡片边界覆盖上方其他模块。
 * 绝对不拦截底层鼠标事件，彻底消除闪烁。
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
        (anchorX - tooltipWidth / 2).coerceIn(12.dp, (containerWidth - tooltipWidth - 12.dp).coerceAtLeast(12.dp))
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
            .offset(x = leftOffset, y = 8.dp)
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
            shadowElevation = 16.dp
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
                    TooltipLine(
                        label = s.usageTokenCacheTotal,
                        value = UsageNumberFormatter.formatTokens(cacheTotal)
                    )

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UsageVisualTokens.Chart.axisTopPadding)
            .height(UsageVisualTokens.Chart.axisHeight)
    ) {
        val containerWidth = maxWidth
        val plotStart = UsageVisualTokens.Chart.plotHorizontalPadding
        val plotEnd = containerWidth - UsageVisualTokens.Chart.plotHorizontalPadding
        val plotWidth = (plotEnd - plotStart).coerceAtLeast(1.dp)
        val xStep = if (buckets.size <= 1) 0.dp else plotWidth / (buckets.size - 1)
        val itemApproxWidth = 68.dp

        val visibleIndices = remember(buckets.size, plotWidth) {
            calculateVisibleAxisIndices(
                bucketCount = buckets.size,
                plotWidthDp = plotWidth.value,
                minLabelSpacingDp = 72f
            )
        }

        buckets.forEachIndexed { index, bucket ->
            if (index in visibleIndices) {
                val isSelected = selectedIndex == index
                val xPos = if (buckets.size <= 1) (plotStart + plotEnd) / 2 else plotStart + xStep * index
                val leftOffset = (xPos - itemApproxWidth / 2).coerceIn(
                    0.dp,
                    (containerWidth - itemApproxWidth).coerceAtLeast(0.dp)
                )

                Column(
                    modifier = Modifier
                        .offset(x = leftOffset)
                        .width(itemApproxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 计算 X 轴可见刻度索引集合：
 * 1. 当数据点物理间距 >= 最小标签间距时，全量展示所有刻度点，避免遗漏。
 * 2. 当点数较多空间不足时，采用等间距均匀步长抽样，保证刻度分布均匀且不重叠。
 */
internal fun calculateVisibleAxisIndices(
    bucketCount: Int,
    plotWidthDp: Float,
    minLabelSpacingDp: Float = 72f
): Set<Int> {
    if (bucketCount <= 0) return emptySet()
    if (bucketCount == 1) return setOf(0)
    if (bucketCount == 2) return setOf(0, 1)

    val safeWidth = plotWidthDp.coerceAtLeast(1f)
    val xStepDp = safeWidth / (bucketCount - 1)

    if (xStepDp >= minLabelSpacingDp) {
        return (0 until bucketCount).toSet()
    }

    val maxAllowedLabels = (safeWidth / minLabelSpacingDp).toInt().coerceIn(2, bucketCount)
    val step = ceil((bucketCount - 1).toFloat() / (maxAllowedLabels - 1)).toInt().coerceAtLeast(1)

    val indices = mutableSetOf<Int>()
    var currentIndex = 0
    while (currentIndex < bucketCount - 1) {
        indices.add(currentIndex)
        currentIndex += step
    }

    val lastIndex = bucketCount - 1
    val prevIndex = indices.maxOrNull()
    if (prevIndex != null && (lastIndex - prevIndex) * xStepDp < minLabelSpacingDp * 0.7f && prevIndex != 0) {
        indices.remove(prevIndex)
    }
    indices.add(lastIndex)

    return indices
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
