package com.yuzhiqiang.antigravity.proxy.model

import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import kotlinx.serialization.json.JsonElement

enum class NeutralRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

sealed class NeutralContent {
    data class Text(val text: String) : NeutralContent()
    data class Image(val mimeType: String, val base64Data: String) : NeutralContent()
    data class Thinking(val text: String, val signature: String? = null) : NeutralContent()
    data class ToolCall(
        val id: String,
        val functionName: String,
        val argumentsJson: String
    ) : NeutralContent()

    data class ToolResult(
        val toolCallId: String,
        val functionName: String? = null,
        val content: String
    ) : NeutralContent()
}

data class NeutralToolCall(
    val id: String,
    val functionName: String,
    val argumentsJson: String
)

data class NeutralToolDefinition(
    val name: String,
    val description: String = "",
    val parametersJson: JsonElement
)

data class NeutralMessage(
    val role: NeutralRole,
    val contents: List<NeutralContent>,
    val toolCalls: List<NeutralToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

data class NeutralUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val totalTokens: Long? = null
)

data class NeutralChatRequest(
    val originalModelId: String,
    val targetUpstreamModelId: String,
    val systemPrompt: String? = null,
    val messages: List<NeutralMessage> = emptyList(),
    val tools: List<NeutralToolDefinition> = emptyList(),
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val reasoningBudgetTokens: Int? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val stream: Boolean = true,
    val extraBody: Map<String, JsonElement> = emptyMap(),
    val reasoningLevel: ReasoningLevel? = null,
    val reasoningMapping: ReasoningMapping? = null,
    /** 宿主声明的输出模态，用于 Gemini/图像生成请求。 */
    val outputModalities: Set<ModelModality> = emptySet(),
    /** 宿主传入的 imageConfig 等生图参数。 */
    val imageGenerationConfig: JsonElement? = null
)

enum class StreamErrorSource {
    UPSTREAM_RESPONSE,
    UPSTREAM_TRANSPORT,
    STUDIO_ADAPTER,
    STUDIO_PROXY
}

sealed class NeutralStreamChunk {
    data class TextDelta(val text: String, val choiceIndex: Int = 0) : NeutralStreamChunk()
    data class ReasoningDelta(
        val thinkingText: String,
        val signature: String? = null,
        val choiceIndex: Int = 0
    ) : NeutralStreamChunk()

    data class InlineDataDelta(
        val mimeType: String,
        val base64Data: String,
        val choiceIndex: Int = 0
    ) : NeutralStreamChunk()

    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argsText: String,
        val choiceIndex: Int = 0
    ) :
        NeutralStreamChunk()

    data class Completed(
        val finishReason: String? = "stop",
        val usage: NeutralUsage? = null,
        val choiceIndex: Int = 0
    ) : NeutralStreamChunk()

    data class Error(
        val message: String,
        val statusCode: Int = 500,
        /** 上游 HTTP 流已经建立后发生的解析/断流错误不能再修改下游状态码。 */
        val responseStarted: Boolean = false,
        val source: StreamErrorSource = StreamErrorSource.STUDIO_PROXY
    ) : NeutralStreamChunk()
}
