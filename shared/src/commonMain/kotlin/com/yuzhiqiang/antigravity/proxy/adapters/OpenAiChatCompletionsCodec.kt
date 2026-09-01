package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralMessage
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.normalizedNeutralUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * OpenAI Chat Completions 协议编解码器。
 * 负责请求体组装、流式块解析、非流式响应解析与 Token Usage 解析。
 */
internal object OpenAiChatCompletionsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = mutableListOf<Pair<com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition, JsonObject>>()
        for (tool in request.tools) {
            val schema = parseJsonObject(tool.parametersJson, "OpenAI tool " + tool.name + " parameters")
            if (schema.isFailure) {
                return Result.failure(schema.exceptionOrNull() ?: IllegalArgumentException("Invalid tool schema"))
            }
            tools.add(tool to (normalizeJsonSchema(schema.getOrThrow()) as JsonObject))
        }

        val messages = mutableListOf<JsonObject>()
        request.systemPrompt?.let {
            val systemMessage = messageJson("system", listOf(NeutralContent.Text(it)))
            if (systemMessage.isFailure) {
                return Result.failure(
                    systemMessage.exceptionOrNull() ?: IllegalArgumentException("Invalid system message")
                )
            }
            messages.add(systemMessage.getOrThrow())
        }
        for (message in request.messages) {
            val parsedMessage = messageJson(messageRole(message), message.contents)
            if (parsedMessage.isFailure) {
                return Result.failure(parsedMessage.exceptionOrNull() ?: IllegalArgumentException("Invalid message"))
            }
            messages.add(parsedMessage.getOrThrow())
        }

        val baseBody = buildJsonObject {
            put("model", request.targetUpstreamModelId)
            put("messages", buildJsonArray { messages.forEach { add(it) } })
            put("stream", request.stream)
            if (request.stream) {
                // OpenAI 只有显式开启该选项才会在流末尾返回 usage；byok
                // 依赖它完成 Token 统计与 Checkpoint 观测。
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            request.topP?.let { put("top_p", it) }
            request.reasoningMapping?.let { mapping ->
                when (mapping.kind.lowercase()) {
                    "effort" -> ReasoningMappingSupport.mappingValueAsString(mapping)
                        ?.let { value -> put("reasoning_effort", value) }

                    "disabled" -> put("reasoning_effort", "none")
                }
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { (tool, schema) ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", schema)
                            })
                        })
                    }
                })
            }
        }
        val merged = ProviderAdapter.mergeSafeExtraBody(baseBody, request)
        if (!request.stream) return Result.success(merged)
        val streamOptions = (merged["stream_options"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        streamOptions["include_usage"] = JsonPrimitive(true)
        return Result.success(JsonObject(merged + ("stream_options" to JsonObject(streamOptions))))
    }

    fun parseChunk(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            root["error"]?.jsonObject?.let { error ->
                val statusCode = error["code"]?.jsonPrimitive?.intOrNull
                    ?: error["status"]?.jsonPrimitive?.intOrNull
                    ?: error["status_code"]?.jsonPrimitive?.intOrNull
                    ?: error["statusCode"]?.jsonPrimitive?.intOrNull
                    ?: 502
                return Result.success(
                    listOf(
                        NeutralStreamChunk.Error(
                            error["message"]?.jsonPrimitive?.contentOrNull ?: "OpenAI stream error",
                            statusCode
                        )
                    )
                )
            }
            val usage = parseUsage(root)
            val chunks = mutableListOf<NeutralStreamChunk>()
            root["model"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let { chunks += NeutralStreamChunk.ResponseMetadata(it) }
            val choices = root["choices"]?.jsonArray.orEmpty()
            if (choices.isEmpty()) {
                usage?.let { chunks.add(NeutralStreamChunk.Completed(null, it)) }
            }
            choices.forEachIndexed { position, choiceElement ->
                val choice = choiceElement.jsonObject
                val choiceIndex = choice["index"]?.jsonPrimitive?.intOrNull ?: position
                val delta = choice["delta"]?.jsonObject ?: JsonObject(emptyMap())
                appendOpenAiTextContent(delta["content"], chunks, choiceIndex)
                delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.ReasoningDelta(it, choiceIndex = choiceIndex))
                }
                delta["reasoning"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.ReasoningDelta(it, choiceIndex = choiceIndex))
                }
                delta["tool_calls"]?.jsonArray?.forEachIndexed { toolPosition, toolElement ->
                    val tool = toolElement.jsonObject
                    val function = tool["function"]?.jsonObject
                    chunks.add(
                        NeutralStreamChunk.ToolCallDelta(
                            index = tool["index"]?.jsonPrimitive?.intOrNull ?: toolPosition,
                            id = tool["id"]?.jsonPrimitive?.contentOrNull
                                ?: ("call_" + choiceIndex + "_" + toolPosition),
                            name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                            argsText = function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "",
                            choiceIndex = choiceIndex
                        )
                    )
                }
                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.Completed(it, usage, choiceIndex))
                } ?: usage?.let {
                    chunks.add(NeutralStreamChunk.Completed(null, it, choiceIndex))
                }
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse OpenAI stream chunk: " + error.message, error))
        }
    }

    fun parseNonStreamingResponse(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val usage = parseUsage(root)
            val chunks = mutableListOf<NeutralStreamChunk>()
            root["model"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let { chunks += NeutralStreamChunk.ResponseMetadata(it) }
            val choices = root["choices"]?.jsonArray.orEmpty()
            if (choices.isEmpty()) {
                usage?.let { chunks.add(NeutralStreamChunk.Completed(null, it)) }
            }
            choices.forEachIndexed { position, choiceElement ->
                val choice = choiceElement.jsonObject
                val choiceIndex = choice["index"]?.jsonPrimitive?.intOrNull ?: position
                val message = choice["message"]?.jsonObject ?: JsonObject(emptyMap())
                appendOpenAiTextContent(message["content"], chunks, choiceIndex)
                message["reasoning_content"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.ReasoningDelta(it, choiceIndex = choiceIndex))
                }
                message["tool_calls"]?.jsonArray?.forEachIndexed { toolPosition, toolElement ->
                    val tool = toolElement.jsonObject
                    val function = tool["function"]?.jsonObject
                    chunks.add(
                        NeutralStreamChunk.ToolCallDelta(
                            index = tool["index"]?.jsonPrimitive?.intOrNull ?: toolPosition,
                            id = tool["id"]?.jsonPrimitive?.contentOrNull
                                ?: ("call_" + choiceIndex + "_" + toolPosition),
                            name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                            argsText = function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "",
                            choiceIndex = choiceIndex
                        )
                    )
                }
                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.Completed(it, usage, choiceIndex))
                } ?: usage?.let {
                    chunks.add(NeutralStreamChunk.Completed(null, it, choiceIndex))
                }
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse OpenAI response: " + error.message, error))
        }
    }

    private fun messageJson(role: String, contents: List<NeutralContent>): Result<JsonObject> {
        val toolResult = contents.filterIsInstance<NeutralContent.ToolResult>().firstOrNull()
        if (role == "tool" && toolResult != null) {
            return Result.success(
                buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", toolResult.toolCallId)
                    toolResult.functionName?.let { put("name", it) }
                    put("content", toolResult.content)
                }
            )
        }

        val toolCalls = contents.filterIsInstance<NeutralContent.ToolCall>()
        for (call in toolCalls) {
            val arguments = parseJsonObject(call.argumentsJson, "OpenAI tool " + call.functionName + " arguments")
            if (arguments.isFailure) {
                return Result.failure(arguments.exceptionOrNull() ?: IllegalArgumentException("Invalid tool arguments"))
            }
        }
        return Result.success(
            buildJsonObject {
                put("role", role)
                val nonToolContents = contents.filterNot {
                    it is NeutralContent.ToolCall || it is NeutralContent.Thinking
                }
                if (nonToolContents.isNotEmpty()) {
                    put("content", openAiContent(nonToolContents))
                }
                val thinking = contents.filterIsInstance<NeutralContent.Thinking>()
                    .joinToString("\n") { it.text }
                    .takeIf { it.isNotBlank() }
                thinking?.let { put("reasoning_content", it) }
                if (toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.functionName)
                                    put("arguments", call.argumentsJson)
                                })
                            })
                        }
                    })
                }
            }
        )
    }

    private fun parseJsonObject(value: JsonElement, label: String): Result<JsonObject> {
        val parsed = if (value is JsonPrimitive && value.isString) {
            try {
                json.parseToJsonElement(value.content)
            } catch (error: Exception) {
                return Result.failure(IllegalArgumentException(label + " is invalid JSON: " + error.message, error))
            }
        } else {
            value
        }
        return if (parsed is JsonObject) {
            Result.success(parsed)
        } else {
            Result.failure(IllegalArgumentException(label + " must be a JSON object"))
        }
    }

    private fun parseJsonObject(value: String, label: String): Result<JsonObject> {
        return try {
            val parsed = json.parseToJsonElement(value)
            if (parsed is JsonObject) {
                Result.success(parsed)
            } else {
                Result.failure(IllegalArgumentException(label + " must be a JSON object"))
            }
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException(label + " is invalid JSON: " + error.message, error))
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

    private fun openAiContent(contents: List<NeutralContent>): JsonElement {
        val textOnly = contents.all { it is NeutralContent.Text || it is NeutralContent.Thinking }
        if (textOnly) {
            return JsonPrimitive(contents.joinToString("\n") {
                when (it) {
                    is NeutralContent.Text -> it.text
                    is NeutralContent.Thinking -> it.text
                    else -> ""
                }
            })
        }
        return buildJsonArray {
            contents.forEach { content ->
                when (content) {
                    is NeutralContent.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", content.text)
                    })

                    is NeutralContent.Thinking -> add(buildJsonObject {
                        put("type", "text")
                        put("text", content.text)
                    })

                    is NeutralContent.Image -> when {
                        content.mimeType.startsWith("image/", ignoreCase = true) -> add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:" + content.mimeType + ";base64," + content.base64Data)
                            })
                        })

                        openAiInputAudioFormat(content.mimeType) != null -> add(buildJsonObject {
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject {
                                put("data", content.base64Data)
                                put("format", openAiInputAudioFormat(content.mimeType) ?: "wav")
                            })
                        })

                        else -> add(buildJsonObject {
                            put("type", "file")
                            put("file", buildJsonObject {
                                put("filename", inlineDataFilename(content.mimeType))
                                put("file_data", content.base64Data)
                            })
                        })
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun parseUsage(root: JsonObject): NeutralUsage? {
        val usage = root["usage"]?.jsonObject ?: return null
        fun long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
            usage[key]?.jsonPrimitive?.longOrNull
        }

        val prompt = long("prompt_tokens", "input_tokens")
        val completion = long("completion_tokens", "output_tokens")
        val reasoning = usage["completion_tokens_details"]?.jsonObject
            ?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull
            ?: long("reasoning_tokens", "thinking_tokens")
        val cached = usage["prompt_tokens_details"]?.jsonObject
            ?.get("cached_tokens")?.jsonPrimitive?.longOrNull
            ?: long("cache_read_input_tokens", "cached_tokens")
        val cacheWrite = long("cache_creation_input_tokens", "cache_write_input_tokens")
        val cachedTokens = cached ?: 0L
        val cacheWriteTokens = cacheWrite ?: 0L
        val reasoningTokens = reasoning ?: 0L
        val validCacheBreakdown = prompt != null && prompt >= 0L &&
                cachedTokens >= 0L && cacheWriteTokens in 0L..prompt &&
                cachedTokens <= prompt - cacheWriteTokens
        val validReasoningBreakdown = completion != null && completion >= 0L &&
                reasoningTokens in 0L..completion
        return normalizedNeutralUsage(
            inputTokens = prompt?.let { total ->
                if (validCacheBreakdown) total - (cached ?: 0L) - (cacheWrite ?: 0L) else total
            },
            outputTokens = completion?.let { total ->
                if (validReasoningBreakdown) total - (reasoning ?: 0L) else total
            },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            cacheWriteTokens = cacheWrite.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            reportedTotalTokens = long("total_tokens")
        )
    }

    private fun appendOpenAiTextContent(
        value: JsonElement?,
        target: MutableList<NeutralStreamChunk>,
        choiceIndex: Int = 0
    ) {
        when (value) {
            is JsonPrimitive -> value.contentOrNull?.let { target += NeutralStreamChunk.TextDelta(it, choiceIndex) }
            is JsonArray -> value.forEach { partElement ->
                val part = partElement as? JsonObject ?: return@forEach
                val type = part["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                when (type) {
                    "text", "output_text", "input_text", "refusal" -> {
                        part["text"]?.jsonPrimitive?.contentOrNull?.let {
                            target += NeutralStreamChunk.TextDelta(it, choiceIndex)
                        }
                        part["refusal"]?.jsonPrimitive?.contentOrNull?.let {
                            target += NeutralStreamChunk.TextDelta(it, choiceIndex)
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    private fun messageRole(message: NeutralMessage): String {
        return when (message.role) {
            NeutralRole.SYSTEM -> "system"
            NeutralRole.USER -> "user"
            NeutralRole.ASSISTANT -> "assistant"
            NeutralRole.TOOL -> "tool"
        }
    }

    private fun openAiInputAudioFormat(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            else -> null
        }
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
            "text/plain" -> "input.txt"
            "text/markdown" -> "input.md"
            "text/html" -> "input.html"
            "text/css" -> "input.css"
            "text/csv" -> "input.csv"
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
