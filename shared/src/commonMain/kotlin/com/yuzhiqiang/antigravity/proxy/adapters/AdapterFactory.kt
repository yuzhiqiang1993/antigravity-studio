package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol

object AdapterFactory {
    private val openAiAdapter = OpenAiAdapter()
    private val anthropicAdapter = AnthropicAdapter()
    private val geminiAdapter = GeminiAdapter()

    fun getAdapter(protocol: ProviderProtocol): ProviderAdapter {
        return when (protocol) {
            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> openAiAdapter
            ProviderProtocol.ANTHROPIC_MESSAGES -> anthropicAdapter
            ProviderProtocol.GEMINI_GENERATE_CONTENT -> geminiAdapter
        }
    }
}
