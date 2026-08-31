package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

internal data class StreamAttemptResult(
    val error: NeutralStreamChunk.Error?,
    val usage: NeutralUsage?,
    val firstByteMs: Long? = null,
    val firstTokenMs: Long? = null,
    val lastTokenMs: Long? = null,
    val maxChunkGapMs: Long? = null,
    val stallCount: Int = 0,
    val stallDurationMs: Long? = null,
    val committed: Boolean,
    val completed: Boolean
) {
    val isSuccessful: Boolean
        get() = error == null && completed
}

/**
 * 增量转发一次上游流。首个可编码业务帧写出前允许调用方丢弃本次尝试并重试；
 * 一旦有帧写入下游，本次尝试即提交，后续错误必须在当前流内结束，不能重新请求上游。
 */
internal suspend fun streamProviderAttempt(
    channel: ReceiveChannel<NeutralStreamChunk>,
    encoder: ResponseEncoder.GeminiStreamEncoder,
    requestStartTimeMs: Long,
    idleTimeoutMs: Long,
    heartbeatIntervalMs: Long = 15_000L,
    clockMs: () -> Long = System::currentTimeMillis,
    timingTracker: StreamTimingTracker = StreamTimingTracker(requestStartTimeMs),
    mapError: (NeutralStreamChunk.Error) -> NeutralStreamChunk.Error = { it },
    onFrames: suspend (List<String>) -> Unit,
    onFirstToken: suspend (Long) -> Unit = {},
    onHeartbeat: suspend () -> Unit = {}
): StreamAttemptResult {
    var latestUsage: NeutralUsage? = null
    var committed = false
    var sawCompleted = false
    var sawMeaningfulContent = false
    var idleDeadlineMs = clockMs() + idleTimeoutMs

    suspend fun emitFrames(frames: List<String>, containsMeaningfulContent: Boolean) {
        if (frames.isEmpty()) return
        onFrames(frames)
        committed = true
        if (containsMeaningfulContent) {
            val (elapsedMs, isFirst) = timingTracker.recordMeaningfulContent(clockMs())
            if (isFirst) {
                onFirstToken(elapsedMs)
            }
        }
    }

    fun result(
        error: NeutralStreamChunk.Error? = null,
        completed: Boolean = false
    ): StreamAttemptResult {
        val timing = timingTracker.snapshot()
        return StreamAttemptResult(
            error = error,
            usage = latestUsage,
            firstByteMs = timing.firstByteMs,
            firstTokenMs = timing.firstTokenMs,
            lastTokenMs = timing.lastTokenMs,
            maxChunkGapMs = timing.maxChunkGapMs,
            stallCount = timing.stallCount,
            stallDurationMs = timing.stallDurationMs,
            committed = committed,
            completed = completed
        )
    }

    suspend fun fail(rawError: NeutralStreamChunk.Error): StreamAttemptResult {
        val error = mapError(rawError)
        if (committed) {
            emitFrames(encoder.encode(error), containsMeaningfulContent = false)
        }
        return result(error = error)
    }

    suspend fun finish(): StreamAttemptResult {
        val frames = encoder.finish()
        val failureStatus = encoder.failureStatusCode
        emitFrames(
            frames,
            containsMeaningfulContent = failureStatus == null &&
                    sawMeaningfulContent && timingTracker.snapshot().firstTokenMs == null
        )
        if (failureStatus != null) {
            return result(
                error = NeutralStreamChunk.Error(
                    encoder.failureMessage ?: "Failed to finalize provider response",
                    failureStatus,
                    source = StreamErrorSource.STUDIO_ADAPTER
                )
            )
        }
        return result(completed = true)
    }

    while (true) {
        val now = clockMs()
        val remainingIdleMs = maxOf(1L, idleDeadlineMs - now)
        val receiveTimeoutMs = minOf(heartbeatIntervalMs, remainingIdleMs)
        val received = withTimeoutOrNull(receiveTimeoutMs) {
            channel.receiveCatching()
        }

        if (received == null) {
            if (clockMs() >= idleDeadlineMs) {
                return fail(
                    NeutralStreamChunk.Error(
                        "流式传输空闲超时（${idleTimeoutMs / 1000}s 未收到数据）",
                        504,
                        source = StreamErrorSource.UPSTREAM_TRANSPORT
                    )
                )
            }
            onHeartbeat()
            continue
        }

        val chunk = received.getOrNull()
        if (chunk == null) {
            val cause = received.exceptionOrNull()
            if (cause == null && sawCompleted) return finish()
            return fail(
                NeutralStreamChunk.Error(
                    cause?.message ?: "上游流在完成信令前关闭",
                    cause?.let(ProviderAdapter::upstreamFailureStatus) ?: 502,
                    source = StreamErrorSource.UPSTREAM_TRANSPORT
                )
            )
        }

        val chunkReceivedAt = clockMs()
        timingTracker.recordFirstByte(chunkReceivedAt)
        idleDeadlineMs = chunkReceivedAt + idleTimeoutMs
        if (chunk is NeutralStreamChunk.Error) return fail(chunk)

        if (chunk is NeutralStreamChunk.Completed) {
            latestUsage = chunk.usage ?: latestUsage
            sawCompleted = true
        }
        val meaningfulContent = isMeaningfulContentChunk(chunk)
        sawMeaningfulContent = sawMeaningfulContent || meaningfulContent
        val frames = encoder.encode(chunk)
        val failureStatus = encoder.failureStatusCode
        if (failureStatus != null) {
            emitFrames(frames, containsMeaningfulContent = false)
            return result(
                error = NeutralStreamChunk.Error(
                    encoder.failureMessage ?: "Failed to encode provider response",
                    failureStatus,
                    source = StreamErrorSource.STUDIO_ADAPTER
                )
            )
        }
        emitFrames(frames, containsMeaningfulContent = meaningfulContent)
    }
}

internal fun isMeaningfulContentChunk(chunk: NeutralStreamChunk?): Boolean = when (chunk) {
    is NeutralStreamChunk.TextDelta -> chunk.text.isNotEmpty()
    is NeutralStreamChunk.ReasoningDelta -> chunk.thinkingText.isNotEmpty() || !chunk.signature.isNullOrEmpty()
    is NeutralStreamChunk.ToolCallDelta -> true
    is NeutralStreamChunk.InlineDataDelta -> true
    else -> false
}
