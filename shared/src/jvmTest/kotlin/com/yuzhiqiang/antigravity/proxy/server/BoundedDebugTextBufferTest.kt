package com.yuzhiqiang.antigravity.proxy.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedDebugTextBufferTest {

    @Test
    fun keepsContentWithinLimitUnchanged() {
        val buffer = BoundedDebugTextBuffer(maxChars = 5)

        buffer.append("abcde")

        assertEquals("abcde", buffer.toString())
    }

    @Test
    fun truncatesAcrossAppendsAndCountsAllOmittedCharacters() {
        val buffer = BoundedDebugTextBuffer(maxChars = 5)

        buffer.append("abc")
        buffer.append("defgh")

        assertEquals(
            "abcde\n\n... [Truncated: 3 chars omitted for performance]",
            buffer.toString()
        )

        buffer.append("ij")

        assertEquals(
            "abcde\n\n... [Truncated: 5 chars omitted for performance]",
            buffer.toString()
        )
    }

    @Test
    fun rejectsNonPositiveLimit() {
        assertFailsWith<IllegalArgumentException> {
            BoundedDebugTextBuffer(maxChars = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BoundedDebugTextBuffer(maxChars = -1)
        }
    }
}
