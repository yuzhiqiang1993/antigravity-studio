package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralMessage
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAiAdapter : ProviderAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk> = flow {
        if (provider.protocol == ProviderProtocol.OPENAI_RESPONSES &&
            request.outputModalities.contains(ModelModality.IMAGE)
        ) {
            emit(NeutralStreamChunk.Error(
                "OpenAI Responses API 不支持图像生成，请改用 Gemini 或 OpenAI Chat Completions",
                400
            ))
            return@flow
        }
        if (provider.protocol == ProviderProtocol.OPENAI_RESPONSES) {
            emitAll(sendResponsesStream(provider, request))
            return@flow
        }
        if (request.outputModalities.contains(ModelModality.IMAGE)) {
            emitAll(sendImageGeneration(provider, request))
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
        var responseStarted = false
        try {
            ProviderAdapter.executeStreamingWithTimeout(provider) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBody.toString())
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        emitApiError(response, "OpenAI")
                        return@execute
                    }
                    responseStarted = true

                if (!request.stream) {
                    val responseBody = ProviderAdapter.readLimitedResponseText(response)
                    if (responseBody.isFailure) {
                        emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "OpenAI response body exceeds 4 MiB buffered limit", 502))
                        return@execute
                    }
                    val parsed = parseNonStreamingResponse(responseBody.getOrThrow())
                    if (parsed.isFailure) {
                        emit(NeutralStreamChunk.Error(parsed.exceptionOrNull()?.message ?: "Invalid OpenAI response", 502))
                        return@execute
                    }
                    parsed.getOrThrow().forEach { emit(it) }
                    if (parsed.getOrThrow().none { it is NeutralStreamChunk.Completed }) {
                        emit(NeutralStreamChunk.Completed())
                    }
                    return@execute
                }

                val channel: ByteReadChannel = response.body()
                var streamEnded = false
                var sawCompletion = false
                val openToolCalls = mutableSetOf<Pair<Int, Int>>()
                val closedToolCalls = mutableSetOf<Pair<Int, Int>>()
                while (!channel.isClosedForRead && !streamEnded) {
                    val event = ProviderAdapter.readSseDataEvent(channel)
                    if (event.isFailure) {
                        emit(NeutralStreamChunk.Error(event.exceptionOrNull()?.message ?: "Invalid OpenAI SSE frame", 502, responseStarted = true))
                        return@execute
                    }
                    val data = event.getOrNull() ?: break
                    if (data.trim() == "[DONE]") {
                        if (!sawCompletion) emit(NeutralStreamChunk.Completed())
                        streamEnded = true
                        break
                    }
                    val parsed = parseChunk(data)
                    if (parsed.isFailure) {
                        emit(
                            NeutralStreamChunk.Error(
                                parsed.exceptionOrNull()?.message ?: "Invalid OpenAI stream chunk",
                                502,
                                responseStarted = true
                            )
                        )
                        return@execute
                    }
                    for (chunk in parsed.getOrThrow()) {
                        if (chunk is NeutralStreamChunk.ToolCallDelta) {
                            val key = chunk.choiceIndex to chunk.index
                            if (key in closedToolCalls) {
                                emit(NeutralStreamChunk.Error(
                                    "OpenAI tool call choice ${chunk.choiceIndex} index ${chunk.index} received data after it ended",
                                    502,
                                    responseStarted = true
                                ))
                                streamEnded = true
                                break
                            }
                            openToolCalls += key
                        }
                        val effectiveChunk = if (chunk is NeutralStreamChunk.Error) {
                            chunk.copy(responseStarted = true)
                        } else {
                            chunk
                        }
                        if (effectiveChunk is NeutralStreamChunk.Completed) {
                            sawCompletion = true
                            val choiceTools = openToolCalls.filter { key -> key.first == effectiveChunk.choiceIndex }
                            closedToolCalls += choiceTools
                            openToolCalls.removeAll(choiceTools)
                        }
                        if (effectiveChunk is NeutralStreamChunk.Error) streamEnded = true
                        emit(effectiveChunk)
                        if (streamEnded) break
                    }
                }
                if (!sawCompletion && !streamEnded) emit(NeutralStreamChunk.Completed())
                }
            }
        } catch (error: Exception) {
            val status = ProviderAdapter.upstreamFailureStatus(error)
            emit(NeutralStreamChunk.Error("OpenAI request failed: ${error.message ?: "unknown error"}", status, responseStarted = responseStarted))
        }
    }

    override suspend fun testConnection(provider: Provider): Boolean {
        return fetchModels(provider).isNotEmpty()
    }

    override suspend fun fetchModels(provider: Provider): List<String> {
        return fetchDiscoveredModels(provider).map { it.id }
    }

    override suspend fun fetchDiscoveredModels(provider: Provider): List<com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo> {
        return fetchModelCatalog(provider).models
    }

    override suspend fun fetchModelCatalog(provider: Provider): ProviderAdapter.ModelCatalogResult {
        val url = ProviderAdapter.appendCpaCatalogVersion(provider.modelsEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: normalizeUrl(provider.effectiveBaseUrl, "/models"))
        return try {
            val response = ProviderAdapter.sharedHttpClient.get(url) {
                ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                ProviderAdapter.applyTimeouts(this, provider, streaming = false)
            }
            val bodyResult = ProviderAdapter.readLimitedResponseText(response)
            val body = bodyResult.getOrNull()
            if (!response.status.isSuccess()) {
                return ProviderAdapter.ModelCatalogResult(
                    rawBody = body,
                    errorMessage = "HTTP ${response.status.value}"
                )
            }
            if (body == null) {
                return ProviderAdapter.ModelCatalogResult(
                    errorMessage = bodyResult.exceptionOrNull()?.message ?: "读取响应失败"
                )
            }
            ProviderAdapter.ModelCatalogResult(
                models = com.yuzhiqiang.antigravity.proxy.catalog.UniversalModelCatalogParser.parse(
                    body,
                    protocol = provider.protocol,
                    isCpaCatalog = ProviderAdapter.isCpaCatalogUrl(url)
                ),
                rawBody = body
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            ProviderAdapter.ModelCatalogResult(errorMessage = error.message ?: "请求失败")
        }
    }

    /** OpenAI 兼容 Provider 的图片生成最小闭环，统一回传为 Gemini inlineData。 */
    private fun sendImageGeneration(
        provider: Provider,
        request: NeutralChatRequest
    ): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = imageGenerationUrl(provider, model)
        val prompt = request.messages.asReversed()
            .firstOrNull { message -> message.role == NeutralRole.USER }
            ?.contents
            ?.filterIsInstance<NeutralContent.Text>()
            ?.joinToString("\n") { text -> text.text }
            .orEmpty()
        if (prompt.isBlank()) {
            emit(NeutralStreamChunk.Error("Image generation requires a non-empty text prompt", 400))
            return@flow
        }

        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("response_format", "b64_json")
            imageSize(request.imageGenerationConfig)?.let { put("size", it) }
            (request.imageGenerationConfig as? JsonObject)
                ?.get("quality")
                ?.let { put("quality", it) }
            (request.imageGenerationConfig as? JsonObject)?.forEach { (key, value) ->
                if (key !in setOf("model", "prompt", "n", "response_format", "size", "quality", "aspectRatio", "aspect_ratio")) {
                    put(key, value)
                }
            }
        }.let { base -> ProviderAdapter.mergeSafeExtraBody(base, request) }

        try {
            val response = ProviderAdapter.executeStreamingWithTimeout(
                provider,
                minimumRequestTimeoutMs = 120_000L
            ) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(
                        this,
                        provider,
                        streaming = false,
                        minimumRequestTimeoutMs = 120_000L
                    )
                    setBody(body.toString())
                }.execute()
            }
            if (!response.status.isSuccess()) {
                emitApiError(response, "OpenAI image")
                return@flow
            }
            val responseBody = ProviderAdapter.readLimitedResponseText(response)
            if (responseBody.isFailure) {
                emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "OpenAI image response body exceeds 4 MiB buffered limit", 502))
                return@flow
            }
            val root = json.parseToJsonElement(responseBody.getOrThrow()).jsonObject
            val data = root["data"]?.jsonArray.orEmpty()
            if (data.isEmpty()) {
                emit(NeutralStreamChunk.Error("OpenAI image response did not contain data", 502))
                return@flow
            }
            data.forEach { element ->
                val image = element.jsonObject
                val base64 = image["b64_json"]?.jsonPrimitive?.contentOrNull
                if (!base64.isNullOrBlank()) {
                    emit(NeutralStreamChunk.InlineDataDelta("image/png", base64.replace(Regex("\\s+"), "")))
                } else {
                    val imageUrl = image["url"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (imageUrl.isNullOrBlank()) {
                        emit(NeutralStreamChunk.Error("OpenAI image response did not contain b64_json or url", 502))
                    } else if (imageUrl.startsWith("https://", ignoreCase = true) ||
                        imageUrl.startsWith("http://", ignoreCase = true) ||
                        imageUrl.startsWith("data:image/", ignoreCase = true)
                    ) {
                        // 与 byok 一致：URL 结果转为宿主可展示的 Markdown，避免
                        // 把外部 URL 当作代理响应地址而破坏后续请求路由。
                        emit(NeutralStreamChunk.TextDelta("![generated_image]($imageUrl)"))
                    } else {
                        emit(NeutralStreamChunk.Error("OpenAI image response returned an unsafe URL", 502))
                    }
                }
            }
            emit(NeutralStreamChunk.Completed("stop"))
        } catch (error: Exception) {
            emit(NeutralStreamChunk.Error("OpenAI image request failed: ${error.message ?: "unknown error"}", ProviderAdapter.upstreamFailureStatus(error)))
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
        var responseStarted = false
        try {
            ProviderAdapter.executeStreamingWithTimeout(provider) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBodyResult.getOrThrow().toString())
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        emitApiError(response, "OpenAI Responses")
                        return@execute
                    }
                    responseStarted = true
                    if (!request.stream) {
                        val responseBody = ProviderAdapter.readLimitedResponseText(response)
                        if (responseBody.isFailure) {
                            emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "OpenAI Responses body exceeds 4 MiB buffered limit", 502))
                            return@execute
                        }
                        val parsed = OpenAiResponsesCodec.parseNonStreamingResponse(responseBody.getOrThrow())
                        if (parsed.isFailure) {
                            emit(
                                NeutralStreamChunk.Error(
                                    parsed.exceptionOrNull()?.message ?: "Invalid OpenAI Responses response",
                                    502
                                )
                            )
                            return@execute
                        }
                        val chunks = parsed.getOrThrow()
                        chunks.forEach { chunk -> emit(chunk) }
                        if (chunks.none { chunk -> chunk is NeutralStreamChunk.Completed || chunk is NeutralStreamChunk.Error }) {
                            emit(NeutralStreamChunk.Completed())
                        }
                        return@execute
                    }

                    val state = OpenAiResponsesCodec.StreamState()
                    val channel: ByteReadChannel = response.body()
                    var completed = false
                    while (!channel.isClosedForRead && !completed) {
                        val event = ProviderAdapter.readSseDataEvent(channel)
                        if (event.isFailure) {
                            emit(NeutralStreamChunk.Error(event.exceptionOrNull()?.message ?: "Invalid OpenAI Responses SSE frame", 502, responseStarted = true))
                            return@execute
                        }
                        val data = event.getOrNull() ?: break
                        if (data.trim() == "[DONE]") {
                            emit(NeutralStreamChunk.Completed())
                            completed = true
                            continue
                        }
                        val parsed = OpenAiResponsesCodec.parseStreamEvent(data, state)
                        if (parsed.isFailure) {
                            emit(
                                NeutralStreamChunk.Error(
                                    parsed.exceptionOrNull()?.message ?: "Invalid OpenAI Responses stream event",
                                    502,
                                    responseStarted = true
                                )
                            )
                            return@execute
                        }
                        parsed.getOrThrow().forEach { chunk ->
                            val effectiveChunk = if (chunk is NeutralStreamChunk.Error) {
                                chunk.copy(responseStarted = true)
                            } else {
                                chunk
                            }
                            if (effectiveChunk is NeutralStreamChunk.Completed || effectiveChunk is NeutralStreamChunk.Error) {
                                completed = true
                            }
                            emit(effectiveChunk)
                        }
                    }
                    if (!completed) {
                        emit(NeutralStreamChunk.Error("OpenAI Responses stream ended before completion", 502))
                    }
                }
            }
        } catch (error: Exception) {
            val status = ProviderAdapter.upstreamFailureStatus(error)
            emit(NeutralStreamChunk.Error("OpenAI Responses request failed: ${error.message ?: "unknown error"}", status, responseStarted = responseStarted))
        }
    }

    private fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = mutableListOf<Pair<com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition, JsonObject>>()
        for (tool in request.tools) {
            val schema = parseJsonObject(tool.parametersJson, "OpenAI tool ${tool.name} parameters")
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
                                put("url", "data:${content.mimeType};base64,${content.base64Data}")
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
            val usage = parseUsage(root)
            val chunks = mutableListOf<NeutralStreamChunk>()
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
                                ?: "call_${choiceIndex}_$toolPosition",
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
            Result.failure(IllegalArgumentException("Failed to parse OpenAI stream chunk: ${error.message}", error))
        }
    }

    private fun parseNonStreamingResponse(data: String): Result<List<NeutralStreamChunk>> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val usage = parseUsage(root)
            val chunks = mutableListOf<NeutralStreamChunk>()
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
                                ?: "call_${choiceIndex}_$toolPosition",
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
            Result.failure(IllegalArgumentException("Failed to parse OpenAI response: ${error.message}", error))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<NeutralStreamChunk>.emitApiError(
        response: HttpResponse,
        providerName: String
    ) {
        val bodyResult = ProviderAdapter.readLimitedResponseText(response)
        val rawBody = bodyResult.getOrElse { "<${it.message ?: "response body unavailable"}>" }
        val extractedMessage = runCatching {
            val element = json.parseToJsonElement(rawBody)
            val jsonObject = element.jsonObject
            val errObj = jsonObject["error"]?.jsonObject
            errObj?.get("message")?.jsonPrimitive?.content ?: jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val displayMessage = extractedMessage ?: rawBody
        val status = bodyResult.exceptionOrNull()
            ?.let(ProviderAdapter::upstreamFailureStatus)
            ?: response.status.value
        emit(
            NeutralStreamChunk.Error(
                "$providerName API error (${response.status.value}): $displayMessage",
                status
            )
        )
    }

    private fun parseUsage(root: JsonObject): NeutralUsage? {
        val usage = root["usage"]?.jsonObject ?: return null
        fun long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
            usage[key]?.jsonPrimitive?.longOrNull
        }
        val prompt = long("prompt_tokens", "input_tokens")
        val completion = long("completion_tokens", "output_tokens")
        val reasoning = long("reasoning_tokens")
        val cached = usage["prompt_tokens_details"]?.jsonObject
            ?.get("cached_tokens")?.jsonPrimitive?.longOrNull
            ?: long("cache_read_input_tokens", "cached_tokens")
        val cacheWrite = long("cache_creation_input_tokens", "cache_write_input_tokens")
        val validCacheBreakdown = prompt != null &&
                (cached ?: 0L) + (cacheWrite ?: 0L) <= prompt
        val validReasoningBreakdown = completion != null &&
                (reasoning ?: 0L) <= completion
        val computedTotal = prompt?.plus(completion ?: 0L)
        val reportedTotal = long("total_tokens")
        return NeutralUsage(
            inputTokens = prompt?.let { total ->
                if (validCacheBreakdown) total - (cached ?: 0L) - (cacheWrite ?: 0L) else total
            },
            outputTokens = completion?.let { total ->
                if (validReasoningBreakdown) total - (reasoning ?: 0L) else total
            },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            cacheWriteTokens = cacheWrite.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            totalTokens = reportedTotal?.takeIf { computedTotal == null || it >= computedTotal } ?: computedTotal
        )
    }

    /** OpenAI 兼容服务有时把 content 编码成多模态数组；不能强制按 primitive 读取。 */
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

    private fun authHeaders(provider: Provider): Map<String, String> {
        return provider.apiKey
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }
            ?: emptyMap()
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
        val queryIndex = base.indexOf('?')
        if (queryIndex < 0) return if (base.endsWith(path)) base else "$base$path"
        val pathPart = base.substring(0, queryIndex)
        val queryPart = base.substring(queryIndex)
        return if (pathPart.endsWith(path)) base else "${pathPart.trimEnd('/')}$path$queryPart"
    }

    /** 从 chat/responses 同源端点推导 OpenAI images 端点，并保留原有 query 参数。 */
    private fun imageGenerationUrl(provider: Provider, model: String): String {
        val configured = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: return normalizeUrl(provider.effectiveBaseUrl, "/images/generations")
        return runCatching {
            val uri = java.net.URI(configured)
            val path = uri.path.trimEnd('/')
            val targetPath = if (path.endsWith("/images/generations")) {
                path
            } else {
                val basePath = path
                    .removeSuffix("/chat/completions")
                    .removeSuffix("/responses")
                "${basePath.ifBlank { "/v1" }}/images/generations"
            }
            java.net.URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                targetPath,
                uri.query,
                uri.fragment
            ).toString()
        }.getOrElse {
            normalizeUrl(provider.effectiveBaseUrl, "/images/generations")
        }
    }

    private fun imageSize(config: JsonElement?): String? {
        val objectConfig = config as? JsonObject ?: return null
        objectConfig["size"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        return when (objectConfig["aspectRatio"]?.jsonPrimitive?.contentOrNull
            ?: objectConfig["aspect_ratio"]?.jsonPrimitive?.contentOrNull) {
            "1:1" -> "1024x1024"
            "16:9" -> "1536x1024"
            "9:16" -> "1024x1536"
            "4:3" -> "1024x768"
            "3:4" -> "768x1024"
            "21:9" -> "1792x768"
            "9:21" -> "768x1792"
            else -> null
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
