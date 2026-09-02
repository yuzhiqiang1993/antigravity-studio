package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.resolveActivityIdentity
import com.yuzhiqiang.antigravity.network.PlatformNetworkConfig
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolutionException
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class LocalProxyServer(
    private val configStore: ConfigStore,
    private val accessTokenStore: ProxyAccessTokenStore = ProxyAccessTokenStore()
) {

    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val lifecycleMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _actualPort = MutableStateFlow(8321)
    val actualPort: StateFlow<Int> = _actualPort.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    private val generationSemaphore = Semaphore(4)
    private val controlPlaneSemaphore = Semaphore(8)

    private val passthroughHandler = OfficialPassthroughHandler(
        configStore = configStore,
        actualPortProvider = { _actualPort.value }
    )
    private val byokHandler = ByokForwardHandler(configStore)

    val secureEndpoint: String
        get() = ProxyEndpoint.secure(_actualPort.value, accessToken ?: accessTokenStore.loadOrCreate().getOrThrow())

    suspend fun start(desiredPort: Int = configStore.currentConfig.proxyPort): Result<Int> = lifecycleMutex.withLock {
        if (_isRunning.value) return Result.success(_actualPort.value)

        if (desiredPort !in 1024..65535) {
            return Result.failure(IllegalArgumentException("代理端口必须位于 1024 - 65535 之间"))
        }

        val token = accessTokenStore.loadOrCreate().getOrElse { error -> return Result.failure(error) }

        // 在开始监听前完成有界预热，避免首个生成请求等待或错过 macOS 系统代理。
        withContext(Dispatchers.IO) {
            PlatformNetworkConfig.awaitSystemProxyPrewarm()
        }

        val availablePort = findAvailablePort(desiredPort, (desiredPort + 20).coerceAtMost(65535))
            ?: return Result.failure(IllegalStateException("No available port found near $desiredPort"))

        return try {
            val server = embeddedServer(CIO, host = "127.0.0.1", port = availablePort) {
                routing {
                    post("/{...}") {
                        val normalizedPath = this@LocalProxyServer.authorizedPath(call, token) ?: return@post
                        val requestStartTime = System.currentTimeMillis()
                        when {
                            isFixedGetPath(normalizedPath) -> respondMethodNotAllowed(call)
                            isOfficialCatalogFetchPath(normalizedPath) -> {
                                controlPlaneSemaphore.withPermit {
                                    handleChatRequest(
                                        call = call,
                                        startTime = requestStartTime,
                                        queueWaitMs = System.currentTimeMillis() - requestStartTime
                                    )
                                }
                            }

                            isGenerationPath(normalizedPath) -> {
                                val generationStartTime = System.currentTimeMillis()
                                val queueWaitMs = generationStartTime - requestStartTime
                                generationSemaphore.withPermit {
                                    handleChatRequest(
                                        call = call,
                                        startTime = generationStartTime,
                                        queueWaitMs = queueWaitMs
                                    )
                                }
                            }

                            else -> controlPlaneSemaphore.withPermit {
                                handlePassthroughRequest(
                                    call = call,
                                    startTime = requestStartTime,
                                    queueWaitMs = System.currentTimeMillis() - requestStartTime
                                )
                            }
                        }
                    }
                    get("/{...}") {
                        val requestPath = call.request.path()
                        if (requestPath == "/health" || requestPath == "/healthz") {
                            if (requestPath == "/health") {
                                call.respondText(
                                    """{"status":"ok","product":"antigravity-studio","port":$availablePort,"capabilities":{"models":true,"generate":true,"stream":true}}""",
                                    ContentType.Application.Json
                                )
                            } else {
                                call.respondText(
                                    """{"status":"ok","product":"antigravity-studio","port":$availablePort}""",
                                    ContentType.Application.Json
                                )
                            }
                            return@get
                        }
                        val normalizedPath = this@LocalProxyServer.authorizedPath(call, token) ?: return@get
                        when (normalizedPath) {
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
                        val path = this@LocalProxyServer.authorizedPath(call, token) ?: return@delete
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    put("/{...}") {
                        val path = this@LocalProxyServer.authorizedPath(call, token) ?: return@put
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    patch("/{...}") {
                        val path = this@LocalProxyServer.authorizedPath(call, token) ?: return@patch
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                    head("/{...}") {
                        val path = this@LocalProxyServer.authorizedPath(call, token) ?: return@head
                        controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                    }
                }
            }
            server.start(wait = false)
            persistActualPort(server, availablePort)
            serverEngine = server
            accessToken = token
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

    private suspend fun handleChatRequest(
        call: ApplicationCall,
        startTime: Long,
        queueWaitMs: Long
    ) {
        val path = normalizeProxyPath(call.request.path())
        val clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call)
        val config = configStore.currentConfig
        val isDebug = config.isDebugMode
        val reqHeaders = if (isDebug) extractRequestHeaders(call) else null

        val rawBody = readRequestBody(call).getOrElse { error ->
            val isPayloadTooLarge = error is PayloadTooLargeException || error.cause is PayloadTooLargeException
            val status = if (isPayloadTooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest
            val message = error.message ?: "Failed to read request body"
            recordFailure(
                path = path,
                modelId = null,
                startTime = startTime,
                status = status.value,
                message = message,
                queueWaitMs = queueWaitMs,
                clientSource = clientSource,
                requestHeaders = reqHeaders,
                responseBody = if (isDebug) message else null
            )
            respondError(call, status, message)
            return
        }

        if (isOfficialCatalogFetchPath(path)) {
            passthroughHandler.forwardOfficialCatalog(call, path, rawBody, startTime, queueWaitMs)
            return
        }

        val pathModelId = extractPathModelId(path)
        val requestRoot = AntigravityRequestParser.parseObject(rawBody).getOrElse { error ->
            val message = error.message ?: "Invalid request body"
            recordFailure(
                path,
                pathModelId,
                startTime,
                400,
                message,
                queueWaitMs = queueWaitMs,
                clientSource = clientSource,
                requestHeaders = reqHeaders,
                requestBody = if (isDebug) rawBody else null,
                responseBody = if (isDebug) message else null
            )
            respondError(call, HttpStatusCode.BadRequest, message)
            return
        }
        val bodyModelResult = AntigravityRequestParser.extractModelId(requestRoot)
        val requestedModelId = bodyModelResult.getOrNull() ?: pathModelId

        if (requestedModelId.isNullOrBlank()) {
            recordFailure(
                path,
                null,
                startTime,
                400,
                "Missing model ID in request",
                queueWaitMs = queueWaitMs,
                clientSource = clientSource,
                requestHeaders = reqHeaders,
                requestBody = if (isDebug) rawBody else null,
                responseBody = if (isDebug) "Missing model ID in request" else null
            )
            respondError(call, HttpStatusCode.BadRequest, "Missing model ID in request")
            return
        }

        if (RouteResolver.isPotentialCustomModelId(config, requestedModelId)) {
            val parsedRequest = AntigravityRequestParser.parse(
                root = requestRoot,
                fallbackOriginalModelId = requestedModelId
            )
            if (parsedRequest.isFailure) {
                val message = parsedRequest.exceptionOrNull()?.message ?: "Invalid request"
                recordFailure(
                    path,
                    requestedModelId,
                    startTime,
                    400,
                    message,
                    queueWaitMs = queueWaitMs,
                    clientSource = clientSource,
                    requestHeaders = reqHeaders,
                    requestBody = if (isDebug) rawBody else null,
                    responseBody = if (isDebug) message else null
                )
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
                recordFailure(
                    path,
                    requestedModelId,
                    startTime,
                    status,
                    message,
                    queueWaitMs = queueWaitMs,
                    clientSource = clientSource,
                    requestHeaders = reqHeaders,
                    requestBody = if (isDebug) rawBody else null,
                    responseBody = if (isDebug) message else null
                )
                respondError(call, HttpStatusCode.fromValue(status), message)
                return
            }
            byokHandler.forwardToByok(
                call,
                path,
                startTime,
                routeResult.getOrThrow(),
                rawBody,
                queueWaitMs
            )
            return
        }

        passthroughHandler.forwardOfficial(
            call,
            path,
            rawBody.toByteArray(Charsets.UTF_8),
            requestedModelId,
            startTime,
            queueWaitMs
        )
    }

    private suspend fun handlePassthroughRequest(
        call: ApplicationCall,
        startTime: Long = System.currentTimeMillis(),
        queueWaitMs: Long? = null
    ) {
        val path = normalizeProxyPath(call.request.path())
        val clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call)
        val isDebug = configStore.currentConfig.isDebugMode
        val reqHeaders = if (isDebug) extractRequestHeaders(call) else null

        val rawBody = if (call.request.httpMethod == HttpMethod.Head) {
            ByteArray(0)
        } else {
            val body = readRequestBodyBytes(call).getOrElse { error ->
                val isPayloadTooLarge = error is PayloadTooLargeException || error.cause is PayloadTooLargeException
                val status = if (isPayloadTooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest
                val message = error.message ?: "Failed to read request body"
                recordFailure(
                    path = path,
                    modelId = null,
                    startTime = startTime,
                    status = status.value,
                    message = message,
                    method = call.request.httpMethod.value,
                    queueWaitMs = queueWaitMs,
                    clientSource = clientSource,
                    requestHeaders = reqHeaders,
                    responseBody = if (isDebug) message else null
                )
                respondError(call, status, message)
                return
            }
            body
        }

        passthroughHandler.forwardOfficial(
            call = call,
            path = path,
            rawBody = rawBody,
            modelId = null,
            startTime = startTime,
            queueWaitMs = queueWaitMs
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
                    message.contains(
                        "tool arguments",
                        ignoreCase = true
                    ) || message.contains("流") -> "stream_interrupted"

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
            CatalogInjector.applyOfficialCompressionPolicies(localCatalog, config.compressionPolicyAssignments),
            config,
            includeTiered = false
        )
        if (config.collectNonChatLogs) {
            val isDebug = config.isDebugMode
            ActivityRecorder.record(
                method = "GET",
                path = path,
                modelIdentity = null,
                clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call),
                providerName = "Studio Local Catalog",
                statusCode = 200,
                durationMs = System.currentTimeMillis() - startTime,
                isOfficialPassthrough = false,
                timestamp = startTime,
                requestHeaders = if (isDebug) extractRequestHeaders(call) else null,
                responseBody = if (isDebug) responseJson.toString() else null
            )
        }
        call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)

    }

    private suspend fun respondPureOfficialCatalog(call: ApplicationCall) {
        val cached = OfficialCatalogProbe.rawOfficialCatalogBody
        if (!cached.isNullOrBlank()) {
            call.respondText(cached, ContentType.Application.Json, HttpStatusCode.OK)
            return
        }
        respondError(
            call,
            HttpStatusCode.BadGateway,
            "未获取到官方原始模型目录（请先启动 Antigravity IDE 或 App）",
            "native_forwarding_failed"
        )
    }

    private suspend fun authorizedPath(call: ApplicationCall, expectedToken: String): String? {
        val rawPath = call.request.path()
        if (rawPath == "/health" || rawPath == "/healthz") {
            return rawPath
        }

        val candidates = buildList {
            if (rawPath.startsWith("/v1internal/")) {
                val segment = rawPath.removePrefix("/v1internal/").substringBefore('/')
                if (segment.isNotBlank()) add(segment)
            }
            val authHeader = call.request.headers[HttpHeaders.Authorization]
            if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
                val token = authHeader.substring(7).trim()
                if (token.isNotBlank()) add(token)
            }
            val customToken = call.request.headers["X-Antigravity-Proxy-Token"]?.trim()
                ?: call.request.headers["X-Antigravity-Studio-Token"]?.trim()
                ?: call.request.headers["X-Antigravity-Token"]?.trim()
            if (!customToken.isNullOrBlank()) {
                add(customToken)
            }
        }

        val expectedBytes = expectedToken.toByteArray(Charsets.UTF_8)
        val isAuthorized = candidates.any { candidate ->
            java.security.MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), expectedBytes)
        }

        if (isAuthorized) {
            return normalizeProxyPath(rawPath)
        }

        respondError(
            call = call,
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized: missing or invalid proxy access token",
            category = "authentication"
        )
        return null
    }

    private suspend fun readRequestBody(call: ApplicationCall): Result<String> {
        return readRequestBodyBytes(call).map { it.toString(Charsets.UTF_8) }
    }

    private suspend fun readRequestBodyBytes(call: ApplicationCall): Result<ByteArray> {
        return runCatching {
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_REQUEST_BODY_BYTES) {
                throw PayloadTooLargeException("Request body exceeds 32 MiB limit (Content-Length: $contentLength)")
            }
            val channel = call.receiveChannel()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalBytes = 0L
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                totalBytes += read
                if (totalBytes > MAX_REQUEST_BODY_BYTES) {
                    throw PayloadTooLargeException("Request body exceeds 32 MiB limit")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun isOfficialCatalogFetchPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("fetchavailablemodels") ||
                lower.contains("getavailablemodels") ||
                lower.contains("listavailablemodels")
    }

    private fun isGenerationPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("generatecontent") ||
                lower.contains("streamgeneratecontent") ||
                lower.contains("/chat/completions") ||
                lower.contains("/messages") ||
                lower.contains("/responses") ||
                lower.contains("/completions")
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
            val afterPadding = normalized.substring(paddingIndex + "/dummy_path_padding".length)
            normalized = afterPadding.ifBlank { "/" }
        }
        if (normalized.startsWith("/v1internal/")) {
            val rest = normalized.removePrefix("/v1internal/")
            val slashIndex = rest.indexOf('/')
            if (slashIndex > 0) {
                val segment = rest.substring(0, slashIndex)
                if (segment.all { it == 'x' || it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    normalized = rest.substring(slashIndex)
                }
            } else {
                normalized = "/"
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
        method: String = "POST",
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
            providerName = null,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = false,
            timestamp = startTime,
            errorMessage = message,
            queueWaitMs = queueWaitMs,
            requestHeaders = requestHeaders,
            requestBody = requestBody,
            responseHeaders = responseHeaders,
            responseBody = responseBody
        )
    }

    companion object {
        const val MAX_REQUEST_BODY_BYTES = 32 * 1024 * 1024L // 32 MiB
    }
}

class PayloadTooLargeException(message: String) : IllegalArgumentException(message)

