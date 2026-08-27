package com.yuzhiqiang.antigravity.proxy.encoder

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
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
                    val payload = toolCallPayload(chunk.choiceIndex, chunk.id, chunk.name, args.getOrThrow())
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
        val candidates = linkedMapOf<Int, CandidateBuffer>()
        var usage: NeutralUsage? = null
        var error: NeutralStreamChunk.Error? = null
        fun candidate(index: Int): CandidateBuffer = candidates.getOrPut(index) { CandidateBuffer() }
        chunks.forEach { chunk ->
            when (chunk) {
                is NeutralStreamChunk.TextDelta -> candidate(chunk.choiceIndex).parts.add(
                    buildJsonObject { put("text", chunk.text) }
                )

                is NeutralStreamChunk.ReasoningDelta -> candidate(chunk.choiceIndex).parts.add(
                    buildJsonObject {
                        put("thought", true)
                        put("text", chunk.thinkingText)
                        chunk.signature?.let { put("thoughtSignature", it) }
                    }
                )

                is NeutralStreamChunk.InlineDataDelta -> candidate(chunk.choiceIndex).parts.add(
                    buildJsonObject {
                        put("inlineData", buildJsonObject {
                            put("mimeType", chunk.mimeType)
                            put("data", chunk.base64Data)
                        })
                    }
                )

                is NeutralStreamChunk.ToolCallDelta -> {
                    val pending = candidate(chunk.choiceIndex).toolCalls.getOrPut(chunk.index) { PendingToolCall() }
                    if (!chunk.id.isNullOrBlank()) pending.id = chunk.id
                    if (!chunk.name.isNullOrBlank()) pending.name = chunk.name
                    pending.arguments.append(chunk.argsText)
                }

                is NeutralStreamChunk.Completed -> {
                    candidate(chunk.choiceIndex).finishReason = finishReasonValue(chunk.finishReason)
                    usage = chunk.usage ?: usage
                }

                is NeutralStreamChunk.Error -> error = chunk
            }
        }
        if (error != null) {
            // byok 的代理错误即使请求来自 Cloud Code envelope 路由，也保持
            // 顶层 error，避免宿主把它误识别成一次成功的 response 包络。
            return Result.success(errorPayload(error.statusCode, error.message, false).toString())
        }
        if (candidates.isEmpty()) candidates[0] = CandidateBuffer()
        return try {
            val response = buildJsonObject {
                put("candidates", buildJsonArray {
                    candidates.toSortedMap().forEach { (choiceIndex, buffer) ->
                        buffer.toolCalls.toSortedMap().forEach { (_, call) ->
                            val arguments = parseArguments(call.arguments.toString())
                            if (arguments.isFailure) {
                                throw arguments.exceptionOrNull()
                                    ?: IllegalArgumentException("Invalid tool arguments")
                            }
                            buffer.parts.add(functionCallPart(call, arguments.getOrThrow()))
                        }
                        val effectiveFinishReason = if (buffer.toolCalls.isNotEmpty()) {
                            "TOOL_CALL"
                        } else {
                            buffer.finishReason
                        }
                        add(buildJsonObject {
                            put("index", choiceIndex)
                            put("content", buildJsonObject {
                                put("role", "model")
                                put("parts", JsonArrayBuilderCompat.from(buffer.parts))
                            })
                            effectiveFinishReason?.let { put("finishReason", it) }
                        })
                    }
                })
                modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                usage?.let { put("usageMetadata", usageMetadata(it)) }
            }
            Result.success(if (cloudCodeEnvelope) wrapEnvelope(response).toString() else response.toString())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun encodeErrorToGeminiJson(message: String, statusCode: Int, cloudCodeEnvelope: Boolean = false): String {
        return errorPayload(statusCode, message, false).toString()
    }

    private fun finishReasonValue(value: String?): String {
        return when (value?.uppercase()) {
            "STOP", "END_TURN", "STOP_SEQUENCE" -> "STOP"
            "MAX_TOKENS", "MAX_OUTPUT_TOKENS", "MAX_OUTPUT_TOKEN", "LENGTH", "INCOMPLETE" -> "MAX_TOKENS"
            "TOOL_CALL", "TOOL_USE", "FUNCTION_CALL", "FUNCTION_CALLS" -> "TOOL_CALL"
            "SAFETY", "CONTENT_FILTER", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT" -> "SAFETY"

            "OTHER" -> "OTHER"
            null -> "STOP"
            else -> "OTHER"
        }
    }

    private fun usageMetadata(usage: NeutralUsage): JsonObject {
        return buildJsonObject {
            val prompt = (usage.inputTokens ?: 0L) +
                    (usage.cacheReadTokens ?: 0L) +
                    (usage.cacheWriteTokens ?: 0L)
            val output = usage.outputTokens ?: 0L
            put("promptTokenCount", prompt)
            put("candidatesTokenCount", output)
            usage.reasoningTokens?.let { put("thoughtsTokenCount", it) }
            put("totalTokenCount", usage.totalTokens ?: prompt + output)
            usage.cacheReadTokens?.let { put("cachedContentTokenCount", it) }
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
                put("category", errorCategory(statusCode, message))
                put("message", message)
            })
        }
        return if (cloudCodeEnvelope) wrapEnvelope(payload) else payload
    }

    private fun errorCategory(statusCode: Int, message: String): String {
        return when {
            message.contains("stream", ignoreCase = true) || message.contains("SSE", ignoreCase = true) ||
                    message.contains(
                        "tool arguments",
                        ignoreCase = true
                    ) || message.contains("流") -> "stream_interrupted"

            message.contains("unsupported", ignoreCase = true) || message.contains("不支持") -> "unsupported_feature"
            statusCode == 400 -> "invalid_request"
            statusCode == 401 || statusCode == 403 -> "authentication"
            statusCode == 404 -> "model_not_found"
            statusCode == 408 || statusCode == 504 -> "timeout"
            statusCode == 413 -> "payload_too_large"
            statusCode == 422 -> "unsupported_feature"
            statusCode == 429 -> "rate_limit"
            statusCode in 500..599 -> "upstream_server_error"
            else -> "internal"
        }
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

    private data class CandidateBuffer(
        val parts: MutableList<JsonElement> = mutableListOf(),
        val toolCalls: MutableMap<Int, PendingToolCall> = linkedMapOf(),
        var finishReason: String? = null
    )

    /**
     * 工具调用参数在请求内聚合，并把 Finish 延迟到上游流真正结束时再发出。
     *
     * byok 的流事件把候选完成与整个响应结束分开处理：多个候选可以分别完成，
     * 最终 usage 也只能在 ResponseEnd 时确定。Studio 因此不能在第一个 Completed
     * 上直接关闭 SSE，否则会丢掉后续候选和最后一帧 usage。
     */
    class GeminiStreamEncoder internal constructor(
        private val cloudCodeEnvelope: Boolean,
        private var modelVersion: String?
    ) {
        private val pendingToolCalls = linkedMapOf<Pair<Int, Int>, PendingToolCall>()
        private val finishReasons = linkedMapOf<Int, String?>()
        private var streamFinished = false
        private var lastUsage: NeutralUsage? = null
        var failureStatusCode: Int? = null
            private set
        var failureMessage: String? = null
            private set

        fun encode(chunk: NeutralStreamChunk): List<String> {
            if (streamFinished) return emptyList()
            return when (chunk) {
                is NeutralStreamChunk.TextDelta -> listOf(sse(wrapIfNeeded(textPayload(chunk.choiceIndex, chunk.text))))
                is NeutralStreamChunk.ReasoningDelta -> listOf(
                    sse(wrapIfNeeded(thinkingPayload(chunk.choiceIndex, chunk.thinkingText, chunk.signature)))
                )

                is NeutralStreamChunk.InlineDataDelta -> listOf(
                    sse(
                        wrapIfNeeded(
                            inlinePayload(
                                chunk.choiceIndex,
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
                    streamFinished = true
                    val frames = mutableListOf<String>()
                    val errorText = "\n\n[Studio 代理异常 (${chunk.statusCode})]: ${chunk.message}"
                    val errorPayload = buildJsonObject {
                        put("candidates", buildJsonArray {
                            add(buildJsonObject {
                                put("index", 0)
                                put("content", buildJsonObject {
                                    put("role", "model")
                                    put("parts", buildJsonArray {
                                        add(buildJsonObject { put("text", errorText) })
                                    })
                                })
                            })
                        })
                        put("error", buildJsonObject {
                            put("code", chunk.statusCode)
                            put("category", errorCategory(chunk.statusCode, chunk.message))
                            put("message", chunk.message)
                        })
                        modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                    }
                    frames.add(sse(wrapIfNeeded(errorPayload)))
                    if (cloudCodeEnvelope) {
                        frames.add(sse(errorPayload))
                    }
                    val candidateIndexes = linkedSetOf<Int>().apply {
                        addAll(finishReasons.keys)
                        if (isEmpty()) add(0)
                    }
                    val finishPayload = buildJsonObject {
                        put("candidates", buildJsonArray {
                            candidateIndexes.toSortedSet().forEach { choiceIndex ->
                                add(buildJsonObject {
                                    put("index", choiceIndex)
                                    put("content", buildJsonObject {
                                        put("role", "model")
                                        put("parts", buildJsonArray {
                                            add(buildJsonObject { put("text", "") })
                                        })
                                    })
                                    put("finishReason", "OTHER")
                                })
                            }
                        })
                        modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                        lastUsage?.let { put("usageMetadata", usageMetadata(it)) }
                    }
                    frames.add(sse(wrapIfNeeded(finishPayload)))
                    if (!cloudCodeEnvelope) {
                        frames.add("data: [DONE]\n\n")
                    }
                    frames
                }

                is NeutralStreamChunk.Completed -> {
                    lastUsage = chunk.usage ?: lastUsage
                    // Gemini/OpenAI 可能在所有候选已经结束后单独发送 usage-only
                    // 帧；它只更新最终 usage，不应凭空创建 index=0 候选。
                    if (!(chunk.finishReason == null && chunk.usage != null &&
                                finishReasons.isNotEmpty() && chunk.choiceIndex !in finishReasons)
                    ) {
                        val previous = finishReasons[chunk.choiceIndex]
                        finishReasons[chunk.choiceIndex] = chunk.finishReason
                            ?: previous
                                    ?: chunk.usage?.let { "OTHER" }
                    }
                    emptyList()
                }
            }
        }

        private fun encodeToolCallDelta(chunk: NeutralStreamChunk.ToolCallDelta): List<String> {
            val pending = pendingToolCalls.getOrPut(chunk.choiceIndex to chunk.index) { PendingToolCall() }
            if (!chunk.id.isNullOrBlank()) pending.id = chunk.id
            if (!chunk.name.isNullOrBlank()) pending.name = chunk.name
            pending.arguments.append(chunk.argsText)
            return emptyList()
        }

        /** 结束上游流，刷新所有工具调用、候选 Finish、最终 usage 与 DONE。 */
        fun finish(): List<String> {
            if (streamFinished || failureStatusCode != null) return emptyList()
            val frames = mutableListOf<String>()
            val candidateIndexes = linkedSetOf<Int>().apply {
                addAll(finishReasons.keys)
                addAll(pendingToolCalls.keys.map { key -> key.first })
                if (isEmpty()) add(0)
            }
            val toolCandidateIndexes = pendingToolCalls.keys.map { key -> key.first }.toSet()
            pendingToolCalls.toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                .forEach { (key, pending) ->
                    val arguments = parseArguments(pending.arguments.toString())
                    if (arguments.isFailure) {
                        failureStatusCode = 502
                        failureMessage = arguments.exceptionOrNull()?.message ?: "Invalid tool arguments"
                        pendingToolCalls.clear()
                        streamFinished = true
                        val errFrames = mutableListOf<String>()
                        val errorText = "\n\n[Studio 代理异常 (502)]: ${failureMessage ?: "Invalid tool arguments"}"
                        val errorPayload = buildJsonObject {
                            put("candidates", buildJsonArray {
                                add(buildJsonObject {
                                    put("index", key.first)
                                    put("content", buildJsonObject {
                                        put("role", "model")
                                        put("parts", buildJsonArray {
                                            add(buildJsonObject { put("text", errorText) })
                                        })
                                    })
                                })
                            })
                            put("error", buildJsonObject {
                                put("code", 502)
                                put("category", errorCategory(502, failureMessage ?: "Invalid tool arguments"))
                                put("message", failureMessage ?: "Invalid tool arguments")
                            })
                            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                        }
                        errFrames.add(sse(wrapIfNeeded(errorPayload)))
                        if (cloudCodeEnvelope) {
                            errFrames.add(sse(errorPayload))
                        }
                        val finishPayload = buildJsonObject {
                            put("candidates", buildJsonArray {
                                add(buildJsonObject {
                                    put("index", key.first)
                                    put("content", buildJsonObject {
                                        put("role", "model")
                                        put("parts", buildJsonArray {
                                            add(buildJsonObject { put("text", "") })
                                        })
                                    })
                                    put("finishReason", "OTHER")
                                })
                            })
                            modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                            lastUsage?.let { put("usageMetadata", usageMetadata(it)) }
                        }
                        errFrames.add(sse(wrapIfNeeded(finishPayload)))
                        if (!cloudCodeEnvelope) {
                            errFrames.add("data: [DONE]\n\n")
                        }
                        return errFrames
                    }
                    frames.add(
                        sse(
                            wrapIfNeeded(
                                toolCallPayload(
                                    key.first,
                                    pending.id,
                                    pending.name,
                                    arguments.getOrThrow()
                                )
                            )
                        )
                    )
                }
            pendingToolCalls.clear()
            val finishPayload = buildJsonObject {
                put("candidates", buildJsonArray {
                    candidateIndexes.toSortedSet().forEach { choiceIndex ->
                        val finishReason = if (choiceIndex in toolCandidateIndexes) {
                            "TOOL_CALL"
                        } else {
                            finishReasonValue(finishReasons[choiceIndex])
                        }
                        add(buildJsonObject {
                            put("index", choiceIndex)
                            put("content", buildJsonObject {
                                put("role", "model")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject { put("text", "") })
                                })
                            })
                            put("finishReason", finishReason)
                        })
                    }
                })
                modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                lastUsage?.let { put("usageMetadata", usageMetadata(it)) }
            }
            frames.add(sse(wrapIfNeeded(finishPayload)))
            if (!cloudCodeEnvelope) frames.add("data: [DONE]\n\n")
            streamFinished = true
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
