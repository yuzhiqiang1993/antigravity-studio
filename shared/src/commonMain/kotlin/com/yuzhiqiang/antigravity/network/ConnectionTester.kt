package com.yuzhiqiang.antigravity.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    /**
     * 测试代理连接是否正常。
     * 对标 agy-byok 中 Overview 页的 "Test Connection" 按钮。
     */
    suspend fun testProxy(port: Int): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("http://127.0.0.1:$port/v1/models")
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
    suspend fun testOfficialService(): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://daily-cloudcode-pa.googleapis.com/v1internal:listExperiments")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { output -> output.write("{}".toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            TestResult(
                success = responseCode > 0,
                latencyMs = System.currentTimeMillis() - startTime,
                statusCode = responseCode,
                error = if (responseCode > 0) null else "官方服务未返回有效 HTTP 状态"
            )
        } catch (error: Exception) {
            TestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startTime,
                error = error.message ?: "无法连接官方服务"
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 测试上游 Provider 是否可达。
     * 对标 agy-byok 的 provider 连通性检测。
     */
    suspend fun testUpstream(baseUrl: String, apiKey: String? = null): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("${baseUrl.trimEnd('/')}/v1/models")
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
     */
    suspend fun testProvider(
        provider: com.yuzhiqiang.antigravity.domain.model.Provider,
        modelId: String? = null
    ): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
            val endpoint = provider.modelsEndpoint?.takeIf { it.isNotBlank() } ?: "$baseUrl/v1/models"
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = provider.connectTimeoutMs.toInt().coerceIn(1000, 15000)
            connection.readTimeout = provider.requestTimeoutMs.toInt().coerceIn(1000, 30000)
            connection.setRequestProperty("Accept", "application/json")
            if (!provider.apiKey.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            }
            provider.headers?.forEach { (k, v) ->
                connection.setRequestProperty(k, v)
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
}
