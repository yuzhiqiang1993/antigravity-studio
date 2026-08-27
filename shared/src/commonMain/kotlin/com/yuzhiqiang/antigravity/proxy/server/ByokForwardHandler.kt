package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.routing.ResolvedRoute
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

class ByokForwardHandler(
    private val configStore: ConfigStore
) {

    suspend fun forwardToByok(
        call: ApplicationCall,
        path: String,
        startTime: Long,
        route: ResolvedRoute
    ) {
        val cloudCode = path.contains("/v1internal")
        val stream = route.request.stream

        val logId = ActivityRecorder.startActivity(
            method = "POST",
            path = path,
            modelId = route.virtualModel?.id ?: route.upstreamModel.id,
            requestedModelId = route.requestedModelId,
            providerName = route.provider.name,
            isOfficialPassthrough = false,
            timestamp = startTime
        )

        if (!stream) {
            val maxRetries = maxOf(0, route.provider.maxRetries)
            val baseDelayMs = maxOf(100L, route.provider.retryDelayMs)
            var attempt = 0
            var collected: List<NeutralStreamChunk> = emptyList()
            var errorChunk: NeutralStreamChunk.Error? = null
            while (attempt <= maxRetries) {
                attempt++
                if (attempt > 1) {
                    ActivityRecorder.updateRetryCount(logId, attempt - 1)
                }
                collected = collectProviderChunks(route)
                errorChunk = collected.filterIsInstance<NeutralStreamChunk.Error>().firstOrNull()
                if (errorChunk == null || !isRetryableError(errorChunk) || attempt > maxRetries) {
                    break
                }
                val backoffMs = calculateBackoff(attempt, baseDelayMs)
                kotlinx.coroutines.delay(backoffMs)
            }
            val retryCount = attempt - 1
            val request = route.request
            val encoded = ResponseEncoder.encodeChunksToGeminiJsonResult(
                collected,
                request.targetUpstreamModelId,
                cloudCode
            )
            val encoderError = encoded.exceptionOrNull()
            val status = errorChunk?.statusCode ?: if (encoderError != null) 502 else 200
            val usage = collected.filterIsInstance<NeutralStreamChunk.Completed>()
                .lastOrNull { it.usage != null }
                ?.usage
            val body = encoded.getOrElse { error ->
                ResponseEncoder.encodeErrorToGeminiJson(
                    error.message ?: "Failed to encode provider response",
                    502,
                    cloudCode
                )
            }
            val durationMs = System.currentTimeMillis() - startTime
            val nonStreamingFirstTokenMs = if (status < 400 && collected.any(::isContentChunk)) durationMs else null
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = status,
                durationMs = durationMs,
                modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                providerName = route.provider.name,
                errorMessage = errorChunk?.message ?: encoderError?.message,
                usage = usage,
                firstTokenMs = nonStreamingFirstTokenMs,
                retryCount = retryCount
            )
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
            return
        }

        var status = 200
        var errorMessage: String? = null
        var emittedBusinessFrame = false
        var latestUsage: NeutralUsage? = null
        var firstTokenMs: Long? = null

        val maxRetries = maxOf(0, route.provider.maxRetries)
        val baseDelayMs = maxOf(100L, route.provider.retryDelayMs)
        val primaryTimeoutMs = maxOf(route.provider.requestTimeoutMs, 600_000L)

        var primaryChannel: ReceiveChannel<NeutralStreamChunk>? = null
        var primaryFirst: NeutralStreamChunk? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            attempt++
            if (attempt > 1) {
                ActivityRecorder.updateRetryCount(logId, attempt - 1)
            }
            val channel = openProviderStream(route)
            val firstChunk = withTimeoutOrNull(primaryTimeoutMs) {
                channel.receiveCatching().getOrNull()
            } ?: NeutralStreamChunk.Error("等待服务商响应首包超时（${primaryTimeoutMs / 1000}s 无响应）", 504)

            val isErrorBeforeResponse = firstChunk is NeutralStreamChunk.Error && !firstChunk.responseStarted
            if (isErrorBeforeResponse && isRetryableError(firstChunk as NeutralStreamChunk.Error) && attempt <= maxRetries) {
                channel.cancel()
                val backoffMs = calculateBackoff(attempt, baseDelayMs)
                kotlinx.coroutines.delay(backoffMs)
                continue
            }

            primaryChannel = channel
            primaryFirst = firstChunk
            break
        }

        val retryCount = attempt - 1
        val channel = primaryChannel ?: openProviderStream(route)
        val firstChunk = primaryFirst ?: NeutralStreamChunk.Error("服务商未能提供有效响应", 502)

        if (firstChunk is NeutralStreamChunk.Error && !firstChunk.responseStarted) {
            val primaryError = firstChunk as NeutralStreamChunk.Error
            channel.cancel()
            status = primaryError.statusCode
            errorMessage = primaryError.message
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = status,
                durationMs = System.currentTimeMillis() - startTime,
                modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                providerName = route.provider.name,
                errorMessage = errorMessage,
                retryCount = retryCount
            )
            val encoder = ResponseEncoder.newStreamEncoder(cloudCode, route.request.targetUpstreamModelId)
            val errFrames = encoder.encode(primaryError)
            call.response.headers.append("Cache-Control", "no-cache")
            call.response.headers.append("X-Accel-Buffering", "no")
            call.respondTextWriter(ContentType.Text.EventStream) {
                errFrames.forEach { frame ->
                    write(frame)
                    flush()
                }
            }
            return
        }

        try {
            call.response.headers.append("Cache-Control", "no-cache")
            call.response.headers.append("X-Accel-Buffering", "no")
            call.respondTextWriter(ContentType.Text.EventStream) {
                suspend fun writeFrames(frames: List<String>) {
                    frames.forEach { frame ->
                        if (frame.isNotEmpty() && !frame.startsWith(":")) {
                            emittedBusinessFrame = true
                        }
                        write(frame)
                        flush()
                    }
                }

                suspend fun receiveNextWithHeartbeat(
                    ch: ReceiveChannel<NeutralStreamChunk>,
                    idleTimeoutMs: Long
                ): NeutralStreamChunk? {
                    val deadline = System.currentTimeMillis() + idleTimeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        val result = withTimeoutOrNull(15_000L) {
                            ch.receiveCatching()
                        }
                        if (result != null) {
                            val chunk = result.getOrNull()
                            if (chunk != null) {
                                return chunk
                            }
                            val cause = result.exceptionOrNull()
                            if (cause != null) {
                                val status = ProviderAdapter.upstreamFailureStatus(cause)
                                return NeutralStreamChunk.Error(
                                    cause.message ?: "上游流式连接异常中断",
                                    status,
                                    responseStarted = true
                                )
                            }
                            if (ch.isClosedForReceive) return null
                        }
                        if (ch.isClosedForReceive) return null
                        writeFrames(listOf(": ping\n\n"))
                    }
                    return NeutralStreamChunk.Error(
                        "流式传输空闲超时（${idleTimeoutMs / 1000}s 未收到数据）",
                        504,
                        responseStarted = true
                    )
                }

                val encoder = ResponseEncoder.newStreamEncoder(cloudCode, route.request.targetUpstreamModelId)
                var streamStopped = false
                val idleTimeoutMs = maxOf(route.provider.streamIdleTimeoutMs, 600_000L)
                try {
                    var next: NeutralStreamChunk? = firstChunk
                    while (next != null && !streamStopped) {
                        val chunk = next
                        when (chunk) {
                            is NeutralStreamChunk.Error -> {
                                status = chunk.statusCode
                                errorMessage = chunk.message
                                streamStopped = true
                            }

                            is NeutralStreamChunk.Completed -> Unit
                            else -> Unit
                        }
                        if (firstTokenMs == null && isContentChunk(chunk)) {
                            val ttft = System.currentTimeMillis() - startTime
                            firstTokenMs = ttft
                            ActivityRecorder.updateFirstToken(logId, ttft)
                        }
                        if (chunk is NeutralStreamChunk.Completed && chunk.usage != null) {
                            latestUsage = chunk.usage
                        }
                        writeFrames(encoder.encode(chunk))
                        encoder.failureStatusCode?.let { failureStatus ->
                            status = failureStatus
                            errorMessage = encoder.failureMessage
                            streamStopped = true
                        }
                        if (!streamStopped) {
                            next = receiveNextWithHeartbeat(channel, idleTimeoutMs)
                        }
                    }
                } catch (error: Exception) {
                    status = 502
                    errorMessage = error.message ?: "Provider stream failed"
                    streamStopped = true
                    writeFrames(
                        encoder.encode(
                            NeutralStreamChunk.Error(
                                errorMessage ?: "Provider stream failed",
                                status,
                                responseStarted = true
                            )
                        )
                    )
                } finally {
                    channel.cancel()
                }
                if (!streamStopped && encoder.failureStatusCode == null) {
                    writeFrames(encoder.finish())
                    encoder.failureStatusCode?.let { failureStatus ->
                        status = failureStatus
                        errorMessage = encoder.failureMessage
                    }
                }
            }
        } catch (error: Throwable) {
            val isClientCancellation = error is kotlinx.coroutines.CancellationException ||
                    error.message?.contains("Cannot write to channel", ignoreCase = true) == true ||
                    error.message?.contains("ClosedWriteChannelException", ignoreCase = true) == true ||
                    error.message?.contains("Connection reset", ignoreCase = true) == true ||
                    error.message?.contains("Broken pipe", ignoreCase = true) == true
            if (isClientCancellation) {
                status = 499
                errorMessage = "客户端主动中断或关闭连接 (Client Closed Request)"
            } else {
                status = 502
                errorMessage = error.message ?: "Provider stream failed"
            }
        }
        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            modelId = route.virtualModel?.id ?: route.upstreamModel.id,
            providerName = route.provider.name,
            errorMessage = errorMessage,
            usage = latestUsage,
            firstTokenMs = firstTokenMs,
            retryCount = retryCount
        )
    }

    private fun isContentChunk(chunk: NeutralStreamChunk?): Boolean {
        return when (chunk) {
            is NeutralStreamChunk.TextDelta -> chunk.text.isNotEmpty()
            is NeutralStreamChunk.ReasoningDelta -> chunk.thinkingText.isNotEmpty() || !chunk.signature.isNullOrEmpty()
            is NeutralStreamChunk.ToolCallDelta -> true
            is NeutralStreamChunk.InlineDataDelta -> true
            else -> false
        }
    }

    private suspend fun collectProviderChunks(route: ResolvedRoute): List<NeutralStreamChunk> {
        val adapter = AdapterFactory.getAdapter(route.provider.protocol)
        val chunks = mutableListOf<NeutralStreamChunk>()
        adapter.sendStream(route.provider, route.request).toList(chunks)
        return chunks
    }

    private suspend fun openProviderStream(route: ResolvedRoute): ReceiveChannel<NeutralStreamChunk> {
        val adapter = AdapterFactory.getAdapter(route.provider.protocol)
        val streamFlow = adapter.sendStream(route.provider, route.request)
        return streamFlow.produceIn(CoroutineScope(currentCoroutineContext()))
    }

    companion object {
        fun isRetryableError(statusCode: Int): Boolean {
            return (statusCode in 500..599 && statusCode != 501) ||
                    statusCode == 429 ||
                    statusCode == 408 ||
                    statusCode == 499
        }

        fun isRetryableError(error: NeutralStreamChunk.Error): Boolean {
            if (isRetryableError(error.statusCode)) return true
            val msg = error.message.lowercase()
            return msg.contains("tls handshake") ||
                    msg.contains("handshake") ||
                    msg.contains("eof") ||
                    msg.contains("connection reset") ||
                    msg.contains("connection refused") ||
                    msg.contains("broken pipe") ||
                    msg.contains("socket") ||
                    msg.contains("timed out") ||
                    msg.contains("timeout") ||
                    msg.contains("stream disconnected") ||
                    msg.contains("closed before") ||
                    msg.contains("overloaded") ||
                    msg.contains("rate limit")
        }

        fun calculateBackoff(attempt: Int, baseDelayMs: Long, maxDelayMs: Long = 5_000L): Long {
            val factor = 1L shl minOf(attempt - 1, 5)
            val rawDelay = minOf(baseDelayMs * factor, maxDelayMs)
            val jitter = (rawDelay * 0.2 * kotlin.random.Random.nextDouble(-1.0, 1.0)).toLong()
            return maxOf(50L, rawDelay + jitter)
        }
    }
}
