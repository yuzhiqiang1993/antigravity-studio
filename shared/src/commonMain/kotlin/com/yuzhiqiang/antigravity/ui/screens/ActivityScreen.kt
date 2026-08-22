package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.EmptyStateView
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    val displayedLogs = logs.filter { log ->
        val matchesQuery = normalizedQuery.isBlank() || listOfNotNull(
            log.modelId,
            log.providerName,
            log.path,
            log.errorMessage
        ).any { it.lowercase().contains(normalizedQuery) }
        matchesQuery && (!filterOnlyFailed || log.statusCode >= 400)
    }
    val failedCount = logs.count { it.statusCode >= 400 }

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
                    shape = RoundedCornerShape(AppTokens.Radius.medium)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.Size.iconMedium)
                    )
                    Spacer(Modifier.width(AppTokens.Spacing.xs))
                    Text(
                        text = s.activityClear,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        )

        StudioCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppTokens.Spacing.card)
            ) {
                // 搜索与过滤筛选栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = s.activitySearchPlaceholder,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.Size.iconMedium)
                            )
                        },
                        modifier = Modifier.width(AppTokens.Size.modelSearchFieldWidth),
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                        FilterChip(
                            selected = !filterOnlyFailed,
                            onClick = { filterOnlyFailed = false },
                            label = {
                                Text(
                                    text = "${s.activityFilterAll} (${logs.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        FilterChip(
                            selected = filterOnlyFailed,
                            onClick = { filterOnlyFailed = true },
                            label = {
                                Text(
                                    text = "${s.activityFilterFailed} ($failedCount)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(AppTokens.Spacing.md))

                if (displayedLogs.isEmpty()) {
                    EmptyStateView(
                        icon = if (logs.isEmpty()) Icons.Outlined.History else Icons.Outlined.SearchOff,
                        title = if (logs.isEmpty()) s.activityEmpty else "未找到匹配日志",
                        description = if (logs.isEmpty()) "当 Antigravity 发起模型代理调用时，此处将实时展示调用明细" else "尝试输入其他关键词或清除筛选条件",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        items(displayedLogs, key = { it.id }) { log ->
                            ActivityLogRow(
                                log = log,
                                s = s,
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
private fun ActivityLogRow(
    log: ActivityLog,
    s: Strings,
    onClick: () -> Unit
) {
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppTokens.Radius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.content),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        Text(
                            text = log.method.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = log.path,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${log.durationMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
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
                    Text(
                        text = "${log.providerName ?: "未知服务商"} / ${log.modelId ?: "未知模型"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = routeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!log.errorMessage.isNullOrBlank()) {
                    Text(
                        text = log.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

private fun formatLogTime(timestampMs: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timestampMs)
        val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val h = ldt.hour.toString().padStart(2, '0')
        val m = ldt.minute.toString().padStart(2, '0')
        val s = ldt.second.toString().padStart(2, '0')
        "$h:$m:$s"
    } catch (_: Exception) {
        "-"
    }
}
