package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * 官方模型探针，完全 1:1 复刻 agy-byok 的 fetch_official_models_catalog 与 parser 实现。
 * 动态探测本地 Antigravity IDE / App 运行中的语言服务，
 * 实时拉取并解析 GetAvailableModels 原始协议与元数据（无任何写死配置）。
 */
object OfficialCatalogProbe {

    private data class LanguageServerCandidate(
        val pid: Long,
        val source: String,
        val csrf: String,
        val port: Int
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
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
     * 动态探测并直连本地 Antigravity IDE / App 语言服务纯接口：
     * 1. 严格基于系统进程动态发现运行中的 language_server 实例（零硬编码端口）
     * 2. 直连官方 RPC 接口 /exa.language_server_pb.LanguageServerService/GetAvailableModels
     * 3. 自动排除自定义 Provider / 虚拟模型，杜绝三方污染
     */
    suspend fun fetchOfficialModels(
        excludedModelIds: Set<String> = emptySet()
    ): Result<List<OfficialCatalogModel>> = withContext(Dispatchers.IO) {
        val candidates = discoverLanguageServerCandidates()
        if (candidates.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未找到本地运行中的 Antigravity IDE 或 App 官方语言服务进程"))
        }

        val trustAllSsl = createInsecureSslSocketFactory()
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val url = URL("https://127.0.0.1:${candidate.port}/exa.language_server_pb.LanguageServerService/GetAvailableModels")
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    sslSocketFactory = trustAllSsl
                    hostnameVerifier = HostnameVerifier { _, _ -> true }
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Codeium-Csrf-Token", candidate.csrf)
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write("{}".toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseBytes = connection.inputStream.use { it.readBytes() }
                    val responseBody = responseBytes.toString(Charsets.UTF_8)
                    rawOfficialCatalogBody = responseBody
                    val rawModels = parseOfficialCatalogModels(responseBody)
                    // 彻底剔除三方自定义模型，确保展示纯净官方数据
                    val models = rawModels.filterNot { m ->
                        m.id in excludedModelIds || m.displayName in excludedModelIds
                    }
                    lastParsedModels = models
                    return@withContext Result.success(models)
                } else {
                    lastException = IllegalStateException("官方语言服务返回 HTTP $responseCode")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        return@withContext Result.failure(lastException ?: IllegalStateException("拉取官方模型失败"))
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
                val contextWindow = item["contextWindow"]?.jsonPrimitive?.longOrNull
                    ?: item["context_window"]?.jsonPrimitive?.longOrNull
                    ?: maxTokens

                val inputTokenLimit = item["inputTokenLimit"]?.jsonPrimitive?.longOrNull
                    ?: item["input_token_limit"]?.jsonPrimitive?.longOrNull
                    ?: maxTokens

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

    private fun discoverLanguageServerCandidates(): List<LanguageServerCandidate> {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("mac") || os.contains("linux") -> discoverUnixCandidates()
            os.contains("win") -> discoverWindowsCandidates()
            else -> emptyList()
        }
    }

    private fun discoverUnixCandidates(): List<LanguageServerCandidate> {
        val candidates = mutableListOf<LanguageServerCandidate>()
        try {
            val process = ProcessBuilder("/bin/ps", "-axo", "pid=,command=").start()
            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            process.waitFor()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || !trimmed.contains("language_server")) continue
                val separator = trimmed.indexOfFirst { it.isWhitespace() }
                if (separator <= 0) continue
                val pid = trimmed.substring(0, separator).trim().toLongOrNull() ?: continue
                val command = trimmed.substring(separator).trim()

                val csrf = extractFlagValue(command, "--csrf_token")
                if (!csrf.isNullOrBlank()) {
                    val source = extractFlagValue(command, "--subclient_type") ?: "ide"
                    val httpsPort = extractFlagValue(command, "--https_server_port")?.toIntOrNull()
                    val ports = mutableListOf<Int>()
                    if (httpsPort != null && httpsPort > 0) {
                        ports.add(httpsPort)
                    }
                    if (ports.isEmpty()) {
                        ports.addAll(getListeningPortsByLsof(pid))
                    }
                    for (port in ports) {
                        if (port > 0 && candidates.none { it.port == port }) {
                            candidates.add(LanguageServerCandidate(pid, source, csrf, port))
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return candidates
    }

    private fun discoverWindowsCandidates(): List<LanguageServerCandidate> {
        val candidates = mutableListOf<LanguageServerCandidate>()
        try {
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                "Get-CimInstance Win32_Process -Filter \"Name LIKE '%language_server%'\" | ForEach-Object { \$_.ProcessId.ToString() + ' ' + \$_.CommandLine }"
            ).start()
            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            process.waitFor()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                if (parts.size < 2) continue
                val pid = parts[0].toLongOrNull() ?: continue
                val command = parts[1]

                val csrf = extractFlagValue(command, "--csrf_token") ?: continue
                val source = extractFlagValue(command, "--subclient_type") ?: "ide"
                val httpsPort = extractFlagValue(command, "--https_server_port")?.toIntOrNull()
                val ports = mutableListOf<Int>()
                if (httpsPort != null && httpsPort > 0) {
                    ports.add(httpsPort)
                }
                if (ports.isEmpty()) {
                    ports.addAll(getListeningPortsWindows(pid))
                }

                for (port in ports) {
                    if (port > 0 && candidates.none { it.port == port }) {
                        candidates.add(LanguageServerCandidate(pid, source, csrf, port))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return candidates
    }

    private fun getListeningPortsWindows(pid: Long): List<Int> {
        val ports = mutableListOf<Int>()
        try {
            val process = ProcessBuilder("netstat", "-ano", "-p", "tcp").start()
            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            process.waitFor()
            for (line in lines) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("TCP", ignoreCase = true)) continue
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 5 && parts[3].equals("LISTENING", ignoreCase = true)) {
                    val linePid = parts[4].toLongOrNull()
                    if (linePid == pid) {
                        val localAddress = parts[1]
                        val port = localAddress.substringAfterLast(':', "").toIntOrNull()
                        if (port != null && port > 0) {
                            ports.add(port)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ports
    }

    private fun getListeningPortsByLsof(pid: Long): List<Int> {
        val ports = mutableListOf<Int>()
        try {
            val process = ProcessBuilder(
                "/usr/sbin/lsof",
                "-nP",
                "-a",
                "-p", pid.toString(),
                "-iTCP",
                "-sTCP:LISTEN",
                "-Fn"
            ).start()
            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            process.waitFor()

            for (line in lines) {
                if (line.startsWith("n*:")) {
                    val port = line.removePrefix("n*:").toIntOrNull()
                    if (port != null && port > 0) ports.add(port)
                } else if (line.startsWith("n127.0.0.1:")) {
                    val port = line.removePrefix("n127.0.0.1:").toIntOrNull()
                    if (port != null && port > 0) ports.add(port)
                }
            }
        } catch (_: Exception) {
        }
        return ports
    }

    private fun extractFlagValue(command: String, flag: String): String? {
        val prefix = "$flag="
        val parts = command.split(Regex("\\s+"))
        for (i in parts.indices) {
            val part = parts[i]
            if (part == flag && i + 1 < parts.size) {
                return parts[i + 1].trim('"', '\'')
            }
            if (part.startsWith(prefix)) {
                return part.removePrefix(prefix).trim('"', '\'')
            }
        }
        return null
    }

    private fun createInsecureSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }
}
