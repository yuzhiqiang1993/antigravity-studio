package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.routing.ResolvedRoute
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolutionException
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
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
        val config = configStore.currentConfig
        var activeRoute = route
        var fallbackAttempted = false
        var fallbackSucceeded = false

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
            var collected = collectProviderChunks(route)
            val primaryError = collected.filterIsInstance<NeutralStreamChunk.Error>().firstOrNull()
            if (primaryError != null && isRetryableFallbackError(primaryError)) {
                fallbackAttempted = true
                val fallbackResult = RouteResolver.resolveFallback(config, route)
                val fallbackRoute = fallbackResult.getOrNull()
                if (fallbackRoute != null) {
                    activeRoute = fallbackRoute
                    collected = collectProviderChunks(fallbackRoute)
                    fallbackSucceeded = collected.none { it is NeutralStreamChunk.Error }
                }
            }

            val request = activeRoute.request
            val errorChunk = collected.filterIsInstance<NeutralStreamChunk.Error>().firstOrNull()
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
                modelId = activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
                providerName = activeRoute.provider.name,
                errorMessage = errorChunk?.message ?: encoderError?.message,
                fallbackAttempted = fallbackAttempted,
                fallbackSucceeded = fallbackSucceeded,
                usage = usage,
                firstTokenMs = nonStreamingFirstTokenMs
            )
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
            return
        }

        var status = 200
        var errorMessage: String? = null
        var emittedBusinessFrame = false
        var latestUsage: NeutralUsage? = null
        var firstTokenMs: Long? = null

        var primaryChannel = openProviderStream(route)
        val primaryTimeoutMs = maxOf(route.provider.requestTimeoutMs, 600_000L)
        var primaryFirst = withTimeoutOrNull(primaryTimeoutMs) {
            primaryChannel.receiveCatching().getOrNull()
        } ?: NeutralStreamChunk.Error("等待服务商响应首包超时（${primaryTimeoutMs / 1000}s 无响应）", 504)

        if (primaryFirst is NeutralStreamChunk.Error && !primaryFirst.responseStarted) {
            val primaryError = primaryFirst as NeutralStreamChunk.Error
            if (isRetryableFallbackError(primaryError)) {
                fallbackAttempted = true
                val fallbackResult = RouteResolver.resolveFallback(config, route)
                val fallbackRoute = fallbackResult.getOrNull()
                if (fallbackRoute != null) {
                    primaryChannel.cancel()
                    activeRoute = fallbackRoute
                    primaryChannel = openProviderStream(fallbackRoute)
                    val fallbackTimeoutMs = maxOf(fallbackRoute.provider.requestTimeoutMs, 600_000L)
                    primaryFirst = withTimeoutOrNull(fallbackTimeoutMs) {
                        primaryChannel.receiveCatching().getOrNull()
                    } ?: NeutralStreamChunk.Error("备用服务商响应首包超时（${fallbackTimeoutMs / 1000}s 无响应）", 504)
                    fallbackSucceeded = primaryFirst !is NeutralStreamChunk.Error
                    if (primaryFirst is NeutralStreamChunk.Error && !primaryFirst.responseStarted) {
                        val fallbackError = primaryFirst as NeutralStreamChunk.Error
                        primaryChannel.cancel()
                        status = fallbackError.statusCode
                        errorMessage = fallbackError.message
                        ActivityRecorder.finishActivity(
                            id = logId,
                            statusCode = status,
                            durationMs = System.currentTimeMillis() - startTime,
                            modelId = fallbackRoute.virtualModel?.id ?: fallbackRoute.upstreamModel.id,
                            providerName = fallbackRoute.provider.name,
                            errorMessage = errorMessage,
                            fallbackAttempted = true,
                            fallbackSucceeded = false
                        )
                        call.respondText(
                            ResponseEncoder.encodeErrorToGeminiJson(errorMessage, status, cloudCode),
                            ContentType.Application.Json,
                            HttpStatusCode.fromValue(status)
                        )
                        return
                    }
                } else {
                    primaryChannel.cancel()
                    status = (fallbackResult.exceptionOrNull() as? RouteResolutionException)?.statusCode
                        ?: primaryError.statusCode
                    errorMessage = fallbackResult.exceptionOrNull()?.message ?: primaryError.message
                    ActivityRecorder.finishActivity(
                        id = logId,
                        statusCode = status,
                        durationMs = System.currentTimeMillis() - startTime,
                        modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                        providerName = route.provider.name,
                        errorMessage = errorMessage,
                        fallbackAttempted = false,
                        fallbackSucceeded = false
                    )
                    call.respondText(
                        ResponseEncoder.encodeErrorToGeminiJson(errorMessage ?: primaryError.message, status, cloudCode),
                        ContentType.Application.Json,
                        HttpStatusCode.fromValue(status)
                    )
                    return
                }
            } else {
                primaryChannel.cancel()
                status = primaryError.statusCode
                errorMessage = primaryError.message
                ActivityRecorder.finishActivity(
                    id = logId,
                    statusCode = status,
                    durationMs = System.currentTimeMillis() - startTime,
                    modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                    providerName = route.provider.name,
                    errorMessage = errorMessage
                )
                call.respondText(
                    ResponseEncoder.encodeErrorToGeminiJson(primaryError.message, status, cloudCode),
                    ContentType.Application.Json,
                    HttpStatusCode.fromValue(status)
                )
                return
            }
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
                    channel: ReceiveChannel<NeutralStreamChunk>,
                    idleTimeoutMs: Long
                ): NeutralStreamChunk? {
                    val deadline = System.currentTimeMillis() + idleTimeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        val chunk = withTimeoutOrNull(15_000L) {
                            channel.receiveCatching().getOrNull()
                        }
                        if (chunk != null) return chunk
                        if (channel.isClosedForReceive) return null
                        writeFrames(listOf(": ping\n\n"))
                    }
                    return NeutralStreamChunk.Error("流式传输空闲超时（${idleTimeoutMs / 1000}s 未收到数据）", 504)
                }

                suspend fun consumeChannel(
                    channel: ReceiveChannel<NeutralStreamChunk>,
                    first: NeutralStreamChunk?,
                    encoder: ResponseEncoder.GeminiStreamEncoder
                ): Boolean {
                    var failed = false
                    var next: NeutralStreamChunk? = first
                    val idleTimeoutMs = maxOf(activeRoute.provider.streamIdleTimeoutMs, 600_000L)
                    try {
                        if (next == null) next = receiveNextWithHeartbeat(channel, idleTimeoutMs)
                        while (next != null) {
                            val chunk = next
                            when (chunk) {
                                is NeutralStreamChunk.Error -> {
                                    failed = true
                                    status = chunk.statusCode
                                    errorMessage = chunk.message
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
                                failed = true
                                status = failureStatus
                                errorMessage = encoder.failureMessage
                            }
                            if (failed) break
                            next = receiveNextWithHeartbeat(channel, idleTimeoutMs)
                        }
                    } catch (error: Exception) {
                        failed = true
                        status = 502
                        errorMessage = error.message ?: "Provider stream failed"
                        writeFrames(
                            encoder.encode(
                                NeutralStreamChunk.Error(
                                    errorMessage ?: "Provider stream failed",
                                    status
                                )
                            )
                        )
                    } finally {
                        channel.cancel()
                    }
                    if (!failed && encoder.failureStatusCode == null) {
                        writeFrames(encoder.finish())
                        encoder.failureStatusCode?.let { failureStatus ->
                            failed = true
                            status = failureStatus
                            errorMessage = encoder.failureMessage
                        }
                    }
                    return failed
                }

                val primaryEncoder = ResponseEncoder.newStreamEncoder(cloudCode, activeRoute.request.targetUpstreamModelId)
                var primaryStopped = false
                val firstChunk = primaryFirst
                val primaryIdleTimeoutMs = maxOf(activeRoute.provider.streamIdleTimeoutMs, 600_000L)
                try {
                    var next: NeutralStreamChunk? = firstChunk
                    while (next != null && !primaryStopped) {
                        val chunk = next
                        if (chunk is NeutralStreamChunk.Error &&
                            !emittedBusinessFrame &&
                            !fallbackAttempted &&
                            isRetryableFallbackError(chunk)
                        ) {
                            fallbackAttempted = true
                            val fallbackResult = RouteResolver.resolveFallback(config, route)
                            val fallbackRoute = fallbackResult.getOrNull()
                            if (fallbackRoute != null) {
                                activeRoute = fallbackRoute
                                val fallbackEncoder = ResponseEncoder.newStreamEncoder(
                                    cloudCode,
                                    fallbackRoute.request.targetUpstreamModelId
                                )
                                fallbackSucceeded = !consumeChannel(
                                    openProviderStream(fallbackRoute),
                                    null,
                                    fallbackEncoder
                                )
                            } else {
                                status = chunk.statusCode
                                errorMessage = fallbackResult.exceptionOrNull()?.message ?: chunk.message
                                writeFrames(
                                    primaryEncoder.encode(
                                        NeutralStreamChunk.Error(errorMessage ?: chunk.message, status)
                                    )
                                )
                            }
                            primaryStopped = true
                        } else {
                            when (chunk) {
                                is NeutralStreamChunk.Error -> {
                                    status = chunk.statusCode
                                    errorMessage = chunk.message
                                    primaryStopped = true
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
                            writeFrames(primaryEncoder.encode(chunk))
                            primaryEncoder.failureStatusCode?.let { failureStatus ->
                                status = failureStatus
                                errorMessage = primaryEncoder.failureMessage
                                primaryStopped = true
                            }
                        }
                        if (!primaryStopped) {
                            next = receiveNextWithHeartbeat(primaryChannel, primaryIdleTimeoutMs)
                        }
                    }
                } catch (error: Exception) {
                    status = 502
                    errorMessage = error.message ?: "Provider stream failed"
                    primaryStopped = true
                    writeFrames(
                        primaryEncoder.encode(
                            NeutralStreamChunk.Error(errorMessage ?: "Provider stream failed", status)
                        )
                    )
                } finally {
                    primaryChannel.cancel()
                }
                if (!primaryStopped && primaryEncoder.failureStatusCode == null) {
                    writeFrames(primaryEncoder.finish())
                    primaryEncoder.failureStatusCode?.let { failureStatus ->
                        status = failureStatus
                        errorMessage = primaryEncoder.failureMessage
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
        if (fallbackAttempted && status >= 400) {
            fallbackSucceeded = false
        }
        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            modelId = activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
            providerName = activeRoute.provider.name,
            errorMessage = errorMessage,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            usage = latestUsage,
            firstTokenMs = firstTokenMs
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

    private fun isRetryableFallbackError(error: NeutralStreamChunk.Error): Boolean {
        return error.statusCode in setOf(404, 408, 429, 500, 502, 503, 504, 524) ||
                error.message.contains("rate limit", ignoreCase = true) ||
                error.message.contains("timed out", ignoreCase = true) ||
                error.message.contains("timeout", ignoreCase = true) ||
                error.message.contains("connection", ignoreCase = true) ||
                error.message.contains("disconnected", ignoreCase = true) ||
                error.message.contains("stream error", ignoreCase = true) ||
                error.message.contains("closed before", ignoreCase = true) ||
                error.message.contains("broken pipe", ignoreCase = true) ||
                error.message.contains("reset", ignoreCase = true) ||
                error.message.contains("overloaded", ignoreCase = true)
    }
}
