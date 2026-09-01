package com.yuzhiqiang.antigravity.proxy.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NeutralUsageNormalizerTest {

    @Test
    fun keepsReportedTotalGapAsUnattributedTokens() {
        val usage = normalizedNeutralUsage(
            inputTokens = 10,
            outputTokens = 5,
            reportedTotalTokens = 20
        )

        assertEquals(10L, usage?.inputTokens)
        assertEquals(5L, usage?.outputTokens)
        assertEquals(5L, usage?.unattributedTokens)
        assertEquals(20L, usage?.totalTokens)
        assertConserved(usage)
    }

    @Test
    fun turnsTotalOnlyUsageIntoUnattributedTokens() {
        val usage = normalizedNeutralUsage(reportedTotalTokens = 42)

        assertEquals(42L, usage?.unattributedTokens)
        assertEquals(42L, usage?.totalTokens)
        assertConserved(usage)
    }

    @Test
    fun ignoresNegativeComponentsAndNeverLetsReportedTotalReduceKnownUsage() {
        val usage = normalizedNeutralUsage(
            inputTokens = -10,
            outputTokens = 8,
            cacheReadTokens = 4,
            reportedTotalTokens = 5
        )

        assertNull(usage?.inputTokens)
        assertEquals(8L, usage?.outputTokens)
        assertEquals(4L, usage?.cacheReadTokens)
        assertNull(usage?.unattributedTokens)
        assertEquals(12L, usage?.totalTokens)
        assertConserved(usage)
    }

    private fun assertConserved(usage: NeutralUsage?) {
        val components = (usage?.inputTokens ?: 0L) +
                (usage?.outputTokens ?: 0L) +
                (usage?.cacheReadTokens ?: 0L) +
                (usage?.cacheWriteTokens ?: 0L) +
                (usage?.reasoningTokens ?: 0L) +
                (usage?.unattributedTokens ?: 0L)
        assertEquals(components, usage?.totalTokens)
    }
}
