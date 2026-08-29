package com.yuzhiqiang.antigravity.proxy.adapters

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderSseReaderTest {

    @Test
    fun sseReaderSupportsCommentsCrlfAndMultilineData() = runBlocking {
        val channel = ByteReadChannel(
            ":keep-alive\r\nevent:message\r\ndata: {\"text\":\r\ndata: \"ok\"}\r\n\r\n"
        )
        assertEquals("{\"text\":\n\"ok\"}", ProviderAdapter.readSseDataEvent(channel).getOrThrow())
    }

    @Test
    fun sseReaderSkipsEmptyDataHeartbeatBeforeJsonEvent() = runBlocking {
        val channel = ByteReadChannel(
            "event: ping\ndata:\n\n:keep-alive\ndata: {\"type\":\"message_stop\"}\n\n"
        )

        assertEquals(
            "{\"type\":\"message_stop\"}",
            ProviderAdapter.readSseDataEvent(channel).getOrThrow()
        )
    }
}
