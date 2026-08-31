package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.splitDuration
import com.yuzhiqiang.antigravity.ui.utils.splitHitRate
import com.yuzhiqiang.antigravity.ui.utils.splitTps
import com.yuzhiqiang.antigravity.ui.utils.toColor

@Composable
internal fun ActivityMetricsRow(
    totalCount: Int,
    failedCount: Int,
    averageFirstTokenMs: Long,
    averageTps: Double?,
    averageDuration: Long,
    overallCacheHitRate: Double?,
    filter: ActivityLogFilter,
    onFilterChange: (ActivityLogFilter) -> Unit,
    onShowModelLatencyStats: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    val avgTtftTier = getFirstTokenLatencyTier(averageFirstTokenMs)
    val avgDurationTier = getDurationLatencyTier(averageDuration)

    val ttftSplit = splitDuration(averageFirstTokenMs)
    val tpsSplit = splitTps(averageTps)
    val durationSplit = splitDuration(averageDuration)
    val hitRateSplit = splitHitRate(overallCacheHitRate)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActivityMetricCard(
            icon = Icons.Outlined.Layers,
            label = s.activityTotal,
            value = totalCount.toString(),
            unit = null,
            selected = filter.statuses.isEmpty(),
            onClick = { onFilterChange(filter.copy(statuses = emptySet())) },
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            icon = Icons.Outlined.ErrorOutline,
            label = s.activityFailedTotal,
            value = failedCount.toString(),
            unit = null,
            isWarning = failedCount > 0,
            selected = filter.statuses == setOf(ActivityStatusKind.FAILED),
            onClick = {
                onFilterChange(filter.copy(statuses = setOf(ActivityStatusKind.FAILED)))
            },
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            icon = Icons.Outlined.Bolt,
            label = s.activityAvgTtft,
            value = ttftSplit?.first ?: "--",
            unit = ttftSplit?.second,
            customValueColor = if (averageFirstTokenMs > 0) avgTtftTier?.toColor() else null,
            hasDrillDown = true,
            onClick = onShowModelLatencyStats,
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            icon = Icons.Outlined.Speed,
            label = s.activityAvgTps,
            value = tpsSplit?.first ?: "--",
            unit = tpsSplit?.second,
            customValueColor = if (averageTps != null && averageTps > 0.0) MaterialTheme.colorScheme.primary else null,
            hasDrillDown = true,
            onClick = onShowModelLatencyStats,
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            icon = Icons.Outlined.Timer,
            label = s.activityAvgDuration,
            value = durationSplit?.first ?: "--",
            unit = durationSplit?.second,
            customValueColor = if (averageDuration > 0) avgDurationTier?.toColor() else null,
            hasDrillDown = true,
            onClick = onShowModelLatencyStats,
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            icon = Icons.Outlined.Storage,
            label = s.activityCacheHitRate,
            value = hitRateSplit?.first ?: "--",
            unit = hitRateSplit?.second,
            customValueColor = getCacheHitRateColor(overallCacheHitRate),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActivityMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String? = null,
    isWarning: Boolean = false,
    selected: Boolean = false,
    customValueColor: Color? = null,
    hasDrillDown: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isClickable = onClick != null
    val containerColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHovered && isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }

    val borderColor = when {
        selected && isWarning -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        isHovered && isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = labelColor.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hasDrillDown) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowOutward,
                        contentDescription = null,
                        modifier = Modifier.size(10.5.dp),
                        tint = labelColor.copy(alpha = if (isHovered) 0.85f else 0.32f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!unit.isNullOrBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = valueColor.copy(alpha = 0.72f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}
