package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.*
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.utils.LatencyTier
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatTokens
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val config by viewModel.config.collectAsState()
    val logs by viewModel.activityLogs.collectAsState()
    val autoScroll = config.activityAutoScroll
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyFailed by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<ActivityLog?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    val normalizedQuery = searchQuery.trim().lowercase()

    val availableTagsWithCounts = remember(logs, s) {
        logs.groupingBy { if (it.isOfficialPassthrough) s.activityPassthrough else (it.providerName ?: s.activityUnknownProvider) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    val displayedLogs = remember(logs, normalizedQuery, filterOnlyFailed, selectedTags) {
        logs.filter { log ->
            val tag = if (log.isOfficialPassthrough) s.activityPassthrough else (log.providerName ?: s.activityUnknownProvider)
            val matchesTag = selectedTags.isEmpty() || tag in selectedTags
            val matchesQuery = normalizedQuery.isBlank() || listOfNotNull(
                log.modelId,
                log.requestedModelId,
                if (log.isOfficialPassthrough) s.activityPassthrough else log.providerName,
                log.path,
                log.errorMessage
            ).any { it.lowercase().contains(normalizedQuery) }
            val matchesFailed = !filterOnlyFailed || log.statusCode >= 400
            matchesTag && matchesQuery && matchesFailed
        }
    }

    LaunchedEffect(displayedLogs.firstOrNull()?.id, autoScroll) {
        if (autoScroll && displayedLogs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    val failedCount = remember(logs) { logs.count { !it.isPending && it.statusCode >= 400 } }
    val averageDuration = remember(logs) {
        logs.filter { !it.isPending && it.statusCode > 0 }
            .takeIf { it.isNotEmpty() }
            ?.map { it.durationMs }
            ?.average()
            ?.toLong() ?: 0L
    }
    val totalInputTokens = remember(logs) { logs.mapNotNull { it.inputTokens }.sum() }
    val totalCacheReadTokens = remember(logs) { logs.mapNotNull { it.cacheReadTokens }.sum() }
    val totalCacheWriteTokens = remember(logs) { logs.mapNotNull { it.cacheWriteTokens }.sum() }
    val overallCacheHitRate = remember(totalInputTokens, totalCacheReadTokens, totalCacheWriteTokens) {
        calculateCacheHitRate(totalCacheReadTokens, totalInputTokens, totalCacheWriteTokens)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        PageHeader(title = s.activityTitle)

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部工具栏：搜索框 + Tag 多选筛选器 + 清空操作
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

                        TagFilterDropdown(
                            availableTagsWithCounts = availableTagsWithCounts,
                            selectedTags = selectedTags,
                            onTagToggle = { tag ->
                                val allTagNames = availableTagsWithCounts.map { it.first }.toSet()
                                val effective = if (selectedTags.isEmpty()) allTagNames else selectedTags
                                val next = if (tag in effective) effective - tag else effective + tag
                                selectedTags = if (next.isEmpty() || next.size == allTagNames.size) emptySet() else next
                            },
                            onSelectAll = { selectedTags = emptySet() },
                            onClearAll = { selectedTags = emptySet() },
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
                                selectedTags = emptySet()
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 指标卡片（前两项支持点击快速切换筛选）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActivityMetricCard(
                        label = s.activityTotal,
                        value = logs.size.toString(),
                        selected = !filterOnlyFailed,
                        onClick = { filterOnlyFailed = false },
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricCard(
                        label = s.activityFailedTotal,
                        value = failedCount.toString(),
                        isWarning = failedCount > 0,
                        selected = filterOnlyFailed,
                        onClick = { filterOnlyFailed = true },
                        modifier = Modifier.weight(1f)
                    )
                    val avgTier = getDurationLatencyTier(averageDuration)
                    ActivityMetricCard(
                        label = s.activityAverage,
                        value = formatDuration(averageDuration),
                        customValueColor = if (averageDuration > 0) avgTier.toColor() else null,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricCard(
                        label = s.activityCacheHitRate,
                        value = formatHitRate(overallCacheHitRate),
                        customValueColor = getCacheHitRateColor(overallCacheHitRate),
                        modifier = Modifier.weight(1f)
                    )
                }

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
            onCopyNotice = { viewModel.showNotice(it) }
        )
    }
}

@Composable
private fun ActivityMetricCard(
    label: String,
    value: String,
    isWarning: Boolean = false,
    selected: Boolean = false,
    customValueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isClickable = onClick != null
    val containerColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHovered && isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val borderColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        isHovered && isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val borderWidth = if (selected) 1.5.dp else 1.dp

    val labelColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        isWarning -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val valueColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        customValueColor != null -> customValueColor
        isWarning -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val cardModifier = modifier
        .then(
            if (isClickable) {
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
            } else {
                Modifier
            }
        )

    OutlinedCard(
        modifier = cardModifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = labelColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = valueColor
            )
        }
    }
}

@Composable
private fun ActivityLogRow(
    log: ActivityLog,
    s: Strings,
    searchQuery: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val statusColors = AppStatusColors
    val isSuccess = log.statusCode in 200..399
    val statusColor = when {
        log.isPending -> MaterialTheme.colorScheme.primary
        isSuccess -> statusColors.success
        else -> statusColors.error
    }
    val statusTone = when {
        log.isPending -> BadgeTone.INFO
        log.statusCode in 200..299 -> BadgeTone.SUCCESS
        log.statusCode in 300..499 -> BadgeTone.WARNING
        else -> BadgeTone.ERROR
    }
    val statusText = if (log.isPending) s.activityPending else "${log.statusCode}"
    val routeLabel = when {
        log.fallbackSucceeded -> s.activityFallback
        log.fallbackAttempted -> s.activityFallbackFailed
        else -> null
    }
    val time = formatLogTime(log.timestamp)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = log.method.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        HighlightedText(
                            text = log.path,
                            query = searchQuery,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (log.isPending) {
                            if (log.firstTokenMs != null) {
                                val ttftTier = getFirstTokenLatencyTier(log.firstTokenMs)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = s.activityFirstTokenLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = formatDuration(log.firstTokenMs),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = ttftTier.toColor()
                                    )
                                }
                            }
                            Text(
                                text = s.activityProcessing,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            if (log.firstTokenMs != null) {
                                val ttftTier = getFirstTokenLatencyTier(log.firstTokenMs)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = s.activityFirstTokenLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = formatDuration(log.firstTokenMs),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = ttftTier.toColor()
                                    )
                                }
                            }
                            val durationTier = getDurationLatencyTier(log.durationMs)
                            Text(
                                text = formatDuration(log.durationMs),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = durationTier.toColor(defaultColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                        StatusBadge(
                            text = statusText,
                            tone = statusTone,
                            showDot = log.isPending,
                            pulse = log.isPending
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subtitleText = when {
                        log.modelId != null && !log.isOfficialPassthrough -> "${log.providerName ?: s.activityUnknownProvider} / ${log.modelId}"
                        log.modelId != null -> log.modelId
                        log.isOfficialPassthrough -> s.activityPassthrough
                        else -> log.providerName.orEmpty()
                    }
                    if (subtitleText.isNotEmpty()) {
                        HighlightedText(
                            text = subtitleText,
                            query = searchQuery,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hasDetailedTokens = log.inputTokens != null || log.outputTokens != null
                        val hasAnyTokens = log.totalTokens != null || hasDetailedTokens

                        if (hasAnyTokens) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (log.inputTokens != null && log.inputTokens > 0) {
                                    Text(
                                        text = "${s.activityTokenInput} ${formatTokens(log.inputTokens)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                if (log.outputTokens != null && log.outputTokens > 0) {
                                    Text(
                                        text = "${s.activityTokenOutput} ${formatTokens(log.outputTokens)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                if (log.cacheReadTokens != null && log.cacheReadTokens > 0) {
                                    val hitRate = calculateCacheHitRate(log.cacheReadTokens, log.inputTokens, log.cacheWriteTokens)
                                    val hitRateText = if (hitRate != null) " (${formatHitRate(hitRate)})" else ""
                                    Text(
                                        text = "${s.activityTokenCache} ${formatTokens(log.cacheReadTokens)}$hitRateText",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = getCacheHitRateColor(hitRate, defaultColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f))
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                val total = log.totalTokens ?: ((log.inputTokens ?: 0L) + (log.outputTokens ?: 0L)).takeIf { it > 0 }
                                if (total != null && total > 0) {
                                    Text(
                                        text = "${s.activityTokenTotal} ${formatTokens(total)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                        if (routeLabel != null) {
                            Text(
                                text = routeLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = if (log.fallbackSucceeded) AppStatusColors.success else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (!log.errorMessage.isNullOrBlank()) {
                    HighlightedText(
                        text = log.errorMessage,
                        query = searchQuery,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3
                    )
                }
            }
        }
    }
}

private fun formatLogTime(timestampMs: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS")
        sdf.format(java.util.Date(timestampMs))
    } catch (_: Exception) {
        "-"
    }
}

@Composable
private fun TagFilterDropdown(
    availableTagsWithCounts: List<Pair<String, Int>>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedTags.isNotEmpty()

    val buttonLabel = when {
        selectedTags.isEmpty() -> s.activityAllTags
        selectedTags.size == 1 -> selectedTags.first()
        else -> s.activitySelectedTagsCount(selectedTags.size)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor = if (isFiltered) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else if (isHovered) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isFiltered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val textColor = if (isFiltered) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.topButtonHeight)
                .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm))
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded }
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppTokens.Size.iconSmall)
            )

            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body,
                    fontWeight = if (isFiltered) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = textColor,
                maxLines = 1
            )

            if (isFiltered) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onClearAll() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = s.activityClearFilter,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppTokens.Size.iconSmall)
                )
            }
        }

        StudioDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 260.dp, max = 340.dp)
        ) {
            // Header: Title + Select All / Reset Actions
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${s.activityTagFilterTitle} (${availableTagsWithCounts.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = s.activitySelectAll,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onSelectAll() }
                        )
                        if (isFiltered) {
                            Text(
                                text = s.activityClearFilter,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onClearAll() }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (availableTagsWithCounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.activityEmpty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    availableTagsWithCounts.forEach { (tag, count) ->
                        val isExplicitChecked = tag in selectedTags

                        val itemInteractionSource = remember { MutableInteractionSource() }
                        val isItemHovered by itemInteractionSource.collectIsHoveredAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isItemHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .hoverable(itemInteractionSource)
                                .clickable(
                                    interactionSource = itemInteractionSource,
                                    indication = null,
                                    onClick = { onTagToggle(tag) }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = if (selectedTags.isEmpty()) true else isExplicitChecked,
                                    onCheckedChange = { onTagToggle(tag) },
                                    modifier = Modifier.size(20.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isExplicitChecked) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isExplicitChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
