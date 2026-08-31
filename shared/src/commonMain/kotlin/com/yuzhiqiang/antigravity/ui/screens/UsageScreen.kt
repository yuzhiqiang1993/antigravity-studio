package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioGlassSurface
import com.yuzhiqiang.antigravity.ui.components.StudioTooltip
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.usage.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val stats by viewModel.usageStats.collectAsState()
    val isRefreshing by viewModel.isRefreshingUsage.collectAsState()
    val currentTimeRange by viewModel.usageTimeRange.collectAsState()
    val customDateRange by viewModel.usageCustomDateRange.collectAsState()
    val selectedSources by viewModel.usageSelectedSources.collectAsState()

    var showCustomDateDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val controlsScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 顶部主标题
        val statsBadge = if (stats.totalTokens > 0) {
            s.usageStatsBadge(stats.totalConversations, stats.daysActive)
        } else {
            "0"
        }
        PageHeader(
            title = s.navUsage,
            badge = statsBadge,
            subtitle = s.usageSubtitle,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                .align(Alignment.CenterHorizontally)
        )

        // 2. 现代毛玻璃操作栏：[滚动时间] │ [日历时间] │ [全部/自定义] + [来源过滤] + [刷新按钮]
        StudioGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(AppTokens.Radius.pill),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(controlsScrollState)
                    .padding(
                        horizontal = AppTokens.Spacing.compact,
                        vertical = AppTokens.Spacing.xs
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：时间范围筛选胶囊（对齐插件分组）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimeRangeChip(
                        label = s.usageTimeRange24h,
                        selected = currentTimeRange == UsageTimeRange.ROLLING_24H,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_24H) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRange7d,
                        selected = currentTimeRange == UsageTimeRange.ROLLING_7D,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_7D) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRange14d,
                        selected = currentTimeRange == UsageTimeRange.ROLLING_14D,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_14D) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRange30d,
                        selected = currentTimeRange == UsageTimeRange.ROLLING_30D,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_30D) }
                    )

                    VerticalDivider(
                        modifier = Modifier.height(16.dp).padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    TimeRangeChip(
                        label = s.usageTimeRangeToday,
                        selected = currentTimeRange == UsageTimeRange.CALENDAR_TODAY,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.CALENDAR_TODAY) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRangeThisWeek,
                        selected = currentTimeRange == UsageTimeRange.CALENDAR_THIS_WEEK,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.CALENDAR_THIS_WEEK) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRangeThisMonth,
                        selected = currentTimeRange == UsageTimeRange.CALENDAR_THIS_MONTH,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.CALENDAR_THIS_MONTH) }
                    )

                    VerticalDivider(
                        modifier = Modifier.height(16.dp).padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    TimeRangeChip(
                        label = s.usageTimeRangeAllTime,
                        selected = currentTimeRange == UsageTimeRange.ALL_TIME,
                        onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ALL_TIME) }
                    )
                    TimeRangeChip(
                        label = s.usageTimeRangeCustom,
                        selected = currentTimeRange == UsageTimeRange.CUSTOM,
                        onClick = { showCustomDateDialog = true }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 右侧：来源筛选与刷新
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 来源多选组
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SourceFilterChip(
                            label = s.usageSourceAll,
                            selected = selectedSources.contains("all"),
                            onClick = { viewModel.toggleUsageSource("all") }
                        )
                        SourceFilterChip(
                            label = s.usageSourceIde,
                            selected = selectedSources.contains("ide"),
                            onClick = { viewModel.toggleUsageSource("ide") }
                        )
                        SourceFilterChip(
                            label = s.usageSourceApp,
                            selected = selectedSources.contains("standalone"),
                            onClick = { viewModel.toggleUsageSource("standalone") }
                        )
                        SourceFilterChip(
                            label = s.usageSourceCli,
                            selected = selectedSources.contains("cli"),
                            onClick = { viewModel.toggleUsageSource("cli") }
                        )
                    }

                    // 刷新按钮
                    StudioTooltip(text = s.accountsRefreshAllTooltip) {
                        IconButton(
                            onClick = { viewModel.refreshUsageStats(force = true) },
                            modifier = Modifier.size(AppTokens.Size.compactControlHeight)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppTokens.Size.iconMedium),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = s.accountsRefreshAllTooltip,
                                    modifier = Modifier.size(AppTokens.Size.iconMedium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 滚动主看板区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                .align(Alignment.CenterHorizontally)
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // A. 当前时间范围的 Hero KPI（用量概览：费用、总 Token、五维构成条、调用总数）
            UsageKpiGrid(stats = stats)

            // B. 每日/每小时消耗走势平滑面积图（完整五维 tooltip）
            UsageTrendChart(
                dailyBuckets = stats.dailyBuckets,
                hourlyBuckets = stats.hourlyBuckets,
                timeRange = currentTimeRange
            )

            // D. 插件 up dashboard 的年度活跃度网格
            ActivityHeatmapCard(dailyBuckets = stats.dailyBuckets)

            // E. 热门模型使用排行 & 数据来源分布
            TopModelsAndSourcesSection(
                modelBuckets = stats.modelBuckets,
                sourceBuckets = stats.sourceBuckets
            )

            // H. 高消耗会话排行榜 (Top Conversations)
            TopConversationsCard(conversations = stats.topConversations)
        }
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            initialStartDate = customDateRange?.startDate ?: "",
            initialEndDate = customDateRange?.endDate ?: "",
            initialFollowNow = customDateRange?.followNow ?: false,
            onConfirm = { range ->
                viewModel.setUsageTimeRange(UsageTimeRange.CUSTOM, range)
            },
            onDismiss = { showCustomDateDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        modifier = Modifier.height(28.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(28.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}
