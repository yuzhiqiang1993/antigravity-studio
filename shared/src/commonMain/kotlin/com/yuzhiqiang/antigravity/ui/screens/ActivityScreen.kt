package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.*
import com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.components.tour.tourAnchor
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tourManager = LocalSpotlightTourManager.current
    val config by viewModel.config.collectAsState()
    val logs by viewModel.activityLogs.collectAsState()
    val officialModels by viewModel.officialModels.collectAsState()
    val autoScroll = config.activityAutoScroll
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var activityFilter by remember { mutableStateOf(ActivityLogFilter()) }
    var filterResetKey by remember { mutableIntStateOf(0) }
    var selectedLog by remember { mutableStateOf<ActivityLog?>(null) }
    var showModelLatencyStats by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val normalizedQuery = searchQuery.trim()

    fun resetActivityFilters() {
        activityFilter = ActivityLogFilter()
        searchQuery = ""
        filterResetKey++
    }

    val filterCounts = rememberActivityFilterCounts(logs)
    val displayedLogs = rememberActivityDisplayedLogs(
        logs = logs,
        normalizedQuery = normalizedQuery,
        activityFilter = activityFilter,
        s = s
    )

    LaunchedEffect(displayedLogs.firstOrNull()?.id, autoScroll) {
        if (autoScroll && displayedLogs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    val statistics = rememberActivityStatistics(displayedLogs)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        PageHeader(title = s.activityTitle)

        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .tourAnchor(TourStep.ACTIVITY_PANEL, tourManager),
            shape = RoundedCornerShape(14.dp),
            color = StudioGlassTokens.cardBackgroundColor(isDark),
            shadowElevation = StudioGlassTokens.cardElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部工具栏：全文搜索 + 筛选弹窗按钮 + 自动滚动 + 清空日志
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = s.activitySearchPlaceholder,
                            modifier = Modifier.width(280.dp)
                        )

                        ActivityOnlyAiChatToggleButton(
                            isOnlyAiChat = activityFilter.onlyAiChat,
                            onClick = {
                                activityFilter = activityFilter.copy(onlyAiChat = !activityFilter.onlyAiChat)
                            },
                            s = s
                        )

                        ActivityFilterButton(
                            isFiltered = activityFilter.isActive,
                            filterCount = activityFilter.activeCount,
                            onClick = { showFilterDialog = true },
                            s = s
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setActivityAutoScroll(!autoScroll) },
                            modifier = Modifier.height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.topButtonHeight),
                            shape = RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (autoScroll) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (autoScroll) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (autoScroll) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            if (autoScroll) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            com.yuzhiqiang.antigravity.ui.theme.AppStatusColors.success,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = if (autoScroll) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = s.activityAutoScroll,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (autoScroll) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.clearActivityLogs()
                                resetActivityFilters()
                            },
                            modifier = Modifier.height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.topButtonHeight),
                            shape = RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = s.activityClear,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                if (activityFilter.isActive || normalizedQuery.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ActivityFilterSummaryRow(
                        filter = activityFilter,
                        shownCount = displayedLogs.size,
                        totalCount = logs.size,
                        onFilterChange = { activityFilter = it },
                        onResetAll = ::resetActivityFilters,
                        s = s
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 指标卡片（动态展示当前筛选范围下的总数、异常、首字耗时、输出速率、会话耗时、缓存命中率）
                ActivityMetricsRow(
                    totalCount = displayedLogs.size,
                    failedCount = statistics.failedCount,
                    averageFirstTokenMs = statistics.averageFirstTokenMs,
                    averageTps = statistics.averageTps,
                    averageDuration = statistics.averageDuration,
                    overallCacheHitRate = statistics.overallCacheHitRate,
                    filter = activityFilter,
                    onFilterChange = { activityFilter = it },
                    onShowModelLatencyStats = { showModelLatencyStats = true },
                    s = s
                )

                Spacer(Modifier.height(14.dp))

                if (displayedLogs.isEmpty()) {
                    EmptyStateView(
                        icon = if (logs.isEmpty()) Icons.Outlined.History else Icons.Outlined.SearchOff,
                        title = if (logs.isEmpty()) s.activityEmpty else s.activityNoMatchingLogs,
                        description = if (logs.isEmpty()) s.activityEmptyDesc else s.activityNoMatchingDesc,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedLogs, key = { it.id }) { log ->
                            ActivityLogRow(
                                log = log,
                                s = s,
                                searchQuery = searchQuery,
                                onClick = { selectedLog = log }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        com.yuzhiqiang.antigravity.ui.dialogs.ActivityDetailDialog(
            log = log,
            onDismiss = { selectedLog = null },
            onCopyNotice = { viewModel.showNotice(it) },
            onOpenNetworkSettings = viewModel::openNetworkSettings,
            isDebugMode = config.isDebugMode
        )
    }

    if (showModelLatencyStats) {
        com.yuzhiqiang.antigravity.ui.dialogs.ModelLatencyStatsDialog(
            logs = logs,
            config = config,
            officialModels = officialModels,
            onDismiss = { showModelLatencyStats = false }
        )
    }

    if (showFilterDialog) {
        ActivityFilterDialog(
            totalCount = logs.size,
            matchingCount = displayedLogs.size,
            clientCounts = filterCounts.clientCounts,
            endpointCounts = filterCounts.endpointCounts,
            routeCounts = filterCounts.routeCounts,
            statusCounts = filterCounts.statusCounts,
            filter = activityFilter,
            resetKey = filterResetKey,
            onFilterChange = { activityFilter = it },
            onResetAll = ::resetActivityFilters,
            onDismiss = { showFilterDialog = false },
            s = s
        )
    }
}
