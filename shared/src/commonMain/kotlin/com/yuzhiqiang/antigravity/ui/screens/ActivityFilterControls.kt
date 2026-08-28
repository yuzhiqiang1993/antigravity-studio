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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@Composable
internal fun ActivityFilterDropdown(
    totalCount: Int,
    matchingCount: Int,
    clientCounts: Map<ActivityClientKind, Int>,
    endpointCounts: List<Pair<String, Int>>,
    routeCounts: List<Pair<String, Int>>,
    statusCounts: Map<ActivityStatusKind, Int>,
    filter: ActivityLogFilter,
    resetKey: Int,
    onFilterChange: (ActivityLogFilter) -> Unit,
    onResetAll: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var endpointSearch by remember { mutableStateOf("") }
    LaunchedEffect(resetKey) {
        endpointSearch = ""
    }

    val isFiltered = filter.isActive
    val buttonLabel = if (isFiltered) {
        "${s.activityTagFilterTitle} (${filter.activeCount})"
    } else {
        s.activityTagFilterTitle
    }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(StudioDesignTokens.Sizes.topButtonHeight),
            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
            contentPadding = PaddingValues(horizontal = StudioDesignTokens.Padding.topBarVertical, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isFiltered) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
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
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
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
            Spacer(Modifier.width(AppTokens.Spacing.xxs))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppTokens.Size.iconSmall)
            )
        }

        StudioDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(660.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = StudioDesignTokens.Padding.spaceBetweenColumns,
                        vertical = StudioDesignTokens.Padding.innerBlock
                    ),
                verticalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenRows)
            ) {
                // 面板 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                        Text(
                            text = s.activityTagFilterTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.activityFilterMatches(matchingCount, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isFiltered) {
                        Text(
                            text = s.activityFilterResetAll,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    endpointSearch = ""
                                    onResetAll()
                                }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                val orderedRoutes = remember(routeCounts, filter.routes) {
                    routeCounts.sortedWith(
                        compareByDescending<Pair<String, Int>> { it.first in filter.routes }
                            .thenByDescending { it.second }
                            .thenBy { it.first }
                    )
                }
                val visibleEndpoints = remember(endpointCounts, endpointSearch, filter.endpoints) {
                    val normalized = endpointSearch.trim().lowercase()
                    endpointCounts
                        .filter { normalized.isEmpty() || it.first.lowercase().contains(normalized) }
                        .sortedWith(
                            compareByDescending<Pair<String, Int>> { it.first in filter.endpoints }
                                .thenByDescending { it.second }
                                .thenBy { it.first }
                        )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenColumns),
                    verticalAlignment = Alignment.Top
                ) {
                    // 左栏：客户端、状态、路由/服务商（固定紧凑宽度 230dp）
                    Column(
                        modifier = Modifier.width(230.dp),
                        verticalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenRows)
                    ) {
                        // 1. 客户端
                        ActivityFilterSectionHeader(
                            title = s.activityFilterClients,
                            allSelected = filter.clients.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(clients = emptySet())) },
                            s = s
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)) {
                            ActivityClientKind.values().toList().chunked(2).forEach { rowKinds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
                                ) {
                                    rowKinds.forEach { kind ->
                                        ActivityFilterChip(
                                            label = activityClientLabel(kind, s),
                                            count = clientCounts[kind] ?: 0,
                                            selected = kind in filter.clients,
                                            onClick = {
                                                onFilterChange(filter.copy(clients = filter.clients.toggle(kind)))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 请求状态
                        ActivityFilterSectionHeader(
                            title = s.activityFilterStatuses,
                            allSelected = filter.statuses.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(statuses = emptySet())) },
                            s = s
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)) {
                            ActivityStatusKind.values().toList().chunked(2).forEach { rowStatuses ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
                                ) {
                                    rowStatuses.forEach { status ->
                                        ActivityFilterChip(
                                            label = activityStatusLabel(status, s),
                                            count = statusCounts[status] ?: 0,
                                            selected = status in filter.statuses,
                                            onClick = {
                                                onFilterChange(filter.copy(statuses = filter.statuses.toggle(status)))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 路由 / 服务商
                        ActivityFilterSectionHeader(
                            title = s.activityFilterRoutes,
                            allSelected = filter.routes.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(routes = emptySet())) },
                            s = s
                        )
                        if (orderedRoutes.isEmpty()) {
                            ActivityFilterEmptyText(s.activityFilterNoRoutes)
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                orderedRoutes.forEach { (route, count) ->
                                    ActivityFilterOptionRow(
                                        label = activityRouteLabel(route, s),
                                        count = count,
                                        checked = route in filter.routes,
                                        onClick = {
                                            onFilterChange(filter.copy(routes = filter.routes.toggle(route)))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.height(355.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 右栏：请求接口多选（宽幅占满剩余空间 ~390dp）
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        ActivityFilterSectionHeader(
                            title = s.activityFilterEndpoints,
                            allSelected = filter.endpoints.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(endpoints = emptySet())) },
                            s = s
                        )
                        StudioSearchField(
                            value = endpointSearch,
                            onValueChange = { endpointSearch = it },
                            placeholder = s.activityFilterEndpointSearch,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (visibleEndpoints.isEmpty()) {
                            ActivityFilterEmptyText(s.activityFilterNoEndpoints)
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 285.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visibleEndpoints.forEach { (path, count) ->
                                    EndpointFilterOptionRow(
                                        path = path,
                                        count = count,
                                        checked = path in filter.endpoints,
                                        onClick = {
                                            onFilterChange(filter.copy(endpoints = filter.endpoints.toggle(path)))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun ActivityFilterSectionHeader(
    title: String,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    s: Strings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = s.activityFilterAllOption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onSelectAll)
        )
    }
}

@Composable
private fun ActivityFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = StudioDesignTokens.TextSize.badge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = StudioDesignTokens.TextSize.caption),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    }
                )
            }
        },
        modifier = modifier
            .height(28.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}

@Composable
private fun ActivityFilterOptionRow(
    label: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.sm, vertical = AppTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = StudioDesignTokens.TextSize.body,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EndpointFilterOptionRow(
    path: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val (prefix, action) = remember(path) { splitEndpointPath(path) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.sm, vertical = AppTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prefix.isNotEmpty()) {
                    Text(
                        text = prefix,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = StudioDesignTokens.TextSize.body,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        ),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                        maxLines = 1
                    )
                }
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = StudioDesignTokens.TextSize.body,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
    }
}

private fun splitEndpointPath(path: String): Pair<String, String> {
    val colonIdx = path.lastIndexOf(':')
    if (colonIdx > 0 && colonIdx < path.length - 1) {
        return path.substring(0, colonIdx + 1) to path.substring(colonIdx + 1)
    }
    val slashIdx = path.lastIndexOf('/')
    if (slashIdx > 0 && slashIdx < path.length - 1) {
        return path.substring(0, slashIdx + 1) to path.substring(slashIdx + 1)
    }
    return "" to path
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

@Composable
private fun ActivityFilterEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTokens.Spacing.sm),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    if (path.length <= 28) return path
    return "…${path.takeLast(27)}"
}
