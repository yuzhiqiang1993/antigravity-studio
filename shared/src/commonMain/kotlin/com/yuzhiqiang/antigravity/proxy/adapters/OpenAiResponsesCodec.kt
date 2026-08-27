package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralMessage
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** OpenAI Responses 上游协议的请求编码与响应解码。 */
object OpenAiResponsesCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** 每个 Responses 流请求独立维护工具调用聚合状态。 */
    class StreamState {
        private val tools = linkedMapOf<Int, ToolState>()

        internal fun tool(index: Int): ToolState {
            return tools.getOrPut(index) { ToolState() }
        }

        internal fun hasToolCalls(): Boolean {
            return tools.isNotEmpty()
        }
    }

    internal data class ToolState(
        var id: String? = null,
        var name: String? = null,
        var sawArgumentDelta: Boolean = false,
        var metadataEmitted: Boolean = false,
        var emittedId: String? = null,
        var emittedName: String? = null,
        var closed: Boolean = false
    )

    fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = request.tools.map { tool ->
            val schema = parseJsonObject(tool.parametersJson, "Responses tool ${tool.name} parameters")
            if (schema.isFailure) {
                return Result.failure(
                    schema.exceptionOrNull() ?: IllegalArgumentException("工具参数不是有效 JSON 对象")
                )
            }
            tool to (normalizeJsonSchema(schema.getOrThrow()) as JsonObject)
        }
        val input = request.messages.flatMap(::messageItems)
        val body = buildJsonObject {
            put("model", request.targetUpstreamModelId)
            put("input", buildJsonArray { input.forEach(::add) })
            put("stream", request.stream)
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("instructions", it) }
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_output_tokens", it) }
            request.topP?.let { put("top_p", it) }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { (tool, schema) ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", schema)
                        })
                    }
                })
            }
            request.reasoningMapping?.let { mapping ->
                when (mapping.kind.lowercase()) {
                    "effort" -> ReasoningMappingSupport.mappingValueAsString(mapping)?.let { effort ->
                        put("reasoning", buildJsonObject { put("effort", effort) })
                    } ?: return@buildJsonObject

                    "disabled" -> Unit
                    else -> Unit
                }
            }
        }
        val mapping = request.reasoningMapping
        if (mapping != null && mapping.kind.lowercase() != "disabled" &&
            (mapping.kind.lowercase() != "effort" || ReasoningMappingSupport.mappingValueAsString(mapping) == null)
        ) {
            return Result.failure(IllegalArgumentException("OpenAI Responses 只支持 effort reasoning mapping"))
        }
        return Result.success(ProviderAdapter.mergeSafeExtraBody(body, request))
    }

    fun parseNonStreamingResponse(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val error = responseError(root)
            if (error != null) return Result.success(listOf(error))
            val status = root.stringValue("status")?.lowercase() ?: "completed"
            if (status == "failed") {
                return Result.success(listOf(NeutralStreamChunk.Error(responseErrorMessage(root), 502)))
            }
            val output = root["output"]?.jsonArray ?: JsonArray(emptyList())
            val chunks = mutableListOf<NeutralStreamChunk>()
            var hasToolCall = false
            output.forEachIndexed { index, itemElement ->
                val item = itemElement as? JsonObject ?: return@forEachIndexed
                when (item.stringValue("type")?.lowercase()) {
                    "message" -> appendMessageOutput(item, chunks)
                    "reasoning" -> appendReasoningOutput(item, chunks)
                    "function_call" -> {
                        hasToolCall = true
                        chunks += NeutralStreamChunk.ToolCallDelta(
                            index = item.intValue("output_index", "index") ?: index,
                            id = item.stringValue("call_id", "id"),
                            name = item.stringValue("name"),
                            argsText = item.stringValue("arguments") ?: "{}"
                        )
                    }
                }
            }
            chunks += NeutralStreamChunk.Completed(normalizeFinishReason(root, status, hasToolCall), parseUsage(root))
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("解析 OpenAI Responses 响应失败：${error.message}", error))
        }
    }

    fun parseStreamEvent(data: String, state: StreamState): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val error = responseError(root)
            if (error != null) return Result.success(listOf(error))
            when (root.stringValue("type")?.lowercase()) {
                "response.output_text.delta" -> {
                    Result.success(root.stringValue("delta")?.let { listOf(NeutralStreamChunk.TextDelta(it)) }
                        ?: emptyList())
                }

                "response.refusal.delta" -> {
                    Result.success(root.stringValue("delta")?.let { listOf(NeutralStreamChunk.TextDelta(it)) }
                        ?: emptyList())
                }

                "response.reasoning_summary_text.delta" -> {
                    Result.success(
                        root.stringValue("delta", "text")?.let { listOf(NeutralStreamChunk.ReasoningDelta(it)) }
                            ?: emptyList()
                    )
                }

                "response.output_item.added" -> parseToolAdded(root, state)
                "response.function_call_arguments.delta" -> parseToolArgumentsDelta(root, state)
                "response.output_item.done" -> parseToolDone(root, state)
                "response.completed" -> {
                    Result.success(
                        listOf(
                            NeutralStreamChunk.Completed(
                                normalizeFinishReason(
                                    root.objectValue("response") ?: root,
                                    "completed",
                                    state.hasToolCalls()
                                ),
                                parseUsage(root.objectValue("response") ?: root)
                            )
                        )
                    )
                }

                "response.incomplete" -> {
                    val response = root.objectValue("response") ?: root
                    Result.success(
                        listOf(
                            NeutralStreamChunk.Completed(
                                response.objectValue("incomplete_details")
                                    ?.stringValue("reason")
                                    ?: root.stringValue("reason")
                                    ?: "incomplete",
                                parseUsage(response)
                            )
                        )
                    )
                }

                "response.failed" -> Result.success(
                    listOf(NeutralStreamChunk.Error(responseErrorMessage(root), 502))
                )

                else -> Result.success(emptyList())
            }
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("解析 OpenAI Responses 流事件失败：${error.message}", error))
        }
    }

    private fun messageItems(message: NeutralMessage): List<JsonElement> {
        val content = mutableListOf<JsonElement>()
        val functionCalls = mutableListOf<JsonElement>()
        val functionOutputs = mutableListOf<JsonElement>()
        message.contents.forEach { item ->
            when (item) {
                is NeutralContent.Text -> content += buildJsonObject {
                    put("type", if (message.role == NeutralRole.ASSISTANT) "output_text" else "input_text")
                    put("text", item.text)
                }

                is NeutralContent.Image -> {
                    if (item.mimeType.startsWith("image/", ignoreCase = true)) {
                        content += buildJsonObject {
                            put("type", "input_image")
                            put("image_url", "data:${item.mimeType};base64,${item.base64Data}")
                        }
                    } else {
                        content += buildJsonObject {
                            put("type", "input_file")
                            put("filename", inlineDataFilename(item.mimeType))
                            put("file_data", item.base64Data)
                        }
                    }
                }

                is NeutralContent.ToolCall -> functionCalls += buildJsonObject {
                    put("type", "function_call")
                    put("call_id", item.id)
                    put("name", item.functionName)
                    put("arguments", item.argumentsJson)
                }

                is NeutralContent.ToolResult -> functionOutputs += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", item.toolCallId)
                    put("output", item.content)
                }

                is NeutralContent.Thinking -> Unit
            }
        }
        val items = mutableListOf<JsonElement>()
        if (content.isNotEmpty()) {
            items += buildJsonObject {
                put("role", responsesRole(message.role))
                put("content", JsonArray(content))
            }
        }
        items += functionCalls
        items += functionOutputs
        return items
    }

    private fun responsesRole(role: NeutralRole): String {
        return when (role) {
            NeutralRole.SYSTEM -> "system"
            NeutralRole.USER -> "user"
            NeutralRole.ASSISTANT -> "assistant"
            NeutralRole.TOOL -> "tool"
        }
    }

    private fun appendMessageOutput(item: JsonObject, chunks: MutableList<NeutralStreamChunk>) {
        item["content"]?.jsonArray?.forEach { contentElement ->
            val content = contentElement as? JsonObject ?: return@forEach
            when (content.stringValue("type")?.lowercase()) {
                "output_text" -> content.stringValue("text")?.let { chunks += NeutralStreamChunk.TextDelta(it) }
                "refusal" -> content.stringValue("refusal")?.let { chunks += NeutralStreamChunk.TextDelta(it) }
            }
        }
    }

    private fun appendReasoningOutput(item: JsonObject, chunks: MutableList<NeutralStreamChunk>) {
        item["summary"]?.jsonArray?.forEach { summaryElement ->
            val summary = summaryElement as? JsonObject ?: return@forEach
            summary.stringValue("text")?.let { chunks += NeutralStreamChunk.ReasoningDelta(it) }
        }
    }

    private fun parseToolAdded(root: JsonObject, state: StreamState): Result<List<NeutralStreamChunk>> {
        val item = root.objectValue("item") ?: return Result.success(emptyList())
        if (item.stringValue("type")?.lowercase() != "function_call") return Result.success(emptyList())
        val index = outputIndex(root, item)
        val tool = state.tool(index)
        tool.id = item.stringValue("call_id", "id") ?: tool.id
        tool.name = item.stringValue("name") ?: tool.name
        return Result.success(emitToolMetadata(index, tool))
    }

    private fun parseToolArgumentsDelta(root: JsonObject, state: StreamState): Result<List<NeutralStreamChunk>> {
        val index = outputIndex(root, root)
        val tool = state.tool(index)
        if (tool.closed) {
            return Result.failure(IllegalArgumentException("OpenAI Responses tool call $index received arguments after it ended"))
        }
        val delta = root.stringValue("delta") ?: ""
        tool.sawArgumentDelta = tool.sawArgumentDelta || delta.isNotEmpty()
        val chunk = NeutralStreamChunk.ToolCallDelta(index, tool.id, tool.name, delta)
        return Result.success(listOf(chunk))
    }

    private fun parseToolDone(root: JsonObject, state: StreamState): Result<List<NeutralStreamChunk>> {
        val item = root.objectValue("item") ?: return Result.success(emptyList())
        if (item.stringValue("type")?.lowercase() != "function_call") return Result.success(emptyList())
        val index = outputIndex(root, item)
        val tool = state.tool(index)
        tool.id = item.stringValue("call_id", "id") ?: tool.id
        tool.name = item.stringValue("name") ?: tool.name
        val chunks = emitToolMetadata(index, tool).toMutableList()
        val arguments = item.stringValue("arguments")
        if (!tool.sawArgumentDelta && !arguments.isNullOrBlank()) {
            chunks += NeutralStreamChunk.ToolCallDelta(index, tool.id, tool.name, arguments)
        }
        tool.closed = true
        return Result.success(chunks)
    }

    private fun emitToolMetadata(index: Int, tool: ToolState): List<NeutralStreamChunk> {
        if (tool.metadataEmitted && tool.emittedId == tool.id && tool.emittedName == tool.name) {
            return emptyList()
        }
        tool.metadataEmitted = true
        tool.emittedId = tool.id
        tool.emittedName = tool.name
        return listOf(NeutralStreamChunk.ToolCallDelta(index, tool.id, tool.name, ""))
    }

    private fun outputIndex(root: JsonObject, item: JsonObject): Int {
        return root.intValue("output_index", "index")
            ?: item.intValue("output_index", "index")
            ?: 0
    }

    private fun normalizeFinishReason(root: JsonObject, status: String, hasToolCall: Boolean): String {
        if (hasToolCall) return "tool_call"
        if (status == "incomplete") {
            return root.objectValue("incomplete_details")?.stringValue("reason") ?: "incomplete"
        }
        return when (status.lowercase()) {
            "completed", "complete", "stop" -> "stop"
            else -> status
        }
    }

    private fun responseError(root: JsonObject): NeutralStreamChunk.Error? {
        val error = root.objectValue("error") ?: root.objectValue("response")?.objectValue("error")
        return error?.let {
            val statusCode = it.intValue("code", "status", "status_code", "statusCode") ?: 502
            NeutralStreamChunk.Error(
                message = it.stringValue("message") ?: "OpenAI Responses upstream error",
                statusCode = statusCode
            )
        }
    }

    private fun responseErrorMessage(root: JsonObject): String {
        return root.objectValue("error")?.stringValue("message")
            ?: root.objectValue("response")?.objectValue("error")?.stringValue("message")
            ?: "OpenAI Responses upstream request failed"
    }

    private fun parseUsage(root: JsonObject): NeutralUsage? {
        val usage = root["usage"]?.jsonObject ?: return null
        fun long(key: String): Long? = usage[key]?.jsonPrimitive?.longOrNull
        val input = long("input_tokens")
        val output = long("output_tokens")
        val cached = usage["input_tokens_details"]?.jsonObject
            ?.get("cached_tokens")?.jsonPrimitive?.longOrNull
        val reasoning = usage["output_tokens_details"]?.jsonObject
            ?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull
        val validCacheBreakdown = input != null && (cached ?: 0L) <= input
        val validReasoningBreakdown = output != null && (reasoning ?: 0L) <= output
        val computedTotal = input?.plus(output ?: 0L)
        val reportedTotal = long("total_tokens")
        return NeutralUsage(
            inputTokens = input?.let { total -> if (validCacheBreakdown) total - (cached ?: 0L) else total },
            outputTokens = output?.let { total -> if (validReasoningBreakdown) total - (reasoning ?: 0L) else total },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            totalTokens = reportedTotal?.takeIf { computedTotal == null || it >= computedTotal } ?: computedTotal
        )
    }

    private fun parseJsonObject(value: JsonElement, label: String): Result<JsonObject> {
        val parsed = if (value is JsonPrimitive && value.isString) {
            try {
                json.parseToJsonElement(value.content)
            } catch (error: Exception) {
                return Result.failure(IllegalArgumentException("$label 不是有效 JSON：${error.message}", error))
            }
        } else {
            value
        }
        return if (parsed is JsonObject) {
            Result.success(parsed)
        } else {
            Result.failure(IllegalArgumentException("$label 必须是 JSON 对象"))
        }
    }

    private fun normalizeJsonSchema(value: JsonElement): JsonElement {
        return when (value) {
            is JsonObject -> JsonObject(value.mapValues { (key, child) ->
                if (key == "type" && child is JsonPrimitive && child.isString) {
                    JsonPrimitive(child.content.lowercase())
                } else {
                    normalizeJsonSchema(child)
                }
            })
            is JsonArray -> JsonArray(value.map(::normalizeJsonSchema))
            else -> value
        }
    }

    private fun JsonObject.objectValue(vararg keys: String): JsonObject? {
        return keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }
    }

    private fun JsonObject.stringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }
    }

    private fun JsonObject.intValue(vararg keys: String): Int? {
        return keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.intOrNull }
    }

    private fun inlineDataFilename(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "application/pdf" -> "input.pdf"
            "application/json" -> "input.json"
            "application/rtf", "text/rtf" -> "input.rtf"
            "application/x-ipynb+json" -> "input.ipynb"
            "application/x-javascript", "text/javascript" -> "input.js"
            "application/x-python-code", "text/x-python", "text/x-python-script" -> "input.py"
            "application/x-typescript", "text/x-typescript" -> "input.ts"
            "text/css" -> "input.css"
            "text/csv" -> "input.csv"
            "text/html" -> "input.html"
            "text/markdown" -> "input.md"
            "text/plain" -> "input.txt"
            "text/xml" -> "input.xml"
            "audio/wav", "audio/x-wav" -> "input.wav"
            "audio/mpeg", "audio/mp3" -> "input.mp3"
            "audio/webm", "audio/webm;codecs=opus" -> "input.webm"
            "audio/flac" -> "input.flac"
            "video/audio/s16le" -> "input.pcm"
            "video/audio/wav" -> "input.wav"
            "video/jpeg2000", "video/videoframe/jpeg2000" -> "input.j2k"
            "video/mp4" -> "input.mp4"
            "video/text/timestamp" -> "input.txt"
            "video/webm" -> "input.webm"
            else -> "input.bin"
        }
    }
}
