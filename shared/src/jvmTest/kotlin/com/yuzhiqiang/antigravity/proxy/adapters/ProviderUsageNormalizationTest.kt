package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderUsageNormalizationTest {

    @Test
    fun openAiChatIgnoresNegativeCacheAndReasoningBreakdowns() {
        val chunks = OpenAiChatCompletionsCodec.parseNonStreamingResponse(
            """
            {
              "choices": [],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 8,
                "total_tokens": 18,
                "prompt_tokens_details": {"cached_tokens": -2},
                "completion_tokens_details": {"reasoning_tokens": -3}
              }
            }
            """.trimIndent()
        ).getOrThrow()
        val usage = (chunks.single() as NeutralStreamChunk.Completed).usage

        assertEquals(10L, usage?.inputTokens)
        assertEquals(8L, usage?.outputTokens)
        assertNull(usage?.cacheReadTokens)
        assertNull(usage?.reasoningTokens)
        assertEquals(18L, usage?.totalTokens)
    }

    @Test
    fun openAiResponsesIgnoresNegativeCacheAndReasoningBreakdowns() {
        val chunks = OpenAiResponsesCodec.parseNonStreamingResponse(
            """
            {
              "status": "completed",
              "output": [],
              "usage": {
                "input_tokens": 10,
                "output_tokens": 8,
                "total_tokens": 18,
                "input_tokens_details": {"cached_tokens": -2},
                "output_tokens_details": {"reasoning_tokens": -3}
              }
            }
            """.trimIndent()
        ).getOrThrow()
        val usage = (chunks.single() as NeutralStreamChunk.Completed).usage

        assertEquals(10L, usage?.inputTokens)
        assertEquals(8L, usage?.outputTokens)
        assertNull(usage?.cacheReadTokens)
        assertNull(usage?.reasoningTokens)
        assertEquals(18L, usage?.totalTokens)
    }
}
