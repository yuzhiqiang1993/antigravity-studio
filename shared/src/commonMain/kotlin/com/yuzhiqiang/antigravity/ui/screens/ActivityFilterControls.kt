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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@Composable
internal fun ActivityOnlyAiChatToggleButton(
    isOnlyAiChat: Boolean,
    onClick: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(StudioDesignTokens.Sizes.topButtonHeight),
        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isOnlyAiChat) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isOnlyAiChat) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isOnlyAiChat) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        @Suppress("DEPRECATION")
        Icon(
            imageVector = Icons.Outlined.Chat,
            contentDescription = null,
            tint = if (isOnlyAiChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppTokens.Size.iconSmall)
        )
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Text(
            text = s.activityFilterOnlyAiChat,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = StudioDesignTokens.TextSize.body,
                fontWeight = if (isOnlyAiChat) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun ActivityFilterButton(
    isFiltered: Boolean,
    filterCount: Int,
    onClick: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    val buttonLabel = if (isFiltered) {
        "${s.activityTagFilterTitle} ($filterCount)"
    } else {
        s.activityTagFilterTitle
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(StudioDesignTokens.Sizes.topButtonHeight),
        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
        contentPadding = PaddingValues(horizontal = StudioDesignTokens.Padding.topBarVertical, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isFiltered) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isFiltered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isFiltered) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            tint = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppTokens.Size.iconSmall)
        )
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Text(
            text = buttonLabel,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = StudioDesignTokens.TextSize.body,
                fontWeight = if (isFiltered) FontWeight.SemiBold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun ActivityFilterSummaryRow(
    filter: ActivityLogFilter,
    shownCount: Int,
    totalCount: Int,
    onFilterChange: (ActivityLogFilter) -> Unit,
    onResetAll: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (filter.onlyAiChat) {
                ActivityFilterSummaryChip(label = "仅看会话生成") {
                    onFilterChange(filter.copy(onlyAiChat = false))
                }
            }
            if (filter.clients.isNotEmpty()) {
                val label = filter.clients.singleOrNull()?.let { activityClientLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterClients, filter.clients.size)
                ActivityFilterSummaryChip(label = label) {
                    onFilterChange(filter.copy(clients = emptySet()))
                }
            }
            if (filter.statuses.isNotEmpty()) {
                val label = filter.statuses.singleOrNull()?.let { activityStatusLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterStatuses, filter.statuses.size)
                ActivityFilterSummaryChip(label = label) {
                    onFilterChange(filter.copy(statuses = emptySet()))
                }
            }
            if (filter.routes.isNotEmpty()) {
                val label = filter.routes.singleOrNull()?.let { activityRouteLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterRoutes, filter.routes.size)
                ActivityFilterSummaryChip(label = label) {
                    onFilterChange(filter.copy(routes = emptySet()))
                }
            }
            if (filter.endpoints.isNotEmpty()) {
                val label = filter.endpoints.singleOrNull()?.let(::compactPath)
                    ?: s.activityFilterSelectedDimension(s.activityFilterEndpoints, filter.endpoints.size)
                ActivityFilterSummaryChip(label = label, maxWidth = 240.dp) {
                    onFilterChange(filter.copy(endpoints = emptySet()))
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenRows)
        ) {
            Text(
                text = s.activityFilterMatches(shownCount, totalCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = s.activityFilterResetAll,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onResetAll)
            )
        }
    }
}


@Composable
private fun ActivityFilterSummaryChip(
    label: String,
    maxWidth: androidx.compose.ui.unit.Dp = 180.dp,
    onClear: () -> Unit
) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.badge,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                modifier = Modifier
                    .size(AppTokens.Size.iconSmall)
                    .pointerHoverIcon(PointerIcon.Hand)
            )
        },
        modifier = Modifier
            .height(28.dp)
            .widthIn(max = maxWidth)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = true,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            selectedBorderWidth = 1.dp
        )
    )
}



internal fun activityClientLabel(kind: ActivityClientKind, s: Strings): String = when (kind) {
    ActivityClientKind.IDE -> s.activityFilterClientIde
    ActivityClientKind.CLI -> s.activityFilterClientCli
    ActivityClientKind.APP -> s.activityFilterClientApp
    ActivityClientKind.PLUGIN -> s.activityFilterClientPlugin
    ActivityClientKind.OTHER -> s.activityFilterOtherClient
}


internal fun activityStatusLabel(status: ActivityStatusKind, s: Strings): String = when (status) {
    ActivityStatusKind.SUCCESS -> s.activityFilterSuccess
    ActivityStatusKind.FAILED -> s.activityFilterFailedStatus
    ActivityStatusKind.PENDING -> s.activityPending
    ActivityStatusKind.RETRIED -> s.activityFilterRetried
}

internal fun activityRouteLabel(route: String, s: Strings): String = when (route) {
    OFFICIAL_ROUTE_KEY -> s.activityPassthrough
    UNKNOWN_ROUTE_KEY -> s.activityUnknownProvider
    else -> route
}

private fun compactPath(path: String): String {
    val clean = cleanEndpointDisplayPath(path)
    if (clean.length <= 28) return clean
    return "…${clean.takeLast(27)}"
}
