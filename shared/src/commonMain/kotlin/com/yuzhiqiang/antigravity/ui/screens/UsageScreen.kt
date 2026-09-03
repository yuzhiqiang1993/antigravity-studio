package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.yuzhiqiang.antigravity.ui.components.StudioSlidingTabLayout
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
import com.yuzhiqiang.antigravity.ui.components.StudioTooltip
import com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.components.tour.tourAnchor
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.usage.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tourManager = LocalSpotlightTourManager.current
    val stats by viewModel.usageStats.collectAsState()
    val isRefreshing by viewModel.isRefreshingUsage.collectAsState()
    val isInitialLoading by viewModel.isUsageInitialLoading.collectAsState()
    val currentTimeRange by viewModel.usageTimeRange.collectAsState()
    val customDateRange by viewModel.usageCustomDateRange.collectAsState()
    val selectedSources by viewModel.usageSelectedSources.collectAsState()
    val selectedModel by viewModel.usageSelectedModel.collectAsState()
    val autoRefreshInterval by viewModel.usageAutoRefreshInterval.collectAsState()

    val isCustomRange = currentTimeRange == UsageTimeRange.CUSTOM
    val activeCustomRange = customDateRange?.takeIf { isCustomRange && it.startDate.isNotBlank() }
    val customDateLabel = remember(activeCustomRange, s) {
        if (activeCustomRange != null) {
            val startFmt = UsageNumberFormatter.formatShortDate(activeCustomRange.startDate)
            val endFmt = if (activeCustomRange.followNow || activeCustomRange.endDate.isBlank()) {
                s.usagePresetToday
            } else {
                UsageNumberFormatter.formatShortDate(activeCustomRange.endDate)
            }
            "$startFmt~$endFmt"
        } else {
            s.usageTimeRangeCustom
        }
    }

    val timePresets = remember(s, customDateLabel) {
        listOf(
            StudioTabItem(UsageTimeRange.CALENDAR_TODAY, s.usagePresetToday),
            StudioTabItem(UsageTimeRange.ROLLING_24H, s.usagePreset1Day),
            StudioTabItem(UsageTimeRange.ROLLING_7D, s.usagePreset7Days),
            StudioTabItem(UsageTimeRange.ROLLING_14D, s.usagePreset14Days),
            StudioTabItem(UsageTimeRange.ROLLING_30D, s.usagePreset30Days),
            StudioTabItem(
                key = UsageTimeRange.CUSTOM,
                title = customDateLabel,
                icon = Icons.Outlined.CalendarMonth,
                trailingIcon = Icons.Outlined.KeyboardArrowDown
            )
        )
    }

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
        PageHeader(title = s.navUsage)

        // 2. 现代毛玻璃浮岛顶栏操作栏 (与 ModelsScreen、AccountsScreen 保持一致的 StudioGlassSurface)
        StudioGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .tourAnchor(TourStep.USAGE_PANEL, tourManager),
            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = StudioDesignTokens.Padding.topBarHorizontal,
                        vertical = StudioDesignTokens.Padding.topBarVertical
                    )
                    .horizontalScroll(controlsScrollState),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧一体化时间选择器：[今天 | 1 天 | 7 天 | 14 天 | 30 天 | 📅 自定义 ⌄]
                StudioTooltip(text = s.usageDateRangeTooltip, enabled = !showDateRangeDialog) {
                    StudioSlidingTabLayout(
                        items = timePresets,
                        selectedKey = currentTimeRange,
                        onSelect = { range ->
                            if (range == UsageTimeRange.CUSTOM) {
                                showDateRangeDialog = true
                            } else {
                                viewModel.setUsageTimeRange(range)
                            }
                        },
                        tabHeight = 34.dp,
                        scrollable = false
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
                    stats.availableModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel.orEmpty()
                }

                Box {
                    StudioTooltip(text = s.usageFilterModelTooltip, enabled = !showModelDropdown) {
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                            text = currentModelLabel,
                            isActive = isModelCustom,
                            onClick = { showModelDropdown = !showModelDropdown }
                        )
                    }

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
                    StudioTooltip(text = s.usageFilterSourceTooltip, enabled = !showSourceDropdown) {
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                            text = currentSourceLabel,
                            isActive = isSourceCustom,
                            onClick = { showSourceDropdown = !showSourceDropdown }
                        )
                    }

                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu(
                        expanded = showSourceDropdown,
                        onDismissRequest = { showSourceDropdown = false },
                        modifier = Modifier.widthIn(min = 140.dp)
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
                    StudioTooltip(text = s.usageAutoRefreshTooltip, enabled = !showAutoRefreshDropdown) {
                        com.yuzhiqiang.antigravity.ui.components.StudioDropdownTrigger(
                            text = autoRefreshLabel,
                            leadingIcon = Icons.Outlined.Refresh,
                            isActive = isAutoRefreshActive,
                            onClick = { showAutoRefreshDropdown = !showAutoRefreshDropdown }
                        )
                    }

                    com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu(
                        expanded = showAutoRefreshDropdown,
                        onDismissRequest = { showAutoRefreshDropdown = false },
                        modifier = Modifier.widthIn(min = 120.dp)
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
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    Surface(
                        onClick = { viewModel.refreshUsageStats(force = true) },
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isHovered -> if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer
                            else -> if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isHovered -> if (isDark) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            }
                        ),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .size(34.dp)
                            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
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
                                    tint = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // 3. 滚动主看板区域：仅在首轮冷启动且尚无任何数据时展示骨架屏，后续静默刷新绝不闪回骨架屏
        val showSkeleton = isInitialLoading && stats.totalTokens == 0L && stats.totalCalls == 0L && stats.totalConversations == 0L

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
        ) {
            com.yuzhiqiang.antigravity.ui.animation.StudioCrossfade(
                targetState = showSkeleton,
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
                            timeRange = stats.timeRange
                        )

                        // E. 热门模型使用排行
                        TopModelsBreakdownCard(
                            modelBuckets = stats.modelBuckets,
                            cnyRate = stats.cnyRate
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
