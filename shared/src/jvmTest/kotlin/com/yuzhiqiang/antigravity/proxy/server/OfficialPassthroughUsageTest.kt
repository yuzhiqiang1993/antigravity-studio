package com.yuzhiqiang.antigravity.proxy.server

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
        assertEquals(20L, observation.usage?.inputTokens)
        assertEquals(10L, observation.usage?.outputTokens)
        assertEquals(2L, observation.usage?.reasoningTokens)
        assertTrue(buffer.isEmpty())
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
