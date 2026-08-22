package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo

interface ProviderAdapter {
    suspend fun sendStream(provider: Provider, request: NeutralChatRequest): Flow<NeutralStreamChunk>
    suspend fun testConnection(provider: Provider): Boolean
    suspend fun fetchModels(provider: Provider): List<String>
    suspend fun fetchDiscoveredModels(provider: Provider): List<DiscoveredModelInfo> {
        return fetchModels(provider).map { DiscoveredModelInfo(id = it) }
    }

    companion object {
        val sharedHttpClient = HttpClient(CIO) {
            install(HttpTimeout)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
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
            streaming: Boolean
        ) {
            builder.timeout {
                connectTimeoutMillis = provider.connectTimeoutMs.takeIf { it > 0L }
                requestTimeoutMillis = if (streaming) {
                    null
                } else {
                    provider.requestTimeoutMs.takeIf { it > 0L }
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
            execute: suspend () -> HttpResponse
        ): HttpResponse {
            val timeoutMs = provider.requestTimeoutMs.takeIf { it > 0L }
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
