package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.routing.ResolvedRoute
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolutionException
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import io.ktor.server.request.receiveText
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LocalProxyServer(
    private val configStore: ConfigStore
) {
    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val catalogJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _actualPort = MutableStateFlow(12345)
    val actualPort: StateFlow<Int> = _actualPort.asStateFlow()

    @Synchronized
    fun start(desiredPort: Int = configStore.currentConfig.proxyPort): Result<Int> {
        if (_isRunning.value) return Result.success(_actualPort.value)

        val availablePort = findAvailablePort(desiredPort, desiredPort + 20)
            ?: return Result.failure(IllegalStateException("No available port found near $desiredPort"))

        return try {
            val server = embeddedServer(CIO, host = "127.0.0.1", port = availablePort) {
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                }
                routing {
                    get("/health") {
                        call.respondText(
                            "{\"status\":\"ok\",\"port\":$availablePort}",
                            ContentType.Application.Json
                        )
                    }
                    get("/v1beta/models") {
                        respondModelCatalog(call)
                    }
                    get("/v1/models") {
                        respondModelCatalog(call)
                    }
                    get("/antigravity/official-catalog") {
                        respondPureOfficialCatalog(call)
                    }
                    post("/{...}") {
                        handleChatRequest(call)
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
        val path = call.request.path()
        val rawBody = call.receiveText()
        val config = configStore.currentConfig
        val pathModelId = extractPathModelId(path)
        val bodyModelResult = AntigravityRequestParser.extractModelId(rawBody)
        if (bodyModelResult.isFailure &&
            !bodyModelResult.exceptionOrNull()?.message.orEmpty().startsWith("Missing model ID")
        ) {
            val message = bodyModelResult.exceptionOrNull()?.message ?: "Invalid request body"
            recordFailure(path, pathModelId, null, startTime, 400, message)
            call.respond(HttpStatusCode.BadRequest, message)
            return
        }
        val requestedModelId = bodyModelResult.getOrNull() ?: pathModelId

        if (requestedModelId.isNullOrBlank()) {
            recordFailure(path, null, null, startTime, 400, "Missing model ID in request")
            call.respond(HttpStatusCode.BadRequest, "Missing model ID in request")
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
                call.respond(HttpStatusCode.BadRequest, message)
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
                call.respond(HttpStatusCode.fromValue(status), message)
                return
            }
            forwardToByok(call, path, startTime, routeResult.getOrThrow())
            return
        }

        forwardOfficial(call, path, rawBody, requestedModelId, startTime)
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
                fallbackAttempted = fallbackAttempted,
                fallbackSucceeded = fallbackSucceeded
            )
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(status))
            return
        }

        var status = 200
        var errorMessage: String? = null
        var emittedBusinessFrame = false
        try {
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

                suspend fun consumeWithoutFallback(
                    streamRoute: ResolvedRoute,
                    encoder: ResponseEncoder.GeminiStreamEncoder
                ): Boolean {
                    var failed = false
                    var completed = false
                    try {
                        AdapterFactory.getAdapter(streamRoute.provider.protocol)
                            .sendStream(streamRoute.provider, streamRoute.request)
                            .collect { chunk ->
                                when (chunk) {
                                    is NeutralStreamChunk.Error -> {
                                        failed = true
                                        status = chunk.statusCode
                                        errorMessage = chunk.message
                                    }

                                    is NeutralStreamChunk.Completed -> completed = true
                                    else -> Unit
                                }
                                writeFrames(encoder.encode(chunk))
                                encoder.failureStatusCode?.let { failureStatus ->
                                    failed = true
                                    status = failureStatus
                                    errorMessage = encoder.failureMessage
                                }
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
                    }
                    if (!failed && !completed && encoder.failureStatusCode == null) {
                        writeFrames(encoder.encode(NeutralStreamChunk.Completed()))
                        encoder.failureStatusCode?.let { failureStatus ->
                            failed = true
                            status = failureStatus
                            errorMessage = encoder.failureMessage
                        }
                    }
                    return failed
                }

                val primaryEncoder = ResponseEncoder.newStreamEncoder(cloudCode, route.request.targetUpstreamModelId)
                var primaryStopped = false
                var primaryCompleted = false
                try {
                    AdapterFactory.getAdapter(route.provider.protocol)
                        .sendStream(route.provider, route.request)
                        .collect { chunk ->
                            if (primaryStopped) return@collect
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
                                    fallbackSucceeded = !consumeWithoutFallback(fallbackRoute, fallbackEncoder)
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

                                    is NeutralStreamChunk.Completed -> primaryCompleted = true
                                    else -> Unit
                                }
                                writeFrames(primaryEncoder.encode(chunk))
                                primaryEncoder.failureStatusCode?.let { failureStatus ->
                                    status = failureStatus
                                    errorMessage = primaryEncoder.failureMessage
                                    primaryStopped = true
                                }
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
                }
                if (!primaryStopped && !primaryCompleted && primaryEncoder.failureStatusCode == null) {
                    writeFrames(primaryEncoder.encode(NeutralStreamChunk.Completed()))
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
        recordActivity(
            path,
            activeRoute.virtualModel?.id ?: activeRoute.upstreamModel.id,
            activeRoute.provider.name,
            startTime,
            status,
            errorMessage,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded
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

    private fun isRetryableFallbackError(error: NeutralStreamChunk.Error): Boolean {
        return error.statusCode == 408 || error.statusCode == 429 ||
                error.statusCode == 502 || error.statusCode == 504 ||
                error.statusCode in 500..599
    }

    private suspend fun forwardOfficial(
        call: ApplicationCall,
        path: String,
        rawBody: String,
        modelId: String,
        startTime: Long
    ) {
        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(path, modelId, "Official Cloud Code", startTime, 502, message)
            call.respond(HttpStatusCode.BadGateway, message)
            return
        }
        val officialUrl = officialUrlResult.getOrThrow()
        var responseStarted = false
        try {
            val response: HttpResponse = ProviderAdapter.sharedHttpClient.preparePost(officialUrl) {
                contentType(ContentType.Application.Json)
                call.request.headers.forEach { name, values ->
                    if (!isHopByHopHeader(name) && !name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                        values.forEach { header(name, it) }
                    }
                }
                setBody(rawBody)
            }.execute()
            val status = response.status.value
            val responseContentType = response.contentType() ?: ContentType.Application.Json
            val isStreaming = path.contains("streamGenerateContent") ||
                    responseContentType.match(ContentType.Text.EventStream)
            if (isStreaming) {
                responseStarted = true
                try {
                    call.respondBytesWriter(contentType = responseContentType, status = response.status) {
                        val source: ByteReadChannel = response.body()
                        source.copyTo(this)
                    }
                    recordActivity(path, modelId, "Official Cloud Code", startTime, status, null)
                } catch (error: Exception) {
                    recordFailure(path, modelId, "Official Cloud Code", startTime, 502, error.message)
                }
                return
            }
            recordActivity(path, modelId, "Official Cloud Code", startTime, status, null)
            call.respondText(
                response.bodyAsText(),
                responseContentType,
                response.status
            )
        } catch (error: Exception) {
            val message = "Official Cloud Code passthrough failed: ${error.message ?: "unknown error"}"
            recordFailure(path, modelId, "Official Cloud Code", startTime, 502, message)
            if (!responseStarted) {
                call.respond(HttpStatusCode.BadGateway, message)
            }
        }
    }

    private suspend fun respondModelCatalog(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val path = "/v1beta/models"
        val config = configStore.currentConfig
        val isRawRequested =
            call.request.headers["X-Antigravity-Raw-Official"]?.equals("true", ignoreCase = true) == true
                    || call.request.queryParameters["raw"]?.equals("true", ignoreCase = true) == true

        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(path, null, "Official Cloud Code", startTime, 502, message, method = "GET")
            respondCatalogError(call, message)
            return
        }

        try {
            val response = ProviderAdapter.sharedHttpClient.get(officialUrlResult.getOrThrow()) {
                call.request.headers.forEach { name, values ->
                    if (!isHopByHopHeader(name) && !name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                        values.forEach { header(name, it) }
                    }
                }
            }
            if (!response.status.isSuccess()) {
                val message = "Official Cloud Code model catalog failed with HTTP ${response.status.value}"
                recordFailure(path, null, "Official Cloud Code", startTime, 502, message, method = "GET")
                respondCatalogError(call, message)
                return
            }

            val root = try {
                catalogJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    ?: throw IllegalArgumentException("response is not a JSON object")
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Official Cloud Code model catalog returned invalid JSON: ${error.message ?: "unknown error"}",
                    error
                )
            }
            // 缓存纯净官方目录 (对齐 agy-byok forwarding.rs)
            com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.setRawOfficialCatalog(root.toString())

            // 如果客户端明确请求纯净官方目录（Raw 模式），则跳过三方模型注入
            val responseJson = if (isRawRequested) {
                root
            } else {
                val filtered = removeDisabledOfficialModels(root, config.disabledOfficialModels)
                injectCustomModels(filtered, config)
            }

            recordActivity(path, null, "Official Cloud Code", startTime, 200, null, method = "GET")
            call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        } catch (error: Exception) {
            val message = error.message ?: "Official Cloud Code model catalog failed"
            recordFailure(path, null, "Official Cloud Code", startTime, 502, message, method = "GET")
            respondCatalogError(call, message)
        }
    }

    private suspend fun respondPureOfficialCatalog(call: ApplicationCall) {
        val cached = com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.rawOfficialCatalogBody
        if (!cached.isNullOrBlank() && call.request.queryParameters["refresh"] != "true") {
            call.respondText(cached, ContentType.Application.Json, HttpStatusCode.OK)
            return
        }
        // 如果无缓存或要求刷新，走纯净上游拉取流程
        respondModelCatalog(call)
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
            val response = ProviderAdapter.sharedHttpClient.get(officialUrlResult.getOrThrow())
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
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
                put("message", message)
            })
        }
        call.respondText(body.toString(), ContentType.Application.Json, HttpStatusCode.BadGateway)
    }

    private fun injectCustomModels(
        root: JsonObject,
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig
    ): JsonObject {
        val entries = customCatalogEntries(config)
        if (entries.isEmpty()) return root
        val response = root["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(root + ("response" to appendCatalogEntries(response, entries)))
        } else {
            appendCatalogEntries(root, entries)
        }
    }

    private fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        val disabled = disabledModelIds.map(::normalizeCatalogModelId).toSet()
        val filteredRoot = filterCatalogContainer(root, disabled)
        val response = filteredRoot["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(filteredRoot + ("response" to filterCatalogContainer(response, disabled)))
        } else {
            filteredRoot
        }
    }

    private fun filterCatalogContainer(
        container: JsonObject,
        disabled: Set<String>
    ): JsonObject {
        return when (val models = container["models"]) {
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
    }

    private fun appendCatalogEntries(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        return when (val models = container["models"]) {
            is JsonArray -> {
                val existing = models.mapNotNull { catalogModelIds(it).firstOrNull() }.toSet()
                val additions = entries.filterNot { catalogModelIds(it).any(existing::contains) }
                JsonObject(container + ("models" to JsonArray(models + additions)))
            }

            is JsonObject -> {
                val updated = models.toMutableMap()
                entries.forEach { entry ->
                    val key = catalogModelIds(entry).firstOrNull() ?: return@forEach
                    updated[key] = entry
                }
                JsonObject(container + ("models" to JsonObject(updated)))
            }

            else -> JsonObject(container + ("models" to JsonArray(entries)))
        }
    }

    private fun customCatalogEntries(
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig
    ): List<JsonObject> {
        if (config.virtualModels.isEmpty()) {
            return config.upstreamModels
                .filter { upstream ->
                    upstream.enabled && config.providers.any { provider ->
                        provider.id == upstream.providerId && provider.enabled
                    }
                }
                .map { upstream ->
                    buildCatalogEntry(
                        upstream = upstream,
                        modelName = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        displayName = upstream.effectiveName,
                        hostModelId = ModelIdentity.effectiveUpstreamHostModelId(upstream),
                        catalogKey = ModelIdentity.effectiveUpstreamHostModelId(upstream)
                    )
                }
        }
        return config.virtualModels.mapNotNull { virtual ->
            if (!RouteResolver.isRoutableVirtualModel(config, virtual)) return@mapNotNull null
            val upstream = config.upstreamModels.firstOrNull {
                it.id == virtual.upstreamModelId ||
                        it.upstreamModelId == virtual.upstreamModelId ||
                        it.hostModelId == virtual.upstreamModelId
            } ?: return@mapNotNull null
            buildCatalogEntry(
                upstream = upstream,
                modelName = RouteResolver.catalogKey(virtual),
                displayName = virtual.displayName?.takeIf { it.isNotBlank() }
                    ?: virtual.name.takeIf { it.isNotBlank() }
                    ?: virtual.id,
                hostModelId = RouteResolver.effectiveHostModelId(virtual),
                catalogKey = RouteResolver.catalogKey(virtual)
            )
        }
    }

    private fun buildCatalogEntry(
        upstream: com.yuzhiqiang.antigravity.domain.model.UpstreamModel,
        modelName: String,
        displayName: String,
        hostModelId: String,
        catalogKey: String
    ): JsonObject {
        return buildJsonObject {
            put("name", "models/$modelName")
            put("displayName", displayName)
            put("hostModelId", hostModelId)
            put("catalogKey", catalogKey)
            upstream.effectiveContextWindow?.let { put("contextWindow", it) }
            put("supportsTools", upstream.capabilities.tools)
            put("supportsImages", upstream.capabilities.supportsVision)
            put("supportedGenerationMethods", buildJsonArray {
                add(JsonPrimitive("generateContent"))
                add(JsonPrimitive("streamGenerateContent"))
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
            value?.get("id")?.toString()?.trim('"')
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

    private fun recordFailure(
        path: String,
        modelId: String?,
        providerName: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false
    ) {
        recordActivity(
            path,
            modelId,
            providerName,
            startTime,
            status,
            message,
            method,
            fallbackAttempted,
            fallbackSucceeded
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
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelId = modelId,
            providerName = providerName,
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = providerName == "Official Cloud Code",
            errorMessage = message,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded
        )
    }
}
