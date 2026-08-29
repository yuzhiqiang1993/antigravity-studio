package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatHitRate
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

@Composable
internal fun ActivityMetricsRow(
    totalCount: Int,
    failedCount: Int,
    averageDuration: Long,
    overallCacheHitRate: Double?,
    filter: ActivityLogFilter,
    onFilterChange: (ActivityLogFilter) -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    val avgTier = getDurationLatencyTier(averageDuration)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActivityMetricCard(
            label = s.activityTotal,
            value = totalCount.toString(),
            selected = filter.statuses.isEmpty(),
            onClick = { onFilterChange(filter.copy(statuses = emptySet())) },
            modifier = Modifier.weight(1f)
        )
        ActivityMetricCard(
            label = s.activityFailedTotal,
            value = failedCount.toString(),
            isWarning = failedCount > 0,
            selected = filter.statuses == setOf(ActivityStatusKind.FAILED),
            onClick = {
                onFilterChange(filter.copy(statuses = setOf(ActivityStatusKind.FAILED)))
            },
            modifier = Modifier.weight(1f)
        )
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
