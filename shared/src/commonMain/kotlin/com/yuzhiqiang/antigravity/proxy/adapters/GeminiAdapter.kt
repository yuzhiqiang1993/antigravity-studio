package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
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
        try {
            val response = ProviderAdapter.executeWithResponseHeadersTimeout(provider, stream) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                    ProviderAdapter.applyTimeouts(this, provider, stream)
                    setBody(requestBody.toString())
                }.execute()
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                emit(
                    NeutralStreamChunk.Error(
                        "Gemini API error (${response.status.value}): $body",
                        response.status.value
                    )
                )
                return@flow
            }

            if (stream) {
                val channel: ByteReadChannel = response.body()
                var completed = false
                while (!channel.isClosedForRead && !completed) {
                    val line = channel.readUTF8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (trimmed.startsWith("event:")) continue
                    if (!trimmed.startsWith("data:")) {
                        emit(NeutralStreamChunk.Error("Gemini stream frame is missing data field", 502))
                        return@flow
                    }
                    val data = trimmed.removePrefix("data:").trim()
                    val parsed = parseResponse(data)
                    if (parsed.isFailure) {
                        emit(
                            NeutralStreamChunk.Error(
                                parsed.exceptionOrNull()?.message ?: "Invalid Gemini stream chunk", 502
                            )
                        )
                        return@flow
                    }
                    parsed.getOrThrow().forEach { chunk -> emit(chunk) }
                    if (parsed.getOrThrow().any { it is NeutralStreamChunk.Completed }) completed = true
                }
                if (!completed) emit(NeutralStreamChunk.Completed())
            } else {
                val parsed = parseResponse(response.bodyAsText())
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
            emit(NeutralStreamChunk.Error("Gemini request failed: ${error.message ?: "unknown error"}", 502))
        }
    }

    override suspend fun testConnection(provider: Provider): Boolean {
        return fetchModels(provider).isNotEmpty()
    }

    override suspend fun fetchModels(provider: Provider): List<String> {
        val url = provider.modelsEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "${provider.effectiveBaseUrl.trimEnd('/')}/models"
        return try {
            val response = ProviderAdapter.sharedHttpClient.get(url) {
                ProviderAdapter.applyHeaders(this, provider, authHeaders(provider))
                ProviderAdapter.applyTimeouts(this, provider, streaming = false)
            }
            if (!response.status.isSuccess()) return emptyList()
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            root["models"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildGenerateUrl(provider: Provider, model: String, stream: Boolean): String {
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val customEndpoint = provider.generateEndpoint?.trim()?.takeIf { it.isNotEmpty() }
        if (customEndpoint != null) {
            return customEndpoint
                .replace("{model}", model)
                .replace(":streamGenerateContent", ":$method")
                .replace(":generateContent", ":$method")
        }
        val base = provider.effectiveBaseUrl.trimEnd('/')
        val separator = if (base.contains("?")) "&" else "?"
        return "$base/models/$model:$method${separator}alt=${if (stream) "sse" else "json"}"
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
                        )
                        if (response.isFailure) {
                            return Result.failure(
                                response.exceptionOrNull() ?: IllegalArgumentException("Invalid tool response")
                            )
                        }
                        parts.add(buildJsonObject {
                            put("functionResponse", buildJsonObject {
                                content.functionName?.let { put("name", it) }
                                put("id", content.toolCallId)
                                put("response", response.getOrThrow())
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
                                    part["thoughtSignature"]?.jsonPrimitive?.contentOrNull
                                )
                            )
                        } else {
                            chunks.add(NeutralStreamChunk.TextDelta(text))
                        }
                    }
                    part["inlineData"]?.jsonObject?.let { inline ->
                        chunks.add(
                            NeutralStreamChunk.InlineDataDelta(
                                inline["mimeType"]?.jsonPrimitive?.contentOrNull ?: "application/octet-stream",
                                inline["data"]?.jsonPrimitive?.contentOrNull ?: ""
                            )
                        )
                    }
                    part["functionCall"]?.jsonObject?.let { call ->
                        chunks.add(
                            NeutralStreamChunk.ToolCallDelta(
                                index = partIndex,
                                id = call["id"]?.jsonPrimitive?.contentOrNull,
                                name = call["name"]?.jsonPrimitive?.contentOrNull,
                                argsText = call["args"]?.toString() ?: "{}"
                            )
                        )
                    }
                }
                candidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let {
                    chunks.add(NeutralStreamChunk.Completed(it))
                }
            }
            Result.success(chunks)
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse Gemini response: ${error.message}", error))
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

    private fun parseJsonElement(value: String, label: String): Result<JsonElement> {
        return try {
            Result.success(json.parseToJsonElement(value))
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("$label is invalid JSON: ${error.message}", error))
        }
    }

    private fun authHeaders(provider: Provider): Map<String, String> {
        return provider.apiKey?.let { mapOf("x-goog-api-key" to it) } ?: emptyMap()
    }
}
