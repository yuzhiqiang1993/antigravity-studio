package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.proxy.adapters.OpenAiResponsesCodec
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByokErrorHandlingTest {

    @Test
    fun openAiResponsesExplicitErrorKeepsUpstreamResponseSource() {
        val chunks = OpenAiResponsesCodec.parseStreamEvent(
            """{"type":"error","error":{"message":"Upstream provider failed","code":502}}""",
            OpenAiResponsesCodec.StreamState()
        ).getOrThrow()

        val error = chunks.single() as NeutralStreamChunk.Error
        assertEquals("Upstream provider failed", error.message)
        assertEquals(StreamErrorSource.UPSTREAM_RESPONSE, error.source)
    }

    @Test
    fun byokConnectionFailureProvidesActionableUpstreamGuidance() {
        val friendlyError = ByokForwardHandler.toUserFacingError(
            NeutralStreamChunk.Error(
                "OpenAI request failed: Failed to connect to /127.0.0.1:8317",
                502,
                source = StreamErrorSource.UPSTREAM_TRANSPORT
            )
        )

        assertTrue(friendlyError.message.startsWith("上游服务连接异常，请检查上游服务是否正常运行"))
        assertTrue(friendlyError.message.contains("服务地址与端口配置正确"))
        assertTrue(friendlyError.message.contains("Failed to connect to /127.0.0.1:8317"))
        assertEquals(StreamErrorSource.UPSTREAM_TRANSPORT, friendlyError.source)
    }

    @Test
    fun officialConnectionFailureIncludesSystemProxyGuidanceAndOriginalError() {
        val friendlyError = ByokForwardHandler.toUserFacingError(
            NeutralStreamChunk.Error(
                "Failed to connect to /127.0.0.1:7890",
                502,
                source = StreamErrorSource.UPSTREAM_TRANSPORT
            ),
            includeSystemProxyGuidance = true
        )

        assertTrue(friendlyError.message.startsWith("上游服务连接异常，请检查上游服务或系统网络代理是否正常运行"))
        assertTrue(friendlyError.message.contains("确认地址与端口配置正确"))
        assertTrue(friendlyError.message.contains("原始错误：Failed to connect to /127.0.0.1:7890"))
        assertEquals(StreamErrorSource.UPSTREAM_TRANSPORT, friendlyError.source)
    }

    @Test
    fun byokErrorClassifierDistinguishesUpstreamResponseTransportAndAdapterFailures() {
        val upstreamResponseError = ByokForwardHandler.classifyErrorSource(
            NeutralStreamChunk.Error(
                "stream error: stream disconnected before completion: stream closed before response.completed",
                502,
                source = StreamErrorSource.STUDIO_ADAPTER
            )
        )
        val transportError = ByokForwardHandler.classifyErrorSource(
            NeutralStreamChunk.Error("OpenAI Responses stream ended before completion", 502)
        )
        val adapterError = ByokForwardHandler.classifyErrorSource(
            NeutralStreamChunk.Error("Invalid OpenAI Responses stream event", 502)
        )

        assertEquals(StreamErrorSource.UPSTREAM_RESPONSE, upstreamResponseError.source)
        assertEquals(StreamErrorSource.UPSTREAM_TRANSPORT, transportError.source)
        assertEquals(StreamErrorSource.STUDIO_ADAPTER, adapterError.source)
    }

    @Test
    fun testRetryableStatusCodes() {
        assertTrue(ByokForwardHandler.isRetryableError(500))
        assertTrue(ByokForwardHandler.isRetryableError(502))
        assertTrue(ByokForwardHandler.isRetryableError(503))
        assertTrue(ByokForwardHandler.isRetryableError(504))
        assertTrue(ByokForwardHandler.isRetryableError(525))
        assertTrue(ByokForwardHandler.isRetryableError(429))
        assertTrue(ByokForwardHandler.isRetryableError(408))
        assertTrue(ByokForwardHandler.isRetryableError(499))

        assertFalse(ByokForwardHandler.isRetryableError(400))
        assertFalse(ByokForwardHandler.isRetryableError(401))
        assertFalse(ByokForwardHandler.isRetryableError(403))
        assertFalse(ByokForwardHandler.isRetryableError(404))
        assertFalse(ByokForwardHandler.isRetryableError(501))

        val tlsEofError = NeutralStreamChunk.Error(
            "OpenAI API error (500): Post \"https://chatgpt.com/backend-api/codex/responses\": utls: TLS handshake: EOF",
            500
        )
        assertTrue(ByokForwardHandler.isRetryableError(tlsEofError))

        val socketError = NeutralStreamChunk.Error("connection reset by peer", 502)
        assertTrue(ByokForwardHandler.isRetryableError(socketError))

        val adapterError = NeutralStreamChunk.Error(
            "Invalid Anthropic response",
            502,
            source = StreamErrorSource.STUDIO_ADAPTER
        )
        assertFalse(ByokForwardHandler.isRetryableError(adapterError))
        assertFalse(
            ByokForwardHandler.isRetryableError(
                NeutralStreamChunk.Error(
                    "Anthropic API error (401): invalid key",
                    401,
                    source = StreamErrorSource.UPSTREAM_RESPONSE
                )
            )
        )
    }

    @Test
    fun testExponentialBackoffWithJitter() {
        val delay1 = ByokForwardHandler.calculateBackoff(1, 500L)
        val delay2 = ByokForwardHandler.calculateBackoff(2, 500L)
        val delay3 = ByokForwardHandler.calculateBackoff(3, 500L)

        assertTrue(delay1 in 350L..650L)
        assertTrue(delay2 in 700L..1300L)
        assertTrue(delay3 in 1400L..2600L)
    }

    @Test
    fun testProviderRetryDefaults() {
        val provider = Provider(id = "test-provider", name = "Test")
        assertEquals(2, provider.maxRetries)
        assertEquals(500L, provider.retryDelayMs)
    }
}
