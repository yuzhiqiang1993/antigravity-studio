package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.*
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

    var lastParsedSnapshot: OfficialCatalogSnapshot = OfficialCatalogSnapshot()
        private set

    fun clearRawOfficialCatalog() {
        rawOfficialCatalogBody = null
        lastParsedModels = emptyList()
        lastParsedSnapshot = OfficialCatalogSnapshot()
        ModelIdentityRegistryHolder.updateOfficialModels(emptyList())
    }

    fun setRawOfficialCatalog(body: String) {
        if (body.isBlank()) return
        rawOfficialCatalogBody = body
        val snapshot = parseOfficialCatalogSnapshot(body)
        lastParsedSnapshot = snapshot
        lastParsedModels = snapshot.models
        ModelIdentityRegistryHolder.updateOfficialModels(snapshot.models)
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
                    val snapshot = parseOfficialCatalogSnapshot(responseBody)
                    val excludedCatalogKeys = excludedModelIds
                        .map(ModelIdentity::normalizeModelId)
                        .toSet()
                    val models = snapshot.models.filterNot { model ->
                        ModelIdentity.normalizeModelId(model.catalogModelId) in excludedCatalogKeys
                    }
                    if (snapshot.models.isNotEmpty()) {
                        lastParsedSnapshot = snapshot.copy(models = models)
                        lastParsedModels = models
                        ModelIdentityRegistryHolder.updateOfficialModels(models)
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
        val raw = rawOfficialCatalogBody
            ?: return if (com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN) "(暂无原始数据)" else "(No raw data available)"
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
        val raw = rawOfficialCatalogBody
            ?: return if (com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN) "(暂无原始数据，请先点击「刷新」拉取官方模型)" else "(No raw data available, please click Refresh to fetch official models first)"
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
                config.disabledOfficialCatalogModelIds
            )
            // 2. 注入官方模型压缩策略 (Checkpointer 实验)
            val overridden = com.yuzhiqiang.antigravity.proxy.server.CatalogInjector.applyOfficialCompressionPolicies(
                filtered,
                config.compressionPolicyAssignments
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

    fun parseOfficialCatalogModels(body: String): List<OfficialCatalogModel> =
        parseOfficialCatalogSnapshot(body).models

    fun parseOfficialCatalogSnapshot(body: String): OfficialCatalogSnapshot {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return OfficialCatalogSnapshot()
            val response = root["response"] as? JsonObject ?: root
            val modelsObject = response["models"] as? JsonObject ?: return OfficialCatalogSnapshot()
            val deprecated = parseDeprecatedModelIds(response)
            val agentOrder = parseAgentModelOrder(response)
            val (roleByModel, hasRoleMetadata) = parseOfficialModelRoles(response, agentOrder)
            val tierGroups = parseTierGroups(response["tieredModelIds"])
            val tierGroupsByModel = buildMap<String, MutableList<String>> {
                tierGroups.forEach { (groupId, modelIds) ->
                    modelIds.forEach { modelId -> getOrPut(modelId) { mutableListOf() }.add(groupId) }
                }
            }
            val replacements = deprecated.toMutableMap()
            val models = modelsObject.mapNotNull { (rawCatalogModelId, value) ->
                val item = value as? JsonObject ?: return@mapNotNull null
                val catalogModelId = ModelIdentity.normalizeModelId(rawCatalogModelId)
                if (catalogModelId.isBlank()) return@mapNotNull null
                val runtimeModelId = item.string("model")?.let(ModelIdentity::normalizeModelId)
                val providerModelId = item.string("vertexModelId")
                val canonicalModelId = item.string("canonicalModelId") ?: providerModelId
                val baseModelId = item.string("baseModelId")
                val version = item.string("version")
                val displayName = item.string("displayName", "label") ?: catalogModelId
                val catalogApiProvider = item.string("apiProvider")
                val providerVendor = item.string("modelProvider")
                val thinkingBudget = item.int("thinkingBudget")
                val minThinkingBudget = item.int("minThinkingBudget")
                val reasoningLevel = parseReasoningLevel(item.string("thinkingLevel", "reasoningLevel"))
                val reasoningProfile = if (
                    thinkingBudget != null || minThinkingBudget != null || reasoningLevel != null
                ) {
                    ReasoningProfile(
                        level = reasoningLevel,
                        budgetTokens = thinkingBudget,
                        minBudgetTokens = minThinkingBudget,
                        source = ModelIdentitySource.OFFICIAL_PROVIDER_MODEL
                    )
                } else {
                    null
                }
                val maxTokens = item.long("maxTokens")
                val contextWindow = listOfNotNull(
                    item.long("maxContextWindow", "max_context_window", "contextWindow", "context_window"),
                    maxTokens
                ).maxOrNull()
                val inputTokenLimit = listOfNotNull(
                    item.long("maxInputTokens", "max_input_tokens", "inputTokenLimit", "input_token_limit"),
                    contextWindow,
                    maxTokens
                ).maxOrNull()
                val outputTokenLimit = item.long(
                    "outputTokenLimit",
                    "output_token_limit",
                    "maxOutputTokens"
                )
                val itemReplacement = (parseReplacement(item["replacement"])
                    ?: item.string("replacementModelId"))
                    ?.let(ModelIdentity::normalizeModelId)
                val replacementCatalogModelId = itemReplacement ?: deprecated[catalogModelId]
                if (!replacementCatalogModelId.isNullOrBlank()) {
                    replacements[catalogModelId] = replacementCatalogModelId
                }
                val directRoles = parseStringValues(item["roles"])
                val roles = (roleByModel[catalogModelId].orEmpty() + directRoles)
                    .distinct()
                    .ifEmpty { if (hasRoleMetadata) emptyList() else listOf("agent") }
                val quota = (item["quotaInfo"] as? JsonObject)?.let { quotaInfo ->
                    OfficialModelQuotaInfo(
                        remainingFraction = quotaInfo.double("remainingFraction", "remaining_fraction"),
                        resetTime = quotaInfo.string("resetTime", "reset_time")
                    )
                }
                val tags = parseTags(item)
                OfficialCatalogModel(
                    catalogModelId = catalogModelId,
                    runtimeModelId = runtimeModelId,
                    providerModelId = providerModelId,
                    canonicalModelId = canonicalModelId,
                    baseModelId = baseModelId,
                    version = version,
                    displayName = displayName,
                    catalogApiProvider = catalogApiProvider,
                    providerVendor = providerVendor,
                    reasoningProfile = reasoningProfile,
                    identityResolution = ModelIdentityResolution(
                        status = if (providerModelId != null || canonicalModelId != null) {
                            ModelIdentityStatus.RESOLVED
                        } else {
                            ModelIdentityStatus.UNRESOLVED
                        },
                        source = ModelIdentitySource.OFFICIAL_PROVIDER_MODEL,
                        confidence = if (providerModelId != null || canonicalModelId != null) {
                            ModelIdentityConfidence.EXACT
                        } else {
                            ModelIdentityConfidence.UNKNOWN
                        }
                    ),
                    contextWindow = contextWindow,
                    maxTokens = maxTokens,
                    inputTokenLimit = inputTokenLimit,
                    outputTokenLimit = outputTokenLimit,
                    supportsVision = item.bool("supportsImages", "supportsVision", "supportsImageInput") ?: true,
                    supportsTools = item.bool("supportsTools") ?: true,
                    supportsReasoning = item.bool("supportsThinking", "supportsReasoning")
                        ?: (reasoningProfile != null),
                    isRecommended = item.bool("recommended") ?: true,
                    isDeprecated = replacementCatalogModelId != null,
                    replacementCatalogModelId = replacementCatalogModelId,
                    agentSortOrder = agentOrder[catalogModelId],
                    roles = roles,
                    tierGroupIds = tierGroupsByModel[catalogModelId].orEmpty().distinct(),
                    quotaInfo = quota,
                    tags = tags,
                    rawExtra = item
                )
            }.sortedWith(
                compareBy<OfficialCatalogModel> { it.agentSortOrder ?: Long.MAX_VALUE }
                    .thenBy(OfficialCatalogModel::catalogModelId)
            )
            val roleModelIds = models
                .flatMap { model -> model.roles.map { role -> role to model.catalogModelId } }
                .groupBy({ it.first }, { it.second })
            OfficialCatalogSnapshot(
                models = models,
                replacements = replacements.map { (oldId, newId) ->
                    OfficialModelReplacement(oldId, newId)
                },
                tierGroups = tierGroups,
                defaultAgentModelId = response.string("defaultAgentModelId")
                    ?.let(ModelIdentity::normalizeModelId),
                roleModelIds = roleModelIds,
                fetchedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            OfficialCatalogSnapshot()
        }
    }

    private fun parseDeprecatedModelIds(response: JsonObject): Map<String, String> {
        val deprecated = response["deprecatedModelIds"] as? JsonObject ?: return emptyMap()
        return deprecated.mapNotNull { (oldId, value) ->
            val replacement = parseReplacement(value) ?: return@mapNotNull null
            val normalizedOldId = ModelIdentity.normalizeModelId(oldId)
            val normalizedReplacement = ModelIdentity.normalizeModelId(replacement)
            if (normalizedOldId.isBlank() || normalizedReplacement.isBlank()) null
            else normalizedOldId to normalizedReplacement
        }.toMap()
    }

    private fun parseAgentModelOrder(response: JsonObject): Map<String, Long> {
        val orderMap = mutableMapOf<String, Long>()
        val sorts = response["agentModelSorts"] as? JsonArray ?: return orderMap

        for (sort in sorts) {
            val sortObject = sort as? JsonObject ?: continue
            val groups = sortObject["groups"] as? JsonArray ?: continue
            for (group in groups) {
                val groupObject = group as? JsonObject ?: continue
                val modelIds = groupObject["modelIds"] as? JsonArray ?: continue
                for (elem in modelIds) {
                    val modelId = elem.jsonPrimitive.contentOrNull
                        ?.let(ModelIdentity::normalizeModelId)
                        ?.takeIf(String::isNotBlank)
                        ?: continue
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
        val rolesByModel = mutableMapOf<String, MutableSet<String>>()
        val roleFields = listOf(
            "commandModelIds" to "command",
            "tabModelIds" to "tab",
            "imageGenerationModelIds" to "image_generation",
            "mqueryModelIds" to "mquery",
            "webSearchModelIds" to "web_search",
            "commitMessageModelIds" to "commit_message",
            "audioTranscriptionModelIds" to "audio_transcription"
        )
        var hasMetadata = agentSortOrder.isNotEmpty() ||
                "agentModelSorts" in response ||
                "defaultAgentModelId" in response ||
                "clientModelRoles" in response ||
                roleFields.any { (field, _) -> field in response }

        fun add(role: String, modelIds: List<String>) {
            modelIds.forEach { rawId ->
                val modelId = ModelIdentity.normalizeModelId(rawId)
                if (modelId.isNotBlank()) {
                    hasMetadata = true
                    rolesByModel.getOrPut(modelId) { linkedSetOf() }.add(role)
                }
            }
        }

        add("agent", agentSortOrder.keys.toList())
        response.string("defaultAgentModelId")?.let { add("agent", listOf(it)) }
        roleFields.forEach { (field, role) -> add(role, parseStringValues(response[field])) }

        when (val clientRoles = response["clientModelRoles"]) {
            is JsonObject -> clientRoles.forEach { (role, modelIds) ->
                add(role.lowercase(), parseStringValues(modelIds))
            }

            is JsonArray -> clientRoles.forEach { element ->
                val roleObject = element as? JsonObject ?: return@forEach
                val role = roleObject.string("role", "name", "id")?.lowercase() ?: return@forEach
                add(role, parseStringValues(roleObject["modelIds"] ?: roleObject["models"] ?: roleObject["ids"]))
            }

            else -> Unit
        }
        return rolesByModel.mapValues { (_, roles) -> roles.toList() } to hasMetadata
    }

    private fun parseTierGroups(value: JsonElement?): Map<String, List<String>> = when (value) {
        is JsonObject -> value.mapValues { (_, modelIds) ->
            parseStringValues(modelIds)
                .map(ModelIdentity::normalizeModelId)
                .filter(String::isNotBlank)
                .distinct()
        }

        is JsonArray -> value.mapNotNull { element ->
            val group = element as? JsonObject ?: return@mapNotNull null
            val groupId = group.string("id", "tierId", "name") ?: return@mapNotNull null
            val modelIds = parseStringValues(group["modelIds"] ?: group["models"] ?: group["ids"])
                .map(ModelIdentity::normalizeModelId)
                .filter(String::isNotBlank)
                .distinct()
            groupId to modelIds
        }.toMap()

        else -> emptyMap()
    }

    private fun parseTags(item: JsonObject): OfficialModelTags? {
        val tag = item["tag"]
        val title = when (tag) {
            is JsonPrimitive -> tag.contentOrNull
            is JsonObject -> tag.string("title", "name", "label")
            else -> null
        } ?: item.string("tagTitle")
        val description = (tag as? JsonObject)?.string("description") ?: item.string("tagDescription")
        return if (title == null && description == null) null else OfficialModelTags(title, description)
    }

    private fun parseReplacement(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.string("newModelId", "replacementModelId", "modelId", "replacement")
        else -> null
    }

    private fun parseStringValues(value: JsonElement?): List<String> = when (value) {
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        is JsonArray -> value.flatMap(::parseStringValues)
        is JsonObject -> {
            val explicit = value["modelIds"] ?: value["modelId"] ?: value["models"] ?: value["ids"]
            if (explicit != null) parseStringValues(explicit) else emptyList()
        }

        else -> emptyList()
    }

    private fun parseReasoningLevel(value: String?): ReasoningLevel? {
        val normalized = value?.trim()?.lowercase()?.replace('-', '_') ?: return null
        return ReasoningLevel.entries.firstOrNull { level -> level.name.lowercase() == normalized }
    }

    private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun JsonObject.long(vararg names: String): Long? = names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.longOrNull
    }

    private fun JsonObject.int(vararg names: String): Int? = long(*names)
        ?.takeIf { value -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
        ?.toInt()

    private fun JsonObject.double(vararg names: String): Double? = names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.doubleOrNull
    }

    private fun JsonObject.bool(vararg names: String): Boolean? = names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.booleanOrNull
    }
}
