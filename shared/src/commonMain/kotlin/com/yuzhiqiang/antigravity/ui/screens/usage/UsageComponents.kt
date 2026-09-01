package com.yuzhiqiang.antigravity.ui.screens.usage


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.usage.*
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.components.StudioGlassCard
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlin.math.roundToInt

/**
 * 现代高阶 Hero 看板：
 * 1. 突出真实消耗 Token（超大数字 + 紧凑万/亿换算，主视觉焦点）；
 * 2. 弱化请求数与总成本（右上角紧凑微卡片承载）；
 * 3. 结构化 2x2/2x3 Token 构成指标卡（带专属图标与百分比）；
 * 4. 底部融合轻量缓存命中率进度条与多维色彩比例条。
 */
@Composable
fun UsageKpiGrid(
    stats: DeepUsageStats,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val colors = usageTokenColors()
    val tokenFormatter = UsageNumberFormatter
    val hasUnmatchedPricing = stats.modelBuckets.any { !it.pricingMatched }
    val allUnmatched = stats.modelBuckets.isNotEmpty() && stats.modelBuckets.all { !it.pricingMatched }
    val costAmount = tokenFormatter.formatUsdAmount(stats.estimatedCostUsd)
    val costValue = when {
        stats.totalCalls == 0L -> s.usageCostValue(costAmount)
        allUnmatched && stats.estimatedCostUsd == 0.0 -> s.usageCostUnavailable
        stats.costLowerBound -> s.usageCostLowerBound(costAmount)
        else -> s.usageCostValue(costAmount)
    }

    val totalTokens = stats.totalTokens
    val promptHitRatio = stats.promptCacheHitRatio
    val cacheHitRatioText = promptHitRatio?.let { ratio ->
        val prefix = if (stats.cacheHitRateIncomplete) "≈" else ""
        "$prefix${tokenFormatter.formatPercent(ratio * 100.0)}%"
    } ?: "—"

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部 Hero 主视觉栏：左侧超大 Token 消耗 + 右侧弱化的请求与成本徽标卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧主视觉：真实消耗 Tokens
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ElectricBolt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = s.usageKpiTotalTokensTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = tokenFormatter.formatCount(totalTokens),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (totalTokens >= 10_000L) {
                                Text(
                                    text = "≈ ${tokenFormatter.formatTokens(totalTokens)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 右侧弱化辅助指标卡：总请求数 + 预估成本
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 请求数
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = s.usageKpiCallsTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShowChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = tokenFormatter.formatCount(stats.totalCalls),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(22.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // 预估总成本
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = s.usageKpiCostTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = costValue,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (stats.estimatedCostUsd > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. 中层：输入 (Input)、输出 (Output)、缓存利用 (Cache) 三卡片并列对齐
            val totalInputTokens = stats.totalInput + stats.totalCacheRead + stats.totalCacheWrite
            val totalOutputTokens = stats.totalOutput + stats.totalReasoning
            val inputPct = if (totalTokens > 0L) (totalInputTokens.toDouble() / totalTokens.toDouble() * 100.0) else 0.0
            val outputPct =
                if (totalTokens > 0L) (totalOutputTokens.toDouble() / totalTokens.toDouble() * 100.0) else 0.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 卡片 1：输入用量 (Prompt Input)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colors.input
                                )
                                Text(
                                    text = s.usageTotalInputTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${tokenFormatter.formatPercent(inputPct)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.input
                            )
                        }

                        Text(
                            text = tokenFormatter.formatTokens(totalInputTokens),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 细项标签行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubmetricTag(
                                label = s.usageUncachedInputLabel,
                                value = tokenFormatter.formatTokens(stats.totalInput),
                                dotColor = colors.input
                            )
                            if (stats.totalCacheWrite > 0L) {
                                SubmetricTag(
                                    label = s.usageCacheWriteLabel,
                                    value = tokenFormatter.formatTokens(stats.totalCacheWrite),
                                    dotColor = colors.cacheWrite
                                )
                            }
                        }
                    }
                }

                // 卡片 2：输出用量 (Model Output)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colors.output
                                )
                                Text(
                                    text = s.usageTotalOutputTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${tokenFormatter.formatPercent(outputPct)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.output
                            )
                        }

                        Text(
                            text = tokenFormatter.formatTokens(totalOutputTokens),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 细项标签行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubmetricTag(
                                label = s.usageGenerationLabel,
                                value = tokenFormatter.formatTokens(stats.totalOutput),
                                dotColor = colors.output
                            )
                            if (stats.totalReasoning > 0L) {
                                SubmetricTag(
                                    label = s.usageTokenThinking,
                                    value = tokenFormatter.formatTokens(stats.totalReasoning),
                                    dotColor = colors.reasoning
                                )
                            }
                            if (stats.totalUnattributed > 0L) {
                                SubmetricTag(
                                    label = s.usageTokenUnattributed,
                                    value = tokenFormatter.formatTokens(stats.totalUnattributed),
                                    dotColor = colors.unattributed
                                )
                            }
                        }
                    }
                }

                // 卡片 3：缓存命中与效率 (Cache Performance)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colors.cacheRead
                                )
                                Text(
                                    text = s.usageCacheCardTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = cacheHitRatioText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.cacheRead
                            )
                        }

                        Text(
                            text = tokenFormatter.formatTokens(stats.totalCacheRead),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 细项标签行：已省成本 + 命中 Tokens
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (stats.estimatedSavingsUsd > 0.0) {
                                SubmetricTag(
                                    label = s.usageSavedAmountLabel,
                                    value = "$${tokenFormatter.formatUsdAmount(stats.estimatedSavingsUsd)}",
                                    dotColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            SubmetricTag(
                                label = s.usageCacheHitLabel,
                                value = tokenFormatter.formatTokens(stats.totalCacheRead),
                                dotColor = colors.cacheRead
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmetricTag(
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor)
        )
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TokenCompositionBar(stats: DeepUsageStats, colors: UsageTokenColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UsageVisualTokens.progressHeight)
            .clip(RoundedCornerShape(UsageVisualTokens.progressRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
    ) {
        if (stats.totalTokens > 0L) {
            TokenCompositionSegment(stats.totalInput, colors.input)
            TokenCompositionSegment(stats.totalCacheRead, colors.cacheRead)
            TokenCompositionSegment(stats.totalCacheWrite, colors.cacheWrite)
            TokenCompositionSegment(stats.totalOutput, colors.output)
            TokenCompositionSegment(stats.totalReasoning, colors.reasoning)
            TokenCompositionSegment(stats.totalUnattributed, colors.unattributed)
        }
    }
}

@Composable
private fun RowScope.TokenCompositionSegment(value: Long, color: Color) {
    if (value > 0L) {
        Box(
            modifier = Modifier
                .weight(value.toFloat().coerceAtLeast(0.001f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

/** Token 构成彩色横向比例条与图例（独立复用版本）。 */
@Composable
fun TokenCompositionCard(
    stats: DeepUsageStats,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val colors = usageTokenColors()
    val total = stats.totalTokens

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.usageCompositionTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            TokenCompositionBar(stats = stats, colors = colors)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLegendItem(s.usageTokenPromptInput, stats.totalInput, colors.input, total)
                CompositionLegendItem(s.usageTokenCacheRead, stats.totalCacheRead, colors.cacheRead, total)
                CompositionLegendItem(s.usageTokenCacheWrite, stats.totalCacheWrite, colors.cacheWrite, total)
                CompositionLegendItem(s.usageTokenModelOutput, stats.totalOutput, colors.output, total)
                CompositionLegendItem(s.usageTokenThinking, stats.totalReasoning, colors.reasoning, total)
                CompositionLegendItem(s.usageTokenUnattributed, stats.totalUnattributed, colors.unattributed, total)
            }
        }
    }
}

@Composable
private fun CompositionLegendItem(
    label: String,
    count: Long,
    color: Color,
    total: Long
) {
    val pct = if (total > 0) (count.toDouble() / total.toDouble() * 100) else 0.0
    val compact = UsageNumberFormatter.formatTokens(count)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.badgeText),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$compact (${UsageNumberFormatter.formatPercent(pct)}%)",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = UsageVisualTokens.Typography.legendText,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun localizedSourceName(source: String, s: com.yuzhiqiang.antigravity.i18n.Strings): String =
    when (source.lowercase()) {
        "ide" -> s.usageSourceIde
        "standalone", "app" -> s.usageSourceApp
        "cli" -> s.usageSourceCli
        else -> source
    }

/**
 * 热门模型排行榜与来源分布
 */
@Composable
fun TopModelsAndSourcesSection(
    modelBuckets: List<ModelUsageBucket>,
    sourceBuckets: List<AppSourceUsageBucket>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.weight(1.3f)) {
                    TopModelsBreakdownCard(modelBuckets = modelBuckets)
                }
                Box(modifier = Modifier.weight(1f)) {
                    SourceBreakdownCard(sourceBuckets = sourceBuckets)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TopModelsBreakdownCard(modelBuckets = modelBuckets)
                SourceBreakdownCard(sourceBuckets = sourceBuckets)
            }
        }
    }
}

@Composable
fun SourceBreakdownCard(
    sourceBuckets: List<AppSourceUsageBucket>,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val totalTokens = remember(sourceBuckets) {
        maxOf(1L, sourceBuckets.sumOf { it.totalTokens })
    }

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.usageSourceBreakdownTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                sourceBuckets.forEach { bucket ->
                    val progress = (bucket.totalTokens.toFloat() / totalTokens.toFloat()).coerceIn(0.02f, 1f)
                    val compact = UsageNumberFormatter.formatTokens(bucket.totalTokens)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedSourceName(bucket.appSource, s),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = s.usageSourceTokensCostLabel(
                                    compact,
                                    usageBucketCostLabel(
                                        bucket.costUsd,
                                        bucket.pricingMatched,
                                        bucket.costLowerBound,
                                        s
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (bucket.pricingMatched) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 热门模型消耗排行榜
 */
@Composable
fun TopModelsBreakdownCard(
    modelBuckets: List<ModelUsageBucket>,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tokenColors = usageTokenColors()

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.usageTopModelsTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = UsageVisualTokens.Typography.cardTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = s.usageModelCount(modelBuckets.size),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = UsageVisualTokens.Typography.sectionBadge),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (modelBuckets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.usageTopModelsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.ModelList.itemSpacing)
                ) {
                    modelBuckets.take(8).forEachIndexed { index, item ->
                        ModelBreakdownRow(
                            rank = index + 1,
                            bucket = item,
                            costFormatted = modelCostLabel(item, s),
                            tokensFormatted = UsageNumberFormatter.formatTokens(item.totalTokens) +
                                    if (item.missingUsage != null && item.unattributed == 0L) "+" else "",
                            inputFormatted = formatUsageValue(item.input, item.missingUsage?.input ?: 0L),
                            outputFormatted = formatUsageValue(item.output, item.missingUsage?.output ?: 0L),
                            cacheFormatted = formatUsageValue(item.cacheRead, item.missingUsage?.cache ?: 0L),
                            cacheWriteFormatted = formatUsageValue(
                                item.cacheWrite,
                                item.missingUsage?.cacheWrite ?: 0L
                            ),
                            reasoningFormatted = formatUsageValue(item.reasoning, item.missingUsage?.reasoning ?: 0L),
                            tokenColors = tokenColors
                        )
                        if (index < minOf(7, modelBuckets.size - 1)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modelCostLabel(bucket: ModelUsageBucket, s: com.yuzhiqiang.antigravity.i18n.Strings): String =
    usageBucketCostLabel(bucket.costUsd, bucket.pricingMatched, bucket.costLowerBound, s)

internal fun usageBucketCostLabel(
    costUsd: Double,
    pricingMatched: Boolean,
    costLowerBound: Boolean,
    s: com.yuzhiqiang.antigravity.i18n.Strings
): String {
    if (!pricingMatched) return s.usageCostUnavailable
    val amount = UsageNumberFormatter.formatUsdAmount(costUsd)
    return if (costLowerBound) s.usageCostLowerBound(amount) else s.usageCostValue(amount)
}

private fun formatUsageValue(value: Long, missingCalls: Long): String {
    if (missingCalls <= 0L) return UsageNumberFormatter.formatTokens(value)
    return if (value > 0L) "${UsageNumberFormatter.formatTokens(value)}+" else "—"
}

@Composable
private fun ModelBreakdownRow(
    rank: Int,
    bucket: ModelUsageBucket,
    costFormatted: String,
    tokensFormatted: String,
    inputFormatted: String,
    outputFormatted: String,
    cacheFormatted: String,
    cacheWriteFormatted: String,
    reasoningFormatted: String,
    tokenColors: UsageTokenColors
) {
    val s = strings()
    val cacheRate = calculatePromptCacheHitRatio(
        cacheReadTokens = bucket.cacheRead,
        uncachedInputTokens = bucket.input,
        cacheWriteTokens = bucket.cacheWrite
    )?.times(100.0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.ModelList.rowGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UsageVisualTokens.ModelList.headerGap),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(UsageVisualTokens.ModelList.rankBadgeSize)
                        .clip(CircleShape)
                        .background(if (rank <= 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = UsageVisualTokens.Typography.rankNumber
                        ),
                        color = if (rank <= 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = bucket.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = UsageVisualTokens.Typography.modelTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(AppTokens.Radius.xs),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = if (cacheRate == null) {
                            "—"
                        } else {
                            val prefix = if (bucket.cacheHitRateIncomplete) "≈" else ""
                            s.usageCacheRate("$prefix${UsageNumberFormatter.formatPercent(cacheRate)}")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.badgeText),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = s.usageTokensCount(tokensFormatted),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = UsageVisualTokens.Typography.heroSupporting,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = costFormatted,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = UsageVisualTokens.Typography.modelTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (bucket.pricingMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // 输入、缓存读取、缓存写入、输出、思考与未归因 Token 使用独立分段。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(UsageVisualTokens.ModelList.barHeight)
                .clip(RoundedCornerShape(AppTokens.Radius.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            TokenCompositionSegment(bucket.input, tokenColors.input)
            TokenCompositionSegment(bucket.cacheRead, tokenColors.cacheRead)
            TokenCompositionSegment(bucket.cacheWrite, tokenColors.cacheWrite)
            TokenCompositionSegment(bucket.output, tokenColors.output)
            TokenCompositionSegment(bucket.reasoning, tokenColors.reasoning)
            TokenCompositionSegment(bucket.unattributed, tokenColors.unattributed)
        }

        Text(
            text = s.usageModelTokensDetail(
                inputFormatted,
                outputFormatted,
                cacheFormatted,
                cacheWriteFormatted,
                reasoningFormatted
            ) + if (bucket.unattributed > 0L) {
                " · ${s.usageTokenUnattributed} ${UsageNumberFormatter.formatTokens(bucket.unattributed)}"
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = UsageVisualTokens.Typography.modelMeta),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (bucket.pricingSource != "unknown") {
            Text(
                text = s.usagePricingSource(bucket.pricingSource),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.axisTime),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (bucket.missingUsage != null || bucket.costLowerBound) {
            Text(
                text = s.usageModelUsageIncomplete,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.axisTime),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 高消耗会话排行榜 (Top Conversations)
 */
@Composable
fun TopConversationsCard(
    conversations: List<ConversationUsageBucket>,
    modifier: Modifier = Modifier
) {
    val s = strings()

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.usageTopConversationsTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = s.usageConversationCount(conversations.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.usageTopConversationsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    conversations.take(10).forEachIndexed { idx, convo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = convo.title,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = localizedSourceName(convo.appSource, s),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = s.usageConversationTokensDetail(
                                        UsageNumberFormatter.formatTokens(convo.totalTokens),
                                        convo.calls
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = usageBucketCostLabel(
                                    convo.costUsd,
                                    convo.pricingMatched,
                                    convo.costLowerBound,
                                    s
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (convo.pricingMatched) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (idx < minOf(9, conversations.size - 1)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自定义日期范围选择弹窗（Material 3 原生 DateRangePicker）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    initialStartDate: String,
    initialEndDate: String,
    initialFollowNow: Boolean = false,
    onConfirm: (CustomDateRange) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    val initialStartMillis = remember(initialStartDate) {
        runCatching {
            java.time.LocalDate.parse(initialStartDate.trim())
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
    val initialEndMillis = remember(initialEndDate) {
        runCatching {
            java.time.LocalDate.parse(initialEndDate.trim())
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    val hasValidRange = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = state.selectedStartDateMillis ?: return@TextButton
                    val endMillis = state.selectedEndDateMillis ?: return@TextButton
                    val startDate = java.time.Instant.ofEpochMilli(startMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .toString()
                    val endDate = java.time.Instant.ofEpochMilli(endMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .toString()
                    onConfirm(
                        CustomDateRange(
                            startDate = startDate,
                            endDate = endDate,
                            followNow = false
                        )
                    )
                    onDismiss()
                },
                enabled = hasValidRange
            ) {
                Text(s.commonConfirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.commonCancel)
            }
        },
        shape = RoundedCornerShape(24.dp)
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f, fill = false),
            title = {
                Text(
                    text = s.usageCustomDateDialogTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp)
                )
            },
            headline = {
                DateRangePickerDefaults.DateRangePickerHeadline(
                    selectedStartDateMillis = state.selectedStartDateMillis,
                    selectedEndDateMillis = state.selectedEndDateMillis,
                    displayMode = state.displayMode,
                    dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)
                )
            },
            showModeToggle = false
        )
    }
}
