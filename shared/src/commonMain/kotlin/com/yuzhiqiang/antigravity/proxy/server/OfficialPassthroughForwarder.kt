package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.resolveActivityIdentity
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import io.ktor.client.request.prepareRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.delay

internal class OfficialPassthroughForwarder(
    private val configStore: ConfigStore,
    private val actualPortProvider: () -> Int
) {
    private val responseHandler = OfficialPassthroughResponseHandler(actualPortProvider)

    suspend fun forwardOfficial(
        call: ApplicationCall,
        path: String,
        rawBody: ByteArray,
        modelId: String?,
        startTime: Long,
        queueWaitMs: Long? = null
    ) {
        val isDebug = configStore.currentConfig.isDebugMode
        val requestHeaders = if (isDebug) extractRequestHeaders(call) else null
        val requestBody = OfficialPassthroughRequestSupport.debugBody(rawBody, isDebug)

        val logId = ActivityRecorder.startActivity(
            method = call.request.httpMethod.value,
            path = path,
            modelIdentity = modelId?.let {
                ModelObservation(requestedModelId = it, catalogModelId = it).resolveActivityIdentity()
            },
            clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call),
            providerName = "Official Cloud Code",
            isOfficialPassthrough = true,
            timestamp = startTime,
            queueWaitMs = queueWaitMs,
            requestHeaders = requestHeaders,
            requestBody = requestBody
        )
        val officialUrlResult = OfficialPassthroughRouting.officialUrl(
            path,
            call.request.queryString(),
            actualPortProvider
        )
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = 502,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = message,
                errorSource = StreamErrorSource.STUDIO_PROXY.name,
                responseBody = if (isDebug) message else null
            )
            OfficialPassthroughErrorResponder.respondError(
                call,
                HttpStatusCode.BadGateway,
                message,
                "native_forwarding_failed"
            )
            return
        }
        val officialUrl = officialUrlResult.getOrThrow()
        val isStreaming = path.contains("streamGenerateContent") ||
                (call.request.headers[HttpHeaders.Accept]?.contains("text/event-stream") == true)
        val maxRetries = 3
        val baseDelayMs = 500L
        var attempt = 0
        var responseStarted = false
        var lastStatus = 200
        var lastErrorMessage: String? = null
        var lastErrorSource: StreamErrorSource? = null

        while (attempt <= maxRetries) {
            attempt++
            if (attempt > 1) {
                ActivityRecorder.updateRetryCount(logId, attempt - 1)
            }
            var retryNeeded = false
            try {
                val result = ProviderAdapter.officialClientFor(officialUrl).prepareRequest(officialUrl) {
                    OfficialPassthroughRequestSupport.applyRequest(this, call, rawBody)
                }.execute { response ->
                    lastStatus = response.status.value
                    responseHandler.handle(
                        call = call,
                        path = path,
                        modelId = modelId,
                        startTime = startTime,
                        logId = logId,
                        isDebug = isDebug,
                        isStreaming = isStreaming,
                        attempt = attempt,
                        canRetryStatus = attempt <= maxRetries,
                        onResponseStarted = { responseStarted = true },
                        response = response
                    )
                }
                lastStatus = result.status
                responseStarted = result.responseStarted
                retryNeeded = result.retryNeeded
                if (result.errorMessage != null) lastErrorMessage = result.errorMessage
                if (result.errorSource != null) lastErrorSource = result.errorSource

                if (responseStarted || !retryNeeded) {
                    return
                }
            } catch (error: Exception) {
                lastErrorMessage = error.message ?: "Official Cloud Code passthrough failed"
                lastErrorSource = StreamErrorSource.UPSTREAM_TRANSPORT
                if (attempt <= maxRetries) {
                    val backoffMs = ByokForwardHandler.calculateBackoff(attempt, baseDelayMs)
                    delay(backoffMs)
                    continue
                }
                break
            }

            if (retryNeeded && attempt <= maxRetries) {
                val backoffMs = ByokForwardHandler.calculateBackoff(attempt, baseDelayMs)
                delay(backoffMs)
                continue
            }
        }

        if (!responseStarted) {
            val finalStatus = if (lastStatus >= 400) lastStatus else 502
            val finalError = ByokForwardHandler.toUserFacingError(
                NeutralStreamChunk.Error(
                    message = lastErrorMessage ?: "上游服务转发失败（HTTP $finalStatus）",
                    statusCode = finalStatus,
                    source = lastErrorSource ?: StreamErrorSource.STUDIO_PROXY
                ),
                includeSystemProxyGuidance = true
            )
            val finalMessage = finalError.message
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = finalStatus,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = finalMessage,
                errorSource = finalError.source.name,
                retryCount = attempt - 1
            )
            if (isStreaming) {
                val cloudCode = path.contains("/v1internal")
                val encoder = ResponseEncoder.newStreamEncoder(cloudCode, modelId)
                val errFrames = encoder.encode(NeutralStreamChunk.Error(finalMessage, finalStatus))
                call.response.headers.append("Cache-Control", "no-cache")
                call.response.headers.append("X-Accel-Buffering", "no")
                call.respondTextWriter(ContentType.Text.EventStream) {
                    errFrames.forEach { frame ->
                        write(frame)
                        flush()
                    }
                }
            } else {
                OfficialPassthroughErrorResponder.respondError(
                    call,
                    HttpStatusCode.fromValue(finalStatus),
                    finalMessage,
                    "native_forwarding_failed"
                )
            }
        }
    }
}
