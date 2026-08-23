package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun CustomProviderView(
    provider: Provider,
    models: List<UpstreamModel>,
    modelTestStatuses: Map<String, AppViewModel.ModelTestStatus>,
    isProviderTesting: Boolean,
    compressionPolicies: Map<String, ModelCompressionPolicy>,
    onEditProvider: () -> Unit,
    onDeleteProvider: () -> Unit,
    onTestProvider: () -> Unit,
    onEditSingleModel: (UpstreamModel) -> Unit,
    onDeleteSingleModel: (UpstreamModel) -> Unit,
    onTestSingleModel: (UpstreamModel) -> Unit,
    onToggleModelEnabled: (UpstreamModel) -> Unit,
    onEditPolicy: (String) -> Unit,
    onOpenVisionDetail: (String, Boolean) -> Unit,
    onOpenReasoningDetail: (String, List<String>) -> Unit,
    onOpenInfoDetail: (ModelMetaInfo) -> Unit,
    onCopyNotice: (String) -> Unit
) {
    val passedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.SUCCESS }
    val failedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.ERROR }
    val hasTested = passedCount > 0 || failedCount > 0

    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(
                    horizontal = AppTokens.Spacing.card,
                    vertical = AppTokens.Spacing.content
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
            ) {
                Box(
                    modifier = Modifier
                        .size(AppTokens.Size.brandMark)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = provider.protocol.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = AppTokens.Spacing.control,
                                    vertical = AppTokens.Spacing.compact
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small
                            )
                            .clickable {
                                copyTextToClipboard(provider.effectiveBaseUrl)
                                onCopyNotice("已复制服务地址")
                            }
                            .padding(
                                horizontal = AppTokens.Spacing.control,
                                vertical = AppTokens.Spacing.compact
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                    ) {
                        Text(
                            text = provider.effectiveBaseUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasTested) {
                    val summaryContainer = if (failedCount == 0) {
                        AppStatusColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                    val summaryContent = if (failedCount == 0) {
                        AppStatusColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = summaryContainer,
                        contentColor = summaryContent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (failedCount == 0) {
                                AppStatusColors.success.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Text(
                            text = if (failedCount == 0) {
                                "$passedCount/${models.size} 项通过"
                            } else {
                                "$passedCount/${models.size} 项通过 ($failedCount 失败)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(
                                horizontal = AppTokens.Spacing.content,
                                vertical = AppTokens.Spacing.compact
                            )
                        )
                    }
                }

                ModernToolButton(
                    icon = Icons.Outlined.Sensors,
                    text = if (isProviderTesting) "测试中..." else if (failedCount > 0) "重试失败项 ($failedCount)" else "批量测试",
                    onClick = onTestProvider,
                    enabled = !isProviderTesting && models.isNotEmpty()
                )
                ModernToolButton(
                    icon = Icons.Outlined.Settings,
                    text = "编辑配置",
                    onClick = onEditProvider
                )
                ModernToolButton(
                    icon = Icons.Outlined.Delete,
                    text = "删除服务商",
                    isDestructive = true,
                    onClick = onDeleteProvider
                )
            }
        }

        if (models.isEmpty()) {
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
                        Icons.Outlined.LayersClear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        "该服务商尚未添加模型，点击「编辑配置」添加或拉取",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        models.forEach { model ->
                            key(model.id) {
                            UniversalModelCard(
                                state = createCustomCardState(
                                    model = model,
                                    testStatus = modelTestStatuses[model.id],
                                    hasPolicy = compressionPolicies.containsKey(model.id),
                                    policy = compressionPolicies[model.id],
                                    onEditModel = { onEditSingleModel(model) },
                                    onDeleteModel = { onDeleteSingleModel(model) },
                                    onTestModel = { onTestSingleModel(model) },
                                    onToggleEnabled = { onToggleModelEnabled(model) },
                                    onEditPolicy = { onEditPolicy(model.id) },
                                    onOpenVisionDetail = {
                                        onOpenVisionDetail(
                                            model.displayName ?: model.upstreamModelId,
                                            model.capabilities.supportsVision
                                        )
                                    },
                                    onOpenReasoningDetail = {
                                        onOpenReasoningDetail(
                                            model.displayName ?: model.upstreamModelId,
                                            listOf("Thinking / Reasoning")
                                        )
                                    },
                                    onOpenInfoDetail = {
                                        onOpenInfoDetail(
                                            ModelMetaInfo(
                                                name = model.displayName ?: model.upstreamModelId,
                                                id = model.upstreamModelId,
                                                contextLimit = model.effectiveContextWindow,
                                                outputLimit = model.tokenLimits.outputTokenLimit ?: model.maxOutputTokens
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
                        models.chunked(columnCount).forEach { rowModels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rowModels.forEach { model ->
                                    key(model.id) {
                                    UniversalModelCard(
                                        state = createCustomCardState(
                                            model = model,
                                            testStatus = modelTestStatuses[model.id],
                                            hasPolicy = compressionPolicies.containsKey(model.id),
                                            policy = compressionPolicies[model.id],
                                            onEditModel = { onEditSingleModel(model) },
                                            onDeleteModel = { onDeleteSingleModel(model) },
                                            onTestModel = { onTestSingleModel(model) },
                                            onToggleEnabled = { onToggleModelEnabled(model) },
                                            onEditPolicy = { onEditPolicy(model.id) },
                                            onOpenVisionDetail = {
                                                onOpenVisionDetail(
                                                    model.displayName ?: model.upstreamModelId,
                                                    model.capabilities.supportsVision
                                                )
                                            },
                                            onOpenReasoningDetail = {
                                                onOpenReasoningDetail(
                                                    model.displayName ?: model.upstreamModelId,
                                                    listOf("Thinking / Reasoning")
                                                )
                                            },
                                            onOpenInfoDetail = {
                                                onOpenInfoDetail(
                                                    ModelMetaInfo(
                                                        name = model.displayName ?: model.upstreamModelId,
                                                        id = model.upstreamModelId,
                                                        contextLimit = model.effectiveContextWindow,
                                                        outputLimit = model.tokenLimits.outputTokenLimit
                                                            ?: model.maxOutputTokens
                                                    )
                                                )
                                            }
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    }
                                }
                                val emptySlots = columnCount - rowModels.size
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
