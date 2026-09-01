package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicAdapterTest {

    @Test
    fun mergesMessageStartAndMessageDeltaUsage() {
        val adapter = AnthropicAdapter()
        val start = adapter.parseEvent(
            """
            {
              "type": "message_start",
              "message": {
                "usage": {
                  "input_tokens": 100,
                  "cache_read_input_tokens": 20,
                  "cache_creation_input_tokens": 10,
                  "output_tokens": 0
                }
              }
            }
            """.trimIndent()
        ).getOrThrow()

        assertTrue(start.chunks.isEmpty())
        assertEquals(100L, start.usage?.inputTokens)
        assertEquals(20L, start.usage?.cacheReadTokens)
        assertEquals(10L, start.usage?.cacheWriteTokens)
        assertEquals(130L, start.usage?.totalTokens)

        val delta = adapter.parseEvent(
            """
            {
              "type": "message_delta",
              "delta": {"stop_reason": "end_turn"},
              "usage": {"output_tokens": 30}
            }
            """.trimIndent(),
            previousUsage = start.usage
        ).getOrThrow()
        val completed = delta.chunks.single() as NeutralStreamChunk.Completed

        assertEquals("end_turn", completed.finishReason)
        assertEquals(100L, completed.usage?.inputTokens)
        assertEquals(20L, completed.usage?.cacheReadTokens)
        assertEquals(10L, completed.usage?.cacheWriteTokens)
        assertEquals(30L, completed.usage?.outputTokens)
        assertEquals(160L, completed.usage?.totalTokens)
    }

    @Test
    fun detailedStreamUsageConsumesGapFromEarlierTotalOnlySnapshot() {
        val adapter = AnthropicAdapter()
        val start = adapter.parseEvent(
            """
            {
              "type": "message_start",
              "message": {"usage": {"total_tokens": 100}}
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(100L, start.usage?.unattributedTokens)
        assertEquals(100L, start.usage?.totalTokens)

        val delta = adapter.parseEvent(
            """
            {
              "type": "message_delta",
              "delta": {"stop_reason": "end_turn"},
              "usage": {"input_tokens": 60, "output_tokens": 20, "total_tokens": 100}
            }
            """.trimIndent(),
            previousUsage = start.usage
        ).getOrThrow()
        val usage = (delta.chunks.single() as NeutralStreamChunk.Completed).usage

        assertEquals(60L, usage?.inputTokens)
        assertEquals(20L, usage?.outputTokens)
        assertEquals(20L, usage?.unattributedTokens)
        assertEquals(100L, usage?.totalTokens)
    }

    @Test
    fun keepsCacheUsageWhenItExceedsUncachedInput() {
        val adapter = AnthropicAdapter()

        val start = adapter.parseEvent(
            """
            {
              "type": "message_start",
              "message": {
                "usage": {
                  "input_tokens": 21,
                  "cache_read_input_tokens": 1886,
                  "cache_creation_input_tokens": 99,
                  "output_tokens": 0
                }
              }
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(21L, start.usage?.inputTokens)
        assertEquals(1886L, start.usage?.cacheReadTokens)
        assertEquals(99L, start.usage?.cacheWriteTokens)
        assertEquals(2006L, start.usage?.totalTokens)
    }
}
