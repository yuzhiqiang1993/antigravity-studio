package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement

internal data class OfficialResponseHandlingResult(
    val status: Int,
    val responseStarted: Boolean = false,
    val retryNeeded: Boolean = false,
    val errorMessage: String? = null,
    val errorSource: StreamErrorSource? = null
)

internal class OfficialPassthroughResponseHandler(
    private val actualPortProvider: () -> Int
) {
    suspend fun handle(
        call: ApplicationCall,
        path: String,
        modelId: String?,
        startTime: Long,
        logId: String,
        isDebug: Boolean,
        isStreaming: Boolean,
        attempt: Int,
        canRetryStatus: Boolean,
        onResponseStarted: () -> Unit,
        response: HttpResponse
    ): OfficialResponseHandlingResult {
        val status = response.status.value
        val responseContentType = response.contentType() ?: ContentType.Application.Json
        val responseIsStreaming = isStreaming || responseContentType.match(ContentType.Text.EventStream)
        val responseHeaders = if (isDebug) extractResponseHeaders(response.headers) else null

        if (status >= 400 && canRetryStatus) {
            return OfficialResponseHandlingResult(
                status = status,
                retryNeeded = true,
                errorMessage = "Official Cloud Code API error ($status)",
                errorSource = StreamErrorSource.UPSTREAM_RESPONSE
            )
        }

        if (responseIsStreaming) {
            return handleStreamingResponse(
                call = call,
                path = path,
                modelId = modelId,
                startTime = startTime,
                logId = logId,
                isDebug = isDebug,
                attempt = attempt,
                onResponseStarted = onResponseStarted,
                status = status,
                responseContentType = responseContentType,
                responseHeaders = responseHeaders,
                response = response
            )
        }

        val bodyBytes = withTimeout(120_000L) {
            ProviderAdapter.readResponseBodyBytes(response)
        }.getOrElse { error ->
            throw IllegalStateException(error.message ?: "Failed to read official response body", error)
        }

        val responseBodyString = if (OfficialPassthroughRouting.isTextualContentType(responseContentType)) {
            OfficialPassthroughRouting.rewriteOfficialUrls(
                bodyBytes.toString(Charsets.UTF_8),
                call,
                actualPortProvider
            )
        } else {
            null
        }

        val nonStreamingUsage = responseBodyString?.let { text ->
            runCatching {
                val jsonElement: JsonElement = OfficialPassthroughJson.catalog.parseToJsonElement(text)
                OfficialPassthroughUsage.parseGeminiUsage(jsonElement)
            }.getOrNull()
        }

        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            usage = nonStreamingUsage,
            retryCount = attempt - 1,
            responseHeaders = responseHeaders,
            responseBody = if (isDebug) (responseBodyString ?: bodyBytes.decodeToString()) else null
        )
        OfficialPassthroughHttpSupport.copyForwardResponseHeaders(call, response)
        val responseBody = responseBodyString?.toByteArray(Charsets.UTF_8) ?: bodyBytes
        call.respondBytes(responseBody, responseContentType, response.status)
        return OfficialResponseHandlingResult(status = status)
    }

    private suspend fun handleStreamingResponse(
        call: ApplicationCall,
        path: String,
        modelId: String?,
        startTime: Long,
        logId: String,
        isDebug: Boolean,
        attempt: Int,
        onResponseStarted: () -> Unit,
        status: Int,
        responseContentType: ContentType,
        responseHeaders: Map<String, String>?,
        response: HttpResponse
    ): OfficialResponseHandlingResult {
        val source: ByteReadChannel = response.body()
        val firstBuffer = ByteArray(8192)
        var firstRead = -1
        var firstReadError: Throwable? = null
        try {
            firstRead = source.readAvailable(firstBuffer)
        } catch (error: Throwable) {
            firstReadError = error
        }

        if (firstReadError != null || firstRead <= 0) {
            return OfficialResponseHandlingResult(
                status = 502,
                retryNeeded = true,
                errorMessage = firstReadError?.message ?: "Stream closed before receiving initial data",
                errorSource = StreamErrorSource.UPSTREAM_TRANSPORT
            )
        }

        onResponseStarted()
        call.response.headers.append("Cache-Control", "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        OfficialPassthroughHttpSupport.copyForwardResponseHeaders(call, response)
        val ttft = System.currentTimeMillis() - startTime
        val firstTokenMs: Long? = ttft
        ActivityRecorder.updateFirstToken(logId, ttft)
        var latestUsage: NeutralUsage? = null
        val sseBuffer = StringBuilder()
        val debugStreamBody = if (isDebug) StringBuilder() else null
        var streamErrorCaught: Throwable? = null

        try {
            call.respondBytesWriter(contentType = responseContentType, status = response.status) {
                writeFully(firstBuffer, 0, firstRead)
                flush()
                val firstText = firstBuffer.decodeToString(0, firstRead)
                debugStreamBody?.append(firstText)
                sseBuffer.append(firstText)
                OfficialPassthroughUsage.extractUsageFromSseBuffer(sseBuffer, isFinal = false)?.let { usage ->
                    latestUsage = usage
                }

                val buffer = ByteArray(8192)
                try {
                    while (!source.isClosedForRead) {
                        val read = source.readAvailable(buffer)
                        if (read <= 0) {
                            if (read < 0) break
                            continue
                        }
                        writeFully(buffer, 0, read)
                        flush()

                        val chunkText = buffer.decodeToString(0, read)
                        debugStreamBody?.append(chunkText)
                        sseBuffer.append(chunkText)
                        OfficialPassthroughUsage.extractUsageFromSseBuffer(sseBuffer, isFinal = false)
                            ?.let { usage -> latestUsage = usage }
                    }
                } catch (streamError: Throwable) {
                    streamErrorCaught = streamError
                    val cloudCode = path.contains("/v1internal")
                    val encoder = ResponseEncoder.newStreamEncoder(cloudCode, modelId)
                    val errFrames = encoder.encode(
                        NeutralStreamChunk.Error(
                            streamError.message ?: "Official stream connection interrupted",
                            502
                        )
                    )
                    errFrames.forEach { frame ->
                        writeFully(frame.toByteArray(Charsets.UTF_8))
                        flush()
                    }
                }
            }
            OfficialPassthroughUsage.extractUsageFromSseBuffer(sseBuffer, isFinal = true)?.let { usage ->
                latestUsage = usage
            }
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = if (streamErrorCaught != null) 502 else status,
                durationMs = System.currentTimeMillis() - startTime,
                firstTokenMs = firstTokenMs,
                usage = latestUsage,
                errorMessage = streamErrorCaught?.message,
                errorSource = streamErrorCaught?.let { StreamErrorSource.UPSTREAM_TRANSPORT.name },
                retryCount = attempt - 1,
                responseHeaders = responseHeaders,
                responseBody = debugStreamBody?.toString()
            )
        } catch (error: Exception) {
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = 502,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = streamErrorCaught?.message ?: error.message,
                errorSource = if (streamErrorCaught != null) {
                    StreamErrorSource.UPSTREAM_TRANSPORT.name
                } else {
                    StreamErrorSource.STUDIO_PROXY.name
                },
                usage = latestUsage,
                firstTokenMs = firstTokenMs,
                retryCount = attempt - 1,
                responseHeaders = responseHeaders,
                responseBody = debugStreamBody?.toString()
            )
        }

        return OfficialResponseHandlingResult(
            status = status,
            responseStarted = true
        )
    }
}
