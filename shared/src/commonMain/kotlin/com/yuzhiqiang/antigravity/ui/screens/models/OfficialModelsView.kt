package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicyAssignment
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
import androidx.compose.ui.graphics.luminance

@Composable
fun OfficialModelsView(
    groupedModels: List<GroupedOfficialModel>,
    isFetching: Boolean,
    disabledCatalogModelIds: List<String>,
    compressionPolicyAssignments: List<ModelCompressionPolicyAssignment>,
    testSummary: String?,
    isTestSuccess: Boolean,
    fetchError: String?,
    isTesting: Boolean,
    onTestConnection: () -> Unit,
    onRefresh: () -> Unit,
    onViewRawJson: () -> Unit,
    onViewModifiedJson: () -> Unit,
    onToggleGroup: (GroupedOfficialModel) -> Unit,
    onEditPolicy: (String) -> Unit,
    onOpenVisionDetail: (String, Boolean) -> Unit,
    onOpenReasoningDetail: (String, List<String>) -> Unit,
    onOpenInfoDetail: (ModelMetaInfo) -> Unit,
    hasAccounts: Boolean = true,
    onNavigateToAccounts: (() -> Unit)? = null,
    isDebugMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hasError = fetchError != null || (testSummary != null && !isTestSuccess)
                val statusText = testSummary
                    ?: fetchError?.let { error -> s.modelsOfficialSyncFailed(error) }
                    ?: if (isFetching) s.modelsOfficialSyncing
                    else if (!hasAccounts) s.modelsNoAccountTitle
                    else if (groupedModels.isNotEmpty()) s.modelsOfficialSynced
                    else s.modelsOfficialWaitingSync
                val statusColor = when {
                    isTesting || isFetching || !hasAccounts -> AppStatusColors.warning
                    hasError -> MaterialTheme.colorScheme.error
                    groupedModels.isNotEmpty() -> AppStatusColors.success
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernToolButton(
                    icon = Icons.Outlined.Sensors,
                    text = s.modelsTestConnection,
                    onClick = onTestConnection,
                    enabled = !isTesting && hasAccounts,
                    isLoading = isTesting
                )
                ModernToolButton(
                    icon = Icons.Outlined.Refresh,
                    text = s.commonRefresh,
                    onClick = onRefresh,
                    enabled = !isFetching,
                    isLoading = isFetching
                )
                if (isDebugMode) {
                    ModernToolButton(
                        icon = Icons.Outlined.Code,
                        text = s.modelsRawJson,
                        onClick = onViewRawJson
                    )
                    ModernToolButton(
                        icon = Icons.Outlined.Visibility,
                        text = s.modelsModifiedJson,
                        onClick = onViewModifiedJson
                    )
                }
            }
        }


        if (groupedModels.isEmpty() && isFetching) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        com.yuzhiqiang.antigravity.ui.components.SkeletonCard(
                            modifier = Modifier.weight(1f),
                            height = 180.dp
                        )
                        com.yuzhiqiang.antigravity.ui.components.SkeletonCard(
                            modifier = Modifier.weight(1f),
                            height = 180.dp
                        )
                    }
                }
            }
        } else if (groupedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .padding(AppTokens.Spacing.pageSection),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                ) {
                    Icon(
                        if (!hasAccounts) Icons.Outlined.AccountCircle else Icons.Outlined.LayersClear,
                        contentDescription = null,
                        tint = if (!hasAccounts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (!hasAccounts) s.modelsNoAccountTitle else s.modelsNoOfficialDetected,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (!hasAccounts) s.modelsNoAccountHint else s.modelsNoOfficialHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppTokens.Spacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!hasAccounts && onNavigateToAccounts != null) {
                            Button(
                                onClick = onNavigateToAccounts,
                                shape = RoundedCornerShape(AppTokens.Radius.medium),
                                contentPadding = PaddingValues(
                                    horizontal = AppTokens.Spacing.section,
                                    vertical = AppTokens.Spacing.xs
                                )
                            ) {
                                @Suppress("DEPRECATION")
                                Icon(
                                    Icons.Outlined.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppTokens.Size.iconSmall)
                                )
                                Spacer(Modifier.width(AppTokens.Spacing.xs))
                                Text(s.modelsGoToAccounts, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (hasAccounts) {
                            Button(
                                onClick = onRefresh,
                                enabled = !isFetching,
                                shape = RoundedCornerShape(AppTokens.Radius.medium),
                                contentPadding = PaddingValues(
                                    horizontal = AppTokens.Spacing.section,
                                    vertical = AppTokens.Spacing.xs
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppTokens.Size.iconSmall)
                                )
                                Spacer(Modifier.width(AppTokens.Spacing.xs))
                                Text(s.modelsRefreshOfficial, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRefresh,
                                enabled = !isFetching,
                                shape = RoundedCornerShape(AppTokens.Radius.medium),
                                contentPadding = PaddingValues(
                                    horizontal = AppTokens.Spacing.section,
                                    vertical = AppTokens.Spacing.xs
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppTokens.Size.iconSmall)
                                )
                                Spacer(Modifier.width(AppTokens.Spacing.xs))
                                Text(s.commonRefresh, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columnCount = when {
                    maxWidth < 720.dp -> 1
                    maxWidth < 1120.dp -> 2
                    maxWidth < 1560.dp -> 3
                    else -> 4
                }
                if (columnCount == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        groupedModels.forEach { group ->
                            key(group.baseItem.catalogModelId) {
                                UniversalModelCard(
                                    state = createOfficialCardState(
                                        group = group,
                                        disabledCatalogModelIds = disabledCatalogModelIds,
                                        compressionPolicyAssignments = compressionPolicyAssignments,
                                        onToggle = { onToggleGroup(group) },
                                        onEditPolicy = { onEditPolicy(group.baseItem.catalogModelId) },
                                        onOpenVisionDetail = {
                                            onOpenVisionDetail(
                                                group.baseName,
                                                group.baseItem.supportsVision
                                            )
                                        },
                                        onOpenReasoningDetail = {
                                            onOpenReasoningDetail(
                                                group.baseName,
                                                group.variants.map { it.label }
                                            )
                                        },
                                        onOpenInfoDetail = {
                                            onOpenInfoDetail(
                                                ModelMetaInfo(
                                                    name = group.baseName,
                                                    id = group.baseItem.catalogModelId,
                                                    contextLimit = group.baseItem.inputTokenLimit
                                                        ?: group.baseItem.maxTokens,
                                                    outputLimit = group.baseItem.outputTokenLimit,
                                                    roles = group.baseItem.roles
                                                )
                                            )
                                        }
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        groupedModels.chunked(columnCount).forEach { rowGroups ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rowGroups.forEach { group ->
                                    key(group.baseItem.catalogModelId) {
                                        UniversalModelCard(
                                            state = createOfficialCardState(
                                                group = group,
                                                disabledCatalogModelIds = disabledCatalogModelIds,
                                                compressionPolicyAssignments = compressionPolicyAssignments,
                                                onToggle = { onToggleGroup(group) },
                                                onEditPolicy = { onEditPolicy(group.baseItem.catalogModelId) },
                                                onOpenVisionDetail = {
                                                    onOpenVisionDetail(
                                                        group.baseName,
                                                        group.baseItem.supportsVision
                                                    )
                                                },
                                                onOpenReasoningDetail = {
                                                    onOpenReasoningDetail(
                                                        group.baseName,
                                                        group.variants.map { it.label }
                                                    )
                                                },
                                                onOpenInfoDetail = {
                                                    onOpenInfoDetail(
                                                        ModelMetaInfo(
                                                            name = group.baseName,
                                                            id = group.baseItem.catalogModelId,
                                                            contextLimit = group.baseItem.inputTokenLimit
                                                                ?: group.baseItem.maxTokens,
                                                            outputLimit = group.baseItem.outputTokenLimit,
                                                            roles = group.baseItem.roles
                                                        )
                                                    )
                                                }
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                val emptySlots = columnCount - rowGroups.size
                                repeat(emptySlots) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
