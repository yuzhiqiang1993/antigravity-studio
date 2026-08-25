package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
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
                errorMessage = message
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
                    var firstTokenMs: Long? = null
                    try {
                        call.respondBytesWriter(contentType = responseContentType, status = response.status) {
                            val source: ByteReadChannel = response.body()
                            val buffer = ByteArray(8192)
                            while (!source.isClosedForRead) {
                                val read = source.readAvailable(buffer)
                                if (read <= 0) {
                                    if (read < 0) break
                                    continue
                                }
                                if (firstTokenMs == null) {
                                    val ttft = System.currentTimeMillis() - startTime
                                    firstTokenMs = ttft
                                    ActivityRecorder.updateFirstToken(logId, ttft)
                                }
                                writeFully(buffer, 0, read)
                                flush()
                            }
                        }
                        ActivityRecorder.finishActivity(
                            id = logId,
                            statusCode = status,
                            durationMs = System.currentTimeMillis() - startTime,
                            firstTokenMs = firstTokenMs
                        )
                    } catch (error: Exception) {
                        ActivityRecorder.finishActivity(
                            id = logId,
                            statusCode = 502,
                            durationMs = System.currentTimeMillis() - startTime,
                            errorMessage = error.message
                        )
                    }
                    return@execute
                }
                val bodyBytes = withTimeout(120_000L) {
                    ProviderAdapter.readResponseBodyBytes(response)
                }.getOrElse { error ->
                    throw IllegalStateException(error.message ?: "Failed to read official response body", error)
                }
                ActivityRecorder.finishActivity(
                    id = logId,
                    statusCode = status,
                    durationMs = System.currentTimeMillis() - startTime
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
            val message = "Official Cloud Code passthrough failed: " + (error.message ?: "unknown error")
            recordFailure(path, modelId, startTime, 502, message, method = call.request.httpMethod.value)
            if (!responseStarted) {
                respondError(call, HttpStatusCode.BadGateway, message, "native_forwarding_failed")
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
            recordFailure(path, null, startTime, 502, message)
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
                recordFailure(path, null, startTime, response.status.value, body)
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
            val filtered = CatalogInjector.removeDisabledOfficialModels(root, configStore.currentConfig.disabledOfficialModels)
            val overridden = CatalogInjector.applyOfficialCompressionPolicies(
                filtered,
                configStore.currentConfig.modelCompressionPolicies
            )
            val responseJson = CatalogInjector.injectCustomModels(overridden, configStore.currentConfig)
            recordActivity(path, null, startTime, response.status.value, null)
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
        recordActivity(
            path,
            null,
            startTime,
            status.value,
            if (status == HttpStatusCode.OK) null else reason
        )
        if (hasCustomModels) {
            call.respondText(
                rewriteOfficialUrls(fallback.toString(), call),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
            return
        }
        respondError(call, status, reason, if (status == HttpStatusCode.BadGateway) "native_forwarding_failed" else "internal")
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
        method: String = "POST"
    ) {
        recordActivity(path, modelId, startTime, status, message, method)
    }

    private fun recordActivity(
        path: String,
        modelId: String?,
        startTime: Long,
        status: Int,
        message: String?,
        method: String = "POST",
        firstTokenMs: Long? = null
    ) {
        ActivityRecorder.record(
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = null,
            providerName = "Official Cloud Code",
            statusCode = status,
            durationMs = System.currentTimeMillis() - startTime,
            isOfficialPassthrough = true,
            errorMessage = message,
            firstTokenMs = firstTokenMs
        )
    }
}

