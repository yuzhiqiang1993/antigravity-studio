package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageExtractorTest {

    @Test
    fun testExtractPlannerResponseUsage() {
        val lines = sequenceOf(
            """{"step_index":0,"source":"USER_EXPLICIT","type":"USER_INPUT","content":"hello"}""",
            """{"step_index":1,"source":"MODEL","type":"PLANNER_RESPONSE","created_at":"2026-08-31T06:15:10Z","usage":{"input_tokens":1000,"output_tokens":200,"cache_read_tokens":800,"thinking_output_tokens":50,"model":"claude-3-7-sonnet","response_id":"resp-123"}}"""
        )

        val entries = UsageExtractor.extractFromTranscript(lines, "convo-1", "ide")
        assertEquals(1, entries.size)

        val entry = entries[0]
        assertEquals("resp-123", entry.responseId)
        assertEquals(1000L, entry.input)
        assertEquals(200L, entry.output)
        assertEquals(800L, entry.cacheRead)
        assertEquals(50L, entry.reasoning)
        assertEquals("claude-3-7-sonnet", entry.model)
        assertEquals("2026-08-31T06:15:10Z", entry.timestamp)
        assertEquals("convo-1", entry.conversationId)
        assertEquals("ide", entry.appSource)
    }

    @Test
    fun testDedupEntriesWithSameResponseId() {
        val entries = listOf(
            TokenEntry(responseId = "resp-1", input = 100, output = 50, model = "gemini-2.0-flash"),
            TokenEntry(responseId = "resp-1", input = 100, output = 50, model = "gemini-2.0-flash"),
            TokenEntry(responseId = "resp-2", input = 200, output = 80, model = "gemini-2.0-flash")
        )

        val deduped = UsageExtractor.dedupEntries(entries)
        assertEquals(2, deduped.size)
        assertEquals("resp-1", deduped[0].responseId)
        assertEquals("resp-2", deduped[1].responseId)
    }

    @Test
    fun testNestedChatModelProvidesModelDisplayNameAndTimestampContext() {
        val lines = sequenceOf(
            """{"chatModel":{"responseModel":"vendor/model-a","displayName":"Model A","chatStartMetadata":{"createdAt":"2026-08-31T06:15:10Z"},"usage":{"input_tokens":100}}}"""
        )

        val entry = UsageExtractor.extractFromTranscript(lines, "nested", "ide").single()

        assertEquals("vendor/model-a", entry.model)
        assertEquals("Model A", entry.modelDisplayName)
        assertEquals("vendor/model-a", entry.modelCanonicalId)
        assertEquals("session-display:model-a", entry.modelAggregationId)
        assertEquals("2026-08-31T06:15:10Z", entry.timestamp)
        assertEquals(listOf("output", "cache", "cacheWrite", "reasoning"), entry.missingUsageFields)
    }

    @Test
    fun testStepModelUsageUsesSiblingMetadataTimestamp() {
        val lines = sequenceOf(
            """{"metadata":{"createdAt":"2026-08-31T06:15:10Z"},"modelUsage":{"inputTokens":10,"outputTokens":5,"responseId":"step-rid","model":"model-a"}}"""
        )

        val entry = UsageExtractor.extractFromTranscript(lines, "step", "ide").single()

        assertEquals("2026-08-31T06:15:10Z", entry.timestamp)
        assertEquals("model-a", entry.model)
    }

    @Test
    fun testInvalidTokenValuesAreMarkedMissingButExplicitZeroIsKnown() {
        val lines = sequenceOf(
            """{"usage":{"input_tokens":"invalid","output_tokens":5,"cache_read_tokens":-1,"cache_write_tokens":0,"reasoning_tokens":false,"model":"model-a"}}"""
        )

        val entry = UsageExtractor.extractFromTranscript(lines, "invalid-values", "ide").single()

        assertEquals(5L, entry.output)
        assertEquals(0L, entry.cacheWrite)
        assertEquals(listOf("input", "cache", "reasoning"), entry.missingUsageFields)
    }

    @Test
    fun testZeroAliasDoesNotHideLaterPositiveValue() {
        val lines = sequenceOf(
            """{"usage":{"input_tokens":0,"prompt_tokens":"12","output_tokens":0,"cache_read_tokens":0,"cache_write_tokens":0,"reasoning_tokens":0,"model":"model-a"}}"""
        )

        val entry = UsageExtractor.extractFromTranscript(lines, "aliases", "ide").single()

        assertEquals(12L, entry.input)
        assertEquals(emptyList(), entry.missingUsageFields)
    }

    @Test
    fun testTotalOnlyUsageIsRetainedAsUnattributed() {
        val entry = UsageExtractor.extractFromTranscript(
            sequenceOf("""{"usage":{"total_tokens":42,"response_id":"total-only"}}"""),
            "total-only",
            "ide"
        ).single()

        assertEquals(42L, entry.unattributed)
        assertEquals(42L, entry.totalTokens)
    }

    @Test
    fun testReportedTotalGapIsRetainedAsUnattributed() {
        val entry = UsageExtractor.extractFromTranscript(
            sequenceOf("""{"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":20}}"""),
            "reported-gap",
            "ide"
        ).single()

        assertEquals(5L, entry.unattributed)
        assertEquals(20L, entry.totalTokens)
    }

    @Test
    fun testReportedTotalCannotReduceKnownComponents() {
        val entry = UsageExtractor.extractFromTranscript(
            sequenceOf("""{"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":8}}"""),
            "small-total",
            "ide"
        ).single()

        assertEquals(0L, entry.unattributed)
        assertEquals(15L, entry.totalTokens)
    }

    @Test
    fun testTotalOnlyAndDetailedDuplicateMergeWithoutDoubleCounting() {
        val entries = UsageExtractor.extractFromTranscript(
            sequenceOf(
                """{"usage":{"total_tokens":20,"response_id":"same-response"}}""",
                """{"usage":{"input_tokens":10,"output_tokens":5,"response_id":"same-response"}}"""
            ),
            "merged-total",
            "ide"
        )

        val entry = entries.single()
        assertEquals(10L, entry.input)
        assertEquals(5L, entry.output)
        assertEquals(5L, entry.unattributed)
        assertEquals(20L, entry.totalTokens)
    }

    @Test
    fun testFallbackFingerprintKeepsCallsInTheSameSecondSeparateWhenMillisecondsDiffer() {
        val entries = listOf(
            TokenEntry(input = 10, model = "model", timestamp = "2026-08-31T06:15:10.001Z"),
            TokenEntry(input = 10, model = "model", timestamp = "2026-08-31T06:15:10.002Z")
        )

        assertEquals(2, UsageExtractor.dedupEntries(entries).size)
    }
}
