package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.ui.dialogs.*
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioGlassSurface
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.components.tour.tourAnchor
import com.yuzhiqiang.antigravity.ui.screens.models.*
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun ModelsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val tourManager = LocalSpotlightTourManager.current
    val accounts by viewModel.accounts.collectAsState()
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
    var editingSingleModel by remember { mutableStateOf<ProviderModelBinding?>(null) }
    var policyEditingTarget by remember {
        mutableStateOf<Pair<CompressionPolicyTargetType, String>?>(null)
    }

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

    LaunchedEffect(openProviderEditorRequest) {
        if (openProviderEditorRequest) {
            selectedTabId = "official"
            editingProvider = null
            editingSingleModel = null
            showAddProviderDialog = true
            viewModel.consumeOpenProviderEditorRequest()
        }
    }

    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val scrollState = rememberScrollState()

    val officialCount = groupedOfficial.size
    val tabItems = remember(config.providers, config.providerModelBindings, groupedOfficial, s, officialCount) {
        val list = mutableListOf<ProviderTabItem>()
        list.add(
            ProviderTabItem(
                id = "official",
                title = s.modelsOfficialDefault,
                icon = Icons.Outlined.AutoAwesome,
                count = officialCount
            )
        )
        config.providers.forEach { provider ->
            val modelCount = config.providerModelBindings.count { it.providerConfigId == provider.id }
            list.add(
                ProviderTabItem(
                    id = provider.id,
                    title = provider.name,
                    icon = Icons.Outlined.Dns,
                    count = modelCount
                )
            )
        }
        list
    }

    val totalModelsCount = tabItems.sumOf { it.count }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                horizontal = AppTokens.Spacing.pageHorizontal,
                vertical = AppTokens.Spacing.pageVertical
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 顶部主标题 (与 Accounts 保持一致的 Badge 格式)
        PageHeader(
            title = s.modelsTitle,
            badge = "$totalModelsCount"
        )

        // 2. 现代毛玻璃浮岛顶栏操作栏 (与 Accounts 保持一致的 StudioGlassSurface)
        StudioGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .tourAnchor(TourStep.MODELS_MANAGE, tourManager),
            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = StudioDesignTokens.Padding.topBarHorizontal,
                        vertical = 8.dp
                    )
            ) {
                ProviderTabLayout(
                    items = tabItems,
                    selectedId = selectedTabId,
                    onSelect = { tabId ->
                        selectedTabId = tabId
                        if (tabId == "official") {
                            officialTestSummaryText = null
                        }
                    },
                    trailingAction = {
                        Button(
                            onClick = {
                                editingProvider = null
                                editingSingleModel = null
                                showAddProviderDialog = true
                            },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = s.modelsAddProvider,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            )
                        }
                    }
                )
            }
        }

        AnimatedContent(
            targetState = selectedTabId,
            transitionSpec = {
                val targetIndex = tabItems.indexOfFirst { it.id == targetState }.coerceAtLeast(0)
                val initialIndex = tabItems.indexOfFirst { it.id == initialState }.coerceAtLeast(0)
                val direction = if (targetIndex >= initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }

                slideIntoContainer(
                    towards = direction,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(200)) togetherWith
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(animationSpec = tween(160))
            },
            modifier = Modifier.fillMaxWidth()
        ) { currentTabId ->
            if (currentTabId == "official") {
                OfficialModelsView(
                    groupedModels = filteredOfficial,
                    isFetching = isFetchingOfficial,
                    disabledCatalogModelIds = config.disabledOfficialCatalogModelIds,
                    compressionPolicyAssignments = config.compressionPolicyAssignments,
                    testSummary = officialTestSummaryText,
                    isTestSuccess = isOfficialTestSuccess,
                    isTesting = isTestingOfficial,
                    fetchError = officialModelsError,
                    hasAccounts = accounts.isNotEmpty(),
                    onNavigateToAccounts = { viewModel.selectTab(NavTab.ACCOUNTS) },
                    onTestConnection = {
                        scope.launch {
                            isTestingOfficial = true
                            officialTestSummaryText = null
                            val startTime = System.currentTimeMillis()
                            try {
                                isOfficialTestSuccess = false
                                viewModel.fetchOfficialModels().join()
                                val errorMessage = viewModel.officialModelsError.value
                                val duration = "${System.currentTimeMillis() - startTime}ms"
                                if (errorMessage == null) {
                                    officialTestSummaryText = s.modelsTestSuccess(duration)
                                    isOfficialTestSuccess = true
                                } else {
                                    officialTestSummaryText = s.modelsTestFailed
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                officialTestSummaryText = s.modelsTestFailed
                                isOfficialTestSuccess = false
                            } finally {
                                isTestingOfficial = false
                            }
                        }
                    },
                    onRefresh = { viewModel.fetchOfficialModels() },
                    onViewRawJson = {
                        debugDialogTitle = s.modelsRawJsonTitle
                        debugDialogJson =
                            com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.getFormattedRawJson()
                    },
                    onViewModifiedJson = {
                        debugDialogTitle = s.modelsModifiedJsonTitle
                        debugDialogJson =
                            com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.getFormattedModifiedJson(
                                config = config,
                                proxyPort = viewModel.actualProxyPort.value
                            )
                    },
                    onToggleGroup = { group ->
                        val allIds = group.variants.map { it.model.catalogModelId }.toSet()
                        val isCurrentlyDisabled = allIds.any { it in config.disabledOfficialCatalogModelIds }
                        viewModel.toggleOfficialModelGroup(allIds, isCurrentlyDisabled)
                    },
                    onEditPolicy = { catalogModelId ->
                        policyEditingTarget = CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL to catalogModelId
                    },
                    onOpenVisionDetail = { name, vision ->
                        activeMultimodalModelInfo = name to vision
                    },
                    onOpenReasoningDetail = { name, levels ->
                        activeReasoningModelInfo = name to levels
                    },
                    onOpenInfoDetail = { meta ->
                        activeModelMetaInfo = meta
                    },
                    isDebugMode = config.isDebugMode
                )
            } else {
                val currentProvider = config.providers.find { it.id == currentTabId }
                if (currentProvider != null) {
                    val providerModels = config.providerModelBindings.filter {
                        it.providerConfigId == currentProvider.id
                    }
                    val filteredProviderModels = providerModels

                    val isProviderTesting = currentProvider.id in providerTestingIds

                    CustomProviderView(
                        provider = currentProvider,
                        models = filteredProviderModels,
                        modelTestStatuses = modelTestStatuses,
                        isProviderTesting = isProviderTesting,
                        compressionPolicyAssignments = config.compressionPolicyAssignments,
                        onEditProvider = {
                            editingProvider = currentProvider
                            editingSingleModel = null
                            showAddProviderDialog = true
                        },
                        onDeleteProvider = {
                            viewModel.showConfirmDialog(
                                AppViewModel.ConfirmDialogState(
                                    title = s.modelsDeleteProviderConfirmTitle,
                                    message = s.modelsDeleteProviderConfirmMessage(
                                        currentProvider.name,
                                        providerModels.size
                                    ),
                                    confirmLabel = s.commonDelete,
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
                                    title = s.modelsDeleteModelConfirmTitle,
                                    message = s.modelsDeleteModelConfirmMessage(model.effectiveName),
                                    confirmLabel = s.commonDelete,
                                    isDestructive = true,
                                    onConfirm = {
                                        viewModel.deleteSingleModel(model.bindingId)
                                    }
                                )
                            )
                        },
                        onTestSingleModel = { model ->
                            viewModel.testSingleModel(model, currentProvider)
                        },
                        onToggleModelEnabled = { model ->
                            viewModel.toggleCustomModel(model.bindingId)
                        },
                        onEditPolicy = { bindingId ->
                            policyEditingTarget = CompressionPolicyTargetType.PROVIDER_MODEL_BINDING to bindingId
                        },
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
    }

    if (showAddProviderDialog) {
        val currentModels = editingProvider?.let { provider ->
            config.providerModelBindings.filter { model -> model.providerConfigId == provider.id }
        }.orEmpty()

        ProviderEditorDialog(
            initialProvider = editingProvider,
            initialModels = currentModels,
            editingSingleModel = editingSingleModel,
            isDebugMode = config.isDebugMode,
            onViewModelCatalog = { rawBody ->
                debugDialogTitle = s.providerViewModelsResponse
                debugDialogJson = rawBody
            },
            onDismiss = {
                showAddProviderDialog = false
                editingProvider = null
                editingSingleModel = null
            },
            onSave = { provider, models ->
                val saved = if (editingSingleModel != null) {
                    val updated = models.firstOrNull { model ->
                        model.providerModelId == editingSingleModel?.providerModelId
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

    policyEditingTarget?.let { (targetType, targetId) ->
        val currentPolicy = config.compressionPolicyAssignments.firstOrNull { assignment ->
            assignment.targetType == targetType && assignment.targetId == targetId
        }?.policy
        val officialMatch = if (targetType == CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL) {
            groupedOfficial.firstOrNull { group ->
                group.baseItem.catalogModelId == targetId ||
                        group.variants.any { it.model.catalogModelId == targetId }
            }
        } else {
            null
        }
        val customMatch = if (targetType == CompressionPolicyTargetType.PROVIDER_MODEL_BINDING) {
            config.providerModelBindings.firstOrNull { it.bindingId == targetId }
        } else {
            null
        }

        val modelDisplayName = officialMatch?.variants
            ?.firstOrNull { it.model.catalogModelId == targetId }
            ?.model
            ?.displayName
            ?: officialMatch?.baseItem?.displayName
            ?: customMatch?.effectiveName
            ?: targetId

        val contextWindow = officialMatch?.baseItem?.let { it.contextWindow ?: it.maxTokens }
            ?: customMatch?.tokenLimits?.let { it.contextWindow ?: it.inputTokenLimit }

        PolicyEditorDialog(
            modelId = targetId,
            modelDisplayName = modelDisplayName,
            initialPolicy = currentPolicy,
            contextWindow = contextWindow,
            onDismiss = { policyEditingTarget = null },
            onSave = { policy ->
                viewModel.saveCompressionPolicy(targetType, targetId, policy)
                policyEditingTarget = null
            }
        )
    }

    debugDialogJson?.let { jsonContent ->
        OfficialCatalogDebugDialog(
            title = debugDialogTitle ?: s.modelsJsonData,
            jsonContent = jsonContent,
            onDismiss = {
                debugDialogJson = null
                debugDialogTitle = null
            },
            onCopy = {
                if (copyToClipboard(jsonContent)) {
                    viewModel.showNotice(s.modelsCopiedJson)
                }
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
