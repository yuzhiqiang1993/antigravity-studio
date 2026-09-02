package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
    val selectedModel by viewModel.usageSelectedModel.collectAsState()
    val autoRefreshInterval by viewModel.usageAutoRefreshInterval.collectAsState()

    var showDateRangeDialog by remember { mutableStateOf(false) }
    var showSourceDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showAutoRefreshDropdown by remember { mutableStateOf(false) }

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

        // 2. 现代毛玻璃操作栏：[左侧时间分段 + 日期选择]  <----->  [右侧模型筛选 + 来源 + 刷新频率 + 手动刷新]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                .align(Alignment.CenterHorizontally)
                .horizontalScroll(controlsScrollState),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧时间维度组：[今天 | 1 天 | 7 天 | 14 天 | 30 天] + [📅 日期选择 ⌄]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // A. 预设时间分段选择胶囊 (今天 · 1 天 · 7 天 · 14 天 · 30 天)
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.25f else 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        UsageTimePresetTab(
                            label = s.usagePresetToday,
                            selected = currentTimeRange == UsageTimeRange.CALENDAR_TODAY,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.CALENDAR_TODAY) }
                        )
                        UsageTimePresetTab(
                            label = s.usagePreset1Day,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_24H,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_24H) }
                        )
                        UsageTimePresetTab(
                            label = s.usagePreset7Days,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_7D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_7D) }
                        )
                        UsageTimePresetTab(
                            label = s.usagePreset14Days,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_14D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_14D) }
                        )
                        UsageTimePresetTab(
                            label = s.usagePreset30Days,
                            selected = currentTimeRange == UsageTimeRange.ROLLING_30D,
                            onClick = { viewModel.setUsageTimeRange(UsageTimeRange.ROLLING_30D) }
                        )
                    }
                }

                // B. 复合日期时间选择触发器 [📅 日期选择 ⌄]
                val isCustomRange = currentTimeRange == UsageTimeRange.CUSTOM
                val datePickerLabel = if (isCustomRange && customDateRange != null && customDateRange?.startDate?.isNotBlank() == true) {
                    val startFmt = UsageNumberFormatter.formatShortDate(customDateRange!!.startDate)
                    val endFmt = if (customDateRange!!.followNow || customDateRange!!.endDate.isBlank()) {
                        s.usagePresetToday
                    } else {
                        UsageNumberFormatter.formatShortDate(customDateRange!!.endDate)
                    }
                    "$startFmt~$endFmt"
                } else {
                    s.usageTimeRangeCustom
                }

                com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                    text = datePickerLabel,
                    leadingIcon = Icons.Outlined.CalendarMonth,
                    isActive = isCustomRange,
                    onClick = { showDateRangeDialog = true }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧控制组：[全部模型 ⌄] + [全部来源 ⌄] + [⟳ 30s ⌄] + [⟳ 手动刷新]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 模型筛选
                val isModelCustom = !selectedModel.isNullOrBlank() && selectedModel != "all"
                val currentModelLabel = if (!isModelCustom) {
                    s.usageModelAll
                } else {
                    stats.availableModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel!!
                }

                Box {
                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                        text = currentModelLabel,
                        isActive = isModelCustom,
                        onClick = { showModelDropdown = true }
                    )

                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                        modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)
                    ) {
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                            text = s.usageModelAll,
                            isSelected = !isModelCustom,
                            onClick = {
                                viewModel.setUsageSelectedModel("all")
                                showModelDropdown = false
                            }
                        )
                        if (stats.availableModels.isNotEmpty()) {
                            com.yuzhiqiang.antigravity.ui.components.StudioMenuDivider()
                            stats.availableModels.forEach { modelOpt ->
                                com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                                    text = modelOpt.displayName,
                                    subtitle = if (modelOpt.callCount > 0) "${modelOpt.callCount} calls" else null,
                                    isSelected = selectedModel == modelOpt.id,
                                    onClick = {
                                        viewModel.setUsageSelectedModel(modelOpt.id)
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. 来源下拉选择
                val isSourceCustom = !selectedSources.contains("all") && selectedSources.isNotEmpty()
                val currentSourceLabel = when {
                    selectedSources.contains("all") || selectedSources.isEmpty() -> s.usageSourceAll
                    selectedSources.size == 1 -> when (selectedSources.first()) {
                        "ide" -> s.usageSourceIde
                        "standalone" -> s.usageSourceApp
                        "cli" -> s.usageSourceCli
                        else -> selectedSources.first().uppercase()
                    }
                    else -> "${s.usageSourceAll} (${selectedSources.size})"
                }

                Box {
                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                        text = currentSourceLabel,
                        isActive = isSourceCustom,
                        onClick = { showSourceDropdown = true }
                    )

                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu(
                        expanded = showSourceDropdown,
                        onDismissRequest = { showSourceDropdown = false }
                    ) {
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                            text = s.usageSourceAll,
                            isSelected = selectedSources.contains("all"),
                            onClick = {
                                viewModel.toggleUsageSource("all")
                                showSourceDropdown = false
                            }
                        )
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                            text = s.usageSourceIde,
                            isSelected = selectedSources.contains("ide") && !selectedSources.contains("all"),
                            onClick = {
                                viewModel.toggleUsageSource("ide")
                                showSourceDropdown = false
                            }
                        )
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                            text = s.usageSourceApp,
                            isSelected = selectedSources.contains("standalone") && !selectedSources.contains("all"),
                            onClick = {
                                viewModel.toggleUsageSource("standalone")
                                showSourceDropdown = false
                            }
                        )
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                            text = s.usageSourceCli,
                            isSelected = selectedSources.contains("cli") && !selectedSources.contains("all"),
                            onClick = {
                                viewModel.toggleUsageSource("cli")
                                showSourceDropdown = false
                            }
                        )
                    }
                }

                // 3. 定时刷新下拉选择
                val isAutoRefreshActive = autoRefreshInterval > 0
                val autoRefreshLabel = if (autoRefreshInterval == 0) {
                    s.usageAutoRefreshOff
                } else {
                    s.usageAutoRefreshSeconds(autoRefreshInterval)
                }

                Box {
                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                        text = autoRefreshLabel,
                        leadingIcon = Icons.Outlined.Refresh,
                        isActive = isAutoRefreshActive,
                        onClick = { showAutoRefreshDropdown = true }
                    )

                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu(
                        expanded = showAutoRefreshDropdown,
                        onDismissRequest = { showAutoRefreshDropdown = false }
                    ) {
                        listOf(0, 5, 10, 30, 60).forEach { intervalSec ->
                            val itemLabel = if (intervalSec == 0) s.usageAutoRefreshOff else s.usageAutoRefreshSeconds(intervalSec)
                            com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem(
                                text = itemLabel,
                                isSelected = autoRefreshInterval == intervalSec,
                                onClick = {
                                    viewModel.setUsageAutoRefreshInterval(intervalSec)
                                    showAutoRefreshDropdown = false
                                }
                            )
                        }
                    }
                }

                // 4. 手动即时刷新按钮
                StudioTooltip(text = s.accountsRefreshAllTooltip) {
                    IconButton(
                        onClick = { viewModel.refreshUsageStats(force = true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
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

    if (showDateRangeDialog) {
        UsageDateRangePickerDialog(
            initialTimeRange = currentTimeRange,
            initialCustomRange = customDateRange,
            onSelectPresetRange = { preset ->
                viewModel.setUsageTimeRange(preset)
            },
            onConfirmCustomRange = { customRange ->
                viewModel.setUsageTimeRange(UsageTimeRange.CUSTOM, customRange)
            },
            onDismiss = { showDateRangeDialog = false }
        )
    }
}

/**
 * 现代毛玻璃时间分段选择胶囊 Tab (遵循 MD3 Segmented Control 规范)
 */
@Composable
private fun UsageTimePresetTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.65f else 0.85f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.65f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = Modifier
            .height(26.dp)
            .hoverable(interactionSource)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}
