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
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import androidx.compose.ui.graphics.luminance

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
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val passedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.SUCCESS }
    val failedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.ERROR }
    val hasTested = passedCount > 0 || failedCount > 0

    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = StudioGlassTokens.borderWidth,
                    color = StudioGlassTokens.cleanBorderColor(isDark),
                    shape = RoundedCornerShape(StudioGlassTokens.cardCornerRadius)
                ),
            shape = RoundedCornerShape(StudioGlassTokens.cardCornerRadius),
            color = StudioGlassTokens.cardBackgroundColor(isDark),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            .clip(RoundedCornerShape(8.dp))
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

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(
                                text = provider.protocol.name.replace('_', ' '),
                                isActive = true
                            )
                        }

                        if (provider.effectiveBaseUrl.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (copyToClipboard(provider.effectiveBaseUrl)) {
                                            onCopyNotice(s.modelsCopiedProviderUrl)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = provider.effectiveBaseUrl,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            shape = RoundedCornerShape(6.dp),
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
                                    s.modelsPassedCount(passedCount, models.size)
                                } else {
                                    s.modelsPassedWithFailed(passedCount, models.size, failedCount)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    ModernToolButton(
                        icon = Icons.Outlined.Sensors,
                        text = if (isProviderTesting) s.providerTesting else if (failedCount > 0) s.modelsRetryFailed(
                            failedCount
                        ) else s.modelsBatchTest,
                        onClick = onTestProvider,
                        enabled = !isProviderTesting && models.isNotEmpty()
                    )
                    ModernToolButton(
                        icon = Icons.Outlined.Settings,
                        text = s.modelsEditConfig,
                        onClick = onEditProvider
                    )
                    ModernToolButton(
                        icon = Icons.Outlined.Delete,
                        text = s.modelsDeleteProvider,
                        isDestructive = true,
                        onClick = onDeleteProvider
                    )
                }
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
                        s.modelsNoModelsHint,
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
                                                    outputLimit = model.tokenLimits.outputTokenLimit
                                                        ?: model.maxOutputTokens
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
