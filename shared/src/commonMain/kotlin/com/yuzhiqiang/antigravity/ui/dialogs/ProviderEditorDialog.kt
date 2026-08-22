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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 4.dp,
    gapLength: Dp = 3.5.dp
) = this.drawWithContent {
    drawContent()
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f
        )
    )
    val halfWidth = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(halfWidth, halfWidth),
        size = Size(size.width - width.toPx(), size.height - width.toPx()),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = stroke
    )
}

enum class ProviderEditStep {
    SELECT_PRESET,
    CONFIG_CONNECTION,
    SELECT_MODELS
}

enum class ModelSelectionFilter {
    ALL,
    SELECTED,
    UNSELECTED
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
    val inputModalities: Set<ModelModality> = emptySet(),
    val outputModalities: Set<ModelModality> = emptySet(),
    val inputMimeTypes: List<String> = emptyList(),
    val roles: Set<ModelRole> = emptySet(),
    val isImageGeneration: Boolean = false,
    val compressionPolicy: ModelCompressionPolicy? = null,
    val reasoningMappings: Map<ReasoningLevel, ReasoningMapping> = emptyMap(),
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
    131_072L to "128K",
    200_000L to "200K",
    262_144L to "256K",
    380_928L to "372K",
    524_288L to "512K",
    1_048_576L to "1M",
    2_097_152L to "2M"
)

private val OUTPUT_TOKEN_LIMIT_OPTIONS = listOf(
    2_048L to "2K",
    4_096L to "4K",
    8_192L to "8K",
    16_384L to "16K",
    32_768L to "32K",
    65_536L to "64K",
    131_072L to "128K"
)

internal fun formatTokenDisplay(limit: Long?): String {
    if (limit == null || limit <= 0L) return "未设置"
    INPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.let { return it.second.substringBefore(" ") }
    OUTPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.let { return it.second.substringBefore(" ") }

    return when {
        limit >= 1_048_576L && limit % 1_048_576L == 0L -> "${limit / 1_048_576L}M"
        limit >= 1_000_000L && limit % 1_000_000L == 0L -> "${limit / 1_000_000L}M"
        limit >= 1_000_000L -> "${((limit / 100_000.0).toInt()) / 10.0}M"
        limit >= 1024L && limit % 1024L == 0L -> "${limit / 1024L}K"
        limit >= 1000L && limit % 1000L == 0L -> "${limit / 1000L}K"
        limit >= 1000L -> "${limit / 1000L}K"
        else -> limit.toString()
    }
}

private fun parseCustomTokenInput(text: String): Long? {
    val clean = text.trim().lowercase().replace(",", "").replace("_", "")
    if (clean.isBlank()) return null
    return try {
        when {
            clean.endsWith("m") -> {
                val num = clean.removeSuffix("m").trim().toDouble()
                (num * 1_048_576L).toLong()
            }
            clean.endsWith("k") -> {
                val num = clean.removeSuffix("k").trim().toDouble()
                (num * 1024L).toLong()
            }
            else -> clean.toLong()
        }
    } catch (e: Exception) {
        null
    }
}

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
                inputModalities = editingSingleModel.capabilities.inputModalities.toSet(),
                outputModalities = editingSingleModel.capabilities.outputModalities.toSet(),
                inputMimeTypes = editingSingleModel.capabilities.inputMimeTypes,
                roles = editingSingleModel.capabilities.roles.toSet(),
                isImageGeneration = ModelRole.IMAGE_GENERATION in editingSingleModel.capabilities.roles &&
                        ModelRole.AGENT !in editingSingleModel.capabilities.roles,
                compressionPolicy = editingSingleModel.compressionPolicy,
                reasoningMappings = ReasoningMappingSupport.parse(editingSingleModel.capabilities.reasoning.levels),
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
                    inputModalities = model.capabilities.inputModalities.toSet(),
                    outputModalities = model.capabilities.outputModalities.toSet(),
                    inputMimeTypes = model.capabilities.inputMimeTypes,
                    roles = model.capabilities.roles.toSet(),
                    isImageGeneration = ModelRole.IMAGE_GENERATION in model.capabilities.roles &&
                            ModelRole.AGENT !in model.capabilities.roles,
                    compressionPolicy = model.compressionPolicy,
                    reasoningMappings = ReasoningMappingSupport.parse(model.capabilities.reasoning.levels),
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

    var modelFilterTab by remember { mutableStateOf(ModelSelectionFilter.ALL) }

    val filteredFetchedModels = remember(fetchedModelConfigs, modelSearchQuery, modelFilterTab, selectedModelIds) {
        fetchedModelConfigs.filter { model ->
            val matchQuery = modelSearchQuery.isBlank() ||
                model.name.contains(modelSearchQuery, ignoreCase = true) ||
                model.id.contains(modelSearchQuery, ignoreCase = true)
            val matchTab = when (modelFilterTab) {
                ModelSelectionFilter.ALL -> true
                ModelSelectionFilter.SELECTED -> model.id in selectedModelIds
                ModelSelectionFilter.UNSELECTED -> model.id !in selectedModelIds
            }
            matchQuery && matchTab
        }
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxHeight = maxHeight * 0.94f
            val dialogMaxWidth = maxWidth * 0.94f
            val actualHeight = minOf(
                if (isSingleModelMode) AppTokens.Size.singleModelDialogHeight else 600.dp,
                dialogMaxHeight
            )
            val actualWidth = minOf(820.dp, dialogMaxWidth)

            Surface(
                modifier = Modifier
                    .width(actualWidth)
                    .height(actualHeight),
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
                        .clipToBounds()
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
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                                                MaterialTheme.colorScheme.surface
                                            } else {
                                                Color.Transparent
                                            }
                                            val textColor = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(background)
                                                    .clickable { presetCategory = cat }
                                                    .padding(
                                                        horizontal = 10.dp,
                                                        vertical = 5.dp
                                                    )
                                            ) {
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }

                                    com.yuzhiqiang.antigravity.ui.components.StudioSearchField(
                                        value = presetSearchQuery,
                                        onValueChange = { presetSearchQuery = it },
                                        placeholder = "搜索服务商名称...",
                                        modifier = Modifier.width(220.dp)
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
                                        val cardBackground = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }

                                        val cardModifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(AppTokens.Radius.small))
                                            .then(
                                                if (!isSelected) {
                                                    Modifier.dashedBorder(
                                                        width = 1.2.dp,
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                                        cornerRadius = AppTokens.Radius.small,
                                                        dashLength = 4.dp,
                                                        gapLength = 3.5.dp
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
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
                                            }

                                        Surface(
                                            modifier = cardModifier,
                                            shape = RoundedCornerShape(AppTokens.Radius.small),
                                            color = cardBackground,
                                            border = if (isSelected) {
                                                androidx.compose.foundation.BorderStroke(
                                                    width = 1.8.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                null
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
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
                                                        style = MaterialTheme.typography.labelMedium.copy(
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
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(tagBackground)
                                                        .padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp
                                                        )
                                                ) {
                                                    Text(
                                                        text = tagText,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.5.sp
                                                        ),
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

                                // 第 1 行：上游服务名称 + API 协议
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
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                                        colors = fieldColors,
                                        singleLine = true
                                    )

                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = selectedProtocolLabel,
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = !isFetching,
                                            label = { Text("API 协议") },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ExpandMore,
                                                    contentDescription = "选择协议",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isFetching) {
                                                    protocolMenuExpanded = true
                                                },
                                            shape = RoundedCornerShape(AppTokens.Radius.medium),
                                            colors = fieldColors,
                                            singleLine = true
                                        )
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
                                }

                                // 第 2 行：API 地址 Base URL + API Key 并排
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
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
                                        modifier = Modifier.weight(1.15f),
                                        shape = RoundedCornerShape(AppTokens.Radius.medium),
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
                                        modifier = Modifier.weight(0.85f),
                                        shape = RoundedCornerShape(AppTokens.Radius.medium),
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
                                }

                                // 协议微说明条
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = when (protocol) {
                                                ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "适用于 /v1/chat/completions；CPA、Sub2API 及主流 OpenAI 兼容网关。"
                                                ProviderProtocol.ANTHROPIC_MESSAGES -> "适用于 Anthropic /v1/messages 协议。"
                                                ProviderProtocol.GEMINI_GENERATE_CONTENT -> "适用于 Google Gemini generateContent 协议。"
                                                ProviderProtocol.OPENAI_RESPONSES -> "适用于 OpenAI Responses API；请求与工具调用使用 input 事件模型。"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 高级端点设置（默认展开，无需折叠）
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppTokens.Radius.medium))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            RoundedCornerShape(AppTokens.Radius.medium)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "高级设置（自定义端点 URL）",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
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
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(AppTokens.Radius.small),
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
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(AppTokens.Radius.small),
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
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (!isSingleModelMode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            com.yuzhiqiang.antigravity.ui.components.StudioSearchField(
                                                value = modelSearchQuery,
                                                onValueChange = { modelSearchQuery = it },
                                                placeholder = "搜索模型名称或 ID...",
                                                modifier = Modifier.width(220.dp)
                                            )

                                            // 分段胶囊筛选 (全部 / 已选 / 未选)
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                    .padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                val totalCount = fetchedModelConfigs.size
                                                val selCount = selectedModelIds.size
                                                val unselCount = totalCount - selCount

                                                listOf(
                                                    ModelSelectionFilter.ALL to "全部 ($totalCount)",
                                                    ModelSelectionFilter.SELECTED to "已选 ($selCount)",
                                                    ModelSelectionFilter.UNSELECTED to "未选 ($unselCount)"
                                                ).forEach { (filter, label) ->
                                                    val isTabActive = modelFilterTab == filter
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                                            .background(
                                                                if (isTabActive) MaterialTheme.colorScheme.surface
                                                                else Color.Transparent
                                                            )
                                                            .clickable { modelFilterTab = filter }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Normal
                                                            ),
                                                            color = if (isTabActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val allVisibleIds = filteredFetchedModels.map { it.id }.toSet()
                                            val isAllSelected =
                                                allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedModelIds }

                                            OutlinedButton(
                                                onClick = {
                                                    selectedModelIds = if (isAllSelected) {
                                                        selectedModelIds - allVisibleIds
                                                    } else {
                                                        selectedModelIds + allVisibleIds
                                                    }
                                                    markDirty()
                                                },
                                                shape = RoundedCornerShape(AppTokens.Radius.small),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Text(
                                                    if (isAllSelected) "取消全选" else "全选 (${filteredFetchedModels.size})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (filteredFetchedModels.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.SearchOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = if (modelSearchQuery.isNotBlank()) "未搜索到匹配「$modelSearchQuery」的模型" else "当前筛选下暂无模型",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
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
                                                    val modalities = updatedConfig.inputModalities
                                                        .toMutableSet()
                                                        .apply {
                                                            add(ModelModality.TEXT)
                                                            if (updatedConfig.isVision) add(ModelModality.IMAGE)
                                                            else remove(ModelModality.IMAGE)
                                                        }
                                                    val mimeTypes = updatedConfig.inputMimeTypes
                                                        .toMutableSet()
                                                        .apply {
                                                            if (updatedConfig.isVision && none { it.startsWith("image/", ignoreCase = true) }) {
                                                                addAll(listOf("image/png", "image/jpeg", "image/webp"))
                                                            }
                                                            if (!updatedConfig.isVision) {
                                                                removeAll { it.startsWith("image/", ignoreCase = true) }
                                                            }
                                                        }
                                                    updateFetchedModelConfig(updatedConfig.id) {
                                                        updatedConfig.copy(
                                                            inputModalities = modalities,
                                                            inputMimeTypes = mimeTypes.toList().sorted()
                                                        )
                                                    }
                                                    markDirty()
                                                },
                                                onTestModel = {
                                                    scope.launch {
                                                        updateFetchedModelConfig(modelConfig.id) {
                                                            it.copy(isTesting = true, testStatusText = null)
                                                        }
                                                        val tempProvider = currentProvider()
                                                        val result = ConnectionTester.testProvider(
                                                            tempProvider,
                                                            modelConfig.id,
                                                            imageOnly = modelConfig.isImageGeneration
                                                        )
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
                }

                // Footer Buttons
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = AppTokens.Spacing.card, vertical = 12.dp),
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
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(AppTokens.Radius.small),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text("← 重新选择预设", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                            OutlinedButton(
                                enabled = !isFetching,
                                onClick = { currentStep = ProviderEditStep.CONFIG_CONNECTION },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(AppTokens.Radius.small),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text("← 修改连接配置", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentStep != ProviderEditStep.SELECT_MODELS && !isSingleModelMode) {
                            TextButton(
                                enabled = !isFetching,
                                onClick = { requestDismiss() },
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(s.commonCancel, style = MaterialTheme.typography.labelMedium)
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
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(AppTokens.Radius.small),
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
                                                val modelIds =
                                                    (models + initialModels.map { it.upstreamModelId }).distinct()
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

                                                    val isVision = existing?.capabilities?.supportsVision
                                                        ?: disc?.supportsVision
                                                        ?: false

                                                    val inputModalities = existing?.capabilities?.inputModalities?.toSet()
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?: disc?.inputModalities.orEmpty()
                                                    val outputModalities = existing?.capabilities?.outputModalities?.toSet()
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?: disc?.outputModalities.orEmpty()
                                                    val inputMimeTypes = existing?.capabilities?.inputMimeTypes
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?: disc?.inputMimeTypes.orEmpty()
                                                    val roles = existing?.capabilities?.roles?.toSet()
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?: disc?.roles.orEmpty()
                                                    val isImageGeneration = existing?.capabilities?.let {
                                                        ModelRole.IMAGE_GENERATION in it.roles && ModelRole.AGENT !in it.roles
                                                    } ?: disc?.isImageGeneration ?: false
                                                    val compressionPolicy = existing?.compressionPolicy
                                                        ?: disc?.compressionPolicy?.takeIf { policy ->
                                                            (inputLimit == null || policy.maxTokenLimit <= inputLimit) &&
                                                                    (outputLimit == null || policy.maxOutputTokens <= outputLimit)
                                                        }

                                                    val isTools = existing?.capabilities?.tools
                                                        ?: disc?.supportsTools
                                                        ?: true

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
                                                            customValue = disc.defaultReasoningLevel,
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
                                        Text("正在获取模型列表...", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Text("获取模型列表 →", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                            Button(
                                enabled = selectedModelIds.isNotEmpty(),
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(AppTokens.Radius.small),
                                onClick = {
                                    val finalProvider = currentProvider()
                                    val finalModels = fetchedModelConfigs
                                        .filter { it.id in selectedModelIds }
                                        .map { config ->
                                            val cleanId = config.id.replace('/', '-').replace(':', '-')
                                            val inputModalities = config.inputModalities
                                                .ifEmpty {
                                                    if (config.isVision) setOf(
                                                        ModelModality.TEXT,
                                                        ModelModality.IMAGE
                                                    ) else setOf(ModelModality.TEXT)
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
                                                roles = if (config.isImageGeneration) {
                                                    listOf(ModelRole.IMAGE_GENERATION)
                                                } else {
                                                    config.roles.ifEmpty { setOf(ModelRole.AGENT) }.toList()
                                                },
                                                inputModalities = if (config.isImageGeneration) {
                                                    listOf(ModelModality.TEXT)
                                                } else {
                                                    inputModalities.toList()
                                                },
                                                outputModalities = if (config.isImageGeneration) {
                                                    listOf(ModelModality.IMAGE)
                                                } else {
                                                    config.outputModalities.ifEmpty { setOf(ModelModality.TEXT) }.toList()
                                                },
                                                tools = if (config.isImageGeneration) false else config.isTools,
                                                inputMimeTypes = if (config.isImageGeneration) emptyList() else config.inputMimeTypes,
                                                reasoning = if (config.isImageGeneration) {
                                                    ReasoningCapability(supported = false)
                                                } else config.reasoningDraft.toCapability(
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
                                                compressionPolicy = if (config.isImageGeneration) {
                                                    null
                                                } else {
                                                    config.compressionPolicy ?: existing?.compressionPolicy
                                                }
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
                                Text(if (isSingleModelMode) "保存模型配置" else "保存上游服务", style = MaterialTheme.typography.labelMedium)
                            }
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

@Composable
private fun CustomTokenInputDialog(
    title: String,
    initialValue: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf(initialValue?.let { formatTokenDisplay(it) }.orEmpty()) }
    val parsedTokens = remember(inputText) { parseCustomTokenInput(inputText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "支持直接输入数字（如 131072），或带单位简写（如 128k、200k、1m、2m）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("例如 128k、1m 或 131072") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = { inputText = "" }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // 实时解析提示
                if (inputText.isNotBlank()) {
                    if (parsedTokens != null && parsedTokens > 0L) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "解析为: ${java.text.NumberFormat.getIntegerInstance().format(parsedTokens)} Tokens",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            "无法识别该格式，请输入有效数值（如 32k、1m、128000）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 快捷选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("快捷填入:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("128k", "200k", "256k", "372k", "512k", "1m", "2m").forEach { quickTag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { inputText = quickTag }
                        ) {
                            Text(
                                quickTag.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = inputText.isBlank() || (parsedTokens != null && parsedTokens > 0L),
                        onClick = {
                            if (inputText.isBlank()) {
                                onConfirm(null)
                            } else {
                                onConfirm(parsedTokens)
                            }
                            onDismiss()
                        }
                    ) {
                        Text(if (inputText.isBlank()) "设为未设置" else "确认设置")
                    }
                }
            }
        }
    }
}

private data class ModelBrandStyle(
    val badge: String,
    val container: Color,
    val contentColor: Color
)

private fun getModelBrandStyle(modelId: String, modelName: String): ModelBrandStyle {
    val lower = "${modelId} ${modelName}".lowercase()
    return when {
        lower.contains("gemini") -> ModelBrandStyle("G", Color(0xFFE8F0FE), Color(0xFF1967D2))
        lower.contains("claude") -> ModelBrandStyle("C", Color(0xFFFCE8E6), Color(0xFFC5221F))
        lower.contains("gpt") || lower.contains("openai") || lower.contains("o1") || lower.contains("o3") || lower.contains("o4") || lower.contains("chatgpt") ->
            ModelBrandStyle("O", Color(0xFFE6F4EA), Color(0xFF137333))
        lower.contains("deepseek") -> ModelBrandStyle("D", Color(0xFFEEF2FF), Color(0xFF4F46E5))
        lower.contains("grok") || lower.contains("xai") -> ModelBrandStyle("X", Color(0xFFF1F3F4), Color(0xFF202124))
        lower.contains("qwen") || lower.contains("tongyi") -> ModelBrandStyle("Q", Color(0xFFF3E8FD), Color(0xFF7E22CE))
        lower.contains("llama") || lower.contains("meta") -> ModelBrandStyle("M", Color(0xFFE0F2FE), Color(0xFF0369A1))
        lower.contains("mistral") || lower.contains("codestral") || lower.contains("pixtral") ->
            ModelBrandStyle("M", Color(0xFFFEF3C7), Color(0xFFD97706))
        lower.contains("moonshot") || lower.contains("kimi") -> ModelBrandStyle("K", Color(0xFFE0F7FA), Color(0xFF00838F))
        lower.contains("glm") || lower.contains("zhipu") || lower.contains("chatglm") -> ModelBrandStyle("Z", Color(0xFFEDE7F6), Color(0xFF5E35B1))
        lower.contains("hunyuan") -> ModelBrandStyle("H", Color(0xFFE8EAF6), Color(0xFF283593))
        lower.contains("doubao") || lower.contains("skylark") -> ModelBrandStyle("B", Color(0xFFE1F5FE), Color(0xFF0277BD))
        else -> ModelBrandStyle(
            (modelName.firstOrNull() ?: 'M').uppercase().toString(),
            Color(0xFFF1F3F4),
            Color(0xFF5F6368)
        )
    }
}

// 步骤 3 中的单模型配置行
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
    var customDialogType by remember { mutableStateOf<String?>(null) } // "input" 或 "output"
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (customDialogType != null) {
        val isInput = customDialogType == "input"
        CustomTokenInputDialog(
            title = if (isInput) "自定义输入 Token 上限 · ${config.name}" else "自定义输出 Token 上限 · ${config.name}",
            initialValue = if (isInput) config.inputTokenLimit else config.outputTokenLimit,
            onConfirm = { newLimit ->
                if (isInput) {
                    onTokenLimitChanged(
                        config.copy(
                            inputTokenLimit = newLimit,
                            inputTokenLimitSource = if (newLimit != null) TokenLimitSource.CONFIGURED else TokenLimitSource.UNKNOWN
                        )
                    )
                } else {
                    onTokenLimitChanged(
                        config.copy(
                            outputTokenLimit = newLimit,
                            outputTokenLimitSource = if (newLimit != null) TokenLimitSource.CONFIGURED else TokenLimitSource.UNKNOWN
                        )
                    )
                }
            },
            onDismiss = { customDialogType = null }
        )
    }

    val brandStyle = remember(config.id, config.name) {
        getModelBrandStyle(config.id, config.name)
    }

    val cardBg by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface
    )

    val cardBorderColor by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.outlineVariant
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(enabled = !isSingleMode) { onToggleCheck() },
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isChecked) 1.5.dp else 1.dp,
            color = cardBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // 第一行：Checkbox + 品牌徽章 + 模型名称/ID + 测试状态/按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isSingleMode) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.2.dp,
                                    color = if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(3.5.dp)
                                )
                                .clickable { onToggleCheck() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    // 品牌微徽章
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(brandStyle.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = brandStyle.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = brandStyle.contentColor
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (config.isUnavailable) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColors.warning.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "目录未探活",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = statusColors.warning
                                    )
                                }
                            }
                        }

                        Text(
                            text = config.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // 单模型测试区域
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (config.isTesting) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "测试中",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (config.testStatusText != null) {
                        val testSuccess = config.isTestSuccess
                        val pillBg = if (testSuccess) statusColors.successContainer else MaterialTheme.colorScheme.errorContainer
                        val pillText = if (testSuccess) statusColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
                        val dotColor = if (testSuccess) statusColors.success else MaterialTheme.colorScheme.error

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(pillBg)
                                .clickable(enabled = isChecked && !config.isUnavailable) { onTestModel() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    config.testStatusText ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    color = pillText
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTestModel,
                            enabled = isChecked && !config.isUnavailable,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChecked && !config.isUnavailable) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("测试", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp))
                        }
                    }
                }
            }

            // 第二行：参数胶囊（输入/输出） + 能力微胶囊（图像/工具/推理）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧 Token 上限选择器
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 输入上限
                    Box {
                        val inputLabel = formatTokenDisplay(config.inputTokenLimit)

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { expandedInputMenu = true }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "输入: $inputLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "▾",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedInputMenu,
                            onDismissRequest = { expandedInputMenu = false }
                        ) {
                            INPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("自定义输入...", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                                    }
                                },
                                onClick = {
                                    expandedInputMenu = false
                                    customDialogType = "input"
                                }
                            )
                            if (config.inputTokenLimit != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            Text("清除 (设为未设置)", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
                                        }
                                    },
                                    onClick = {
                                        expandedInputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                inputTokenLimit = null,
                                                inputTokenLimitSource = TokenLimitSource.UNKNOWN
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // 输出上限
                    Box {
                        val outputLabel = formatTokenDisplay(config.outputTokenLimit)

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { expandedOutputMenu = true }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "输出: $outputLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "▾",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedOutputMenu,
                            onDismissRequest = { expandedOutputMenu = false }
                        ) {
                            OUTPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("自定义输入...", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                                    }
                                },
                                onClick = {
                                    expandedOutputMenu = false
                                    customDialogType = "output"
                                }
                            )
                            if (config.outputTokenLimit != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            Text("清除 (设为未设置)", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
                                        }
                                    },
                                    onClick = {
                                        expandedOutputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                outputTokenLimit = null,
                                                outputTokenLimitSource = TokenLimitSource.UNKNOWN
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // 右侧能力开关（随时可切换调整）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 图像输入
                    val visionActive = config.isVision
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (visionActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (visionActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onVisionChanged(config.copy(isVision = !config.isVision))
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (visionActive) "✓ 多模态" else "多模态",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (visionActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (visionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 工具调用
                    val toolsActive = config.isTools
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (toolsActive) statusColors.successContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (toolsActive) statusColors.success.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onToolsChanged(config.copy(isTools = !config.isTools))
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (toolsActive) "✓ 工具" else "工具",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (toolsActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (toolsActive) statusColors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 配置推理
                    val reasoningActive = config.isReasoning
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (reasoningActive) AppTokens.Feature.reasoning.container
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (reasoningActive) AppTokens.Feature.reasoning.border
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(onClick = onConfigureReasoning)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (reasoningActive) "✓ 推理 (已配置)" else "配置推理",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (reasoningActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (reasoningActive) AppTokens.Feature.reasoning.foreground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
