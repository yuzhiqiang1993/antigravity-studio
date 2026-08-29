package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import kotlinx.serialization.json.*

internal object CatalogCompressionApplier {

    val catalogJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun applyOfficialCompressionPolicies(
        root: JsonObject,
        policies: Map<String, ModelCompressionPolicy>
    ): JsonObject {
        if (policies.isEmpty()) return root
        val expandedPolicies = policies.toMutableMap()
        CatalogModelFilter.officialModelAliases(root).forEach { (deprecated, replacement) ->
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

    fun findPolicy(
        policies: Map<String, ModelCompressionPolicy>,
        key: String?,
        value: JsonElement
    ): ModelCompressionPolicy? {
        val objectValue = value as? JsonObject
        val rawCandidates = listOfNotNull(
            key,
            objectValue?.get("id")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("name")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("model")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("catalogKey")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("displayName")?.jsonPrimitive?.contentOrNull
        )
        val normalizedCandidates = rawCandidates.flatMap { raw ->
            val norm = CatalogModelFilter.normalizeCatalogModelId(raw)
            val baseSlug = norm.removeSuffix("-high")
                .removeSuffix("-medium")
                .removeSuffix("-low")
                .removeSuffix("-tiered")
            listOf(norm, baseSlug, "$baseSlug-tiered")
        }.distinct()

        return normalizedCandidates.firstNotNullOfOrNull { candidate ->
            policies.entries.firstOrNull { (modelId, _) ->
                val normModelId = CatalogModelFilter.normalizeCatalogModelId(modelId)
                val normBaseSlug = normModelId.removeSuffix("-high")
                    .removeSuffix("-medium")
                    .removeSuffix("-low")
                    .removeSuffix("-tiered")
                normModelId == candidate || normBaseSlug == candidate
            }?.value
        }
    }

    fun applyPolicyToEntry(
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
        val resolved = effectivePolicy.resolveEffective(capacity, declaredOutputLimit) ?: return value
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

        val updatedPayload = if (existingPayload != null) {
            JsonObject(existingPayload).toMutableMap().apply {
                put("enabled", JsonPrimitive(resolved.enabled))
                put("checkpoint_model", JsonPrimitive(resolved.checkpointModel))
                put("strategy", JsonPrimitive(resolved.strategy))
                put("use_last_planner_model", JsonPrimitive(resolved.useLastPlannerModel))
                put("token_threshold", JsonPrimitive(resolved.tokenThreshold.toString()))
                put("max_token_limit", JsonPrimitive(resolved.maxTokenLimit.toString()))
                put("max_output_tokens", JsonPrimitive(resolved.maxOutputTokens.toString()))
            }
        } else {
            mutableMapOf<String, JsonElement>().apply {
                put("enabled", JsonPrimitive(resolved.enabled))
                put("checkpoint_model", JsonPrimitive(resolved.checkpointModel))
                put("strategy", JsonPrimitive(resolved.strategy))
                put("max_overhead_ratio", JsonPrimitive(resolved.maxOverheadRatio))
                put("moving_window_size", JsonPrimitive(resolved.movingWindowSize))
                put("use_last_planner_model", JsonPrimitive(resolved.useLastPlannerModel))
                put("is_sync", JsonPrimitive(resolved.isSync))
                put("max_user_requests", JsonPrimitive(resolved.maxUserRequests))
                put("include_last_user_message", JsonPrimitive(resolved.includeLastUserMessage))
                put("include_conversation_log", JsonPrimitive(resolved.includeConversationLog))
                put("include_running_task_snapshots", JsonPrimitive(resolved.includeRunningTaskSnapshots))
                put("include_subagent_snapshots", JsonPrimitive(resolved.includeSubagentSnapshots))
                put("include_artifact_snapshots", JsonPrimitive(resolved.includeArtifactSnapshots))
                put("token_threshold", JsonPrimitive(resolved.tokenThreshold.toString()))
                put("max_token_limit", JsonPrimitive(resolved.maxTokenLimit.toString()))
                put("max_output_tokens", JsonPrimitive(resolved.maxOutputTokens.toString()))
            }
        }
        val payloadText = catalogJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(updatedPayload)
        )
        val experiment = buildJsonObject {
            put("stringValue", payloadText)
        }
        val existingModelExperiments = entry["modelExperiments"]?.jsonObject ?: JsonObject(emptyMap())
        val existingExperiments = existingModelExperiments["experiments"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedExperiments = JsonObject(existingExperiments + ("CASCADE_USE_EXPERIMENT_CHECKPOINTER" to experiment))
        val modelExperiments = JsonObject(existingModelExperiments + ("experiments" to updatedExperiments))
        return JsonObject(entry + ("modelExperiments" to modelExperiments))
    }

    fun healCheckpointPolicy(
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

    fun checkpointExperiments(policy: ModelCompressionPolicy): JsonObject {
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

    fun checkpointWorkerIds(root: JsonObject): List<String> {
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

    fun checkpointWorkerLimits(root: JsonObject): Map<String, Long> {
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
}
