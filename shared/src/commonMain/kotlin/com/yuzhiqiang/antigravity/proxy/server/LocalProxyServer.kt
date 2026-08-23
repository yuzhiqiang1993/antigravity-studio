package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolutionException
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class LocalProxyServer(
    private val configStore: ConfigStore
) {
    private companion object {
        const val MAX_REQUEST_BODY_BYTES = 4L * 1024L * 1024L
    }

    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val lifecycleMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _actualPort = MutableStateFlow(8321)
    val actualPort: StateFlow<Int> = _actualPort.asStateFlow()

    private val generationSemaphore = Semaphore(256)
    private val controlPlaneSemaphore = Semaphore(64)

    private val passthroughHandler = OfficialPassthroughHandler(configStore) { _actualPort.value }
    private val byokHandler = ByokForwardHandler(configStore)

    suspend fun start(desiredPort: Int = configStore.currentConfig.proxyPort): Result<Int> = lifecycleMutex.withLock {
        if (_isRunning.value) return Result.success(_actualPort.value)

        if (desiredPort !in 1024..65535) {
            return Result.failure(IllegalArgumentException("代理端口必须位于 1024 - 65535 之间"))
        }

        val availablePort = findAvailablePort(desiredPort, (desiredPort + 20).coerceAtMost(65535))
            ?: return Result.failure(IllegalStateException("No available port found near $desiredPort"))

        return try {
            val server = embeddedServer(CIO, host = "127.0.0.1", port = availablePort) {
                install(CORS) {
                    anyHost()
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Options)
                    allowHeader(HttpHeaders.Accept)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("X-Codeium-Csrf-Token")
                    allowHeader("X-Antigravity-Raw-Official")
                    anyMethod()
                    allowHeaders { true }
                }
                routing {
                    options("/{...}") {
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/{...}") {
                        val normalizedPath = normalizeProxyPath(call.request.path())
                                                when {
                            isFixedGetPath(normalizedPath) -> respondMethodNotAllowed(call)
                            isOfficialCatalogFetchPath(normalizedPath) -> {
                                controlPlaneSemaphore.withPermit { handleChatRequest(call) }
                            }
                            isGenerationPath(normalizedPath) -> {
                                generationSemaphore.withPermit { handleChatRequest(call) }
                            }
                            else -> controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                        }
                    }
                    get("/{...}") {
                        val normalizedPath = normalizeProxyPath(call.request.path())
                        when (normalizedPath) {
                            "/health" -> call.respondText(
                                """{"status":"ok","product":"antigravity-studio","port":$availablePort,"capabilities":{"models":true,"generate":true,"stream":true}}""",
                                ContentType.Application.Json
                            )
                            "/healthz" -> call.respondText(
                                """{"status":"ok","product":"antigravity-studio","port":$availablePort}""",
                                ContentType.Application.Json
                            )
                            "/v1/models", "/v1beta/models" -> {
                                controlPlaneSemaphore.withPermit { respondModelCatalog(call) }
                            }
                            "/antigravity/official-catalog" -> {
                                controlPlaneSemaphore.withPermit { respondPureOfficialCatalog(call) }
                            }
                            else -> if (isGenerationPath(normalizedPath)) {
                                respondMethodNotAllowed(call)
                            } else {
                                controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                            }
                        }
                    }
                    delete("/{...}") {
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    put("/{...}") {
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    patch("/{...}") {
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    head("/{...}") {
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                }
            }
            server.start(wait = false)
            persistActualPort(server, availablePort)
            serverEngine = server
            _isRunning.value = true
            _actualPort.value = availablePort
            Result.success(availablePort)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun persistActualPort(
        server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
        actualPort: Int
    ) {
        if (configStore.currentConfig.proxyPort == actualPort) {
            return
        }
        try {
            configStore.saveConfig(configStore.currentConfig.copy(proxyPort = actualPort))
        } catch (error: Exception) {
            server.stop(1000, 2000)
            throw IllegalStateException("代理实际端口回写配置失败：${error.message ?: "未知错误"}", error)
        }
    }

    private fun findAvailablePort(startPort: Int, endPort: Int): Int? {
        for (port in startPort..endPort) {
            try {
                java.net.ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).use {
                    return it.localPort
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    suspend fun stop() = lifecycleMutex.withLock {
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            _isRunning.value = false
        } catch (_: Exception) {
            _isRunning.value = false
        }
    }

    private suspend fun handleChatRequest(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val path = normalizeProxyPath(call.request.path())
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_REQUEST_BODY_BYTES) {
            val message = "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
            recordFailure(path, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        val rawBody = readLimitedRequestBody(call).getOrElse { error ->
            val message = error.message ?: "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
            recordFailure(path, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        if (rawBody.toByteArray(Charsets.UTF_8).size.toLong() > MAX_REQUEST_BODY_BYTES) {
            val message = "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
            recordFailure(path, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        val config = configStore.currentConfig

        if (isOfficialCatalogFetchPath(path)) {
            passthroughHandler.forwardOfficialCatalog(call, path, rawBody, startTime)
            return
        }

        val pathModelId = extractPathModelId(path)
        val bodyModelResult = AntigravityRequestParser.extractModelId(rawBody)
        if (bodyModelResult.isFailure &&
            !bodyModelResult.exceptionOrNull()?.message.orEmpty().startsWith("Missing model ID")
        ) {
            val message = bodyModelResult.exceptionOrNull()?.message ?: "Invalid request body"
            recordFailure(path, pathModelId, startTime, 400, message)
            respondError(call, HttpStatusCode.BadRequest, message)
            return
        }
        val requestedModelId = bodyModelResult.getOrNull() ?: pathModelId

        if (requestedModelId.isNullOrBlank()) {
            recordFailure(path, null, startTime, 400, "Missing model ID in request")
            respondError(call, HttpStatusCode.BadRequest, "Missing model ID in request")
            return
        }

        if (RouteResolver.isPotentialCustomModelId(config, requestedModelId)) {
            val parsedRequest = AntigravityRequestParser.parse(
                rawJson = rawBody,
                fallbackOriginalModelId = requestedModelId
            )
            if (parsedRequest.isFailure) {
                val message = parsedRequest.exceptionOrNull()?.message ?: "Invalid request"
                recordFailure(path, requestedModelId, startTime, 400, message)
                respondError(call, HttpStatusCode.BadRequest, message)
                return
            }
            val request = parsedRequest.getOrThrow().copy(
                stream = isStreamRoute(path, rawBody, parsedRequest.getOrThrow().stream)
            )
            val routeResult = RouteResolver.resolve(config, request)
            if (routeResult.isFailure) {
                val error = routeResult.exceptionOrNull()
                val status = (error as? RouteResolutionException)?.statusCode ?: 404
                val message = error?.message ?: "Unable to resolve configured model"
                recordFailure(path, requestedModelId, startTime, status, message)
                respondError(call, HttpStatusCode.fromValue(status), message)
                return
            }
            byokHandler.forwardToByok(call, path, startTime, routeResult.getOrThrow())
            return
        }

        passthroughHandler.forwardOfficial(call, path, rawBody.toByteArray(Charsets.UTF_8), requestedModelId, startTime)
    }

    private suspend fun handlePassthroughRequest(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val path = normalizeProxyPath(call.request.path())
        val rawBody = if (call.request.httpMethod == HttpMethod.Head) {
            ByteArray(0)
        } else {
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_REQUEST_BODY_BYTES) {
                val message = "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
                recordFailure(path, null, startTime, 413, message, method = call.request.httpMethod.value)
                respondError(call, HttpStatusCode.PayloadTooLarge, message)
                return
            }
            val body = readLimitedRequestBodyBytes(call).getOrElse { error ->
                val message = error.message ?: "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
                recordFailure(path, null, startTime, 413, message, method = call.request.httpMethod.value)
                respondError(call, HttpStatusCode.PayloadTooLarge, message)
                return
            }
            body
        }
        passthroughHandler.forwardOfficial(
            call = call,
            path = path,
            rawBody = rawBody,
            modelId = null,
            startTime = startTime
        )
    }

    private suspend fun respondMethodNotAllowed(call: ApplicationCall) {
        respondError(call, HttpStatusCode.MethodNotAllowed, "Method not allowed for this route", "method_not_allowed")
    }

    private suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String,
        category: String = errorCategory(status.value, message)
    ) {
        call.respondText(
            buildJsonObject {
                put("error", buildJsonObject {
                    put("code", status.value)
                    put("category", category)
                    put("message", message)
                })
            }.toString(),
            ContentType.Application.Json,
            status
        )
    }

    private fun errorCategory(statusCode: Int, message: String): String {
        return when {
            message.contains("stream", ignoreCase = true) || message.contains("SSE", ignoreCase = true) ||
                    message.contains("tool arguments", ignoreCase = true) || message.contains("流") -> "stream_interrupted"
            message.contains("unsupported", ignoreCase = true) || message.contains("不支持") -> "unsupported_feature"
            statusCode == 400 -> "invalid_request"
            statusCode == 401 || statusCode == 403 -> "authentication"
            statusCode == 404 -> "model_not_found"
            statusCode == 408 || statusCode == 504 -> "timeout"
            statusCode == 413 -> "payload_too_large"
            statusCode == 422 -> "unsupported_feature"
            statusCode == 429 -> "rate_limit"
            statusCode in 500..599 -> "upstream_server_error"
            else -> "internal"
        }
    }

    private suspend fun respondModelCatalog(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val path = call.request.path().ifBlank { "/v1beta/models" }
        val config = configStore.currentConfig
        val localCatalog = buildJsonObject { put("models", JsonArray(emptyList())) }
        val responseJson = CatalogInjector.injectCustomModels(
            CatalogInjector.applyOfficialCompressionPolicies(localCatalog, config.modelCompressionPolicies),
            config,
            includeTiered = false
        )
        ActivityRecorder.record(
            method = "GET",
            path = path,
            modelId = null,
            requestedModelId = null,
            providerName = "Studio Local Catalog",
            statusCode = 200,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = false
        )
        call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        
    }

    private suspend fun respondPureOfficialCatalog(call: ApplicationCall) {
        val cached = OfficialCatalogProbe.rawOfficialCatalogBody
        if (!cached.isNullOrBlank()) {
            call.respondText(cached, ContentType.Application.Json, HttpStatusCode.OK)
            return
        }
        respondError(call, HttpStatusCode.BadGateway, "未获取到官方原始模型目录（请先启动 Antigravity IDE 或 App）", "native_forwarding_failed")
    }

    private suspend fun readLimitedRequestBody(call: ApplicationCall): Result<String> {
        return readLimitedRequestBodyBytes(call).map { it.toString(Charsets.UTF_8) }
    }

    private suspend fun readLimitedRequestBodyBytes(call: ApplicationCall): Result<ByteArray> {
        val channel: ByteReadChannel = call.receiveChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0L
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            totalBytes += read.toLong()
            if (totalBytes > MAX_REQUEST_BODY_BYTES) {
                return Result.failure(
                    IllegalArgumentException("Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit")
                )
            }
            output.write(buffer, 0, read)
        }
        return Result.success(output.toByteArray())
    }

    private fun isOfficialCatalogFetchPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("fetchavailablemodels") ||
                lower.contains("getavailablemodels") ||
                lower.contains("listavailablemodels")
    }

    private fun isGenerationPath(path: String): Boolean {
        return path.contains("generateContent") || path.contains("streamGenerateContent")
    }

    private fun isFixedGetPath(path: String): Boolean {
        return when (path) {
            "/health", "/healthz", "/v1/models", "/v1beta/models", "/antigravity/official-catalog" -> true
            else -> false
        }
    }

    private fun extractPathModelId(path: String): String? {
        return path.substringAfter("/models/", "")
            .substringBefore(":")
            .takeIf { it.isNotBlank() }
            ?.removePrefix("models/")
    }

    private fun normalizeProxyPath(path: String): String {
        var normalized = path
        val paddingIndex = normalized.indexOf("/dummy_path_padding")
        if (paddingIndex >= 0) {
            normalized = normalized.substring(paddingIndex + "/dummy_path_padding".length)
        }
        if (normalized.startsWith("/v1internal/")) {
            val rest = normalized.removePrefix("/v1internal/")
            val slashIndex = rest.indexOf('/')
            if (slashIndex > 0) {
                val segment = rest.substring(0, slashIndex)
                if (segment.all { it == 'x' || it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    normalized = rest.substring(slashIndex)
                }
            }
        }
        return normalized.ifBlank { "/" }
    }

    private fun isStreamRoute(path: String, rawBody: String, bodyStream: Boolean): Boolean {
        return when {
            path.contains("streamGenerateContent") -> true
            path.contains("generateContent") -> false
            (path.contains("/chat/completions") || path.contains("/messages") || path.contains("/responses")) &&
                    !rawBody.contains("\"stream\"") -> false
            else -> bodyStream
        }
    }

    private fun recordFailure(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST"
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = null,
            providerName = null,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = false,
            errorMessage = message
        )
    }
}
