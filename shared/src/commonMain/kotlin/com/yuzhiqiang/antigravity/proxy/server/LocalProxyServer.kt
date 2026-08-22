package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.routing.ResolvedRoute
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolutionException
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream

class LocalProxyServer(
    private val configStore: ConfigStore
) {
    private companion object {
        const val MAX_REQUEST_BODY_BYTES = 4L * 1024L * 1024L
    }

    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val catalogJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _actualPort = MutableStateFlow(12345)
    val actualPort: StateFlow<Int> = _actualPort.asStateFlow()

    // 生成请求与目录/透传控制面分离，避免长流占满全部代理并发槽位。
    private val generationSemaphore = Semaphore(256)
    private val controlPlaneSemaphore = Semaphore(64)

    @Synchronized
    fun start(desiredPort: Int = configStore.currentConfig.proxyPort): Result<Int> {
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
                    get("/health") {
                        call.respondText(
                            "{\"status\":\"ok\",\"product\":\"antigravity-studio\",\"port\":$availablePort,\"capabilities\":{\"models\":true,\"generate\":true,\"stream\":true}}",
                            ContentType.Application.Json
                        )
                    }
                    get("/healthz") {
                        call.respondText(
                            "{\"status\":\"ok\",\"product\":\"antigravity-studio\",\"port\":$availablePort}",
                            ContentType.Application.Json
                        )
                    }
                    get("/v1beta/models") {
                        controlPlaneSemaphore.withPermit { respondModelCatalog(call) }
                    }
                    get("/v1/models") {
                        controlPlaneSemaphore.withPermit { respondModelCatalog(call) }
                    }
                    get("/antigravity/official-catalog") {
                        controlPlaneSemaphore.withPermit { respondPureOfficialCatalog(call) }
                    }
                    post("/health") { respondMethodNotAllowed(call) }
                    post("/healthz") { respondMethodNotAllowed(call) }
                    post("/v1/models") { respondMethodNotAllowed(call) }
                    post("/v1beta/models") { respondMethodNotAllowed(call) }
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

                            // byok 对非生成 POST/RPC 走原样官方透传（例如 countTokens），
                            // 不能强制要求请求体里存在 model 字段。
                            else -> controlPlaneSemaphore.withPermit { handlePassthroughRequest(call) }
                        }
                    }
                    get("/{...}") {
                        val normalizedPath = normalizeProxyPath(call.request.path())
                        when (normalizedPath) {
                            "/health" -> call.respondText(
                                "{\"status\":\"ok\",\"product\":\"antigravity-studio\",\"port\":$availablePort,\"capabilities\":{\"models\":true,\"generate\":true,\"stream\":true}}",
                                ContentType.Application.Json
                            )

                            "/healthz" -> call.respondText(
                                "{\"status\":\"ok\",\"product\":\"antigravity-studio\",\"port\":$availablePort}",
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
                    options("/{...}") {
                        call.respond(HttpStatusCode.OK)
                    }
                    // 透传面允许 PUT/DELETE/PATCH/HEAD 等官方 RPC；只有固定路由和
                    // 生成/目录路由需要方法约束。该兜底路由放在方法专用路由之后，
                    // 不会抢走上面的 POST/GET/OPTIONS 处理。
                    route("/{...}") {
                        handle {
                            val normalizedPath = normalizeProxyPath(call.request.path())
                            when {
                                isFixedGetPath(normalizedPath) ||
                                        isOfficialCatalogFetchPath(normalizedPath) ||
                                        isGenerationPath(normalizedPath) -> respondMethodNotAllowed(call)

                                else -> controlPlaneSemaphore.withPermit {
                                    handlePassthroughRequest(call)
                                }
                            }
                        }
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
                // 端口被占用时继续探测下一个候选端口。
            }
        }
        return null
    }

    @Synchronized
    fun stop() {
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
            recordFailure(path, null, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        val rawBody = readLimitedRequestBody(call).getOrElse { error ->
            val message = error.message ?: "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
            recordFailure(path, null, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        if (rawBody.toByteArray(Charsets.UTF_8).size.toLong() > MAX_REQUEST_BODY_BYTES) {
            val message = "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
            recordFailure(path, null, null, startTime, 413, message)
            respondError(call, HttpStatusCode.PayloadTooLarge, message)
            return
        }
        val config = configStore.currentConfig

        // 语言服务通过该 RPC 获取官方目录；它不携带模型 ID，必须在模型路由前处理。
        if (isOfficialCatalogFetchPath(path)) {
            forwardOfficialCatalog(call, path, rawBody, startTime)
            return
        }

        val pathModelId = extractPathModelId(path)
        val bodyModelResult = AntigravityRequestParser.extractModelId(rawBody)
        if (bodyModelResult.isFailure &&
            !bodyModelResult.exceptionOrNull()?.message.orEmpty().startsWith("Missing model ID")
        ) {
            val message = bodyModelResult.exceptionOrNull()?.message ?: "Invalid request body"
            recordFailure(path, pathModelId, null, startTime, 400, message)
            respondError(call, HttpStatusCode.BadRequest, message)
            return
        }
        val requestedModelId = bodyModelResult.getOrNull() ?: pathModelId

        if (requestedModelId.isNullOrBlank()) {
            recordFailure(path, null, null, startTime, 400, "Missing model ID in request")
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
                recordFailure(path, requestedModelId, null, startTime, 400, message)
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
                recordFailure(path, requestedModelId, null, startTime, status, message)
                respondError(call, HttpStatusCode.fromValue(status), message)
                return
            }
            forwardToByok(call, path, startTime, routeResult.getOrThrow())
            return
        }

        forwardOfficial(call, path, rawBody.toByteArray(Charsets.UTF_8), requestedModelId, startTime)
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
                recordFailure(path, null, "Official Cloud Code", startTime, 413, message, method = call.request.httpMethod.value)
                respondError(call, HttpStatusCode.PayloadTooLarge, message)
                return
            }
            val body = readLimitedRequestBodyBytes(call).getOrElse { error ->
                val message = error.message ?: "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
                recordFailure(path, null, "Official Cloud Code", startTime, 413, message, method = call.request.httpMethod.value)
                respondError(call, HttpStatusCode.PayloadTooLarge, message)
                return
            }
            body
        }
        forwardOfficial(
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

    private suspend fun forwardToByok(
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
            recordActivity(
                path,
                activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
                activeRoute.provider.name,
                startTime,
                status,
                errorChunk?.message ?: encoderError?.message,
                requestedModelId = route.requestedModelId,
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

        // 先启动并读取第一帧。这样上游在握手阶段失败时仍能返回真实 HTTP
        // 错误码，而不是像普通 SSE 一样先发出 200 再把错误塞进事件流。
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
                        recordActivity(
                            path,
                            fallbackRoute.virtualModel?.id ?: fallbackRoute.upstreamModel.id,
                            fallbackRoute.provider.name,
                            startTime,
                            status,
                            errorMessage,
                            requestedModelId = route.requestedModelId,
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
                    recordActivity(
                        path,
                        route.virtualModel?.id ?: route.upstreamModel.id,
                        route.provider.name,
                        startTime,
                        status,
                        errorMessage,
                        requestedModelId = route.requestedModelId,
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
                recordActivity(
                    path,
                    route.virtualModel?.id ?: route.upstreamModel.id,
                    route.provider.name,
                    startTime,
                    status,
                    errorMessage,
                    requestedModelId = route.requestedModelId
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
                            // 只有真正写出的 SSE 帧才算已向客户端暴露业务响应。
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
        recordActivity(
            path,
            activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
            activeRoute.provider.name,
            startTime,
            status,
            errorMessage,
            requestedModelId = route.requestedModelId,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            usage = latestUsage
        )
    }

    private suspend fun collectProviderChunks(route: ResolvedRoute): List<NeutralStreamChunk> {
        return try {
            AdapterFactory.getAdapter(route.provider.protocol)
                .sendStream(route.provider, route.request)
                .toList()
        } catch (error: Exception) {
            listOf(NeutralStreamChunk.Error(error.message ?: "Provider request failed", 502))
        }
    }

    private suspend fun openProviderStream(
        route: ResolvedRoute
    ): ReceiveChannel<NeutralStreamChunk> {
        return AdapterFactory.getAdapter(route.provider.protocol)
            .sendStream(route.provider, route.request)
            .produceIn(CoroutineScope(currentCoroutineContext()))
    }

    private fun isRetryableFallbackError(error: NeutralStreamChunk.Error): Boolean {
        return error.statusCode == 408 || error.statusCode == 429 ||
                error.statusCode == 502 || error.statusCode == 504 ||
                error.statusCode in 500..599
    }

    /** 分块请求也必须受与 Content-Length 请求相同的上限约束。 */
    private suspend fun readLimitedRequestBody(call: ApplicationCall): Result<String> {
        return readLimitedRequestBodyBytes(call).map { bytes -> bytes.toString(Charsets.UTF_8) }
    }

    private suspend fun readLimitedRequestBodyBytes(call: ApplicationCall): Result<ByteArray> {
        return try {
            val channel = call.receiveChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > MAX_REQUEST_BODY_BYTES) {
                    channel.cancel(
                        IllegalStateException(
                            "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
                        )
                    )
                    return Result.failure(
                        IllegalStateException(
                            "Request body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit"
                        )
                    )
                }
                output.write(buffer, 0, read)
            }
            Result.success(output.toByteArray())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun forwardOfficial(
        call: ApplicationCall,
        path: String,
        rawBody: ByteArray,
        modelId: String?,
        startTime: Long
    ) {
        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(
                path,
                modelId,
                "Official Cloud Code",
                startTime,
                502,
                message,
                method = call.request.httpMethod.value
            )
            respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
            return
        }
        val officialUrl = officialUrlResult.getOrThrow()
        var responseStarted = false
        try {
            ProviderAdapter.officialClientFor(officialUrl).prepareRequest(officialUrl) {
                method = call.request.httpMethod
                header(HttpHeaders.AcceptEncoding, "identity")
                if (rawBody.isNotEmpty() && method != HttpMethod.Head) {
                    contentType(
                        call.request.headers[HttpHeaders.ContentType]
                            ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                            ?: ContentType.Application.Json
                    )
                    setBody(rawBody)
                } else if (method == HttpMethod.Post || method == HttpMethod.Put || method == HttpMethod.Patch) {
                    contentType(ContentType.Application.Json)
                    setBody(ByteArray(0))
                }
                call.request.headers.forEach { name, values ->
                    if (!isHopByHopHeader(name) &&
                        !name.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                        !name.equals(HttpHeaders.AcceptEncoding, ignoreCase = true) &&
                        !name.equals("x-antigravity-studio-token", ignoreCase = true) &&
                        !name.equals("x-antigravity-studio-internal-probe", ignoreCase = true) &&
                        !name.equals("x-agy-byok-token", ignoreCase = true) &&
                        !name.equals("x-agy-byok-internal-probe", ignoreCase = true)
                    ) {
                        values.forEach { header(name, it) }
                    }
                }
            }.execute { response ->
                val status = response.status.value
                val responseContentType = response.contentType() ?: ContentType.Application.Json
                val isStreaming = path.contains("streamGenerateContent") ||
                        responseContentType.match(ContentType.Text.EventStream)
                if (isStreaming) {
                    responseStarted = true
                    call.response.headers.append("Cache-Control", "no-cache")
                    call.response.headers.append("X-Accel-Buffering", "no")
                    copyForwardResponseHeaders(call, response)
                    try {
                        call.respondBytesWriter(contentType = responseContentType, status = response.status) {
                            val source: ByteReadChannel = response.body()
                            source.copyTo(this)
                        }
                        recordActivity(
                            path,
                            modelId,
                            "Official Cloud Code",
                            startTime,
                            status,
                            null,
                            method = call.request.httpMethod.value
                        )
                    } catch (error: Exception) {
                        recordFailure(path, modelId, "Official Cloud Code", startTime, 502, error.message)
                    }
                    return@execute
                }
                val bodyBytes = withTimeout(120_000L) {
                    ProviderAdapter.readLimitedResponseBytes(response)
                }.getOrElse { error ->
                    throw IllegalStateException(error.message ?: "Official response body exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit", error)
                }
                recordActivity(
                    path,
                    modelId,
                    "Official Cloud Code",
                    startTime,
                    status,
                    null,
                    method = call.request.httpMethod.value
                )
                copyForwardResponseHeaders(call, response)
                val responseBody = if (isTextualContentType(responseContentType)) {
                    rewriteOfficialUrls(bodyBytes.toString(Charsets.UTF_8), call).toByteArray(Charsets.UTF_8)
                } else {
                    bodyBytes
                }
                call.respondBytes(responseBody, responseContentType, response.status)
            }
        } catch (error: Exception) {
            val message = "Official Cloud Code passthrough failed: ${error.message ?: "unknown error"}"
            recordFailure(
                path,
                modelId,
                "Official Cloud Code",
                startTime,
                502,
                message,
                method = call.request.httpMethod.value
            )
            if (!responseStarted) {
                respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
            }
        }
    }

    /** 处理官方语言服务的目录 RPC，并复用 byok 的“原始缓存→过滤→覆盖→注入”顺序。 */
    private suspend fun forwardOfficialCatalog(
        call: ApplicationCall,
        path: String,
        rawBody: String,
        startTime: Long
    ) {
        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(path, null, "Official Cloud Code", startTime, 502, message)
            respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
            return
        }

        try {
            val officialUrl = officialUrlResult.getOrThrow()
            val response = ProviderAdapter.officialClientFor(officialUrl).preparePost(officialUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.AcceptEncoding, "identity")
                call.request.headers.forEach { name, values ->
                    if (!isHopByHopHeader(name) &&
                        !name.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                        !name.equals(HttpHeaders.AcceptEncoding, ignoreCase = true) &&
                        !name.equals("x-antigravity-studio-token", ignoreCase = true) &&
                        !name.equals("x-antigravity-studio-internal-probe", ignoreCase = true) &&
                        !name.equals("x-agy-byok-token", ignoreCase = true) &&
                        !name.equals("x-agy-byok-internal-probe", ignoreCase = true)
                    ) {
                        values.forEach { header(name, it) }
                    }
                }
                if (rawBody.isNotEmpty()) {
                    setBody(rawBody)
                } else {
                    setBody("{}")
                }
            }.let { statement -> withTimeout(30_000L) { statement.execute() } }
            val body = withTimeout(30_000L) {
                ProviderAdapter.readLimitedResponseText(response)
            }.getOrElse { error ->
                throw IllegalStateException(error.message ?: "Official catalog exceeds ${MAX_REQUEST_BODY_BYTES / (1024 * 1024)} MiB limit", error)
            }
            if (!response.status.isSuccess()) {
                respondCatalogFallback(call, path, startTime, "官方目录返回 HTTP ${response.status.value}")
                return
            }
            val parsedRoot = catalogJson.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("官方目录响应不是 JSON 对象")
            val root = JsonObject(parsedRoot - "error")
            // 缓存未经过滤/覆盖的原始正文，供 Studio 调试视图与纯官方目录端点使用。
            OfficialCatalogProbe.setRawOfficialCatalog(body)
            val filtered = removeDisabledOfficialModels(root, configStore.currentConfig.disabledOfficialModels)
            val overridden = applyOfficialCompressionPolicies(
                filtered,
                configStore.currentConfig.modelCompressionPolicies
            )
            val responseJson = injectCustomModels(overridden, configStore.currentConfig)
            recordActivity(path, null, "Official Cloud Code", startTime, response.status.value, null)
            call.respondText(
                rewriteOfficialUrls(responseJson.toString(), call),
                response.contentType() ?: ContentType.Application.Json,
                response.status
            )
        } catch (error: Exception) {
            respondCatalogFallback(call, path, startTime, error.message ?: "官方目录获取失败")
        }
    }

    private suspend fun respondCatalogFallback(
        call: ApplicationCall,
        path: String,
        startTime: Long,
        reason: String
    ) {
        val config = configStore.currentConfig
        val baseCatalog = if (isOfficialCatalogFetchPath(path)) {
            buildJsonObject {
                put("response", buildJsonObject { put("models", JsonObject(emptyMap())) })
            }
        } else {
            buildJsonObject { put("models", JsonArray(emptyList())) }
        }
        val fallback = injectCustomModels(
            baseCatalog,
            config
        )
        val hasCustomModels = customCatalogEntries(config).isNotEmpty()
        val status = if (hasCustomModels) HttpStatusCode.OK else HttpStatusCode.BadGateway
        recordActivity(
            path,
            null,
            "Official Cloud Code",
            startTime,
            status.value,
            if (status == HttpStatusCode.OK) null else reason
        )
        call.respondText(
            if (status == HttpStatusCode.OK) fallback.toString() else buildJsonObject {
                put("error", buildJsonObject {
                    put("code", status.value)
                    put("category", if (status == HttpStatusCode.BadGateway) "native_forwarding_failed" else "internal")
                    put("message", reason)
                })
            }.toString(),
            ContentType.Application.Json,
            status
        )
    }

    private suspend fun respondModelCatalog(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val path = call.request.path().ifBlank { "/v1beta/models" }
        val config = configStore.currentConfig
        // byok 的 /v1/models 与 /v1beta/models 是本地模型列表，不依赖官方目录。
        val localCatalog = buildJsonObject { put("models", JsonArray(emptyList())) }
        val responseJson = injectCustomModels(
            applyOfficialCompressionPolicies(localCatalog, config.modelCompressionPolicies),
            config,
            includeTiered = false
        )
        recordActivity(path, null, "Studio Local Catalog", startTime, 200, null, method = "GET")
        call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    private suspend fun respondPureOfficialCatalog(call: ApplicationCall) {
        val cached = com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.rawOfficialCatalogBody
        if (!cached.isNullOrBlank() && call.request.queryParameters["refresh"] != "true") {
            call.respondText(cached, ContentType.Application.Json, HttpStatusCode.OK)
            return
        }
        val result = fetchPureOfficialCatalog()
        result.fold(
            onSuccess = { body -> call.respondText(body, ContentType.Application.Json, HttpStatusCode.OK) },
            onFailure = { error -> respondCatalogFallback(call, call.request.path(), System.currentTimeMillis(), error.message ?: "官方目录获取失败") }
        )
    }

    suspend fun fetchPureOfficialCatalog(): Result<String> {
        val cached = com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.rawOfficialCatalogBody
        if (!cached.isNullOrBlank()) {
            return Result.success(cached)
        }
        val officialUrlResult = officialUrl("/v1beta/models", "")
        if (officialUrlResult.isFailure) {
            return Result.failure(
                officialUrlResult.exceptionOrNull() ?: IllegalStateException("Invalid official endpoint")
            )
        }
        return try {
            val response = withTimeout(4_000L) {
                ProviderAdapter.officialClientFor(officialUrlResult.getOrThrow())
                    .get(officialUrlResult.getOrThrow())
            }
            if (response.status.isSuccess()) {
                val body = withTimeout(4_000L) {
                    ProviderAdapter.readLimitedResponseText(response)
                }.getOrElse { error ->
                    return Result.failure(error)
                }
                com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.setRawOfficialCatalog(body)
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("Official Cloud Code endpoint returned HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun respondCatalogError(call: ApplicationCall, message: String) {
        val body = buildJsonObject {
            put("error", buildJsonObject {
                put("code", HttpStatusCode.BadGateway.value)
                put("category", "native_forwarding_failed")
                put("message", message)
            })
        }
        call.respondText(body.toString(), ContentType.Application.Json, HttpStatusCode.BadGateway)
    }

    /** 将官方目录中的既有 Checkpointer payload 与用户覆盖策略合并。 */
    private fun applyOfficialCompressionPolicies(
        root: JsonObject,
        policies: Map<String, com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy>
    ): JsonObject {
        if (policies.isEmpty()) return root
        val expandedPolicies = policies.toMutableMap()
        officialModelAliases(root).forEach { (deprecated, replacement) ->
            policies[deprecated]?.let { expandedPolicies[replacement] = it }
            policies[replacement]?.let { expandedPolicies[deprecated] = it }
        }
        val checkpointWorkers = checkpointWorkerIds(root)
        val checkpointWorkerLimits = checkpointWorkerLimits(root)
        val defaultCheckpointWorker = checkpointWorkers
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

        fun applyContainer(container: JsonObject): JsonObject {
            val models = container["models"]
            val updatedModels = when (models) {
                is JsonObject -> JsonObject(models.mapValues { (key, value) ->
                    val policy = findPolicy(expandedPolicies, key, value)
                    if (policy == null) value else applyPolicyToEntry(
                        value,
                        policy,
                        checkpointWorkers,
                        defaultCheckpointWorker,
                        checkpointWorkerLimits
                    )
                })

                is JsonArray -> JsonArray(models.map { value ->
                    val objectValue = value as? JsonObject ?: return@map value
                    val policy = findPolicy(expandedPolicies, null, objectValue)
                    if (policy == null) value else applyPolicyToEntry(
                        value,
                        policy,
                        checkpointWorkers,
                        defaultCheckpointWorker,
                        checkpointWorkerLimits
                    )
                })

                else -> models
            }
            return if (updatedModels == null) container else JsonObject(container + ("models" to updatedModels))
        }

        val direct = applyContainer(root)
        val response = direct["response"] as? JsonObject ?: return direct
        return JsonObject(direct + ("response" to applyContainer(response)))
    }

    private fun findPolicy(
        policies: Map<String, com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy>,
        key: String?,
        value: JsonElement
    ): com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy? {
        val objectValue = value as? JsonObject
        val candidates = listOfNotNull(
            key,
            objectValue?.get("id")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("name")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("model")?.jsonPrimitive?.contentOrNull,
            objectValue?.get("catalogKey")?.jsonPrimitive?.contentOrNull
        ).map(::normalizeCatalogModelId)
        return candidates.firstNotNullOfOrNull { candidate ->
            policies.entries.firstOrNull { (modelId, _) -> normalizeCatalogModelId(modelId) == candidate }?.value
        }
    }

    private fun applyPolicyToEntry(
        value: JsonElement,
        policy: com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null,
        checkpointWorkerLimits: Map<String, Long> = emptyMap()
    ): JsonElement {
        if (!policy.enabled) return value
        val entry = value as? JsonObject ?: return value
        val effectivePolicy = if (
            defaultCheckpointWorker != null &&
                checkpointWorkers.isNotEmpty() &&
                policy.checkpointModel !in checkpointWorkers
        ) {
            policy.copy(checkpointModel = defaultCheckpointWorker)
        } else {
            policy
        }
        val capacity = listOf("maxTokens", "inputTokenLimit", "contextWindow")
            .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
            .filter { it > 0L }
            .minOrNull()
        val outputLimit = listOf("maxOutputTokens", "outputTokenLimit")
            .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
            .filter { it > 0L }
            .minOrNull()
        val declaredOutputLimit = outputLimit ?: effectivePolicy.maxOutputTokens
        val boundedOutputLimit = checkpointWorkerLimits[effectivePolicy.checkpointModel]
            ?.let(declaredOutputLimit::coerceAtMost)
            ?: declaredOutputLimit
        val resolved = effectivePolicy.resolveEffective(capacity, boundedOutputLimit) ?: return value
        val existingPayload = entry["modelExperiments"]
            ?.jsonObject
            ?.get("experiments")
            ?.jsonObject
            ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")
            ?.jsonObject
            ?.get("stringValue")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { raw -> runCatching { catalogJson.parseToJsonElement(raw).jsonObject }.getOrNull() }
            ?.toMutableMap()
            ?: return value

        val updatedPayload = JsonObject(existingPayload).toMutableMap().apply {
            put("enabled", JsonPrimitive(resolved.enabled))
            put("checkpoint_model", JsonPrimitive(resolved.checkpointModel))
            put("strategy", JsonPrimitive(resolved.strategy))
            put("use_last_planner_model", JsonPrimitive(resolved.useLastPlannerModel))
            put("token_threshold", JsonPrimitive(resolved.tokenThreshold.toString()))
            put("max_token_limit", JsonPrimitive(resolved.maxTokenLimit.toString()))
            put("max_output_tokens", JsonPrimitive(resolved.maxOutputTokens.toString()))
        }
        val payloadText = catalogJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(updatedPayload)
        )
        val experiment = buildJsonObject {
            put("stringValue", payloadText)
        }
        val modelExperiments = buildJsonObject {
            put("experiments", buildJsonObject {
                put("CASCADE_USE_EXPERIMENT_CHECKPOINTER", experiment)
            })
        }
        return JsonObject(entry + ("modelExperiments" to modelExperiments))
    }

    private fun checkpointWorkerIds(root: JsonObject): List<String> {
        val workers = mutableListOf<String>()
        fun collect(container: JsonObject) {
            when (val models = container["models"]) {
                is JsonObject -> models.values.forEach { collectEntry(it, workers) }
                is JsonArray -> models.forEach { collectEntry(it, workers) }
                else -> Unit
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return workers
    }

    /** 计算官方目录中每个真实 Checkpointer worker 的最小输出上限。 */
    private fun checkpointWorkerLimits(root: JsonObject): Map<String, Long> {
        val referenced = mutableMapOf<String, Long>()
        val direct = mutableMapOf<String, Long>()

        fun record(catalogKey: String?, value: JsonElement) {
            val entry = value as? JsonObject ?: return
            val raw = entry["modelExperiments"]?.jsonObject
                ?.get("experiments")?.jsonObject
                ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")?.jsonObject
                ?.get("stringValue")?.jsonPrimitive?.contentOrNull
                ?: return
            val worker = runCatching {
                catalogJson.parseToJsonElement(raw).jsonObject["checkpoint_model"]
                    ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: return
            val output = listOf("maxOutputTokens", "outputTokenLimit")
                .mapNotNull { field -> entry[field]?.jsonPrimitive?.longOrNull }
                .filter { it > 0L }
                .minOrNull() ?: return
            referenced[worker] = minOf(referenced[worker] ?: Long.MAX_VALUE, output)
            val isDirect = catalogKey == worker ||
                    entry["id"]?.jsonPrimitive?.contentOrNull == worker ||
                    entry["model"]?.jsonPrimitive?.contentOrNull == worker
            if (isDirect) direct[worker] = minOf(direct[worker] ?: Long.MAX_VALUE, output)
        }

        fun collect(container: JsonObject) {
            when (val models = container["models"]) {
                is JsonObject -> models.forEach { (key, value) -> record(key, value) }
                is JsonArray -> models.forEach { value -> record(null, value) }
                else -> Unit
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return referenced.mapValues { (worker, referencedLimit) -> direct[worker] ?: referencedLimit }
    }

    private fun collectEntry(value: JsonElement, workers: MutableList<String>) {
        val raw = (value as? JsonObject)
            ?.get("modelExperiments")
            ?.jsonObject
            ?.get("experiments")
            ?.jsonObject
            ?.get("CASCADE_USE_EXPERIMENT_CHECKPOINTER")
            ?.jsonObject
            ?.get("stringValue")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return
        runCatching {
            catalogJson.parseToJsonElement(raw).jsonObject["checkpoint_model"]
                ?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let(workers::add)
        }
    }

    private fun checkpointPayload(
        policy: com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
    ): JsonObject {
        return buildJsonObject {
            put("enabled", policy.enabled)
            put("checkpoint_model", policy.checkpointModel)
            put("strategy", policy.strategy)
            put("max_overhead_ratio", policy.maxOverheadRatio)
            put("moving_window_size", policy.movingWindowSize)
            put("use_last_planner_model", policy.useLastPlannerModel)
            put("is_sync", policy.isSync)
            put("max_user_requests", policy.maxUserRequests)
            put("include_last_user_message", policy.includeLastUserMessage)
            put("include_conversation_log", policy.includeConversationLog)
            put("include_running_task_snapshots", policy.includeRunningTaskSnapshots)
            put("include_subagent_snapshots", policy.includeSubagentSnapshots)
            put("include_artifact_snapshots", policy.includeArtifactSnapshots)
            put("retry_config", buildJsonObject {
                put("max_retries", policy.retryConfig.maxRetries)
                put("initial_sleep_duration_ms", policy.retryConfig.initialSleepDurationMs)
                put("exponential_multiplier", policy.retryConfig.exponentialMultiplier)
                put("include_error_feedback", policy.retryConfig.includeErrorFeedback)
            })
            put("token_threshold", policy.tokenThreshold.toString())
            put("max_token_limit", policy.maxTokenLimit.toString())
            put("max_output_tokens", policy.maxOutputTokens.toString())
        }
    }

    private fun isOfficialCatalogFetchPath(path: String): Boolean {
        return path.contains("fetchAvailableModels", ignoreCase = true) ||
                path.contains("GetAvailableModels", ignoreCase = true)
    }

    private fun isGenerationPath(path: String): Boolean {
        return path.contains("generateContent", ignoreCase = true)
    }

    private fun isFixedGetPath(path: String): Boolean {
        return path == "/health" || path == "/healthz" ||
                path == "/v1/models" || path == "/v1beta/models" ||
                path == "/antigravity/official-catalog"
    }

    /** 只在官方响应正文中回写 Cloud Code 地址，避免把代理地址泄露到宿主外部。 */
    private fun rewriteOfficialUrls(body: String, call: ApplicationCall): String {
        val host = call.request.local.serverHost
        val port = call.request.local.serverPort
        val proxyTarget = "http://$host:$port"
        return body
            .replace("https://daily-cloudcode-pa.googleapis.com", proxyTarget)
            .replace("https://cloudcode-pa.googleapis.com", proxyTarget)
            .replace("https://daily-cloudaicompanion-pa.googleapis.com", proxyTarget)
            .replace("https://cloudaicompanion-pa.googleapis.com", proxyTarget)
            .replace("https://daily-cloudcode-pa.sandbox.googleapis.com", proxyTarget)
            .replace("https://cloudcode-pa.sandbox.googleapis.com", proxyTarget)
            .replace("https://generativelanguage.googleapis.com", proxyTarget)
    }

    private fun isTextualContentType(contentType: ContentType): Boolean {
        return contentType.contentType.equals("text", ignoreCase = true) ||
                contentType.contentType.equals("application", ignoreCase = true) &&
                contentType.contentSubtype.lowercase() in setOf(
                    "json", "javascript", "xml", "x-www-form-urlencoded", "grpc+json"
                )
    }

    private fun injectCustomModels(
        root: JsonObject,
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig,
        includeTiered: Boolean = true
    ): JsonObject {
        val checkpointWorkers = checkpointWorkerIds(root)
        val defaultCheckpointWorker = checkpointWorkers
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
        val entries = customCatalogEntries(
            config,
            includeTiered,
            checkpointWorkers,
            defaultCheckpointWorker
        )
        if (entries.isEmpty()) return root
        val response = root["response"] as? JsonObject
        return if (response?.get("models") != null) {
            val updatedResponse = appendCatalogEntries(response, entries)
            val withRoles = injectRoleReferences(updatedResponse, entries)
            JsonObject(root + ("response" to injectTieredReferences(withRoles, entries)))
        } else {
            injectTieredReferences(injectRoleReferences(appendCatalogEntries(root, entries), entries), entries)
        }
    }

    /** 注册 byok 约定的 tieredModelIds.custom，供宿主把推理档位聚合成母条目。 */
    private fun injectTieredReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        if (container["models"] !is JsonObject) return container
        val tieredIds = entries.mapNotNull { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.endsWith("-tiered") }
        }.distinct()
        if (tieredIds.isEmpty()) return container
        val current = container["tieredModelIds"] as? JsonObject ?: JsonObject(emptyMap())
        val existing = (current["custom"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        tieredIds.forEach { id ->
            if (existing.none { it.jsonPrimitive.contentOrNull == id }) existing += JsonPrimitive(id)
        }
        return JsonObject(container + ("tieredModelIds" to JsonObject(current + ("custom" to JsonArray(existing)))))
    }

    private fun injectRoleReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        val result = container.toMutableMap()
        val roleEntries = entries.filterNot { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
        }
        val agentIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
        val imageIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("image_generation", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }

        val existingSorts = result["agentModelSorts"] as? JsonArray
        if (existingSorts != null && agentIds.isNotEmpty()) {
            val sorts = existingSorts.toMutableList()
            val nonAgentIds = roleEntries.filterNot { entry ->
                (entry["roles"] as? JsonArray)?.any {
                    it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
                } == true
            }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
                .toSet()
            // byok 会把自定义 Agent 模型放入每个官方排序组，同时清理同一批
            // 不属于 Agent 的自定义条目，避免宿主排序表把角色模型混入 Agent 选择器。
            sorts.indices.forEach { sortIndex ->
                val sortObject = sorts[sortIndex] as? JsonObject ?: return@forEach
                val groups = sortObject["groups"] as? JsonArray ?: return@forEach
                sorts[sortIndex] = JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                    val groupObject = group as? JsonObject ?: return@map group
                    val currentIds = (groupObject["modelIds"] as? JsonArray)?.toMutableList() ?: mutableListOf()
                    currentIds.removeAll { it.jsonPrimitive.contentOrNull in nonAgentIds }
                    agentIds.forEach { id ->
                        if (currentIds.none { it.jsonPrimitive.contentOrNull == id }) currentIds += JsonPrimitive(id)
                    }
                    JsonObject(groupObject + ("modelIds" to JsonArray(currentIds)))
                })))
            }
            val byokIndex = sorts.indexOfFirst {
                (it as? JsonObject)?.get("displayName")?.jsonPrimitive?.contentOrNull == "BYOK"
            }
            val targetIndex = if (byokIndex >= 0) byokIndex else {
                sorts += buildJsonObject { put("displayName", "BYOK"); put("groups", JsonArray(emptyList())) }
                sorts.lastIndex
            }
            val target = sorts[targetIndex] as? JsonObject ?: JsonObject(emptyMap())
            val groups = mutableListOf<JsonElement>().apply {
                addAll(
                    (target["groups"] as? JsonArray)?.toList()
                        ?: listOf(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
                )
                if (isEmpty()) add(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
            }
            val firstGroup = groups.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
            val modelIds = mutableListOf<JsonElement>().apply {
                addAll((firstGroup["modelIds"] as? JsonArray)?.toList().orEmpty())
            }
            agentIds.forEach { id ->
                if (modelIds.none { it.jsonPrimitive.contentOrNull == id }) modelIds += JsonPrimitive(id)
            }
            groups[0] = JsonObject(firstGroup + ("modelIds" to JsonArray(modelIds)))
            sorts[targetIndex] = JsonObject(target + ("groups" to JsonArray(groups)))
            result["agentModelSorts"] = JsonArray(sorts)
        }

        val existingImageIds = result["imageGenerationModelIds"] as? JsonArray
        if (imageIds.isNotEmpty()) {
            val merged = existingImageIds?.toMutableList() ?: mutableListOf()
            imageIds.forEach { id ->
                if (merged.none { it.jsonPrimitive.contentOrNull == id }) merged += JsonPrimitive(id)
            }
            result["imageGenerationModelIds"] = JsonArray(merged)
        }
        return JsonObject(result)
    }

    private fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        val disabled = disabledModelIds.map(::normalizeCatalogModelId).toMutableSet()
        officialModelAliases(root).forEach { (deprecated, replacement) ->
            if (normalizeCatalogModelId(deprecated) in disabled) disabled += normalizeCatalogModelId(replacement)
            if (normalizeCatalogModelId(replacement) in disabled) disabled += normalizeCatalogModelId(deprecated)
        }
        val filteredRoot = filterCatalogContainer(root, disabled)
        val response = filteredRoot["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(filteredRoot + ("response" to filterCatalogContainer(response, disabled)))
        } else {
            filteredRoot
        }
    }

    private fun officialModelAliases(root: JsonObject): Map<String, String> {
        val aliases = linkedMapOf<String, String>()
        fun collect(container: JsonObject) {
            val deprecated = container["deprecatedModelIds"] as? JsonObject ?: return
            deprecated.forEach { (oldId, value) ->
                val newId = (value as? JsonObject)?.get("newModelId")?.jsonPrimitive?.contentOrNull
                if (!newId.isNullOrBlank()) aliases[oldId] = newId
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return aliases
    }

    private fun filterCatalogContainer(
        container: JsonObject,
        disabled: Set<String>
    ): JsonObject {
        val filteredContainer = when (val models = container["models"]) {
            is JsonArray -> JsonObject(
                container + ("models" to JsonArray(models.filterNot { isDisabledCatalogModel(it, null, disabled) }))
            )

            is JsonObject -> {
                val filtered = models.filterNot { (key, value) ->
                    isDisabledCatalogModel(value, key, disabled)
                }
                JsonObject(container + ("models" to JsonObject(filtered)))
            }

            else -> container
        }

        fun filterIdArray(value: JsonElement?): JsonElement? {
            val array = value as? JsonArray ?: return value
            return JsonArray(array.filterNot { item ->
                normalizeCatalogModelId(item.jsonPrimitive.contentOrNull.orEmpty()) in disabled
            })
        }
        val updated = filteredContainer.toMutableMap()
        if (filteredContainer["agentModelSorts"] != null) {
            updated["agentModelSorts"] = filterSortGroups(filteredContainer["agentModelSorts"], disabled)
        }
        if (filteredContainer["imageGenerationModelIds"] != null) {
            updated["imageGenerationModelIds"] = filterIdArray(filteredContainer["imageGenerationModelIds"])
                ?: JsonArray(emptyList())
        }
        return JsonObject(updated)
    }

    private fun filterSortGroups(value: JsonElement?, disabled: Set<String>): JsonElement {
        val sorts = value as? JsonArray ?: return value ?: JsonArray(emptyList())
        return JsonArray(sorts.map { sort ->
            val sortObject = sort as? JsonObject ?: return@map sort
            val groups = sortObject["groups"] as? JsonArray ?: return@map sort
            JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                val groupObject = group as? JsonObject ?: return@map group
                val ids = groupObject["modelIds"] as? JsonArray ?: return@map group
                JsonObject(groupObject + ("modelIds" to JsonArray(ids.filterNot { id ->
                    normalizeCatalogModelId(id.jsonPrimitive.contentOrNull.orEmpty()) in disabled
                })))
            })))
        })
    }

    private fun appendCatalogEntries(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        return when (val models = container["models"]) {
            is JsonArray -> {
                val arrayEntries = entries.filterNot { entry ->
                    entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
                }
                val existing = models.mapNotNull { catalogModelIds(it).firstOrNull() }.toSet()
                val additions = arrayEntries.filterNot { catalogModelIds(it).any(existing::contains) }
                JsonObject(container + ("models" to JsonArray(models + additions)))
            }

            is JsonObject -> {
                val updated = models.toMutableMap()
                entries.forEach { entry ->
                    val key = entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                        ?: entry["id"]?.jsonPrimitive?.contentOrNull
                        ?: catalogModelIds(entry).firstOrNull()?.removePrefix("models/")
                        ?: return@forEach
                    // Cloud Code 官方目录是 ID -> descriptor 映射；byok 不在
                    // descriptor 内重复写入 id/name，避免宿主把它误当成另一种目录形态。
                    updated[key] = JsonObject(entry - setOf("id", "name"))
                }
                JsonObject(container + ("models" to JsonObject(updated)))
            }

            else -> JsonObject(container + ("models" to JsonArray(entries)))
        }
    }

    private fun customCatalogEntries(
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig,
        includeTiered: Boolean = true,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null
    ): List<JsonObject> {
        val entries = if (config.virtualModels.isEmpty()) {
            config.upstreamModels
                .mapNotNull { upstream ->
                    if (!upstream.enabled) return@mapNotNull null
                   val provider = config.providers.firstOrNull { item ->
                       item.id == upstream.providerId && item.enabled
                   } ?: return@mapNotNull null
                    val displayName = ModelIdentity.configuredModelDisplayName(
                        modelName = upstream.effectiveName,
                        reasoningLevel = null,
                        providerName = provider.name,
                        supportsReasoning = upstream.capabilities.reasoning.supportsReasoning
                    )
                   buildCatalogEntry(
                       upstream = upstream,
                       provider = provider,
                       modelName = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        displayName = displayName,
                       hostModelId = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        catalogKey = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        reasoningLevel = null,
                        entryId = upstream.id,
                        policy = healCheckpointPolicy(
                            upstream.compressionPolicy ?: config.modelCompressionPolicies[upstream.id],
                            checkpointWorkers,
                            defaultCheckpointWorker
                        )
                    )
                }
        } else {
            config.virtualModels.mapNotNull { virtual ->
                if (!RouteResolver.isRoutableVirtualModel(config, virtual)) return@mapNotNull null
                val upstream = config.upstreamModels.firstOrNull {
                    it.id == virtual.upstreamModelId ||
                            it.upstreamModelId == virtual.upstreamModelId ||
                            it.hostModelId == virtual.upstreamModelId
                } ?: return@mapNotNull null
               val provider = config.providers.firstOrNull { item -> item.id == upstream.providerId && item.enabled }
                   ?: return@mapNotNull null
                val rawDisplayName = virtual.displayName?.takeIf { it.isNotBlank() }
                    ?: virtual.name.takeIf { it.isNotBlank() }
                    ?: upstream.displayName?.takeIf { it.isNotBlank() }
                    ?: upstream.name.takeIf { it.isNotBlank() }
                    ?: virtual.id
                val displayName = ModelIdentity.configuredModelDisplayName(
                    modelName = rawDisplayName,
                    reasoningLevel = virtual.defaultReasoningLevel,
                    providerName = provider.name,
                    supportsReasoning = upstream.capabilities.reasoning.supportsReasoning
                )
               buildCatalogEntry(
                   upstream = upstream,
                   provider = provider,
                   modelName = RouteResolver.catalogKey(virtual),
                    displayName = displayName,
                   hostModelId = RouteResolver.effectiveHostModelId(virtual),
                    catalogKey = RouteResolver.catalogKey(virtual),
                    reasoningLevel = virtual.defaultReasoningLevel,
                    entryId = virtual.id,
                    policy = healCheckpointPolicy(
                        upstream.compressionPolicy
                            ?: config.modelCompressionPolicies[virtual.id]
                            ?: config.modelCompressionPolicies[upstream.id],
                        checkpointWorkers,
                        defaultCheckpointWorker
                    )
                )
           }
       }
        return if (includeTiered) entries + buildTieredCatalogEntries(config, entries, checkpointWorkers, defaultCheckpointWorker) else entries
   }

   private fun healCheckpointPolicy(
        policy: com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy?,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy? {
        return if (
            policy != null &&
                defaultCheckpointWorker != null &&
                checkpointWorkers.isNotEmpty() &&
                policy.checkpointModel !in checkpointWorkers
        ) {
            policy.copy(checkpointModel = defaultCheckpointWorker)
        } else {
            policy
        }
    }

   /** 为多个 reasoning variant 生成宿主可识别的 tiered 母条目。 */
    private fun buildTieredCatalogEntries(
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig,
        entries: List<JsonObject>,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): List<JsonObject> {
        val virtualsWithUpstream = config.virtualModels
            .filter { RouteResolver.isRoutableVirtualModel(config, it) }
            .mapNotNull { virtual ->
                val upstream = config.upstreamModels.firstOrNull {
                    (it.id == virtual.upstreamModelId || it.upstreamModelId == virtual.upstreamModelId || it.hostModelId == virtual.upstreamModelId) && it.enabled
                } ?: return@mapNotNull null
                if (!upstream.capabilities.reasoning.supportsReasoning) return@mapNotNull null
                val provider = config.providers.firstOrNull { it.id == upstream.providerId && it.enabled }
                    ?: return@mapNotNull null
                Triple(virtual, upstream, provider)
            }
        if (virtualsWithUpstream.isEmpty()) return emptyList()

        val groupMap = virtualsWithUpstream.groupBy { (virtual, upstream, _) ->
            upstream.id to ModelIdentity.catalogFamilyBase(virtual)
        }

        val tieredEntries = mutableListOf<JsonObject>()
        for ((groupKey, groupModels) in groupMap) {
            val (_, familyBase) = groupKey
            val (firstVm, upstream, provider) = groupModels.first()
            val preferredVm = ModelIdentity.REASONING_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
                groupModels.map { it.first }.firstOrNull { it.defaultReasoningLevel == level }
            } ?: firstVm

            val tieredKey = "$familyBase-tiered"
            val rawName = firstVm.displayName?.takeIf { it.isNotBlank() }
                ?: firstVm.name.takeIf { it.isNotBlank() }
                ?: upstream.displayName?.takeIf { it.isNotBlank() }
                ?: upstream.name
            val baseDisplayName = ModelIdentity.stripDisplayLevelSuffix(
                ModelIdentity.configuredModelDisplayName(
                    modelName = rawName,
                    reasoningLevel = null,
                    providerName = provider.name,
                    supportsReasoning = true
                )
            )
            val tieredHostModelId = ModelIdentity.effectiveHostModelId(preferredVm)
            val entry = buildCatalogEntry(
                upstream = upstream,
                provider = provider,
                modelName = tieredKey,
                displayName = baseDisplayName,
                hostModelId = tieredHostModelId,
                catalogKey = tieredKey,
                reasoningLevel = null,
                entryId = tieredKey,
                policy = healCheckpointPolicy(
                    upstream.compressionPolicy
                        ?: config.modelCompressionPolicies[firstVm.id]
                        ?: config.modelCompressionPolicies[upstream.id],
                    checkpointWorkers,
                    defaultCheckpointWorker
                )
            )
            val updatedEntry = JsonObject(entry + mapOf(
                "thinkingBudget" to JsonPrimitive(-1)
            ))
            tieredEntries += updatedEntry
        }
        return tieredEntries
    }

   private fun buildCatalogEntry(
        upstream: com.yuzhiqiang.antigravity.domain.model.UpstreamModel,
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        modelName: String,
        displayName: String,
        hostModelId: String,
        catalogKey: String,
        entryId: String,
        policy: com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy?,
        reasoningLevel: com.yuzhiqiang.antigravity.domain.model.ReasoningLevel?
    ): JsonObject {
        // 与 byok descriptor 一致：未声明上游限制时仍向宿主提供稳定的经验默认值，
        // 避免宿主把缺失字段解释为零或不可用模型。
        val defaultContextWindow = 128_000L
        val defaultInputTokenLimit = 128_000L
        val defaultOutputTokenLimit = 65_536L
        val capabilities = upstream.capabilities
        val contextWindow = upstream.tokenLimits.contextWindow ?: upstream.contextLength ?: defaultContextWindow
        val inputLimit = upstream.tokenLimits.inputTokenLimit ?: contextWindow ?: defaultInputTokenLimit
        val outputLimit = upstream.tokenLimits.outputTokenLimit ?: upstream.maxOutputTokens ?: defaultOutputTokenLimit
        // 自定义模型策略由用户按 UI 规则配置，保持 byok 的“不再二次钳制”语义；
        // 官方覆盖策略则在 applyPolicyToEntry 中按目录容量收敛。
        val resolvedPolicy = policy?.takeIf { it.enabled }
        val entry = buildJsonObject {
            put("id", entryId)
            put("name", "models/$hostModelId")
            put("displayName", displayName)
            put("description", "Custom BYOK Model (Provider: ${provider.name})")
            put("hostModelId", hostModelId)
            put("catalogKey", catalogKey)
            put("model", hostModelId)
            put("planModel", hostModelId)
            put("requestedModel", hostModelId)
            put("apiProvider", "API_PROVIDER_GOOGLE_GEMINI")
            put("modelProvider", modelProvider(provider.protocol))
            put("recommended", false)
            put("contextWindow", contextWindow)
            put("inputTokenLimit", inputLimit)
            put("maxTokens", inputLimit)
            put("outputTokenLimit", outputLimit)
            put("maxOutputTokens", outputLimit)
            put("supportsImages", ModelModality.IMAGE in capabilities.inputModalities)
            put("supportsAudio", ModelModality.AUDIO in capabilities.inputModalities)
            put("supportsVideo", ModelModality.VIDEO in capabilities.inputModalities)
            put("supportsTools", capabilities.tools)
            put("supportsThinking", capabilities.reasoning.supportsReasoning)
            put("roles", buildJsonArray {
                capabilities.roles.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("inputModalities", buildJsonArray {
                capabilities.inputModalities.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("outputModalities", buildJsonArray {
                capabilities.outputModalities.forEach { add(JsonPrimitive(it.name.lowercase())) }
            })
            put("supportedMimeTypes", buildJsonObject {
                capabilities.inputMimeTypes.forEach { mime -> put(mime, true) }
            })
            if (capabilities.reasoning.supportsReasoning) {
                put("thinkingBudget", effectiveThinkingBudget(upstream, reasoningLevel, provider.protocol))
            }
            capabilities.reasoning.minThinkingBudget?.let { put("minThinkingBudget", it) }
            put("supportedGenerationMethods", buildJsonArray {
                add(JsonPrimitive("generateContent"))
                add(JsonPrimitive("streamGenerateContent"))
            })
            provider.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
                put("tagTitle", name)
                put("tagDescription", "BYOK")
            }
            resolvedPolicy?.let { resolved ->
                put("modelExperiments", checkpointExperiments(resolved))
            }
        }
        return entry
    }

    private fun effectiveThinkingBudget(
        upstream: com.yuzhiqiang.antigravity.domain.model.UpstreamModel,
        reasoningLevel: com.yuzhiqiang.antigravity.domain.model.ReasoningLevel?,
        protocol: com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
    ): Int {
        val reasoning = upstream.capabilities.reasoning
        val mapping = reasoningLevel?.let { level ->
                ReasoningMappingSupport.parse(reasoning.levels)[level]
                ?: ReasoningMappingSupport.defaultMapping(
                    protocol,
                    level
                )
        }
        return when (mapping?.kind?.lowercase()) {
            "budget_tokens" -> ReasoningMappingSupport.mappingValueAsInt(mapping)
                ?: reasoning.thinkingBudget
                ?: -1
            "disabled" -> 0
            else -> reasoning.thinkingBudget ?: -1
        }
    }

    private fun modelProvider(protocol: com.yuzhiqiang.antigravity.domain.model.ProviderProtocol): String {
        return when (protocol) {
            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.ANTHROPIC_MESSAGES -> "MODEL_PROVIDER_ANTHROPIC"
            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.GEMINI_GENERATE_CONTENT -> "MODEL_PROVIDER_GOOGLE"
            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES -> "MODEL_PROVIDER_OPENAI"
        }
    }

    private fun checkpointExperiments(policy: com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy): JsonObject {
        val payload = buildJsonObject {
            put("enabled", policy.enabled)
            put("checkpoint_model", policy.checkpointModel)
            put("strategy", policy.strategy)
            put("max_overhead_ratio", policy.maxOverheadRatio)
            put("moving_window_size", policy.movingWindowSize)
            put("use_last_planner_model", policy.useLastPlannerModel)
            put("is_sync", policy.isSync)
            put("max_user_requests", policy.maxUserRequests)
            put("include_last_user_message", policy.includeLastUserMessage)
            put("include_conversation_log", policy.includeConversationLog)
            put("include_running_task_snapshots", policy.includeRunningTaskSnapshots)
            put("include_subagent_snapshots", policy.includeSubagentSnapshots)
            put("include_artifact_snapshots", policy.includeArtifactSnapshots)
            put("retry_config", buildJsonObject {
                put("max_retries", policy.retryConfig.maxRetries)
                put("initial_sleep_duration_ms", policy.retryConfig.initialSleepDurationMs)
                put("exponential_multiplier", policy.retryConfig.exponentialMultiplier)
                put("include_error_feedback", policy.retryConfig.includeErrorFeedback)
            })
            put("token_threshold", policy.tokenThreshold.toString())
            put("max_token_limit", policy.maxTokenLimit.toString())
            put("max_output_tokens", policy.maxOutputTokens.toString())
        }
        return buildJsonObject {
            put("experiments", buildJsonObject {
                put("CASCADE_USE_EXPERIMENT_CHECKPOINTER", buildJsonObject {
                    put("stringValue", catalogJson.encodeToString(JsonElement.serializer(), payload))
                })
            })
        }
    }

    private fun isDisabledCatalogModel(
        element: JsonElement,
        key: String?,
        disabled: Set<String>
    ): Boolean {
        return catalogModelIds(element, key).any { normalizeCatalogModelId(it) in disabled }
    }

    private fun catalogModelIds(element: JsonElement, key: String? = null): List<String> {
        val value = element as? JsonObject
        return listOfNotNull(
            key,
            value?.get("name")?.toString()?.trim('"'),
            value?.get("model")?.toString()?.trim('"'),
            value?.get("id")?.toString()?.trim('"'),
            value?.get("catalogKey")?.toString()?.trim('"')
        )
    }

    private fun normalizeCatalogModelId(value: String): String {
        return value.trim().removePrefix("models/")
    }

    private fun extractPathModelId(path: String): String? {
        return path.substringAfter("/models/", "")
            .substringBefore(":")
            .takeIf { it.isNotBlank() }
            ?.removePrefix("models/")
    }

    /** 对齐 byok，去除宿主请求中的 dummy padding 和可变 v1internal 前缀。 */
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

    private fun officialUrl(path: String, query: String): Result<String> {
        val endpoint = System.getenv("ANTIGRAVITY_CLOUD_CODE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "https://daily-cloudcode-pa.googleapis.com"
        val parsedEndpoint = runCatching { java.net.URI(endpoint) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Official Cloud Code endpoint is invalid"))
        if (parsedEndpoint.scheme !in setOf("http", "https") || parsedEndpoint.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Official Cloud Code endpoint must be an absolute HTTP(S) URL"))
        }
        if (!parsedEndpoint.query.isNullOrBlank() ||
            !parsedEndpoint.fragment.isNullOrBlank() ||
            !parsedEndpoint.userInfo.isNullOrBlank()
        ) {
            return Result.failure(IllegalArgumentException("Official Cloud Code endpoint cannot contain embedded credentials, query or fragment"))
        }
        val isLoopback = parsedEndpoint.host.equals("127.0.0.1", ignoreCase = true) ||
                parsedEndpoint.host.equals("localhost", ignoreCase = true) ||
                parsedEndpoint.host == "::1"
        if (parsedEndpoint.scheme.equals("http", ignoreCase = true) && !isLoopback) {
            return Result.failure(IllegalArgumentException("非回环官方 Cloud Code 地址必须使用 HTTPS"))
        }
        if (isLocalProxyEndpoint(endpoint)) {
            return Result.failure(IllegalStateException("Official Cloud Code endpoint points to the local proxy; refusing recursive passthrough"))
        }
        val suffix = if (query.isBlank()) path else "$path?$query"
        return Result.success(endpoint.trimEnd('/') + suffix)
    }

    private fun isLocalProxyEndpoint(endpoint: String): Boolean {
        return try {
            val uri = java.net.URI(endpoint)
            val host = uri.host?.lowercase()
            val localHost = host == "127.0.0.1" || host == "localhost" || host == "::1"
            localHost && (uri.port == -1 || uri.port == actualPort.value)
        } catch (_: Exception) {
            false
        }
    }

    private fun isHopByHopHeader(name: String): Boolean {
        if (name.startsWith(":")) return true
        return name.equals(HttpHeaders.Host, ignoreCase = true) ||
                name.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
                name.equals(HttpHeaders.Connection, ignoreCase = true) ||
                name.equals("Keep-Alive", ignoreCase = true) ||
                name.equals("Proxy-Authenticate", ignoreCase = true) ||
                name.equals("Proxy-Authorization", ignoreCase = true) ||
                name.equals("TE", ignoreCase = true) ||
                name.equals("Trailer", ignoreCase = true) ||
                name.equals("Transfer-Encoding", ignoreCase = true) ||
                name.equals("Upgrade", ignoreCase = true)
    }

    private fun copyForwardResponseHeaders(call: ApplicationCall, response: HttpResponse) {
        response.headers.forEach { name, values ->
            if (isHopByHopHeader(name) ||
                name.equals(HttpHeaders.ContentType, ignoreCase = true) ||
                name.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
                name.startsWith("Access-Control-", ignoreCase = true)
            ) {
                return@forEach
            }
            values.forEach { value -> call.response.headers.append(name, value) }
        }
    }

    private fun recordFailure(
        path: String,
        modelId: String?,
        providerName: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        requestedModelId: String? = null,
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false,
        usage: NeutralUsage? = null
    ) {
        recordActivity(
            path,
            modelId,
            providerName,
            startTime,
            status,
            message,
            method,
            requestedModelId,
            fallbackAttempted,
            fallbackSucceeded,
            usage
        )
    }

    private fun recordActivity(
        path: String,
        modelId: String?,
        providerName: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        requestedModelId: String? = null,
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false,
        usage: NeutralUsage? = null
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = requestedModelId,
            providerName = providerName,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = providerName == "Official Cloud Code",
            errorMessage = message,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            usage = usage
        )
    }
}
