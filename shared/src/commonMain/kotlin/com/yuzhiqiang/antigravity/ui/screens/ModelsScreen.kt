package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.ui.dialogs.*
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.models.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch

@Composable
fun ModelsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val officialModels by viewModel.officialModels.collectAsState()
    val officialModelsError by viewModel.officialModelsError.collectAsState()
    val isFetchingOfficial by viewModel.isFetchingOfficialModels.collectAsState()
    val openProviderEditorRequest by viewModel.openProviderEditorRequest.collectAsState()
    val modelTestStatuses by viewModel.modelTestStatuses.collectAsState()
    val providerTestingIds by viewModel.providerTestingIds.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTabId by remember { mutableStateOf("official") }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var editingSingleModel by remember { mutableStateOf<UpstreamModel?>(null) }
    var policyEditingModelId by remember { mutableStateOf<String?>(null) }

    var isTestingOfficial by remember { mutableStateOf(false) }
    var officialTestSummaryText by remember { mutableStateOf<String?>(null) }
    var isOfficialTestSuccess by remember { mutableStateOf(true) }

    var debugDialogTitle by remember { mutableStateOf<String?>(null) }
    var debugDialogJson by remember { mutableStateOf<String?>(null) }

    var activeReasoningModelInfo by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var activeMultimodalModelInfo by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activeModelMetaInfo by remember { mutableStateOf<ModelMetaInfo?>(null) }

    val groupedOfficial = remember(officialModels) { groupOfficialModels(officialModels) }
    val filteredOfficial = groupedOfficial
    val availableOfficialCount = groupedOfficial.sumOf { group ->
        group.variants.count { variant -> variant.model.id !in config.disabledOfficialModels }
    }

    LaunchedEffect(openProviderEditorRequest) {
        if (openProviderEditorRequest) {
            selectedTabId = "official"
            editingProvider = null
            editingSingleModel = null
            showAddProviderDialog = true
            viewModel.consumeOpenProviderEditorRequest()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(
                horizontal = AppTokens.Spacing.pageHorizontal,
                vertical = AppTokens.Spacing.pageVertical
            ),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
            ) {
                Text(
                    text = "模型管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = "${availableOfficialCount + config.upstreamModels.size} 个可用模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(
                            horizontal = AppTokens.Spacing.content,
                            vertical = AppTokens.Spacing.compact
                        )
                    )
                }
            }
            Text(
                text = "统一调度 Google 官方原生模型与三方自建模型，灵活配置上下文压缩与思考预算",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start)
                        .widthIn(max = maxWidth)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                ) {
                    val isOfficialActive = selectedTabId == "official"
                    val officialCount = groupedOfficial.sumOf { group ->
                        group.variants.count { variant -> variant.model.id !in config.disabledOfficialModels }
                    }

                    ModernSegmentedTab(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "官方原生",
                        count = officialCount,
                        isActive = isOfficialActive,
                        onClick = {
                            selectedTabId = "official"
                            officialTestSummaryText = null
                        }
                    )

                    config.providers.forEach { provider ->
                        val isActive = selectedTabId == provider.id
                        val modelCount = config.virtualModels.count { virtualModel ->
                            config.upstreamModels.any { model ->
                                model.id == virtualModel.upstreamModelId && model.providerId == provider.id
                            }
                        }
                        ModernSegmentedTab(
                            icon = Icons.Outlined.Dns,
                            title = provider.name,
                            count = modelCount,
                            isActive = isActive,
                            onClick = {
                                selectedTabId = provider.id
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    editingProvider = null
                    editingSingleModel = null
                    showAddProviderDialog = true
                },
                modifier = Modifier.height(AppTokens.Size.controlHeight),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = AppTokens.Spacing.section),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AppTokens.Size.iconMedium)
                )
                Spacer(Modifier.width(AppTokens.Spacing.control))
                Text("添加上游服务", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (selectedTabId == "official") {
            OfficialModelsView(
                groupedModels = filteredOfficial,
                isFetching = isFetchingOfficial,
                configDisabledModels = config.disabledOfficialModels,
                compressionPolicies = config.modelCompressionPolicies,
                testSummary = officialTestSummaryText,
                isTestSuccess = isOfficialTestSuccess,
                isTesting = isTestingOfficial,
                fetchError = officialModelsError,
                onTestConnection = {
                    scope.launch {
                        isTestingOfficial = true
                        officialTestSummaryText = null
                        val startTime = System.currentTimeMillis()
                        try {
                            isOfficialTestSuccess = false
                            viewModel.fetchOfficialModels().join()
                            val errorMessage = viewModel.officialModelsError.value
                            val duration = System.currentTimeMillis() - startTime
                            if (errorMessage == null) {
                                officialTestSummaryText = "官方通道连通正常 (${duration}ms)"
                                isOfficialTestSuccess = true
                            } else {
                                officialTestSummaryText = "连接失败: $errorMessage"
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            officialTestSummaryText = "连接失败: ${e.message ?: "未知错误"}"
                            isOfficialTestSuccess = false
                        } finally {
                            isTestingOfficial = false
                        }
                    }
                },
                onRefresh = { viewModel.fetchOfficialModels() },
                onViewRawJson = {
                    debugDialogTitle = "官方模型原始 JSON 数据"
                    debugDialogJson =
                        com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.getFormattedRawJson()
                },
                onViewModifiedJson = {
                    debugDialogTitle = "官方模型解析后 JSON 数据"
                    debugDialogJson =
                        com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.getFormattedModifiedJson()
                },
                onToggleGroup = { group ->
                    val allIds = group.variants.map { it.model.id }.toSet()
                    val isCurrentlyDisabled = allIds.any { it in config.disabledOfficialModels }
                    viewModel.toggleOfficialModelGroup(allIds, isCurrentlyDisabled)
                },
                onEditPolicy = { modelId -> policyEditingModelId = modelId },
                onOpenVisionDetail = { name, vision ->
                    activeMultimodalModelInfo = name to vision
                },
                onOpenReasoningDetail = { name, levels ->
                    activeReasoningModelInfo = name to levels
                },
                onOpenInfoDetail = { meta ->
                    activeModelMetaInfo = meta
                }
            )
        } else {
            val currentProvider = config.providers.find { it.id == selectedTabId }
            if (currentProvider != null) {
                val providerModels = config.upstreamModels.filter { it.providerId == currentProvider.id }
                val filteredProviderModels = providerModels

                val isProviderTesting = currentProvider.id in providerTestingIds

                CustomProviderView(
                    provider = currentProvider,
                    models = filteredProviderModels,
                    modelTestStatuses = modelTestStatuses,
                    isProviderTesting = isProviderTesting,
                    compressionPolicies = config.modelCompressionPolicies,
                    onEditProvider = {
                        editingProvider = currentProvider
                        editingSingleModel = null
                        showAddProviderDialog = true
                    },
                    onDeleteProvider = {
                        viewModel.showConfirmDialog(
                            AppViewModel.ConfirmDialogState(
                                title = "删除服务商",
                                message = "确定要删除服务商「${currentProvider.name}」吗？关联的 ${providerModels.size} 个模型配置将一并移除。",
                                confirmLabel = "删除",
                                isDestructive = true,
                                onConfirm = {
                                    viewModel.deleteProvider(currentProvider.id)
                                    selectedTabId = "official"
                                }
                            )
                        )
                    },
                    onTestProvider = {
                        viewModel.testProviderModels(currentProvider.id)
                    },
                    onEditSingleModel = { model ->
                        editingProvider = currentProvider
                        editingSingleModel = model
                        showAddProviderDialog = true
                    },
                   onDeleteSingleModel = { model ->
                       viewModel.showConfirmDialog(
                           AppViewModel.ConfirmDialogState(
                               title = "删除模型",
                               message = "确定要删除模型「${model.displayName ?: model.upstreamModelId}」吗？",
                               confirmLabel = "删除",
                               isDestructive = true,
                               onConfirm = {
                                    viewModel.deleteSingleModel(model.id)
                               }
                           )
                       )
                   },
                   onTestSingleModel = { model ->
                        viewModel.testSingleModel(model, currentProvider)
                   },
                   onToggleModelEnabled = { model ->
                        viewModel.toggleCustomModel(model.id)
                   },
                   onEditPolicy = { modelId -> policyEditingModelId = modelId },
                   onOpenVisionDetail = { name, vision ->
                       activeMultimodalModelInfo = name to vision
                   },
                   onOpenReasoningDetail = { name, levels ->
                       activeReasoningModelInfo = name to levels
                   },
                   onOpenInfoDetail = { meta ->
                       activeModelMetaInfo = meta
                   },
                    onCopyNotice = { msg -> viewModel.showNotice(msg) }
               )
           }
       }
   }

   if (showAddProviderDialog) {
        val currentModels = editingProvider?.let { provider ->
            config.upstreamModels.filter { model -> model.providerId == provider.id }
        }.orEmpty()

        ProviderEditorDialog(
            initialProvider = editingProvider,
            initialModels = currentModels,
            editingSingleModel = editingSingleModel,
            onDismiss = {
                showAddProviderDialog = false
                editingProvider = null
                editingSingleModel = null
            },
            onSave = { provider, models ->
                val saved = if (editingSingleModel != null) {
                    val updated = models.firstOrNull { model ->
                        model.upstreamModelId == editingSingleModel?.upstreamModelId
                    }
                    updated?.let(viewModel::updateSingleModel) == true
                } else {
                    viewModel.saveProvider(provider, models)
                }
                if (saved) {
                    showAddProviderDialog = false
                    editingSingleModel = null
                    selectedTabId = provider.id
                }
            }
        )
    }

   policyEditingModelId?.let { modelId ->
        val currentPolicy = config.modelCompressionPolicies[modelId]
        PolicyEditorDialog(
            modelId = modelId,
            initialPolicy = currentPolicy,
            onDismiss = { policyEditingModelId = null },
            onSave = { policy ->
                viewModel.saveCompressionPolicy(modelId, policy)
                policyEditingModelId = null
            }
        )
    }

   debugDialogJson?.let { jsonContent ->
       OfficialCatalogDebugDialog(
           title = debugDialogTitle ?: "JSON 数据",
           jsonContent = jsonContent,
           onDismiss = {
               debugDialogJson = null
               debugDialogTitle = null
           },
           onCopy = {
               copyTextToClipboard(jsonContent)
                viewModel.showNotice("已复制 JSON 数据")
            }
        )
    }

    activeReasoningModelInfo?.let { (name, levels) ->
        ReasoningDetailDialog(
            modelName = name,
            reasoningLevels = levels,
            onDismiss = { activeReasoningModelInfo = null }
        )
    }

    activeMultimodalModelInfo?.let { (name, hasVision) ->
        MultimodalDetailDialog(
            modelName = name,
            supportsVision = hasVision,
            onDismiss = { activeMultimodalModelInfo = null }
        )
    }

   activeModelMetaInfo?.let { meta ->
       ModelInfoDialog(
           modelName = meta.name,
           modelId = meta.id,
            contextLimit = meta.contextLimit,
            outputLimit = meta.outputLimit,
           roles = meta.roles,
           onDismiss = { activeModelMetaInfo = null }
       )
   }
}
