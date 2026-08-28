package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import com.yuzhiqiang.antigravity.proxy.routing.ResolvedRoute
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList

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
            clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call),
            providerName = route.provider.name,
            isOfficialPassthrough = false,
            timestamp = startTime
        )

        if (!stream) {
            forwardNonStreaming(call, route, cloudCode, startTime, logId)
            return
        }

        forwardTransactionalStream(call, route, cloudCode, startTime, logId)
    }

    private suspend fun forwardNonStreaming(
        call: ApplicationCall,
        route: ResolvedRoute,
        cloudCode: Boolean,
        startTime: Long,
        logId: String
    ) {
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
            collected = try {
                collectProviderChunks(route)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                listOf(
                    NeutralStreamChunk.Error(
                        error.message ?: "上游请求失败",
                        ProviderAdapter.upstreamFailureStatus(error),
                        source = StreamErrorSource.UPSTREAM_TRANSPORT
                    )
                )
            }
            errorChunk = collected.filterIsInstance<NeutralStreamChunk.Error>()
                .firstOrNull()
                ?.let(::classifyErrorSource)
                ?.let(::toUserFacingError)
            if (errorChunk == null || attempt > maxRetries) {
                break
            }
            delay(calculateBackoff(attempt, baseDelayMs))
        }

        val retryCount = attempt - 1
        if (errorChunk != null) {
            collected = collected.map { chunk ->
                if (chunk is NeutralStreamChunk.Error) {
                    toUserFacingError(classifyErrorSource(chunk))
                } else {
                    chunk
                }
            }
        }
        val encoded = ResponseEncoder.encodeChunksToGeminiJsonResult(
            collected,
            route.request.targetUpstreamModelId,
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
        val nonStreamingFirstTokenMs = if (status < 400 && collected.any(::isMeaningfulContentChunk)) {
            durationMs
        } else {
            null
        }
        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = status,
            durationMs = durationMs,
            modelId = route.virtualModel?.id ?: route.upstreamModel.id,
            providerName = route.provider.name,
            errorMessage = errorChunk?.message ?: encoderError?.message,
            errorSource = errorChunk?.source?.name
                ?: encoderError?.let { StreamErrorSource.STUDIO_ADAPTER.name },
            usage = usage,
            firstTokenMs = nonStreamingFirstTokenMs,
            retryCount = retryCount
        )
        call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
    }

    private suspend fun forwardTransactionalStream(
        call: ApplicationCall,
        route: ResolvedRoute,
        cloudCode: Boolean,
        startTime: Long,
        logId: String
    ) {
        var status = 200
        var errorMessage: String? = null
        var errorSource: StreamErrorSource? = null
        var latestUsage: NeutralUsage? = null
        var firstTokenMs: Long? = null
        var attempt = 0

        val maxRetries = maxOf(0, route.provider.maxRetries)
        val baseDelayMs = maxOf(100L, route.provider.retryDelayMs)
        val idleTimeoutMs = maxOf(route.provider.streamIdleTimeoutMs, 600_000L)

        try {
            call.response.headers.append("Cache-Control", "no-cache")
            call.response.headers.append("X-Accel-Buffering", "no")
            call.respondTextWriter(ContentType.Text.EventStream) {
                suspend fun writeFrames(frames: List<String>) {
                    frames.forEach { frame ->
                        write(frame)
                        flush()
                    }
                }

                suspend fun writeHeartbeat() {
                    try {
                        write(": ping\n\n")
                        flush()
                    } catch (error: Throwable) {
                        throw DownstreamWriteException(error)
                    }
                }

                // 先提交 SSE 注释并持续发心跳；注释不包含业务内容，因此任何上游失败都仍可安全重试。
                writeHeartbeat()

                var successfulFrames: List<String>? = null
                var successfulUsage: NeutralUsage? = null
                var finalError: NeutralStreamChunk.Error? = null

                while (attempt <= maxRetries && successfulFrames == null) {
                    attempt++
                    if (attempt > 1) {
                        ActivityRecorder.updateRetryCount(logId, attempt - 1)
                    }

                    val bufferedAttempt = try {
                        val channel = openProviderStream(route)
                        try {
                            collectBufferedStreamAttempt(
                                channel = channel,
                                requestStartTimeMs = startTime,
                                idleTimeoutMs = idleTimeoutMs,
                                onHeartbeat = ::writeHeartbeat
                            )
                        } finally {
                            channel.cancel()
                        }
                    } catch (error: Throwable) {
                        if (error is kotlinx.coroutines.CancellationException || error is DownstreamWriteException) {
                            throw error
                        }
                        BufferedStreamAttempt(
                            chunks = emptyList(),
                            error = NeutralStreamChunk.Error(
                                error.message ?: "上游流式请求失败",
                                ProviderAdapter.upstreamFailureStatus(error),
                                source = StreamErrorSource.UPSTREAM_TRANSPORT
                            ),
                            firstTokenMs = null
                        )
                    }

                    val upstreamError = bufferedAttempt.error
                        ?.let(::classifyErrorSource)
                        ?.let(::toUserFacingError)
                    if (upstreamError == null && bufferedAttempt.isSuccessful) {
                        val encodedAttempt = encodeBufferedAttempt(
                            bufferedAttempt.chunks,
                            cloudCode,
                            route.request.targetUpstreamModelId
                        )
                        if (encodedAttempt.error == null) {
                            successfulFrames = encodedAttempt.frames
                            successfulUsage = bufferedAttempt.chunks
                                .filterIsInstance<NeutralStreamChunk.Completed>()
                                .lastOrNull { it.usage != null }
                                ?.usage
                            finalError = null
                            break
                        }
                        finalError = encodedAttempt.error
                    } else {
                        finalError = upstreamError ?: NeutralStreamChunk.Error(
                            "上游流在完成信令前关闭",
                            502,
                            source = StreamErrorSource.UPSTREAM_TRANSPORT
                        )
                    }

                    if (attempt <= maxRetries) {
                        delay(calculateBackoff(attempt, baseDelayMs))
                        writeHeartbeat()
                    }
                }

                val frames = successfulFrames
                if (frames != null) {
                    latestUsage = successfulUsage
                    if (frames.any { it.isNotEmpty() && !it.startsWith(":") }) {
                        firstTokenMs = System.currentTimeMillis() - startTime
                        ActivityRecorder.updateFirstToken(logId, firstTokenMs ?: 0L)
                    }
                    writeFrames(frames)
                } else {
                    val exhaustedError = finalError ?: NeutralStreamChunk.Error(
                        "服务商未能提供有效响应",
                        502,
                        source = StreamErrorSource.UPSTREAM_TRANSPORT
                    )
                    status = exhaustedError.statusCode
                    errorMessage = exhaustedError.message
                    errorSource = exhaustedError.source
                    val encoder = ResponseEncoder.newStreamEncoder(
                        cloudCode,
                        route.request.targetUpstreamModelId
                    )
                    writeFrames(encoder.encode(exhaustedError))
                }
            }
        } catch (error: Throwable) {
            val isClientCancellation = error is kotlinx.coroutines.CancellationException ||
                    error is DownstreamWriteException ||
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
            errorSource = StreamErrorSource.STUDIO_PROXY
        }

        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            modelId = route.virtualModel?.id ?: route.upstreamModel.id,
            providerName = route.provider.name,
            errorMessage = errorMessage,
            errorSource = errorSource?.name,
            usage = latestUsage,
            firstTokenMs = firstTokenMs,
            retryCount = attempt - 1
        )
    }

    private class DownstreamWriteException(cause: Throwable) : RuntimeException(cause)

    private data class EncodedAttempt(
        val frames: List<String>,
        val error: NeutralStreamChunk.Error?
    )

    private fun encodeBufferedAttempt(
        chunks: List<NeutralStreamChunk>,
        cloudCode: Boolean,
        modelId: String?
    ): EncodedAttempt {
        val encoder = ResponseEncoder.newStreamEncoder(cloudCode, modelId)
        val frames = mutableListOf<String>()
        for (chunk in chunks) {
            frames += encoder.encode(chunk)
            if (encoder.failureStatusCode != null) {
                return EncodedAttempt(
                    emptyList(),
                    NeutralStreamChunk.Error(
                        encoder.failureMessage ?: "Failed to encode provider response",
                        encoder.failureStatusCode ?: 502,
                        source = if (chunk is NeutralStreamChunk.Error) {
                            chunk.source
                        } else {
                            StreamErrorSource.STUDIO_ADAPTER
                        }
                    )
                )
            }
        }
        frames += encoder.finish()
        val failureStatus = encoder.failureStatusCode
        if (failureStatus != null) {
            return EncodedAttempt(
                emptyList(),
                NeutralStreamChunk.Error(
                    encoder.failureMessage ?: "Failed to finalize provider response",
                    failureStatus,
                    source = StreamErrorSource.STUDIO_ADAPTER
                )
            )
        }
        return EncodedAttempt(frames, null)
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
        internal fun toUserFacingError(
            error: NeutralStreamChunk.Error,
            includeSystemProxyGuidance: Boolean = false
        ): NeutralStreamChunk.Error {
            if (error.source != StreamErrorSource.UPSTREAM_TRANSPORT) return error
            val message = error.message.trim()
            val normalized = message.lowercase()
            val guidance = when {
                normalized.contains("failed to connect") ||
                        normalized.contains("connection refused") ||
                        normalized.contains("connectexception") ||
                        normalized.contains("no route to host") ||
                        normalized.contains("network is unreachable") -> if (includeSystemProxyGuidance) {
                    "上游服务连接异常，请检查上游服务或系统网络代理是否正常运行，并确认地址与端口配置正确"
                } else {
                    "上游服务连接异常，请检查上游服务是否正常运行，并确认服务地址与端口配置正确"
                }

                normalized.contains("timed out") || normalized.contains("timeout") -> if (includeSystemProxyGuidance) {
                    "上游服务连接超时，请检查上游服务状态、网络连接与系统网络代理"
                } else {
                    "上游服务连接超时，请检查上游服务状态与网络连接"
                }

                else -> if (includeSystemProxyGuidance) {
                    "上游服务连接异常，请检查上游服务状态、网络连接与系统网络代理"
                } else {
                    "上游服务连接异常，请检查上游服务状态与网络连接"
                }
            }
            if (message.startsWith(guidance)) return error
            return error.copy(message = "$guidance（原始错误：$message）")
        }

        internal fun classifyErrorSource(error: NeutralStreamChunk.Error): NeutralStreamChunk.Error {
            if (error.source == StreamErrorSource.UPSTREAM_RESPONSE ||
                error.source == StreamErrorSource.UPSTREAM_TRANSPORT
            ) {
                return error
            }
            val message = error.message.lowercase()
            val hasExplicitUpstreamErrorShape = message.contains("api error") ||
                    message.contains("upstream error") ||
                    message.contains("stream error:")
            if (error.source == StreamErrorSource.STUDIO_ADAPTER && !hasExplicitUpstreamErrorShape) {
                return error
            }
            val source = when {
                hasExplicitUpstreamErrorShape -> StreamErrorSource.UPSTREAM_RESPONSE

                message.contains("request failed") ||
                        message.contains("failed to read") ||
                        message.contains("stream ended before completion") ||
                        message.contains("stream disconnected") ||
                        message.contains("closed before") ||
                        message.contains("connection") ||
                        message.contains("socket") ||
                        message.contains("timeout") ||
                        message.contains("timed out") ||
                        message.contains("eof") ||
                        message.contains("tls") -> StreamErrorSource.UPSTREAM_TRANSPORT

                message.contains("invalid") ||
                        message.contains("parse") ||
                        message.contains("解析") -> StreamErrorSource.STUDIO_ADAPTER

                else -> StreamErrorSource.STUDIO_PROXY
            }
            return error.copy(source = source)
        }

        fun isRetryableError(statusCode: Int): Boolean {
            return (statusCode in 500..599 && statusCode != 501) ||
                    statusCode == 429 ||
                    statusCode == 408 ||
                    statusCode == 499
        }

        fun isRetryableError(error: NeutralStreamChunk.Error): Boolean {
            if (!error.responseStarted) return true
            if (isRetryableError(error.statusCode)) return true
            val message = error.message.lowercase()
            return message.contains("tls handshake") ||
                    message.contains("handshake") ||
                    message.contains("eof") ||
                    message.contains("connection reset") ||
                    message.contains("connection refused") ||
                    message.contains("broken pipe") ||
                    message.contains("socket") ||
                    message.contains("timed out") ||
                    message.contains("timeout") ||
                    message.contains("stream disconnected") ||
                    message.contains("closed before") ||
                    message.contains("overloaded") ||
                    message.contains("rate limit") ||
                    message.contains("cancel") ||
                    message.contains("reset")
        }

        fun calculateBackoff(attempt: Int, baseDelayMs: Long, maxDelayMs: Long = 5_000L): Long {
            val factor = 1L shl minOf(attempt - 1, 5)
            val rawDelay = minOf(baseDelayMs * factor, maxDelayMs)
            val jitter = (rawDelay * 0.2 * kotlin.random.Random.nextDouble(-1.0, 1.0)).toLong()
            return maxOf(50L, rawDelay + jitter)
        }
    }
}
