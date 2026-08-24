package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
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

class GeminiAdapter : ProviderAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val stream = request.stream
        val url = buildGenerateUrl(provider, model, stream)
        val requestBodyResult = buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid Gemini request: ${requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"}",
                    502
                )
            )
            return@flow
        }
        val requestBody = requestBodyResult.getOrThrow()
        val minimumRequestTimeoutMs = if (request.outputModalities.contains(com.yuzhiqiang.antigravity.domain.model.ModelModality.IMAGE)) {
            120_000L
        } else {
            0L
        }
        var responseStarted = false
        try {
            val response = ProviderAdapter.executeWithResponseHeadersTimeout(
                provider,
                stream,
                minimumRequestTimeoutMs = minimumRequestTimeoutMs
            ) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(
                        this,
                        provider,
                        stream,
                        minimumRequestTimeoutMs = minimumRequestTimeoutMs
                    )
                    setBody(requestBody.toString())
                }.execute()
            }

            if (!response.status.isSuccess()) {
                val bodyResult = ProviderAdapter.readLimitedResponseText(response)
                val body = bodyResult.getOrElse { "<${it.message ?: "response body unavailable"}>" }
                val status = bodyResult.exceptionOrNull()
                    ?.let(ProviderAdapter::upstreamFailureStatus)
                    ?: response.status.value
                emit(
                    NeutralStreamChunk.Error(
                        "Gemini API error (${response.status.value}): $body",
                        status
                    )
                )
                return@flow
            }
            responseStarted = true

            if (stream) {
                val channel: ByteReadChannel = response.body()
                var streamEnded = false
                var sawCompletion = false
                while (!channel.isClosedForRead && !streamEnded) {
                    val event = ProviderAdapter.readSseDataEvent(channel)
                    if (event.isFailure) {
                        emit(NeutralStreamChunk.Error(event.exceptionOrNull()?.message ?: "Invalid Gemini SSE frame", 502, responseStarted = true))
                        return@flow
                    }
                    val data = event.getOrNull() ?: break
                    val parsed = parseResponse(data)
                    if (parsed.isFailure) {
                        emit(
                            NeutralStreamChunk.Error(
                                parsed.exceptionOrNull()?.message ?: "Invalid Gemini stream chunk",
                                502,
                                responseStarted = true
                            )
                        )
                        return@flow
                    }
                    parsed.getOrThrow().forEach { chunk ->
                        val effectiveChunk = if (chunk is NeutralStreamChunk.Error) {
                            chunk.copy(responseStarted = true)
                        } else {
                            chunk
                        }
                        if (effectiveChunk is NeutralStreamChunk.Completed) sawCompletion = true
                        if (effectiveChunk is NeutralStreamChunk.Error) streamEnded = true
                        emit(effectiveChunk)
                        if (streamEnded) return@forEach
                    }
                }
                if (!sawCompletion && !streamEnded) emit(NeutralStreamChunk.Completed())
            } else {
                val responseBody = ProviderAdapter.readLimitedResponseText(response)
                if (responseBody.isFailure) {
                    emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "Gemini response body exceeds 4 MiB buffered limit", 502))
                    return@flow
                }
                val parsed = parseResponse(responseBody.getOrThrow())
                if (parsed.isFailure) {
                    emit(NeutralStreamChunk.Error(parsed.exceptionOrNull()?.message ?: "Invalid Gemini response", 502))
                    return@flow
                }
                parsed.getOrThrow().forEach { emit(it) }
                if (parsed.getOrThrow().none { it is NeutralStreamChunk.Completed }) {
                    emit(NeutralStreamChunk.Completed())
                }
            }
        } catch (error: Exception) {
            val status = ProviderAdapter.upstreamFailureStatus(error)
            emit(NeutralStreamChunk.Error("Gemini request failed: ${error.message ?: "unknown error"}", status, responseStarted = responseStarted))
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
            ?: appendPathBeforeQuery(provider.effectiveBaseUrl.trimEnd('/'), "/models"))
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

    private fun buildGenerateUrl(provider: Provider, model: String, stream: Boolean): String {
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val rawEndpoint = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: appendPathBeforeQuery(
                provider.effectiveBaseUrl.trimEnd('/'),
                "/models/$model:$method"
            )
        return normalizeGenerateEndpoint(rawEndpoint, method, stream)
    }

    /** 对齐 byok：切换 stream/non-stream 时只调整 action 与 alt=sse，不破坏自定义 query。 */
    private fun normalizeGenerateEndpoint(rawEndpoint: String, method: String, stream: Boolean): String {
        return runCatching {
            val uri = java.net.URI(rawEndpoint)
            val currentPath = uri.path.trimEnd('/')
            val resolvedPath = when {
                currentPath.endsWith(":generateContent") ->
                    currentPath.removeSuffix(":generateContent") + ":$method"

                currentPath.endsWith(":streamGenerateContent") ->
                    currentPath.removeSuffix(":streamGenerateContent") + ":$method"

                else -> currentPath
            }
            val queryParts = uri.rawQuery
                ?.split('&')
                ?.filter { it.isNotBlank() && !it.substringBefore('=').equals("alt", ignoreCase = true) }
                ?.toMutableList()
                ?: mutableListOf()
            if (stream) queryParts += "alt=sse"
            val authority = uri.rawAuthority ?: return@runCatching rawEndpoint
            buildString {
                append(uri.scheme).append("://").append(authority)
                append(if (resolvedPath.isBlank()) "/" else resolvedPath)
                if (queryParts.isNotEmpty()) append('?').append(queryParts.joinToString("&"))
                uri.rawFragment?.let { append('#').append(it) }
            }
        }.getOrElse {
            rawEndpoint
                .replace(":streamGenerateContent", ":$method")
                .replace(":generateContent", ":$method")
        }
    }

    private fun appendPathBeforeQuery(base: String, path: String): String {
        val queryIndex = base.indexOf('?')
        return if (queryIndex < 0) {
            base.trimEnd('/') + path
        } else {
            base.substring(0, queryIndex).trimEnd('/') + path + base.substring(queryIndex)
        }
    }

    private fun buildRequestBody(request: NeutralChatRequest): Result<JsonObject> {
        val tools = mutableListOf<Pair<com.yuzhiqiang.antigravity.proxy.model.NeutralToolDefinition, JsonObject>>()
        for (tool in request.tools) {
            val schema = parseJsonObject(tool.parametersJson, "Gemini tool ${tool.name} parameters")
            if (schema.isFailure) {
                return Result.failure(schema.exceptionOrNull() ?: IllegalArgumentException("Invalid tool schema"))
            }
            tools.add(tool to schema.getOrThrow())
        }

        val contents = mutableListOf<JsonObject>()
        for (message in request.messages) {
            if (message.role == NeutralRole.SYSTEM) continue
            val parts = mutableListOf<JsonElement>()
            for (content in message.contents) {
                when (content) {
                    is NeutralContent.Text -> parts.add(buildJsonObject { put("text", content.text) })
                    is NeutralContent.Thinking -> parts.add(buildJsonObject {
                        put("thought", true)
                        put("text", content.text)
                        content.signature?.let { put("thoughtSignature", it) }
                    })

                    is NeutralContent.Image -> parts.add(buildJsonObject {
                        put("inlineData", buildJsonObject {
                            put("mimeType", content.mimeType)
                            put("data", content.base64Data)
                        })
                    })

                    is NeutralContent.ToolCall -> {
                        val args =
                            parseJsonObject(content.argumentsJson, "Gemini tool ${content.functionName} arguments")
                        if (args.isFailure) {
                            return Result.failure(
                                args.exceptionOrNull() ?: IllegalArgumentException("Invalid tool arguments")
                            )
                        }
                        parts.add(buildJsonObject {
                            put("functionCall", buildJsonObject {
                                put("id", content.id)
                                put("name", content.functionName)
                                put("args", args.getOrThrow())
                            })
                        })
                    }

                    is NeutralContent.ToolResult -> {
                        val response = parseJsonElement(
                            content.content,
                            "Gemini tool ${content.functionName ?: "function"} response"
                        ).getOrElse {
                            // byok 对纯文本工具结果包装成对象，避免一次工具调用
                            // 因为上游返回的非 JSON 文本而整条会话失败。
                            buildJsonObject { put("result", content.content) }
                        }
                        parts.add(buildJsonObject {
                            put("functionResponse", buildJsonObject {
                                put("name", content.functionName ?: content.toolCallId)
                                put("id", content.toolCallId)
                                put("response", response)
                            })
                        })
                    }
                }
            }
            contents.add(buildJsonObject {
                put("role", if (message.role == NeutralRole.ASSISTANT) "model" else "user")
                put("parts", JsonArray(parts))
            })
        }

        val baseBody = buildJsonObject {
            put("contents", JsonArray(contents))
            request.systemPrompt?.let { system ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                })
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            tools.forEach { (tool, schema) ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", schema)
                                })
                            }
                        })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                request.temperature?.let { put("temperature", it) }
                request.maxTokens?.let { put("maxOutputTokens", it) }
                request.topP?.let { put("topP", it) }
                request.topK?.let { put("topK", it) }
                request.reasoningMapping?.let { mapping ->
                    when (mapping.kind.lowercase()) {
                        "disabled" -> put("thinkingConfig", buildJsonObject {
                            put("thinkingBudget", 0)
                        })

                        "budget_tokens" -> ReasoningMappingSupport.mappingValueAsInt(mapping)
                            ?.let { budget ->
                                put("thinkingConfig", buildJsonObject {
                                    put("thinkingBudget", budget)
                                })
                            }

                        "native_level" -> ReasoningMappingSupport.mappingValueAsString(mapping)
                            ?.let { level ->
                                put("thinkingConfig", buildJsonObject {
                                    put("thinkingLevel", level)
                                })
                            }
                    }
                }
                if (request.outputModalities.isNotEmpty()) {
                    put("responseModalities", buildJsonArray {
                        request.outputModalities.forEach { modality ->
                            add(JsonPrimitive(modality.name.uppercase()))
                        }
                    })
                }
                request.imageGenerationConfig?.let { config ->
                    put("imageConfig", config)
                }
            })
        }
        return Result.success(ProviderAdapter.mergeSafeExtraBody(baseBody, request))
    }

    private fun parseResponse(data: String): Result<List<NeutralStreamChunk>> {
        if (data == "[DONE]") return Result.success(listOf(NeutralStreamChunk.Completed()))
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            root["error"]?.jsonObject?.let { error ->
                return Result.success(
                    listOf(
                        NeutralStreamChunk.Error(
                            error["message"]?.jsonPrimitive?.contentOrNull ?: "Gemini response error",
                            error["code"]?.jsonPrimitive?.intOrNull ?: 502
                        )
                    )
                )
            }
            val chunks = mutableListOf<NeutralStreamChunk>()
            val usage = parseUsage(root)
            val candidates = root["candidates"]?.jsonArray ?: JsonArray(emptyList())
            candidates.forEach { candidateElement ->
                val candidate = candidateElement.jsonObject
                val index = candidate["index"]?.jsonPrimitive?.intOrNull ?: 0
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
                parts.forEachIndexed { partIndex, partElement ->
                    val part = partElement.jsonObject
                    part["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                        if (part["thought"]?.jsonPrimitive?.contentOrNull == "true") {
                            chunks.add(
                                NeutralStreamChunk.ReasoningDelta(
                                    text,
                                    part["thoughtSignature"]?.jsonPrimitive?.contentOrNull,
                                    index
                                )
                            )
                        } else {
                            chunks.add(NeutralStreamChunk.TextDelta(text, index))
                        }
                    }
                    if (part["text"] == null) {
                        part["thoughtSignature"]?.jsonPrimitive?.contentOrNull?.let { signature ->
                            chunks.add(NeutralStreamChunk.ReasoningDelta("", signature, index))
                        }
                    }
                    part["inlineData"]?.jsonObject?.let { inline ->
                        chunks.add(
                            NeutralStreamChunk.InlineDataDelta(
                                inline["mimeType"]?.jsonPrimitive?.contentOrNull ?: "application/octet-stream",
                                inline["data"]?.jsonPrimitive?.contentOrNull ?: "",
                                index
                            )
                        )
                    }
                    part["functionCall"]?.jsonObject?.let { call ->
                        chunks.add(
                            NeutralStreamChunk.ToolCallDelta(
                                index = partIndex,
                                id = call["id"]?.jsonPrimitive?.contentOrNull
                                    ?: "call_${index}_$partIndex",
                                name = call["name"]?.jsonPrimitive?.contentOrNull,
                                argsText = (call["args"] as? JsonPrimitive)?.contentOrNull
                                    ?: call["args"]?.toString()
                                    ?: "{}",
                                choiceIndex = index
                            )
                        )
                    }
                }
                candidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.Completed(it, usage, index))
                }
            }
            val blockReason = if (candidates.isEmpty()) {
                root["promptFeedback"]?.jsonObject
                    ?.get("blockReason")
                    ?.jsonPrimitive
                    ?.contentOrNull
            } else {
                null
            }
            if (blockReason != null) {
                chunks.add(NeutralStreamChunk.Completed(blockReason, usage))
            } else if (usage != null && chunks.none { it is NeutralStreamChunk.Completed }) {
                chunks.add(NeutralStreamChunk.Completed(null, usage))
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse Gemini response: ${error.message}", error))
        }
    }

    private fun parseUsage(root: JsonObject): NeutralUsage? {
        val usage = root["usageMetadata"]?.jsonObject ?: return null
        fun long(key: String): Long? = usage[key]?.jsonPrimitive?.longOrNull
        val prompt = long("promptTokenCount")
        val cached = long("cachedContentTokenCount")
        val reasoning = long("thoughtsTokenCount")
        val output = long("candidatesTokenCount")
        val validCacheBreakdown = prompt != null && (cached ?: 0L) <= prompt
        val validReasoningBreakdown = output != null && (reasoning ?: 0L) <= output
        val computedTotal = prompt?.plus((output ?: 0L) + (reasoning ?: 0L))
        val reportedTotal = long("totalTokenCount")
        return NeutralUsage(
            inputTokens = prompt?.let { total -> if (validCacheBreakdown) total - (cached ?: 0L) else total },
            outputTokens = output?.let { total -> if (validReasoningBreakdown) total - (reasoning ?: 0L) else total },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            totalTokens = reportedTotal?.takeIf { computedTotal == null || it >= computedTotal } ?: computedTotal
        )
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

    private fun parseJsonElement(value: String, label: String): Result<JsonElement> {
        return try {
            Result.success(json.parseToJsonElement(value))
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("$label is invalid JSON: ${error.message}", error))
        }
    }

    private fun authHeaders(provider: Provider): Map<String, String> {
        return provider.apiKey
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("x-goog-api-key" to it) }
            ?: emptyMap()
    }
}
