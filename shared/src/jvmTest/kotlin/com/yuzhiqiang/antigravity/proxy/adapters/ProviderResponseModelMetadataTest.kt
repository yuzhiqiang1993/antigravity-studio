package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderResponseModelMetadataTest {
    private fun List<NeutralStreamChunk>.responseModelId(): String? =
        filterIsInstance<NeutralStreamChunk.ResponseMetadata>().lastOrNull()?.responseModelId

    @Test
    fun openAiChatReadsRootModel() {
        val chunks = OpenAiChatCompletionsCodec.parseChunk(
            """{"model":"gpt-actual","choices":[]}"""
        ).getOrThrow()
        assertEquals("gpt-actual", chunks.responseModelId())
    }

    @Test
    fun openAiResponsesReadsNestedStreamModel() {
        val chunks = OpenAiResponsesCodec.parseStreamEvent(
            """{"type":"response.created","response":{"model":"gpt-response-actual"}}""",
            OpenAiResponsesCodec.StreamState()
        ).getOrThrow()
        assertEquals("gpt-response-actual", chunks.responseModelId())
    }

    @Test
    fun anthropicReadsStreamAndNonStreamingModel() {
        val adapter = AnthropicAdapter()
        val stream = adapter.parseEvent(
            """{"type":"message_start","message":{"model":"claude-actual"}}"""
        ).getOrThrow().chunks
        val nonStream = adapter.parseNonStreamingResponse(
            """{"model":"claude-actual","content":[],"stop_reason":"end_turn"}"""
        ).getOrThrow()
        assertEquals("claude-actual", stream.responseModelId())
        assertEquals("claude-actual", nonStream.responseModelId())
    }

    @Test
    fun geminiReadsModelVersion() {
        val chunks = GeminiAdapter().parseResponse(
            """{"modelVersion":"gemini-actual","candidates":[]}"""
        ).getOrThrow()
        assertEquals("gemini-actual", chunks.responseModelId())
    }
}
