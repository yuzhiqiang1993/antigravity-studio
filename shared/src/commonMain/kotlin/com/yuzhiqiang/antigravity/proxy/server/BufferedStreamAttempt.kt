package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

internal data class BufferedStreamAttempt(
    val chunks: List<NeutralStreamChunk>,
    val error: NeutralStreamChunk.Error?,
    val firstTokenMs: Long?
) {
    val isSuccessful: Boolean
        get() = error == null && chunks.any { it is NeutralStreamChunk.Completed }
}

/**
 * 完整缓冲一次上游流式尝试。只有收到完成信令才返回成功，调用方可安全丢弃失败尝试并重新请求。
 */
internal suspend fun collectBufferedStreamAttempt(
    channel: ReceiveChannel<NeutralStreamChunk>,
    requestStartTimeMs: Long,
    idleTimeoutMs: Long,
    heartbeatIntervalMs: Long = 15_000L,
    completionDrainMs: Long = 250L,
    clockMs: () -> Long = System::currentTimeMillis,
    onHeartbeat: suspend () -> Unit = {}
): BufferedStreamAttempt {
    val chunks = mutableListOf<NeutralStreamChunk>()
    var firstTokenMs: Long? = null
    var sawCompleted = false
    var idleDeadlineMs = clockMs() + idleTimeoutMs

    while (true) {
        val now = clockMs()
        val remainingIdleMs = maxOf(1L, idleDeadlineMs - now)
        val receiveTimeoutMs = if (sawCompleted) {
            minOf(completionDrainMs, remainingIdleMs)
        } else {
            minOf(heartbeatIntervalMs, remainingIdleMs)
        }
        val result = withTimeoutOrNull(receiveTimeoutMs) {
            channel.receiveCatching()
        }

        if (result == null) {
            if (sawCompleted) {
                return BufferedStreamAttempt(chunks, null, firstTokenMs)
            }
            if (clockMs() >= idleDeadlineMs) {
                return BufferedStreamAttempt(
                    chunks,
                    NeutralStreamChunk.Error(
                        "流式传输空闲超时（${idleTimeoutMs / 1000}s 未收到数据）",
                        504,
                        source = StreamErrorSource.UPSTREAM_TRANSPORT
                    ),
                    firstTokenMs
                )
            }
            onHeartbeat()
            continue
        }

        val chunk = result.getOrNull()
        if (chunk == null) {
            if (sawCompleted) {
                return BufferedStreamAttempt(chunks, null, firstTokenMs)
            }
            val cause = result.exceptionOrNull()
            return BufferedStreamAttempt(
                chunks,
                NeutralStreamChunk.Error(
                    cause?.message ?: "上游流在完成信令前关闭",
                    cause?.let(ProviderAdapter::upstreamFailureStatus) ?: 502,
                    source = StreamErrorSource.UPSTREAM_TRANSPORT
                ),
                firstTokenMs
            )
        }

        idleDeadlineMs = clockMs() + idleTimeoutMs
        if (chunk is NeutralStreamChunk.Error) {
            return BufferedStreamAttempt(chunks, chunk, firstTokenMs)
        }
        chunks += chunk
        if (firstTokenMs == null && isMeaningfulContentChunk(chunk)) {
            firstTokenMs = clockMs() - requestStartTimeMs
        }
        if (chunk is NeutralStreamChunk.Completed) {
            sawCompleted = true
        }
    }
}

internal fun isMeaningfulContentChunk(chunk: NeutralStreamChunk?): Boolean = when (chunk) {
    is NeutralStreamChunk.TextDelta -> chunk.text.isNotEmpty()
    is NeutralStreamChunk.ReasoningDelta -> chunk.thinkingText.isNotEmpty() || !chunk.signature.isNullOrEmpty()
    is NeutralStreamChunk.ToolCallDelta -> true
    is NeutralStreamChunk.InlineDataDelta -> true
    else -> false
}
