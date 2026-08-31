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
 * 插件大盘同款 Hero：两个主指标、一个五维构成条和紧凑明细行。
 * 将 Token 构成放回汇总区域，避免把核心信息拆成多个大卡片。
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
        else -> s.usageCostValue(costAmount)
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
                    text = s.usageOverviewTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = UsageVisualTokens.Typography.overviewTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (stats.dateRangeFrom.isNotBlank()) {
                    Text(
                        text = "${UsageNumberFormatter.formatShortDate(stats.dateRangeFrom)} ~ " +
                                UsageNumberFormatter.formatShortDate(stats.dateRangeTo),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = UsageVisualTokens.Typography.heroSupporting),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UsageVisualTokens.cardGap),
                verticalAlignment = Alignment.Bottom
            ) {
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    title = s.usageKpiCostTitle,
                    value = costValue,
                    supporting = when {
                        hasUnmatchedPricing -> s.usageKpiPricingWarning
                        else -> s.usageKpiCostSavings(tokenFormatter.formatUsdAmount(stats.estimatedSavingsUsd))
                    },
                    accent = MaterialTheme.colorScheme.onSurface
                )
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    title = s.usageKpiTotalTokensTitle,
                    value = tokenFormatter.formatTokens(stats.totalTokens),
                    supporting = s.usageKpiTokensDetail(
                        tokenFormatter.formatTokens(stats.totalInput),
                        tokenFormatter.formatTokens(stats.totalOutput),
                        tokenFormatter.formatTokens(stats.totalReasoning)
                    ),
                    accent = MaterialTheme.colorScheme.onSurface
                )
            }

            TokenCompositionBar(stats = stats, colors = colors)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(UsageVisualTokens.Chart.legendSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLegendItem(s.usageTokenPromptInput, stats.totalInput, colors.input, stats.totalTokens)
                CompositionLegendItem(s.usageTokenCacheRead, stats.totalCacheRead, colors.cacheRead, stats.totalTokens)
                CompositionLegendItem(
                    s.usageTokenCacheWrite,
                    stats.totalCacheWrite,
                    colors.cacheWrite,
                    stats.totalTokens
                )
                CompositionLegendItem(s.usageTokenModelOutput, stats.totalOutput, colors.output, stats.totalTokens)
                CompositionLegendItem(s.usageTokenThinking, stats.totalReasoning, colors.reasoning, stats.totalTokens)
                Text(
                    text = "${UsageNumberFormatter.formatCount(stats.totalCalls)} ${s.usageKpiCallsTitle} · " +
                            s.usageCacheRate(UsageNumberFormatter.formatPercent(stats.cacheHitRatio * 100.0)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = UsageVisualTokens.Typography.legendText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    title: String,
    value: String,
    supporting: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(UsageVisualTokens.innerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = UsageVisualTokens.Typography.heroTitle,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = UsageVisualTokens.Typography.heroValue,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.heroSupporting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
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

/** Token 五维构成彩色横向比例条与图例（独立复用版本）。 */
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
                                    if (item.costLowerBound) "+" else "",
                            inputFormatted = UsageNumberFormatter.formatTokens(item.input),
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

private fun usageBucketCostLabel(
    costUsd: Double,
    pricingMatched: Boolean,
    costLowerBound: Boolean,
    s: com.yuzhiqiang.antigravity.i18n.Strings
): String {
    if (!pricingMatched) return s.usageCostUnavailable
    val amount = UsageNumberFormatter.formatUsdAmount(costUsd)
    return s.usageCostValue(amount)
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
    val cacheBase = bucket.input + bucket.cacheRead
    val cacheRate = if (cacheBase > 0L) {
        bucket.cacheRead.toDouble() / cacheBase.toDouble() * 100.0
    } else null

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
                        text = if (cacheRate == null) s.usageCostUnavailable
                        else s.usageCacheRate(UsageNumberFormatter.formatPercent(cacheRate)),
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

        // 与插件模型卡片一致：输入、缓存读取、缓存写入、输出、思考使用独立分段。
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
        }

        Text(
            text = s.usageModelTokensDetail(
                inputFormatted,
                outputFormatted,
                cacheFormatted,
                cacheWriteFormatted,
                reasoningFormatted
            ),
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
 * 自定义日期范围选择弹窗
 */
@Composable
fun CustomDateRangeDialog(
    initialStartDate: String,
    initialEndDate: String,
    initialFollowNow: Boolean = false,
    onConfirm: (CustomDateRange) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    var start by remember { mutableStateOf(initialStartDate) }
    var end by remember { mutableStateOf(initialEndDate) }
    var followNow by remember { mutableStateOf(initialFollowNow) }
    val dateRangeValid = remember(start, end, followNow) {
        val startDate = runCatching { java.time.LocalDate.parse(start.trim()) }.getOrNull()
        val endDate = runCatching { java.time.LocalDate.parse(end.trim()) }.getOrNull()
        when {
            startDate == null -> false
            followNow -> !startDate.isAfter(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))
            endDate == null -> false
            else -> !endDate.isBefore(startDate)
        }
    }
    val dateRangeError = if (dateRangeValid) null else s.usageCustomDateInvalid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
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
                        text = s.usageCustomDateDialogTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = s.commonClose)
                    }
                }

                // 快捷预设小胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val now = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                    val quickPresets = listOf(
                        s.usageCustomDatePreset3Days to now.minusDays(2).toString(),
                        s.usageCustomDatePreset10Days to now.minusDays(9).toString(),
                        s.usageCustomDatePreset60Days to now.minusDays(59).toString(),
                        s.usageCustomDatePresetLastMonth to now.minusMonths(1).withDayOfMonth(1).toString()
                    )

                    quickPresets.forEach { (label, presetStart) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                start = presetStart
                                end = now.toString()
                                followNow = true
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = s.usageCustomDateStartLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioTextField(
                        value = start,
                        onValueChange = { start = it },
                        placeholder = s.usageCustomDatePlaceholder,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = s.usageCustomDateEndLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioTextField(
                        value = if (followNow) s.usageCustomDateNowValue else end,
                        onValueChange = { if (!followNow) end = it },
                        enabled = !followNow,
                        placeholder = s.usageCustomDatePlaceholder,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 跟随当前时间开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { followNow = !followNow }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = followNow,
                        onCheckedChange = { followNow = it }
                    )
                    Text(
                        text = s.usageCustomDateFollowNow,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (dateRangeError != null) {
                    Text(
                        text = dateRangeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        if (dateRangeValid) {
                            onConfirm(
                                CustomDateRange(
                                    startDate = start.trim(),
                                    endDate = if (followNow) "" else end.trim(),
                                    followNow = followNow
                                )
                            )
                            onDismiss()
                        }
                    },
                    enabled = dateRangeValid,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(s.usageCustomDateConfirm, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
