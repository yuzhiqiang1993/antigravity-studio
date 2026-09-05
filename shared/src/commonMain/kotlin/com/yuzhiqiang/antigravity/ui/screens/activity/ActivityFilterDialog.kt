package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@Composable
internal fun ActivityFilterDialog(
    totalCount: Int,
    matchingCount: Int,
    clientCounts: Map<ActivityClientKind, Int>,
    endpointCounts: List<Pair<String, Int>>,
    routeCounts: List<Pair<String, Int>>,
    statusCounts: Map<ActivityStatusKind, Int>,
    filter: ActivityLogFilter,
    showEndpointsFilter: Boolean = true,
    resetKey: Int,
    onFilterChange: (ActivityLogFilter) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
    s: Strings
) {
    var endpointSearch by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf<ActivityEndpointCategory?>(null) }
    LaunchedEffect(resetKey) {
        endpointSearch = ""
        selectedCategoryTab = null
    }

    val isFiltered = filter.isActive
    val orderedRoutes = remember(routeCounts, filter.routes) {
        routeCounts.sortedWith(
            compareByDescending<Pair<String, Int>> { it.first in filter.routes }
                .thenByDescending { it.second }
                .thenBy { it.first }
        )
    }

    val visibleEndpoints = remember(endpointCounts, endpointSearch, filter.endpoints, selectedCategoryTab) {
        val normalized = endpointSearch.trim().lowercase()
        endpointCounts
            .filter { (path, _) ->
                val info = ActivityEndpointRegistry.resolve(path)
                val matchesCategory = when (selectedCategoryTab) {
                    null -> true
                    ActivityEndpointCategory.AI_CHAT -> info.category == ActivityEndpointCategory.AI_CHAT || info.category == ActivityEndpointCategory.CODE_ASSIST
                    ActivityEndpointCategory.SYSTEM -> info.category == ActivityEndpointCategory.SYSTEM
                    else -> info.category == selectedCategoryTab
                }
                val matchesSearch = normalized.isEmpty() ||
                        path.lowercase().contains(normalized) ||
                        info.displayName.lowercase().contains(normalized) ||
                        info.description.lowercase().contains(normalized)

                matchesCategory && matchesSearch
            }
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.first in filter.endpoints }
                    .thenByDescending { (path, _) ->
                        val cat = ActivityEndpointRegistry.resolve(path).category
                        cat == ActivityEndpointCategory.AI_CHAT || cat == ActivityEndpointCategory.CODE_ASSIST
                    }
                    .thenByDescending { it.second }
                    .thenBy { it.first }
            )
    }

    val aiChatEndpointCount = remember(endpointCounts) {
        endpointCounts.count { (path, _) ->
            val cat = ActivityEndpointRegistry.resolve(path).category
            cat == ActivityEndpointCategory.AI_CHAT || cat == ActivityEndpointCategory.CODE_ASSIST
        }
    }
    val systemEndpointCount = remember(endpointCounts) {
        endpointCounts.count { (path, _) ->
            ActivityEndpointRegistry.resolve(path).category == ActivityEndpointCategory.SYSTEM
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .width(if (showEndpointsFilter) 900.dp else 420.dp)
                .heightIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Header 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = s.activityTagFilterTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(AppTokens.Radius.pill),
                            color = if (isFiltered) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isFiltered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = s.activityFilterMatches(matchingCount, totalCount),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 2. 内容区域 (双栏或单栏)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(AppTokens.Spacing.card),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 客户端、状态、服务商
                    Column(
                        modifier = (if (showEndpointsFilter) Modifier.width(270.dp) else Modifier.fillMaxWidth())
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 客户端
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActivityFilterSectionHeader(
                                title = s.activityFilterClients,
                                allSelected = filter.clients.isEmpty(),
                                onSelectAll = { onFilterChange(filter.copy(clients = emptySet())) },
                                s = s
                            )
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // 请求状态
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActivityFilterSectionHeader(
                                title = s.activityFilterStatuses,
                                allSelected = filter.statuses.isEmpty(),
                                onSelectAll = { onFilterChange(filter.copy(statuses = emptySet())) },
                                s = s
                            )
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // 路由 / 服务商
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    }

                    if (showEndpointsFilter) {
                        VerticalDivider(
                            modifier = Modifier.height(350.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 右栏：请求接口 (weight 1f，充分展开)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActivityFilterSectionHeader(
                                title = s.activityFilterEndpoints,
                                allSelected = filter.endpoints.isEmpty() && !filter.onlyAiChat,
                                onSelectAll = { onFilterChange(filter.copy(endpoints = emptySet(), onlyAiChat = false)) },
                                s = s
                            )

                            // 接口分类快捷 Tab（纯文字无 Emoji）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(AppTokens.Radius.pill),
                                    color = if (selectedCategoryTab == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { selectedCategoryTab = null }
                                ) {
                                    Text(
                                        text = "${s.activityFilterTabAll} (${endpointCounts.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selectedCategoryTab == null) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedCategoryTab == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(AppTokens.Radius.pill),
                                    color = if (selectedCategoryTab == ActivityEndpointCategory.AI_CHAT) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { selectedCategoryTab = ActivityEndpointCategory.AI_CHAT }
                                ) {
                                    Text(
                                        text = "${s.activityFilterTabAiChat} ($aiChatEndpointCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selectedCategoryTab == ActivityEndpointCategory.AI_CHAT) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedCategoryTab == ActivityEndpointCategory.AI_CHAT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(AppTokens.Radius.pill),
                                    color = if (selectedCategoryTab == ActivityEndpointCategory.SYSTEM) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { selectedCategoryTab = ActivityEndpointCategory.SYSTEM }
                                ) {
                                    Text(
                                        text = "${s.activityFilterTabSystem} ($systemEndpointCount)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selectedCategoryTab == ActivityEndpointCategory.SYSTEM) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedCategoryTab == ActivityEndpointCategory.SYSTEM) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

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
                                        .height(265.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    visibleEndpoints.forEach { (path, count) ->
                                        EndpointFilterOptionRow(
                                            path = path,
                                            count = count,
                                            checked = path in filter.endpoints,
                                            onClick = {
                                                onFilterChange(filter.copy(endpoints = filter.endpoints.toggle(path)))
                                            },
                                            s = s
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 3. Footer 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeCount = if (showEndpointsFilter) {
                        filter.activeCount
                    } else {
                        filter.clients.size + filter.routes.size + filter.statuses.size
                    }
                    val hasActiveFilters = activeCount > 0
                    Text(
                        text = if (hasActiveFilters) {
                            s.activityFilterSelectedDimension(s.activityTagFilterTitle, activeCount)
                        } else {
                            s.activityFilterAllOption
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isFiltered) {
                            OutlinedButton(
                                onClick = {
                                    endpointSearch = ""
                                    onResetAll()
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(s.activityFilterResetAll, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = s.commonClose,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

