package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface ProviderAdapter {
    /** 模型目录解析结果；rawBody 只用于调试查看，不参与业务配置持久化。 */
    data class ModelCatalogResult(
        val models: List<DiscoveredModelInfo> = emptyList(),
        val rawBody: String? = null,
        val errorMessage: String? = null
    )

    suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk>
    suspend fun testConnection(provider: Provider): Boolean
    suspend fun fetchModels(provider: Provider): List<String>
    suspend fun fetchDiscoveredModels(provider: Provider): List<DiscoveredModelInfo> {
        return fetchModels(provider).map { DiscoveredModelInfo(id = it) }
    }

    /** 获取模型目录及原始响应；具体适配器应覆写此方法以避免重复请求。 */
    suspend fun fetchModelCatalog(provider: Provider): ModelCatalogResult {
        return ModelCatalogResult(models = fetchDiscoveredModels(provider))
    }

    companion object {
        const val MAX_BUFFERED_RESPONSE_BODY_BYTES: Long = 4L * 1024L * 1024L
        /** Provider 请求与官方 Cloud Code 统一使用 OkHttp 高性能工业级引擎，自动跟随系统代理与 CONNECT 隧道。 */
        val sharedHttpClient = createHttpClient(useSystemProxy = true)
        val officialHttpClient = createHttpClient(useSystemProxy = true)

        fun closeAll() {
            runCatching { sharedHttpClient.close() }
            runCatching { officialHttpClient.close() }
        }

        /** 回环官方地址不能再经过系统代理，否则会破坏本地调试端点。 */
        fun officialClientFor(url: String): HttpClient {
            val uri = runCatching { URI(url) }.getOrNull()
            val host = uri?.host?.lowercase()
            val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
            return if (isLoopback) sharedHttpClient else officialHttpClient
        }

        private fun createHttpClient(useSystemProxy: Boolean = true): HttpClient {
            return HttpClient(OkHttp) {
                engine {
                    config {
                        connectTimeout(120, TimeUnit.SECONDS)
                        readTimeout(120, TimeUnit.SECONDS)
                        writeTimeout(120, TimeUnit.SECONDS)
                        if (useSystemProxy) {
                            proxySelector(ProxySelector.getDefault())
                        } else {
                            proxy(Proxy.NO_PROXY)
                        }
                    }
                }
                // 官方 Cloud Code 透传沿用 byok 的 120 秒整体上限，避免连接超时在 DNS/TLS 建连稍慢时误判为上游不可达。
                install(HttpTimeout) {
                    connectTimeoutMillis = 120_000L
                    requestTimeoutMillis = 120_000L
                }
                install(HttpRequestRetry) {
                    maxRetries = 2
                    retryOnException(maxRetries = 2, retryOnTimeout = false)
                    exponentialDelay()
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                    })
                }
            }
        }

        /**
         * 按 agy precedence 依次应用协议鉴权、Provider headers、headerOverrides。
         * 每层先移除同名 header，保证后层自定义值覆盖前层且不会重复发送。
         */
        fun applyHeaders(
            builder: HttpRequestBuilder,
            provider: Provider,
            authHeaders: Map<String, String> = emptyMap()
        ) {
            fun putSingleHeader(name: String, value: String) {
                builder.headers.remove(name)
                builder.header(name, value)
            }
            authHeaders.forEach { (name, value) -> putSingleHeader(name, value) }
            provider.headers?.forEach { (name, value) -> putSingleHeader(name, value) }
            provider.headerOverrides?.forEach { (name, value) -> putSingleHeader(name, value) }
        }

        /** 为每个请求应用 Provider 的连接、整体请求和流式空闲超时。 */
        fun applyTimeouts(
            builder: HttpRequestBuilder,
            provider: Provider,
            streaming: Boolean,
            minimumRequestTimeoutMs: Long = 0L
        ) {
            val requestTimeoutMs = maxOf(provider.requestTimeoutMs, minimumRequestTimeoutMs)
            val connectTimeoutMs = maxOf(provider.connectTimeoutMs, 30_000L)
            builder.timeout {
                connectTimeoutMillis = connectTimeoutMs.takeIf { it > 0L }
                requestTimeoutMillis = if (streaming) {
                    null
                } else {
                    requestTimeoutMs.takeIf { it > 0L }
                }
                socketTimeoutMillis = if (streaming) {
                    provider.streamIdleTimeoutMs.takeIf { it > 0L }
                } else {
                    null
                }
            }
        }

        /**
         * 流式请求只在等待响应头阶段使用 request_timeout_ms；返回后由 socket 超时约束空闲间隔。
         * 不在此处包裹响应体读取，避免持续有数据的长流被整体请求超时截断。
         */
        suspend fun executeWithResponseHeadersTimeout(
            provider: Provider,
            streaming: Boolean,
            minimumRequestTimeoutMs: Long = 0L,
            execute: suspend () -> HttpResponse
        ): HttpResponse {
            val timeoutMs = maxOf(provider.requestTimeoutMs, minimumRequestTimeoutMs).takeIf { it > 0L }
            return if (!streaming || timeoutMs == null) execute() else withTimeout(timeoutMs) { execute() }
        }

        /** 将已由路由层清理的 extra_body 合并到协议请求顶层，禁止覆盖受控字段。 */
        fun mergeSafeExtraBody(
            base: JsonObject,
            request: NeutralChatRequest
        ): JsonObject {
            if (request.extraBody.isEmpty()) return base
            val merged = base.toMutableMap()
            request.extraBody.forEach { (key, value) ->
                if (key.lowercase() !in ParameterOverrides.CONTROLLED_EXTRA_BODY_KEYS) {
                    merged[key] = mergeJsonElement(merged[key], value)
                }
            }
            return JsonObject(merged)
        }

        fun upstreamFailureStatus(error: Throwable): Int {
            return when (error) {
                is HttpRequestTimeoutException, is SocketTimeoutException -> 504
                else -> 502
            }
        }

        fun responseBodyExceedsBufferedLimit(response: HttpResponse): Boolean {
            return response.headers[HttpHeaders.ContentLength]
                ?.toLongOrNull()
                ?.let { it > MAX_BUFFERED_RESPONSE_BODY_BYTES }
                ?: false
        }

        /** CPA/独立代理在回环 8317 端口要求显式 client_version=1。 */
        fun appendCpaCatalogVersion(url: String): String {
            val uri = runCatching { URI(url) }.getOrNull() ?: return url
            if (!isCpaCatalogUrl(url)) return url
            val query = uri.rawQuery.orEmpty()
            if (query.split('&').any { it.substringBefore('=').equals("client_version", ignoreCase = true) }) {
                return url
            }
            return if (query.isBlank()) "$url?client_version=1" else "$url&client_version=1"
        }

        fun isCpaCatalogUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.trim('[', ']')?.lowercase()
            return host in setOf("localhost", "127.0.0.1", "::1") && uri.port == 8317
        }

        /** 读取一个完整 SSE 事件，支持注释、CRLF 与多行 data。 */
        suspend fun readSseDataEvent(channel: ByteReadChannel): Result<String?> {
            val dataLines = mutableListOf<String>()
            var firstLine = true
            while (!channel.isClosedForRead) {
                val rawLine = channel.readUTF8Line() ?: break
                val line = if (firstLine) rawLine.removePrefix("\uFEFF") else rawLine
                firstLine = false
                if (line.trim().isEmpty()) {
                    if (dataLines.isEmpty()) continue
                    return Result.success(dataLines.joinToString("\n"))
                }
                val trimmed = line.trimEnd()
                if (trimmed.startsWith(":")) continue
                if (trimmed.startsWith("data:")) {
                    dataLines += trimmed.removePrefix("data:").removePrefix(" ")
                    continue
                }
                if (trimmed.startsWith("event:") || trimmed.startsWith("id:") || trimmed.startsWith("retry:")) {
                    continue
                }
                return Result.failure(IllegalArgumentException("SSE frame is missing data field"))
            }
            return if (dataLines.isEmpty()) {
                Result.success(null)
            } else {
                Result.failure(IllegalArgumentException("SSE event is missing a terminating blank line"))
            }
        }

        /**
         * 有些上游使用 chunked 传输，不能只依赖 Content-Length 做大小保护。
         * 这里在解析 JSON 前逐块读取并硬限制总字节数，避免错误响应或目录响应
         * 通过分块传输绕过 byok 同等的缓冲上限。
         */
        suspend fun readLimitedResponseText(response: HttpResponse): Result<String> {
            return readLimitedResponseBytes(response).map { bytes ->
                bytes.toString(Charsets.UTF_8)
            }
        }

        /** 读取受限的二进制响应，供官方透传保留原始媒体类型。 */
        suspend fun readLimitedResponseBytes(response: HttpResponse): Result<ByteArray> {
            if (responseBodyExceedsBufferedLimit(response)) {
                return Result.failure(
                    IllegalStateException(
                        "Upstream response body exceeds ${MAX_BUFFERED_RESPONSE_BODY_BYTES / (1024 * 1024)} MiB buffered limit"
                    )
                )
            }
            return try {
                val channel: ByteReadChannel = response.body()
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > MAX_BUFFERED_RESPONSE_BODY_BYTES) {
                        channel.cancel(
                            IllegalStateException(
                                "Upstream response body exceeds ${MAX_BUFFERED_RESPONSE_BODY_BYTES / (1024 * 1024)} MiB buffered limit"
                            )
                        )
                        return Result.failure(
                            IllegalStateException(
                                "Upstream response body exceeds ${MAX_BUFFERED_RESPONSE_BODY_BYTES / (1024 * 1024)} MiB buffered limit"
                            )
                        )
                    }
                    output.write(buffer, 0, read)
                }
                channel.closedCause?.let { cause -> return Result.failure(cause) }
                Result.success(output.toByteArray())
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

        private fun mergeJsonElement(parent: JsonElement?, child: JsonElement): JsonElement {
            if (parent !is JsonObject || child !is JsonObject) return child
            val merged = parent.toMutableMap()
            child.forEach { (key, value) ->
                merged[key] = mergeJsonElement(merged[key], value)
            }
            return JsonObject(merged)
        }
    }
}
