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
            ActivityRecorder.record(
                method = "POST",
                path = path,
                modelId = activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
                requestedModelId = route.requestedModelId,
                providerName = activeRoute.provider.name,
                statusCode = status,
                durationMs = System.currentTimeMillis() - startTime,
                isOfficialPassthrough = false,
                errorMessage = errorChunk?.message ?: encoderError?.message,
                fallbackAttempted = fallbackAttempted,
                fallbackSucceeded = fallbackSucceeded,
                usage = usage
            )
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
            return
        }

        var status = 200
        var errorMessage: String? = null
        var emittedBusinessFrame = false
        var latestUsage: NeutralUsage? = null

        var primaryChannel = openProviderStream(route)
        var primaryFirst = primaryChannel.receiveCatching().getOrNull()
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
                    primaryFirst = primaryChannel.receiveCatching().getOrNull()
                    fallbackSucceeded = primaryFirst !is NeutralStreamChunk.Error
                    if (primaryFirst is NeutralStreamChunk.Error && !primaryFirst.responseStarted) {
                        val fallbackError = primaryFirst as NeutralStreamChunk.Error
                        primaryChannel.cancel()
                        status = fallbackError.statusCode
                        errorMessage = fallbackError.message
                        ActivityRecorder.record(
                            method = "POST",
                            path = path,
                            modelId = fallbackRoute.virtualModel?.id ?: fallbackRoute.upstreamModel.id,
                            requestedModelId = route.requestedModelId,
                            providerName = fallbackRoute.provider.name,
                            statusCode = status,
                            durationMs = System.currentTimeMillis() - startTime,
                            isOfficialPassthrough = false,
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
                    ActivityRecorder.record(
                        method = "POST",
                        path = path,
                        modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                        requestedModelId = route.requestedModelId,
                        providerName = route.provider.name,
                        statusCode = status,
                        durationMs = System.currentTimeMillis() - startTime,
                        isOfficialPassthrough = false,
                        errorMessage = errorMessage,
                        fallbackAttempted = true,
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
                ActivityRecorder.record(
                    method = "POST",
                    path = path,
                    modelId = route.virtualModel?.id ?: route.upstreamModel.id,
                    requestedModelId = route.requestedModelId,
                    providerName = route.provider.name,
                    statusCode = status,
                    durationMs = System.currentTimeMillis() - startTime,
                    isOfficialPassthrough = false,
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
                        if (frame.isNotEmpty()) {
                            emittedBusinessFrame = true
                        }
                        write(frame)
                        flush()
                    }
                }

                suspend fun consumeChannel(
                    channel: ReceiveChannel<NeutralStreamChunk>,
                    first: NeutralStreamChunk?,
                    encoder: ResponseEncoder.GeminiStreamEncoder
                ): Boolean {
                    var failed = false
                    var next: NeutralStreamChunk? = first
                    try {
                        if (next == null) next = channel.receiveCatching().getOrNull()
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
                            next = channel.receiveCatching().getOrNull()
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
                try {
                    var next = firstChunk
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
                            next = primaryChannel.receiveCatching().getOrNull()
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
        } catch (error: Exception) {
            status = 502
            errorMessage = error.message ?: "Provider stream failed"
        }
        if (fallbackAttempted && status >= 400) {
            fallbackSucceeded = false
        }
        ActivityRecorder.record(
            method = "POST",
            path = path,
            modelId = activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
            requestedModelId = route.requestedModelId,
            providerName = activeRoute.provider.name,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = false,
            errorMessage = errorMessage,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            usage = latestUsage
        )
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
        return error.statusCode in setOf(404, 429, 500, 502, 503, 504) ||
                error.message.contains("rate limit", ignoreCase = true) ||
                error.message.contains("timed out", ignoreCase = true) ||
                error.message.contains("timeout", ignoreCase = true) ||
                error.message.contains("connection", ignoreCase = true) ||
                error.message.contains("overloaded", ignoreCase = true)
    }
}
