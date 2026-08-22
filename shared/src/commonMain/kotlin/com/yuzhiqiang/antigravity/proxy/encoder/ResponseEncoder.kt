package com.yuzhiqiang.antigravity.proxy.encoder

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 将中立流事件编码为 Gemini/Cloud Code 响应。 */
object ResponseEncoder {
    private val json = Json { ignoreUnknownKeys = true }

    /** 每个请求必须创建独立实例，工具调用参数不会在请求间共享。 */
    fun newStreamEncoder(
        cloudCodeEnvelope: Boolean = false,
        modelVersion: String? = null
    ): GeminiStreamEncoder {
        return GeminiStreamEncoder(cloudCodeEnvelope, modelVersion)
    }

    /** 兼容无状态调用方；需要聚合工具参数时应使用 [newStreamEncoder]。 */
    fun encodeChunkToGeminiSse(chunk: NeutralStreamChunk): String {
        return when (chunk) {
            is NeutralStreamChunk.ToolCallDelta -> {
                val args = parseArguments(chunk.argsText)
                if (args.isFailure) {
                    sse(errorPayload(502, args.exceptionOrNull()?.message ?: "Invalid tool arguments", false))
                } else {
                    val payload = toolCallPayload(0, chunk.id, chunk.name, args.getOrThrow())
                    sse(payload)
                }
            }

            else -> GeminiStreamEncoder(false, null).encode(chunk).joinToString("")
        }
    }

    /** 将非流式中立事件聚合为一个 Gemini JSON 响应。 */
    fun encodeChunksToGeminiJson(
        chunks: List<NeutralStreamChunk>,
        modelVersion: String? = null,
        cloudCodeEnvelope: Boolean = false
    ): String {
        return encodeChunksToGeminiJsonResult(chunks, modelVersion, cloudCodeEnvelope).getOrElse { error ->
            encodeErrorToGeminiJson(error.message ?: "Invalid tool arguments", 502, cloudCodeEnvelope)
        }
    }

    /** 非流式编码结果，工具参数解析失败时保留可供服务器识别的失败状态。 */
    fun encodeChunksToGeminiJsonResult(
        chunks: List<NeutralStreamChunk>,
        modelVersion: String? = null,
        cloudCodeEnvelope: Boolean = false
    ): Result<String> {
        val parts = mutableListOf<JsonElement>()
        val toolCalls = linkedMapOf<Int, PendingToolCall>()
        var finishReason: String? = null
        var error: NeutralStreamChunk.Error? = null
        chunks.forEach { chunk ->
            when (chunk) {
                is NeutralStreamChunk.TextDelta -> parts.add(buildJsonObject { put("text", chunk.text) })
                is NeutralStreamChunk.ReasoningDelta -> parts.add(
                    buildJsonObject {
                        put("thought", true)
                        put("text", chunk.thinkingText)
                        chunk.signature?.let { put("thoughtSignature", it) }
                    }
                )

                is NeutralStreamChunk.InlineDataDelta -> parts.add(
                    buildJsonObject {
                        put("inlineData", buildJsonObject {
                            put("mimeType", chunk.mimeType)
                            put("data", chunk.base64Data)
                        })
                    }
                )

                is NeutralStreamChunk.ToolCallDelta -> {
                    val pending = toolCalls.getOrPut(chunk.index) { PendingToolCall() }
                    if (!chunk.id.isNullOrBlank()) pending.id = chunk.id
                    if (!chunk.name.isNullOrBlank()) pending.name = chunk.name
                    pending.arguments.append(chunk.argsText)
                }

                is NeutralStreamChunk.Completed -> finishReason = finishReasonValue(chunk.finishReason)
                is NeutralStreamChunk.Error -> error = chunk
            }
        }
        if (error != null) {
            return Result.success(errorPayload(error.statusCode, error.message, cloudCodeEnvelope).toString())
        }
        toolCalls.toSortedMap().forEach { (_, call) ->
            val arguments = parseArguments(call.arguments.toString())
            if (arguments.isFailure) {
                return Result.failure(
                    arguments.exceptionOrNull() ?: IllegalArgumentException("Invalid tool arguments")
                )
            }
            parts.add(functionCallPart(call, arguments.getOrThrow()))
        }
        val effectiveFinishReason = if (toolCalls.isNotEmpty()) {
            "TOOL_CALL"
        } else {
            finishReason
        }
        val response = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", JsonArrayBuilderCompat.from(parts))
                    })
                    effectiveFinishReason?.let { put("finishReason", it) }
                })
            })
            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
        }
        return Result.success(if (cloudCodeEnvelope) wrapEnvelope(response).toString() else response.toString())
    }

    fun encodeErrorToGeminiJson(message: String, statusCode: Int, cloudCodeEnvelope: Boolean = false): String {
        return errorPayload(statusCode, message, cloudCodeEnvelope).toString()
    }

    private fun finishReasonValue(value: String?): String {
        return when (value?.uppercase()) {
            "MAX_TOKENS", "MAX_OUTPUT_TOKENS", "MAX_OUTPUT_TOKEN", "LENGTH", "INCOMPLETE" -> "MAX_TOKENS"
            "TOOL_CALL", "TOOL_USE", "FUNCTION_CALL", "FUNCTION_CALLS" -> "TOOL_CALL"
            "SAFETY", "CONTENT_FILTER" -> "SAFETY"
            "OTHER" -> "OTHER"
            else -> "STOP"
        }
    }

    private fun parseArguments(value: String): Result<JsonObject> {
        if (value.isBlank()) return Result.success(JsonObject(emptyMap()))
        return try {
            val parsed = json.parseToJsonElement(value)
            if (parsed is JsonObject) {
                Result.success(parsed)
            } else {
                Result.failure(IllegalArgumentException("Tool arguments must be a JSON object"))
            }
        } catch (error: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse tool arguments JSON: ${error.message}", error))
        }
    }

    private fun functionCallPart(call: PendingToolCall, arguments: JsonObject): JsonElement {
        return buildJsonObject {
            put("functionCall", buildJsonObject {
                call.id?.let { put("id", it) }
                put("name", call.name ?: "")
                put("args", arguments)
            })
        }
    }

    private fun toolCallPayload(candidateIndex: Int, id: String?, name: String?, arguments: JsonObject): JsonObject {
        return buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("index", candidateIndex)
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("functionCall", buildJsonObject {
                                    id?.let { put("id", it) }
                                    put("name", name ?: "")
                                    put("args", arguments)
                                })
                            })
                        })
                    })
                })
            })
        }
    }

    private fun errorPayload(statusCode: Int, message: String, cloudCodeEnvelope: Boolean): JsonObject {
        val payload = buildJsonObject {
            put("error", buildJsonObject {
                put("code", statusCode)
                put("message", message)
            })
        }
        return if (cloudCodeEnvelope) wrapEnvelope(payload) else payload
    }

    private fun sse(payload: JsonObject): String = "data: ${payload}\n\n"

    private fun wrapEnvelope(payload: JsonObject): JsonObject {
        return buildJsonObject {
            put("response", payload)
            put("traceId", "")
            put("metadata", buildJsonObject {})
        }
    }

    private data class PendingToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )

    /** 工具调用参数在请求内聚合，并确保 Completed/DONE 只发一次。 */
    class GeminiStreamEncoder internal constructor(
        private val cloudCodeEnvelope: Boolean,
        private var modelVersion: String?
    ) {
        private val pendingToolCalls = linkedMapOf<Int, PendingToolCall>()
        private var completed = false
        var failureStatusCode: Int? = null
            private set
        var failureMessage: String? = null
            private set

        fun encode(chunk: NeutralStreamChunk): List<String> {
            if (completed) return emptyList()
            return when (chunk) {
                is NeutralStreamChunk.TextDelta -> listOf(sse(wrapIfNeeded(textPayload(0, chunk.text))))
                is NeutralStreamChunk.ReasoningDelta -> listOf(
                    sse(wrapIfNeeded(thinkingPayload(0, chunk.thinkingText, chunk.signature)))
                )

                is NeutralStreamChunk.InlineDataDelta -> listOf(
                    sse(
                        wrapIfNeeded(
                            inlinePayload(
                                0,
                                chunk.mimeType,
                                chunk.base64Data
                            )
                        )
                    )
                )

                is NeutralStreamChunk.ToolCallDelta -> encodeToolCallDelta(chunk)
                is NeutralStreamChunk.Error -> {
                    failureStatusCode = chunk.statusCode
                    failureMessage = chunk.message
                    completed = true
                    listOf(sse(errorPayload(chunk.statusCode, chunk.message, cloudCodeEnvelope)))
                }

                is NeutralStreamChunk.Completed -> encodeCompleted(chunk)
            }
        }

        private fun encodeToolCallDelta(chunk: NeutralStreamChunk.ToolCallDelta): List<String> {
            val pending = pendingToolCalls.getOrPut(chunk.index) { PendingToolCall() }
            if (!chunk.id.isNullOrBlank()) pending.id = chunk.id
            if (!chunk.name.isNullOrBlank()) pending.name = chunk.name
            pending.arguments.append(chunk.argsText)
            return emptyList()
        }

        private fun encodeCompleted(chunk: NeutralStreamChunk.Completed): List<String> {
            val frames = mutableListOf<String>()
            pendingToolCalls.toSortedMap().forEach { (_, pending) ->
                val arguments = parseArguments(pending.arguments.toString())
                if (arguments.isFailure) {
                    failureStatusCode = 502
                    failureMessage = arguments.exceptionOrNull()?.message ?: "Invalid tool arguments"
                    pendingToolCalls.clear()
                    completed = true
                    return listOf(
                        sse(
                            errorPayload(
                                502,
                                failureMessage ?: "Invalid tool arguments",
                                cloudCodeEnvelope
                            )
                        )
                    )
                }
                frames.add(sse(wrapIfNeeded(toolCallPayload(0, pending.id, pending.name, arguments.getOrThrow()))))
            }
            pendingToolCalls.clear()
            val finishReason = if (frames.isNotEmpty()) "TOOL_CALL" else finishReasonValue(chunk.finishReason)
            val finishPayload = buildJsonObject {
                put("candidates", buildJsonArray {
                    add(buildJsonObject {
                        put("index", 0)
                        put("finishReason", finishReason)
                    })
                })
                modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
            }
            frames.add(sse(wrapIfNeeded(finishPayload)))
            if (!cloudCodeEnvelope) frames.add("data: [DONE]\n\n")
            completed = true
            return frames
        }

        private fun wrapIfNeeded(payload: JsonObject): JsonObject {
            return if (cloudCodeEnvelope) wrapEnvelope(payload) else payload
        }

        private fun textPayload(index: Int, text: String): JsonObject = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("index", index)
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
                    })
                })
            })
            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
        }

        private fun thinkingPayload(index: Int, text: String, signature: String?): JsonObject = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("index", index)
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("thought", true)
                                put("text", text)
                                signature?.let { put("thoughtSignature", it) }
                            })
                        })
                    })
                })
            })
            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
        }

        private fun inlinePayload(index: Int, mimeType: String, data: String): JsonObject = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("index", index)
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", mimeType)
                                    put("data", data)
                                })
                            })
                        })
                    })
                })
            })
            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
        }
    }

    private object JsonArrayBuilderCompat {
        fun from(elements: List<JsonElement>): kotlinx.serialization.json.JsonArray {
            return kotlinx.serialization.json.JsonArray(elements)
        }
    }
}
