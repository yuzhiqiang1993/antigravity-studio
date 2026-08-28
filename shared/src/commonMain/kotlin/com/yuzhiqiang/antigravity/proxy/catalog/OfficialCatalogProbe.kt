package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.network.PlatformNetworkConfig
import io.ktor.client.HttpClient
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
import java.util.concurrent.TimeUnit

/**
 * 官方模型探针。
 * 基于当前有效账号凭据与 Token 自动续期，直接请求 Google 官方 CloudCode PA 远端端点，
 * 实时拉取并解析 fetchAvailableModels 官方原生协议报文与能力元数据。
 */
object OfficialCatalogProbe {

    private val CLOUD_CODE_HOSTS = listOf(
        "https://cloudcode-pa.googleapis.com",
        "https://daily-cloudcode-pa.googleapis.com"
    )
    private const val DEFAULT_PROJECT_ID = "cloudaicompanion-enterprise"
    private const val USER_AGENT = "Antigravity/4.1.29 Chrome/132.0.6834.160 Electron/39.2.3"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
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

    var rawOfficialCatalogBody: String? = null
        private set

    var lastParsedModels: List<OfficialCatalogModel> = emptyList()
        private set

    fun clearRawOfficialCatalog() {
        rawOfficialCatalogBody = null
        lastParsedModels = emptyList()
    }

    fun setRawOfficialCatalog(body: String) {
        if (body.isNotBlank()) {
            rawOfficialCatalogBody = body
            val parsed = parseOfficialCatalogModels(body)
            if (parsed.isNotEmpty()) {
                lastParsedModels = parsed
            }
        }
    }

    /**
     * 基于账号凭据直接请求 Google 官方 CloudCode PA 服务获取官方模型列表：
     * 1. 自动检查 Token 状态，若过期或遇到 401 自动触发 tokenRefreshCallback 换取最新 Token
     * 2. 依次请求 /v1internal:loadCodeAssist（提取精准 projectId）与 /v1internal:fetchAvailableModels
     * 3. 彻底剔除三方自定义模型，确保展示纯净官方数据
     */
    suspend fun fetchOfficialModels(
        account: AccountInfo?,
        tokenRefreshCallback: (suspend (refreshToken: String) -> Result<String>)? = null,
        excludedModelIds: Set<String> = emptySet()
    ): Result<List<OfficialCatalogModel>> = withContext(Dispatchers.IO) {
        if (account == null) {
            return@withContext Result.failure(IllegalStateException("请先在「账号配额」页添加或激活有效账号"))
        }

        if (account.tokens.accessToken.isBlank() && account.tokens.refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("当前账号暂无可用认证凭据"))
        }

        var currentToken = account.tokens.accessToken
        var attemptedRefresh = false

        // 预检：如果 token 为空或即将过期（60秒内），优先尝试刷新
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
            return@withContext Result.failure(IllegalStateException("无法获取有效的 Access Token，请检查账号状态"))
        }

        while (true) {
            val result = executeFetch(currentToken, excludedModelIds)
            if (result.isSuccess) {
                return@withContext result
            }

            val error = result.exceptionOrNull()
            val isUnauthorized = error?.message?.contains("401") == true ||
                    error?.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true

            // 遇到 401 且未刷新过时，自动刷新一次重试
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

        @Suppress("UNREACHABLE_CODE")
        Result.failure(IllegalStateException("拉取官方模型失败"))
    }

    private suspend fun executeFetch(
        token: String,
        excludedModelIds: Set<String>
    ): Result<List<OfficialCatalogModel>> {
        var projectId = DEFAULT_PROJECT_ID
        var lastError: Exception? = null

        // 1. 尝试 loadCodeAssist 获取 projectId
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
                root?.get("cloudaicompanionProject")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                    projectId = it
                }
                break
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. 请求 fetchAvailableModels 获取官方模型列表
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

                if (response.status.value in 200..299) {
                    val responseBody = response.bodyAsText()
                    rawOfficialCatalogBody = responseBody
                    val rawModels = parseOfficialCatalogModels(responseBody)
                    val models = rawModels.filterNot { m ->
                        m.id in excludedModelIds || m.displayName in excludedModelIds
                    }
                    if (models.isNotEmpty()) {
                        lastParsedModels = models
                        return Result.success(models)
                    }
                } else {
                    lastError = IllegalStateException("官方接口返回 HTTP ${response.status.value}")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        return Result.failure(lastError ?: IllegalStateException("拉取官方模型失败"))
    }

    /**
     * 获取格式化好的 Raw JSON 数据
     */
    fun getFormattedRawJson(): String {
        val raw = rawOfficialCatalogBody ?: return if (com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN) "(暂无原始数据)" else "(No raw data available)"
        return try {
            val element = json.parseToJsonElement(raw)
            json.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * 获取格式化好的 Modified 解析后数据（1:1 对标 agy-byok 的 prepare_model_catalog_response 最终注入报文）
     */
    fun getFormattedModifiedJson(
        config: com.yuzhiqiang.antigravity.domain.model.AppConfig? = null,
        proxyPort: Int = 8045
    ): String {
        val raw = rawOfficialCatalogBody ?: return if (com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN) "(暂无原始数据，请先点击「刷新」拉取官方模型)" else "(No raw data available, please click Refresh to fetch official models first)"
        return try {
            val parsedRoot = json.parseToJsonElement(raw) as? JsonObject
                ?: return raw
            val root = JsonObject(parsedRoot - "error")
            if (config == null) {
                return json.encodeToString(JsonElement.serializer(), root)
            }
            // 1. 过滤已禁用官方模型
            val filtered = com.yuzhiqiang.antigravity.proxy.server.CatalogInjector.removeDisabledOfficialModels(
                root,
                config.disabledOfficialModels
            )
            // 2. 注入官方模型压缩策略 (Checkpointer 实验)
            val overridden = com.yuzhiqiang.antigravity.proxy.server.CatalogInjector.applyOfficialCompressionPolicies(
                filtered,
                config.modelCompressionPolicies
            )
            // 3. 注入自定义虚拟模型与上游
            val responseJson = com.yuzhiqiang.antigravity.proxy.server.CatalogInjector.injectCustomModels(
                overridden,
                config
            )
            // 4. 重写官方 URL 为本地代理端口
            val proxyTarget = "http://127.0.0.1:$proxyPort"
            val rewritten = responseJson.toString()
                .replace("https://daily-cloudcode-pa.googleapis.com", proxyTarget)
                .replace("https://cloudcode-pa.googleapis.com", proxyTarget)

            val element = json.parseToJsonElement(rewritten)
            json.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            "生成修改后 JSON 失败: ${e.message ?: "未知错误"}"
        }
    }

    /**
     * 1:1 对标 Rust 版 parse_official_catalog_models
     */
    fun parseOfficialCatalogModels(body: String): List<OfficialCatalogModel> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val responseObj = root["response"]?.jsonObject ?: root
            val modelsObj = responseObj["models"]?.jsonObject ?: return emptyList()

            // 1. 解析过时模型映射 (deprecatedModelIds)
            val deprecatedMap = parseDeprecatedModelIds(responseObj)

            // 2. 解析 Agent 模型排序 (agentModelSorts)
            val agentSortOrderMap = parseAgentModelOrder(responseObj)

            // 3. 解析模型角色 (clientModelRoles / defaultAgentModelId)
            val (officialRoles, hasRoleMetadata) = parseOfficialModelRoles(responseObj, agentSortOrderMap)

            val result = mutableListOf<OfficialCatalogModel>()

            for ((modelId, value) in modelsObj) {
                val item = value.jsonObject
                val displayName = item["displayName"]?.jsonPrimitive?.contentOrNull
                    ?: item["label"]?.jsonPrimitive?.contentOrNull
                    ?: modelId

                val maxTokens = item["maxTokens"]?.jsonPrimitive?.longOrNull
                val contextWindow = listOfNotNull(
                    item["maxContextWindow"]?.jsonPrimitive?.longOrNull,
                    item["max_context_window"]?.jsonPrimitive?.longOrNull,
                    item["contextWindow"]?.jsonPrimitive?.longOrNull,
                    item["context_window"]?.jsonPrimitive?.longOrNull,
                    maxTokens
                ).maxOrNull()

                val inputTokenLimit = listOfNotNull(
                    item["maxInputTokens"]?.jsonPrimitive?.longOrNull,
                    item["max_input_tokens"]?.jsonPrimitive?.longOrNull,
                    item["inputTokenLimit"]?.jsonPrimitive?.longOrNull,
                    item["input_token_limit"]?.jsonPrimitive?.longOrNull,
                    contextWindow,
                    maxTokens
                ).maxOrNull()

                val outputTokenLimit = item["outputTokenLimit"]?.jsonPrimitive?.longOrNull
                    ?: item["output_token_limit"]?.jsonPrimitive?.longOrNull
                    ?: item["maxOutputTokens"]?.jsonPrimitive?.longOrNull

                val supportsVision = item["supportsImages"]?.jsonPrimitive?.booleanOrNull
                    ?: item["supportsVision"]?.jsonPrimitive?.booleanOrNull
                    ?: item["supportsImageInput"]?.jsonPrimitive?.booleanOrNull
                    ?: true

                val supportsTools = item["supportsTools"]?.jsonPrimitive?.booleanOrNull ?: true
                val supportsThinking = item["supportsThinking"]?.jsonPrimitive?.booleanOrNull
                    ?: item["supportsReasoning"]?.jsonPrimitive?.booleanOrNull
                    ?: displayName.contains("thinking", ignoreCase = true)

                val isRecommended = item["recommended"]?.jsonPrimitive?.booleanOrNull ?: true
                val isDeprecated = deprecatedMap.containsKey(modelId)
                val replacementModelId = deprecatedMap[modelId]

                val roles = officialRoles[modelId] ?: if (hasRoleMetadata) emptyList() else listOf("agent")
                val sortOrder = agentSortOrderMap[modelId]

                result.add(
                    OfficialCatalogModel(
                        id = modelId,
                        displayName = displayName,
                        contextWindow = contextWindow,
                        maxTokens = maxTokens,
                        inputTokenLimit = inputTokenLimit,
                        outputTokenLimit = outputTokenLimit,
                        supportsVision = supportsVision,
                        supportsTools = supportsTools,
                        supportsReasoning = supportsThinking,
                        isRecommended = isRecommended,
                        isDeprecated = isDeprecated,
                        replacementModelId = replacementModelId,
                        agentSortOrder = sortOrder,
                        roles = roles
                    )
                )
            }

            // 按照官方排序：先排 agentSortOrder，再排 ID
            if (agentSortOrderMap.isNotEmpty()) {
                result.sortedWith(
                    compareBy<OfficialCatalogModel> { it.agentSortOrder ?: Long.MAX_VALUE }
                        .thenBy { it.id }
                )
            } else {
                result.sortedBy { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDeprecatedModelIds(response: JsonObject): Map<String, String> {
        val deprecatedObj = response["deprecatedModelIds"]?.jsonObject ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        for ((oldId, valElem) in deprecatedObj) {
            val newId = valElem.jsonObject["newModelId"]?.jsonPrimitive?.contentOrNull
            if (!oldId.isNullOrBlank() && !newId.isNullOrBlank()) {
                map[oldId] = newId
            }
        }
        return map
    }

    private fun parseAgentModelOrder(response: JsonObject): Map<String, Long> {
        val orderMap = mutableMapOf<String, Long>()
        val sorts = response["agentModelSorts"]?.jsonArray ?: return orderMap

        for (sort in sorts) {
            val groups = sort.jsonObject["groups"]?.jsonArray ?: continue
            for (group in groups) {
                val modelIds = group.jsonObject["modelIds"]?.jsonArray ?: continue
                for (elem in modelIds) {
                    val modelId = elem.jsonPrimitive.contentOrNull ?: continue
                    if (!orderMap.containsKey(modelId)) {
                        orderMap[modelId] = orderMap.size.toLong()
                    }
                }
            }
        }
        return orderMap
    }

    private fun parseOfficialModelRoles(
        response: JsonObject,
        agentSortOrder: Map<String, Long>
    ): Pair<Map<String, List<String>>, Boolean> {
        val rolesMap = mutableMapOf<String, MutableSet<String>>()
        var hasMetadata = false

        for (modelId in agentSortOrder.keys) {
            hasMetadata = true
            rolesMap.getOrPut(modelId) { mutableSetOf() }.add("agent")
        }

        val defaultAgent = response["defaultAgentModelId"]?.jsonPrimitive?.contentOrNull
        if (!defaultAgent.isNullOrBlank()) {
            hasMetadata = true
            rolesMap.getOrPut(defaultAgent) { mutableSetOf() }.add("agent")
        }

        val roleFields = listOf(
            "commandModelIds" to "command",
            "tabModelIds" to "tab",
            "imageGenerationModelIds" to "image_generation",
            "mqueryModelIds" to "mquery",
            "webSearchModelIds" to "web_search",
            "commitMessageModelIds" to "commit_message",
            "audioTranscriptionModelIds" to "audio_transcription"
        )

        for ((field, roleName) in roleFields) {
            val array = response[field]?.jsonArray ?: continue
            for (elem in array) {
                val mId = elem.jsonPrimitive.contentOrNull ?: continue
                if (mId.isNotBlank()) {
                    hasMetadata = true
                    rolesMap.getOrPut(mId) { mutableSetOf() }.add(roleName)
                }
            }
        }

        return rolesMap.mapValues { it.value.toList() } to hasMetadata
    }
}
