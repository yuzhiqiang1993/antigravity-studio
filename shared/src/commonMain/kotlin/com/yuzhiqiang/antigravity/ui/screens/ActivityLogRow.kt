package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.HighlightedText
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatTokens
import com.yuzhiqiang.antigravity.ui.utils.formatTps
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

@Composable
internal fun ActivityLogRow(
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
    val time = formatLogTime(log.timestamp)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isHovered) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isHovered) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
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

                        if (log.retryCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = s.activityRetryBadge(log.retryCount),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )

                        if (log.isPending) {
                            if (log.firstTokenMs != null) {
                                val ttftTier = getFirstTokenLatencyTier(log.firstTokenMs)
                                MetricPill(
                                    icon = Icons.Outlined.Bolt,
                                    text = formatDuration(log.firstTokenMs),
                                    color = ttftTier.toColor()
                                )
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
                                MetricPill(
                                    icon = Icons.Outlined.Bolt,
                                    text = formatDuration(log.firstTokenMs),
                                    color = ttftTier.toColor()
                                )
                            }
                            if (log.tokensPerSecond != null && log.tokensPerSecond > 0.0) {
                                MetricPill(
                                    icon = Icons.Outlined.Speed,
                                    text = formatTps(log.tokensPerSecond),
                                    color = MaterialTheme.colorScheme.secondary
                                )
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

                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        log.clientSource?.takeIf { it.isNotBlank() }?.let { client ->
                            ClientSourceBadge(client)
                        }
                        if (subtitleText.isNotEmpty()) {
                            HighlightedText(
                                text = subtitleText,
                                query = searchQuery,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                    val tpsSuffix = if (log.tokensPerSecond != null && log.tokensPerSecond > 0.0) {
                                        " (${formatTps(log.tokensPerSecond)})"
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "${s.activityTokenOutput} ${formatTokens(log.outputTokens)}$tpsSuffix",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (log.tokensPerSecond != null && log.tokensPerSecond > 0.0) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        }
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                if (log.cacheReadTokens != null && log.cacheReadTokens > 0) {
                                    val hitRate = calculateCacheHitRate(
                                        log.cacheReadTokens,
                                        log.inputTokens,
                                        log.cacheWriteTokens
                                    )
                                    val hitRateText = if (hitRate != null) " (${formatHitRate(hitRate)})" else ""
                                    Text(
                                        text = "${s.activityTokenCache} ${formatTokens(log.cacheReadTokens)}$hitRateText",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = getCacheHitRateColor(
                                            hitRate,
                                            defaultColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                                        )
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                val total = log.totalTokens ?: ((log.inputTokens ?: 0L) + (log.outputTokens
                                    ?: 0L)).takeIf { it > 0 }
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

@Composable
private fun MetricPill(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = color
            )
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
private fun ClientSourceBadge(
    clientSource: String,
    modifier: Modifier = Modifier
) {
    val isPlugin = "Plugin" in clientSource || "Cockpit" in clientSource
    val isIde = "IDE" in clientSource
    val isApp = "App" in clientSource
    val isCli = "CLI" in clientSource || "agy" in clientSource.lowercase()

    val (bg, textColor) = when {
        isPlugin -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f) to MaterialTheme.colorScheme.onTertiaryContainer
        isIde -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) to MaterialTheme.colorScheme.onPrimaryContainer
        isApp -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f) to MaterialTheme.colorScheme.onSecondaryContainer
        isCli -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f) to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = clientSource,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}
