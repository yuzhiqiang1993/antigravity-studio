package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import kotlinx.serialization.json.*

internal object CustomCatalogBuilder {

    fun customCatalogEntries(
        config: AppConfig,
        includeTiered: Boolean = true,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null
    ): List<JsonObject> {
        val entries = if (config.virtualModels.isEmpty()) {
            config.upstreamModels
                .mapNotNull { upstream ->
                    if (!upstream.enabled) return@mapNotNull null
                    val provider = config.providers.firstOrNull { item ->
                        item.id == upstream.providerId && item.enabled
                    } ?: return@mapNotNull null
                    val displayName = ModelIdentity.configuredModelDisplayName(
                        modelName = upstream.effectiveName,
                        reasoningLevel = null,
                        providerName = provider.name,
                        supportsReasoning = upstream.capabilities.reasoning.supportsReasoning
                    )
                    buildCatalogEntry(
                        upstream = upstream,
                        provider = provider,
                        modelName = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        displayName = displayName,
                        hostModelId = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        catalogKey = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        reasoningLevel = null,
                        entryId = upstream.id,
                        policy = CatalogCompressionApplier.healCheckpointPolicy(
                            upstream.compressionPolicy ?: config.modelCompressionPolicies[upstream.id],
                            checkpointWorkers,
                            defaultCheckpointWorker
                        )
                    )
                }
        } else {
            val routableVirtuals = config.virtualModels.filter { virtual ->
                RouteResolver.isRoutableVirtualModel(config, virtual)
            }
            routableVirtuals.mapNotNull { virtual ->
                val upstream = config.upstreamModels.firstOrNull {
                    it.id == virtual.upstreamModelId ||
                            it.upstreamModelId == virtual.upstreamModelId ||
                            it.hostModelId == virtual.upstreamModelId
                } ?: return@mapNotNull null
                val provider = config.providers.firstOrNull { item -> item.id == upstream.providerId && item.enabled }
                    ?: return@mapNotNull null
                val rawDisplayName = virtual.displayName?.takeIf { it.isNotBlank() }
                    ?: virtual.name.takeIf { it.isNotBlank() }
                    ?: upstream.displayName?.takeIf { it.isNotBlank() }
                    ?: upstream.name.takeIf { it.isNotBlank() }
                    ?: virtual.id
                val displayName = ModelIdentity.configuredModelDisplayName(
                    modelName = rawDisplayName,
                    reasoningLevel = virtual.defaultReasoningLevel,
                    providerName = provider.name,
                    supportsReasoning = upstream.capabilities.reasoning.supportsReasoning
                )
                buildCatalogEntry(
                    upstream = upstream,
                    provider = provider,
                    modelName = RouteResolver.catalogKey(virtual),
                    displayName = displayName,
                    hostModelId = RouteResolver.effectiveHostModelId(virtual),
                    catalogKey = RouteResolver.catalogKey(virtual),
                    reasoningLevel = virtual.defaultReasoningLevel,
                    entryId = virtual.id,
                    policy = CatalogCompressionApplier.healCheckpointPolicy(
                        config.modelCompressionPolicies[virtual.id]
                            ?: config.modelCompressionPolicies[upstream.id]
                            ?: upstream.compressionPolicy,
                        checkpointWorkers,
                        defaultCheckpointWorker
                    )
                )
            }
        }
        return if (includeTiered) entries + buildTieredCatalogEntries(
            config,
            entries,
            checkpointWorkers,
            defaultCheckpointWorker
        ) else entries
    }

    fun buildTieredCatalogEntries(
        config: AppConfig,
        entries: List<JsonObject>,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): List<JsonObject> {
        val virtualsWithUpstream = config.virtualModels
            .filter { RouteResolver.isRoutableVirtualModel(config, it) }
            .mapNotNull { virtual ->
                val upstream = config.upstreamModels.firstOrNull {
                    (it.id == virtual.upstreamModelId || it.upstreamModelId == virtual.upstreamModelId || it.hostModelId == virtual.upstreamModelId) && it.enabled
                } ?: return@mapNotNull null
                if (!upstream.capabilities.reasoning.supportsReasoning) return@mapNotNull null
                val provider = config.providers.firstOrNull { it.id == upstream.providerId && it.enabled }
                    ?: return@mapNotNull null
                Triple(virtual, upstream, provider)
            }
        if (virtualsWithUpstream.isEmpty()) return emptyList()

        val groupMap = virtualsWithUpstream.groupBy { (virtual, upstream, _) ->
            upstream.id to ModelIdentity.catalogFamilyBase(virtual)
        }

        val tieredEntries = mutableListOf<JsonObject>()
        for ((groupKey, groupModels) in groupMap) {
            val (_, familyBase) = groupKey
            val (firstVm, upstream, provider) = groupModels.first()
            val preferredVm = ModelIdentity.REASONING_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
                groupModels.map { it.first }.firstOrNull { it.defaultReasoningLevel == level }
            } ?: firstVm

            val tieredKey = "$familyBase-tiered"
            val rawName = firstVm.displayName?.takeIf { it.isNotBlank() }
                ?: firstVm.name.takeIf { it.isNotBlank() }
                ?: upstream.displayName?.takeIf { it.isNotBlank() }
                ?: upstream.name
            val baseDisplayName = ModelIdentity.stripDisplayLevelSuffix(
                ModelIdentity.configuredModelDisplayName(
                    modelName = rawName,
                    reasoningLevel = null,
                    providerName = provider.name,
                    supportsReasoning = true
                )
            )
            val tieredHostModelId = ModelIdentity.effectiveHostModelId(preferredVm)
            val entry = buildCatalogEntry(
                upstream = upstream,
                provider = provider,
                modelName = tieredKey,
                displayName = baseDisplayName,
                hostModelId = tieredHostModelId,
                catalogKey = tieredKey,
                reasoningLevel = null,
                entryId = tieredKey,
                policy = CatalogCompressionApplier.healCheckpointPolicy(
                    config.modelCompressionPolicies[firstVm.id]
                        ?: config.modelCompressionPolicies[upstream.id]
                        ?: upstream.compressionPolicy,
                    checkpointWorkers,
                    defaultCheckpointWorker
                )
            )
            val updatedEntry = JsonObject(
                entry + mapOf(
                    "thinkingBudget" to JsonPrimitive(-1)
                )
            )
            tieredEntries += updatedEntry
        }
        return tieredEntries
    }

    fun buildCatalogEntry(
        upstream: UpstreamModel,
        provider: Provider,
        modelName: String,
        displayName: String,
        hostModelId: String,
        catalogKey: String,
        entryId: String,
        policy: ModelCompressionPolicy?,
        reasoningLevel: ReasoningLevel? = null
    ): JsonObject {
        val defaultContextWindow = 128_000L
        val defaultInputTokenLimit = 128_000L
        val defaultOutputTokenLimit = 65_536L
        val capabilities = upstream.capabilities
        val contextWindow = upstream.tokenLimits.contextWindow ?: upstream.contextLength ?: defaultContextWindow
        val inputLimit = upstream.tokenLimits.inputTokenLimit ?: contextWindow ?: defaultInputTokenLimit
        val outputLimit = upstream.tokenLimits.outputTokenLimit ?: upstream.maxOutputTokens ?: defaultOutputTokenLimit
        val resolvedPolicy = policy?.takeIf { it.enabled }?.resolveEffective(contextWindow, outputLimit)
        val entry = buildJsonObject {
            put("id", entryId)
            put("name", "models/$hostModelId")
            put("displayName", displayName)
            put("description", "Custom BYOK Model (Provider: " + provider.name + ")")
            put("hostModelId", hostModelId)
            put("catalogKey", catalogKey)
            put("model", hostModelId)
            put("planModel", hostModelId)
            put("requestedModel", hostModelId)
            put("apiProvider", "API_PROVIDER_GOOGLE_GEMINI")
            put("modelProvider", modelProvider(provider.protocol))
            put("recommended", false)
            put("contextWindow", contextWindow)
            put("inputTokenLimit", inputLimit)
            put("maxTokens", inputLimit)
            put("outputTokenLimit", outputLimit)
            put("maxOutputTokens", outputLimit)
            put("supportsImages", ModelModality.IMAGE in capabilities.inputModalities)
            put("supportsAudio", ModelModality.AUDIO in capabilities.inputModalities)
            put("supportsVideo", ModelModality.VIDEO in capabilities.inputModalities)
            put("supportsTools", capabilities.tools)
            put("supportsThinking", capabilities.reasoning.supportsReasoning)
            put("roles", buildJsonArray {
                capabilities.roles.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("inputModalities", buildJsonArray {
                capabilities.inputModalities.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("outputModalities", buildJsonArray {
                capabilities.outputModalities.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("supportedMimeTypes", buildJsonObject {
                capabilities.inputMimeTypes.forEach { mime -> put(mime, true) }
            })
            if (capabilities.reasoning.supportsReasoning) {
                put("thinkingBudget", effectiveThinkingBudget(upstream, reasoningLevel, provider.protocol))
            }
            capabilities.reasoning.minThinkingBudget?.let { put("minThinkingBudget", it) }
            put("supportedGenerationMethods", buildJsonArray {
                add(JsonPrimitive("generateContent"))
                add(JsonPrimitive("streamGenerateContent"))
            })
            provider.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
                put("tagTitle", name)
                put("tagDescription", "BYOK")
            }
            resolvedPolicy?.let { resolved ->
                put("modelExperiments", CatalogCompressionApplier.checkpointExperiments(resolved))
            }
        }
        return entry
    }

    fun effectiveThinkingBudget(
        upstream: UpstreamModel,
        reasoningLevel: ReasoningLevel?,
        protocol: ProviderProtocol
    ): Int {
        val reasoning = upstream.capabilities.reasoning
        val mapping = reasoningLevel?.let { level ->
            ReasoningMappingSupport.parse(reasoning.levels)[level]
                ?: ReasoningMappingSupport.defaultMapping(protocol, level)
        }
        return when (mapping?.kind?.lowercase()) {
            "budget_tokens" -> ReasoningMappingSupport.mappingValueAsInt(mapping)
                ?: reasoning.thinkingBudget
                ?: -1

            "disabled" -> 0
            else -> reasoning.thinkingBudget ?: -1
        }
    }

    fun modelProvider(protocol: ProviderProtocol): String {
        return when (protocol) {
            ProviderProtocol.ANTHROPIC_MESSAGES -> "MODEL_PROVIDER_ANTHROPIC"
            ProviderProtocol.GEMINI_GENERATE_CONTENT -> "MODEL_PROVIDER_GOOGLE"
            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> "MODEL_PROVIDER_OPENAI"
        }
    }
}
