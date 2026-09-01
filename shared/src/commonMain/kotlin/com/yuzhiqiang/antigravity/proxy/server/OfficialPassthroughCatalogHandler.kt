package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.resolveActivityIdentity
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import io.ktor.client.request.preparePost
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.queryString
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class OfficialPassthroughCatalogHandler(
    private val configStore: ConfigStore,
    private val actualPortProvider: () -> Int
) {
    suspend fun forwardOfficialCatalog(
        call: ApplicationCall,
        path: String,
        rawBody: String,
        startTime: Long,
        queueWaitMs: Long? = null
    ) {
        val officialUrlResult = OfficialPassthroughRouting.officialUrl(
            path,
            call.request.queryString(),
            actualPortProvider
        )
        val isDebug = configStore.currentConfig.isDebugMode
        val requestHeaders = if (isDebug) extractRequestHeaders(call) else null

        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(
                path,
                null,
                startTime,
                502,
                message,
                errorSource = StreamErrorSource.STUDIO_PROXY,
                queueWaitMs = queueWaitMs
            )
            OfficialPassthroughErrorResponder.respondError(
                call,
                HttpStatusCode.BadGateway,
                message,
                "native_forwarding_failed"
            )
            return
        }

        try {
            val officialUrl = officialUrlResult.getOrThrow()
            val response = ProviderAdapter.officialClientFor(officialUrl).preparePost(officialUrl) {
                OfficialPassthroughRequestSupport.applyCatalogRequest(this, call, rawBody)
            }.execute()

            val responseHeaders = if (isDebug) extractResponseHeaders(response.headers) else null
            val body = ProviderAdapter.readResponseBodyText(response).getOrElse { error ->
                throw IllegalStateException(error.message ?: "Failed to read official catalog response", error)
            }
            if (response.status.value !in 200..299) {
                recordFailure(
                    path,
                    null,
                    startTime,
                    response.status.value,
                    body,
                    errorSource = StreamErrorSource.UPSTREAM_RESPONSE,
                    queueWaitMs = queueWaitMs,
                    requestHeaders = requestHeaders,
                    requestBody = if (isDebug) rawBody else null,
                    responseHeaders = responseHeaders,
                    responseBody = if (isDebug) body else null
                )
                call.respondText(
                    OfficialPassthroughRouting.rewriteOfficialUrls(body, call, actualPortProvider),
                    response.contentType() ?: ContentType.Application.Json,
                    response.status
                )
                return
            }
            val parsedRoot = OfficialPassthroughJson.catalog.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("官方目录响应不是 JSON 对象")
            val root = JsonObject(parsedRoot - "error")
            OfficialCatalogProbe.setRawOfficialCatalog(body)
            val filtered = CatalogInjector.removeDisabledOfficialModels(
                root,
                configStore.currentConfig.disabledOfficialCatalogModelIds
            )
            val overridden = CatalogInjector.applyOfficialCompressionPolicies(
                filtered,
                configStore.currentConfig.compressionPolicyAssignments
            )
            val responseJson = CatalogInjector.injectCustomModels(overridden, configStore.currentConfig)
            val clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call)
            recordActivity(
                path = path,
                modelId = null,
                startTime = startTime,
                status = response.status.value,
                message = null,
                clientSource = clientSource,
                requestHeaders = requestHeaders,
                requestBody = if (isDebug) rawBody else null,
                responseHeaders = responseHeaders,
                responseBody = if (isDebug) responseJson.toString() else null,
                queueWaitMs = queueWaitMs
            )
            call.respondText(
                OfficialPassthroughRouting.rewriteOfficialUrls(responseJson.toString(), call, actualPortProvider),
                response.contentType() ?: ContentType.Application.Json,
                response.status
            )
        } catch (error: Exception) {
            respondCatalogFallback(
                call,
                path,
                startTime,
                error.message ?: "官方目录获取失败",
                rawBody,
                queueWaitMs
            )
        }
    }

    suspend fun respondCatalogFallback(
        call: ApplicationCall,
        path: String,
        startTime: Long,
        reason: String,
        rawBody: String? = null,
        queueWaitMs: Long? = null
    ) {
        val config = configStore.currentConfig
        val isDebug = config.isDebugMode
        val requestHeaders = if (isDebug) extractRequestHeaders(call) else null
        val baseCatalog = if (path.contains("fetchAvailableModels")) {
            buildJsonObject {
                put("response", buildJsonObject { put("models", JsonObject(emptyMap())) })
            }
        } else {
            buildJsonObject { put("models", JsonArray(emptyList())) }
        }
        val fallback = CatalogInjector.injectCustomModels(baseCatalog, config)
        val hasCustomModels = CatalogInjector.customCatalogEntries(config).isNotEmpty()
        val status = if (hasCustomModels) HttpStatusCode.OK else HttpStatusCode.BadGateway
        val errorSource = if (reason.contains("JSON", ignoreCase = true) || reason.contains("解析")) {
            StreamErrorSource.STUDIO_ADAPTER
        } else {
            StreamErrorSource.UPSTREAM_TRANSPORT
        }
        val finalReason = if (status == HttpStatusCode.BadGateway) {
            ByokForwardHandler.toUserFacingError(
                NeutralStreamChunk.Error(reason, status.value, source = errorSource),
                includeSystemProxyGuidance = true
            ).message
        } else {
            reason
        }
        val clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call)
        recordActivity(
            path = path,
            modelId = null,
            startTime = startTime,
            status = status.value,
            message = if (status == HttpStatusCode.OK) null else finalReason,
            errorSource = errorSource.takeIf { status != HttpStatusCode.OK },
            clientSource = clientSource,
            requestHeaders = requestHeaders,
            requestBody = if (isDebug) rawBody else null,
            responseBody = if (isDebug) fallback.toString() else null,
            queueWaitMs = queueWaitMs
        )
        if (hasCustomModels) {
            call.respondText(
                OfficialPassthroughRouting.rewriteOfficialUrls(fallback.toString(), call, actualPortProvider),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
            return
        }
        OfficialPassthroughErrorResponder.respondError(
            call,
            status,
            finalReason,
            if (status == HttpStatusCode.BadGateway) "native_forwarding_failed" else "internal"
        )
    }

    private fun recordFailure(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        errorSource: StreamErrorSource = StreamErrorSource.STUDIO_PROXY,
        queueWaitMs: Long? = null,
        clientSource: String? = null,
        requestHeaders: Map<String, String>? = null,
        requestBody: String? = null,
        responseHeaders: Map<String, String>? = null,
        responseBody: String? = null
    ) {
        recordActivity(
            path,
            modelId,
            startTime,
            status,
            message,
            method,
            errorSource = errorSource,
            queueWaitMs = queueWaitMs,
            clientSource = clientSource,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseHeaders = responseHeaders,
            responseBody = responseBody
        )
    }

    private fun recordActivity(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        errorSource: StreamErrorSource? = null,
        queueWaitMs: Long? = null,
        clientSource: String? = null,
        requestHeaders: Map<String, String>? = null,
        requestBody: String? = null,
        responseHeaders: Map<String, String>? = null,
        responseBody: String? = null
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelIdentity = modelId?.let {
                ModelObservation(requestedModelId = it, catalogModelId = it).resolveActivityIdentity()
            },
            clientSource = clientSource,
            providerName = "Official Cloud Code",
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = true,
            timestamp = startTime,
            errorMessage = message,
            errorSource = errorSource?.name,
            queueWaitMs = queueWaitMs,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseHeaders = responseHeaders,
            responseBody = responseBody
        )
    }
}
