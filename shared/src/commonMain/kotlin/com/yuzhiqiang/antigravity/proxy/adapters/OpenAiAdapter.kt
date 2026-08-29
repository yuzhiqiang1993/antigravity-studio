package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

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
            emitAll(OpenAiImageHandler.sendImageGeneration(provider, request, OpenAiAdapterUtils.authHeaders(provider)))
            return@flow
        }

        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: OpenAiAdapterUtils.normalizeUrl(provider.effectiveBaseUrl, "/chat/completions")
        val requestBodyResult = OpenAiChatCompletionsCodec.buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid OpenAI request: " + (requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"),
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
                    ProviderAdapter.applyHeaders(this, provider, OpenAiAdapterUtils.authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBody.toString())
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        OpenAiAdapterUtils.emitApiError(this, response, "OpenAI", json)
                        return@execute
                    }
                    responseStarted = true

                if (!request.stream) {
                    val responseBody = ProviderAdapter.readResponseBodyText(response)
                    if (responseBody.isFailure) {
                        emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "Failed to read OpenAI response body", 502))
                        return@execute
                    }
                    val parsed = OpenAiChatCompletionsCodec.parseNonStreamingResponse(responseBody.getOrThrow())
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
                    val parsed = OpenAiChatCompletionsCodec.parseChunk(data)
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
                                    "OpenAI tool call choice " + chunk.choiceIndex + " index " + chunk.index + " received data after it ended",
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
                if (!sawCompletion && !streamEnded) {
                    emit(
                        NeutralStreamChunk.Error(
                            "OpenAI stream ended before completion",
                            502,
                            responseStarted = true
                        )
                    )
                }
                }
            }
        } catch (error: Exception) {
            val status = ProviderAdapter.upstreamFailureStatus(error)
            emit(NeutralStreamChunk.Error("OpenAI request failed: " + (error.message ?: "unknown error"), status, responseStarted = responseStarted))
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
            ?: OpenAiAdapterUtils.normalizeUrl(provider.effectiveBaseUrl, "/models"))
        return try {
            val response = ProviderAdapter.sharedHttpClient.get(url) {
                ProviderAdapter.applyHeaders(this, provider, OpenAiAdapterUtils.authHeaders(provider))
                ProviderAdapter.applyTimeouts(this, provider, streaming = false)
            }
            val bodyResult = ProviderAdapter.readLimitedResponseText(response)
            val body = bodyResult.getOrNull()
            if (!response.status.isSuccess()) {
                return ProviderAdapter.ModelCatalogResult(
                    rawBody = body,
                    errorMessage = "HTTP " + response.status.value
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

    private fun sendResponsesStream(
        provider: Provider,
        request: NeutralChatRequest
    ): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: OpenAiAdapterUtils.normalizeUrl(provider.effectiveBaseUrl, "/responses")
        val requestBodyResult = OpenAiResponsesCodec.buildRequestBody(request)
        if (requestBodyResult.isFailure) {
            emit(
                NeutralStreamChunk.Error(
                    "Invalid OpenAI Responses request: " + (requestBodyResult.exceptionOrNull()?.message ?: "invalid JSON"),
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
                    ProviderAdapter.applyHeaders(this, provider, OpenAiAdapterUtils.authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, request.stream)
                    setBody(requestBodyResult.getOrThrow().toString())
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        OpenAiAdapterUtils.emitApiError(this, response, "OpenAI Responses", json)
                        return@execute
                    }
                    responseStarted = true
                    if (!request.stream) {
                        val responseBody = ProviderAdapter.readResponseBodyText(response)
                        if (responseBody.isFailure) {
                            emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "Failed to read OpenAI Responses body", 502))
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
            emit(NeutralStreamChunk.Error("OpenAI Responses request failed: " + (error.message ?: "unknown error"), status, responseStarted = responseStarted))
        }
    }
}
