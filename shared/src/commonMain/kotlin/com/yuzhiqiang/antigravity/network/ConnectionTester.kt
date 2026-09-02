package com.yuzhiqiang.antigravity.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter

/**
 * 代理连接测试器，对标 agy-byok 的 test_connection 命令。
 * 通过向代理发送一个轻量请求来验证代理是否可达。
 */
object ConnectionTester {

    data class TestResult(
        val success: Boolean,
        val latencyMs: Long = 0,
        val statusCode: Int = 0,
        val error: String? = null
    )

    data class OutboundProxyTestResult(
        val success: Boolean,
        val latencyMs: Long,
        val statusCode: Int = 0,
        val endpoint: NetworkProxyEndpoint? = null,
        val direct: Boolean = false,
        val fellBackToDirect: Boolean = false,
        val error: String? = null
    )

    /**
     * 测试代理连接是否正常。
     * 对标 agy-byok 中 Overview 页的 "Test Connection" 按钮。
     */
    suspend fun testProxy(port: Int): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URI("http://127.0.0.1:$port/v1/models").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime

            connection.disconnect()

            TestResult(
                success = responseCode in 200..299,
                latencyMs = latency,
                statusCode = responseCode
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TestResult(
                success = false,
                latencyMs = latency,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 测试官方 Cloud Code 服务是否能够建立网络通信。
     * 该检查关注网络连通性，不把官方接口返回的鉴权或业务状态误判为网络失败。
     */
    suspend fun testOfficialService(): TestResult {
        val result = testOutboundProxy(PlatformNetworkConfig.currentOutboundProxy())
        return TestResult(
            success = result.success,
            latencyMs = result.latencyMs,
            statusCode = result.statusCode,
            error = result.error
        )
    }

    suspend fun testOutboundProxy(config: OutboundProxyConfig): OutboundProxyTestResult =
        withContext(Dispatchers.IO) {
            val target = URI("https://daily-cloudcode-pa.googleapis.com/v1internal:listExperiments")
            val routes = PlatformNetworkConfig.selectProxies(config, target)
            if (routes.isEmpty()) {
                return@withContext OutboundProxyTestResult(
                    success = false,
                    latencyMs = 0L,
                    error = "当前模式未检测到可用的系统代理，且未允许直连回退"
                )
            }

            val startedAt = System.currentTimeMillis()
            var lastError: String? = null
            routes.forEachIndexed { index, proxy ->
                val result = testOfficialRoute(target, proxy)
                if (result.success) {
                    return@withContext OutboundProxyTestResult(
                        success = true,
                        latencyMs = System.currentTimeMillis() - startedAt,
                        statusCode = result.statusCode,
                        endpoint = proxyEndpoint(proxy),
                        direct = proxy.type() == Proxy.Type.DIRECT,
                        fellBackToDirect = index > 0 && proxy.type() == Proxy.Type.DIRECT
                    )
                }
                lastError = result.error
            }

            OutboundProxyTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startedAt,
                error = lastError ?: "所有出站网络路径均连接失败"
            )
        }

    /**
     * 测试上游 Provider 是否可达。
     * 对标 agy-byok 的 provider 连通性检测。
     */
    suspend fun testUpstream(baseUrl: String, apiKey: String? = null): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URI(appendPath(baseUrl, "/models")).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime

            connection.disconnect()

            TestResult(
                success = responseCode in 200..299,
                latencyMs = latency,
                statusCode = responseCode
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TestResult(
                success = false,
                latencyMs = latency,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 测试指定 Provider 及其模型连通性。
     * 对标 agy-byok 的 test_model_connection 逻辑：
     * - 若指定 modelId，则针对协议发送最小补全请求（Reply with OK.），校验鉴权、模型有效性及额度；
     * - 若未指定 modelId，则请求模型列表或基础端点验证服务商可达性。
     */
    suspend fun testProvider(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        modelId: String? = null
    ): TestResult = testProviderInternal(provider, modelId, imageOnly = false)

    /** 编辑器尚未落盘模型配置时，直接按当前目录识别结果执行生图探测。 */
    suspend fun testProvider(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        modelId: String,
        imageOnly: Boolean
    ): TestResult = testProviderInternal(provider, modelId, imageOnly)

    /** 使用模型能力元数据执行与 byok 一致的生图连通性探测。 */
    suspend fun testProvider(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        model: com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
    ): TestResult = testProviderInternal(
        provider,
        model.providerModelId,
        imageOnly = com.yuzhiqiang.antigravity.domain.model.ModelRole.IMAGE_GENERATION in model.capabilities.roles &&
                com.yuzhiqiang.antigravity.domain.model.ModelRole.AGENT !in model.capabilities.roles
    )

    /** 以指定推理档位执行最小模型探测，保留 byok 的 reasoning mapping 语义。 */
    suspend fun testProvider(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        model: com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding,
        reasoningLevel: ReasoningLevel
    ): TestResult {
        val mapping = ReasoningMappingSupport.resolveMapping(
            protocol = provider.protocol,
            level = reasoningLevel,
            configured = ReasoningMappingSupport.parse(model.capabilities.reasoning.levels),
            outputTokenLimit = model.tokenLimits.outputTokenLimit
        ) ?: return TestResult(success = false, error = "模型不支持推理档位 ${reasoningLevel.label}")
        return testProviderInternal(
            provider = provider,
            modelId = model.providerModelId,
            imageOnly = false,
            reasoningEffort = ReasoningMappingSupport.mappingValueAsString(mapping),
            reasoningBudget = ReasoningMappingSupport.mappingValueAsInt(mapping),
            reasoningDisabled = mapping.kind.equals("disabled", ignoreCase = true)
        )
    }

    private suspend fun testProviderInternal(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        modelId: String?,
        imageOnly: Boolean,
        reasoningEffort: String? = null,
        reasoningBudget: Int? = null,
        reasoningDisabled: Boolean = false
    ): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        try {
            val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
            val effectiveTimeout = provider.requestTimeoutMs
                .coerceAtLeast(if (imageOnly) 60_000L else 3_000L)
                .toInt()
                .coerceIn(3000, if (imageOnly) 60_000 else 30_000)
            val effectiveConnectTimeout = provider.connectTimeoutMs.toInt().coerceIn(2000, 15000)

            if (!modelId.isNullOrBlank()) {
                // 真实模型单项连通性测试 (按协议构建最小请求)
                if (imageOnly && provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.ANTHROPIC_MESSAGES) {
                    return@withContext TestResult(
                        success = false,
                        error = "Anthropic 不支持图像生成"
                    )
                }
                when (provider.protocol) {
                    com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.ANTHROPIC_MESSAGES -> {
                        val endpoint = provider.generateEndpoint
                            ?.takeIf { it.isNotBlank() }
                            ?.replace("{model}", modelId)
                            ?: appendProtocolPath(baseUrl, "/messages")
                        connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = effectiveConnectTimeout
                            readTimeout = effectiveTimeout
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("anthropic-version", "2023-06-01")
                            if (!provider.apiKey.isNullOrBlank()) {
                                setRequestProperty("x-api-key", provider.apiKey)
                            }
                            provider.headers?.forEach { (k, v) -> setRequestProperty(k, v) }
                            provider.headerOverrides?.forEach { (k, v) -> setRequestProperty(k, v) }
                            val payload = when {
                                reasoningBudget != null -> """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"max_tokens":${reasoningBudget + 1},"thinking":{"type":"enabled","budget_tokens":$reasoningBudget}}"""
                                reasoningDisabled -> """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"max_tokens":8,"thinking":{"type":"disabled"}}"""
                                else -> """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"max_tokens":8}"""
                            }
                            outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        }
                    }

                    com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.GEMINI_GENERATE_CONTENT -> {
                        val endpoint = provider.generateEndpoint
                            ?.takeIf { it.isNotBlank() }
                            ?.replace("{model}", modelId)
                            ?.replace(":streamGenerateContent", ":generateContent")
                            ?: appendGeminiModelPath(baseUrl, modelId)
                        connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = effectiveConnectTimeout
                            readTimeout = effectiveTimeout
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Accept", "application/json")
                            if (!provider.apiKey.isNullOrBlank()) {
                                setRequestProperty("x-goog-api-key", provider.apiKey)
                            }
                            provider.headers?.forEach { (k, v) -> setRequestProperty(k, v) }
                            provider.headerOverrides?.forEach { (k, v) -> setRequestProperty(k, v) }
                            val payload = if (imageOnly) {
                                """{"contents":[{"parts":[{"text":"a small red dot"}]}],"generationConfig":{"responseModalities":["IMAGE"]}}"""
                            } else if (reasoningBudget != null) {
                                """{"contents":[{"parts":[{"text":"Reply with OK."}]}],"generationConfig":{"maxOutputTokens":8,"thinkingConfig":{"thinkingBudget":$reasoningBudget}}}"""
                            } else if (reasoningDisabled) {
                                """{"contents":[{"parts":[{"text":"Reply with OK."}]}],"generationConfig":{"maxOutputTokens":8,"thinkingConfig":{"thinkingBudget":0}}}"""
                            } else {
                                """{"contents":[{"parts":[{"text":"Reply with OK."}]}],"generationConfig":{"maxOutputTokens":8}}"""
                            }
                            outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        }
                    }

                    else -> {
                        // 默认 OPENAI_CHAT_COMPLETIONS / OPENAI_RESPONSES 格式
                        val defaultPath = if (
                            provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES
                        ) "/v1/responses" else "/v1/chat/completions"
                        if (imageOnly && provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES) {
                            return@withContext TestResult(
                                success = false,
                                error = "OpenAI Responses API 不支持图像生成"
                            )
                        }
                        val endpoint =
                            if (imageOnly && provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_CHAT_COMPLETIONS) {
                                deriveOpenAiImageEndpoint(provider)
                            } else {
                                provider.generateEndpoint
                                    ?.takeIf { it.isNotBlank() }
                                    ?.replace("{model}", modelId)
                                    ?: appendProtocolPath(baseUrl, defaultPath.removePrefix("/v1"))
                            }
                        connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = effectiveConnectTimeout
                            readTimeout = effectiveTimeout
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Accept", "application/json")
                            applyProviderAuth(this, provider)
                            provider.headers?.forEach { (k, v) -> setRequestProperty(k, v) }
                            provider.headerOverrides?.forEach { (k, v) -> setRequestProperty(k, v) }
                            val payload = if (imageOnly) {
                                """{"model":"$modelId","prompt":"a small red dot","n":1,"response_format":"b64_json"}"""
                            } else if (reasoningEffort != null && provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES) {
                                """{"model":"$modelId","input":[{"role":"user","content":"Reply with OK."}],"reasoning":{"effort":"$reasoningEffort"},"stream":false}"""
                            } else if (reasoningEffort != null) {
                                """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"reasoning_effort":"$reasoningEffort","stream":false}"""
                            } else if (reasoningDisabled && provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES) {
                                """{"model":"$modelId","input":[{"role":"user","content":"Reply with OK."}],"stream":false}"""
                            } else if (reasoningDisabled) {
                                """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"reasoning_effort":"none","stream":false}"""
                            } else if (
                                provider.protocol == com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.OPENAI_RESPONSES
                            ) {
                                """{"model":"$modelId","input":[{"role":"user","content":"Reply with OK."}],"max_output_tokens":8,"stream":false}"""
                            } else {
                                """{"model":"$modelId","messages":[{"role":"user","content":"Reply with OK."}],"max_tokens":8,"stream":false}"""
                            }
                            outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        }
                    }
                }
            } else {
                // 纯服务商连通性探测 (GET /v1/models)
                val endpoint = ProviderAdapter.appendCpaCatalogVersion(
                    provider.modelsEndpoint?.takeIf { it.isNotBlank() }
                        ?: appendPath(baseUrl, "/models")
                )
                connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = effectiveConnectTimeout
                    readTimeout = effectiveTimeout
                    setRequestProperty("Accept", "application/json")
                    applyProviderAuth(this, provider)
                    provider.headers?.forEach { (k, v) -> setRequestProperty(k, v) }
                    provider.headerOverrides?.forEach { (k, v) -> setRequestProperty(k, v) }
                }
            }

            val responseConnection = connection
                ?: throw IllegalStateException("未创建 Provider 连接")
            val responseCode = responseConnection.responseCode
            val latency = System.currentTimeMillis() - startTime
            val isSuccess = responseCode in 200..299

            val errorDetail = if (!isSuccess) {
                try {
                    val stream = responseConnection.errorStream ?: responseConnection.inputStream
                    val errorBody = stream?.use { input ->
                        val bytes = input.readNBytes(4 * 1024 * 1024 + 1)
                        bytes.toString(Charsets.UTF_8).let { body ->
                            if (bytes.size > 4 * 1024 * 1024) body.take(4 * 1024 * 1024) + "..." else body
                        }
                    } ?: ""
                    if (errorBody.isNotBlank()) {
                        if (errorBody.length > 120) errorBody.take(120) + "..." else errorBody
                    } else {
                        "HTTP $responseCode"
                    }
                } catch (_: Exception) {
                    "HTTP $responseCode"
                }
            } else {
                val body = try {
                    responseConnection.inputStream.use { stream ->
                        val bytes = stream.readBytes()
                        bytes.toString(Charsets.UTF_8)
                    }
                } catch (error: Exception) {
                    return@withContext TestResult(
                        success = false,
                        latencyMs = latency,
                        statusCode = responseCode,
                        error = "读取上游响应失败：${error.message ?: "未知错误"}"
                    )
                }
                if (body.isBlank()) {
                    "上游返回空响应"
                } else {
                    runCatching { Json.parseToJsonElement(body) }
                        .exceptionOrNull()
                        ?.let { "上游返回的 JSON 无法解析：${it.message ?: "格式无效"}" }
                }
            }

            TestResult(
                success = isSuccess && errorDetail == null,
                latencyMs = latency,
                statusCode = responseCode,
                error = errorDetail
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TestResult(
                success = false,
                latencyMs = latency,
                error = e.message ?: "网络连接异常"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun testOfficialRoute(target: URI, proxy: Proxy): TestResult {
        val startedAt = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            connection = (target.toURL().openConnection(proxy) as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            TestResult(
                success = status > 0 && status != 407,
                latencyMs = System.currentTimeMillis() - startedAt,
                statusCode = status,
                error = if (status == 407) "代理服务器要求身份认证，当前配置未提供认证信息" else null
            )
        } catch (error: Exception) {
            TestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startedAt,
                error = error.message ?: "网络连接异常"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun proxyEndpoint(proxy: Proxy): NetworkProxyEndpoint? {
        val address = proxy.address() as? java.net.InetSocketAddress ?: return null
        val protocol = if (proxy.type() == Proxy.Type.SOCKS) {
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol.SOCKS5
        } else {
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol.HTTP
        }
        return NetworkProxyEndpoint(protocol, address.hostString, address.port)
    }

    private fun applyProviderAuth(
        connection: HttpURLConnection,
        provider: com.yuzhiqiang.antigravity.domain.model.Provider
    ) {
        if (provider.apiKey.isNullOrBlank()) return
        when (provider.protocol) {
            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.ANTHROPIC_MESSAGES ->
                connection.setRequestProperty("x-api-key", provider.apiKey)

            com.yuzhiqiang.antigravity.domain.model.ProviderProtocol.GEMINI_GENERATE_CONTENT ->
                connection.setRequestProperty("x-goog-api-key", provider.apiKey)

            else -> connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
    }

    private fun appendPath(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val queryIndex = base.indexOf('?')
        if (queryIndex < 0) return if (base.endsWith(path)) base else "$base$path"
        val pathPart = base.substring(0, queryIndex)
        val queryPart = base.substring(queryIndex)
        return if (pathPart.endsWith(path)) base else "${pathPart.trimEnd('/')}$path$queryPart"
    }

    private fun appendProtocolPath(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/v1") && path.startsWith("/") -> "$base$path"
            base.endsWith("/v1beta") && path.startsWith("/") -> "$base$path"
            else -> "$base/v1$path"
        }
    }

    private fun appendGeminiModelPath(baseUrl: String, modelId: String): String {
        val base = baseUrl.trimEnd('/')
        val apiBase = if (base.endsWith("/v1beta")) base else "$base/v1beta"
        return "$apiBase/models/$modelId:generateContent"
    }

    private fun deriveOpenAiImageEndpoint(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider
    ): String {
        val endpoint = provider.generateEndpoint?.trim()?.takeIf { it.isNotBlank() }
            ?: return appendProtocolPath(provider.effectiveBaseUrl, "/images/generations")
        val expanded = endpoint.replace("{model}", "model")
        return runCatching {
            val uri = java.net.URI(expanded)
            val path = uri.path.trimEnd('/')
            val basePath = path.removeSuffix("/chat/completions").removeSuffix("/responses")
            java.net.URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                "${basePath.ifBlank { "/v1" }}/images/generations",
                uri.query,
                uri.fragment
            ).toString()
        }.getOrElse { appendProtocolPath(provider.effectiveBaseUrl, "/images/generations") }
    }
}
