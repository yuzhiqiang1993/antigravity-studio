package com.yuzhiqiang.antigravity.services.quota

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaGroup
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow
import com.yuzhiqiang.antigravity.network.PlatformNetworkConfig
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 账号配额抓取服务 (1:1 深度对齐 Cockpit 插件 QuotaApiService & quotaSummary 规范)：
 * 1. 支持通过 loadCodeAssist 精确解析项目 ID、订阅层级 (Paid/Current/User Tier) 及 Google One AI 积分；
 * 2. 通过 retrieveUserQuotaSummary 并传入 {"project": projectId} 完整拉取双模型 (Claude/Gemini) 5小时与周额度；
 * 3. 支持官方模型族鲁棒分类 (Claude / Gemini / 3P / Anthropic / GPT) 与窗口顺序归一化；
 * 4. 具备 Token 401 自动检测与重试容错机制。
 */
class QuotaFetchService(
    private val tokenRefreshCallback: (suspend (refreshToken: String) -> Result<String>)? = null
) {

    companion object {
        private val CLOUD_CODE_HOSTS = listOf(
            "https://cloudcode-pa.googleapis.com"
        )
        private const val DEFAULT_PROJECT_ID = "cloudaicompanion-enterprise"
        private const val USER_AGENT = "Antigravity/4.1.29 Chrome/132.0.6834.160 Electron/39.2.3"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                proxySelector(PlatformNetworkConfig.createSmartProxySelector())
                connectTimeout(6, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.SECONDS)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 6_000L
            requestTimeoutMillis = 10_000L
        }
    }

    /**
     * 直接通过官方 REST API 拉取账号实时配额与订阅层级数据
     */
    suspend fun fetchActiveAccountQuota(activeAccount: AccountInfo): Result<AccountQuotaSnapshot> = withContext(Dispatchers.IO) {
        fetchRemoteAccountQuota(activeAccount)
    }

    suspend fun fetchRemoteAccountQuota(account: AccountInfo): Result<AccountQuotaSnapshot> = withContext(Dispatchers.IO) {
        if (account.tokens.accessToken.isBlank() && account.tokens.refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("账号暂无可用凭据"))
        }

        var currentToken = account.tokens.accessToken
        var attemptedRefresh = false

        // 预检：如果 token 为空或即将过期（1分钟内），直接预先通过 refreshToken 刷新出最新 accessToken
        val nowSec = System.currentTimeMillis() / 1000L
        val isExpired = account.tokens.expiryTimestamp <= (nowSec + 60L)

        if ((currentToken.isBlank() || isExpired) && account.tokens.refreshToken.isNotBlank() && tokenRefreshCallback != null) {
            attemptedRefresh = true
            val refreshResult = tokenRefreshCallback.invoke(account.tokens.refreshToken)
            if (refreshResult.isSuccess) {
                currentToken = refreshResult.getOrThrow()
            }
        }

        if (currentToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("无法获取有效的 Access Token"))
        }

        var lastFetchResult: Result<AccountQuotaSnapshot> = Result.failure(IllegalStateException("未执行配额抓取"))

        while (true) {
            val result = executeQuotaFetch(account, currentToken)
            if (result.isSuccess) {
                return@withContext result
            }

            lastFetchResult = result
            val error = result.exceptionOrNull()
            val isUnauthorized = error?.message?.contains("401") == true ||
                    error?.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true

            // 如果遇到 401 且尚未重试过，尝试换取新的 access token 并重试
            if (isUnauthorized && !attemptedRefresh && tokenRefreshCallback != null && account.tokens.refreshToken.isNotBlank()) {
                attemptedRefresh = true
                val refreshResult = tokenRefreshCallback.invoke(account.tokens.refreshToken)
                if (refreshResult.isSuccess) {
                    currentToken = refreshResult.getOrThrow()
                    continue
                }
            }

            return@withContext result
        }

        lastFetchResult
    }


    private suspend fun executeQuotaFetch(account: AccountInfo, token: String): Result<AccountQuotaSnapshot> {
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("缺少有效的 Access Token"))
        }
        var lastError: Exception? = null

        // 1. 尝试调用 loadCodeAssist 获取项目 ID、订阅层级及 Google One AI 积分
        var projectId = DEFAULT_PROJECT_ID
        var tierName: String? = null
        var isPro = false
        var detectedTier: com.yuzhiqiang.antigravity.domain.model.account.AccountTier = com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE
        var aiCredits: Double? = null

        for (host in CLOUD_CODE_HOSTS) {
            try {
                val url = "$host/v1internal:loadCodeAssist"
                val response: HttpResponse = httpClient.post(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    contentType(ContentType.Application.Json)
                    setBody("""{"metadata":{"ideType":"ANTIGRAVITY"}}""")
                }

                if (response.status == HttpStatusCode.Unauthorized) {
                    return Result.failure(IllegalStateException("HTTP 401 Unauthorized: Access Token 已失效"))
                }

                val responseText = response.bodyAsText()
                val root = json.parseToJsonElement(responseText) as? JsonObject
                if (root != null) {
                    // 解析项目 ID
                    root["cloudaicompanionProject"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                        projectId = it
                    }

                    val paidTier = root["paidTier"] as? JsonObject
                    val currentTier = root["currentTier"] as? JsonObject
                    val userTier = root["userTier"] as? JsonObject

                    val combinedTierText = "${paidTier?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                            "${paidTier?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                            "${currentTier?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                            "${currentTier?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                            "${userTier?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()} " +
                            "${userTier?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()}"

                    detectedTier = when {
                        combinedTierText.contains("ultra", ignoreCase = true) -> com.yuzhiqiang.antigravity.domain.model.account.AccountTier.ULTRA
                        combinedTierText.contains("enterprise", ignoreCase = true) -> com.yuzhiqiang.antigravity.domain.model.account.AccountTier.ENTERPRISE
                        combinedTierText.contains("pro", ignoreCase = true) ||
                                combinedTierText.contains("premium", ignoreCase = true) ||
                                (paidTier != null && paidTier.isNotEmpty()) -> com.yuzhiqiang.antigravity.domain.model.account.AccountTier.PRO
                        else -> com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE
                    }

                    isPro = detectedTier != com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE
                    tierName = when (detectedTier) {
                        com.yuzhiqiang.antigravity.domain.model.account.AccountTier.ULTRA -> "Google AI Ultra"
                        com.yuzhiqiang.antigravity.domain.model.account.AccountTier.PRO -> "Google AI Pro"
                        com.yuzhiqiang.antigravity.domain.model.account.AccountTier.ENTERPRISE -> "Enterprise"
                        com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE -> "FREE"
                    }




                    // 解析 Google One AI 积分 (aiCredits)
                    val rawCredits = userTier?.get("availableCredits") as? JsonArray
                        ?: paidTier?.get("availableCredits") as? JsonArray
                        ?: currentTier?.get("availableCredits") as? JsonArray
                        ?: root["availableCredits"] as? JsonArray

                    if (rawCredits != null) {
                        for (creditElem in rawCredits) {
                            val creditObj = creditElem as? JsonObject ?: continue
                            val creditType = creditObj["creditType"]?.jsonPrimitive?.contentOrNull
                            if (creditType == "GOOGLE_ONE_AI" || creditType == "FREE_TIER") {
                                val amountStr = creditObj["creditAmount"]?.jsonPrimitive?.contentOrNull
                                val parsed = amountStr?.toDoubleOrNull()
                                if (parsed != null) {
                                    aiCredits = parsed
                                    break
                                }
                            }
                        }
                    }

                    break
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. 尝试调用 retrieveUserQuotaSummary 并传入精准的 {"project": "$projectId"}
        val quotaGroups = mutableListOf<QuotaGroup>()
        for (host in CLOUD_CODE_HOSTS) {
            try {
                val url = "$host/v1internal:retrieveUserQuotaSummary"
                val response: HttpResponse = httpClient.post(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    contentType(ContentType.Application.Json)
                    setBody("""{"project":"$projectId"}""")
                }

                if (response.status == HttpStatusCode.Unauthorized) {
                    return Result.failure(IllegalStateException("HTTP 401 Unauthorized: Access Token 已失效"))
                }

                if (response.status == HttpStatusCode.Forbidden) {
                    // 403 Forbidden 权限不足，返回受限标记
                    return Result.success(
                        AccountQuotaSnapshot(
                            accountId = account.id,
                            email = account.email,
                            fetchedAt = System.currentTimeMillis(),
                            tierName = tierName,
                            isPro = isPro,
                            aiCredits = aiCredits,
                            models = emptyList(),
                            groups = emptyList(),
                            isForbidden = true,
                            isError = false
                        )
                    )
                }

                val responseText = response.bodyAsText()
                val parsedGroups = parseQuotaSummaryGroups(responseText)
                if (parsedGroups.isNotEmpty()) {
                    quotaGroups.addAll(parsedGroups)
                    break
                }
            } catch (_: Exception) {
            }
        }

        // 3. 尝试调用 fetchAvailableModels 获取具体模型池列表
        val modelQuotas = mutableListOf<ModelQuotaInfo>()
        for (host in CLOUD_CODE_HOSTS) {
            try {
                val url = "$host/v1internal:fetchAvailableModels"
                val response: HttpResponse = httpClient.post(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    contentType(ContentType.Application.Json)
                    setBody("""{"project":"$projectId"}""")
                }

                if (response.status == HttpStatusCode.Unauthorized) {
                    return Result.failure(IllegalStateException("HTTP 401 Unauthorized: Access Token 已失效"))
                }

                val responseText = response.bodyAsText()
                val models = parseModelsFromCatalogJson(responseText)
                if (models.isNotEmpty()) {
                    modelQuotas.addAll(models)
                    break
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (modelQuotas.isEmpty() && quotaGroups.isEmpty()) {
            return Result.failure(lastError ?: IllegalStateException("未能从 Google 官方端点解析到有效配额数据"))
        }

        val snapshot = AccountQuotaSnapshot(
            accountId = account.id,
            email = account.email,
            fetchedAt = System.currentTimeMillis(),
            tierName = tierName,
            tier = detectedTier,
            isPro = isPro,
            aiCredits = aiCredits,
            models = modelQuotas,
            groups = quotaGroups,
            isForbidden = false,
            isError = false
        )

        return Result.success(snapshot)
    }

    /**
     * 从官方 GetAvailableModels JSON 报文中解析模型配额
     */
    fun parseQuotaFromOfficialCatalogJson(account: AccountInfo, rawJson: String): AccountQuotaSnapshot {
        val models = parseModelsFromCatalogJson(rawJson)
        val groups = parseQuotaSummaryGroups(rawJson)

        return AccountQuotaSnapshot(
            accountId = account.id,
            email = account.email,
            fetchedAt = System.currentTimeMillis(),
            tierName = if (account.profile.tier.name == "PRO") "Google AI Pro" else "FREE",
            isPro = account.profile.tier.name == "PRO",
            models = models,
            groups = groups
        )
    }

    private fun parseModelsFromCatalogJson(rawJson: String): List<ModelQuotaInfo> {
        val list = mutableListOf<ModelQuotaInfo>()
        try {
            val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return emptyList()
            val responseObj = root["response"] as? JsonObject ?: root
            val modelsObj = responseObj["models"] as? JsonObject ?: return emptyList()

            for ((modelKey, modelVal) in modelsObj) {
                val item = modelVal as? JsonObject ?: continue
                val displayName = item["displayName"]?.jsonPrimitive?.contentOrNull
                    ?: item["label"]?.jsonPrimitive?.contentOrNull
                    ?: modelKey

                val quotaInfo = item["quotaInfo"] as? JsonObject
                val remainingFraction = quotaInfo?.get("remainingFraction")?.jsonPrimitive?.doubleOrNull
                    ?: quotaInfo?.get("remaining_fraction")?.jsonPrimitive?.doubleOrNull
                    ?: 1.0

                val resetTimeIso = quotaInfo?.get("resetTime")?.jsonPrimitive?.contentOrNull
                    ?: quotaInfo?.get("reset_time")?.jsonPrimitive?.contentOrNull

                val resetEpochSeconds = resetTimeIso?.let { parseIsoToEpochSeconds(it) }

                val keyLower = modelKey.lowercase()
                val family = when {
                    keyLower.contains("claude") || keyLower.contains("anthropic") || keyLower.contains("3p") -> "claude"
                    keyLower.contains("gemini") -> "gemini"
                    keyLower.contains("gpt") -> "gpt"
                    else -> "other"
                }

                list.add(
                    ModelQuotaInfo(
                        id = modelKey,
                        displayName = displayName,
                        family = family,
                        window = QuotaWindow.FIVE_HOUR,
                        remainingFraction = remainingFraction.coerceIn(0.0, 1.0),
                        resetTimeIso = resetTimeIso,
                        resetTimeEpochSeconds = resetEpochSeconds,
                        isExhausted = remainingFraction <= 0.0
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    /**
     * 1:1 对齐插件 normalizeQuotaSummaryResponse 算法
     */
    private fun parseQuotaSummaryGroups(rawJson: String): List<QuotaGroup> {
        val result = mutableListOf<QuotaGroup>()
        try {
            val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return emptyList()
            val payload = root["response"] as? JsonObject ?: root
            val groupsArray = payload["groups"] as? JsonArray ?: return emptyList()

            for (groupElem in groupsArray) {
                val groupObj = groupElem as? JsonObject ?: continue
                val rawDisplayName = groupObj["displayName"]?.jsonPrimitive?.contentOrNull ?: ""
                val rawDescription = groupObj["description"]?.jsonPrimitive?.contentOrNull ?: ""

                // 鲁棒的模型族分类检测 (融合 displayName, description 与 bucketId)
                val bucketsArray = groupObj["buckets"] as? JsonArray ?: continue
                val bucketIdsText = bucketsArray.joinToString(" ") { b ->
                    val bObj = b as? JsonObject
                    bObj?.get("bucketId")?.jsonPrimitive?.contentOrNull
                        ?: bObj?.get("bucket_id")?.jsonPrimitive?.contentOrNull
                        ?: ""
                }

                val fullKey = "$rawDisplayName $rawDescription $bucketIdsText".lowercase()
                val family = when {
                    fullKey.contains("gemini") -> "gemini"
                    fullKey.contains("claude") || fullKey.contains("anthropic") || fullKey.contains("gpt") || fullKey.contains("3p") -> "claude"
                    else -> continue
                }
                val label = if (family == "claude") "Claude" else "Gemini"

                val buckets = mutableListOf<ModelQuotaInfo>()

                for (bucketElem in bucketsArray) {
                    val bucketObj = bucketElem as? JsonObject ?: continue
                    val windowRaw = bucketObj["window"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    val bucketId = bucketObj["bucketId"]?.jsonPrimitive?.contentOrNull
                        ?: bucketObj["bucket_id"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val bucketKey = "$windowRaw $bucketId".lowercase()

                    val window = when {
                        bucketKey.contains("weekly") -> QuotaWindow.WEEKLY
                        bucketKey.contains("5h") || bucketKey.contains("fivehour") -> QuotaWindow.FIVE_HOUR
                        else -> QuotaWindow.FIVE_HOUR
                    }

                    val remainingFraction = bucketObj["remainingFraction"]?.jsonPrimitive?.doubleOrNull
                        ?: bucketObj["remaining_fraction"]?.jsonPrimitive?.doubleOrNull
                        ?: 1.0

                    val resetTimeIso = bucketObj["resetTime"]?.jsonPrimitive?.contentOrNull
                        ?: bucketObj["reset_time"]?.jsonPrimitive?.contentOrNull
                    val resetEpochSeconds = resetTimeIso?.let { parseIsoToEpochSeconds(it) }

                    buckets.add(
                        ModelQuotaInfo(
                            id = "$family-${window.name.lowercase()}",
                            displayName = if (window == QuotaWindow.FIVE_HOUR) "五小时额度" else "周额度",
                            family = family,
                            window = window,
                            remainingFraction = remainingFraction.coerceIn(0.0, 1.0),
                            resetTimeIso = resetTimeIso,
                            resetTimeEpochSeconds = resetEpochSeconds,
                            isExhausted = remainingFraction <= 0.0
                        )
                    )
                }

                // 严格排序：5小时额度在前 (0)，周额度在后 (1)
                buckets.sortBy { if (it.window == QuotaWindow.FIVE_HOUR) 0 else 1 }

                if (buckets.isNotEmpty()) {
                    result.add(
                        QuotaGroup(
                            family = family,
                            label = label,
                            displayName = rawDisplayName.takeIf { it.isNotBlank() } ?: "$label 模型",
                            buckets = buckets
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        // 严格排序模型族：Gemini 模型族置顶 (0)，Claude 模型族在后 (1)
        result.sortBy { if (it.family == "gemini") 0 else 1 }
        return result
    }

    private fun parseIsoToEpochSeconds(isoString: String): Long? {
        return try {
            Instant.parse(isoString.trim()).epochSecond
        } catch (_: Exception) {
            null
        }
    }
}
