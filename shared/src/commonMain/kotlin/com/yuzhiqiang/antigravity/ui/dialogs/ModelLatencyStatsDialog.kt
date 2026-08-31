package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.EmptyStateView
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.screens.ModelLatencyStat
import com.yuzhiqiang.antigravity.ui.screens.rememberModelLatencyStats
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel
import com.yuzhiqiang.antigravity.ui.utils.ModelDisplayNameResolver
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatTps
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

/**
 * 各模型耗时与速度统计弹窗：
 * - 复用 StudioDialogSurface 与 AppTokens 尺寸令牌，遵循应用统一的 Material Design 3 弹窗规范
 * - 顶部展示模型数量、首字均值、速率均值、总耗时均值 4 项核心汇总指标
 * - 列表各模型采用三指标并列卡片（首字响应 TTFT、输出速率 TPS、会话总耗时 Duration），纵向层次分明，杜绝文字挤压折行
 * - 优先展示友好的 displayName，并在需要时展示原始 modelId
 * - 文案通俗易懂，过滤非必要的复杂工程指标
 * - 底部标准 Action 栏统一消费 s.commonClose
 */
@Composable
fun ModelLatencyStatsDialog(
    logs: List<ActivityLog>,
    config: AppConfig? = null,
    officialModels: List<OfficialCatalogModel>? = null,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stats = rememberModelLatencyStats(logs)

    val validStats = stats.filter { it.sampleCount > 0 || it.completedCount > 0 }
    val totalTtftSamples = validStats.sumOf { it.sampleCount }
    val totalCompletedRequests = validStats.sumOf { it.completedCount }
    val activeModelCount = validStats.size
    // 与 calculateModelLatencyStats 保持相同的模型归属口径，避免无模型日志只进入均值分子。
    val modelLatencyLogs = logs.filter { (it.modelId ?: it.requestedModelId)?.isNotBlank() == true }

    val overallAvgTtft = if (totalTtftSamples > 0) {
        val totalMs = modelLatencyLogs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }.sum()
        totalMs / totalTtftSamples
    } else {
        0L
    }

    val tpsLogs = modelLatencyLogs.mapNotNull { it.tokensPerSecond?.takeIf { tps -> tps > 0.0 } }
    val overallAvgTps = if (tpsLogs.isNotEmpty()) tpsLogs.average() else null

    val overallAvgDuration = if (totalCompletedRequests > 0) {
        val totalDurationMs = modelLatencyLogs.filter { !it.isPending && it.durationMs > 0 }.sumOf { it.durationMs }
        totalDurationMs / totalCompletedRequests
    } else {
        0L
    }

    val maxAvgTtft = validStats.maxOfOrNull { it.averageFirstTokenMs }?.coerceAtLeast(1L) ?: 1L
    val maxAvgTps = validStats.mapNotNull { it.averageTps }.maxOrNull()?.coerceAtLeast(1.0) ?: 100.0
    val maxAvgDuration = validStats.maxOfOrNull { it.averageDurationMs }?.coerceAtLeast(1L) ?: 1L

    Dialog(onDismissRequest = onDismiss) {
        StudioDialogSurface(
            modifier = Modifier
                .width(AppTokens.Size.doctorDialogWidth)
                .heightIn(min = 400.dp, max = AppTokens.Size.doctorDialogMaxHeight)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = s.activityModelLatencyDialogTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.activityModelLatencyDialogSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 2. 汇总概览卡片行（4 列并排，呼吸感更好）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LatencySummaryCard(
                        label = s.activityModelLatencyActiveModels,
                        value = activeModelCount.toString(),
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    val overallTtftTier = getFirstTokenLatencyTier(overallAvgTtft)
                    LatencySummaryCard(
                        label = s.activityModelLatencyOverallAvg,
                        value = if (overallAvgTtft > 0) formatDuration(overallAvgTtft) else "--",
                        valueColor = if (overallAvgTtft > 0) overallTtftTier?.toColor() else null,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    LatencySummaryCard(
                        label = s.activityModelLatencyOverallAvgTps,
                        value = overallAvgTps?.let { formatTps(it) } ?: "--",
                        valueColor = overallAvgTps?.let { MaterialTheme.colorScheme.secondary },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    val overallDurationTier = getDurationLatencyTier(overallAvgDuration)
                    LatencySummaryCard(
                        label = s.activityModelLatencyOverallAvgDuration,
                        value = if (overallAvgDuration > 0) formatDuration(overallAvgDuration) else "--",
                        valueColor = if (overallAvgDuration > 0) overallDurationTier?.toColor() else null,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3. 模型多指标卡片列表
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = AppTokens.Spacing.card)
                ) {
                    if (validStats.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Outlined.Timer,
                            title = s.activityModelLatencyEmpty,
                            description = s.activityModelLatencyEmptyDesc,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(validStats, key = { it.modelId }) { stat ->
                                MultiMetricModelCard(
                                    stat = stat,
                                    maxAvgTtft = maxAvgTtft,
                                    maxAvgTps = maxAvgTps,
                                    maxAvgDuration = maxAvgDuration,
                                    config = config,
                                    officialModels = officialModels,
                                    s = s,
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 4. 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = s.commonClose,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LatencySummaryCard(
    label: String,
    value: String,
    valueColor: Color? = null,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.30f else 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 多指标并列模型卡片：
 * 遵循通透层次规范（外层纯净 surface 背景 + 微边框；内层微底色子卡片 + 进度条）
 */
@Composable
private fun MultiMetricModelCard(
    stat: ModelLatencyStat,
    maxAvgTtft: Long,
    maxAvgTps: Double,
    maxAvgDuration: Long,
    config: AppConfig? = null,
    officialModels: List<OfficialCatalogModel>? = null,
    s: Strings,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = androidx.compose.runtime.remember(stat.modelId, config, officialModels) {
        ModelDisplayNameResolver.resolve(
            modelId = stat.modelId,
            config = config,
            officialModels = officialModels
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶行：模型名称 (displayName + modelId) + 状态徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayName != stat.modelId) {
                        Text(
                            text = stat.modelId,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 仅在存在卡顿时展示警示徽标
                    if (stat.totalStallCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = if (isDark) 0.20f else 0.14f)
                        ) {
                            Text(
                                text = s.activityModelLatencyStallBadge(stat.totalStallCount),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 调用次数徽标
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.55f else 0.80f)
                    ) {
                        val badgeText = if (stat.completedCount == stat.totalRequests) {
                            s.activityModelLatencyCallsCount(stat.totalRequests)
                        } else {
                            s.activityModelLatencyCompletedCalls(stat.completedCount, stat.totalRequests)
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 中间：首字响应、生成速率、总耗时三指标并列子卡片行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 首字响应 (TTFT)
                val ttftTier = getFirstTokenLatencyTier(stat.averageFirstTokenMs)
                val ttftColor = if (stat.averageFirstTokenMs > 0) {
                    ttftTier?.toColor() ?: MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val ttftRatio = if (maxAvgTtft > 0 && stat.averageFirstTokenMs > 0) {
                    (stat.averageFirstTokenMs.toFloat() / maxAvgTtft.toFloat()).coerceIn(0.04f, 1f)
                } else {
                    0f
                }
                val ttftFootnote = formatRangeFootnote(
                    minMs = stat.minFirstTokenMs,
                    maxMs = stat.maxFirstTokenMs,
                    p50Ms = stat.p50FirstTokenMs,
                    medianLabel = s.activityModelLatencyMedian
                )

                MetricColumnCard(
                    title = s.activityModelLatencyColAvgTtft,
                    valueText = if (stat.averageFirstTokenMs > 0) formatDuration(stat.averageFirstTokenMs) else "--",
                    valueColor = ttftColor,
                    footnoteText = ttftFootnote,
                    progressRatio = ttftRatio,
                    progressColor = ttftColor,
                    icon = Icons.Outlined.ElectricBolt,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )

                // 2. 生成速率 (TPS)
                val tpsColor = if (stat.averageTps != null && stat.averageTps > 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val tpsRatio = if (maxAvgTps > 0 && stat.averageTps != null && stat.averageTps > 0) {
                    (stat.averageTps.toFloat() / maxAvgTps.toFloat()).coerceIn(0.04f, 1f)
                } else {
                    0f
                }
                val tpsFootnote = formatTpsFootnote(
                    minTps = stat.minTps,
                    maxTps = stat.maxTps,
                    p50Tps = stat.p50Tps,
                    medianLabel = s.activityModelLatencyMedian
                )

                MetricColumnCard(
                    title = s.activityModelLatencyColAvgTps,
                    valueText = stat.averageTps?.let { formatTps(it) } ?: "--",
                    valueColor = tpsColor,
                    footnoteText = tpsFootnote,
                    progressRatio = tpsRatio,
                    progressColor = tpsColor,
                    icon = Icons.Outlined.Speed,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )

                // 3. 会话总耗时 (Duration)
                val durationTier = getDurationLatencyTier(stat.averageDurationMs)
                val durationColor = if (stat.averageDurationMs > 0) {
                    durationTier?.toColor() ?: MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val durationRatio = if (maxAvgDuration > 0 && stat.averageDurationMs > 0) {
                    (stat.averageDurationMs.toFloat() / maxAvgDuration.toFloat()).coerceIn(0.04f, 1f)
                } else {
                    0f
                }
                val durationFootnote = formatRangeFootnote(
                    minMs = stat.minDurationMs,
                    maxMs = stat.maxDurationMs,
                    p50Ms = stat.p50DurationMs,
                    medianLabel = s.activityModelLatencyMedian
                )

                MetricColumnCard(
                    title = s.activityModelLatencyColAvgDuration,
                    valueText = if (stat.averageDurationMs > 0) formatDuration(stat.averageDurationMs) else "--",
                    valueColor = durationColor,
                    footnoteText = durationFootnote,
                    progressRatio = durationRatio,
                    progressColor = durationColor,
                    icon = Icons.Outlined.AccessTime,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单项指标列卡片：
 * 采用纵向层次结构（标题 -> 核心大数值 -> 进度条 -> 辅助范围说明），避免横向挤压换行
 */
@Composable
private fun MetricColumnCard(
    title: String,
    valueText: String,
    valueColor: Color,
    footnoteText: String,
    progressRatio: Float,
    progressColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppTokens.Radius.small),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.38f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. 顶部：图标 + 标题（单行完整展示）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. 中间：大字数值独立占行（永不挤压折行）
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 3. 进度指示条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.40f))
            ) {
                if (progressRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressRatio)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(progressColor)
                    )
                }
            }

            // 4. 底部：通俗易懂的极值区间或中位数说明（单行）
            Text(
                text = footnoteText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 格式化耗时辅助说明文案（极值区间或中位数）
 */
private fun formatRangeFootnote(minMs: Long, maxMs: Long, p50Ms: Long, medianLabel: String): String {
    if (minMs > 0L && maxMs > 0L && minMs != maxMs) {
        return "${formatDuration(minMs)} ~ ${formatDuration(maxMs)}"
    }
    if (p50Ms > 0L) {
        return "$medianLabel: ${formatDuration(p50Ms)}"
    }
    if (maxMs > 0L) {
        return formatDuration(maxMs)
    }
    return "--"
}

/**
 * 格式化速率辅助说明文案（极值区间或中位数）
 */
private fun formatTpsFootnote(minTps: Double?, maxTps: Double?, p50Tps: Double?, medianLabel: String): String {
    if (minTps != null && maxTps != null && minTps > 0.0 && maxTps > 0.0 && kotlin.math.abs(maxTps - minTps) > 0.1) {
        return "${formatTps(minTps)} ~ ${formatTps(maxTps)}"
    }
    if (p50Tps != null && p50Tps > 0.0) {
        return "$medianLabel: ${formatTps(p50Tps)}"
    }
    return "--"
}
