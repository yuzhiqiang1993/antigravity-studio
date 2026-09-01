package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import kotlinx.serialization.json.*

/**
 * 纯函数日志提取器
 * 负责从 transcript.jsonl 中逐行解析并提取 TokenEntry，并执行特征指纹去重。
 */
object UsageExtractor {

    private data class UsageContext(
        val usage: JsonObject,
        val container: JsonObject,
        val timestampContainer: JsonObject? = null
    )

    private data class TokenFieldValue(
        val value: Long,
        val known: Boolean
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 解析单个 transcript.jsonl 文件内容并提取去重后的 TokenEntry 列表
     */
    fun extractFromTranscript(
        lines: Sequence<String>,
        conversationId: String,
        appSource: String = "ide"
    ): List<TokenEntry> {
        val rawEntries = mutableListOf<TokenEntry>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue

            try {
                val element = json.parseToJsonElement(trimmed)
                if (element !is JsonObject) continue
                extractEntriesFromJsonObject(element, conversationId, appSource, rawEntries)
            } catch (_: Exception) {
                // 单行解析异常直接跳过，保证鲁棒性
            }
        }

        return dedupEntries(rawEntries)
    }

    /** 将 Language Server 分页返回的 JSON 对象复用同一套本地 usage 解析逻辑。 */
    fun extractFromJsonObjects(
        objects: Iterable<JsonObject>,
        conversationId: String,
        appSource: String = "ide"
    ): List<TokenEntry> {
        val entries = mutableListOf<TokenEntry>()
        objects.forEach { obj ->
            extractEntriesFromJsonObject(obj, conversationId, appSource, entries)
        }
        return dedupEntries(entries)
    }

    /**
     * 对 TokenEntry 集合进行去重（优先保留信息更完整的项）
     */
    fun dedupEntries(entries: List<TokenEntry>): List<TokenEntry> {
        if (entries.isEmpty()) return emptyList()
        val byFingerprint = LinkedHashMap<String, TokenEntry>()

        for (entry in entries) {
            val fp = entry.fingerprint()
            val existing = byFingerprint[fp]
            if (existing == null) {
                byFingerprint[fp] = entry
            } else {
                // 合并/优先保留非空字段
                byFingerprint[fp] = mergeTokenEntry(existing, entry)
            }
        }

        return byFingerprint.values.toList()
    }

    private fun extractEntriesFromJsonObject(
        obj: JsonObject,
        conversationId: String,
        appSource: String,
        out: MutableList<TokenEntry>
    ) {
        val createdAt = getString(obj, "created_at", "createdAt", "timestamp")

        // 尝试探测 usage 对象，同时保留其外层 chatModel/metadata 的模型证据。
        val usageContext = findUsageContext(obj)
        if (usageContext != null) {
            val contextTimestamp = createdAt.ifBlank {
                getString(usageContext.container, "created_at", "createdAt", "timestamp")
                    .ifBlank { getNestedCreatedAt(usageContext.container) }
                    .ifBlank {
                        usageContext.timestampContainer?.let {
                            getString(it, "created_at", "createdAt", "timestamp")
                                .ifBlank { getNestedCreatedAt(it) }
                        }.orEmpty()
                    }
            }
            val entry = parseUsageObject(
                usage = usageContext.usage,
                defaultTs = contextTimestamp,
                conversationId = conversationId,
                appSource = appSource,
                modelContext = usageContext.container
            )
            if (entry != null && entry.totalTokens > 0) {
                out.add(entry)
                return
            }
        }

        // 备选：针对包含 tool_calls / steps / metadata 内部的嵌套 usage
        val toolCalls = obj["tool_calls"]?.jsonArray ?: obj["toolCalls"]?.jsonArray
        if (toolCalls != null) {
            for (call in toolCalls) {
                if (call !is JsonObject) continue
                val callContext = findUsageContext(call)
                if (callContext != null) {
                    val entry = parseUsageObject(
                        usage = callContext.usage,
                        defaultTs = createdAt,
                        conversationId = conversationId,
                        appSource = appSource,
                        modelContext = callContext.container
                    )
                    if (entry != null && entry.totalTokens > 0) {
                        out.add(entry)
                    }
                }
            }
        }
    }

    private fun findUsageContext(obj: JsonObject): UsageContext? {
        val metadata = obj["metadata"] as? JsonObject
        if (metadata != null) {
            val nestedUsage = metadata["usage"] ?: metadata["model_usage"] ?: metadata["modelUsage"]
            if (nestedUsage is JsonObject) return UsageContext(nestedUsage, metadata)
        }

        val direct = obj["usage"] ?: obj["model_usage"] ?: obj["modelUsage"]
        if (direct is JsonObject) return UsageContext(direct, obj, timestampContainer = metadata)

        val chatModel = obj["chat_model"] ?: obj["chatModel"]
        if (chatModel is JsonObject) {
            val nestedUsage = chatModel["usage"]
            if (nestedUsage is JsonObject) return UsageContext(nestedUsage, chatModel)
        }

        return null
    }

    private fun parseUsageObject(
        usage: JsonObject,
        defaultTs: String,
        conversationId: String,
        appSource: String,
        modelContext: JsonObject? = null
    ): TokenEntry? {
        val input = readTokenField(
            usage,
            "input_tokens", "inputTokens", "prompt_tokens", "promptTokens", "input"
        )
        val output = readTokenField(
            usage,
            "output_tokens",
            "outputTokens",
            "completion_tokens",
            "completionTokens",
            "output",
            "response_output_tokens",
            "responseOutputTokens"
        )
        val cacheRead = readTokenField(
            usage,
            "cache_read_tokens",
            "cacheReadTokens",
            "cache_read_input_tokens",
            "cacheReadInputTokens",
            "cache_read"
        )
        val cacheWrite = readTokenField(
            usage,
            "cache_write_tokens",
            "cacheWriteTokens",
            "cache_creation_input_tokens",
            "cacheCreationInputTokens",
            "cache_write"
        )
        val reasoning = readTokenField(
            usage,
            "thinking_output_tokens",
            "thinkingOutputTokens",
            "reasoning_tokens",
            "reasoningTokens",
            "thinking"
        )
        val reportedTotal = readTokenField(
            usage,
            "total_tokens",
            "totalTokens",
            "total_token_count",
            "totalTokenCount"
        )
        val attributedTotal = saturatedSum(
            input.value,
            output.value,
            cacheRead.value,
            cacheWrite.value,
            reasoning.value
        )
        val unattributed = reportedTotal.value
            .takeIf { reportedTotal.known && it > attributedTotal }
            ?.minus(attributedTotal)
            ?: 0L

        if (attributedTotal == 0L && unattributed == 0L) return null

        val usageModel = getString(usage, "model", "model_id", "modelId", "response_model", "responseModel")
        val contextModel = modelContext?.let {
            getString(it, "response_model", "responseModel", "model", "model_id", "modelId")
        }.orEmpty()
        val model = usageModel.takeUnless(::isMissingModelValue)
            ?: contextModel
        val displayName = getString(usage, "display_name", "displayName", "name")
            .ifBlank { modelContext?.let { getString(it, "display_name", "displayName", "name") }.orEmpty() }
        val provider = getString(usage, "api_provider", "apiProvider", "provider")
            .ifBlank { modelContext?.let { getString(it, "api_provider", "apiProvider", "provider") }.orEmpty() }
        val responseId = getString(usage, "response_id", "responseId", "id")
        val ts = getString(usage, "created_at", "createdAt", "timestamp").ifBlank { defaultTs }
        val identity = UsageModelIdentityResolver.resolve(
            responseModel = if (UsageModelIdentityResolver.isOpaqueModelReference(model) &&
                !isMissingModelValue(contextModel) &&
                !UsageModelIdentityResolver.isOpaqueModelReference(contextModel)
            ) contextModel else model,
            displayName = displayName,
            runtimeModelId = usageModel.takeIf(UsageModelIdentityResolver::isOpaqueModelReference)
                ?: contextModel.takeIf(UsageModelIdentityResolver::isOpaqueModelReference)
        )

        return TokenEntry(
            responseId = responseId.takeIf { it.isNotBlank() },
            input = input.value,
            output = output.value,
            cacheRead = cacheRead.value,
            cacheWrite = cacheWrite.value,
            reasoning = reasoning.value,
            unattributed = unattributed,
            model = identity.model,
            modelDisplayName = identity.displayName,
            modelCanonicalId = identity.canonicalId,
            modelRuntimeId = identity.runtimeId,
            modelAggregationId = identity.aggregationId,
            modelPricingIds = identity.pricingModelIds,
            modelEvidenceSource = identity.evidenceSource,
            missingUsageFields = missingUsageFields(input, output, cacheRead, cacheWrite, reasoning),
            provider = provider,
            timestamp = ts,
            conversationId = conversationId,
            appSource = appSource
        )
    }

    private fun saturatedSum(vararg values: Long): Long {
        var total = 0L
        for (value in values) {
            total = if (value > Long.MAX_VALUE - total) Long.MAX_VALUE else total + value
        }
        return total
    }

    private fun readTokenField(obj: JsonObject, vararg keys: String): TokenFieldValue {
        var hasValidZero = false
        for (key in keys) {
            val primitive = obj[key] as? JsonPrimitive ?: continue
            val parsed = primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
            when {
                parsed == null || parsed < 0L -> Unit
                parsed > 0L -> return TokenFieldValue(parsed, known = true)
                else -> hasValidZero = true
            }
        }
        return TokenFieldValue(0L, known = hasValidZero)
    }

    private fun missingUsageFields(
        input: TokenFieldValue,
        output: TokenFieldValue,
        cacheRead: TokenFieldValue,
        cacheWrite: TokenFieldValue,
        reasoning: TokenFieldValue
    ): List<String> = buildList {
        if (!input.known) add("input")
        if (!output.known) add("output")
        if (!cacheRead.known) add("cache")
        if (!cacheWrite.known) add("cacheWrite")
        if (!reasoning.known) add("reasoning")
    }

    private fun isMissingModelValue(value: String): Boolean =
        value.isBlank() || value.equals("unknown", ignoreCase = true) || value == "?"

    private fun getNestedCreatedAt(obj: JsonObject): String {
        val startMetadata = obj["chatStartMetadata"] ?: obj["chat_start_metadata"]
        return if (startMetadata is JsonObject) {
            getString(startMetadata, "createdAt", "created_at", "timestamp")
        } else {
            ""
        }
    }

    private fun getString(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val el = obj[k] ?: continue
            val str = el.jsonPrimitive.contentOrNull?.trim()
            if (!str.isNullOrEmpty()) return str
        }
        return ""
    }

    private fun mergeTokenEntry(first: TokenEntry, second: TokenEntry): TokenEntry {
        val firstConcrete = first.model.isNotBlank()
                && first.model != "unknown"
                && !UsageModelIdentityResolver.isOpaqueModelReference(first.model)
        val secondConcrete = second.model.isNotBlank()
                && second.model != "unknown"
                && !UsageModelIdentityResolver.isOpaqueModelReference(second.model)
        val model = when {
            firstConcrete -> first.model
            secondConcrete -> second.model
            first.model.isNotBlank() -> first.model
            else -> second.model
        }
        val missing = if (first.missingUsageFields.isEmpty() || second.missingUsageFields.isEmpty()) {
            emptyList()
        } else {
            first.missingUsageFields.intersect(second.missingUsageFields.toSet()).toList()
        }
        val displayName = listOfNotNull(first.modelDisplayName, second.modelDisplayName)
            .firstOrNull { !isMissingModelValue(it) && !UsageModelIdentityResolver.isOpaqueModelReference(it) }
        val canonicalId = listOfNotNull(first.modelCanonicalId, second.modelCanonicalId)
            .firstOrNull { !isMissingModelValue(it) && !UsageModelIdentityResolver.isOpaqueModelReference(it) }
        val catalogId = listOfNotNull(first.modelCatalogId, second.modelCatalogId)
            .firstOrNull { !isMissingModelValue(it) && !UsageModelIdentityResolver.isOpaqueModelReference(it) }
        val input = maxOf(first.input, second.input)
        val output = maxOf(first.output, second.output)
        val cacheRead = maxOf(first.cacheRead, second.cacheRead)
        val cacheWrite = maxOf(first.cacheWrite, second.cacheWrite)
        val reasoning = maxOf(first.reasoning, second.reasoning)
        val reportedTotal = maxOf(first.totalTokens, second.totalTokens)
        val unattributed = (reportedTotal - saturatedSum(input, output, cacheRead, cacheWrite, reasoning))
            .coerceAtLeast(0L)
        return first.copy(
            responseId = first.responseId ?: second.responseId,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            model = model,
            modelDisplayName = displayName,
            modelCanonicalId = canonicalId,
            modelCatalogId = catalogId,
            modelRuntimeId = first.modelRuntimeId ?: second.modelRuntimeId,
            modelAggregationId = first.modelAggregationId ?: second.modelAggregationId,
            modelPricingIds = (first.modelPricingIds + second.modelPricingIds).distinct(),
            modelEvidenceSource = first.modelEvidenceSource ?: second.modelEvidenceSource,
            missingUsageFields = missing,
            provider = if (first.provider.isNotBlank()) first.provider else second.provider,
            timestamp = if (first.timestamp.isNotBlank()) first.timestamp else second.timestamp
        )
    }
}
