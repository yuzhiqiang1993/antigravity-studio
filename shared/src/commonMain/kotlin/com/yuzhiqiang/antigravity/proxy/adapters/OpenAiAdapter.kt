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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import kotlinx.serialization.json.put

class OpenAiAdapter : ProviderAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk> = flow {
        if (provider.protocol == ProviderProtocol.OPENAI_RESPONSES) {
            emitAll(sendResponsesStream(provider, request))
            return@flow
        }

        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: normalizeUrl(provider.effectiveBaseUrl, "/chat/completions")
        val requestBodyResult = buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid OpenAI request: ${requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"}",
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
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBody.toString())
                }.execute()
            }

            if (!response.status.isSuccess()) {
                emitApiError(response, "OpenAI")
                return@flow
            }

            if (!request.stream) {
                val parsed = parseNonStreamingResponse(response.bodyAsText())
                if (parsed.isFailure) {
                    emit(NeutralStreamChunk.Error(parsed.exceptionOrNull()?.message ?: "Invalid OpenAI response", 502))
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
                    emit(NeutralStreamChunk.Error("OpenAI stream frame is missing data field", 502))
                    return@flow
                }
                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    emit(NeutralStreamChunk.Completed())
                    completed = true
                    break
                }
                val parsed = parseChunk(data)
                if (parsed.isFailure) {
                    emit(
                        NeutralStreamChunk.Error(
                            parsed.exceptionOrNull()?.message ?: "Invalid OpenAI stream chunk",
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
            emit(NeutralStreamChunk.Error("OpenAI request failed: ${error.message ?: "unknown error"}", 502))
        }
    }

    override suspend fun testConnection(provider: Provider): Boolean {
        return fetchModels(provider).isNotEmpty()
    }

    override suspend fun fetchModels(provider: Provider): List<String> {
        val url = provider.modelsEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: normalizeUrl(provider.effectiveBaseUrl, "/models")
        return try {
            val response = ProviderAdapter.sharedHttpClient.get(url) {
                ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                ProviderAdapter.applyTimeouts(this, provider, streaming = false)
            }
            if (!response.status.isSuccess()) return emptyList()
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sendResponsesStream(
        provider: Provider,
        request: NeutralChatRequest
    ): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: normalizeUrl(provider.effectiveBaseUrl, "/responses")
        val requestBodyResult = OpenAiResponsesCodec.buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid OpenAI Responses request: ${requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"}",
                    502
                )
            )
            return@flow
        }
        try {
            val response = ProviderAdapter.executeWithResponseHeadersTimeout(provider, request.stream) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBodyResult.getOrThrow().toString())
                }.execute()
            }
            if (!response.status.isSuccess()) {
                emitApiError(response, "OpenAI Responses")
                return@flow
            }
            if (!request.stream) {
                val parsed = OpenAiResponsesCodec.parseNonStreamingResponse(response.bodyAsText())
                if (parsed.isFailure) {
                    emit(
                        NeutralStreamChunk.Error(
                            parsed.exceptionOrNull()?.message ?: "Invalid OpenAI Responses response",
                            502
                        )
                    )
                    return@flow
                }
                val chunks = parsed.getOrThrow()
                chunks.forEach { chunk -> emit(chunk) }
                if (chunks.none { chunk -> chunk is NeutralStreamChunk.Completed || chunk is NeutralStreamChunk.Error }) {
                    emit(NeutralStreamChunk.Completed())
                }
                return@flow
            }

            val state = OpenAiResponsesCodec.StreamState()
            val channel: ByteReadChannel = response.body()
            var completed = false
            while (!channel.isClosedForRead && !completed) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (trimmed.startsWith("event:")) continue
                if (!trimmed.startsWith("data:")) {
                    emit(NeutralStreamChunk.Error("OpenAI Responses stream frame is missing data field", 502))
                    return@flow
                }
                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    emit(NeutralStreamChunk.Completed())
                    completed = true
                    continue
                }
                val parsed = OpenAiResponsesCodec.parseStreamEvent(data, state)
                if (parsed.isFailure) {
                    emit(
                        NeutralStreamChunk.Error(
                            parsed.exceptionOrNull()?.message ?: "Invalid OpenAI Responses stream event",
                            502
                        )
                    )
                    return@flow
                }
                parsed.getOrThrow().forEach { chunk ->
                    if (chunk is NeutralStreamChunk.Completed || chunk is NeutralStreamChunk.Error) {
                        completed = true
                    }
                    emit(chunk)
                }
            }
            if (!completed) {
                emit(NeutralStreamChunk.Error("OpenAI Responses stream ended before completion", 502))
            }
        } catch (error: Exception) {
            emit(NeutralStreamChunk.Error("OpenAI Responses request failed: ${error.message ?: "unknown error"}", 502))
        }
    }

    private fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = mutableListOf<Pair<com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition, JsonObject>>()
        for (tool in request.tools) {
            val schema = parseJsonObject(tool.parametersJson, "OpenAI tool ${tool.name} parameters")
            if (schema.isFailure) {
                return Result.failure(schema.exceptionOrNull() ?: IllegalArgumentException("Invalid tool schema"))
            }
            tools.add(tool to schema.getOrThrow())
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
        return Result.success(ProviderAdapter.mergeSafeExtraBody(baseBody, request))
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
            val arguments = parseJsonObject(call.argumentsJson, "OpenAI tool ${call.functionName} arguments")
            if (arguments.isFailure) {
                return Result.failure(arguments.exceptionOrNull() ?: IllegalArgumentException("Invalid tool arguments"))
            }
        }
        return Result.success(
            buildJsonObject {
                put("role", role)
                put("content", openAiContent(contents.filterNot { it is NeutralContent.ToolCall }))
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

                    is NeutralContent.Image -> add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:${content.mimeType};base64,${content.base64Data}")
                        })
                    })

                    else -> Unit
                }
            }
        }
    }

    private fun parseChunk(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            root["error"]?.jsonObject?.let { error ->
                return Result.success(
                    listOf(
                        NeutralStreamChunk.Error(
                            error["message"]?.jsonPrimitive?.contentOrNull ?: "OpenAI stream error",
                            error["code"]?.jsonPrimitive?.intOrNull ?: 502
                        )
                    )
                )
            }
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return Result.success(emptyList())
            val delta = choice["delta"]?.jsonObject ?: JsonObject(emptyMap())
            val chunks = mutableListOf<NeutralStreamChunk>()
            delta["content"]?.jsonPrimitive?.contentOrNull?.let { chunks.add(NeutralStreamChunk.TextDelta(it)) }
            delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.let {
                chunks.add(
                    NeutralStreamChunk.ReasoningDelta(
                        it
                    )
                )
            }
            delta["reasoning"]?.jsonPrimitive?.contentOrNull?.let { chunks.add(NeutralStreamChunk.ReasoningDelta(it)) }
            delta["tool_calls"]?.jsonArray?.forEach { toolElement ->
                val tool = toolElement.jsonObject
                val function = tool["function"]?.jsonObject
                chunks.add(
                    NeutralStreamChunk.ToolCallDelta(
                        index = tool["index"]?.jsonPrimitive?.intOrNull ?: 0,
                        id = tool["id"]?.jsonPrimitive?.contentOrNull,
                        name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                        argsText = function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: ""
                    )
                )
            }
            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                chunks.add(NeutralStreamChunk.Completed(it))
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse OpenAI stream chunk: ${error.message}", error))
        }
    }

    private fun parseNonStreamingResponse(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return Result.success(emptyList())
            val message = choice["message"]?.jsonObject ?: JsonObject(emptyMap())
            val chunks = mutableListOf<NeutralStreamChunk>()
            message["content"]?.jsonPrimitive?.contentOrNull?.let { chunks.add(NeutralStreamChunk.TextDelta(it)) }
            message["reasoning_content"]?.jsonPrimitive?.contentOrNull?.let {
                chunks.add(NeutralStreamChunk.ReasoningDelta(it))
            }
            message["tool_calls"]?.jsonArray?.forEach { toolElement ->
                val tool = toolElement.jsonObject
                val function = tool["function"]?.jsonObject
                chunks.add(
                    NeutralStreamChunk.ToolCallDelta(
                        index = tool["index"]?.jsonPrimitive?.intOrNull ?: 0,
                        id = tool["id"]?.jsonPrimitive?.contentOrNull,
                        name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                        argsText = function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: ""
                    )
                )
            }
            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                chunks.add(NeutralStreamChunk.Completed(it))
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse OpenAI response: ${error.message}", error))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<NeutralStreamChunk>.emitApiError(
        response: HttpResponse,
        providerName: String
    ) {
        val body = response.bodyAsText()
        emit(
            NeutralStreamChunk.Error(
                "$providerName API error (${response.status.value}): $body",
                response.status.value
            )
        )
    }

    private fun authHeaders(provider: Provider): Map<String, String> {
        return provider.apiKey?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
    }

    private fun messageRole(message: NeutralMessage): String {
        return when (message.role) {
            NeutralRole.SYSTEM -> "system"
            NeutralRole.USER -> "user"
            NeutralRole.ASSISTANT -> "assistant"
            NeutralRole.TOOL -> "tool"
        }
    }

    private fun normalizeUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith(path)) base else "$base$path"
    }
}
