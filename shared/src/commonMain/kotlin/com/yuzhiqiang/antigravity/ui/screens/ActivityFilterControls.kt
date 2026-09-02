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

/**
 * 居中高质感模态筛选弹窗 (ActivityFilterDialog)
 */
@Composable
internal fun ActivityFilterDialog(
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
                .width(900.dp)
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

                // 2. 双栏卡片式内容区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(AppTokens.Spacing.card),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 左栏：客户端、状态、服务商 (width 270.dp)
                    Column(
                        modifier = Modifier
                            .width(270.dp)
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
                    Text(
                        text = if (isFiltered) {
                            s.activityFilterSelectedDimension(s.activityTagFilterTitle, filter.activeCount)
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
    onClick: () -> Unit,
    s: Strings
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val info = remember(path) { ActivityEndpointRegistry.resolve(path) }
    val isAiChat = info.category == ActivityEndpointCategory.AI_CHAT || info.category == ActivityEndpointCategory.CODE_ASSIST
    val displayName = remember(path, s) { s.activityEndpointDisplayName(path) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = if (checked || isAiChat) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else if (isAiChat) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isAiChat) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        ) {
                            Text(
                                text = s.activityFilterCoreBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = cleanEndpointDisplayPath(path),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Surface(
            shape = RoundedCornerShape(AppTokens.Radius.pill),
            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

internal fun cleanEndpointDisplayPath(path: String): String {
    val trimmed = path.trim()
    val colonIdx = trimmed.lastIndexOf(':')
    if (colonIdx >= 0 && colonIdx < trimmed.length - 1) {
        return trimmed.substring(colonIdx + 1)
    }
    val cleaned = trimmed.removePrefix("/v1internal/").removePrefix("/v1/").removePrefix("/")
    return cleaned.ifBlank { trimmed.ifBlank { "/" } }
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
    val clean = cleanEndpointDisplayPath(path)
    if (clean.length <= 28) return clean
    return "…${clean.takeLast(27)}"
}
