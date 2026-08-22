package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralMessage
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AnthropicAdapter : ProviderAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: normalizeUrl(provider.effectiveBaseUrl, "/messages")
        val requestBodyResult = buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid Anthropic request: ${requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"}",
                    502
                )
            )
            return@flow
        }
        val requestBody = requestBodyResult.getOrThrow()
        try {
            val response = ProviderAdapter.executeWithResponseHeadersTimeout(provider, request.stream) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(
                        this,
                        provider,
                        buildMap {
                            provider.apiKey?.let { put("x-api-key", it) }
                            put("anthropic-version", "2023-06-01")
                        }
                    )
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBody.toString())
                }.execute()
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                emit(
                    NeutralStreamChunk.Error(
                        "Anthropic API error (${response.status.value}): $body",
                        response.status.value
                    )
                )
                return@flow
            }

            if (!request.stream) {
                val parsed = parseNonStreamingResponse(response.bodyAsText())
                if (parsed.isFailure) {
                    emit(
                        NeutralStreamChunk.Error(
                            parsed.exceptionOrNull()?.message ?: "Invalid Anthropic response",
                            502
                        )
                    )
                    return@flow
                }
                parsed.getOrThrow().forEach { emit(it) }
                if (parsed.getOrThrow().none { it is NeutralStreamChunk.Completed }) {
                    emit(NeutralStreamChunk.Completed())
                }
                return@flow
            }

            val channel: ByteReadChannel = response.body()
            var completed = false
            while (!channel.isClosedForRead && !completed) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (trimmed.startsWith("event:")) continue
                if (!trimmed.startsWith("data:")) {
                    emit(NeutralStreamChunk.Error("Anthropic stream frame is missing data field", 502))
                    return@flow
                }
                val data = trimmed.removePrefix("data:").trim()
                val parsed = parseEvent(data)
                if (parsed.isFailure) {
                    emit(
                        NeutralStreamChunk.Error(
                            parsed.exceptionOrNull()?.message ?: "Invalid Anthropic stream event",
                            502
                        )
                    )
                    return@flow
                }
                parsed.getOrThrow().forEach { chunk ->
                    if (chunk is NeutralStreamChunk.Completed) completed = true
                    emit(chunk)
                }
            }
            if (!completed) emit(NeutralStreamChunk.Completed())
        } catch (error: Exception) {
            emit(NeutralStreamChunk.Error("Anthropic request failed: ${error.message ?: "unknown error"}", 502))
        }
    }

    override suspend fun testConnection(provider: Provider): Boolean {
        return fetchModels(provider).isNotEmpty()
    }

    override suspend fun fetchModels(provider: Provider): List<String> {
        return fetchDiscoveredModels(provider).map { it.id }
    }

    override suspend fun fetchDiscoveredModels(provider: Provider): List<com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo> {
        val url = provider.modelsEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: normalizeUrl(provider.effectiveBaseUrl, "/models")
        return try {
            val response = ProviderAdapter.sharedHttpClient.get(url) {
                ProviderAdapter.applyHeaders(
                    this,
                    provider,
                    buildMap {
                        provider.apiKey?.let { put("x-api-key", it) }
                        put("anthropic-version", "2023-06-01")
                    }
                )
                ProviderAdapter.applyTimeouts(this, provider, streaming = false)
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = response.bodyAsText()
            com.yuzhiqiang.antigravity.proxy.catalog.UniversalModelCatalogParser.parse(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = mutableListOf<Pair<com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition, JsonObject>>()
        for (tool in request.tools) {
            val schema = parseJsonObject(tool.parametersJson, "Anthropic tool ${tool.name} input_schema")
            if (schema.isFailure) {
                return Result.failure(schema.exceptionOrNull() ?: IllegalArgumentException("Invalid tool schema"))
            }
            tools.add(tool to schema.getOrThrow())
        }

        val messages = mutableListOf<JsonObject>()
        for (message in request.messages) {
            if (message.role == NeutralRole.SYSTEM) continue
            val parsedMessage = messageJson(message)
            if (parsedMessage.isFailure) {
                return Result.failure(parsedMessage.exceptionOrNull() ?: IllegalArgumentException("Invalid message"))
            }
            messages.add(parsedMessage.getOrThrow())
        }

        val mapping = request.reasoningMapping
        if (mapping != null && !ReasoningMappingSupport.isSupported(ProviderProtocol.ANTHROPIC_MESSAGES, mapping)) {
            return Result.failure(IllegalArgumentException("Anthropic 不支持推理映射 ${mapping.kind}"))
        }
        val budget = mapping
            ?.takeIf { item -> item.kind.equals("budget_tokens", ignoreCase = true) }
            ?.let(ReasoningMappingSupport::mappingValueAsInt)
        if (mapping?.kind.equals("budget_tokens", ignoreCase = true) && budget == null) {
            return Result.failure(IllegalArgumentException("Anthropic 思考预算不是有效整数"))
        }
        val maxTokens = maxOf(request.maxTokens ?: 4096, (budget ?: 0) + 1)

        val baseBody = buildJsonObject {
            put("model", request.targetUpstreamModelId)
            put("messages", buildJsonArray { messages.forEach { add(it) } })
            put("max_tokens", maxTokens)
            put("stream", request.stream)
            request.systemPrompt?.let { put("system", it) }
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("top_p", it) }
            if (mapping != null) {
                when (mapping.kind.lowercase()) {
                    "budget_tokens" -> put("thinking", buildJsonObject {
                        put("type", "enabled")
                        put("budget_tokens", budget ?: 0)
                    })

                    "adaptive" -> put("thinking", buildJsonObject {
                        put("type", "adaptive")
                    })

                    "effort" -> ReasoningMappingSupport.mappingValueAsString(mapping)?.let { effort ->
                        put("output_config", buildJsonObject { put("effort", effort) })
                    }
                }
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { (tool, schema) ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", schema)
                        })
                    }
                })
            }
        }
        return Result.success(ProviderAdapter.mergeSafeExtraBody(baseBody, request))
    }

    private fun messageJson(message: NeutralMessage): Result<JsonObject> {
        val role = if (message.role == NeutralRole.ASSISTANT) "assistant" else "user"
        val toolResult = message.contents.filterIsInstance<NeutralContent.ToolResult>().firstOrNull()
        if (toolResult != null) {
            return Result.success(
                buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", toolResult.toolCallId)
                            put("content", toolResult.content)
                        })
                    })
                }
            )
        }

        val content = anthropicContent(message.contents)
        if (content.isFailure) {
            return Result.failure(content.exceptionOrNull() ?: IllegalArgumentException("Invalid content"))
        }
        return Result.success(buildJsonObject {
            put("role", role)
            put("content", content.getOrThrow())
        })
    }

    private fun anthropicContent(contents: List<NeutralContent>): Result<JsonElement> {
        val textOnly = contents.all { it is NeutralContent.Text || it is NeutralContent.Thinking }
        if (textOnly) {
            return Result.success(
                JsonArray(
                    listOf(
                buildJsonObject {
                    put("type", "text")
                    put("text", contents.joinToString("\n") {
                        when (it) {
                            is NeutralContent.Text -> it.text
                            is NeutralContent.Thinking -> it.text
                            else -> ""
                        }
                    })
                }
            )))
        }
        val toolInputs = mutableMapOf<String, JsonObject>()
        for (content in contents.filterIsInstance<NeutralContent.ToolCall>()) {
            val input = parseJsonObject(content.argumentsJson, "Anthropic tool ${content.functionName} input")
            if (input.isFailure) {
                return Result.failure(input.exceptionOrNull() ?: IllegalArgumentException("Invalid tool input"))
            }
            toolInputs[content.id] = input.getOrThrow()
        }
        return Result.success(buildJsonArray {
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

                    is NeutralContent.Image -> add(buildJsonObject {
                        put("type", "image")
                        put("source", buildJsonObject {
                            put("type", "base64")
                            put("media_type", content.mimeType)
                            put("data", content.base64Data)
                        })
                    })

                    is NeutralContent.ToolCall -> add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", content.id)
                        put("name", content.functionName)
                        put("input", toolInputs.getValue(content.id))
                    })

                    else -> Unit
                }
            }
        })
    }

    private fun parseEvent(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val type = root["type"]?.jsonPrimitive?.contentOrNull
            val chunks = mutableListOf<NeutralStreamChunk>()
            when (type) {
                "content_block_start" -> {
                    val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val block = root["content_block"]?.jsonObject
                    if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                        chunks.add(
                            NeutralStreamChunk.ToolCallDelta(
                                index = index,
                                id = block["id"]?.jsonPrimitive?.contentOrNull,
                                name = block["name"]?.jsonPrimitive?.contentOrNull,
                                argsText = ""
                            )
                        )
                    }
                }

                "content_block_delta" -> {
                    val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val delta = root["delta"]?.jsonObject ?: JsonObject(emptyMap())
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let {
                            chunks.add(NeutralStreamChunk.TextDelta(it))
                        }

                        "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                            chunks.add(NeutralStreamChunk.ReasoningDelta(it))
                        }

                        "input_json_delta" -> chunks.add(
                            NeutralStreamChunk.ToolCallDelta(
                                index = index,
                                id = null,
                                name = null,
                                argsText = delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: ""
                            )
                        )
                    }
                }

                "message_delta" -> {
                    val reason = root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                    if (reason != null) chunks.add(NeutralStreamChunk.Completed(reason))
                }

                "message_stop" -> chunks.add(NeutralStreamChunk.Completed())
                "error" -> {
                    val error = root["error"]?.jsonObject
                    chunks.add(
                        NeutralStreamChunk.Error(
                            error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Anthropic stream error",
                            502
                        )
                    )
                }
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse Anthropic stream event: ${error.message}", error))
        }
    }

    private fun parseNonStreamingResponse(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val chunks = mutableListOf<NeutralStreamChunk>()
            root["content"]?.jsonArray?.forEachIndexed { index, blockElement ->
                val block = blockElement.jsonObject
                when (block["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" -> block["text"]?.jsonPrimitive?.contentOrNull?.let {
                        chunks.add(NeutralStreamChunk.TextDelta(it))
                    }

                    "thinking" -> block["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                        chunks.add(NeutralStreamChunk.ReasoningDelta(it))
                    }

                    "tool_use" -> chunks.add(
                        NeutralStreamChunk.ToolCallDelta(
                            index = index,
                            id = block["id"]?.jsonPrimitive?.contentOrNull,
                            name = block["name"]?.jsonPrimitive?.contentOrNull,
                            argsText = block["input"]?.toString() ?: "{}"
                        )
                    )
                }
            }
            root["stop_reason"]?.jsonPrimitive?.contentOrNull?.let {
                chunks.add(NeutralStreamChunk.Completed(it))
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse Anthropic response: ${error.message}", error))
        }
    }

    private fun parseJsonObject(value: JsonElement, label: String): Result<JsonObject> {
        val parsed = if (value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
            try {
                json.parseToJsonElement(value.content)
            } catch (error: Exception) {
                return Result.failure(IllegalArgumentException("$label is invalid JSON: ${error.message}", error))
            }
        } else {
            value
        }
        return if (parsed is JsonObject) {
            Result.success(parsed)
        } else {
            Result.failure(IllegalArgumentException("$label must be a JSON object"))
        }
    }

    private fun parseJsonObject(value: String, label: String): Result<JsonObject> {
        return try {
            val parsed = json.parseToJsonElement(value)
            if (parsed is JsonObject) {
                Result.success(parsed)
            } else {
                Result.failure(IllegalArgumentException("$label must be a JSON object"))
            }
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("$label is invalid JSON: ${error.message}", error))
        }
    }

    private fun normalizeUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith(path)) base else "$base$path"
    }
}
