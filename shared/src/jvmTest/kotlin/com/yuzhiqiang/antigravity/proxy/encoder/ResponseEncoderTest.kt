package com.yuzhiqiang.antigravity.proxy.encoder

import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseEncoderTest {

    @Test
    fun responseEncoderKeepsProviderCandidateIndex() {
        val body = ResponseEncoder.encodeChunksToGeminiJson(
            listOf(
                NeutralStreamChunk.TextDelta("answer", choiceIndex = 4),
                NeutralStreamChunk.Completed("STOP", choiceIndex = 4)
            )
        )

        assertTrue(body.contains("\"index\":4"))
        assertTrue(body.contains("\"text\":\"answer\""))
    }

    @Test
    fun proxyErrorsStayTopLevelOnCloudCodeRoutes() {
        val body = ResponseEncoder.encodeErrorToGeminiJson("stream interrupted", 502, cloudCodeEnvelope = true)
        assertTrue(body.contains("\"error\""))
        assertTrue(!body.contains("\"response\""))
        assertTrue(body.contains("\"category\":\"stream_interrupted\""))
    }

    @Test
    fun streamEncoderKeepsAllCandidatesUntilTheUpstreamStreamEnds() {
        val encoder = ResponseEncoder.newStreamEncoder()
        assertTrue(
            encoder.encode(NeutralStreamChunk.TextDelta("first", choiceIndex = 2))
                .single()
                .contains("\"index\":2")
        )
        assertTrue(encoder.encode(NeutralStreamChunk.Completed("stop", choiceIndex = 2)).isEmpty())
        assertTrue(
            encoder.encode(NeutralStreamChunk.TextDelta("second", choiceIndex = 7))
                .single()
                .contains("\"index\":7")
        )
        assertTrue(encoder.encode(NeutralStreamChunk.Completed("length", choiceIndex = 7)).isEmpty())

        val endFrames = encoder.finish()
        assertEquals(2, endFrames.size)
        assertTrue(endFrames[0].contains("\"index\":2"))
        assertTrue(endFrames[0].contains("\"finishReason\":\"STOP\""))
        assertTrue(endFrames[0].contains("\"index\":7"))
        assertTrue(endFrames[0].contains("\"finishReason\":\"MAX_TOKENS\""))
        assertEquals("data: [DONE]\n\n", endFrames[1])
        assertTrue(encoder.finish().isEmpty())
    }

    @Test
    fun streamEncoderAttachesFinalUsageToFinishFrame() {
        val encoder = ResponseEncoder.newStreamEncoder()
        encoder.encode(
            NeutralStreamChunk.Completed(
                finishReason = "stop",
                choiceIndex = 2
            )
        )
        encoder.encode(
            NeutralStreamChunk.Completed(
                usage = NeutralUsage(
                    inputTokens = 7,
                    outputTokens = 4,
                    cacheReadTokens = 3,
                    reasoningTokens = 5
                ),
                choiceIndex = 2
            )
        )

        val frame = encoder.finish().first()
        assertTrue(frame.contains("\"index\":2"))
        assertTrue(frame.contains("\"text\":\"\""))
        assertTrue(frame.contains("\"promptTokenCount\":10"))
        assertTrue(frame.contains("\"candidatesTokenCount\":4"))
        assertTrue(frame.contains("\"thoughtsTokenCount\":5"))
        assertTrue(frame.contains("\"totalTokenCount\":19"))
    }

    @Test
    fun geminiUsageKeepsUnattributedTokensOnlyInReportedTotal() {
        val body = ResponseEncoder.encodeChunksToGeminiJson(
            listOf(
                NeutralStreamChunk.Completed(
                    usage = NeutralUsage(
                        inputTokens = 10,
                        outputTokens = 5,
                        unattributedTokens = 5,
                        totalTokens = 20
                    )
                )
            )
        )

        assertTrue(body.contains("\"promptTokenCount\":10"))
        assertTrue(body.contains("\"candidatesTokenCount\":5"))
        assertTrue(body.contains("\"totalTokenCount\":20"))
        assertTrue(!body.contains("unattributed", ignoreCase = true))
    }

    @Test
    fun streamEncoderEmitsErrorTextAndFinishReasonOnStreamError() {
        val encoder = ResponseEncoder.newStreamEncoder(cloudCodeEnvelope = false)
        val frames = encoder.encode(
            NeutralStreamChunk.Error(
                "stream disconnected before completion: stream closed before response.completed",
                502
            )
        )
        // 1. 必须包含标准 error 帧
        assertTrue(frames.any { it.contains("\"code\":502") && it.contains("stream disconnected") })
        // 2. 必须包含可见的候选文本帧，供客户端界面展示错误
        assertTrue(frames.any { it.contains("[Studio 代理异常 (502)]") })
        // 3. 必须包含 finishReason 为 OTHER 的 Candidate 结束帧，使客户端状态机结束生成
        assertTrue(frames.any { it.contains("\"finishReason\":\"OTHER\"") })
        // 4. 标准流下必须包含 [DONE] 帧
        assertTrue(frames.any { it.contains("[DONE]") })
    }

    @Test
    fun streamEncoderWrapsErrorEnvelopeForCloudCode() {
        val encoder = ResponseEncoder.newStreamEncoder(cloudCodeEnvelope = true)
        val frames = encoder.encode(
            NeutralStreamChunk.Error(
                "upstream timeout",
                504
            )
        )
        // 包含带 response 包裹的错误及顶层错误
        assertTrue(frames.any { it.contains("\"response\"") && it.contains("\"code\":504") })
        assertTrue(frames.any { it.contains("\"error\"") && it.contains("\"code\":504") })
        assertTrue(frames.any { it.contains("[Studio 代理异常 (504)]") })
        assertTrue(frames.any { it.contains("\"finishReason\":\"OTHER\"") })
    }

    @Test
    fun responseMetadataOverridesRequestedProviderModelId() {
        val body = ResponseEncoder.encodeChunksToGeminiJson(
            chunks = listOf(
                NeutralStreamChunk.ResponseMetadata("provider-response-model"),
                NeutralStreamChunk.Completed()
            ),
            modelVersion = "requested-provider-model"
        )

        assertTrue(body.contains("\"modelVersion\":\"provider-response-model\""))
        assertTrue(!body.contains("requested-provider-model"))
    }

    @Test
    fun testCloudCodeErrorFrameAlwaysContainsCandidates() {
        val encoder = ResponseEncoder.newStreamEncoder(cloudCodeEnvelope = true)
        val frames = encoder.encode(
            NeutralStreamChunk.Error(
                "stream disconnected before completion: stream closed before response.completed",
                502
            )
        )
        // 验证所有发送给 Cloud Code 的数据帧都必须含有 candidates，杜绝 IDE 前端 TypeError: Cannot read properties of undefined
        val dataFrames = frames.filter { it.startsWith("data:") && !it.contains("[DONE]") }
        assertTrue(dataFrames.isNotEmpty())
        dataFrames.forEach { frame ->
            assertTrue(frame.contains("\"candidates\""), "Frame must contain candidates array: $frame")
        }
    }
}
