package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.data.presets.PresetCategory
import com.yuzhiqiang.antigravity.data.presets.PresetProviderTemplate
import com.yuzhiqiang.antigravity.data.presets.ProviderPresets
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch
import java.util.UUID

enum class ProviderEditStep {
    SELECT_PRESET,
    CONFIG_CONNECTION,
    SELECT_MODELS
}

// 供步骤 3 选中的模型配置项
data class CatalogModelConfig(
    val id: String,
    val name: String,
    val inputTokenLimit: Long? = null,
    val inputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val outputTokenLimit: Long? = null,
    val outputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val isVision: Boolean = false,
    val isReasoning: Boolean = false,
    val reasoningDraft: ReasoningConfigDraft = ReasoningConfigDraft(
        enabled = false,
        levels = emptySet(),
        customValue = null,
        thinkingBudget = null,
        minThinkingBudget = null,
        mappings = emptyMap()
    ),
    val isTools: Boolean = true,
    val isUnavailable: Boolean = false,
    val testStatusText: String? = null,
    val isTestSuccess: Boolean = true,
    val isTesting: Boolean = false
)

private val INPUT_TOKEN_LIMIT_OPTIONS = listOf(
    16_384L to "16K",
    32_768L to "32K",
    65_536L to "64K",
    131_072L to "128K",
    200_000L to "200K",
    1_048_576L to "1M",
    2_097_152L to "2M"
)

private val OUTPUT_TOKEN_LIMIT_OPTIONS = listOf(
    4_096L to "4K",
    8_192L to "8K",
    16_384L to "16K",
    32_768L to "32K",
    65_536L to "64K"
)

private fun suggestedEndpoints(baseUrl: String, protocol: ProviderProtocol): Pair<String, String> {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isBlank()) return "" to ""

    return if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
        val apiBase = if (base.endsWith("/v1beta")) base else "$base/v1beta"
        "$apiBase/models" to "$apiBase/models/{model}:generateContent"
    } else {
        val apiBase = if (base.endsWith("/v1")) base else "$base/v1"
        val generatePath = when (protocol) {
            ProviderProtocol.ANTHROPIC_MESSAGES -> "/messages"
            ProviderProtocol.OPENAI_RESPONSES -> "/responses"
            else -> "/chat/completions"
        }
        "$apiBase/models" to "$apiBase$generatePath"
    }
}

private fun detectPresetId(baseUrl: String): String? {
    val host = baseUrl.trim()
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .lowercase()
    if (host.isBlank()) return null
    return ProviderPresets.allPresets.firstOrNull { preset ->
        val presetHost = preset.defaultBaseUrl
            .substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .lowercase()
        presetHost.isNotBlank() && presetHost == host
    }?.id
}

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
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

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

    var providerId by remember { mutableStateOf(initialProvider?.id ?: UUID.randomUUID().toString().take(8)) }
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var protocol by remember { mutableStateOf(initialProvider?.protocol ?: ProviderProtocol.OPENAI_CHAT_COMPLETIONS) }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var showApiKey by remember { mutableStateOf(false) }

    var modelsEndpoint by remember { mutableStateOf(initialProvider?.modelsEndpoint ?: "") }
    var generateEndpoint by remember { mutableStateOf(initialProvider?.generateEndpoint ?: "") }
    var showAdvancedEndpoints by remember { mutableStateOf(false) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }

    // 步骤 3 模型挑选状态
    var fetchedModelConfigs by remember { mutableStateOf<List<CatalogModelConfig>>(emptyList()) }
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var modelSearchQuery by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var reasoningModelId by remember { mutableStateOf<String?>(null) }

    fun markDirty() {
        isDirty = true
    }

    fun updateSuggestedEndpoints() {
        val (suggestedModelsEndpoint, suggestedGenerateEndpoint) = suggestedEndpoints(baseUrl, protocol)
        modelsEndpoint = suggestedModelsEndpoint
        generateEndpoint = suggestedGenerateEndpoint
    }

    fun updateFetchedModelConfig(
        modelId: String,
        transform: (CatalogModelConfig) -> CatalogModelConfig
    ) {
        fetchedModelConfigs = fetchedModelConfigs.map { config ->
            if (config.id == modelId) transform(config) else config
        }
    }

    fun resetCatalogResults() {
        fetchedModelConfigs = emptyList()
        selectedModelIds = emptySet()
        modelSearchQuery = ""
        fetchError = null
        if (currentStep == ProviderEditStep.SELECT_MODELS) {
            currentStep = ProviderEditStep.CONFIG_CONNECTION
        }
    }

    fun requestDismiss() {
        if (isFetching) {
            fetchError = "正在获取模型列表，请稍候再关闭编辑器"
            return
        }
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    fun currentProvider(): Provider {
        val updatedApiKey = apiKey.ifBlank { null }
        return (initialProvider ?: Provider(id = providerId, name = name, protocol = protocol, baseUrl = baseUrl)).copy(
            id = providerId,
            name = name,
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = updatedApiKey,
            modelsEndpoint = modelsEndpoint.ifBlank { null },
            generateEndpoint = generateEndpoint.ifBlank { null }
        )
    }

    // 初始化单模型编辑或初次进入
    LaunchedEffect(editingSingleModel) {
        if (editingSingleModel != null) {
            val reasoningDraft = ReasoningConfigDraft.fromCapabilities(editingSingleModel.capabilities)
            val singleConfig = CatalogModelConfig(
                id = editingSingleModel.upstreamModelId,
                name = editingSingleModel.displayName ?: editingSingleModel.upstreamModelId,
                inputTokenLimit = editingSingleModel.tokenLimits.contextWindow
                    ?: editingSingleModel.tokenLimits.inputTokenLimit,
                inputTokenLimitSource = if (editingSingleModel.tokenLimits.contextWindow != null) {
                    editingSingleModel.tokenLimits.contextWindowSource
                } else {
                    editingSingleModel.tokenLimits.inputTokenLimitSource
                },
                outputTokenLimit = editingSingleModel.tokenLimits.outputTokenLimit,
                outputTokenLimitSource = editingSingleModel.tokenLimits.outputTokenLimitSource,
                isVision = editingSingleModel.capabilities.supportsVision,
                isReasoning = reasoningDraft.enabled,
                reasoningDraft = reasoningDraft,
                isTools = editingSingleModel.capabilities.tools
            )
            fetchedModelConfigs = listOf(singleConfig)
            selectedModelIds = setOf(singleConfig.id)
        } else if (initialModels.isNotEmpty()) {
            val configs = initialModels.map { model ->
                val reasoningDraft = ReasoningConfigDraft.fromCapabilities(model.capabilities)
                CatalogModelConfig(
                    id = model.upstreamModelId,
                    name = model.displayName ?: model.upstreamModelId,
                    inputTokenLimit = model.tokenLimits.contextWindow ?: model.tokenLimits.inputTokenLimit,
                    inputTokenLimitSource = if (model.tokenLimits.contextWindow != null) {
                        model.tokenLimits.contextWindowSource
                    } else {
                        model.tokenLimits.inputTokenLimitSource
                    },
                    outputTokenLimit = model.tokenLimits.outputTokenLimit,
                    outputTokenLimitSource = model.tokenLimits.outputTokenLimitSource,
                    isVision = model.capabilities.supportsVision,
                    isReasoning = reasoningDraft.enabled,
                    reasoningDraft = reasoningDraft,
                    isTools = model.capabilities.tools
                )
            }
            fetchedModelConfigs = configs
            selectedModelIds = configs.map { it.id }.toSet()
        }
    }

    var presetCategory by remember { mutableStateOf(PresetCategory.ALL) }
    var presetSearchQuery by remember { mutableStateOf("") }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    val filteredPresets = remember(presetCategory, presetSearchQuery) {
        ProviderPresets.allPresets.filter { preset ->
            val matchCat = presetCategory == PresetCategory.ALL || preset.category == presetCategory
            val matchQuery = presetSearchQuery.isBlank() || preset.name.contains(
                presetSearchQuery,
                ignoreCase = true
            ) || preset.description.contains(presetSearchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val filteredFetchedModels = remember(fetchedModelConfigs, modelSearchQuery) {
        if (modelSearchQuery.isBlank()) fetchedModelConfigs
        else fetchedModelConfigs.filter {
            it.name.contains(modelSearchQuery, ignoreCase = true) || it.id.contains(
                modelSearchQuery,
                ignoreCase = true
            )
        }
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(AppTokens.Size.dialogWidth)
                .height(
                    if (isSingleModelMode) {
                        AppTokens.Size.singleModelDialogHeight
                    } else {
                        AppTokens.Size.dialogHeight
                    }
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header：左侧标题、中间步骤、右侧关闭按钮，对齐旧版三列结构。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppTokens.Size.fieldHeight)
                        .padding(
                            horizontal = AppTokens.Spacing.card,
                            vertical = AppTokens.Spacing.content
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                            ) {
                                Text(
                                    text = when {
                                        isSingleModelMode -> editingSingleModel?.let { model ->
                                            "编辑模型 · ${model.displayName ?: model.upstreamModelId}"
                                        }.orEmpty()

                                        initialProvider != null -> "编辑上游服务 · ${initialProvider.name}"
                                        else -> "添加上游服务"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (isDirty) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(statusColors.warningContainer)
                                            .padding(
                                                horizontal = AppTokens.Spacing.control,
                                                vertical = AppTokens.Spacing.compact
                                            )
                                    ) {
                                        Text(
                                            "未保存",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = statusColors.onWarningContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isSingleModelMode) "调整模型上下文限制与能力配置" else "配置服务商连接并挑选要接入的模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isSingleModelMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                        ) {
                            listOf(
                                Triple(ProviderEditStep.SELECT_PRESET, 1, "选择预设"),
                                Triple(ProviderEditStep.CONFIG_CONNECTION, 2, "连接配置"),
                                Triple(ProviderEditStep.SELECT_MODELS, 3, "选择模型")
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
                                        .clip(MaterialTheme.shapes.small)
                                        .padding(
                                            horizontal = AppTokens.Spacing.control,
                                            vertical = AppTokens.Spacing.compact
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(AppTokens.Size.iconMedium)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            num.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isActive) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            }
                                        ),
                                        color = when {
                                            isActive -> MaterialTheme.colorScheme.primary
                                            canNavigate -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                }
                                if (num < 3) {
                                    Box(
                                        modifier = Modifier
                                            .width(AppTokens.Spacing.content)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
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
                            modifier = Modifier.size(AppTokens.Size.compactControlHeight)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AppTokens.Size.iconMedium)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Step Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppTokens.Spacing.card,
                            vertical = AppTokens.Spacing.content
                        )
                ) {
                    when (currentStep) {
                        ProviderEditStep.SELECT_PRESET -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Category Tabs + Search
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(AppTokens.Spacing.compact),
                                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                                    ) {
                                        listOf(
                                            PresetCategory.ALL to "全部",
                                            PresetCategory.AGGREGATOR to "聚合网关",
                                            PresetCategory.RECOMMENDED to "常用推荐",
                                            PresetCategory.OFFICIAL to "官方厂商",
                                            PresetCategory.LOCAL_CUSTOM to "本地/自定义"
                                        ).forEach { (cat, label) ->
                                            val selected = presetCategory == cat
                                            val background = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            val textColor = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(background)
                                                    .clickable { presetCategory = cat }
                                                    .padding(
                                                        horizontal = AppTokens.Spacing.content,
                                                        vertical = AppTokens.Spacing.compact
                                                    )
                                            ) {
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (selected) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Medium
                                                        }
                                                    ),
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = presetSearchQuery,
                                        onValueChange = { presetSearchQuery = it },
                                        placeholder = {
                                            Text(
                                                "搜索服务名称...",
                                                style = MaterialTheme.typography.labelSmall
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
                                        colors = fieldColors,
                                        singleLine = true
                                    )
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = AppTokens.Size.presetGridMinWidth),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    gridItems(filteredPresets, key = { it.id }) { preset ->
                                        val isSelected = selectedPresetId == preset.id
                                        val isCustom = preset.category == PresetCategory.LOCAL_CUSTOM
                                        val tagText = when (preset.category) {
                                            PresetCategory.AGGREGATOR -> "聚合"
                                            PresetCategory.RECOMMENDED -> "推荐"
                                            PresetCategory.OFFICIAL -> "官方"
                                            PresetCategory.LOCAL_CUSTOM -> "本地"
                                            PresetCategory.ALL -> "自定义"
                                        }
                                        val tagBackground = when (preset.category) {
                                            PresetCategory.OFFICIAL -> MaterialTheme.colorScheme.primaryContainer
                                            PresetCategory.LOCAL_CUSTOM -> statusColors.successContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val tagColor = when (preset.category) {
                                            PresetCategory.OFFICIAL -> MaterialTheme.colorScheme.primary
                                            PresetCategory.LOCAL_CUSTOM -> statusColors.success
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val cardBackground = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isCustom -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                        val cardBorder = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isCustom -> statusColors.success.copy(alpha = 0.6f)
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppTokens.Size.controlHeight)
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable(enabled = !isFetching && initialProvider == null) {
                                                    selectedPresetId = preset.id
                                                    if (preset.id == "custom_openai") {
                                                        name = ""
                                                        protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
                                                        baseUrl = ""
                                                        updateSuggestedEndpoints()
                                                        isDirty = false
                                                        selectedPresetId = null
                                                    } else {
                                                        name = preset.name
                                                        protocol = preset.protocol
                                                        baseUrl = preset.defaultBaseUrl
                                                        updateSuggestedEndpoints()
                                                        markDirty()
                                                    }
                                                    resetCatalogResults()
                                                    currentStep = ProviderEditStep.CONFIG_CONNECTION
                                                },
                                            shape = MaterialTheme.shapes.small,
                                            color = cardBackground,
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = cardBorder
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(AppTokens.Size.iconLarge)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(
                                                            if (isCustom) {
                                                                statusColors.successContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.primaryContainer
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = preset.name.trim().firstOrNull()?.uppercase() ?: "?",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = if (isCustom) {
                                                            statusColors.success
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        }
                                                    )
                                                }
                                                Text(
                                                    text = preset.name,
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(tagBackground)
                                                        .padding(
                                                            horizontal = AppTokens.Spacing.control,
                                                            vertical = AppTokens.Spacing.compact
                                                        )
                                                ) {
                                                    Text(
                                                        text = tagText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = tagColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ProviderEditStep.CONFIG_CONNECTION -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val protocolOptions = listOf(
                                    ProviderProtocol.OPENAI_CHAT_COMPLETIONS to "OpenAI · Chat Completions",
                                    ProviderProtocol.OPENAI_RESPONSES to "OpenAI · Responses",
                                    ProviderProtocol.ANTHROPIC_MESSAGES to "Anthropic · Messages API",
                                    ProviderProtocol.GEMINI_GENERATE_CONTENT to "Google · Gemini generateContent"
                                )
                                val selectedProtocolLabel =
                                    protocolOptions.firstOrNull { it.first == protocol }?.second.orEmpty()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = {
                                            name = it
                                            markDirty()
                                        },
                                        enabled = !isFetching,
                                        label = { Text("上游服务名称") },
                                        placeholder = { Text("例如 CPA、公司代理、DeepSeek 官方") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(AppTokens.Size.fieldHeight),
                                        shape = MaterialTheme.shapes.small,
                                        colors = fieldColors,
                                        singleLine = true
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "API 协议",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(AppTokens.Size.fieldHeight)
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        MaterialTheme.shapes.small
                                                    )
                                                    .clickable(enabled = !isFetching) {
                                                        protocolMenuExpanded = true
                                                    }
                                                    .padding(horizontal = AppTokens.Spacing.section),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = selectedProtocolLabel,
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.Outlined.ExpandMore,
                                                    contentDescription = "选择协议",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = protocolMenuExpanded,
                                                onDismissRequest = { protocolMenuExpanded = false },
                                                modifier = Modifier.widthIn(min = 300.dp, max = 380.dp)
                                            ) {
                                                protocolOptions.forEach { (candidateProtocol, label) ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                label,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        },
                                                        onClick = {
                                                            protocolMenuExpanded = false
                                                            protocol = candidateProtocol
                                                            updateSuggestedEndpoints()
                                                            selectedPresetId = detectPresetId(baseUrl)
                                                            resetCatalogResults()
                                                            markDirty()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = when (protocol) {
                                                ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "适用于 /v1/chat/completions；CPA、Sub2API 及主流 OpenAI 兼容网关。"
                                                ProviderProtocol.ANTHROPIC_MESSAGES -> "适用于 Anthropic /v1/messages 协议。"
                                                ProviderProtocol.GEMINI_GENERATE_CONTENT -> "适用于 Google Gemini generateContent 协议。"
                                                ProviderProtocol.OPENAI_RESPONSES -> "适用于 OpenAI Responses API；请求与工具调用使用 input 事件模型。"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = {
                                        baseUrl = it
                                        updateSuggestedEndpoints()
                                        selectedPresetId = detectPresetId(it)
                                        resetCatalogResults()
                                        markDirty()
                                    },
                                    enabled = !isFetching,
                                    label = { Text("API 地址 (Base URL)") },
                                    placeholder = { Text("例如 https://api.openai.com/v1") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppTokens.Size.fieldHeight),
                                    shape = MaterialTheme.shapes.small,
                                    colors = fieldColors,
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = {
                                        apiKey = it
                                        resetCatalogResults()
                                        markDirty()
                                    },
                                    enabled = !isFetching,
                                    label = { Text("API Key (选填)") },
                                    placeholder = { Text("输入 API Key（无鉴权则留空）") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppTokens.Size.fieldHeight),
                                    shape = MaterialTheme.shapes.small,
                                    colors = fieldColors,
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(
                                            enabled = !isFetching,
                                            onClick = { showApiKey = !showApiKey }
                                        ) {
                                            Icon(
                                                imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                )

                                // 高级设置折叠面板
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            MaterialTheme.shapes.small
                                        )
                                        .padding(AppTokens.Spacing.content),
                                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isFetching) {
                                                showAdvancedEndpoints = !showAdvancedEndpoints
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "高级设置（自定义端点 URL）",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = if (showAdvancedEndpoints) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (showAdvancedEndpoints) {
                                        OutlinedTextField(
                                            value = modelsEndpoint,
                                            onValueChange = {
                                                modelsEndpoint = it
                                                resetCatalogResults()
                                                markDirty()
                                            },
                                            enabled = !isFetching,
                                            label = { Text("模型列表接口 (自定义)") },
                                            placeholder = { Text("留空自动推断") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppTokens.Size.fieldHeight),
                                            shape = MaterialTheme.shapes.small,
                                            colors = fieldColors,
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = generateEndpoint,
                                            onValueChange = {
                                                generateEndpoint = it
                                                resetCatalogResults()
                                                markDirty()
                                            },
                                            enabled = !isFetching,
                                            label = { Text("生成响应接口 (自定义)") },
                                            placeholder = { Text("留空自动推断") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppTokens.Size.fieldHeight),
                                            shape = MaterialTheme.shapes.small,
                                            colors = fieldColors,
                                            singleLine = true
                                        )
                                    }
                                }

                                if (fetchError != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            fetchError.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        ProviderEditStep.SELECT_MODELS -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (!isSingleModelMode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = modelSearchQuery,
                                            onValueChange = { modelSearchQuery = it },
                                            placeholder = {
                                                Text(
                                                    "搜索模型名称或 ID...",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier
                                                .width(AppTokens.Size.modelSearchFieldWidth)
                                                .height(AppTokens.Size.controlHeight),
                                            shape = MaterialTheme.shapes.small,
                                            colors = fieldColors,
                                            singleLine = true
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val allVisibleIds = filteredFetchedModels.map { it.id }.toSet()
                                            val isAllSelected =
                                                allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedModelIds }

                                            TextButton(
                                                onClick = {
                                                    selectedModelIds = if (isAllSelected) {
                                                        selectedModelIds - allVisibleIds
                                                    } else {
                                                        selectedModelIds + allVisibleIds
                                                    }
                                                    markDirty()
                                                }
                                            ) {
                                                Text(
                                                    if (isAllSelected) "取消全选" else "选择当前结果",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(filteredFetchedModels, key = { it.id }) { modelConfig ->
                                        val isChecked = selectedModelIds.contains(modelConfig.id)
                                        CatalogModelRowCard(
                                            config = modelConfig,
                                            isChecked = isChecked,
                                            isSingleMode = isSingleModelMode,
                                            onToggleCheck = {
                                                selectedModelIds =
                                                    if (isChecked) selectedModelIds - modelConfig.id else selectedModelIds + modelConfig.id
                                                markDirty()
                                            },
                                            onConfigureReasoning = {
                                                reasoningModelId = modelConfig.id
                                            },
                                            onTokenLimitChanged = { updatedConfig ->
                                                updateFetchedModelConfig(updatedConfig.id) { updatedConfig }
                                                markDirty()
                                            },
                                            onToolsChanged = { updatedConfig ->
                                                updateFetchedModelConfig(updatedConfig.id) { updatedConfig }
                                                markDirty()
                                            },
                                            onVisionChanged = { updatedConfig ->
                                                updateFetchedModelConfig(updatedConfig.id) { updatedConfig }
                                                markDirty()
                                            },
                                            onTestModel = {
                                                scope.launch {
                                                    updateFetchedModelConfig(modelConfig.id) {
                                                        it.copy(isTesting = true, testStatusText = null)
                                                    }
                                                    val tempProvider = currentProvider()
                                                    val result =
                                                        ConnectionTester.testProvider(tempProvider, modelConfig.id)
                                                    updateFetchedModelConfig(modelConfig.id) {
                                                        it.copy(
                                                            isTesting = false,
                                                            isTestSuccess = result.success,
                                                            testStatusText = if (result.success) "${result.latencyMs}ms" else "失败"
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Buttons
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppTokens.Size.fieldHeight)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = AppTokens.Spacing.card),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                    ) {
                        if (isSingleModelMode) {
                            TextButton(
                                enabled = !isFetching,
                                onClick = { requestDismiss() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(s.commonCancel)
                            }
                        } else if (currentStep == ProviderEditStep.CONFIG_CONNECTION && initialProvider == null) {
                            OutlinedButton(
                                enabled = !isFetching,
                                onClick = { currentStep = ProviderEditStep.SELECT_PRESET },
                                modifier = Modifier.height(AppTokens.Size.controlHeight),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text("← 重新选择预设")
                            }
                        } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                            OutlinedButton(
                                enabled = !isFetching,
                                onClick = { currentStep = ProviderEditStep.CONFIG_CONNECTION },
                                modifier = Modifier.height(AppTokens.Size.controlHeight),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text("← 返回修改配置")
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                    ) {
                        if (currentStep != ProviderEditStep.SELECT_MODELS && !isSingleModelMode) {
                            TextButton(
                                enabled = !isFetching,
                                onClick = { requestDismiss() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(s.commonCancel)
                            }
                        }
                        if (currentStep == ProviderEditStep.SELECT_MODELS && !isSingleModelMode) {
                            Text(
                                text = if (selectedModelIds.isNotEmpty()) "已选择 ${selectedModelIds.size} 个模型" else "未选择任何模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentStep == ProviderEditStep.CONFIG_CONNECTION) {
                            Button(
                                enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isFetching,
                                onClick = {
                                    scope.launch {
                                        isFetching = true
                                        fetchError = null
                                        try {
                                            val tempProvider = currentProvider()
                                            val adapter = AdapterFactory.getAdapter(protocol)
                                            val models = adapter.fetchModels(tempProvider)
                                            if (models.isNotEmpty() || initialModels.isNotEmpty()) {
                                                val existingMap = initialModels.associateBy { it.upstreamModelId }
                                                val modelIds =
                                                    (models + initialModels.map { it.upstreamModelId }).distinct()
                                                val configs = modelIds.map { mName ->
                                                    val existing = existingMap[mName]
                                                    val isUnavailable = mName !in models
                                                    val lower = mName.lowercase()
                                                    val isVision = existing?.capabilities?.supportsVision
                                                        ?: (lower.contains("vision") || lower.contains("vl") || lower.contains(
                                                            "4o"
                                                        ) || lower.contains("gemini") || lower.contains("claude"))
                                                    val reasoningDraft = existing?.let { model ->
                                                        ReasoningConfigDraft.fromCapabilities(model.capabilities)
                                                    } ?: ReasoningConfigDraft(
                                                        enabled = false,
                                                        levels = emptySet(),
                                                        customValue = null,
                                                        thinkingBudget = null,
                                                        minThinkingBudget = null,
                                                        mappings = emptyMap()
                                                    )
                                                    CatalogModelConfig(
                                                        id = mName,
                                                        name = existing?.displayName ?: mName,
                                                        inputTokenLimit = existing?.tokenLimits?.contextWindow
                                                            ?: existing?.tokenLimits?.inputTokenLimit,
                                                        inputTokenLimitSource = if (existing?.tokenLimits?.contextWindow != null) {
                                                            existing.tokenLimits.contextWindowSource
                                                        } else {
                                                            existing?.tokenLimits?.inputTokenLimitSource
                                                                ?: TokenLimitSource.UNKNOWN
                                                        },
                                                        outputTokenLimit = existing?.tokenLimits?.outputTokenLimit,
                                                        outputTokenLimitSource = existing?.tokenLimits?.outputTokenLimitSource
                                                            ?: TokenLimitSource.UNKNOWN,
                                                        isVision = isVision,
                                                        isReasoning = reasoningDraft.enabled,
                                                        reasoningDraft = reasoningDraft,
                                                        isTools = existing?.capabilities?.tools ?: true,
                                                        isUnavailable = isUnavailable
                                                    )
                                                }
                                                fetchedModelConfigs = configs
                                                selectedModelIds = initialModels.map { it.upstreamModelId }.toSet()
                                                currentStep = ProviderEditStep.SELECT_MODELS
                                            } else {
                                                fetchError = "拉取模型列表失败，请检查 Base URL 与 API Key 是否有效"
                                            }
                                        } catch (error: kotlinx.coroutines.CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            fetchError = "拉取模型列表失败：${error.message ?: "未知错误"}"
                                        } finally {
                                            isFetching = false
                                        }
                                    }
                                },
                                modifier = Modifier.height(AppTokens.Size.controlHeight),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            ) {
                                Text(if (isFetching) "正在获取..." else "获取模型列表 →")
                            }
                        } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                            Button(
                                enabled = selectedModelIds.isNotEmpty(),
                                onClick = {
                                    val finalProvider = currentProvider()
                                    val finalModels = fetchedModelConfigs
                                        .filter { it.id in selectedModelIds }
                                        .map { config ->
                                            val cleanId = config.id.replace('/', '-').replace(':', '-')
                                            val inputModalities = if (config.isVision) listOf(
                                                ModelModality.TEXT,
                                                ModelModality.IMAGE
                                            ) else listOf(ModelModality.TEXT)

                                            val existing = initialModels.firstOrNull { model ->
                                                model.upstreamModelId == config.id
                                            }
                                            val modelId = existing?.id ?: "${providerId}-$cleanId"
                                            val tokenLimits = ModelTokenLimits(
                                                contextWindow = config.inputTokenLimit,
                                                contextWindowSource = config.inputTokenLimitSource,
                                                inputTokenLimit = config.inputTokenLimit,
                                                inputTokenLimitSource = config.inputTokenLimitSource,
                                                outputTokenLimit = config.outputTokenLimit,
                                                outputTokenLimitSource = config.outputTokenLimitSource
                                            )
                                            val capabilities = ModelCapabilities(
                                                inputModalities = inputModalities,
                                                tools = config.isTools,
                                                reasoning = config.reasoningDraft.toCapability(
                                                    protocol = protocol,
                                                    outputTokenLimit = config.outputTokenLimit
                                                ),
                                                vision = config.isVision
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
                                                capabilities = capabilities
                                            )
                                        }
                                    onSave(finalProvider, finalModels)
                                },
                                modifier = Modifier.height(AppTokens.Size.controlHeight),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            ) {
                                Text(if (isSingleModelMode) "保存模型配置" else "保存上游服务")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            title = "放弃未保存修改",
            message = "当前编辑器存在未保存的 Provider 或模型修改，确定要放弃吗？",
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

// 步骤 3 中的单模型配置行 (对齐 catalogModelRow & TokenLimitsControl)
@Composable
private fun CatalogModelRowCard(
    config: CatalogModelConfig,
    isChecked: Boolean,
    isSingleMode: Boolean,
    onToggleCheck: () -> Unit,
    onConfigureReasoning: () -> Unit,
    onTokenLimitChanged: (CatalogModelConfig) -> Unit,
    onToolsChanged: (CatalogModelConfig) -> Unit,
    onVisionChanged: (CatalogModelConfig) -> Unit,
    onTestModel: () -> Unit
) {
    val statusColors = AppStatusColors
    var expandedInputMenu by remember { mutableStateOf(false) }
    var expandedOutputMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isChecked) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .border(
                1.dp,
                if (isChecked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                MaterialTheme.shapes.medium
            )
            .padding(AppTokens.Spacing.content),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
    ) {
        // 第一行：Checkbox + 模型名称 + 单模型测试按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (!isSingleMode) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleCheck() }
                    )
                }
                Column {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
                    ) {
                        Text(
                            text = config.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (config.isUnavailable) {
                            Text(
                                text = "当前目录缺失",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColors.warning
                            )
                        }
                    }
                }
            }

            // 单模型独立测试区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)
            ) {
                if (config.testStatusText != null) {
                    val summaryBackground = if (config.isTestSuccess) {
                        statusColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                    val summaryTextColor = if (config.isTestSuccess) {
                        statusColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(summaryBackground)
                            .padding(
                                horizontal = AppTokens.Spacing.control,
                                vertical = AppTokens.Spacing.compact
                            )
                    ) {
                        config.testStatusText?.let { statusText ->
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = summaryTextColor
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onTestModel,
                    enabled = isChecked && !config.isUnavailable && !config.isTesting,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(
                        horizontal = AppTokens.Spacing.content,
                        vertical = AppTokens.Spacing.compact
                    ),
                    modifier = Modifier.height(AppTokens.Size.compactControlHeight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isChecked && !config.isUnavailable && !config.isTesting) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Text(
                        if (config.isTesting) "测试中..." else "测试",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // 第二行：Token 控制与能力标签 (对齐 .catalog-token-badge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token 控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "输入上限:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    val inputLabel = config.inputTokenLimit?.let { limit ->
                        INPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.second ?: "${limit / 1024}K"
                    } ?: "未设置"

                    Box(
                        modifier = Modifier
                            .height(AppTokens.Size.compactControlHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isChecked) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .border(
                                1.dp,
                                if (isChecked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                MaterialTheme.shapes.small
                            )
                            .clickable(enabled = isChecked) { expandedInputMenu = true }
                            .padding(horizontal = AppTokens.Spacing.control)
                    ) {
                        Text(
                            inputLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    DropdownMenu(
                        expanded = expandedInputMenu,
                        onDismissRequest = { expandedInputMenu = false }
                    ) {
                        INPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                onClick = {
                                    expandedInputMenu = false
                                    onTokenLimitChanged(
                                        config.copy(
                                            inputTokenLimit = valLimit,
                                            inputTokenLimitSource = TokenLimitSource.CONFIGURED
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "输出上限:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    val outputLabel = config.outputTokenLimit?.let { limit ->
                        OUTPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.second ?: "${limit / 1024}K"
                    } ?: "未设置"

                    Box(
                        modifier = Modifier
                            .height(AppTokens.Size.compactControlHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isChecked) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .border(
                                1.dp,
                                if (isChecked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                MaterialTheme.shapes.small
                            )
                            .clickable(enabled = isChecked) { expandedOutputMenu = true }
                            .padding(horizontal = AppTokens.Spacing.control)
                    ) {
                        Text(
                            outputLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    DropdownMenu(
                        expanded = expandedOutputMenu,
                        onDismissRequest = { expandedOutputMenu = false }
                    ) {
                        OUTPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                onClick = {
                                    expandedOutputMenu = false
                                    onTokenLimitChanged(
                                        config.copy(
                                            outputTokenLimit = valLimit,
                                            outputTokenLimitSource = TokenLimitSource.CONFIGURED
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 能力开关小徽章
            Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.compact)) {
                val visionStyle = if (config.isVision) {
                    AppTokens.Feature.vision
                } else {
                    AppTokens.FeatureStyle(
                        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        border = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(visionStyle.container)
                        .border(1.dp, visionStyle.border, MaterialTheme.shapes.small)
                        .clickable(enabled = isChecked) {
                            onVisionChanged(config.copy(isVision = !config.isVision))
                        }
                        .padding(
                            horizontal = AppTokens.Spacing.control,
                            vertical = AppTokens.Spacing.compact
                        )
                ) {
                    Text(
                        text = if (config.isVision) "✓ 图像输入" else "图像输入",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (config.isVision) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                        ),
                        color = visionStyle.foreground
                    )
                }

                val toolsStyle = if (config.isTools) {
                    AppTokens.Feature.tools
                } else {
                    AppTokens.FeatureStyle(
                        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        border = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(toolsStyle.container)
                        .border(1.dp, toolsStyle.border, MaterialTheme.shapes.small)
                        .clickable(enabled = isChecked) {
                            onToolsChanged(config.copy(isTools = !config.isTools))
                        }
                        .padding(
                            horizontal = AppTokens.Spacing.control,
                            vertical = AppTokens.Spacing.compact
                        )
                ) {
                    Text(
                        text = if (config.isTools) "✓ 工具调用" else "工具调用",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (config.isTools) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                        ),
                        color = toolsStyle.foreground
                    )
                }

                if (config.isReasoning) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(AppTokens.Feature.reasoning.container)
                            .clickable(enabled = isChecked, onClick = onConfigureReasoning)
                            .padding(
                                horizontal = AppTokens.Spacing.control,
                                vertical = AppTokens.Spacing.compact
                            )
                    ) {
                        Text(
                            "配置推理",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = AppTokens.Feature.reasoning.foreground
                        )
                    }
                } else {
                    TextButton(
                        onClick = onConfigureReasoning,
                        enabled = isChecked,
                        contentPadding = PaddingValues(
                            horizontal = AppTokens.Spacing.compact,
                            vertical = 0.dp
                        ),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AppTokens.Feature.reasoning.foreground,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            "配置推理",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
