package com.yuzhiqiang.antigravity.ui.dialogs.provider

import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo
import com.yuzhiqiang.antigravity.ui.dialogs.ReasoningConfigDraft

/**
 * Provider 模型目录与配置项的双向转换与合并映射器
 */
internal object ProviderModelConfigMapper {

    fun toCatalogModelConfig(model: UpstreamModel): CatalogModelConfig {
        val isImageGen =
            ModelRole.IMAGE_GENERATION in model.capabilities.roles && ModelRole.AGENT !in model.capabilities.roles
        return CatalogModelConfig(
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
            isImageGeneration = isImageGen,
            compressionPolicy = model.compressionPolicy,
            reasoningMappings = ReasoningMappingSupport.parse(model.capabilities.reasoning.levels),
            isReasoning = model.capabilities.reasoning.supportsReasoning,
            reasoningDraft = ReasoningConfigDraft.fromCapabilities(model.capabilities),
            isTools = model.capabilities.tools
        )
    }

    fun createManualCatalogConfigs(initialModels: List<UpstreamModel>): List<CatalogModelConfig> {
        val existingMap = initialModels.associateBy { it.upstreamModelId }
        val modelIds = initialModels.map { it.upstreamModelId }.distinct()

        return modelIds.map { mName ->
            val existing = existingMap[mName]
            val inputLimit = existing?.tokenLimits?.contextWindow
                ?: existing?.tokenLimits?.inputTokenLimit
                ?: 131_072L
            val outputLimit = existing?.tokenLimits?.outputTokenLimit ?: 4_096L
            val isVision = existing?.capabilities?.supportsVision ?: true
            val isTools = existing?.capabilities?.tools ?: true
            val reasoningDraft = existing?.let { ReasoningConfigDraft.fromCapabilities(it.capabilities) }
                ?: ReasoningConfigDraft(
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
                vendor = null,
                inputTokenLimit = inputLimit,
                inputTokenLimitSource = if (existing != null) existing.tokenLimits.contextWindowSource else TokenLimitSource.CONFIGURED,
                outputTokenLimit = outputLimit,
                outputTokenLimitSource = if (existing != null) existing.tokenLimits.outputTokenLimitSource else TokenLimitSource.CONFIGURED,
                isVision = isVision,
                inputModalities = setOf(ModelModality.TEXT).let { if (isVision) it + ModelModality.IMAGE else it },
                outputModalities = setOf(ModelModality.TEXT),
                inputMimeTypes = if (isVision) listOf("image/png", "image/jpeg", "image/webp") else emptyList(),
                roles = emptySet(),
                isImageGeneration = false,
                compressionPolicy = existing?.compressionPolicy,
                reasoningMappings = emptyMap(),
                isReasoning = reasoningDraft.enabled,
                reasoningDraft = reasoningDraft,
                isTools = isTools,
                isUnavailable = false
            )
        }
    }

    fun mergeDiscoveredCatalogConfigs(
        discoveredList: List<DiscoveredModelInfo>,
        initialModels: List<UpstreamModel>
    ): List<CatalogModelConfig> {
        val discoveredMap = discoveredList.associateBy { it.id }
        val models = discoveredList.map { it.id }
        val existingMap = initialModels.associateBy { it.upstreamModelId }
        val modelIds = (models + initialModels.map { it.upstreamModelId }).distinct()

        return modelIds.map { mName ->
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
            val inputModalities = existing?.capabilities?.inputModalities?.toSet()?.takeIf { it.isNotEmpty() }
                ?: disc?.inputModalities.orEmpty()
            val outputModalities = existing?.capabilities?.outputModalities?.toSet()?.takeIf { it.isNotEmpty() }
                ?: disc?.outputModalities.orEmpty()
            val inputMimeTypes =
                existing?.capabilities?.inputMimeTypes?.takeIf { it.isNotEmpty() } ?: disc?.inputMimeTypes.orEmpty()
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
                vendor = disc?.vendor,
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
    }

    fun buildFinalUpstreamModels(
        fetchedModelConfigs: List<CatalogModelConfig>,
        selectedModelIds: Set<String>,
        initialModels: List<UpstreamModel>,
        providerId: String,
        protocol: ProviderProtocol
    ): List<UpstreamModel> {
        return fetchedModelConfigs
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
                val modelId = existing?.id ?: "$providerId-$cleanId"
                val tokenLimits = ModelTokenLimits(
                    contextWindow = config.inputTokenLimit.takeUnless { config.isImageGeneration },
                    contextWindowSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.inputTokenLimitSource,
                    inputTokenLimit = config.inputTokenLimit.takeUnless { config.isImageGeneration },
                    inputTokenLimitSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.inputTokenLimitSource,
                    outputTokenLimit = config.outputTokenLimit.takeUnless { config.isImageGeneration },
                    outputTokenLimitSource = if (config.isImageGeneration) TokenLimitSource.UNKNOWN else config.outputTokenLimitSource
                )
                val capabilities = ModelCapabilities(
                    roles = if (config.isImageGeneration) listOf(ModelRole.IMAGE_GENERATION) else config.roles.ifEmpty {
                        setOf(
                            ModelRole.AGENT
                        )
                    }.toList(),
                    inputModalities = if (config.isImageGeneration) listOf(ModelModality.TEXT) else inputModalities.toList(),
                    outputModalities = if (config.isImageGeneration) listOf(ModelModality.IMAGE) else config.outputModalities.ifEmpty {
                        setOf(
                            ModelModality.TEXT
                        )
                    }.toList(),
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
                    compressionPolicy = if (config.isImageGeneration) null else config.compressionPolicy
                        ?: existing?.compressionPolicy
                )
            }
    }
}
