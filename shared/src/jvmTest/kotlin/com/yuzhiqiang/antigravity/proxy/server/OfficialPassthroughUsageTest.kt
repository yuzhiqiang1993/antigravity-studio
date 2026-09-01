package com.yuzhiqiang.antigravity.proxy.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialPassthroughUsageTest {

    @Test
    fun extractsUsageAndMeaningfulContentFromCrLfSseEvents() {
        val buffer = StringBuilder(
            ": ping\r\n\r\n" +
                    "data: {\"response\":{\"modelVersion\":\"test\"}}\r\n\r\n" +
                    "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]}}]," +
                    "\"usageMetadata\":{\"promptTokenCount\":20,\"candidatesTokenCount\":12,\"thoughtsTokenCount\":2}}}\r\n\r\n"
        )

        val observation = OfficialPassthroughUsage.extractObservationFromSseBuffer(buffer)

        assertTrue(observation.hasMeaningfulContent)
        assertEquals("test", observation.responseModelId)
        assertEquals(20L, observation.usage?.inputTokens)
        assertEquals(12L, observation.usage?.outputTokens)
        assertEquals(2L, observation.usage?.reasoningTokens)
        assertEquals(34L, observation.usage?.totalTokens)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun geminiUsageComponentsAddUpToReportedTotal() {
        val usage = OfficialPassthroughUsage.parseGeminiUsage(
            Json.parseToJsonElement(
                """
                {
                  "usageMetadata": {
                    "promptTokenCount": 1500,
                    "cachedContentTokenCount": 500,
                    "candidatesTokenCount": 200,
                    "thoughtsTokenCount": 80,
                    "totalTokenCount": 1780
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1000L, usage?.inputTokens)
        assertEquals(500L, usage?.cacheReadTokens)
        assertEquals(200L, usage?.outputTokens)
        assertEquals(80L, usage?.reasoningTokens)
        assertEquals(1780L, usage?.totalTokens)
        val componentTotal = (usage?.inputTokens ?: 0L) +
                (usage?.cacheReadTokens ?: 0L) +
                (usage?.cacheWriteTokens ?: 0L) +
                (usage?.outputTokens ?: 0L) +
                (usage?.reasoningTokens ?: 0L)
        assertEquals(usage?.totalTokens, componentTotal)
    }

    @Test
    fun keepsReportedTotalGapAsUnattributedTokens() {
        val usage = OfficialPassthroughUsage.parseGeminiUsage(
            Json.parseToJsonElement(
                """
                {
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 5,
                    "totalTokenCount": 20
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(10L, usage?.inputTokens)
        assertEquals(5L, usage?.outputTokens)
        assertEquals(5L, usage?.unattributedTokens)
        assertEquals(20L, usage?.totalTokens)
    }

    @Test
    fun ignoresNegativeCacheBreakdownWithoutInflatingPromptTokens() {
        val usage = OfficialPassthroughUsage.parseGeminiUsage(
            Json.parseToJsonElement(
                """
                {
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "cachedContentTokenCount": -3,
                    "candidatesTokenCount": 5,
                    "totalTokenCount": 15
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(10L, usage?.inputTokens)
        assertEquals(null, usage?.cacheReadTokens)
        assertEquals(5L, usage?.outputTokens)
        assertEquals(15L, usage?.totalTokens)
    }

    @Test
    fun keepsPartialEventUntilItCanBeParsed() {
        val buffer = StringBuilder(
            "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"search\"}}]}}]}}"
        )

        assertFalse(OfficialPassthroughUsage.extractObservationFromSseBuffer(buffer).hasMeaningfulContent)
        assertTrue(buffer.isNotEmpty())

        buffer.append("\n\n")
        assertTrue(OfficialPassthroughUsage.extractObservationFromSseBuffer(buffer).hasMeaningfulContent)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun usageOnlyEventDoesNotBecomeFirstToken() {
        val buffer = StringBuilder(
            "data: {\"response\":{\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":0}}}\n\n"
        )

        val observation = OfficialPassthroughUsage.extractObservationFromSseBuffer(buffer)

        assertFalse(observation.hasMeaningfulContent)
        assertEquals(0L, observation.usage?.outputTokens)
    }
}
