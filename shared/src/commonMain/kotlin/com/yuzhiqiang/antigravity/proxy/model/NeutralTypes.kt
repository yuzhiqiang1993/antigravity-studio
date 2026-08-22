package com.yuzhiqiang.antigravity.proxy.model

import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
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
    val reasoningMapping: ReasoningMapping? = null
)

sealed class NeutralStreamChunk {
    data class TextDelta(val text: String) : NeutralStreamChunk()
    data class ReasoningDelta(val thinkingText: String, val signature: String? = null) : NeutralStreamChunk()
    data class InlineDataDelta(val mimeType: String, val base64Data: String) : NeutralStreamChunk()
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val argsText: String) :
        NeutralStreamChunk()

    data class Completed(val finishReason: String? = "stop") : NeutralStreamChunk()
    data class Error(val message: String, val statusCode: Int = 500) : NeutralStreamChunk()
}
