package com.yuzhiqiang.antigravity.ui.screens.models

import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicyAssignment
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel

fun createOfficialCardState(
    group: GroupedOfficialModel,
    disabledCatalogModelIds: List<String>,
    compressionPolicyAssignments: List<ModelCompressionPolicyAssignment>,
    onToggle: () -> Unit,
    onEditPolicy: () -> Unit,
    onOpenVisionDetail: () -> Unit,
    onOpenReasoningDetail: () -> Unit,
    onOpenInfoDetail: () -> Unit
): UniversalModelCardUiState {
    val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
    val allCatalogModelIds = group.variants.map { it.model.catalogModelId }.toSet()
    val isDisabled = allCatalogModelIds.any { it in disabledCatalogModelIds }
    val item = group.baseItem
    val policy = compressionPolicyAssignments.firstOrNull { assignment ->
        assignment.targetType == CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL &&
                assignment.targetId == item.catalogModelId
    }?.policy
    val hasPolicy = policy != null
    val compressionLabel = policy?.let {
        val limit = formatTokenDisplay(it.maxTokenLimit)
        val prep = formatTokenDisplay(it.tokenThreshold)
        s.modelsPolicyCapacityWithPrep(limit, prep)
    } ?: s.modelsOfficialDefault
    val contextText = item.contextWindow?.let(::formatTokenDisplay)
        ?: item.maxTokens?.let(::formatTokenDisplay)
        ?: "1048K"
    val outputText = item.outputTokenLimit?.let(::formatTokenDisplay)

    return UniversalModelCardUiState(
        title = group.baseName,
        brand = ModelBrand.fromModelName(group.baseName),
        isEnabled = !isDisabled,
        onToggleEnabled = onToggle,
        contextLimitText = contextText,
        outputLimitText = outputText,
        supportsVision = item.supportsVision,
        supportsTools = item.supportsTools,
        supportsReasoning = item.supportsReasoning,
        reasoningVariants = group.variants.map { it.label },
        compressionLabel = compressionLabel,
        isCompressionCustom = hasPolicy,
        onEditCompressionPolicy = onEditPolicy,
        onOpenVisionDetail = onOpenVisionDetail,
        onOpenReasoningDetail = onOpenReasoningDetail,
        onOpenInfoDetail = onOpenInfoDetail
    )
}

fun createCustomCardState(
    model: ProviderModelBinding,
    testStatus: AppViewModel.ModelTestStatus?,
    hasPolicy: Boolean,
    policy: ModelCompressionPolicy?,
    onEditModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onTestModel: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEditPolicy: () -> Unit,
    onOpenVisionDetail: () -> Unit,
    onOpenReasoningDetail: () -> Unit,
    onOpenInfoDetail: () -> Unit
): UniversalModelCardUiState {
    val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
    val modelTitle = model.effectiveName
    val customSubtitle = model.providerModelId.takeIf { it != modelTitle }
    val rawLevels = ReasoningMappingSupport.configuredLevels(model.capabilities.reasoning.levels)
    val reasoningLevels = when {
        rawLevels.isNotEmpty() -> rawLevels.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }
        model.capabilities.reasoning.supportsReasoning -> listOf("High", "Medium", "Low")
        else -> emptyList()
    }
    val compressionLabel = policy?.let {
        val limit = formatTokenDisplay(it.maxTokenLimit)
        val prep = formatTokenDisplay(it.tokenThreshold)
        s.modelsPolicyCapacityWithPrep(limit, prep)
    } ?: s.modelsOfficialDefault

    return UniversalModelCardUiState(
        title = modelTitle,
        subtitle = customSubtitle,
        brand = ModelBrand.fromModelName(modelTitle),
        isEnabled = model.enabled,
        onToggleEnabled = onToggleEnabled,
        testStatus = testStatus,
        onTest = onTestModel,
        onEdit = onEditModel,
        onDelete = onDeleteModel,
        contextLimitText = (model.tokenLimits.contextWindow ?: model.tokenLimits.inputTokenLimit)
            ?.let(::formatTokenDisplay) ?: s.commonNotSet,
        outputLimitText = model.tokenLimits.outputTokenLimit?.let(::formatTokenDisplay),
        supportsVision = model.capabilities.supportsVision,
        supportsTools = model.capabilities.tools,
        supportsReasoning = model.capabilities.reasoning.supportsReasoning || reasoningLevels.isNotEmpty(),
        reasoningVariants = reasoningLevels,
        compressionLabel = compressionLabel,
        isCompressionCustom = hasPolicy,
        onEditCompressionPolicy = onEditPolicy,
        onOpenVisionDetail = onOpenVisionDetail,
        onOpenReasoningDetail = onOpenReasoningDetail,
        onOpenInfoDetail = onOpenInfoDetail
    )
}
