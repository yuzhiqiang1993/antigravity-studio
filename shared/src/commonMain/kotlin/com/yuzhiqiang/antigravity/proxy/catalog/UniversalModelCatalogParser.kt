package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.CustomModelCheckpointRetryConfig
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.TokenLimitSource
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import kotlinx.serialization.json.*

/**
 * 远端模型发现与元数据中立模型
 */
data class DiscoveredModelInfo(
    val id: String,
    val displayName: String? = null,
    val vendor: String? = null,
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
    val inputModalities: Set<ModelModality> = emptySet(),
    val outputModalities: Set<ModelModality> = emptySet(),
    val inputMimeTypes: List<String> = emptyList(),
    val roles: Set<ModelRole> = emptySet(),
    val isImageGeneration: Boolean = false,
    val compressionPolicy: ModelCompressionPolicy? = null,
    val reasoningMappings: Map<ReasoningLevel, ReasoningMapping> = emptyMap(),
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
    fun parse(
        rawJson: String,
        protocol: ProviderProtocol? = null,
        isCpaCatalog: Boolean = false
    ): List<DiscoveredModelInfo> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()

        return try {
            val root = json.parseToJsonElement(trimmed)
            val parsed = when (root) {
                is JsonArray -> parseJsonArray(root, protocol, isCpaCatalog)
                is JsonObject -> parseJsonObject(root, protocol, isCpaCatalog)
                else -> emptyList()
            }
            parsed.distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonArray(
        array: JsonArray,
        protocol: ProviderProtocol?,
        isCpaCatalog: Boolean
    ): List<DiscoveredModelInfo> {
        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> parseSingleModelObject(element, protocol = protocol, isCpaCatalog = isCpaCatalog)
                is JsonPrimitive -> {
                    val id = element.contentOrNull?.trim()
                    if (!id.isNullOrBlank()) DiscoveredModelInfo(id = id) else null
                }
                else -> null
            }
        }
    }

    private fun parseJsonObject(
        obj: JsonObject,
        protocol: ProviderProtocol?,
        isCpaCatalog: Boolean
    ): List<DiscoveredModelInfo> {
        // 0. 外层包装层：response, result, payload
        obj["response"]?.jsonObject?.let { return parseJsonObject(it, protocol, isCpaCatalog) }
        obj["result"]?.jsonObject?.let { return parseJsonObject(it, protocol, isCpaCatalog) }
        obj["payload"]?.jsonObject?.let { return parseJsonObject(it, protocol, isCpaCatalog) }

        // 1. 常见包装键：data (OpenAI / OpenRouter / Anthropic / Sub2API / CPA)
        obj["data"]?.let { dataElem ->
            when (dataElem) {
                is JsonArray -> return parseJsonArray(dataElem, protocol, isCpaCatalog)
                is JsonObject -> return parseJsonObject(dataElem, protocol, isCpaCatalog)
                else -> Unit
            }
        }

        // 2. 常见包装键：models (Gemini / Ollama / CPA Client / Antigravity 官方)
        obj["models"]?.let { modelsElem ->
            when (modelsElem) {
                is JsonArray -> return parseJsonArray(modelsElem, protocol, isCpaCatalog)
                is JsonObject -> {
                    // 对象映射形态：{"gpt-4o": {...}, "claude-3-5": {...}}
                    return modelsElem.mapNotNull { (key, value) ->
                        if (value is JsonObject) {
                            parseSingleModelObject(value, fallbackId = key, protocol = protocol, isCpaCatalog = isCpaCatalog)
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
                if (value is JsonObject) parseSingleModelObject(value, fallbackId = key, protocol = protocol, isCpaCatalog = isCpaCatalog) else null
            }
        }

        // 4. 单一模型对象
        val single = parseSingleModelObject(obj, protocol = protocol, isCpaCatalog = isCpaCatalog)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun parseSingleModelObject(
        obj: JsonObject,
        fallbackId: String? = null,
        protocol: ProviderProtocol? = null,
        isCpaCatalog: Boolean = false
    ): DiscoveredModelInfo? {
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
        val rawVendor = obj.stringField(
            "publisher", "vendor", "vendor_name", "vendorName",
            "provider", "provider_name", "providerName", "owned_by", "ownedBy"
        )?.trim()?.takeIf { it.isNotEmpty() }

        val group = obj.stringField("group")?.trim()?.lowercase()
        val inferredVendor = inferVendorFromId(id) ?: inferVendorFromGroup(group)

        val isGenericGatewayVendor = rawVendor?.lowercase()?.let {
            it == "system" || (it == "openai" && inferredVendor != null && inferredVendor != "OpenAI") ||
            it.contains("gate") || it.contains("proxy") || it.contains("oneapi") || it.contains("newapi") ||
            it.contains("relay") || it.contains("default") || it.contains("custom")
        } ?: false

        val vendor = if (inferredVendor != null && (rawVendor == null || isGenericGatewayVendor)) {
            inferredVendor
        } else {
            rawVendor ?: inferredVendor
        }

        // 输入上下文探测：max_tokens 只有 Gemini/CPA 语义下才可作为输入上限，
        // 普通 OpenAI/Anthropic 的 max_tokens 是输出预算，不能误写成输入上限。
        val contextTokens = listOfNotNull(
            obj.findLong("max_context_window", "maxContextWindow"),
            obj.findLong("context_window", "contextWindow")
        ).maxOrNull()
        val contextLength = listOfNotNull(
            obj.findLong("max_context_length", "maxContextLength"),
            obj.findLong("context_length", "contextLength")
        ).maxOrNull()
        val explicitInputTokens = listOfNotNull(
            obj.findLong("maxInputTokens", "max_input_tokens"),
            obj.findLong("inputTokenLimit", "input_token_limit"),
            obj.findLong("maxPromptTokens", "max_prompt_tokens"),
            obj.findLong("maxContextTokens")
        ).maxOrNull()
        val inputTokens = explicitInputTokens ?: when {
            isCpaCatalog -> listOfNotNull(contextTokens, contextLength).maxOrNull()
            protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT -> obj.findLong("maxTokens", "max_tokens")
            else -> listOfNotNull(contextTokens, contextLength).maxOrNull()
        }

        // 输出上限探测（严格由上游真实字段给出）
        val outputTokens = obj.findLong(
            "maxOutputTokens",
            "max_output_tokens",
            "outputTokenLimit",
            "output_token_limit",
            "max_completion_tokens",
            "maxCompletionTokens",
            "max_output_length",
            "maxOutputLength"
        ) ?: if (isCpaCatalog || protocol == ProviderProtocol.ANTHROPIC_MESSAGES) {
            obj.findLong("maxTokens", "max_tokens")
        } else {
            null
        }

        // 多模态能力探测：优先使用显式 modalities，再用布尔能力字段补全。
        val capabilities = obj["capabilities"] as? JsonObject
        val archObj = obj["architecture"] as? JsonObject
        val rawInputModalities = obj["input_modalities"] ?: obj["inputModalities"]
            ?: capabilities?.get("input_modalities") ?: capabilities?.get("inputModalities")
            ?: archObj?.get("input_modalities")
        val rawOutputModalities = obj["output_modalities"] ?: obj["outputModalities"]
            ?: capabilities?.get("output_modalities") ?: capabilities?.get("outputModalities")
            ?: archObj?.get("output_modalities")
        val inputModalities = parseModalities(rawInputModalities).toMutableSet()
        val outputModalities = parseModalities(rawOutputModalities).toMutableSet()
        val archModality = archObj?.stringField("modality")
        val isArchVision = archModality?.contains("image", ignoreCase = true) == true
        val explicitVision = obj.booleanField("supportsImages")
            ?: obj.booleanField("supports_images")
            ?: obj.booleanField("supportsVision")
            ?: obj.booleanField("supports_vision")
            ?: obj.booleanField("multimodal")
            ?: capabilities?.booleanField("vision")
            ?: capabilities?.booleanField("supportsVision")
            ?: capabilities?.booleanField("supports_vision")
        val supportsVision = explicitVision ?: (isArchVision || inputModalities.contains(ModelModality.IMAGE))
        if (supportsVision) inputModalities.add(ModelModality.IMAGE)
        val supportsAudio = obj.booleanField("supportsAudio")
            ?: obj.booleanField("supports_audio")
            ?: capabilities?.booleanField("supportsAudio")
            ?: capabilities?.booleanField("supports_audio")
        if (supportsAudio == true) inputModalities.add(ModelModality.AUDIO)
        val supportsVideo = obj.booleanField("supportsVideo")
            ?: obj.booleanField("supports_video")
            ?: capabilities?.booleanField("supportsVideo")
            ?: capabilities?.booleanField("supports_video")
        if (supportsVideo == true) inputModalities.add(ModelModality.VIDEO)
        if (inputModalities.isEmpty()) inputModalities.add(ModelModality.TEXT)
        if (outputModalities.isEmpty()) outputModalities.add(ModelModality.TEXT)

        // 工具支持探测 (默认 true)
        val supportedFeatures = (obj["supported_features"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }
        val supportedParams = (obj["supported_parameters"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }
        val explicitTools = obj.booleanField("supportsTools")
            ?: obj.booleanField("supports_tools")
            ?: obj.booleanField("tools")
            ?: obj.booleanField("function_calling")
            ?: obj.nestedBoolean("capabilities", "tools")
            ?: obj.nestedBoolean("capabilities", "function_calling")
            ?: if (supportedFeatures != null) (supportedFeatures.contains("tools") || supportedFeatures.contains("function_calling")) else null
            ?: if (supportedParams != null) (supportedParams.contains("tools") || supportedParams.contains("tool_choice")) else null
        val supportsTools = explicitTools ?: true

        // 推理与 Thinking 探测：兼容 CPA/OpenRouter/Anthropic/智谱 的 levels、effort、
        // supported_parameters、supported_features、thinking.type 等多种目录形状。
        val reasoningObj = obj["reasoning"] as? JsonObject
        val supportedReasoningLevels = mutableSetOf<String>()
        val reasoningMappings = linkedMapOf<ReasoningLevel, ReasoningMapping>()
        var discoveredReasoningSupport: Boolean? = null
        val reasoningValues = listOfNotNull(
            obj["reasoning"],
            obj["thinking"],
            obj["reasoning_capability"],
            obj["reasoningCapability"],
            obj["effort"],
            obj["supported_efforts"],
            obj["supportedEfforts"],
            capabilities?.get("reasoning"),
            capabilities?.get("thinking"),
            capabilities?.get("effort"),
            capabilities?.get("supported_efforts"),
            capabilities?.get("supportedEfforts")
        )
        reasoningValues.forEach { value ->
            val result = collectReasoningMetadata(value, protocol, supportedReasoningLevels, reasoningMappings)
            if (result == false || discoveredReasoningSupport == null) discoveredReasoningSupport = result
        }
        listOf("supported_reasoning_levels", "supportedReasoningLevels", "reasoning_levels", "reasoningLevels")
            .mapNotNull { key -> obj[key] }
            .forEach { value ->
                collectReasoningMetadata(value, protocol, supportedReasoningLevels, reasoningMappings)
            }
        supportedParams?.forEach { parameter ->
            if (parameter.contains("reasoning", ignoreCase = true) ||
                parameter.contains("thinking", ignoreCase = true)
            ) discoveredReasoningSupport = true
        }
        if (supportedFeatures?.any { it.contains("reasoning") || it.contains("thinking") } == true) {
            discoveredReasoningSupport = true
        }
        val type = obj.stringField("type", "model_type", "modelType")
        if (type?.contains("reasoning", ignoreCase = true) == true ||
            type?.contains("thinking", ignoreCase = true) == true
        ) discoveredReasoningSupport = true
        val supportsReasoning = obj.booleanField("supportsThinking")
            ?: obj.booleanField("supports_thinking")
            ?: obj.booleanField("supportsReasoning")
            ?: obj.booleanField("supports_reasoning")
            ?: discoveredReasoningSupport
            ?: supportedReasoningLevels.isNotEmpty()

        val defaultReasoningLevel = obj.stringField("default_reasoning_level")
            ?: obj.stringField("defaultReasoningLevel")
            ?: reasoningObj?.stringField("default", "default_level", "defaultLevel", "default_effort", "defaultEffort")
            ?: (obj["thinking"] as? JsonObject)?.stringField("default", "default_level", "defaultLevel", "default_effort", "defaultEffort")

        val thinkingBudget = obj.longField("thinkingBudget")
            ?: obj.longField("thinking_budget")
            ?: reasoningObj?.longField("thinkingBudget")
            ?: reasoningObj?.longField("thinking_budget")
            ?: (obj["thinking"] as? JsonObject)?.findLong("thinkingBudget", "thinking_budget", "budgetTokens", "budget_tokens")

        val minThinkingBudget = obj.longField("minThinkingBudget")
            ?: obj.longField("min_thinking_budget")
            ?: reasoningObj?.longField("minThinkingBudget")
            ?: reasoningObj?.longField("min_thinking_budget")
            ?: (obj["thinking"] as? JsonObject)?.findLong("minThinkingBudget", "min_thinking_budget")
        val normalizedThinkingBudget = thinkingBudget?.takeIf { it in -1L..Int.MAX_VALUE.toLong() }
        val normalizedMinThinkingBudget = minThinkingBudget?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }

        val mimeValue = obj["supportedMimeTypes"] ?: obj["supported_mime_types"]
            ?: capabilities?.get("supportedMimeTypes") ?: capabilities?.get("supported_mime_types")
        val inputMimeTypes = parseMimeTypes(mimeValue)
        val roles = parseRoles(obj["roles"] ?: capabilities?.get("roles"))
        if (ModelRole.IMAGE_GENERATION in roles) outputModalities.add(ModelModality.IMAGE)
        val completeInputMimeTypes = completeMimeTypes(inputMimeTypes, inputModalities)
        val isImageGeneration = (ModelRole.IMAGE_GENERATION in roles && ModelRole.AGENT !in roles) ||
                isLikelyImageGenerationModel(id, displayName)
        val compressionPolicy = parseCompressionPolicy(obj)
        return DiscoveredModelInfo(
            id = id,
            displayName = displayName?.takeIf { it.isNotBlank() && it != id },
            vendor = vendor,
            inputTokenLimit = inputTokens?.takeIf { it > 0L },
            inputTokenLimitSource = if (inputTokens != null && inputTokens > 0L) TokenLimitSource.CATALOG else TokenLimitSource.UNKNOWN,
            outputTokenLimit = outputTokens?.takeIf { it > 0L },
            outputTokenLimitSource = if (outputTokens != null && outputTokens > 0L) TokenLimitSource.CATALOG else TokenLimitSource.UNKNOWN,
            supportsVision = supportsVision,
            supportsTools = supportsTools,
            supportsReasoning = supportsReasoning,
            defaultReasoningLevel = defaultReasoningLevel,
            supportedReasoningLevels = supportedReasoningLevels,
            thinkingBudget = normalizedThinkingBudget,
            minThinkingBudget = normalizedMinThinkingBudget,
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            inputMimeTypes = completeInputMimeTypes,
            roles = roles,
            isImageGeneration = isImageGeneration,
            compressionPolicy = compressionPolicy,
            reasoningMappings = reasoningMappings
        )
    }

    private fun JsonObject.stringField(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull
    }
    private fun JsonObject.longField(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.longOrNull }
    private fun JsonObject.booleanField(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.booleanOrNull }
    private fun JsonObject.nestedString(parentKey: String, childKey: String): String? = (this[parentKey] as? JsonObject)?.stringField(childKey)
    private fun JsonObject.nestedLong(parentKey: String, childKey: String): Long? = (this[parentKey] as? JsonObject)?.longField(childKey)
    private fun JsonObject.nestedBoolean(parentKey: String, childKey: String): Boolean? = (this[parentKey] as? JsonObject)?.booleanField(childKey)

    private fun JsonObject.findLong(vararg keys: String): Long? {
        keys.firstNotNullOfOrNull { longField(it) }?.let { return it }
        listOf("limits", "metadata", "capabilities", "top_provider", "topProvider", "details")
            .forEach { container ->
                (this[container] as? JsonObject)?.findLong(*keys)?.let { return it }
            }
        return null
    }

    private fun collectReasoningMetadata(
        value: JsonElement,
        protocol: ProviderProtocol?,
        levels: MutableSet<String>,
        mappings: MutableMap<ReasoningLevel, ReasoningMapping>
    ): Boolean? {
        return when (value) {
            is JsonPrimitive -> {
                value.booleanOrNull ?: value.contentOrNull?.let { raw ->
                    when (raw.trim().lowercase()) {
                        "enabled", "supported", "true", "on" -> true
                        "disabled", "unsupported", "none", "false", "off" -> false
                        else -> {
                            addReasoningLevel(raw, protocol, null, levels, mappings)
                            null
                        }
                    }
                }
            }

            is JsonArray -> {
                var supported: Boolean? = null
                value.forEach { item ->
                    val discovered = if (item is JsonObject) {
                        item.stringField("effort", "level", "name")?.let { raw ->
                            addReasoningLevel(raw, protocol, item.findLong("budget_tokens", "budgetTokens", "thinking_budget", "thinkingBudget"), levels, mappings)
                        }
                        collectReasoningMetadata(item, protocol, levels, mappings)
                    } else {
                        collectReasoningMetadata(item, protocol, levels, mappings)
                    }
                    if (discovered != null) supported = discovered
                }
                supported
            }

            is JsonObject -> {
                val explicitSupported = value.booleanField("supported", "enabled", "supports_reasoning", "supportsReasoning")
                var supported = explicitSupported
                listOf(
                    "levels", "supported_levels", "supportedLevels", "reasoning_levels", "reasoningLevels",
                    "effort", "supported_efforts", "supportedEfforts", "modes", "supported_modes", "supportedModes", "types"
                ).forEach { key ->
                    value[key]?.let { nested ->
                        val discovered = collectReasoningMetadata(nested, protocol, levels, mappings)
                        if (discovered != null && supported == null) supported = discovered
                    }
                }
                value.forEach { (key, nested) ->
                    val level = parseReasoningLevel(key) ?: return@forEach
                    if ((nested as? JsonPrimitive)?.booleanOrNull == false) return@forEach
                    val budget = (nested as? JsonObject)?.findLong(
                        "budget_tokens", "budgetTokens", "thinking_budget", "thinkingBudget",
                        "max_thinking_tokens", "maxThinkingTokens"
                    ) ?: (nested as? JsonPrimitive)?.longOrNull
                    addReasoningLevel(key, protocol, budget, levels, mappings)
                    if (level != ReasoningLevel.OFF && supported == null) supported = true
                }
                value.stringField("type")?.lowercase()?.let { type ->
                    when {
                        supported != null -> Unit
                        type == "enabled" || type == "adaptive" -> supported = true
                        type == "disabled" || type == "none" -> supported = false
                    }
                    if (type == "adaptive") addReasoningLevel("adaptive", protocol, null, levels, mappings)
                }
                supported
            }
        }
    }

    private fun addReasoningLevel(
        rawLevel: String,
        protocol: ProviderProtocol?,
        budget: Long?,
        levels: MutableSet<String>,
        mappings: MutableMap<ReasoningLevel, ReasoningMapping>
    ) {
        val level = parseReasoningLevel(rawLevel) ?: return
        levels += rawLevel
        val mapping = when {
            budget != null && budget in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ->
                ReasoningMapping("budget_tokens", JsonPrimitive(budget.toInt()))

            level == ReasoningLevel.OFF -> ReasoningMapping("disabled")
            level == ReasoningLevel.ADAPTIVE && protocol == ProviderProtocol.ANTHROPIC_MESSAGES ->
                ReasoningMapping("adaptive")

            protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT ->
                ReasoningMapping("native_level", JsonPrimitive(rawLevel.trim().lowercase()))

            else -> ReasoningMapping("effort", JsonPrimitive(rawLevel.trim().lowercase()))
        }
        mappings.putIfAbsent(level, mapping)
    }

    private fun parseReasoningLevel(value: String): ReasoningLevel? {
        return when (value.lowercase().filterNot { it == '-' || it == '_' || it.isWhitespace() }) {
            "off", "none" -> ReasoningLevel.OFF
            "low", "minimal" -> ReasoningLevel.LOW
            "medium", "med", "balanced" -> ReasoningLevel.MEDIUM
            "high" -> ReasoningLevel.HIGH
            "xhigh", "extrahigh", "ultra", "extreme" -> ReasoningLevel.X_HIGH
            "max", "maximum" -> ReasoningLevel.MAX
            "adaptive" -> ReasoningLevel.ADAPTIVE
            "auto" -> ReasoningLevel.AUTO
            else -> null
        }
    }

    private fun parseModalities(value: JsonElement?): Set<ModelModality> {
        return (value as? JsonArray).orEmpty().mapNotNull { item ->
            when ((item as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()) {
                "text" -> ModelModality.TEXT
                "image", "images" -> ModelModality.IMAGE
                "audio" -> ModelModality.AUDIO
                "video" -> ModelModality.VIDEO
                "document", "file", "pdf" -> ModelModality.DOCUMENT
                else -> null
            }
        }.toSet()
    }

    private fun parseMimeTypes(value: JsonElement?): List<String> {
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            is JsonObject -> value.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys.toList()
            else -> emptyList()
        }.map(String::lowercase).distinct()
    }

    private fun completeMimeTypes(
        declared: List<String>,
        modalities: Set<ModelModality>
    ): List<String> {
        val result = declared.toMutableSet()
        if (ModelModality.IMAGE in modalities && result.none { it.startsWith("image/") }) {
            result += listOf("image/png", "image/jpeg", "image/webp")
        }
        if (ModelModality.AUDIO in modalities && result.none { it.startsWith("audio/") }) {
            result += "audio/wav"
        }
        if (ModelModality.VIDEO in modalities && result.none { it.startsWith("video/") }) {
            result += listOf("video/mp4", "video/webm")
        }
        if (ModelModality.DOCUMENT in modalities && "application/pdf" !in result) {
            result += "application/pdf"
        }
        return result.toList().sorted()
    }

    private fun parseRoles(value: JsonElement?): Set<ModelRole> {
        return (value as? JsonArray).orEmpty().mapNotNull { item ->
            when ((item as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.replace('-', '_')) {
                "agent" -> ModelRole.AGENT
                "command" -> ModelRole.COMMAND
                "tab" -> ModelRole.TAB
                "image_generation" -> ModelRole.IMAGE_GENERATION
                "mquery" -> ModelRole.MQUERY
                "web_search" -> ModelRole.WEB_SEARCH
                "commit_message" -> ModelRole.COMMIT_MESSAGE
                "audio_transcription" -> ModelRole.AUDIO_TRANSCRIPTION
                else -> null
            }
        }.toSet()
    }

    private fun isLikelyImageGenerationModel(id: String, displayName: String?): Boolean {
        val text = "$id ${displayName.orEmpty()}".lowercase()
        return listOf(
            "flash-image", "imagen", "nano-banana", "image-generation", "image_generation",
            "text-to-image", "text2image", "image-to-image", "image2image", "text-to-video", "text2video",
            "dall-e", "dalle",
            "gpt-image", "gpt_image", "flux", "midjourney", "sdxl", "stable-diffusion",
            "stable_image", "recraft", "kolors", "ideogram", "kling", "cogview",
            "grok-imagine", "imagine", "hunyuan-image", "hunyuan-video", "doubao-image", "wanx",
            "veo", "sora"
        ).any(text::contains) || Regex("(?:image|imagen)[-_ ]?(?:\\d|v\\d)").containsMatchIn(text)
    }

    private fun parseCompressionPolicy(model: JsonObject): ModelCompressionPolicy? {
        val raw = model["modelExperiments"]?.jsonObject
            ?.get("experiments")?.jsonObject
            ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")?.jsonObject
            ?.get("stringValue")?.jsonPrimitive?.contentOrNull
            ?: return null
        val payload = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        // byok 只把包含完整核心阈值的实验配置提升为默认策略；缺少
        // enabled/max_token_limit 等关键字段时返回 null，不能用 Studio 默认值伪造。
        if (listOf("enabled", "token_threshold", "max_token_limit", "max_output_tokens")
                .any { key -> payload[key] == null }
        ) return null
        fun bool(key: String, fallback: Boolean) = payload[key]?.jsonPrimitive?.booleanOrNull ?: fallback
        fun long(key: String, fallback: Long) = payload[key]?.jsonPrimitive?.longOrNull
            ?: payload[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: fallback
        fun string(key: String, fallback: String) = payload[key]?.jsonPrimitive?.contentOrNull ?: fallback
        val retry = payload["retry_config"]?.jsonObject
        val policy = ModelCompressionPolicy(
            enabled = bool("enabled", true),
            checkpointModel = string("checkpoint_model", ModelCompressionPolicy.DEFAULT_CHECKPOINT_MODEL),
            strategy = string("strategy", ModelCompressionPolicy.DEFAULT_STRATEGY),
            maxOverheadRatio = string("max_overhead_ratio", "0.30"),
            movingWindowSize = string("moving_window_size", "1"),
            useLastPlannerModel = bool("use_last_planner_model", false),
            isSync = bool("is_sync", false),
            maxUserRequests = long("max_user_requests", 10L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            includeLastUserMessage = bool("include_last_user_message", false),
            includeConversationLog = bool("include_conversation_log", true),
            includeRunningTaskSnapshots = bool("include_running_task_snapshots", true),
            includeSubagentSnapshots = bool("include_subagent_snapshots", true),
            includeArtifactSnapshots = bool("include_artifact_snapshots", true),
            retryConfig = CustomModelCheckpointRetryConfig(
                maxRetries = retry?.let { it["max_retries"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() } ?: 0,
                initialSleepDurationMs = retry?.let { it["initial_sleep_duration_ms"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() } ?: 1_000,
                exponentialMultiplier = retry?.let { it["exponential_multiplier"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() } ?: 2,
                includeErrorFeedback = retry?.let { it["include_error_feedback"]?.jsonPrimitive?.booleanOrNull } ?: false
            ),
            tokenThreshold = long("token_threshold", 50_000L),
            maxTokenLimit = long("max_token_limit", 128_000L),
            maxOutputTokens = long("max_output_tokens", 16_384L)
        )
        return runCatching { policy.validate("catalog checkpointer"); policy }.getOrNull()
    }

    private fun inferVendorFromId(id: String): String? {
        val prefix = id.substringBefore('/', "").trim().lowercase()
        if (prefix.isNotEmpty() && prefix != id.lowercase()) {
            return when {
                prefix.contains("openai") -> "OpenAI"
                prefix.contains("anthropic") -> "Anthropic"
                prefix.contains("google") -> "Google"
                prefix.contains("deepseek") -> "DeepSeek"
                prefix.contains("meta") || prefix.contains("llama") -> "Meta"
                prefix.contains("qwen") || prefix.contains("alibaba") -> "Qwen"
                prefix.contains("mistral") -> "Mistral"
                prefix.contains("moonshot") || prefix.contains("kimi") -> "Moonshot"
                prefix.contains("zhipu") || prefix.contains("glm") || prefix.contains("bigmodel") -> "Zhipu AI"
                prefix.contains("minimax") -> "MiniMax"
                prefix.contains("01-ai") || prefix.contains("yi") -> "01.AI"
                prefix.contains("x-ai") || prefix.contains("grok") || prefix.contains("xai") -> "xAI"
                prefix.contains("cohere") -> "Cohere"
                prefix.contains("baichuan") -> "Baichuan"
                prefix.contains("tencent") || prefix.contains("hunyuan") -> "Tencent"
                prefix.contains("bytedance") || prefix.contains("doubao") -> "ByteDance"
                else -> prefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
        val lowerId = id.lowercase()
        return when {
            lowerId.startsWith("gpt-") || lowerId.startsWith("o1") || lowerId.startsWith("o3") || lowerId.startsWith("chatgpt") || lowerId.startsWith("text-embedding-") -> "OpenAI"
            lowerId.startsWith("claude-") -> "Anthropic"
            lowerId.startsWith("gemini-") || lowerId.startsWith("gemma-") -> "Google"
            lowerId.startsWith("deepseek-") -> "DeepSeek"
            lowerId.startsWith("grok-") -> "xAI"
            lowerId.startsWith("qwen") || lowerId.startsWith("qwq") -> "Alibaba"
            lowerId.startsWith("moonshot-") || lowerId.startsWith("kimi-") -> "Moonshot"
            lowerId.startsWith("glm-") || lowerId.startsWith("chatglm") -> "Zhipu AI"
            lowerId.startsWith("minimax") || lowerId.startsWith("abab") -> "MiniMax"
            lowerId.startsWith("hunyuan") -> "Tencent"
            lowerId.startsWith("doubao") -> "ByteDance"
            lowerId.startsWith("ernie") -> "Baidu"
            lowerId.startsWith("mistral-") || lowerId.startsWith("codestral-") || lowerId.startsWith("pixtral-") -> "Mistral"
            lowerId.startsWith("llama-") || lowerId.startsWith("llama3") || lowerId.startsWith("llama2") -> "Meta"
            else -> null
        }
    }

    private fun inferVendorFromGroup(group: String?): String? {
        return when (group?.lowercase()?.trim()) {
            "claude", "anthropic" -> "Anthropic"
            "gpt", "openai" -> "OpenAI"
            "gemini", "google" -> "Google"
            "deepseek" -> "DeepSeek"
            "grok", "xai" -> "xAI"
            "kimi", "moonshot" -> "Moonshot"
            "glm", "zhipu" -> "Zhipu AI"
            "qwen", "alibaba" -> "Alibaba"
            "minimax" -> "MiniMax"
            else -> null
        }
    }
}


