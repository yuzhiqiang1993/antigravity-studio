package com.yuzhiqiang.antigravity.proxy.parser

import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralMessage
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 将 Antigravity/Gemini 及兼容请求转换为中立请求模型。 */
object AntigravityRequestParser {
    private const val MODEL_PREFIX = "models/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 从原始 JSON 中提取模型 ID，失败时保留明确的 4xx 原因。 */
    fun extractModelId(rawJson: String): Result<String> {
        val root = parseObject(rawJson).getOrElse { return Result.failure(it) }
        return extractModelId(root)
    }

    /** 从顶层对象及其 request envelope 中提取模型 ID。 */
    fun extractModelId(root: JsonObject): Result<String> {
        val request = root.objectValue("request")
        val model = modelId(root) ?: request?.let(::modelId)
        val normalizedModel = model?.trim()?.takeIf { it.isNotEmpty() }?.removePrefix(MODEL_PREFIX)
        return if (normalizedModel != null) {
            Result.success(normalizedModel)
        } else {
            Result.failure(IllegalArgumentException("Missing model ID in Antigravity request"))
        }
    }

    /**
     * 解析请求。非法 JSON、非对象 JSON 和缺失模型均通过 `Result.failure` 返回，避免把坏请求
     * 静默降级为空请求并继续访问上游。
     */
    fun parse(
        rawJson: String,
        targetUpstreamModelId: String = "",
        fallbackOriginalModelId: String? = null
    ): Result<NeutralChatRequest> {
        val root = parseObject(rawJson).getOrElse { return Result.failure(it) }
        return parse(root, targetUpstreamModelId, fallbackOriginalModelId)
    }

    /** 复用已解析的请求对象，避免路由识别和完整请求转换各解析一次大型 JSON。 */
    fun parse(
        root: JsonObject,
        targetUpstreamModelId: String = "",
        fallbackOriginalModelId: String? = null
    ): Result<NeutralChatRequest> {
        val originalModel = extractModelId(root).getOrElse { error ->
            val fallback = fallbackOriginalModelId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.removePrefix(MODEL_PREFIX)
            if (fallback != null && error.message.orEmpty().startsWith("Missing model ID")) {
                fallback
            } else {
                return Result.failure(error)
            }
        }
        val request = root.objectValue("request") ?: root
        val modelForUpstream = targetUpstreamModelId.trim().ifEmpty { originalModel }

        val pendingToolCalls = PendingToolCalls()
        val systemPrompt = readSystemPrompt(root, request)
        val messages = readMessages(root, request, pendingToolCalls)
        val tools = readTools(root, request)
        val generation = firstObject(request, root, "generationConfig", "generation_config")
        val stream = firstBoolean(request, root, "stream") ?: true

        return Result.success(
            NeutralChatRequest(
                originalModelId = originalModel,
                targetUpstreamModelId = modelForUpstream.removePrefix(MODEL_PREFIX),
                systemPrompt = systemPrompt,
                messages = messages,
                tools = tools,
                temperature = firstFloat(generation, request, root, "temperature"),
                maxTokens = firstInt(generation, request, root, "maxOutputTokens", "max_output_tokens", "max_tokens"),
                topP = firstFloat(generation, request, root, "topP", "top_p"),
                topK = firstInt(generation, request, root, "topK", "top_k"),
                stream = stream,
                extraBody = readExtraBody(root, request),
                reasoningBudgetTokens = readReasoningBudget(root, request),
                reasoningLevel = readReasoningLevel(root, request),
                outputModalities = readOutputModalities(root, request, generation),
                imageGenerationConfig = firstElement(
                    generation ?: request,
                    root,
                    "imageConfig",
                    "image_config"
                )
            )
        )
    }

    internal fun parseObject(rawJson: String): Result<JsonObject> {
        return try {
            val element = json.parseToJsonElement(rawJson)
            if (element is JsonObject) {
                Result.success(element)
            } else {
                Result.failure(IllegalArgumentException("Antigravity request must be a JSON object"))
            }
        } catch (error: Exception) {
            Result.failure(
                IllegalArgumentException(
                    "Failed to parse Antigravity JSON request: ${error.message}",
                    error
                )
            )
        }
    }

    private fun modelId(value: JsonObject): String? {
        return listOf(
            "model",
            "requestedModel",
            "requested_model",
            "planModel",
            "plan_model",
            "modelId",
            "model_id"
        ).firstNotNullOfOrNull { value.stringValue(it) }
    }

    private fun readSystemPrompt(root: JsonObject, payload: JsonObject): String? {
        val instruction = firstElement(
            payload,
            root,
            "systemInstruction",
            "system_instruction",
            "instructions"
        )
        val parts = instruction?.jsonObjectOrNull()?.arrayValue("parts")
            ?: instruction?.arrayOrNull()
        val fromParts = parts
            ?.mapNotNull { it.jsonObjectOrNull()?.stringValue("text") ?: it.stringOrNull() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
        return fromParts?.takeIf { it.isNotEmpty() }
            ?: instruction?.stringOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun readMessages(
        root: JsonObject,
        payload: JsonObject,
        pendingToolCalls: PendingToolCalls
    ): List<NeutralMessage> {
        val input = firstElement(payload, root, "input")
        if (input != null) return readResponsesInput(input, pendingToolCalls)
        val array = firstArray(payload, root, "contents", "messages") ?: return emptyList()
        val messages = mutableListOf<NeutralMessage>()
        array.forEachIndexed { messageIndex, item ->
            val message = item.jsonObjectOrNull() ?: return@forEachIndexed
            val role = roleOf(message.stringValue("role"))
            val blocks = mutableListOf<NeutralContent>()
            val toolResults = mutableListOf<NeutralContent.ToolResult>()
            val parts = message.arrayValue("parts")
            if (parts != null) {
                parts.forEachIndexed { partIndex, part ->
                    readGeminiPart(part, messageIndex, partIndex, pendingToolCalls, blocks, toolResults)
                }
            } else {
                readOpenAiMessage(message, messageIndex, pendingToolCalls, blocks, toolResults)
            }

            if (role == NeutralRole.SYSTEM) {
                if (blocks.isNotEmpty()) {
                    messages.add(NeutralMessage(role, blocks))
                }
            } else if (blocks.isNotEmpty() || toolResults.isEmpty()) {
                messages.add(NeutralMessage(role, blocks))
            }
            toolResults.forEach { result ->
                messages.add(
                    NeutralMessage(
                        role = NeutralRole.TOOL,
                        contents = listOf(result),
                        toolCallId = result.toolCallId,
                        name = result.functionName
                    )
                )
            }
        }
        return messages
    }

    private fun readResponsesInput(
        input: JsonElement,
        pendingToolCalls: PendingToolCalls
    ): List<NeutralMessage> {
        if (input is JsonPrimitive) {
            return input.contentOrNull?.takeIf { it.isNotBlank() }?.let { text ->
                listOf(NeutralMessage(NeutralRole.USER, listOf(NeutralContent.Text(text))))
            }.orEmpty()
        }
        val array = input as? JsonArray ?: return emptyList()
        val messages = mutableListOf<NeutralMessage>()
        array.forEachIndexed { index, element ->
            val item = element.jsonObjectOrNull() ?: return@forEachIndexed
            when (item.stringValue("type")?.lowercase()) {
                "message" -> {
                    val role = roleOf(item.stringValue("role"))
                    val contents = readResponsesMessageContents(item)
                    if (contents.isNotEmpty()) messages += NeutralMessage(role, contents)
                }

                "function_call" -> {
                    val name = item.stringValue("name") ?: return@forEachIndexed
                    val id = item.stringValue("call_id", "id")
                        ?: pendingToolCalls.nextGeneratedId(name, index, 0)
                    val arguments = item.stringValue("arguments") ?: "{}"
                    pendingToolCalls.add(name, id)
                    messages += NeutralMessage(
                        NeutralRole.ASSISTANT,
                        listOf(NeutralContent.ToolCall(id, name, arguments))
                    )
                }

                "function_call_output" -> {
                    val id = item.stringValue("call_id", "tool_call_id") ?: return@forEachIndexed
                    val content = item.elementValue("output", "content")?.asJsonText() ?: "{}"
                    messages += NeutralMessage(
                        NeutralRole.TOOL,
                        listOf(NeutralContent.ToolResult(id, content = content)),
                        toolCallId = id
                    )
                }

                else -> if (item.stringValue("role") != null) {
                    val blocks = mutableListOf<NeutralContent>()
                    val results = mutableListOf<NeutralContent.ToolResult>()
                    readOpenAiMessage(item, index, pendingToolCalls, blocks, results)
                    if (blocks.isNotEmpty()) {
                        messages += NeutralMessage(roleOf(item.stringValue("role")), blocks)
                    }
                    results.forEach { result ->
                        messages += NeutralMessage(
                            NeutralRole.TOOL,
                            listOf(result),
                            toolCallId = result.toolCallId,
                            name = result.functionName
                        )
                    }
                }
            }
        }
        return messages
    }

    private fun readResponsesMessageContents(message: JsonObject): List<NeutralContent> {
        val content = message.elementValue("content") ?: return emptyList()
        if (content is JsonPrimitive) {
            return content.contentOrNull?.let { listOf(NeutralContent.Text(it)) }.orEmpty()
        }
        val parts = content as? JsonArray ?: return emptyList()
        return parts.mapNotNull { element ->
            val part = element.jsonObjectOrNull() ?: return@mapNotNull null
            when (part.stringValue("type")?.lowercase()) {
                "input_text", "output_text", "text" -> {
                    part.stringValue("text")?.let(NeutralContent::Text)
                }

                "input_image", "image_url" -> {
                    val imageUrl = part.stringValue("image_url")
                        ?: part.objectValue("image_url")?.stringValue("url")
                    parseDataUrl(imageUrl)?.let { (mimeType, data) -> NeutralContent.Image(mimeType, data) }
                }

                else -> null
            }
        }
    }

    private fun readGeminiPart(
        part: JsonElement,
        messageIndex: Int,
        partIndex: Int,
        pendingToolCalls: PendingToolCalls,
        blocks: MutableList<NeutralContent>,
        toolResults: MutableList<NeutralContent.ToolResult>
    ) {
        val partObject = part.jsonObjectOrNull() ?: return
        val text = partObject.stringValue("text")
        val thought = partObject.booleanValue("thought") == true
        val signature = partObject.stringValue("thoughtSignature", "thought_signature")
        if (text != null || thought || signature != null) {
            if (thought || signature != null) {
                val previous = blocks.lastOrNull() as? NeutralContent.Thinking
                if (previous != null) {
                    blocks[blocks.lastIndex] = previous.copy(
                        text = previous.text + text.orEmpty(),
                        signature = signature ?: previous.signature
                    )
                } else {
                    blocks.add(NeutralContent.Thinking(text.orEmpty(), signature))
                }
            } else {
                blocks.add(NeutralContent.Text(text.orEmpty()))
            }
            return
        }

        val inline = partObject.objectValue("inlineData", "inline_data")
        if (inline != null) {
            blocks.add(
                NeutralContent.Image(
                    mimeType = inline.stringValue("mimeType", "mime_type") ?: "application/octet-stream",
                    base64Data = inline.stringValue("data") ?: ""
                )
            )
            return
        }

        val functionCall = partObject.objectValue("functionCall", "function_call")
        if (functionCall != null) {
            val name = functionCall.stringValue("name") ?: ""
            val id = functionCall.stringValue("id")?.takeIf { it.isNotEmpty() }
                ?: pendingToolCalls.nextGeneratedId(name, messageIndex, partIndex)
            val args = functionCall.elementValue("args", "arguments")?.asJsonText() ?: "{}"
            pendingToolCalls.add(name, id)
            blocks.add(NeutralContent.ToolCall(id, name, args))
            return
        }

        val functionResponse = partObject.objectValue("functionResponse", "function_response")
        if (functionResponse != null) {
            val name = functionResponse.stringValue("name") ?: ""
            val explicitId = functionResponse.stringValue("id", "toolCallId", "tool_call_id")
            val id = pendingToolCalls.resolve(name, explicitId, messageIndex, partIndex)
            val content = functionResponse.elementValue("response", "content")?.asJsonText() ?: "{}"
            toolResults.add(NeutralContent.ToolResult(id, name.takeIf { it.isNotEmpty() }, content))
        }
    }

    private fun readOpenAiMessage(
        message: JsonObject,
        messageIndex: Int,
        pendingToolCalls: PendingToolCalls,
        blocks: MutableList<NeutralContent>,
        toolResults: MutableList<NeutralContent.ToolResult>
    ) {
        val role = message.stringValue("role")?.lowercase()
        val toolCallId = message.stringValue("tool_call_id", "toolCallId")
        val content = message.elementValue("content")
        if (content is JsonPrimitive) {
            content.contentOrNull?.let { blocks.add(NeutralContent.Text(it)) }
        } else if (content is JsonArray) {
            content.forEach { part ->
                val partObject = part.jsonObjectOrNull() ?: return@forEach
                when (partObject.stringValue("type")?.lowercase()) {
                    "text", "input_text", "output_text" -> {
                        partObject.stringValue("text")?.let { blocks.add(NeutralContent.Text(it)) }
                    }

                    "image_url", "input_image" -> {
                        val imageUrl = partObject.objectValue("image_url", "imageUrl")?.stringValue("url")
                        parseDataUrl(imageUrl)?.let { blocks.add(NeutralContent.Image(it.first, it.second)) }
                    }
                }
            }
        }

        val toolCalls = message.arrayValue("tool_calls", "toolCalls")
        toolCalls?.forEachIndexed { callIndex, call ->
            val callObject = call.jsonObjectOrNull() ?: return@forEachIndexed
            val function = callObject.objectValue("function") ?: callObject
            val name = function.stringValue("name") ?: ""
            val id = callObject.stringValue("id")?.takeIf { it.isNotEmpty() }
                ?: pendingToolCalls.nextGeneratedId(name, messageIndex, callIndex)
            val arguments = function.elementValue("arguments", "args")?.asJsonText() ?: "{}"
            pendingToolCalls.add(name, id)
            blocks.add(NeutralContent.ToolCall(id, name, arguments))
        }

        if (role == "tool" || toolCallId != null) {
            val toolContent = content?.asJsonText() ?: "{}"
            val name = message.stringValue("name")
            val id = pendingToolCalls.resolve(name.orEmpty(), toolCallId, messageIndex, 0)
            toolResults.add(NeutralContent.ToolResult(id, name, toolContent))
            blocks.clear()
        }
    }

    private fun readTools(root: JsonObject, payload: JsonObject): List<NeutralToolDefinition> {
        val array = firstArray(payload, root, "tools") ?: return emptyList()
        val tools = mutableListOf<NeutralToolDefinition>()
        array.forEach { element ->
            val tool = element.jsonObjectOrNull() ?: return@forEach
            val declarations = tool.arrayValue("functionDeclarations", "function_declarations")
            if (declarations != null) {
                declarations.forEach { declaration ->
                    addToolDefinition(declaration.jsonObjectOrNull(), tools)
                }
            } else {
                val function = tool.objectValue("function") ?: tool
                addToolDefinition(function, tools)
            }
        }
        return tools.distinctBy { it.name }
    }

    private fun readExtraBody(root: JsonObject, payload: JsonObject): Map<String, JsonElement> {
        return firstElement(payload, root, "extraBody", "extra_body")
            ?.jsonObjectOrNull()
            ?.toMap()
            ?: emptyMap()
    }

    private fun readReasoningBudget(root: JsonObject, payload: JsonObject): Int? {
        val direct = firstInt(
            payload,
            root,
            "reasoningBudgetTokens",
            "reasoning_budget_tokens",
            "thinkingBudget",
            "thinking_budget",
            "budgetTokens",
            "budget_tokens"
        )
        if (direct != null) return direct
        val generationConfig = firstObject(payload, root, "generationConfig", "generation_config")
        val thinkingConfig = firstObject(
            payload,
            root,
            "thinkingConfig",
            "thinking_config"
        ) ?: generationConfig?.objectValue("thinkingConfig", "thinking_config")
        return thinkingConfig?.let { config ->
            firstInt(config, config, "thinkingBudget", "thinking_budget", "budgetTokens", "budget_tokens")
        }
    }

    private fun readReasoningLevel(root: JsonObject, payload: JsonObject): ReasoningLevel? {
        val generationConfig = firstObject(payload, root, "generationConfig", "generation_config")
        val thinkingConfig = firstObject(payload, root, "thinkingConfig", "thinking_config")
            ?: generationConfig?.objectValue("thinkingConfig", "thinking_config")
        val reasoningObject = firstObject(payload, root, "reasoning")
        val value = firstElement(
            payload,
            root,
            "reasoningLevel",
            "reasoning_level",
            "thinkingLevel",
            "thinking_level",
            "reasoning_effort",
            "defaultReasoningLevel",
            "default_reasoning_level"
        )?.stringOrNull()
            ?: thinkingConfig?.stringValue("thinkingLevel", "thinking_level")
            ?: reasoningObject?.stringValue("effort")
        val normalizedValue = value?.lowercase() ?: return null
        return when (normalizedValue.replace('-', '_')) {
            "off", "none", "disabled" -> ReasoningLevel.OFF
            "low" -> ReasoningLevel.LOW
            "medium" -> ReasoningLevel.MEDIUM
            "high" -> ReasoningLevel.HIGH
            "x_high", "xhigh" -> ReasoningLevel.X_HIGH
            "max" -> ReasoningLevel.MAX
            "adaptive" -> ReasoningLevel.ADAPTIVE
            "auto" -> ReasoningLevel.AUTO
            else -> null
        }
    }

    private fun readOutputModalities(
        root: JsonObject,
        payload: JsonObject,
        generation: JsonObject?
    ): Set<ModelModality> {
        val raw = firstElement(
            generation ?: payload,
            payload,
            "responseModalities",
            "response_modalities",
            "outputModalities",
            "output_modalities"
        ) ?: root["responseModalities"]
        val values = when (raw) {
            is JsonArray -> raw.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonPrimitive -> raw.contentOrNull?.split(',').orEmpty()
            else -> emptyList()
        }
        return values.mapNotNull { value ->
            when (value.trim().lowercase()) {
                "text" -> ModelModality.TEXT
                "image", "images" -> ModelModality.IMAGE
                "audio" -> ModelModality.AUDIO
                "video" -> ModelModality.VIDEO
                "document", "file" -> ModelModality.DOCUMENT
                else -> null
            }
        }.toSet()
    }

    private fun addToolDefinition(value: JsonObject?, target: MutableList<NeutralToolDefinition>) {
        if (value == null) return
        val name = value.stringValue("name")?.takeIf { it.isNotEmpty() } ?: return
        val description = value.stringValue("description") ?: ""
        val parameters = value.elementValue("parameters", "parameters_schema", "input_schema")
            ?: JsonObject(emptyMap())
        target.add(NeutralToolDefinition(name, description, parameters))
    }

    private fun roleOf(value: String?): NeutralRole {
        return when (value?.lowercase()) {
            "system" -> NeutralRole.SYSTEM
            "assistant", "model" -> NeutralRole.ASSISTANT
            "tool", "function" -> NeutralRole.TOOL
            else -> NeutralRole.USER
        }
    }

    private fun parseDataUrl(value: String?): Pair<String, String>? {
        if (value == null || !value.startsWith("data:")) return null
        val separator = value.indexOf(",")
        if (separator <= 5 || !value.substring(0, separator).contains(";base64", ignoreCase = true)) return null
        return value.substring(5, separator).substringBefore(";") to value.substring(separator + 1)
    }

    private class PendingToolCalls {
        private val idsByName = linkedMapOf<String, ArrayDeque<String>>()

        fun add(name: String, id: String) {
            idsByName.getOrPut(name) { ArrayDeque() }.addLast(id)
        }

        fun resolve(name: String, explicitId: String?, messageIndex: Int, partIndex: Int): String {
            if (!explicitId.isNullOrBlank()) {
                idsByName[name]?.remove(explicitId)
                return explicitId
            }
            val queue = idsByName[name]
            return if (queue != null && queue.isNotEmpty()) {
                queue.removeFirst()
            } else {
                nextGeneratedId(name, messageIndex, partIndex)
            }
        }

        fun nextGeneratedId(_name: String, messageIndex: Int, partIndex: Int): String {
            // 与 byok 保持稳定的宿主侧 ID 形态；消息/part 坐标已经足够保证
            // 同一请求内唯一，且后续无 ID 的 functionResponse 可按名称队列配对。
            return "call_${messageIndex}_${partIndex}"
        }
    }

    private fun firstElement(first: JsonObject, second: JsonObject, vararg keys: String): JsonElement? {
        return keys.firstNotNullOfOrNull { key -> first[key] ?: second[key] }
    }

    private fun firstObject(first: JsonObject, second: JsonObject, vararg keys: String): JsonObject? {
        return keys.firstNotNullOfOrNull { key -> first.objectValue(key) ?: second.objectValue(key) }
    }

    private fun firstArray(first: JsonObject, second: JsonObject, vararg keys: String): JsonArray? {
        return keys.firstNotNullOfOrNull { key -> first.arrayValue(key) ?: second.arrayValue(key) }
    }

    private fun firstBoolean(first: JsonObject, second: JsonObject, key: String): Boolean? {
        return first.booleanValue(key) ?: second.booleanValue(key)
    }

    private fun firstFloat(vararg objectsAndKeys: Any?): Float? {
        val keyNames = objectsAndKeys.filterIsInstance<String>()
        val objects = objectsAndKeys.filterIsInstance<JsonObject>()
        return keyNames.firstNotNullOfOrNull { key -> objects.firstNotNullOfOrNull { it.floatValue(key) } }
    }

    private fun firstInt(vararg objectsAndKeys: Any?): Int? {
        val keyNames = objectsAndKeys.filterIsInstance<String>()
        val objects = objectsAndKeys.filterIsInstance<JsonObject>()
        return keyNames.firstNotNullOfOrNull { key -> objects.firstNotNullOfOrNull { it.intValue(key) } }
    }

    private fun JsonObject.objectValue(vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { this[it]?.jsonObjectOrNull() }

    private fun JsonObject.arrayValue(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { this[it]?.arrayOrNull() }

    private fun JsonObject.stringValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull }

    private fun JsonObject.booleanValue(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.booleanOrNull }

    private fun JsonObject.floatValue(key: String): Float? = this[key]?.jsonPrimitive?.floatOrNull

    private fun JsonObject.intValue(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.elementValue(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { this[it] }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.arrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement.asJsonText(): String {
        return when (this) {
            is JsonPrimitive -> contentOrNull ?: toString()
            else -> toString()
        }
    }
}
