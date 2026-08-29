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
        val (inputTokenLimit, inputTokenLimitSource) = when {
            model.tokenLimits.contextWindow != null -> {
                model.tokenLimits.contextWindow to model.tokenLimits.contextWindowSource
            }

            model.tokenLimits.inputTokenLimit != null -> {
                model.tokenLimits.inputTokenLimit to model.tokenLimits.inputTokenLimitSource
            }

            else -> null to TokenLimitSource.UNKNOWN
        }
        return CatalogModelConfig(
            id = model.upstreamModelId,
            name = model.displayName ?: model.name,
            inputTokenLimit = inputTokenLimit,
            inputTokenLimitSource = inputTokenLimitSource,
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
        return initialModels
            .map { it.upstreamModelId }
            .distinct()
            .map { modelId -> toCatalogModelConfig(existingMap.getValue(modelId)) }
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
                val existing = initialModels.firstOrNull { model ->
                    model.upstreamModelId == config.id
                }
                val modelId = existing?.id ?: "$providerId-$cleanId"
                val useDefaultImageCapabilities = config.isImageGeneration && existing == null
                val inputModalities = if (useDefaultImageCapabilities) {
                    setOf(ModelModality.TEXT)
                } else {
                    config.inputModalities
                        .ifEmpty { setOf(ModelModality.TEXT) }
                        .toMutableSet()
                        .apply {
                            if (config.isVision) add(ModelModality.IMAGE) else remove(ModelModality.IMAGE)
                        }
                }
                val inputMimeTypes = if (useDefaultImageCapabilities) {
                    emptyList()
                } else if (config.isVision) {
                    val configuredMimeTypes = config.inputMimeTypes
                    if (configuredMimeTypes.any { mime -> mime.startsWith("image/", ignoreCase = true) }) {
                        configuredMimeTypes
                    } else {
                        configuredMimeTypes + listOf("image/png", "image/jpeg", "image/webp")
                    }
                } else {
                    config.inputMimeTypes.filterNot { mime -> mime.startsWith("image/", ignoreCase = true) }
                }
                val tokenLimits = ModelTokenLimits(
                    contextWindow = config.inputTokenLimit.takeUnless { useDefaultImageCapabilities },
                    contextWindowSource = if (useDefaultImageCapabilities) {
                        TokenLimitSource.UNKNOWN
                    } else {
                        config.inputTokenLimitSource
                    },
                    inputTokenLimit = config.inputTokenLimit.takeUnless { useDefaultImageCapabilities },
                    inputTokenLimitSource = if (useDefaultImageCapabilities) {
                        TokenLimitSource.UNKNOWN
                    } else {
                        config.inputTokenLimitSource
                    },
                    outputTokenLimit = config.outputTokenLimit.takeUnless { useDefaultImageCapabilities },
                    outputTokenLimitSource = if (useDefaultImageCapabilities) {
                        TokenLimitSource.UNKNOWN
                    } else {
                        config.outputTokenLimitSource
                    }
                )
                val capabilities = ModelCapabilities(
                    roles = if (useDefaultImageCapabilities) {
                        listOf(ModelRole.IMAGE_GENERATION)
                    } else {
                        config.roles.ifEmpty {
                            setOf(
                                if (config.isImageGeneration) ModelRole.IMAGE_GENERATION else ModelRole.AGENT
                            )
                        }.toList()
                    },
                    inputModalities = inputModalities.toList(),
                    outputModalities = if (useDefaultImageCapabilities) {
                        listOf(ModelModality.IMAGE)
                    } else {
                        config.outputModalities.ifEmpty {
                            setOf(
                                if (config.isImageGeneration) ModelModality.IMAGE else ModelModality.TEXT
                            )
                        }.toList()
                    },
                    tools = if (useDefaultImageCapabilities) false else config.isTools,
                    inputMimeTypes = inputMimeTypes,
                    reasoning = if (useDefaultImageCapabilities) {
                        ReasoningCapability(supported = false)
                    } else {
                        config.reasoningDraft.toCapability(
                            protocol = protocol,
                            outputTokenLimit = config.outputTokenLimit
                        )
                    }
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
                    compressionPolicy = if (useDefaultImageCapabilities) null else config.compressionPolicy
                        ?: existing?.compressionPolicy
                )
            }
    }
}
