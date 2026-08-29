package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.dialogs.provider.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProviderEditorDialog(
    initialProvider: Provider? = null,
    initialModels: List<UpstreamModel> = emptyList(),
    editingSingleModel: UpstreamModel? = null,
    onDismiss: () -> Unit,
    onSave: (Provider, List<UpstreamModel>) -> Unit,
    isDebugMode: Boolean = false,
    onViewModelCatalog: (String) -> Unit = {}
) {
    val s = strings()
    val scope = rememberCoroutineScope()

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
    var isDebugFetching by remember { mutableStateOf(false) }
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
                listOf(ProviderModelConfigMapper.toCatalogModelConfig(editingSingleModel))
            } else if (initialModels.isNotEmpty()) {
                initialModels.map { ProviderModelConfigMapper.toCatalogModelConfig(it) }
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

    fun skipFetchAndConfigureManually() {
        fetchedModelConfigs = ProviderModelConfigMapper.createManualCatalogConfigs(initialModels)
        selectedModelIds = initialModels.map { it.upstreamModelId }.toSet()
        fetchError = null
        currentStep = ProviderEditStep.SELECT_MODELS
        markDirty()
    }

    fun fetchModelsFromRemote() {
        scope.launch {
            isFetching = true
            fetchError = null
            try {
                val tempProvider = currentProvider()
                val adapter = AdapterFactory.getAdapter(protocol)
                val catalogResult = adapter.fetchModelCatalog(tempProvider)
                val discoveredList = catalogResult.models
                val models = discoveredList.map { it.id }
                if (models.isNotEmpty() || initialModels.isNotEmpty()) {
                    fetchedModelConfigs = ProviderModelConfigMapper.mergeDiscoveredCatalogConfigs(
                        discoveredList = discoveredList,
                        initialModels = initialModels
                    )
                    selectedModelIds = initialModels.map { it.upstreamModelId }.toSet()
                    currentStep = ProviderEditStep.SELECT_MODELS
                } else {
                    fetchError = catalogResult.errorMessage?.let {
                        s.providerModelsResponseUnavailable(it)
                    } ?: s.providerFetchFailedCheckUrlKey
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fetchError = s.providerFetchFailedWithError(error.message ?: s.commonUnknown)
            } finally {
                isFetching = false
            }
        }
    }

    fun requestModelCatalogDebug() {
        scope.launch {
            isDebugFetching = true
            try {
                val result = AdapterFactory.getAdapter(protocol).fetchModelCatalog(currentProvider())
                val rawBody = result.rawBody?.takeIf { it.isNotBlank() }
                onViewModelCatalog(
                    rawBody ?: s.providerModelsResponseUnavailable(result.errorMessage ?: s.commonUnknown)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onViewModelCatalog(
                    s.providerModelsResponseUnavailable(error.message ?: s.commonUnknown)
                )
            } finally {
                isDebugFetching = false
            }
        }
    }

    fun requestDismiss() {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    fun handleSave() {
        val finalProvider = currentProvider()
        val finalModels = ProviderModelConfigMapper.buildFinalUpstreamModels(
            fetchedModelConfigs = fetchedModelConfigs,
            selectedModelIds = selectedModelIds,
            initialModels = initialModels,
            providerId = providerId,
            protocol = protocol
        )
        onSave(finalProvider, finalModels)
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .width(AppTokens.Size.dialogWidth)
                .height(if (isSingleModelMode) AppTokens.Size.singleModelDialogHeight else AppTokens.Size.dialogHeight),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProviderEditorHeader(
                    isSingleModelMode = isSingleModelMode,
                    editingSingleModel = editingSingleModel,
                    initialProvider = initialProvider,
                    currentStep = currentStep,
                    isFetching = isFetching,
                    onStepSelect = { currentStep = it },
                    onClose = { requestDismiss() }
                )

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
                                    val isZh = I18nManager.currentLanguage == AppLanguage.ZH_CN
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
                                isFetching = isFetching || isDebugFetching,
                                onSkipFetch = ::skipFetchAndConfigureManually
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
                                onAddNewModel = { newModel ->
                                    fetchedModelConfigs = listOf(newModel) + fetchedModelConfigs.filter { it.id != newModel.id }
                                    selectedModelIds = selectedModelIds + newModel.id
                                    markDirty()
                                },
                                currentProvider = ::currentProvider,
                                coroutineScope = scope
                            )
                        }
                    }
                }

                ProviderEditorFooter(
                    currentStep = currentStep,
                    isSingleModelMode = isSingleModelMode,
                    initialProvider = initialProvider,
                    isFetching = isFetching,
                    isDebugFetching = isDebugFetching,
                    isDebugMode = isDebugMode,
                    name = name,
                    baseUrl = baseUrl,
                    selectedModelCount = selectedModelIds.size,
                    onPrevStep = {
                        currentStep = when (currentStep) {
                            ProviderEditStep.SELECT_MODELS -> ProviderEditStep.CONFIG_CONNECTION
                            ProviderEditStep.CONFIG_CONNECTION -> ProviderEditStep.SELECT_PRESET
                            else -> currentStep
                        }
                    },
                    onDebugCatalog = ::requestModelCatalogDebug,
                    onSkipFetch = ::skipFetchAndConfigureManually,
                    onFetchModels = ::fetchModelsFromRemote,
                    onCancel = { requestDismiss() },
                    onSave = ::handleSave
                )
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
