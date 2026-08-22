package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.ui.components.FallbackSelector
import com.yuzhiqiang.antigravity.ui.dialogs.*
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

data class ModelMetaInfo(
    val name: String,
    val id: String,
    val contextLimit: Long? = null,
    val outputLimit: Long? = null,
    val roles: List<String> = emptyList()
)

// 官方模型聚合分组数据结构
data class OfficialModelVariant(
    val label: String,
    val model: OfficialCatalogModel
)

data class GroupedOfficialModel(
    val baseName: String,
    val baseItem: OfficialCatalogModel,
    val variants: List<OfficialModelVariant>
)

private fun filterMainAgentModels(models: List<OfficialCatalogModel>): List<OfficialCatalogModel> {
    val hasAgentMetadata = models.any { it.roles.isNotEmpty() }
    val filtered = if (hasAgentMetadata) {
        models.filter {
            it.roles.contains("agent") && !it.isDeprecated && it.isRecommended
        }
    } else {
        models.filter { !it.isDeprecated }
    }

    return filtered.sortedWith(
        compareBy<OfficialCatalogModel> { it.agentSortOrder ?: Long.MAX_VALUE }
            .thenBy { it.id }
    )
}

private fun groupOfficialModels(models: List<OfficialCatalogModel>): List<GroupedOfficialModel> {
    val mainModels = filterMainAgentModels(models)
    if (mainModels.isEmpty()) return emptyList()

    val regex = Regex("""^(.*?)(?:\s*\((.*?)\))?$""")
    val groupMap = linkedMapOf<String, MutableList<OfficialModelVariant>>()
    val baseItemMap = mutableMapOf<String, OfficialCatalogModel>()

    for (m in mainModels) {
        val rawName = m.displayName.ifBlank { m.id }
        val match = regex.find(rawName)
        val baseName = match?.groupValues?.getOrNull(1)?.trim() ?: rawName
        val variantLabel = match?.groupValues?.getOrNull(2)?.trim()?.ifBlank { null }
            ?: if (m.supportsReasoning) "Thinking" else "Default"

        if (!baseItemMap.containsKey(baseName)) {
            baseItemMap[baseName] = m
        }
        groupMap.getOrPut(baseName) { mutableListOf() }.add(OfficialModelVariant(variantLabel, m))
    }

    val levelOrder = mapOf("High" to 1, "Medium" to 2, "Low" to 3, "Thinking" to 4, "Max" to 5, "Default" to 6)

    return groupMap.map { (baseName, variants) ->
        val sortedVariants = variants.sortedBy { levelOrder[it.label] ?: 99 }
        GroupedOfficialModel(
            baseName = baseName,
            baseItem = baseItemMap[baseName] ?: sortedVariants.first().model,
            variants = sortedVariants
        )
    }
}

// 品牌识别辅助（用于为卡片赋予品牌色与 Logo）
private enum class ModelBrand(
    val brandName: String,
    val colors: AppTokens.Brand.Colors,
    val iconVector: ImageVector
) {
    GEMINI("Google DeepMind", AppTokens.Brand.gemini, Icons.Outlined.AutoAwesome),
    CLAUDE("Anthropic", AppTokens.Brand.claude, Icons.Outlined.Psychology),
    OPENAI("OpenAI", AppTokens.Brand.openAi, Icons.Outlined.DataObject),
    DEEPSEEK("DeepSeek", AppTokens.Brand.deepSeek, Icons.Outlined.Terminal),
    QWEN("Alibaba Cloud", AppTokens.Brand.qwen, Icons.Outlined.Cloud),
    CUSTOM("Custom Model", AppTokens.Brand.custom, Icons.Outlined.Dns);

    companion object {
        fun fromModelName(name: String): ModelBrand {
            val lower = name.lowercase()
            return when {
                lower.contains("gemini") -> GEMINI
                lower.contains("claude") || lower.contains("sonnet") || lower.contains("opus") || lower.contains("haiku") -> CLAUDE
                lower.contains("gpt") || lower.contains("o1") || lower.contains("o3") || lower.contains("chatgpt") -> OPENAI
                lower.contains("deepseek") -> DEEPSEEK
                lower.contains("qwen") -> QWEN
                else -> CUSTOM
            }
        }
    }
}

private fun copyTextToClipboard(value: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}

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

    // 当前选中的 Tab: "official" 表示官方原生，否则为 Provider 的 id
    var selectedTabId by remember { mutableStateOf("official") }
    var searchQuery by remember { mutableStateOf("") }

    var showAddProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var editingSingleModel by remember { mutableStateOf<UpstreamModel?>(null) }
    var policyEditingModelId by remember { mutableStateOf<String?>(null) }

    // 官方连通性测试状态
    var isTestingOfficial by remember { mutableStateOf(false) }
    var officialTestSummaryText by remember { mutableStateOf<String?>(null) }
    var isOfficialTestSuccess by remember { mutableStateOf(true) }

    // 官方模型 Debug 弹窗状态
    var debugDialogTitle by remember { mutableStateOf<String?>(null) }
    var debugDialogJson by remember { mutableStateOf<String?>(null) }

    // 模型能力详情弹窗状态
    var activeReasoningModelInfo by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var activeMultimodalModelInfo by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activeModelMetaInfo by remember { mutableStateOf<ModelMetaInfo?>(null) }

    val groupedOfficial = remember(officialModels) { groupOfficialModels(officialModels) }
    val filteredOfficial = remember(groupedOfficial, searchQuery) {
        if (searchQuery.isBlank()) groupedOfficial
        else groupedOfficial.filter {
            it.baseName.contains(searchQuery, ignoreCase = true) || it.baseItem.id.contains(
                searchQuery,
                ignoreCase = true
            )
        }
    }
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
        // =========================================================================
        // 1. 页面标题层级
        // =========================================================================
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

        // =========================================================================
        // 2. Provider 筛选与主操作
        // =========================================================================
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
                    // (1) 官方原生 Tab
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

                    // (2) 各自定义 Provider Tab
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

        // =========================================================================
        // 3. 主体内容卡片 (内嵌现代工具栏与网格)
        // =========================================================================
        if (selectedTabId == "official") {
            OfficialModelsView(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
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
                    allIds.forEach { modelId ->
                        if (isCurrentlyDisabled) {
                            if (modelId in config.disabledOfficialModels) {
                                viewModel.toggleOfficialModel(modelId)
                            }
                        } else {
                            if (modelId !in config.disabledOfficialModels) {
                                viewModel.toggleOfficialModel(modelId)
                            }
                        }
                    }
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
                val filteredProviderModels = if (searchQuery.isBlank()) providerModels
                else providerModels.filter {
                    it.effectiveName.contains(
                        searchQuery,
                        ignoreCase = true
                    ) || it.upstreamModelId.contains(searchQuery, ignoreCase = true)
                }

                val isProviderTesting = currentProvider.id in providerTestingIds

                CustomProviderView(
                    provider = currentProvider,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    models = filteredProviderModels,
                    modelTestStatuses = modelTestStatuses,
                    isProviderTesting = isProviderTesting,
                    compressionPolicies = config.modelCompressionPolicies,
                    virtualModels = config.virtualModels,
                    onEditProvider = {
                        editingProvider = currentProvider
                        editingSingleModel = null
                        showAddProviderDialog = true
                    },
                    onDeleteProvider = {
                        viewModel.showConfirmDialog(
                            AppViewModel.ConfirmDialogState(
                                title = "删除服务商",
                                message = "确定要删除服务商「${currentProvider.name}」吗？其下关联的 ${providerModels.size} 个模型也将一并移除。",
                                isDestructive = true,
                                onConfirm = {
                                    if (viewModel.deleteProvider(currentProvider.id)) {
                                        selectedTabId = "official"
                                    }
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
                    onEditPolicy = { modelId -> policyEditingModelId = modelId },
                    onUpdateFallback = { virtualModelId, fallbackId ->
                        viewModel.updateVirtualModelFallback(virtualModelId, fallbackId)
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
                    onCopyNotice = { viewModel.showNotice(it) }
                )
            }
        }
    }

    // 弹窗系统
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

    val debugTitle = debugDialogTitle
    val debugJson = debugDialogJson
    if (debugTitle != null && debugJson != null) {
        OfficialCatalogDebugDialog(
            title = debugTitle,
            jsonContent = debugJson,
            onDismiss = {
                debugDialogJson = null
                debugDialogTitle = null
            },
            onCopy = {
                copyTextToClipboard(debugJson)
                viewModel.showNotice("JSON 已复制到剪贴板")
            }
        )
    }

    activeReasoningModelInfo?.let { (modelName, levels) ->
        ReasoningDetailDialog(
            modelName = modelName,
            reasoningLevels = levels,
            onDismiss = { activeReasoningModelInfo = null }
        )
    }

    activeMultimodalModelInfo?.let { (modelName, vision) ->
        MultimodalDetailDialog(
            modelName = modelName,
            supportsVision = vision,
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

// =========================================================================
// 现代分段 Tab 按钮 (Modern Segmented Tab)
// =========================================================================
@Composable
private fun ModernSegmentedTab(
    icon: ImageVector,
    title: String,
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tabShape = RoundedCornerShape(AppTokens.Radius.pill)
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .clip(tabShape)
            .clickable(onClick = onClick),
        shape = tabShape,
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppTokens.Spacing.content,
                vertical = AppTokens.Spacing.control
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppTokens.Size.iconMedium)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
            Surface(
                shape = RoundedCornerShape(AppTokens.Radius.pill),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isActive) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = AppTokens.Spacing.control,
                        vertical = AppTokens.Spacing.compact
                    )
                )
            }
        }
    }
}

// =========================================================================
// 官方原生模型视图 (Toolbar + 响应式双列卡片)
// =========================================================================
@Composable
private fun OfficialModelsView(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    groupedModels: List<GroupedOfficialModel>,
    isFetching: Boolean,
    configDisabledModels: List<String>,
    compressionPolicies: Map<String, com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy>,
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
    onOpenInfoDetail: (ModelMetaInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(
                    horizontal = AppTokens.Spacing.section,
                    vertical = AppTokens.Spacing.content
                ),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧状态
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
            ) {
                val hasError = fetchError != null || (testSummary != null && !isTestSuccess)
                val statusText = testSummary
                    ?: fetchError?.let { error -> "官方模型同步失败：$error" }
                    ?: if (isFetching) "正在同步官方模型数据..."
                    else if (groupedModels.isNotEmpty()) "官方模型数据已同步"
                    else "等待同步官方模型数据"
                val statusColor = when {
                    isTesting || isFetching -> AppStatusColors.warning
                    hasError -> MaterialTheme.colorScheme.error
                    groupedModels.isNotEmpty() -> AppStatusColors.success
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(AppTokens.Size.iconSmall)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧搜索与按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "搜索模型...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    },
                    modifier = Modifier
                        .width(AppTokens.Size.searchFieldWidth)
                        .height(AppTokens.Size.controlHeight),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true
                )
                ModernToolButton(
                    icon = Icons.Outlined.Sensors,
                    text = if (isTesting) "测试中..." else "测试连接",
                    onClick = onTestConnection,
                    enabled = !isTesting
                )
                ModernToolButton(
                    icon = Icons.Outlined.Refresh,
                    text = if (isFetching) "刷新中..." else "刷新",
                    onClick = onRefresh,
                    enabled = !isFetching
                )
                ModernToolButton(
                    icon = Icons.Outlined.Code,
                    text = "原始 JSON",
                    onClick = onViewRawJson
                )
                ModernToolButton(
                    icon = Icons.Outlined.Visibility,
                    text = "修改后 JSON",
                    onClick = onViewModifiedJson
                )
            }
        }

        // 模型卡片网格
        if (groupedModels.isEmpty() && isFetching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppTokens.Size.emptyStateHeight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppTokens.Size.iconLarge)
                )
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
                        Icons.Outlined.LayersClear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        "暂无匹配的官方模型",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columnCount = ((maxWidth.value + 16f) / 296f).toInt().coerceAtLeast(1)
                if (columnCount == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        groupedModels.forEach { group ->
                            ModernOfficialCard(
                                group = group,
                                configDisabledModels = configDisabledModels,
                                compressionPolicies = compressionPolicies,
                                onToggle = { onToggleGroup(group) },
                                onEditPolicy = { onEditPolicy(group.baseItem.id) },
                                onOpenVisionDetail = {
                                    onOpenVisionDetail(
                                        group.baseName,
                                        group.baseItem.supportsVision
                                    )
                                },
                                onOpenReasoningDetail = {
                                    onOpenReasoningDetail(
                                        group.baseName,
                                        group.variants.map { it.label })
                                },
                                onOpenInfoDetail = {
                                    onOpenInfoDetail(
                                        ModelMetaInfo(
                                            name = group.baseName,
                                            id = group.baseItem.id,
                                            contextLimit = group.baseItem.inputTokenLimit ?: group.baseItem.maxTokens,
                                            outputLimit = group.baseItem.outputTokenLimit,
                                            roles = group.baseItem.roles
                                        )
                                    )
                                }
                            )
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
                                    ModernOfficialCard(
                                        group = group,
                                        configDisabledModels = configDisabledModels,
                                        compressionPolicies = compressionPolicies,
                                        onToggle = { onToggleGroup(group) },
                                        onEditPolicy = { onEditPolicy(group.baseItem.id) },
                                        onOpenVisionDetail = {
                                            onOpenVisionDetail(
                                                group.baseName,
                                                group.baseItem.supportsVision
                                            )
                                        },
                                        onOpenReasoningDetail = {
                                            onOpenReasoningDetail(
                                                group.baseName,
                                                group.variants.map { it.label })
                                        },
                                        onOpenInfoDetail = {
                                            onOpenInfoDetail(
                                                ModelMetaInfo(
                                                    name = group.baseName,
                                                    id = group.baseItem.id,
                                                    contextLimit = group.baseItem.inputTokenLimit
                                                        ?: group.baseItem.maxTokens,
                                                    outputLimit = group.baseItem.outputTokenLimit,
                                                    roles = group.baseItem.roles
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowGroups.size == 1) {
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

// =========================================================================
// 现代化官方模型卡片 (Modern Official Model Card)
// =========================================================================
@Composable
private fun ModernOfficialCard(
    group: GroupedOfficialModel,
    configDisabledModels: List<String>,
    compressionPolicies: Map<String, com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy>,
    onToggle: () -> Unit,
    onEditPolicy: () -> Unit,
    onOpenVisionDetail: () -> Unit,
    onOpenReasoningDetail: () -> Unit,
    onOpenInfoDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allModelIds = group.variants.map { it.model.id }.toSet()
    val isDisabled = allModelIds.any { it in configDisabledModels }
    val item = group.baseItem

    val policy =
        compressionPolicies[item.id] ?: group.variants.firstNotNullOfOrNull { compressionPolicies[it.model.id] }
    val hasPolicy = policy != null
    val brand = ModelBrand.fromModelName(group.baseName)

    val cardAlpha = if (isDisabled) 0.55f else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDisabled) 0.dp else AppTokens.Elevation.card
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- 1. 卡片 Header (品牌徽标 + 模型大名 + 右侧开关) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 品牌徽标使用令牌中的品牌色，不再在卡片内创建独立色板。
                    Box(
                        modifier = Modifier
                            .size(AppTokens.Size.brandMark)
                            .clip(MaterialTheme.shapes.small)
                            .background(brand.colors.start.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = brand.iconVector,
                            contentDescription = null,
                            tint = brand.colors.accent,
                            modifier = Modifier.size(AppTokens.Size.iconLarge)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
                        Text(
                            text = group.baseName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${brand.brandName} · ${if (item.contextWindow != null) "${item.contextWindow / 1000}K 上下文" else "官方多模态引擎"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // 现代 Switch 风格的启用/禁用按钮
                ModernToggleSwitch(
                    isChecked = !isDisabled,
                    onToggle = onToggle
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- 2. 卡片 Body (特性 Tags 徽章 + 推理档位胶囊) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppTokens.Spacing.card,
                        vertical = AppTokens.Spacing.content
                    ),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
            ) {
                // 特性徽章集合
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.supportsVision) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Image,
                            text = "视觉解析",
                            style = AppTokens.Feature.vision,
                            onClick = onOpenVisionDetail
                        )
                    }
                    if (item.supportsTools) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Build,
                            text = "工具联动",
                            style = AppTokens.Feature.tools,
                            onClick = null
                        )
                    }
                    if (item.supportsReasoning) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Psychology,
                            text = "深度推理",
                            style = AppTokens.Feature.reasoning,
                            onClick = onOpenReasoningDetail
                        )
                    }
                    ModernFeatureTag(
                        icon = Icons.Outlined.Info,
                        text = "规格详情",
                        style = AppTokens.Feature.info,
                        onClick = onOpenInfoDetail
                    )
                }

                // 推理档位胶囊行
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)) {
                    Text(
                        text = "推理档位 (REASONING TIERS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        group.variants.forEach { variant ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = AppTokens.Spacing.content,
                                        vertical = AppTokens.Spacing.compact
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(AppTokens.Spacing.compact)
                                            .clip(CircleShape)
                                            .background(AppStatusColors.success)
                                    )
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. 卡片 Footer (压缩策略管理栏) ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppTokens.Spacing.card,
                            vertical = AppTokens.Spacing.content
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Text(
                            text = "上下文压缩",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val pillLabel = if (hasPolicy) {
                        if (policy?.triggerThresholdTokens != null && policy.triggerThresholdTokens > 0) {
                            "${policy.triggerThresholdTokens / 1000}K (已自定义)"
                        } else {
                            "自定义策略"
                        }
                    } else {
                        "官方默认 (80%)"
                    }
                    val policyContainer = if (hasPolicy) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val policyContent = if (hasPolicy) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier.clickable(onClick = onEditPolicy),
                        shape = RoundedCornerShape(AppTokens.Radius.pill),
                        color = policyContainer,
                        contentColor = policyContent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasPolicy) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AppTokens.Spacing.content,
                                vertical = AppTokens.Spacing.compact
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                        ) {
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.Size.iconSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 自定义 Provider 视图
// =========================================================================
@Composable
private fun CustomProviderView(
    provider: Provider,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    models: List<UpstreamModel>,
    modelTestStatuses: Map<String, AppViewModel.ModelTestStatus>,
    isProviderTesting: Boolean,
    compressionPolicies: Map<String, com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy>,
    virtualModels: List<VirtualModel>,
    onEditProvider: () -> Unit,
    onDeleteProvider: () -> Unit,
    onTestProvider: () -> Unit,
    onEditSingleModel: (UpstreamModel) -> Unit,
    onDeleteSingleModel: (UpstreamModel) -> Unit,
    onTestSingleModel: (UpstreamModel) -> Unit,
    onEditPolicy: (String) -> Unit,
    onUpdateFallback: (String, String?) -> Unit,
    onOpenVisionDetail: (String, Boolean) -> Unit,
    onOpenReasoningDetail: (String, List<String>) -> Unit,
    onOpenInfoDetail: (ModelMetaInfo) -> Unit,
    onCopyNotice: (String) -> Unit
) {
    val passedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.SUCCESS }
    val failedCount = models.count { modelTestStatuses[it.id]?.status == AppViewModel.ModelTestStatusKind.ERROR }
    val hasTested = passedCount > 0 || failedCount > 0

    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)) {
        // 顶部服务商信息条
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

                    // Base URL 复制展示条
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

            // 右侧搜索与操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "搜索模型...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    },
                    modifier = Modifier
                        .width(AppTokens.Size.searchFieldWidth)
                        .height(AppTokens.Size.controlHeight),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true
                )
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

        // 模型网格
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
                val columnCount = ((maxWidth.value + 16f) / 296f).toInt().coerceAtLeast(1)
                if (columnCount == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        models.forEach { model ->
                            ModernCustomModelCard(
                                model = model,
                                testStatus = modelTestStatuses[model.id],
                                hasPolicy = compressionPolicies.containsKey(model.id),
                                virtualModels = virtualModels.filter { virtual ->
                                    virtual.upstreamModelId == model.id
                                },
                                allVirtualModels = virtualModels,
                                onUpdateFallback = onUpdateFallback,
                                onEditModel = { onEditSingleModel(model) },
                                onDeleteModel = { onDeleteSingleModel(model) },
                                onTestModel = { onTestSingleModel(model) },
                                onEditPolicy = { onEditPolicy(model.id) },
                                onOpenVisionDetail = {
                                    onOpenVisionDetail(
                                        model.displayName ?: model.upstreamModelId,
                                        model.capabilities.supportsVision
                                    )
                                },
                                onOpenReasoningDetail = {
                                    onOpenReasoningDetail(
                                        model.displayName ?: model.upstreamModelId, listOf("Thinking / Reasoning")
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
                                    ModernCustomModelCard(
                                        model = model,
                                        testStatus = modelTestStatuses[model.id],
                                        hasPolicy = compressionPolicies.containsKey(model.id),
                                        virtualModels = virtualModels.filter { virtual ->
                                            virtual.upstreamModelId == model.id
                                        },
                                        allVirtualModels = virtualModels,
                                        onUpdateFallback = onUpdateFallback,
                                        onEditModel = { onEditSingleModel(model) },
                                        onDeleteModel = { onDeleteSingleModel(model) },
                                        onTestModel = { onTestSingleModel(model) },
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
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowModels.size == 1) {
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

// =========================================================================
// 现代化三方模型卡片 (Modern Custom Model Card)
// =========================================================================
@Composable
private fun ModernCustomModelCard(
    model: UpstreamModel,
    testStatus: AppViewModel.ModelTestStatus?,
    hasPolicy: Boolean,
    virtualModels: List<VirtualModel>,
    allVirtualModels: List<VirtualModel>,
    onUpdateFallback: (String, String?) -> Unit,
    onEditModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onTestModel: () -> Unit,
    onEditPolicy: () -> Unit,
    onOpenVisionDetail: () -> Unit,
    onOpenReasoningDetail: () -> Unit,
    onOpenInfoDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelTitle = (model.displayName ?: "").ifBlank { model.upstreamModelId }
    val brand = ModelBrand.fromModelName(modelTitle)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = AppTokens.Elevation.card),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- 1. 卡片 Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(AppTokens.Size.brandMark)
                            .clip(MaterialTheme.shapes.small)
                            .background(brand.colors.start.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            brand.iconVector,
                            contentDescription = null,
                            tint = brand.colors.accent,
                            modifier = Modifier.size(AppTokens.Size.iconLarge)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
                        Text(
                            text = modelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "上游 ID: ${model.upstreamModelId}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 操作按钮组（编辑 / 删除）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEditModel,
                        modifier = Modifier.size(AppTokens.Size.compactControlHeight)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    }
                    IconButton(
                        onClick = onDeleteModel,
                        modifier = Modifier.size(AppTokens.Size.compactControlHeight)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- 2. 卡片 Body ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppTokens.Spacing.card,
                        vertical = AppTokens.Spacing.content
                    ),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
            ) {
                // 特性徽章集合
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (model.capabilities.supportsVision) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Image,
                            text = "视觉解析",
                            style = AppTokens.Feature.vision,
                            onClick = onOpenVisionDetail
                        )
                    }
                    if (model.capabilities.tools) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Build,
                            text = "工具联动",
                            style = AppTokens.Feature.tools,
                            onClick = null
                        )
                    }
                    if (model.capabilities.reasoning.supportsReasoning) {
                        ModernFeatureTag(
                            icon = Icons.Outlined.Psychology,
                            text = "深度推理",
                            style = AppTokens.Feature.reasoning,
                            onClick = onOpenReasoningDetail
                        )
                    }
                    ModernFeatureTag(
                        icon = Icons.Outlined.Info,
                        text = "规格详情",
                        style = AppTokens.Feature.info,
                        onClick = onOpenInfoDetail
                    )
                }

                if (virtualModels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)) {
                        Text(
                            "模型入口与备用路由",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        virtualModels.forEach { virtualModel ->
                            FallbackSelector(
                                source = virtualModel,
                                allVirtualModels = allVirtualModels,
                                onSelected = { fallbackId ->
                                    onUpdateFallback(virtualModel.id, fallbackId)
                                }
                            )
                        }
                    }
                }

                // 连通性测试胶囊
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)) {
                    Text(
                        text = "连通性测试 (ENDPOINT STATUS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    val dotColor = when (testStatus?.status) {
                        AppViewModel.ModelTestStatusKind.SUCCESS -> AppStatusColors.success
                        AppViewModel.ModelTestStatusKind.PENDING -> AppStatusColors.warning
                        AppViewModel.ModelTestStatusKind.ERROR -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val testContainer = when (testStatus?.status) {
                        AppViewModel.ModelTestStatusKind.SUCCESS -> AppStatusColors.successContainer
                        AppViewModel.ModelTestStatusKind.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val testContent = when (testStatus?.status) {
                        AppViewModel.ModelTestStatusKind.SUCCESS -> AppStatusColors.onSuccessContainer
                        AppViewModel.ModelTestStatusKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier.clickable(onClick = onTestModel),
                        shape = MaterialTheme.shapes.small,
                        color = testContainer,
                        contentColor = testContent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AppTokens.Spacing.content,
                                vertical = AppTokens.Spacing.control
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(AppTokens.Size.statusDot)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = when (testStatus?.status) {
                                    AppViewModel.ModelTestStatusKind.SUCCESS -> "连通成功 (${testStatus.latencyMs ?: 0}ms) · 点击重测"
                                    AppViewModel.ModelTestStatusKind.PENDING -> "正在向服务商发送握手探测..."
                                    AppViewModel.ModelTestStatusKind.ERROR -> "测试失败 (${testStatus.error ?: "网络异常"}) · 点击重试"
                                    null -> "尚未测试 · 点击检测连通性"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = testContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- 3. 卡片 Footer ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppTokens.Spacing.card,
                            vertical = AppTokens.Spacing.content
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                    ) {
                        Icon(
                            Icons.Outlined.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Text("上下文压缩", style = MaterialTheme.typography.bodySmall)
                    }

                    val pillLabel = if (hasPolicy) "自定义策略" else "上游默认 (80%)"
                    val policyContainer = if (hasPolicy) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val policyContent = if (hasPolicy) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier.clickable(onClick = onEditPolicy),
                        shape = RoundedCornerShape(AppTokens.Radius.pill),
                        color = policyContainer,
                        contentColor = policyContent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasPolicy) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AppTokens.Spacing.content,
                                vertical = AppTokens.Spacing.compact
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                        ) {
                            Text(text = pillLabel, style = MaterialTheme.typography.labelMedium)
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.Size.iconSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 现代化特性小标签 (Modern Feature Tag)
@Composable
private fun ModernFeatureTag(
    icon: ImageVector,
    text: String,
    style: AppTokens.FeatureStyle,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        shape = MaterialTheme.shapes.small,
        color = style.container,
        contentColor = style.foreground,
        border = androidx.compose.foundation.BorderStroke(1.dp, style.border)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppTokens.Spacing.control,
                vertical = AppTokens.Spacing.compact
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppTokens.Size.iconSmall)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// 现代化工具按钮 (Modern Tool Button)
@Composable
private fun ModernToolButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(AppTokens.Size.compactControlHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.content),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(AppTokens.Size.iconSmall)
        )
        Spacer(Modifier.width(AppTokens.Spacing.compact))
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

// 使用 Material3 标准开关，保持统一的触控语义和主题颜色。
@Composable
private fun ModernToggleSwitch(
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Switch(
        checked = isChecked,
        onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// 官方模型 Debug 弹窗
@Composable
private fun OfficialCatalogDebugDialog(
    title: String,
    jsonContent: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(AppTokens.Size.debugDialogWidth)
                .heightIn(
                    min = AppTokens.Size.debugDialogMinHeight,
                    max = AppTokens.Size.debugDialogMaxHeight
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.padding(AppTokens.Spacing.card),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.compactControlHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(AppTokens.Size.debugCodePadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = jsonContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Spacer(Modifier.width(AppTokens.Spacing.control))
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.height(AppTokens.Size.controlHeight),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Spacer(Modifier.width(AppTokens.Spacing.compact))
                        Text("复制 JSON", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
