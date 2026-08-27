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
        /** 针对长思考推理模型与大 Prompt Prefill，默认最小请求超时保底为 600 秒（10 分钟）。 */
        const val DEFAULT_MINIMUM_REQUEST_TIMEOUT_MS: Long = 600_000L

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
                        connectTimeout(60, TimeUnit.SECONDS)
                        readTimeout(900, TimeUnit.SECONDS)
                        writeTimeout(900, TimeUnit.SECONDS)
                        if (useSystemProxy) {
                            proxySelector(ProxySelector.getDefault())
                        } else {
                            proxy(Proxy.NO_PROXY)
                        }
                    }
                }
                // 整体超时上限放宽至 900 秒（15 分钟），满足复杂推理模型深度思考与长上下文场景。
                install(HttpTimeout) {
                    connectTimeoutMillis = 60_000L
                    requestTimeoutMillis = 900_000L
                }
                install(HttpRequestRetry) {
                    // 全局禁用对生成/转发请求的自动隐式重试，避免向已计费或产生中间状态的上游发起重复并发请求
                    noRetry()
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
            minimumRequestTimeoutMs: Long = DEFAULT_MINIMUM_REQUEST_TIMEOUT_MS
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
                    maxOf(provider.streamIdleTimeoutMs, minimumRequestTimeoutMs).takeIf { it > 0L }
                } else {
                    null
                }
            }
        }

        /**
         * 流式请求只在等待响应头与建立连接阶段使用 request_timeout_ms；
         * 超时后主动熔断并标记为 504 Gateway Timeout，防止客户端无限挂起。
         */
        suspend fun <T> executeStreamingWithTimeout(
            provider: Provider,
            minimumRequestTimeoutMs: Long = DEFAULT_MINIMUM_REQUEST_TIMEOUT_MS,
            block: suspend () -> T
        ): T {
            val timeoutMs = maxOf(provider.requestTimeoutMs, minimumRequestTimeoutMs).takeIf { it > 0L }
            return if (timeoutMs == null) block() else withTimeout(timeoutMs) { block() }
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
                is HttpRequestTimeoutException,
                is SocketTimeoutException,
                is kotlinx.coroutines.TimeoutCancellationException -> 504
                else -> {
                    if (error.message?.contains("timed out", ignoreCase = true) == true ||
                        error.message?.contains("timeout", ignoreCase = true) == true
                    ) {
                        504
                    } else {
                        502
                    }
                }
            }
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
                // 未知行/非标准字段容错跳过，避免中断长流
                continue
            }
            return if (dataLines.isEmpty()) {
                Result.success(null)
            } else {
                Result.success(dataLines.joinToString("\n"))
            }
        }

        /** 读取上游响应文本内容。 */
        suspend fun readResponseBodyText(response: HttpResponse): Result<String> {
            return readResponseBodyBytes(response).map { bytes ->
                bytes.toString(Charsets.UTF_8)
            }
        }

        suspend fun readLimitedResponseText(response: HttpResponse): Result<String> = readResponseBodyText(response)

        /** 读取上游二进制响应，供透传或解析使用。 */
        suspend fun readResponseBodyBytes(response: HttpResponse): Result<ByteArray> {
            return try {
                val channel: ByteReadChannel = response.body()
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                }
                channel.closedCause?.let { cause -> return Result.failure(cause) }
                Result.success(output.toByteArray())
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

        suspend fun readLimitedResponseBytes(response: HttpResponse): Result<ByteArray> = readResponseBodyBytes(response)

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
