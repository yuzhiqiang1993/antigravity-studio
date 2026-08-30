package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

/**
 * 各模型首字平均耗时 (TTFT) 统计弹窗：
 * - 遵循 Material Design 3 顶级容器规范 (StudioDialogSurface + 24dp 圆角)
 * - 顶部展示模型数、会话总样本量、全局首字均值 3 项汇总指标
 * - 列表展示各模型的平均首字耗时、极值区间、总耗时均值与可视化耗时对比条
 */
@Composable
fun ModelLatencyStatsDialog(
    logs: List<ActivityLog>,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stats = rememberModelLatencyStats(logs)

    val validStats = stats.filter { it.sampleCount > 0 }
    val totalSamples = validStats.sumOf { it.sampleCount }
    val activeModelCount = validStats.size
    val overallAvgTtft = if (validStats.isNotEmpty()) {
        val totalMs = logs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }.sum()
        if (totalSamples > 0) totalMs / totalSamples else 0L
    } else {
        0L
    }
    val maxAvgTtft = validStats.maxOfOrNull { it.averageFirstTokenMs }?.coerceAtLeast(1L) ?: 1L

    Dialog(onDismissRequest = onDismiss) {
        StudioDialogSurface(
            modifier = Modifier
                .width(680.dp)
                .heightIn(min = 400.dp, max = 680.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.20f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = s.activityModelLatencyDialogTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.activityModelLatencyDialogSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.40f)
                )

                // 2. 汇总概览卡片行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LatencySummaryCard(
                        label = s.activityModelLatencyActiveModels,
                        value = activeModelCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    LatencySummaryCard(
                        label = s.activityModelLatencyTotalSamples,
                        value = totalSamples.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    val overallTier = getFirstTokenLatencyTier(overallAvgTtft)
                    LatencySummaryCard(
                        label = s.activityModelLatencyOverallAvg,
                        value = if (overallAvgTtft > 0) formatDuration(overallAvgTtft) else "--",
                        valueColor = if (overallAvgTtft > 0) overallTier?.toColor() else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3. 模型首字耗时列表
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 22.dp)
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
                                ModelLatencyCard(
                                    stat = stat,
                                    maxAvgTtft = maxAvgTtft,
                                    s = s,
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.40f)
                )

                // 4. 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "完成",
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
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModelLatencyCard(
    stat: ModelLatencyStat,
    maxAvgTtft: Long,
    s: Strings,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val ttftTier = getFirstTokenLatencyTier(stat.averageFirstTokenMs)
    val latencyColor = ttftTier?.toColor() ?: MaterialTheme.colorScheme.primary
    val ratio = if (maxAvgTtft > 0) {
        (stat.averageFirstTokenMs.toFloat() / maxAvgTtft.toFloat()).coerceIn(0.04f, 1f)
    } else {
        0.04f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶行：模型名称 + 样本 Badge + 首字平均耗时
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = stat.modelId,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = s.activityModelLatencySampleCount(stat.sampleCount),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = s.activityModelLatencyColAvgTtft,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(stat.averageFirstTokenMs),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        ),
                        color = latencyColor
                    )
                }
            }

            // 中间：可视化对比条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.6f else 0.8f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(latencyColor)
                )
            }

            // 底行：极值区间 + 总平均耗时 + 总请求量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${s.activityModelLatencyColRange}:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDuration(stat.minFirstTokenMs)} ~ ${formatDuration(stat.maxFirstTokenMs)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (stat.averageDurationMs > 0) {
                        Text(
                            text = "${s.activityModelLatencyColAvgDuration}: ${formatDuration(stat.averageDurationMs)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "${stat.totalRequests} ${s.activityTotal}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
