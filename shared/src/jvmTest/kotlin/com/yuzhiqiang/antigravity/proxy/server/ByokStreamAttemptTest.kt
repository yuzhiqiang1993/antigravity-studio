package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByokStreamAttemptTest {

    @Test
    fun streamingAttemptEmitsFirstBusinessFrameBeforeCompletion() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val emitted = Channel<List<String>>(Channel.UNLIMITED)
        val attempt = async {
            streamProviderAttempt(
                channel = channel,
                encoder = ResponseEncoder.newStreamEncoder(),
                requestStartTimeMs = System.currentTimeMillis(),
                idleTimeoutMs = 1_000L,
                onFrames = { emitted.send(it) }
            )
        }

        channel.send(NeutralStreamChunk.TextDelta("first token"))
        val firstFrames = withTimeout(1_000L) { emitted.receive() }

        assertTrue(firstFrames.any { it.contains("first token") })
        assertFalse(attempt.isCompleted)

        channel.send(NeutralStreamChunk.Completed())
        channel.close()
        assertTrue(attempt.await().isSuccessful)
    }

    @Test
    fun streamingAttemptDoesNotTreatCandidateCompletionAsWholeStreamEnd() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        val attempt = async {
            streamProviderAttempt(
                channel = channel,
                encoder = ResponseEncoder.newStreamEncoder(),
                requestStartTimeMs = System.currentTimeMillis(),
                idleTimeoutMs = 5_000L,
                heartbeatIntervalMs = 20L,
                onFrames = { frames += it }
            )
        }

        channel.send(NeutralStreamChunk.TextDelta("first", choiceIndex = 0))
        channel.send(NeutralStreamChunk.Completed(choiceIndex = 0))
        delay(60L)

        assertFalse(attempt.isCompleted)

        channel.send(NeutralStreamChunk.TextDelta("second", choiceIndex = 1))
        channel.send(NeutralStreamChunk.Completed(choiceIndex = 1))
        channel.close()
        assertTrue(attempt.await().isSuccessful)
        assertTrue(frames.any { it.contains("first") })
        assertTrue(frames.any { it.contains("second") })
    }

    @Test
    fun streamingAttemptTreatsIdleTimeoutAfterCandidateCompletionAsFailure() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        channel.send(NeutralStreamChunk.Completed(choiceIndex = 0))

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = System.currentTimeMillis(),
            idleTimeoutMs = 80L,
            heartbeatIntervalMs = 20L,
            onFrames = {}
        )
        channel.close()

        assertFalse(result.isSuccessful)
        assertFalse(result.completed)
        assertEquals(504, result.error?.statusCode)
        assertEquals(StreamErrorSource.UPSTREAM_TRANSPORT, result.error?.source)
    }

    @Test
    fun streamingAttemptEndsCurrentStreamWhenErrorFollowsCommittedOutput() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        channel.send(NeutralStreamChunk.TextDelta("a".repeat(4_096)))
        channel.send(
            NeutralStreamChunk.Error(
                "stream error: stream disconnected before completion",
                502,
                source = StreamErrorSource.UPSTREAM_RESPONSE
            )
        )
        channel.close()

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = 0L,
            idleTimeoutMs = 1_000L,
            onFrames = { frames += it }
        )

        assertFalse(result.isSuccessful)
        assertTrue(result.committed)
        assertEquals(StreamErrorSource.UPSTREAM_RESPONSE, result.error?.source)
        assertTrue(frames.any { it.contains("a".repeat(256)) })
        assertTrue(frames.any { it.contains("Studio 代理异常") })
    }

    @Test
    fun streamingAttemptWritesInStreamErrorWhenCommittedStreamClosesBeforeCompletion() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        channel.send(NeutralStreamChunk.TextDelta("partial"))
        channel.close()

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = 0L,
            idleTimeoutMs = 1_000L,
            onFrames = { frames += it }
        )

        assertFalse(result.isSuccessful)
        assertTrue(result.committed)
        assertEquals(StreamErrorSource.UPSTREAM_TRANSPORT, result.error?.source)
        assertTrue(frames.any { it.contains("partial") })
        assertTrue(frames.any { it.contains("Studio 代理异常") })
    }

    @Test
    fun streamingAttemptSucceedsOnlyAfterCompletionSignal() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        channel.send(NeutralStreamChunk.TextDelta("complete answer"))
        channel.send(NeutralStreamChunk.Completed())
        channel.close()

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = 0L,
            idleTimeoutMs = 1_000L,
            onFrames = { frames += it }
        )

        assertTrue(result.isSuccessful)
        assertTrue(result.completed)
        assertTrue(result.committed)
        assertEquals(null, result.error)
        assertTrue(frames.any { it.contains("complete answer") })
        assertTrue(frames.any { it == "data: [DONE]\n\n" })
    }

    @Test
    fun streamingAttemptStillReportsErrorArrivingAfterCompletionDuringDrain() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        channel.send(NeutralStreamChunk.TextDelta("answer"))
        channel.send(NeutralStreamChunk.Completed())
        channel.send(NeutralStreamChunk.Error("late upstream failure", 502))
        channel.close()

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = 0L,
            idleTimeoutMs = 1_000L,
            onFrames = { frames += it }
        )

        assertFalse(result.isSuccessful)
        assertTrue(result.committed)
        assertEquals("late upstream failure", result.error?.message)
        assertTrue(frames.any { it.contains("late upstream failure") })
    }

    @Test
    fun streamingAttemptKeepsToolOnlyFailureUncommittedForSafeRetry() = runBlocking {
        val channel = Channel<NeutralStreamChunk>(Channel.UNLIMITED)
        val frames = mutableListOf<String>()
        channel.send(
            NeutralStreamChunk.ToolCallDelta(
                index = 0,
                id = "call-1",
                name = "search",
                argsText = "{\"query\":",
            )
        )
        channel.send(NeutralStreamChunk.Error("stream disconnected", 502))
        channel.close()

        val result = streamProviderAttempt(
            channel = channel,
            encoder = ResponseEncoder.newStreamEncoder(),
            requestStartTimeMs = 0L,
            idleTimeoutMs = 1_000L,
            onFrames = { frames += it }
        )

        assertFalse(result.isSuccessful)
        assertFalse(result.committed)
        assertTrue(frames.isEmpty())
    }
}
