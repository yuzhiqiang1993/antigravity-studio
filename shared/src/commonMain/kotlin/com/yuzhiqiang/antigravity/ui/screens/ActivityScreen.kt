package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val logs by viewModel.activityLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyFailed by remember { mutableStateOf(false) }
    val normalizedQuery = searchQuery.trim().lowercase()

    var selectedLog by remember { mutableStateOf<ActivityLog?>(null) }

    val displayedLogs = remember(logs, normalizedQuery, filterOnlyFailed) {
        logs.filter { log ->
            val matchesQuery = normalizedQuery.isBlank() || listOfNotNull(
                log.modelId,
                log.requestedModelId,
                log.providerName,
                log.path,
                log.errorMessage
            ).any { it.lowercase().contains(normalizedQuery) }
            matchesQuery && (!filterOnlyFailed || log.statusCode >= 400)
        }
    }
    val failedCount = remember(logs) { logs.count { it.statusCode >= 400 } }
    val averageDuration = remember(logs) {
        logs.takeIf { it.isNotEmpty() }
            ?.map { it.durationMs }
            ?.average()
            ?.toLong() ?: 0L
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        PageHeader(
            title = s.activityTitle,
            subtitle = s.activitySubtitle,
            action = {
                OutlinedButton(
                    onClick = { viewModel.clearActivityLogs() },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
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
        )

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
                // 顶部工具栏：紧凑搜索框 + M3 SingleChoiceSegmentedButtonRow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = s.activitySearchPlaceholder,
                        modifier = Modifier.width(320.dp)
                    )

                    // M3 标准 SingleChoiceSegmentedButtonRow（设置充足最小宽度确保文字完整显示）
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.height(34.dp)
                    ) {
                        SegmentedButton(
                            selected = !filterOnlyFailed,
                            onClick = { filterOnlyFailed = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {},
                            modifier = Modifier.defaultMinSize(minWidth = 110.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "${s.activityFilterAll} (${logs.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (!filterOnlyFailed) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        SegmentedButton(
                            selected = filterOnlyFailed,
                            onClick = { filterOnlyFailed = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                            modifier = Modifier.defaultMinSize(minWidth = 110.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = if (failedCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = if (failedCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = "${s.activityFilterFailed} ($failedCount)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (filterOnlyFailed || failedCount > 0) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 指标卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActivityMetricCard(
                        label = s.activityTotal,
                        value = logs.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricCard(
                        label = s.activityFailedTotal,
                        value = failedCount.toString(),
                        isWarning = failedCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricCard(
                        label = s.activityAverage,
                        value = "$averageDuration ms",
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
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            1.dp,
            if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
    val statusColor = if (isSuccess) statusColors.success else statusColors.error
    val statusTone = when {
        log.statusCode in 200..299 -> BadgeTone.SUCCESS
        log.statusCode in 300..499 -> BadgeTone.WARNING
        else -> BadgeTone.ERROR
    }

    val baseRouteLabel = if (log.isOfficialPassthrough) s.activityPassthrough else s.activityRouted
    val routeLabel = when {
        log.fallbackSucceeded -> "${s.activityFallback} · $baseRouteLabel"
        log.fallbackAttempted -> "${s.activityFallbackFailed} · $baseRouteLabel"
        else -> baseRouteLabel
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
                        Text(
                            text = "${log.durationMs} ms",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatusBadge(
                            text = "${log.statusCode}",
                            tone = statusTone,
                            showDot = false
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subtitleText = when {
                        log.modelId != null -> "${log.providerName ?: s.activityUnknownProvider} / ${log.modelId}"
                        log.isOfficialPassthrough -> log.providerName ?: "Official Cloud Code"
                        else -> log.providerName ?: s.activityUnknownProvider
                    }
                    HighlightedText(
                        text = subtitleText,
                        query = searchQuery,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (log.totalTokens != null && log.totalTokens > 0) {
                            Text(
                                text = "Tokens: ${log.totalTokens}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                        Text(
                            text = routeLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
