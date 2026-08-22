package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderProtocol {
    @SerialName("openai_chat_completions")
    OPENAI_CHAT_COMPLETIONS,

    @SerialName("anthropic_messages")
    ANTHROPIC_MESSAGES,

    @SerialName("gemini_generate_content")
    GEMINI_GENERATE_CONTENT,

    @SerialName("openai_responses")
    OPENAI_RESPONSES;

    val displayName: String
        get() = when (this) {
            OPENAI_CHAT_COMPLETIONS -> "OpenAI Chat Completions (/v1/chat/completions)"
            ANTHROPIC_MESSAGES -> "Anthropic Messages (/v1/messages)"
            GEMINI_GENERATE_CONTENT -> "Gemini GenerateContent (/v1beta/models)"
            OPENAI_RESPONSES -> "OpenAI Responses (/v1/responses)"
        }
}
