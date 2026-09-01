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

        // 2. 现代毛玻璃操作栏：[精炼时间分段] + [来源多选分段] + [刷新按钮]
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
                        horizontal = 8.dp,
                        vertical = 6.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：精炼时间范围分段选择器（今日 · 7天 · 30天 · 全部 · 自定义）
                Surface(
                    shape = RoundedCornerShape(AppTokens.Radius.pill),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModernTimeTab(
                            label = s.usageTimeRangeToday,
                            selected = currentTimeRange == UsageTimeRange.CALENDAR_TODAY,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.CALENDAR_TODAY) }
                        )
                        ModernTimeTab(
                            label = s.usageTimeRange24h,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_24H,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_24H) }
                        )
                        ModernTimeTab(
                            label = s.usageTimeRange7d,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_7D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_7D) }
                        )
                        ModernTimeTab(
                            label = s.usageTimeRange14d,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_14D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_14D) }
                        )
                        ModernTimeTab(
                            label = s.usageTimeRange30d,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_30D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_30D) }
                        )

                        // 自定义日期 Tab
                        val isCustom = currentTimeRange == UsageTimeRange.CUSTOM
                        val customLabel =
                            if (isCustom && customDateRange != null && customDateRange?.startDate?.isNotBlank() == true) {
                                "${UsageNumberFormatter.formatShortDate(customDateRange!!.startDate)}~${
                                    UsageNumberFormatter.formatShortDate(
                                        customDateRange!!.endDate
                                    )
                                }"
                            } else {
                                s.usageTimeRangeCustom
                            }
                        ModernTimeTab(
                            label = customLabel,
                            icon = Icons.Outlined.CalendarMonth,
                            selected = isCustom,
                            onClick = { showCustomDateDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右侧：来源筛选与刷新
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppTokens.Radius.pill),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ModernSourceTab(
                                label = s.usageSourceAll,
                                selected = selectedSources.contains("all"),
                                onClick = { viewModel.toggleUsageSource("all") }
                            )
                            ModernSourceTab(
                                label = s.usageSourceIde,
                                selected = selectedSources.contains("ide"),
                                onClick = { viewModel.toggleUsageSource("ide") }
                            )
                            ModernSourceTab(
                                label = s.usageSourceApp,
                                selected = selectedSources.contains("standalone"),
                                onClick = { viewModel.toggleUsageSource("standalone") }
                            )
                            ModernSourceTab(
                                label = s.usageSourceCli,
                                selected = selectedSources.contains("cli"),
                                onClick = { viewModel.toggleUsageSource("cli") }
                            )
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.height(18.dp).padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 刷新按钮
                    StudioTooltip(text = s.accountsRefreshAllTooltip) {
                        IconButton(
                            onClick = { viewModel.refreshUsageStats(force = true) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = s.accountsRefreshAllTooltip,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 滚动主看板区域
        val isInitialLoading = isRefreshing && stats.totalTokens == 0L && stats.totalCalls == 0L

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1160.dp)
                .align(Alignment.CenterHorizontally)
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
        ) {
            com.yuzhiqiang.antigravity.ui.animation.StudioCrossfade(
                targetState = isInitialLoading,
                label = "usage_dashboard_crossfade"
            ) { loading ->
                if (loading) {
                    UsageDashboardSkeleton()
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
                    ) {
                        // A. 当前时间范围的 Hero KPI（用量概览：费用、总 Token、五维构成条、调用总数）
                        UsageKpiGrid(stats = stats)

                        // B. 每日/每小时消耗走势平滑面积图（完整五维 tooltip）
                        UsageTrendChart(
                            dailyBuckets = stats.dailyBuckets,
                            hourlyBuckets = stats.hourlyBuckets,
                            timeRange = currentTimeRange
                        )

                        // E. 热门模型使用排行
                        TopModelsBreakdownCard(
                            modelBuckets = stats.modelBuckets
                        )
                    }
                }
            }
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

@Composable
private fun ModernTimeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        color = containerColor,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = contentColor
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ModernSourceTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        color = containerColor,
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}
