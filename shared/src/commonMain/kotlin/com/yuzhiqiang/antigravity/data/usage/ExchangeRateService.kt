package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.logging.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 权威外汇汇率服务（USD -> CNY 汇率自动更新与本地持久化）。
 *
 * 首选 open.er-api.com 开放权威汇率接口，备选 api.exchangerate-api.com；
 * 网络受限时安全回退至本地磁盘缓存或基准汇率。
 */
class ExchangeRateService(
    private val httpClient: HttpClient? = null,
    private val customRootDir: File? = null
) {
    companion object {
        const val PRIMARY_EXCHANGE_URL = "https://open.er-api.com/v6/latest/USD"
        const val FALLBACK_EXCHANGE_URL = "https://api.exchangerate-api.com/v4/latest/USD"
        const val DEFAULT_USD_TO_CNY = 7.25
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 小时
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val rootDir: File by lazy { customRootDir ?: AppDataPaths.rootDir() }
    private val cacheFile: File by lazy { File(rootDir, "exchange_rate_cache.json") }

    @Volatile
    private var usdToCnyRate: Double = DEFAULT_USD_TO_CNY
    private var lastFetchedAt: Long = 0L

    init {
        loadFromLocalCache()
    }

    fun currentUsdToCny(): Double = usdToCnyRate

    suspend fun refreshRate(force: Boolean = false): Double = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastFetchedAt) < CACHE_TTL_MS && usdToCnyRate > 0.0 && lastFetchedAt > 0L) {
            return@withContext usdToCnyRate
        }

        val client = httpClient ?: HttpClient {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 4000L
                connectTimeoutMillis = 3000L
                socketTimeoutMillis = 4000L
            }
        }
        val ownsClient = httpClient == null

        val urls = listOf(PRIMARY_EXCHANGE_URL, FALLBACK_EXCHANGE_URL)
        var fetchedRate: Double? = null

        for (url in urls) {
            try {
                kotlinx.coroutines.withTimeout(5000L) {
                    val response = client.get(url)
                    if (response.status.value in 200..299) {
                        val body = response.bodyAsText()
                        val rate = parseCnyRate(body)
                        if (rate != null && rate in 4.0..15.0) {
                            fetchedRate = rate
                            saveToLocalCache(rate, now)
                            AppLog.i("Usage/ExchangeRate") { "成功从远端权威接口拉取最新 USD/CNY 汇率: $rate (url=$url)" }
                        }
                    }
                }
                if (fetchedRate != null) break
            } catch (error: Exception) {
                AppLog.d("Usage/ExchangeRate") { "从汇率接口 $url 获取失败: ${error.message}" }
            }
        }

        if (ownsClient) {
            try { client.close() } catch (_: Exception) {}
        }

        val finalRate = fetchedRate ?: usdToCnyRate
        usdToCnyRate = finalRate
        lastFetchedAt = now
        finalRate
    }

    private fun parseCnyRate(body: String): Double? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val rates = root["rates"] as? JsonObject ?: return null
            rates["CNY"]?.jsonPrimitive?.doubleOrNull
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFromLocalCache() {
        try {
            if (!cacheFile.isFile) return
            val text = cacheFile.readText(Charsets.UTF_8)
            val root = json.parseToJsonElement(text) as? JsonObject ?: return
            val rate = root["usdToCny"]?.jsonPrimitive?.doubleOrNull
            val updated = root["updatedAt"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L
            if (rate != null && rate in 4.0..15.0) {
                usdToCnyRate = rate
                lastFetchedAt = updated
                AppLog.d("Usage/ExchangeRate") { "从本地缓存恢复汇率: USD/CNY = $rate" }
            }
        } catch (_: Exception) {
            // 保持基准默认汇率
        }
    }

    private fun saveToLocalCache(rate: Double, now: Long) {
        try {
            val content = """{"usdToCny":$rate,"updatedAt":$now}"""
            AtomicFileWriter.writeText(
                target = cacheFile,
                content = content,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY
            )
        } catch (_: Exception) {
            // 忽略写入异常
        }
    }
}
