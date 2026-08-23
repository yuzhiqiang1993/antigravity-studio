package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import kotlinx.serialization.json.*

object CatalogInjector {

    private val catalogJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun injectCustomModels(
        root: JsonObject,
        config: AppConfig,
        includeTiered: Boolean = true
    ): JsonObject {
        val checkpointWorkers = checkpointWorkerIds(root)
        val defaultCheckpointWorker = checkpointWorkers
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
        val entries = customCatalogEntries(
            config,
            includeTiered,
            checkpointWorkers,
            defaultCheckpointWorker
        )
        if (entries.isEmpty()) return root
        val response = root["response"] as? JsonObject
        return if (response?.get("models") != null) {
            val updatedResponse = appendCatalogEntries(response, entries)
            val withRoles = injectRoleReferences(updatedResponse, entries)
            JsonObject(root + ("response" to injectTieredReferences(withRoles, entries)))
        } else {
            injectTieredReferences(injectRoleReferences(appendCatalogEntries(root, entries), entries), entries)
        }
    }

    fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        val disabled = disabledModelIds.map(::normalizeCatalogModelId).toMutableSet()
        officialModelAliases(root).forEach { (deprecated, replacement) ->
            if (normalizeCatalogModelId(deprecated) in disabled) disabled += normalizeCatalogModelId(replacement)
            if (normalizeCatalogModelId(replacement) in disabled) disabled += normalizeCatalogModelId(deprecated)
        }
        val filteredRoot = filterCatalogContainer(root, disabled)
        val response = filteredRoot["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(filteredRoot + ("response" to filterCatalogContainer(response, disabled)))
        } else {
            filteredRoot
        }
    }

    fun applyOfficialCompressionPolicies(
        root: JsonObject,
        policies: Map<String, ModelCompressionPolicy>
    ): JsonObject {
        if (policies.isEmpty()) return root
        val expandedPolicies = policies.toMutableMap()
        officialModelAliases(root).forEach { (deprecated, replacement) ->
            policies[deprecated]?.let { expandedPolicies[replacement] = it }
            policies[replacement]?.let { expandedPolicies[deprecated] = it }
        }
        val checkpointWorkers = checkpointWorkerIds(root)
        val checkpointWorkerLimits = checkpointWorkerLimits(root)
        val defaultCheckpointWorker = checkpointWorkers
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

        fun applyContainer(container: JsonObject): JsonObject {
            val models = container["models"]
            val updatedModels = when (models) {
                is JsonObject -> JsonObject(models.mapValues { (key, value) ->
                    val policy = findPolicy(expandedPolicies, key, value)
                    if (policy == null) value else applyPolicyToEntry(
                        value,
                        policy,
                        checkpointWorkers,
                        defaultCheckpointWorker,
                        checkpointWorkerLimits
                    )
                })

                is JsonArray -> JsonArray(models.map { value ->
                    val objectValue = value as? JsonObject ?: return@map value
                    val policy = findPolicy(expandedPolicies, null, objectValue)
                    if (policy == null) value else applyPolicyToEntry(
                        value,
                        policy,
                        checkpointWorkers,
                        defaultCheckpointWorker,
                        checkpointWorkerLimits
                    )
                })

                else -> models
            }
            return if (updatedModels == null) container else JsonObject(container + ("models" to updatedModels))
        }

        val direct = applyContainer(root)
        val response = direct["response"] as? JsonObject ?: return direct
        return JsonObject(direct + ("response" to applyContainer(response)))
    }

    private fun injectTieredReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        if (container["models"] !is JsonObject) return container
        val tieredIds = entries.mapNotNull { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.endsWith("-tiered") }
        }.distinct()
        if (tieredIds.isEmpty()) return container
        val current = container["tieredModelIds"] as? JsonObject ?: JsonObject(emptyMap())
        val existing = (current["custom"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        tieredIds.forEach { id ->
            if (existing.none { it.jsonPrimitive.contentOrNull == id }) existing += JsonPrimitive(id)
        }
        return JsonObject(container + ("tieredModelIds" to JsonObject(current + ("custom" to JsonArray(existing)))))
    }

    private fun injectRoleReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        val result = container.toMutableMap()
        val roleEntries = entries.filterNot { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
        }
        val agentIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
        val imageIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("image_generation", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }

        val existingSorts = result["agentModelSorts"] as? JsonArray
        if (existingSorts != null && agentIds.isNotEmpty()) {
            val sorts = existingSorts.toMutableList()
            val nonAgentIds = roleEntries.filterNot { entry ->
                (entry["roles"] as? JsonArray)?.any {
                    it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
                } == true
            }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
                .toSet()
            sorts.indices.forEach { sortIndex ->
                val sortObject = sorts[sortIndex] as? JsonObject ?: return@forEach
                val groups = sortObject["groups"] as? JsonArray ?: return@forEach
                sorts[sortIndex] = JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                    val groupObject = group as? JsonObject ?: return@map group
                    val currentIds = (groupObject["modelIds"] as? JsonArray)?.toMutableList() ?: mutableListOf()
                    currentIds.removeAll { it.jsonPrimitive.contentOrNull in nonAgentIds }
                    agentIds.forEach { id ->
                        if (currentIds.none { it.jsonPrimitive.contentOrNull == id }) currentIds += JsonPrimitive(id)
                    }
                    JsonObject(groupObject + ("modelIds" to JsonArray(currentIds)))
                })))
            }
            val byokIndex = sorts.indexOfFirst {
                (it as? JsonObject)?.get("displayName")?.jsonPrimitive?.contentOrNull == "BYOK"
            }
            val targetIndex = if (byokIndex >= 0) byokIndex else {
                sorts += buildJsonObject { put("displayName", "BYOK"); put("groups", JsonArray(emptyList())) }
                sorts.lastIndex
            }
            val target = sorts[targetIndex] as? JsonObject ?: JsonObject(emptyMap())
            val groups = mutableListOf<JsonElement>().apply {
                addAll(
                    (target["groups"] as? JsonArray)?.toList()
                        ?: listOf(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
                )
                if (isEmpty()) add(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
            }
            val firstGroup = groups.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
            val modelIds = mutableListOf<JsonElement>().apply {
                addAll((firstGroup["modelIds"] as? JsonArray)?.toList().orEmpty())
            }
            agentIds.forEach { id ->
                if (modelIds.none { it.jsonPrimitive.contentOrNull == id }) modelIds += JsonPrimitive(id)
            }
            groups[0] = JsonObject(firstGroup + ("modelIds" to JsonArray(modelIds)))
            sorts[targetIndex] = JsonObject(target + ("groups" to JsonArray(groups)))
            result["agentModelSorts"] = JsonArray(sorts)
        }

        val existingImageIds = result["imageGenerationModelIds"] as? JsonArray
        if (imageIds.isNotEmpty()) {
            val merged = existingImageIds?.toMutableList() ?: mutableListOf()
            imageIds.forEach { id ->
                if (merged.none { it.jsonPrimitive.contentOrNull == id }) merged += JsonPrimitive(id)
            }
            result["imageGenerationModelIds"] = JsonArray(merged)
        }
        return JsonObject(result)
    }

    private fun findPolicy(
        policies: Map<String, ModelCompressionPolicy>,
        key: String?,
        value: JsonElement
    ): ModelCompressionPolicy? {
        val objectValue = value as? JsonObject
        val candidates = listOfNotNull(
            key,
            objectValue?.get("id")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("name")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("model")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("catalogKey")?.jsonPrimitive?.contentOrNull
        ).map(::normalizeCatalogModelId)
        return candidates.firstNotNullOfOrNull { candidate ->
            policies.entries.firstOrNull { (modelId, _) -> normalizeCatalogModelId(modelId) == candidate }?.value
        }
    }

    private fun applyPolicyToEntry(
        value: JsonElement,
        policy: ModelCompressionPolicy,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null,
        checkpointWorkerLimits: Map<String, Long> = emptyMap()
    ): JsonElement {
        if (!policy.enabled) return value
        val entry = value as? JsonObject ?: return value
        val effectivePolicy = if (
            defaultCheckpointWorker != null &&
                checkpointWorkers.isNotEmpty() &&
                policy.checkpointModel !in checkpointWorkers
        ) {
            policy.copy(checkpointModel = defaultCheckpointWorker)
        } else {
            policy
        }
        val capacity = listOf("maxTokens", "inputTokenLimit", "contextWindow")
            .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
            .filter { it > 0L }
            .minOrNull()
        val outputLimit = listOf("maxOutputTokens", "outputTokenLimit")
            .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
            .filter { it > 0L }
            .minOrNull()
        val declaredOutputLimit = outputLimit ?: effectivePolicy.maxOutputTokens
        val boundedOutputLimit = checkpointWorkerLimits[effectivePolicy.checkpointModel]
            ?.let(declaredOutputLimit::coerceAtMost)
            ?: declaredOutputLimit
        val resolved = effectivePolicy.resolveEffective(capacity, boundedOutputLimit) ?: return value
        val existingPayload = entry["modelExperiments"]
            ?.jsonObject
            ?.get("experiments")
            ?.jsonObject
            ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")
            ?.jsonObject
            ?.get("stringValue")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { raw -> runCatching { catalogJson.parseToJsonElement(raw).jsonObject }.getOrNull() }
            ?.toMutableMap()
            ?: return value

        val updatedPayload = JsonObject(existingPayload).toMutableMap().apply {
            put("enabled", JsonPrimitive(resolved.enabled))
            put("checkpoint_model", JsonPrimitive(resolved.checkpointModel))
            put("strategy", JsonPrimitive(resolved.strategy))
            put("use_last_planner_model", JsonPrimitive(resolved.useLastPlannerModel))
            put("token_threshold", JsonPrimitive(resolved.tokenThreshold.toString()))
            put("max_token_limit", JsonPrimitive(resolved.maxTokenLimit.toString()))
            put("max_output_tokens", JsonPrimitive(resolved.maxOutputTokens.toString()))
        }
        val payloadText = catalogJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(updatedPayload)
        )
        val experiment = buildJsonObject {
            put("stringValue", payloadText)
        }
        val modelExperiments = buildJsonObject {
            put("experiments", buildJsonObject {
                put("CASCADE_USE_EXPERIMENT_CHECKPOINTER", experiment)
            })
        }
        return JsonObject(entry + ("modelExperiments" to modelExperiments))
    }

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
                        policy = healCheckpointPolicy(
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
                    policy = healCheckpointPolicy(
                        upstream.compressionPolicy
                            ?: config.modelCompressionPolicies[virtual.id]
                            ?: config.modelCompressionPolicies[upstream.id],
                        checkpointWorkers,
                        defaultCheckpointWorker
                    )
                )
            }
        }
        return if (includeTiered) entries + buildTieredCatalogEntries(config, entries, checkpointWorkers, defaultCheckpointWorker) else entries
    }

    private fun healCheckpointPolicy(
        policy: ModelCompressionPolicy?,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): ModelCompressionPolicy? {
        return if (
            policy != null &&
                defaultCheckpointWorker != null &&
                checkpointWorkers.isNotEmpty() &&
                policy.checkpointModel !in checkpointWorkers
        ) {
            policy.copy(checkpointModel = defaultCheckpointWorker)
        } else {
            policy
        }
    }

    private fun buildTieredCatalogEntries(
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
                policy = healCheckpointPolicy(
                    upstream.compressionPolicy
                        ?: config.modelCompressionPolicies[firstVm.id]
                        ?: config.modelCompressionPolicies[upstream.id],
                    checkpointWorkers,
                    defaultCheckpointWorker
                )
            )
            val updatedEntry = JsonObject(entry + mapOf(
                "thinkingBudget" to JsonPrimitive(-1)
            ))
            tieredEntries += updatedEntry
        }
        return tieredEntries
    }

    private fun buildCatalogEntry(
        upstream: UpstreamModel,
        provider: Provider,
        modelName: String,
        displayName: String,
        hostModelId: String,
        catalogKey: String,
        entryId: String,
        policy: ModelCompressionPolicy?,
        reasoningLevel: ReasoningLevel?
    ): JsonObject {
        val defaultContextWindow = 128_000L
        val defaultInputTokenLimit = 128_000L
        val defaultOutputTokenLimit = 65_536L
        val capabilities = upstream.capabilities
        val contextWindow = upstream.tokenLimits.contextWindow ?: upstream.contextLength ?: defaultContextWindow
        val inputLimit = upstream.tokenLimits.inputTokenLimit ?: contextWindow ?: defaultInputTokenLimit
        val outputLimit = upstream.tokenLimits.outputTokenLimit ?: upstream.maxOutputTokens ?: defaultOutputTokenLimit
        val resolvedPolicy = policy?.takeIf { it.enabled }
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
                put("modelExperiments", checkpointExperiments(resolved))
            }
        }
        return entry
    }

    private fun effectiveThinkingBudget(
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

    private fun modelProvider(protocol: ProviderProtocol): String {
        return when (protocol) {
            ProviderProtocol.ANTHROPIC_MESSAGES -> "MODEL_PROVIDER_ANTHROPIC"
            ProviderProtocol.GEMINI_GENERATE_CONTENT -> "MODEL_PROVIDER_GOOGLE"
            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> "MODEL_PROVIDER_OPENAI"
        }
    }

    private fun checkpointExperiments(policy: ModelCompressionPolicy): JsonObject {
        val payload = buildJsonObject {
            put("enabled", policy.enabled)
            put("checkpoint_model", policy.checkpointModel)
            put("strategy", policy.strategy)
            put("max_overhead_ratio", policy.maxOverheadRatio)
            put("moving_window_size", policy.movingWindowSize)
            put("use_last_planner_model", policy.useLastPlannerModel)
            put("is_sync", policy.isSync)
            put("max_user_requests", policy.maxUserRequests)
            put("include_last_user_message", policy.includeLastUserMessage)
            put("include_conversation_log", policy.includeConversationLog)
            put("include_running_task_snapshots", policy.includeRunningTaskSnapshots)
            put("include_subagent_snapshots", policy.includeSubagentSnapshots)
            put("include_artifact_snapshots", policy.includeArtifactSnapshots)
            put("retry_config", buildJsonObject {
                put("max_retries", policy.retryConfig.maxRetries)
                put("initial_sleep_duration_ms", policy.retryConfig.initialSleepDurationMs)
                put("exponential_multiplier", policy.retryConfig.exponentialMultiplier)
                put("include_error_feedback", policy.retryConfig.includeErrorFeedback)
            })
            put("token_threshold", policy.tokenThreshold.toString())
            put("max_token_limit", policy.maxTokenLimit.toString())
            put("max_output_tokens", policy.maxOutputTokens.toString())
        }
        return buildJsonObject {
            put("experiments", buildJsonObject {
                put("CASCADE_USE_EXPERIMENT_CHECKPOINTER", buildJsonObject {
                    put("stringValue", catalogJson.encodeToString(JsonElement.serializer(), payload))
                })
            })
        }
    }

    private fun checkpointWorkerIds(root: JsonObject): List<String> {
        val workers = mutableListOf<String>()
        fun collect(container: JsonObject) {
            when (val models = container["models"]) {
                is JsonObject -> models.values.forEach { collectEntry(it, workers) }
                is JsonArray -> models.forEach { collectEntry(it, workers) }
                else -> Unit
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return workers
    }

    private fun checkpointWorkerLimits(root: JsonObject): Map<String, Long> {
        val referenced = mutableMapOf<String, Long>()
        val direct = mutableMapOf<String, Long>()

        fun record(catalogKey: String?, value: JsonElement) {
            val entry = value as? JsonObject ?: return
            val raw = entry["modelExperiments"]?.jsonObject
                ?.get("experiments")?.jsonObject
                ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")?.jsonObject
                ?.get("stringValue")?.jsonPrimitive?.contentOrNull
                ?: return
            val worker = runCatching {
                catalogJson.parseToJsonElement(raw).jsonObject["checkpoint_model"]
                    ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: return
            val output = listOf("maxOutputTokens", "outputTokenLimit")
                .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
                .filter { it > 0L }
                .minOrNull() ?: return
            referenced[worker] = minOf(referenced[worker] ?: Long.MAX_VALUE, output)
            val isDirect = catalogKey == worker ||
                    entry["id"]?.jsonPrimitive?.contentOrNull == worker ||
                    entry["model"]?.jsonPrimitive?.contentOrNull == worker
            if (isDirect) direct[worker] = minOf(direct[worker] ?: Long.MAX_VALUE, output)
        }

        fun collect(container: JsonObject) {
            when (val models = container["models"]) {
                is JsonObject -> models.forEach { (key, value) -> record(key, value) }
                is JsonArray -> models.forEach { value -> record(null, value) }
                else -> Unit
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return referenced.mapValues { (worker, referencedLimit) -> direct[worker] ?: referencedLimit }
    }

    private fun collectEntry(value: JsonElement, workers: MutableList<String>) {
        val raw = (value as? JsonObject)
            ?.get("modelExperiments")
            ?.jsonObject
            ?.get("experiments")
            ?.jsonObject
            ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")
            ?.jsonObject
            ?.get("stringValue")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return
        runCatching {
            catalogJson.parseToJsonElement(raw).jsonObject["checkpoint_model"]
                ?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let(workers::add)
        }
    }

    private fun officialModelAliases(root: JsonObject): Map<String, String> {
        val aliases = linkedMapOf<String, String>()
        fun collect(container: JsonObject) {
            val deprecated = container["deprecatedModelIds"] as? JsonObject ?: return
            deprecated.forEach { (oldId, value) ->
                val newId = (value as? JsonObject)?.get("newModelId")?.jsonPrimitive?.contentOrNull
                if (!newId.isNullOrBlank()) aliases[oldId] = newId
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return aliases
    }

    private fun filterCatalogContainer(
        container: JsonObject,
        disabled: Set<String>
    ): JsonObject {
        val filteredContainer = when (val models = container["models"]) {
            is JsonArray -> JsonObject(
                container + ("models" to JsonArray(models.filterNot { isDisabledCatalogModel(it, null, disabled) }))
            )

            is JsonObject -> {
                val filtered = models.filterNot { (key, value) ->
                    isDisabledCatalogModel(value, key, disabled)
                }
                JsonObject(container + ("models" to JsonObject(filtered)))
            }

            else -> container
        }

        fun filterIdArray(value: JsonElement?): JsonElement? {
            val array = value as? JsonArray ?: return value
            return JsonArray(array.filterNot { item ->
                normalizeCatalogModelId(item.jsonPrimitive.contentOrNull.orEmpty()) in disabled
            })
        }
        val updated = filteredContainer.toMutableMap()
        if (filteredContainer["agentModelSorts"] != null) {
            updated["agentModelSorts"] = filterSortGroups(filteredContainer["agentModelSorts"], disabled)
        }
        if (filteredContainer["imageGenerationModelIds"] != null) {
            updated["imageGenerationModelIds"] = filterIdArray(filteredContainer["imageGenerationModelIds"])
                ?: JsonArray(emptyList())
        }
        return JsonObject(updated)
    }

    private fun filterSortGroups(value: JsonElement?, disabled: Set<String>): JsonElement {
        val sorts = value as? JsonArray ?: return value ?: JsonArray(emptyList())
        return JsonArray(sorts.map { sort ->
            val sortObject = sort as? JsonObject ?: return@map sort
            val groups = sortObject["groups"] as? JsonArray ?: return@map sort
            JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                val groupObject = group as? JsonObject ?: return@map group
                val ids = groupObject["modelIds"] as? JsonArray ?: return@map group
                JsonObject(groupObject + ("modelIds" to JsonArray(ids.filterNot { id ->
                    normalizeCatalogModelId(id.jsonPrimitive.contentOrNull.orEmpty()) in disabled
                })))
            })))
        })
    }

    private fun appendCatalogEntries(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        return when (val models = container["models"]) {
            is JsonArray -> {
                val arrayEntries = entries.filterNot { entry ->
                    entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
                }
                val existing = models.mapNotNull { catalogModelIds(it).firstOrNull() }.toSet()
                val additions = arrayEntries.filterNot { catalogModelIds(it).any(existing::contains) }
                JsonObject(container + ("models" to JsonArray(models + additions)))
            }

            is JsonObject -> {
                val updated = models.toMutableMap()
                entries.forEach { entry ->
                    val key = entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                        ?: entry["id"]?.jsonPrimitive?.contentOrNull
                        ?: catalogModelIds(entry).firstOrNull()?.removePrefix("models/")
                        ?: return@forEach
                    updated[key] = JsonObject(entry - setOf("id", "name"))
                }
                JsonObject(container + ("models" to JsonObject(updated)))
            }

            else -> JsonObject(container + ("models" to JsonArray(entries)))
        }
    }

    private fun isDisabledCatalogModel(
        element: JsonElement,
        key: String?,
        disabled: Set<String>
    ): Boolean {
        return catalogModelIds(element, key).any { normalizeCatalogModelId(it) in disabled }
    }

    private fun catalogModelIds(element: JsonElement, key: String? = null): List<String> {
        val value = element as? JsonObject
        return listOfNotNull(
            key,
            value?.get("name")?.toString()?.trim('"'),
            value?.get("model")?.toString()?.trim('"'),
            value?.get("id")?.toString()?.trim('"'),
            value?.get("catalogKey")?.toString()?.trim('"')
        )
    }

    private fun normalizeCatalogModelId(value: String): String {
        return value.trim().removePrefix("models/")
    }
}

