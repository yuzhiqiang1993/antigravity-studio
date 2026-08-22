package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.TokenLimitSource
import kotlinx.serialization.json.*

/**
 * 远端模型发现与元数据中立模型
 */
data class DiscoveredModelInfo(
    val id: String,
    val displayName: String? = null,
    val inputTokenLimit: Long? = null,
    val inputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val outputTokenLimit: Long? = null,
    val outputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = true,
    val supportsReasoning: Boolean = false,
    val defaultReasoningLevel: String? = null,
    val supportedReasoningLevels: Set<String> = emptySet(),
    val thinkingBudget: Long? = null,
    val minThinkingBudget: Long? = null,
    val rawExtra: Map<String, String> = emptyMap()
)

/**
 * 通用多厂商模型列表探测与元数据解析引擎
 * 严格基于上游实际响应提取真实配置，标记真实配置来源为 CATALOG。
 * 兼容标准 OpenAI、OpenRouter、Google Gemini、Anthropic、Ollama、CPA、Sub2API、OneAPI 等主流响应结构。
 */
object UniversalModelCatalogParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 宽容解析上游模型列表响应
     */
    fun parse(rawJson: String): List<DiscoveredModelInfo> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()

        return try {
            val root = json.parseToJsonElement(trimmed)
            when (root) {
                is JsonArray -> parseJsonArray(root)
                is JsonObject -> parseJsonObject(root)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonArray(array: JsonArray): List<DiscoveredModelInfo> {
        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> parseSingleModelObject(element)
                is JsonPrimitive -> {
                    val id = element.contentOrNull?.trim()
                    if (!id.isNullOrBlank()) DiscoveredModelInfo(id = id) else null
                }
                else -> null
            }
        }
    }

    private fun parseJsonObject(obj: JsonObject): List<DiscoveredModelInfo> {
        // 0. 外层包装层：response, result, payload
        obj["response"]?.jsonObject?.let { return parseJsonObject(it) }
        obj["result"]?.jsonObject?.let { return parseJsonObject(it) }
        obj["payload"]?.jsonObject?.let { return parseJsonObject(it) }

        // 1. 常见包装键：data (OpenAI / OpenRouter / Anthropic / Sub2API / CPA)
        obj["data"]?.let { dataElem ->
            when (dataElem) {
                is JsonArray -> return parseJsonArray(dataElem)
                is JsonObject -> return parseJsonObject(dataElem)
                else -> Unit
            }
        }

        // 2. 常见包装键：models (Gemini / Ollama / CPA Client / Antigravity 官方)
        obj["models"]?.let { modelsElem ->
            when (modelsElem) {
                is JsonArray -> return parseJsonArray(modelsElem)
                is JsonObject -> {
                    // 对象映射形态：{"gpt-4o": {...}, "claude-3-5": {...}}
                    return modelsElem.mapNotNull { (key, value) ->
                        if (value is JsonObject) {
                            parseSingleModelObject(value, fallbackId = key)
                        } else {
                            DiscoveredModelInfo(id = key)
                        }
                    }
                }
                else -> Unit
            }
        }

        // 3. 直接作为模型字典映射：{"gpt-4o": {...}, "claude-3-5": {...}}
        val hasNestedModelObjects = obj.values.isNotEmpty() && obj.values.all { it is JsonObject }
        if (hasNestedModelObjects) {
            return obj.mapNotNull { (key, value) ->
                if (value is JsonObject) parseSingleModelObject(value, fallbackId = key) else null
            }
        }

        // 4. 单一模型对象
        val single = parseSingleModelObject(obj)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun parseSingleModelObject(obj: JsonObject, fallbackId: String? = null): DiscoveredModelInfo? {
        val rawId = obj.stringField("id")
            ?: obj.stringField("slug")
            ?: obj.stringField("name")
            ?: obj.stringField("model")
            ?: obj.stringField("model_id")
            ?: fallbackId
            ?: return null

        val id = rawId.removePrefix("models/").trim()
        if (id.isBlank()) return null

        val displayName = obj.stringField("displayName")
            ?: obj.stringField("display_name")
            ?: obj.stringField("name")
            ?: obj.stringField("title")

        // 输入上下文探测（严格由上游真实字段给出）
        val inputTokens = obj.longField("context_window")
            ?: obj.longField("max_context_window")
            ?: obj.longField("contextWindow")
            ?: obj.longField("context_length")
            ?: obj.longField("max_context_length")
            ?: obj.longField("inputTokenLimit")
            ?: obj.longField("input_token_limit")
            ?: obj.longField("maxTokens")
            ?: obj.longField("maxContextTokens")
            ?: obj.nestedLong("limits", "context_window")
            ?: obj.nestedLong("details", "context_length")

        // 输出上限探测（严格由上游真实字段给出）
        val outputTokens = obj.longField("maxOutputTokens")
            ?: obj.longField("max_output_tokens")
            ?: obj.longField("outputTokenLimit")
            ?: obj.longField("output_token_limit")
            ?: obj.longField("max_completion_tokens")
            ?: obj.longField("maxCompletionTokens")
            ?: obj.nestedLong("top_provider", "max_completion_tokens")
            ?: obj.nestedLong("limits", "max_output_tokens")

        // 多模态能力探测（严格由上游真实字段声明）
        val inputModalities = obj["input_modalities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.lowercase() } ?: emptyList()
        val supportsVision = obj.booleanField("supportsImages")
            ?: obj.booleanField("supports_images")
            ?: obj.booleanField("supportsVision")
            ?: obj.booleanField("supports_vision")
            ?: obj.booleanField("multimodal")
            ?: inputModalities.contains("image")
            ?: (obj.nestedString("architecture", "modality")?.contains("image") == true)
            ?: (obj.nestedString("architecture", "modality")?.contains("multimodal") == true)
            ?: false

        // 工具支持探测 (默认 true)
        val supportsTools = obj.booleanField("supportsTools")
            ?: obj.booleanField("supports_tools")
            ?: obj.booleanField("supports_parallel_tool_calls")
            ?: obj.booleanField("tools")
            ?: obj.booleanField("function_calling")
            ?: obj.nestedBoolean("capabilities", "tools")
            ?: true

        // 推理与 Thinking 探测（严格由上游真实结构声明）
        val reasoningObj = obj["reasoning"]?.jsonObject
        val supportedReasoningLevels = mutableSetOf<String>()
        obj["supported_reasoning_levels"]?.jsonArray?.forEach { levelElem ->
            when (levelElem) {
                is JsonObject -> levelElem.stringField("effort")?.let { supportedReasoningLevels.add(it) }
                is JsonPrimitive -> levelElem.contentOrNull?.let { supportedReasoningLevels.add(it) }
                else -> Unit
            }
        }
        val supportsReasoning = obj.booleanField("supportsThinking")
            ?: obj.booleanField("supports_thinking")
            ?: obj.booleanField("supportsReasoning")
            ?: obj.booleanField("supports_reasoning")
            ?: (reasoningObj != null)
            ?: supportedReasoningLevels.isNotEmpty()
            ?: false

        val defaultReasoningLevel = obj.stringField("default_reasoning_level")
            ?: obj.stringField("defaultReasoningLevel")

        val thinkingBudget = obj.longField("thinkingBudget")
            ?: obj.longField("thinking_budget")
            ?: reasoningObj?.longField("thinkingBudget")
            ?: reasoningObj?.longField("thinking_budget")

        val minThinkingBudget = obj.longField("minThinkingBudget")
            ?: obj.longField("min_thinking_budget")
            ?: reasoningObj?.longField("minThinkingBudget")
            ?: reasoningObj?.longField("min_thinking_budget")

        return DiscoveredModelInfo(
            id = id,
            displayName = displayName?.takeIf { it.isNotBlank() && it != id },
            inputTokenLimit = inputTokens?.takeIf { it > 0L },
            inputTokenLimitSource = if (inputTokens != null && inputTokens > 0L) TokenLimitSource.CATALOG else TokenLimitSource.UNKNOWN,
            outputTokenLimit = outputTokens?.takeIf { it > 0L },
            outputTokenLimitSource = if (outputTokens != null && outputTokens > 0L) TokenLimitSource.CATALOG else TokenLimitSource.UNKNOWN,
            supportsVision = supportsVision,
            supportsTools = supportsTools,
            supportsReasoning = supportsReasoning,
            defaultReasoningLevel = defaultReasoningLevel,
            supportedReasoningLevels = supportedReasoningLevels,
            thinkingBudget = thinkingBudget,
            minThinkingBudget = minThinkingBudget
        )
    }

    private fun JsonObject.stringField(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.longField(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.booleanField(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.nestedString(parentKey: String, childKey: String): String? = this[parentKey]?.jsonObject?.stringField(childKey)
    private fun JsonObject.nestedLong(parentKey: String, childKey: String): Long? = this[parentKey]?.jsonObject?.longField(childKey)
    private fun JsonObject.nestedBoolean(parentKey: String, childKey: String): Boolean? = this[parentKey]?.jsonObject?.booleanField(childKey)
}
