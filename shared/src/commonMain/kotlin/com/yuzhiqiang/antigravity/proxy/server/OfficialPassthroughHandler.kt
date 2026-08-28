package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.StreamErrorSource
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.net.URI

class OfficialPassthroughHandler(
    private val configStore: ConfigStore,
    private val actualPortProvider: () -> Int
) {
    private val catalogJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun forwardOfficial(
        call: ApplicationCall,
        path: String,
        rawBody: ByteArray,
        modelId: String?,
        startTime: Long
    ) {
        val logId = ActivityRecorder.startActivity(
            method = call.request.httpMethod.value,
            path = path,
            modelId = modelId,
            requestedModelId = null,
            clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call),
            providerName = "Official Cloud Code",
            isOfficialPassthrough = true,
            timestamp = startTime
        )
        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            ActivityRecorder.finishActivity(
                id = logId,
                statusCode = 502,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = message,
                errorSource = StreamErrorSource.STUDIO_PROXY.name
            )
            respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
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
                    lastStatus = status
                    val responseContentType = response.contentType() ?: ContentType.Application.Json
                    val responseIsStreaming = isStreaming || responseContentType.match(ContentType.Text.EventStream)

                    // 如果在首包到达前收到任何错误（4xx、5xx），直接触发重试
                    if (status >= 400 && attempt <= maxRetries) {
                        lastErrorMessage = "Official Cloud Code API error ($status)"
                        lastErrorSource = StreamErrorSource.UPSTREAM_RESPONSE
                        retryNeeded = true
                        return@execute
                    }

                    if (responseIsStreaming) {
                        val source: ByteReadChannel = response.body()
                        val firstBuffer = ByteArray(8192)
                        var firstRead = -1
                        var firstReadError: Throwable? = null
                        try {
                            firstRead = source.readAvailable(firstBuffer)
                        } catch (e: Throwable) {
                            firstReadError = e
                        }

                        // 如果在读首包时发生错误（如 stream was reset: CANCEL, TLS EOF, 连接超时）或读取异常
                        if (firstReadError != null || firstRead <= 0) {
                            lastErrorMessage = firstReadError?.message ?: "Stream closed before receiving initial data"
                            lastErrorSource = StreamErrorSource.UPSTREAM_TRANSPORT
                            lastStatus = 502
                            retryNeeded = true
                            return@execute
                        }

                        // 首包成功读取到了有效数据！此时才向客户端开启响应流
                        responseStarted = true
                        call.response.headers.append("Cache-Control", "no-cache")
                        call.response.headers.append("X-Accel-Buffering", "no")
                        copyForwardResponseHeaders(call, response)
                        val ttft = System.currentTimeMillis() - startTime
                        var firstTokenMs: Long? = ttft
                        ActivityRecorder.updateFirstToken(logId, ttft)
                        var latestUsage: NeutralUsage? = null
                        val sseBuffer = StringBuilder()
                        var streamErrorCaught: Throwable? = null

                        try {
                            call.respondBytesWriter(contentType = responseContentType, status = response.status) {
                                // 1. 先把首包数据写入下游
                                writeFully(firstBuffer, 0, firstRead)
                                flush()
                                val firstText = firstBuffer.decodeToString(0, firstRead)
                                sseBuffer.append(firstText)
                                extractUsageFromSseBuffer(sseBuffer, isFinal = false)?.let { usage ->
                                    latestUsage = usage
                                }

                                // 2. 持续读取并写入后续数据
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

                                        // 旁路流式积累 SSE 文本以抓取 usageMetadata
                                        val chunkText = buffer.decodeToString(0, read)
                                        sseBuffer.append(chunkText)
                                        extractUsageFromSseBuffer(sseBuffer, isFinal = false)?.let { usage ->
                                            latestUsage = usage
                                        }
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
                            // 流关闭后清空并解析残留缓冲区
                            extractUsageFromSseBuffer(sseBuffer, isFinal = true)?.let { usage ->
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
                                retryCount = attempt - 1
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
                                retryCount = attempt - 1
                            )
                        }
                        return@execute
                    }

                    // 非流式响应
                    val bodyBytes = withTimeout(120_000L) {
                        ProviderAdapter.readResponseBodyBytes(response)
                    }.getOrElse { error ->
                        throw IllegalStateException(error.message ?: "Failed to read official response body", error)
                    }

                    val responseBodyString = if (isTextualContentType(responseContentType)) {
                        rewriteOfficialUrls(bodyBytes.toString(Charsets.UTF_8), call)
                    } else {
                        null
                    }

                    // 从非流式 JSON 响应中提取 Token 用量
                    val nonStreamingUsage = responseBodyString?.let { text ->
                        runCatching {
                            val jsonElement = catalogJson.parseToJsonElement(text)
                            parseGeminiUsage(jsonElement)
                        }.getOrNull()
                    }

                    ActivityRecorder.finishActivity(
                        id = logId,
                        statusCode = status,
                        durationMs = System.currentTimeMillis() - startTime,
                        usage = nonStreamingUsage,
                        retryCount = attempt - 1
                    )
                    copyForwardResponseHeaders(call, response)
                    val responseBody = responseBodyString?.toByteArray(Charsets.UTF_8) ?: bodyBytes
                    call.respondBytes(responseBody, responseContentType, response.status)
                }

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

        // 所有重试耗尽且未建立流响应
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
                respondError(call, HttpStatusCode.fromValue(finalStatus), finalMessage, "native_forwarding_failed")
            }
        }
    }

    suspend fun forwardOfficialCatalog(
        call: ApplicationCall,
        path: String,
        rawBody: String,
        startTime: Long
    ) {
        val officialUrlResult = officialUrl(path, call.request.queryString())
        if (officialUrlResult.isFailure) {
            val message = officialUrlResult.exceptionOrNull()?.message ?: "Invalid official Cloud Code endpoint"
            recordFailure(
                path,
                null,
                startTime,
                502,
                message,
                errorSource = StreamErrorSource.STUDIO_PROXY
            )
            respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
            return
        }

        try {
            val officialUrl = officialUrlResult.getOrThrow()
            val response = ProviderAdapter.officialClientFor(officialUrl).preparePost(officialUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.AcceptEncoding, "identity")
                setBody(rawBody)
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
            }.execute()
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
                    errorSource = StreamErrorSource.UPSTREAM_RESPONSE
                )
                call.respondText(
                    rewriteOfficialUrls(body, call),
                    response.contentType() ?: ContentType.Application.Json,
                    response.status
                )
                return
            }
            val parsedRoot = catalogJson.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("官方目录响应不是 JSON 对象")
            val root = JsonObject(parsedRoot - "error")
            OfficialCatalogProbe.setRawOfficialCatalog(body)
            val filtered =
                CatalogInjector.removeDisabledOfficialModels(root, configStore.currentConfig.disabledOfficialModels)
            val overridden = CatalogInjector.applyOfficialCompressionPolicies(
                filtered,
                configStore.currentConfig.modelCompressionPolicies
            )
            val responseJson = CatalogInjector.injectCustomModels(overridden, configStore.currentConfig)
            val clientSource = com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector.detect(call)
            recordActivity(path, null, startTime, response.status.value, null, clientSource = clientSource)
            call.respondText(
                rewriteOfficialUrls(responseJson.toString(), call),
                response.contentType() ?: ContentType.Application.Json,
                response.status
            )
        } catch (error: Exception) {
            respondCatalogFallback(call, path, startTime, error.message ?: "官方目录获取失败")
        }
    }

    suspend fun respondCatalogFallback(
        call: ApplicationCall,
        path: String,
        startTime: Long,
        reason: String
    ) {
        val config = configStore.currentConfig
        val baseCatalog = if (path.contains("fetchAvailableModels")) {
            buildJsonObject {
                put("response", buildJsonObject { put("models", JsonObject(emptyMap())) })
            }
        } else {
            buildJsonObject { put("models", JsonArray(emptyList())) }
        }
        val fallback = CatalogInjector.injectCustomModels(
            baseCatalog,
            config
        )
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
            path,
            null,
            startTime,
            status.value,
            if (status == HttpStatusCode.OK) null else finalReason,
            errorSource = errorSource.takeIf { status != HttpStatusCode.OK },
            clientSource = clientSource
        )
        if (hasCustomModels) {
            call.respondText(
                rewriteOfficialUrls(fallback.toString(), call),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
            return
        }
        respondError(
            call,
            status,
            finalReason,
            if (status == HttpStatusCode.BadGateway) "native_forwarding_failed" else "internal"
        )
    }

    fun officialUrl(path: String, query: String): Result<String> {
        val endpoint = System.getenv("ANTIGRAVITY_CLOUD_CODE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "https://daily-cloudcode-pa.googleapis.com"
        val parsedEndpoint = runCatching { URI(endpoint) }.getOrNull()
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
            val uri = URI(endpoint)
            val host = uri.host?.lowercase()
            val localHost = host == "127.0.0.1" || host == "localhost" || host == "::1"
            localHost && (uri.port == -1 || uri.port == actualPortProvider())
        } catch (_: Exception) {
            false
        }
    }

    fun rewriteOfficialUrls(body: String, call: ApplicationCall): String {
        val scheme = call.request.headers["X-Forwarded-Proto"] ?: "http"
        val hostHeader = call.request.headers[HttpHeaders.Host]
        val proxyTarget = if (!hostHeader.isNullOrBlank()) {
            "$scheme://$hostHeader"
        } else {
            "http://127.0.0.1:" + actualPortProvider()
        }
        return body
            .replace("https://daily-cloudcode-pa.googleapis.com", proxyTarget)
            .replace("https://cloudcode-pa.googleapis.com", proxyTarget)
    }

    fun isTextualContentType(contentType: ContentType): Boolean {
        return contentType.contentType.equals("text", ignoreCase = true) ||
                contentType.contentType.equals("application", ignoreCase = true) &&
                contentType.contentSubtype.lowercase() in setOf(
            "json", "javascript", "xml", "x-www-form-urlencoded", "grpc+json"
        )
    }

    fun isHopByHopHeader(name: String): Boolean {
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

    fun copyForwardResponseHeaders(call: ApplicationCall, response: HttpResponse) {
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

    private suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String,
        category: String
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

    private fun recordFailure(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        errorSource: StreamErrorSource = StreamErrorSource.STUDIO_PROXY,
        clientSource: String? = null
    ) {
        recordActivity(path, modelId, startTime, status, message, method, errorSource = errorSource, clientSource = clientSource)
    }

    private fun recordActivity(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        firstTokenMs: Long? = null,
        errorSource: StreamErrorSource? = null,
        clientSource: String? = null
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = null,
            clientSource = clientSource,
            providerName = "Official Cloud Code",
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = true,
            errorMessage = message,
            errorSource = errorSource?.name,
            firstTokenMs = firstTokenMs
        )
    }

    private fun parseGeminiUsage(jsonElement: JsonElement): NeutralUsage? {
        val root = when (jsonElement) {
            is JsonObject -> jsonElement
            is JsonArray -> jsonElement.lastOrNull() as? JsonObject
            else -> null
        } ?: return null
        val effectiveRoot = (root["response"] as? JsonObject) ?: root
        val usage = (effectiveRoot["usageMetadata"] as? JsonObject)
            ?: (root["usageMetadata"] as? JsonObject)
            ?: return null

        fun long(vararg keys: String): Long? {
            for (key in keys) {
                val value = usage[key]?.jsonPrimitive?.longOrNull
                if (value != null) return value
            }
            return null
        }

        val prompt = long("promptTokenCount", "prompt_token_count")
        val cached = long("cachedContentTokenCount", "cached_content_token_count")
        val reasoning = long("thoughtsTokenCount", "thoughts_token_count")
        val output = long("candidatesTokenCount", "candidates_token_count")
        val validCacheBreakdown = prompt != null && (cached ?: 0L) <= prompt
        val validReasoningBreakdown = output != null && (reasoning ?: 0L) <= output
        val computedTotal = prompt?.plus((output ?: 0L) + (reasoning ?: 0L))
        val reportedTotal = long("totalTokenCount", "total_token_count")
        return NeutralUsage(
            inputTokens = prompt?.let { total -> if (validCacheBreakdown) total - (cached ?: 0L) else total },
            outputTokens = output?.let { total -> if (validReasoningBreakdown) total - (reasoning ?: 0L) else total },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            totalTokens = reportedTotal?.takeIf { computedTotal == null || it >= computedTotal } ?: computedTotal
        )
    }

    private fun extractUsageFromSseBuffer(buffer: StringBuilder, isFinal: Boolean = false): NeutralUsage? {
        var foundUsage: NeutralUsage? = null
        while (true) {
            val eventEndIndex = buffer.indexOf("\n\n")
            if (eventEndIndex >= 0) {
                val rawEvent = buffer.substring(0, eventEndIndex)
                buffer.delete(0, eventEndIndex + 2)
                processRawSseEvent(rawEvent)?.let { foundUsage = it }
            } else if (isFinal && buffer.isNotEmpty()) {
                val rawEvent = buffer.toString()
                buffer.clear()
                processRawSseEvent(rawEvent)?.let { foundUsage = it }
                break
            } else {
                break
            }
        }
        return foundUsage
    }

    private fun processRawSseEvent(rawEvent: String): NeutralUsage? {
        var eventUsage: NeutralUsage? = null
        rawEvent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("data:")) {
                val data = trimmed.removePrefix("data:").trim()
                if (data.isNotEmpty() && data != "[DONE]") {
                    val parsedUsage = runCatching {
                        val jsonElement = catalogJson.parseToJsonElement(data)
                        parseGeminiUsage(jsonElement)
                    }.getOrNull()
                    if (parsedUsage != null) {
                        eventUsage = parsedUsage
                    }
                }
            }
        }
        return eventUsage
    }
}
