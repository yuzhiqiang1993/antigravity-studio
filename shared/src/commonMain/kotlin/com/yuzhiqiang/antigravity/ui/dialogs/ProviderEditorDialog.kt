package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.ui.dialogs.provider.*
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProviderEditorDialog(
    initialProvider: Provider? = null,
    initialModels: List<UpstreamModel> = emptyList(),
    editingSingleModel: UpstreamModel? = null,
    onDismiss: () -> Unit,
    onSave: (Provider, List<UpstreamModel>) -> Unit
) {
    val s = strings()
    val scope = rememberCoroutineScope()
    val statusColors = AppStatusColors

    val isSingleModelMode = editingSingleModel != null
    var currentStep by remember {
        mutableStateOf(
            when {
                isSingleModelMode -> ProviderEditStep.SELECT_MODELS
                initialProvider != null -> ProviderEditStep.CONFIG_CONNECTION
                else -> ProviderEditStep.SELECT_PRESET
            }
        )
    }

    val providerId by remember { mutableStateOf(initialProvider?.id ?: "p_${UUID.randomUUID().toString().take(8)}") }
    var selectedPresetId by remember { mutableStateOf(initialProvider?.let { detectPresetId(it.baseUrl) }) }
    var name by remember { mutableStateOf(initialProvider?.name.orEmpty()) }
    var protocol by remember { mutableStateOf(initialProvider?.protocol ?: ProviderProtocol.OPENAI_CHAT_COMPLETIONS) }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey.orEmpty()) }
    var modelsEndpoint by remember { mutableStateOf(initialProvider?.modelsEndpoint.orEmpty()) }
    var generateEndpoint by remember { mutableStateOf(initialProvider?.generateEndpoint.orEmpty()) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    fun markDirty() { if (!isDirty) isDirty = true }

    fun updateSuggestedEndpoints() {
        val (sugModels, sugGen) = suggestedEndpoints(baseUrl, protocol)
        if (modelsEndpoint.isBlank() || modelsEndpoint.contains("/models")) {
            modelsEndpoint = sugModels
        }
        if (generateEndpoint.isBlank() ||
            generateEndpoint.contains("/chat/completions") ||
            generateEndpoint.contains("/messages") ||
            generateEndpoint.contains("/responses") ||
            generateEndpoint.contains(":generateContent")
        ) {
            generateEndpoint = sugGen
        }
    }

    var fetchedModelConfigs by remember {
        mutableStateOf(
            if (isSingleModelMode && editingSingleModel != null) {
                listOf(
                    CatalogModelConfig(
                        id = editingSingleModel.upstreamModelId,
                        name = editingSingleModel.displayName ?: editingSingleModel.name,
                        inputTokenLimit = editingSingleModel.tokenLimits.contextWindow ?: editingSingleModel.tokenLimits.inputTokenLimit,
                        inputTokenLimitSource = editingSingleModel.tokenLimits.contextWindowSource,
                        outputTokenLimit = editingSingleModel.tokenLimits.outputTokenLimit,
                        outputTokenLimitSource = editingSingleModel.tokenLimits.outputTokenLimitSource,
                        isVision = editingSingleModel.capabilities.supportsVision,
                        inputModalities = editingSingleModel.capabilities.inputModalities.toSet(),
                        outputModalities = editingSingleModel.capabilities.outputModalities.toSet(),
                        inputMimeTypes = editingSingleModel.capabilities.inputMimeTypes,
                        roles = editingSingleModel.capabilities.roles.toSet(),
                        isImageGeneration = ModelRole.IMAGE_GENERATION in editingSingleModel.capabilities.roles && ModelRole.AGENT !in editingSingleModel.capabilities.roles,
                        compressionPolicy = editingSingleModel.compressionPolicy,
                        reasoningMappings = ReasoningMappingSupport.parse(editingSingleModel.capabilities.reasoning.levels),
                        isReasoning = editingSingleModel.capabilities.reasoning.supportsReasoning,
                        reasoningDraft = ReasoningConfigDraft.fromCapabilities(editingSingleModel.capabilities),
                        isTools = editingSingleModel.capabilities.tools
                    )
                )
            } else if (initialModels.isNotEmpty()) {
                initialModels.map { model ->
                    CatalogModelConfig(
                        id = model.upstreamModelId,
                        name = model.displayName ?: model.name,
                        inputTokenLimit = model.tokenLimits.contextWindow ?: model.tokenLimits.inputTokenLimit,
                        inputTokenLimitSource = model.tokenLimits.contextWindowSource,
                        outputTokenLimit = model.tokenLimits.outputTokenLimit,
                        outputTokenLimitSource = model.tokenLimits.outputTokenLimitSource,
                        isVision = model.capabilities.supportsVision,
                        inputModalities = model.capabilities.inputModalities.toSet(),
                        outputModalities = model.capabilities.outputModalities.toSet(),
                        inputMimeTypes = model.capabilities.inputMimeTypes,
                        roles = model.capabilities.roles.toSet(),
                        isImageGeneration = ModelRole.IMAGE_GENERATION in model.capabilities.roles && ModelRole.AGENT !in model.capabilities.roles,
                        compressionPolicy = model.compressionPolicy,
                        reasoningMappings = ReasoningMappingSupport.parse(model.capabilities.reasoning.levels),
                        isReasoning = model.capabilities.reasoning.supportsReasoning,
                        reasoningDraft = ReasoningConfigDraft.fromCapabilities(model.capabilities),
                        isTools = model.capabilities.tools
                    )
                }
            } else {
                emptyList()
            }
        )
    }

    var selectedModelIds by remember {
        mutableStateOf(
            if (editingSingleModel != null) {
                setOf(editingSingleModel.upstreamModelId)
            } else {
                initialModels.map { it.upstreamModelId }.toSet()
            }
        )
    }

    var reasoningModelId by remember { mutableStateOf<String?>(null) }

    fun currentProvider(): Provider {
        val (sugModels, sugGen) = suggestedEndpoints(baseUrl, protocol)
        return Provider(
            id = providerId,
            name = name.trim(),
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim().takeIf { it.isNotEmpty() },
            modelsEndpoint = modelsEndpoint.trim().ifBlank { sugModels },
            generateEndpoint = generateEndpoint.trim().ifBlank { sugGen },
            enabled = initialProvider?.enabled ?: true
        )
    }

    fun requestDismiss() {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(AppTokens.Size.dialogWidth)
                .height(if (isSingleModelMode) AppTokens.Size.singleModelDialogHeight else AppTokens.Size.dialogHeight),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isSingleModelMode -> editingSingleModel?.let { model ->
                                        "${s.modelsEditModel} · ${model.displayName ?: model.upstreamModelId}"
                                    }.orEmpty()
                                    initialProvider != null -> "${s.modelsEditProvider} · ${initialProvider.name}"
                                    else -> s.modelsAddProvider
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isSingleModelMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            data class StepItem(val step: ProviderEditStep, val num: Int, val label: String)

                            listOf(
                                StepItem(ProviderEditStep.SELECT_PRESET, 1, s.providerStepPreset),
                                StepItem(ProviderEditStep.CONFIG_CONNECTION, 2, s.providerStepConnection),
                                StepItem(ProviderEditStep.SELECT_MODELS, 3, s.providerStepModels)
                            ).forEach { (step, num, label) ->
                                val isActive = currentStep == step
                                val canNavigate = !isFetching &&
                                        step.ordinal <= currentStep.ordinal &&
                                        !(initialProvider != null && step == ProviderEditStep.SELECT_PRESET)
                                val nodeModifier = if (canNavigate) {
                                    Modifier.clickable { currentStep = step }
                                } else {
                                    Modifier
                                }
                                Row(
                                    modifier = nodeModifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            num.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = when {
                                            isActive -> MaterialTheme.colorScheme.primary
                                            canNavigate -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        }
                                    )
                                }
                                if (num < 3) {
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        IconButton(
                            onClick = { requestDismiss() },
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = s.commonClose,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                        when (currentStep) {
                            ProviderEditStep.SELECT_PRESET -> {
                                ProviderPresetStep(
                                    selectedPresetId = selectedPresetId,
                                    onSelectPreset = { preset ->
                                        selectedPresetId = preset.id
                                        val isZh = com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN
                                        name = preset.displayName(isZh)
                                        protocol = preset.protocol
                                        baseUrl = preset.defaultBaseUrl
                                        updateSuggestedEndpoints()
                                        markDirty()
                                        currentStep = ProviderEditStep.CONFIG_CONNECTION
                                    },
                                    onSelectCustom = {
                                        name = ""
                                        protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
                                        baseUrl = ""
                                        updateSuggestedEndpoints()
                                        selectedPresetId = null
                                        isDirty = false
                                        currentStep = ProviderEditStep.CONFIG_CONNECTION
                                    }
                                )
                            }

                            ProviderEditStep.CONFIG_CONNECTION -> {
                                ProviderConnectionStep(
                                    name = name,
                                    onNameChange = {
                                        name = it
                                        markDirty()
                                    },
                                    protocol = protocol,
                                    onProtocolChange = {
                                        protocol = it
                                        updateSuggestedEndpoints()
                                        selectedPresetId = detectPresetId(baseUrl)
                                        markDirty()
                                    },
                                    baseUrl = baseUrl,
                                    onBaseUrlChange = {
                                        baseUrl = it
                                        updateSuggestedEndpoints()
                                        selectedPresetId = detectPresetId(it)
                                        markDirty()
                                    },
                                    apiKey = apiKey,
                                    onApiKeyChange = {
                                        apiKey = it
                                        markDirty()
                                    },
                                    modelsEndpoint = modelsEndpoint,
                                    onModelsEndpointChange = {
                                        modelsEndpoint = it
                                        markDirty()
                                    },
                                    generateEndpoint = generateEndpoint,
                                    onGenerateEndpointChange = {
                                        generateEndpoint = it
                                        markDirty()
                                    },
                                    fetchError = fetchError,
                                    isFetching = isFetching
                                )
                            }

                            ProviderEditStep.SELECT_MODELS -> {
                                ProviderModelSelectionStep(
                                    fetchedModelConfigs = fetchedModelConfigs,
                                    selectedModelIds = selectedModelIds,
                                    onSelectedModelIdsChange = {
                                        selectedModelIds = it
                                        markDirty()
                                    },
                                    isSingleModelMode = isSingleModelMode,
                                    onConfigureReasoning = { reasoningModelId = it },
                                    onUpdateModelConfig = { updated ->
                                        fetchedModelConfigs = fetchedModelConfigs.map {
                                            if (it.id == updated.id) updated else it
                                        }
                                        markDirty()
                                    },
                                    currentProvider = ::currentProvider,
                                    coroutineScope = scope
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isSingleModelMode && currentStep == ProviderEditStep.CONFIG_CONNECTION && initialProvider == null) {
                                TextButton(
                                    enabled = !isFetching,
                                    onClick = { currentStep = ProviderEditStep.SELECT_PRESET },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(s.providerPrevStep, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                }
                            } else if (!isSingleModelMode && currentStep == ProviderEditStep.SELECT_MODELS) {
                                TextButton(
                                    enabled = !isFetching,
                                    onClick = { currentStep = ProviderEditStep.CONFIG_CONNECTION },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(s.providerPrevStep, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (currentStep == ProviderEditStep.SELECT_MODELS && !isSingleModelMode) {
                                Text(
                                    text = if (selectedModelIds.isNotEmpty()) s.providerFilterSelected(selectedModelIds.size) else s.providerNoModelsEmpty,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                enabled = !isFetching,
                                onClick = { requestDismiss() },
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(s.commonCancel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                            }

                            if (currentStep == ProviderEditStep.CONFIG_CONNECTION) {
                                Button(
                                    enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isFetching,
                                    modifier = Modifier.height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        scope.launch {
                                            isFetching = true
                                            fetchError = null
                                            try {
                                                val tempProvider = currentProvider()
                                                val adapter = AdapterFactory.getAdapter(protocol)
                                                val discoveredList = adapter.fetchDiscoveredModels(tempProvider)
                                                val discoveredMap = discoveredList.associateBy { it.id }
                                                val models = discoveredList.map { it.id }
                                                if (models.isNotEmpty() || initialModels.isNotEmpty()) {
                                                    val existingMap = initialModels.associateBy { it.upstreamModelId }
                                                    val modelIds = (models + initialModels.map { it.upstreamModelId }).distinct()
                                                    val configs = modelIds.map { mName ->
                                                        val existing = existingMap[mName]
                                                        val disc = discoveredMap[mName]
                                                        val isUnavailable = mName !in models
                                                        val inputLimit = existing?.tokenLimits?.contextWindow
                                                            ?: existing?.tokenLimits?.inputTokenLimit
                                                            ?: disc?.inputTokenLimit
                                                        val inputSource = if (existing?.tokenLimits?.contextWindow != null) {
                                                            existing.tokenLimits.contextWindowSource
                                                        } else if (existing?.tokenLimits?.inputTokenLimit != null) {
                                                            existing.tokenLimits.inputTokenLimitSource
                                                        } else {
                                                            disc?.inputTokenLimitSource ?: TokenLimitSource.UNKNOWN
                                                        }
                                                        val outputLimit = existing?.tokenLimits?.outputTokenLimit ?: disc?.outputTokenLimit
                                                        val outputSource = if (existing?.tokenLimits?.outputTokenLimit != null) {
                                                            existing.tokenLimits.outputTokenLimitSource
                                                        } else {
                                                            disc?.outputTokenLimitSource ?: TokenLimitSource.UNKNOWN
                                                        }
                                                        val isVision = existing?.capabilities?.supportsVision ?: disc?.supportsVision ?: false
                                                        val inputModalities = existing?.capabilities?.inputModalities?.toSet()?.takeIf { it.isNotEmpty() } ?: disc?.inputModalities.orEmpty()
                                                        val outputModalities = existing?.capabilities?.outputModalities?.toSet()?.takeIf { it.isNotEmpty() } ?: disc?.outputModalities.orEmpty()
                                                        val inputMimeTypes = existing?.capabilities?.inputMimeTypes?.takeIf { it.isNotEmpty() } ?: disc?.inputMimeTypes.orEmpty()
                                                        val roles = existing?.capabilities?.roles?.toSet()?.takeIf { it.isNotEmpty() } ?: disc?.roles.orEmpty()
                                                        val isImageGeneration = existing?.capabilities?.let {
                                                            ModelRole.IMAGE_GENERATION in it.roles && ModelRole.AGENT !in it.roles
                                                        } ?: disc?.isImageGeneration ?: false
                                                        val compressionPolicy = existing?.compressionPolicy ?: disc?.compressionPolicy?.takeIf { policy ->
                                                            (inputLimit == null || policy.maxTokenLimit <= inputLimit) &&
                                                                    (outputLimit == null || policy.maxOutputTokens <= outputLimit)
                                                        }
                                                        val isTools = existing?.capabilities?.tools ?: disc?.supportsTools ?: true
                                                        val reasoningMappings = existing?.let { model ->
                                                            ReasoningMappingSupport.parse(model.capabilities.reasoning.levels)
                                                        } ?: disc?.reasoningMappings.orEmpty()
                                                        val reasoningDraft = existing?.let { model ->
                                                            ReasoningConfigDraft.fromCapabilities(model.capabilities)
                                                        } ?: if (disc != null && disc.supportsReasoning && !isImageGeneration) {
                                                            val levels = disc.supportedReasoningLevels.mapNotNull { lvlStr ->
                                                                when (lvlStr.lowercase()) {
                                                                    "low" -> ReasoningLevel.LOW
                                                                    "medium" -> ReasoningLevel.MEDIUM
                                                                    "high" -> ReasoningLevel.HIGH
                                                                    "x_high", "xhigh" -> ReasoningLevel.X_HIGH
                                                                    "max" -> ReasoningLevel.MAX
                                                                    else -> null
                                                                }
                                                            }.toSet().ifEmpty {
                                                                setOf(ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH)
                                                            }
                                                            ReasoningConfigDraft(
                                                                enabled = true,
                                                                levels = levels,
                                                                customValue = null,
                                                                thinkingBudget = disc.thinkingBudget?.toInt(),
                                                                minThinkingBudget = disc.minThinkingBudget?.toInt(),
                                                                mappings = reasoningMappings
                                                            )
                                                        } else {
                                                            ReasoningConfigDraft(
                                                                enabled = false,
                                                                levels = emptySet(),
                                                                customValue = null,
                                                                thinkingBudget = null,
                                                                minThinkingBudget = null,
                                                                mappings = emptyMap()
                                                            )
                                                        }
                                                        CatalogModelConfig(
                                                            id = mName,
                                                            name = existing?.displayName ?: disc?.displayName ?: mName,
                                                            inputTokenLimit = inputLimit,
                                                            inputTokenLimitSource = inputSource,
                                                            outputTokenLimit = outputLimit,
                                                            outputTokenLimitSource = outputSource,
                                                            isVision = isVision,
                                                            inputModalities = inputModalities,
                                                            outputModalities = outputModalities,
                                                            inputMimeTypes = inputMimeTypes,
                                                            roles = roles,
                                                            isImageGeneration = isImageGeneration,
                                                            compressionPolicy = compressionPolicy,
                                                            reasoningMappings = reasoningMappings,
                                                            isReasoning = reasoningDraft.enabled,
                                                            reasoningDraft = reasoningDraft,
                                                            isTools = isTools,
                                                            isUnavailable = isUnavailable
                                                        )
                                                    }
                                                    fetchedModelConfigs = configs
                                                    selectedModelIds = initialModels.map { it.upstreamModelId }.toSet()
                                                    currentStep = ProviderEditStep.SELECT_MODELS
                                                } else {
                                                    fetchError = s.providerFetchFailedCheckUrlKey
                                                }
                                            } catch (error: kotlinx.coroutines.CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                fetchError = s.providerFetchFailedWithError(error.message ?: s.commonUnknown)
                                            } finally {
                                                isFetching = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                ) {
                                    if (isFetching) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Text(s.providerTesting, style = MaterialTheme.typography.labelMedium)
                                        }
                                    } else {
                                        Text(s.modelsFetchModels, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                                Button(
                                    enabled = selectedModelIds.isNotEmpty(),
                                    modifier = Modifier.height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        val finalProvider = currentProvider()
                                        val finalModels = fetchedModelConfigs
                                            .filter { it.id in selectedModelIds }
                                            .map { config ->
                                                val cleanId = config.id.replace('/', '-').replace(':', '-')
                                                val inputModalities = config.inputModalities
                                                    .ifEmpty {
                                                        if (config.isVision) setOf(ModelModality.TEXT, ModelModality.IMAGE) else setOf(ModelModality.TEXT)
                                                    }
                                                val existing = initialModels.firstOrNull { model ->
                                                    model.upstreamModelId == config.id
                                                }
                                                val modelId = existing?.id ?: "${providerId}-$cleanId"
                                                val tokenLimits = ModelTokenLimits(
                                                    contextWindow = config.inputTokenLimit.takeUnless { config.isImageGeneration },
                                                    contextWindowSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.inputTokenLimitSource,
                                                    inputTokenLimit = config.inputTokenLimit.takeUnless { config.isImageGeneration },
                                                    inputTokenLimitSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.inputTokenLimitSource,
                                                    outputTokenLimit = config.outputTokenLimit.takeUnless { config.isImageGeneration },
                                                    outputTokenLimitSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.outputTokenLimitSource
                                                )
                                                val capabilities = ModelCapabilities(
                                                    roles = if (config.isImageGeneration) listOf(ModelRole.IMAGE_GENERATION) else config.roles.ifEmpty { setOf(ModelRole.AGENT) }.toList(),
                                                    inputModalities = if (config.isImageGeneration) listOf(ModelModality.TEXT) else inputModalities.toList(),
                                                    outputModalities = if (config.isImageGeneration) listOf(ModelModality.IMAGE) else config.outputModalities.ifEmpty { setOf(ModelModality.TEXT) }.toList(),
                                                    tools = if (config.isImageGeneration) false else config.isTools,
                                                    inputMimeTypes = if (config.isImageGeneration) emptyList() else config.inputMimeTypes,
                                                    reasoning = if (config.isImageGeneration) ReasoningCapability(supported = false) else config.reasoningDraft.toCapability(
                                                        protocol = protocol,
                                                        outputTokenLimit = config.outputTokenLimit
                                                    ),
                                                    vision = if (config.isImageGeneration) false else config.isVision
                                                )
                                                (existing ?: UpstreamModel(
                                                    id = modelId,
                                                    providerId = providerId,
                                                    name = config.name,
                                                    upstreamModelId = config.id
                                                )).copy(
                                                    id = modelId,
                                                    providerId = providerId,
                                                    name = config.name,
                                                    displayName = existing?.displayName ?: config.name,
                                                    upstreamModelId = config.id,
                                                    tokenLimits = tokenLimits,
                                                    capabilities = capabilities,
                                                    compressionPolicy = if (config.isImageGeneration) null else config.compressionPolicy ?: existing?.compressionPolicy
                                                )
                                            }
                                        onSave(finalProvider, finalModels)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                ) {
                                    Text(s.commonSave, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }
        }

    if (showDiscardConfirm) {
        ConfirmDialog(
            title = s.providerDiscardConfirmTitle,
            message = s.providerDiscardConfirmMessage,
            isDestructive = true,
            onConfirm = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismiss = { showDiscardConfirm = false }
        )
    }

    reasoningModelId?.let { modelId ->
        val modelConfig = fetchedModelConfigs.firstOrNull { config -> config.id == modelId }
        if (modelConfig != null) {
            ReasoningConfigDialog(
                modelName = modelConfig.name,
                protocol = protocol,
                outputTokenLimit = modelConfig.outputTokenLimit,
                initialDraft = modelConfig.reasoningDraft,
                onDismiss = { reasoningModelId = null },
                onConfirm = { draft ->
                    fetchedModelConfigs = fetchedModelConfigs.map { config ->
                        if (config.id == modelId) {
                            config.copy(
                                isReasoning = draft.enabled,
                                reasoningDraft = draft
                            )
                        } else {
                            config
                        }
                    }
                    reasoningModelId = null
                    markDirty()
                }
            )
        }
    }
}
