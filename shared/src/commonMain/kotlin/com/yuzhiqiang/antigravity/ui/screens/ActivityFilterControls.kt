package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@Composable
internal fun ActivityQuickClientFilters(
    clientCounts: Map<ActivityClientKind, Int>,
    filter: ActivityLogFilter,
    onFilterChange: (ActivityLogFilter) -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf(ActivityClientKind.IDE, ActivityClientKind.CLI, ActivityClientKind.APP).forEach { kind ->
            CompactActivityFilterChip(
                label = activityClientLabel(kind, s),
                count = clientCounts[kind] ?: 0,
                selected = kind in filter.clients,
                onClick = {
                    onFilterChange(filter.copy(clients = filter.clients.toggle(kind)))
                }
            )
        }
    }
}

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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFiltered = filter.isActive
    val containerColor = when {
        isFiltered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isFiltered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val buttonLabel = if (isFiltered) {
        s.activitySelectedTagsCount(filter.activeCount)
    } else {
        s.activityAllTags
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(StudioDesignTokens.Sizes.topButtonHeight)
                .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource)
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
                    fontSize = StudioDesignTokens.TextSize.body,
                    fontWeight = if (isFiltered) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
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
            modifier = Modifier.width(560.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityFilterSectionHeader(
                            title = s.activityFilterClients,
                            allSelected = filter.clients.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(clients = emptySet())) },
                            s = s
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ActivityClientKind.values().toList().chunked(2).forEach { rowKinds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    rowKinds.forEach { kind ->
                                        CompactActivityFilterChip(
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

                        ActivityFilterSectionHeader(
                            title = s.activityFilterStatuses,
                            allSelected = filter.statuses.isEmpty(),
                            onSelectAll = { onFilterChange(filter.copy(statuses = emptySet())) },
                            s = s
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ActivityStatusKind.values().toList().chunked(2).forEach { rowStatuses ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    rowStatuses.forEach { status ->
                                        CompactActivityFilterChip(
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
                                    .heightIn(max = 126.dp)
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

                    androidx.compose.material3.VerticalDivider(
                        modifier = Modifier.height(330.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .heightIn(max = 276.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visibleEndpoints.forEach { (path, count) ->
                                    ActivityFilterOptionRow(
                                        label = path,
                                        count = count,
                                        checked = path in filter.endpoints,
                                        onClick = {
                                            onFilterChange(filter.copy(endpoints = filter.endpoints.toggle(path)))
                                        },
                                        monospace = true
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (filter.clients.isNotEmpty()) {
                val label = filter.clients.singleOrNull()?.let { activityClientLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterClients, filter.clients.size)
                ActivityFilterSummaryChip(label) {
                    onFilterChange(filter.copy(clients = emptySet()))
                }
            }
            if (filter.statuses.isNotEmpty()) {
                val label = filter.statuses.singleOrNull()?.let { activityStatusLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterStatuses, filter.statuses.size)
                ActivityFilterSummaryChip(label) {
                    onFilterChange(filter.copy(statuses = emptySet()))
                }
            }
            if (filter.routes.isNotEmpty()) {
                val label = filter.routes.singleOrNull()?.let { activityRouteLabel(it, s) }
                    ?: s.activityFilterSelectedDimension(s.activityFilterRoutes, filter.routes.size)
                ActivityFilterSummaryChip(label) {
                    onFilterChange(filter.copy(routes = emptySet()))
                }
            }
            if (filter.endpoints.isNotEmpty()) {
                val label = filter.endpoints.singleOrNull()?.let(::compactPath)
                    ?: s.activityFilterSelectedDimension(s.activityFilterEndpoints, filter.endpoints.size)
                ActivityFilterSummaryChip(label, maxWidth = 220.dp) {
                    onFilterChange(filter.copy(endpoints = emptySet()))
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.activityFilterMatches(shownCount, totalCount),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
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
private fun CompactActivityFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }

    Surface(
        modifier = modifier
            .height(30.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        color = background,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                }
            )
        }
    }
}

@Composable
private fun ActivityFilterOptionRow(
    label: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit,
    monospace: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null
                ),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ActivityFilterSummaryChip(
    label: String,
    maxWidth: androidx.compose.ui.unit.Dp = 160.dp,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.height(27.dp).widthIn(max = maxWidth),
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 9.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ActivityFilterEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun activityClientLabel(kind: ActivityClientKind, s: Strings): String = when (kind) {
    ActivityClientKind.IDE -> s.activityFilterClientIde
    ActivityClientKind.CLI -> s.activityFilterClientCli
    ActivityClientKind.APP -> s.activityFilterClientApp
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
