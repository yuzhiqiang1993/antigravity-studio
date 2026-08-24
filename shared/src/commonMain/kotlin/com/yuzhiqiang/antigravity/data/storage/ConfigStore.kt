package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission

class ConfigStore(
    private val customRootDir: File? = null
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val canonicalConfigFile: File by lazy { resolveCanonicalConfigFile() }

    private val configuredPathError: String? by lazy {
        val configured = (System.getenv("ANTIGRAVITY_STUDIO_CONFIG_PATH")
            ?: System.getenv("AGY_STUDIO_CONFIG_PATH"))?.trim().orEmpty()
        if (configured.isNotEmpty() && !File(configured).isAbsolute) {
            "ANTIGRAVITY_STUDIO_CONFIG_PATH 必须是绝对路径"
        } else {
            null
        }
    }

    private val rootDir: File
        get() = canonicalConfigFile.parentFile ?: File(System.getProperty("user.home"))

    val configFile: File
        get() = canonicalConfigFile

    private val studioSettingsFile: File by lazy {
        File(rootDir, "studio-settings.json")
    }

    /** 旧 Studio 文件只用于一次性安全迁移，迁移前保留备份。 */
    private val studioLegacyConfigFile: File by lazy {
        File(System.getProperty("user.home"), ".antigravity-studio/config.json")
    }

    private val _configState = MutableStateFlow(AppConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    /** 配置加载、解码或导入失败时的可诊断信息；成功加载或保存后恢复为空。 */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        _configState.value = loadConfig()
    }

    val currentConfig: AppConfig
        get() = _configState.value

    fun loadConfig(): AppConfig {
        _loadError.value = null
        configuredPathError?.let {
            recordLoadFailure(IllegalArgumentException(it))
            return _configState.value
        }
        if (configFile.exists()) {
            if (Files.isSymbolicLink(configFile.toPath())) {
                recordLoadFailure(IllegalStateException("配置文件不能是符号链接：${configFile.absolutePath}"))
                return _configState.value
            }
            secureConfigPermissions(configFile).onFailure { recordLoadFailure(it); return _configState.value }
            val parsed = decodeConfig(configFile, "Studio 配置")
            if (parsed.isFailure) {
                recordLoadFailure(parsed.exceptionOrNull())
                return _configState.value
            }
            val loaded = normalizeConfig(parsed.getOrThrow())
            if (requiresCompatibilityRewrite(configFile)) {
                writeConfigFile(loaded).onFailure { recordLoadFailure(it) }
            }
            return loaded
        }

        if (studioLegacyConfigFile.exists() && studioLegacyConfigFile.absoluteFile != configFile.absoluteFile) {
            val parsed = decodeConfig(studioLegacyConfigFile, "旧 Studio 配置")
            if (parsed.isFailure) {
                recordLoadFailure(parsed.exceptionOrNull())
                return _configState.value
            }
            val imported = normalizeConfig(parsed.getOrThrow())
            backupLegacyConfig(studioLegacyConfigFile).onFailure { recordLoadFailure(it) }
            writeConfigFile(imported).onFailure { recordLoadFailure(it) }
            return imported
        }

        val defaultConfig = AppConfig()
        writeConfigFile(defaultConfig).onFailure { recordLoadFailure(it) }
        return normalizeConfig(defaultConfig)
    }

    private fun resolveCanonicalConfigFile(): File {
        customRootDir?.let { return File(it, "config.v1.json") }
        val configuredPath = (System.getenv("ANTIGRAVITY_STUDIO_CONFIG_PATH")
            ?: System.getenv("AGY_STUDIO_CONFIG_PATH"))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf { it.isAbsolute }
        if (configuredPath != null) return configuredPath

        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> File(userHome, "Library/Application Support/Antigravity Studio/config.v1.json")
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData, "Antigravity Studio/config.v1.json")
            }

            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, ".config").absolutePath
                File(configHome, "Antigravity Studio/config.v1.json")
            }
        }
    }

    private fun backupLegacyConfig(file: File): Result<Unit> {
        return try {
            val backup = File(file.parentFile, "${file.name}.migrated.bak")
            val target = if (!backup.exists()) {
                backup
            } else {
                File(file.parentFile, "${file.name}.migrated-${System.currentTimeMillis()}.bak")
            }
            Files.copy(file.toPath(), target.toPath())
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("旧 Studio 配置备份失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun decodeConfig(file: File, label: String): Result<AppConfig> {
        return try {
            secureConfigPermissions(file).getOrThrow()
            val content = file.readText(Charsets.UTF_8)
            val raw = json.parseToJsonElement(content).jsonObject
            val migrated = migrateLegacyCompressionPolicies(raw)
            val config = json.decodeFromJsonElement(AppConfig.serializer(), migrated)
            val uiSettings = readStudioSettings()
            val withUiSettings = config.copy(
                language = uiSettings["language"]?.jsonPrimitive?.contentOrNull ?: config.language,
                themeMode = uiSettings["theme_mode"]?.jsonPrimitive?.contentOrNull ?: config.themeMode
            )
            val normalized = normalizeConfig(withUiSettings)
            validateConfig(normalized)
            Result.success(normalized)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("$label 加载失败：${error.message ?: "内容无效"}", error))
        }
    }


    private fun migrateLegacyCompressionPolicies(root: JsonObject): JsonObject {
        fun migratePolicy(policy: JsonObject): JsonObject {
            val result = policy.toMutableMap()
            fun copyIfMissing(target: String, vararg sources: String) {
                if (result[target] != null) return
                sources.firstNotNullOfOrNull { source -> result[source] }?.let { value ->
                    result[target] = value
                }
            }
            copyIfMissing("token_threshold", "trigger_threshold_tokens")
            copyIfMissing("max_token_limit", "max_checkpoint_tokens")
            copyIfMissing("max_output_tokens", "reserve_output_tokens")
            copyIfMissing("checkpoint_model", "worker_model_id")
            return JsonObject(result)
        }

        val result = root.toMutableMap()
        val policies = root["model_compression_policies"]?.jsonObject
        if (policies != null) {
            result["model_compression_policies"] = JsonObject(
                policies.mapValues { (_, value) ->
                    (value as? JsonObject)?.let(::migratePolicy) ?: value
                }
            )
        }

        val upstreams = root["upstream_models"]?.jsonArray
        if (upstreams != null) {
            result["upstream_models"] = kotlinx.serialization.json.JsonArray(
                upstreams.map { upstream ->
                    val model = upstream as? JsonObject ?: return@map upstream
                    val policy = model["compression_policy"] as? JsonObject ?: return@map upstream
                    JsonObject(model + ("compression_policy" to migratePolicy(policy)))
                }
            )
        }
        return JsonObject(result)
    }

    private fun validateConfig(config: AppConfig) {
        require(config.proxyPort in 1024..65535) {
            "代理端口必须位于 1024 - 65535 之间"
        }
        val providerIds = config.providers.map { provider ->
            require(provider.id.isNotBlank()) { "Provider ID 不能为空" }
            require(provider.name.isNotBlank()) { "Provider 名称不能为空" }
            require(
                provider.baseUrl.isNotBlank() ||
                        !provider.modelsEndpoint.isNullOrBlank() ||
                        !provider.generateEndpoint.isNullOrBlank()
            ) { "Provider ${provider.id} 必须配置 Base URL 或请求端点" }
            require(provider.connectTimeoutMs > 0L && provider.requestTimeoutMs > 0L && provider.streamIdleTimeoutMs > 0L) {
                "Provider ${provider.id} 的超时必须大于 0"
            }
            require(provider.connectTimeoutMs <= provider.requestTimeoutMs) {
                "Provider ${provider.id} 的连接超时不能超过请求超时"
            }
            validateEndpoint(provider.baseUrl.takeIf { it.isNotBlank() }, "Provider ${provider.id} Base URL")
            validateEndpoint(provider.modelsEndpoint, "Provider ${provider.id} models endpoint")
            validateEndpoint(provider.generateEndpoint, "Provider ${provider.id} generate endpoint")
            validateHeaders(provider.headers.orEmpty(), "Provider ${provider.id} headers")
            validateHeaders(provider.headerOverrides.orEmpty(), "Provider ${provider.id} header overrides")
            validateParameters(provider.defaultParameters, "Provider ${provider.id} default parameters")
            validateParameters(provider.parameterOverrides, "Provider ${provider.id} parameter overrides")
            provider.id
        }
        require(providerIds.size == providerIds.toSet().size) { "Provider ID 不能重复" }

        val upstreamIds = config.upstreamModels.map { model ->
            require(model.id.isNotBlank()) { "UpstreamModel ID 不能为空" }
            require(model.providerId in providerIds) {
                "模型 ${model.id} 引用了不存在的 Provider ${model.providerId}"
            }
            require(model.upstreamModelId.isNotBlank()) {
                "模型 ${model.id} 的 upstream_model_id 不能为空"
            }
            validateModelCapabilities(model)
            validateReasoningCapabilities(
                model,
                config.providers.first { provider -> provider.id == model.providerId }.protocol
            )
            listOf(
                model.tokenLimits.contextWindow,
                model.tokenLimits.inputTokenLimit,
                model.tokenLimits.outputTokenLimit
            ).filterNotNull().forEach { limit ->
                require(limit in 1L..4_294_967_295L) {
                    "模型 ${model.id} 的 Token 上限超出 byok 支持范围"
                }
            }
            require(model.tokenLimits.contextWindow == null || model.tokenLimits.contextWindow >= 2L) {
                "模型 ${model.id} 的 context_window 至少需要 2 Token"
            }
            require(model.tokenLimits.inputTokenLimit == null || model.tokenLimits.inputTokenLimit >= 2L) {
                "模型 ${model.id} 的 input_token_limit 至少需要 2 Token"
            }
            require(model.tokenLimits.outputTokenLimit == null || model.tokenLimits.outputTokenLimit > 0L) {
                "模型 ${model.id} 的 output_token_limit 必须大于 0"
            }
            require(model.contextLength == null || model.contextLength in 1L..4_294_967_295L) {
                "模型 ${model.id} 的 context_length 超出 byok 支持范围"
            }
            require(model.maxOutputTokens == null || model.maxOutputTokens in 1L..4_294_967_295L) {
                "模型 ${model.id} 的 max_output_tokens 超出 byok 支持范围"
            }
            model.hostModelId?.takeIf { it.isNotBlank() }?.let { hostId ->
                require(isValidCustomHostModelId(hostId)) {
                    "模型 ${model.id} 的 host_model_id 必须位于 MODEL_PLACEHOLDER_M400-M599 槽位"
                }
            }
            validateParameters(model.parameterOverrides, "模型 ${model.id} parameter overrides")
            model.compressionPolicy?.validate("模型 ${model.id} compression_policy")
            model.id
        }
        require(upstreamIds.size == upstreamIds.toSet().size) { "UpstreamModel ID 不能重复" }

        val virtualIds = config.virtualModels.map { model ->
            require(model.id.isNotBlank()) { "VirtualModel ID 不能为空" }
            require(model.upstreamModelId in upstreamIds) {
                "虚拟模型 ${model.id} 引用了不存在的上游模型 ${model.upstreamModelId}"
            }
            require(isValidCustomHostModelId(ModelIdentity.effectiveHostModelId(model))) {
                "虚拟模型 ${model.id} 的 host_model_id 必须位于 MODEL_PLACEHOLDER_M400-M599 槽位"
            }
            model.id
        }
        require(virtualIds.size == virtualIds.toSet().size) { "VirtualModel ID 不能重复" }
        val acceptedVirtualIds = mutableMapOf<String, String>()
        config.virtualModels.forEach { model ->
            ModelIdentity.acceptedIds(model).forEach { acceptedId ->
                val normalized = acceptedId.trim().removePrefix("models/")
                val existingOwner = acceptedVirtualIds.putIfAbsent(normalized, model.id)
                require(existingOwner == null || existingOwner == model.id) {
                    "VirtualModel ${model.id} 与 $existingOwner 的可接受模型标识冲突：$normalized"
                }
            }
        }
        config.virtualModels.forEach { model ->
            model.fallbackVirtualModelId?.let { fallback ->
                require(fallback in virtualIds && fallback != model.id) {
                    "虚拟模型 ${model.id} 的 fallback 引用无效"
                }
            }
            validateParameters(model.parameterOverrides, "虚拟模型 ${model.id} parameter overrides")
            val level = model.defaultReasoningLevel ?: return@forEach
            if (level != com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.OFF &&
                level != com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.AUTO
            ) {
                val upstream = config.upstreamModels.first { it.id == model.upstreamModelId }
                val provider = config.providers.first { it.id == upstream.providerId }
                val mapping = ReasoningMappingSupport.resolveMapping(
                    provider.protocol,
                    level,
                    ReasoningMappingSupport.parse(upstream.capabilities.reasoning.levels),
                    upstream.tokenLimits.outputTokenLimit
                )
                require(mapping != null) {
                    "虚拟模型 ${model.id} 的推理档位 ${level.label} 不受 Provider/上游支持"
                }
            }
        }
        config.modelCompressionPolicies.forEach { (modelId, policy) ->
            require(modelId.isNotBlank()) { "压缩策略模型 ID 不能为空" }
            policy.validate("model_compression_policies[$modelId]")
        }
        config.upstreamModels.forEach { model ->
            model.compressionPolicy?.validate("upstream_models[${model.id}].compression_policy")
        }
    }

    private fun writeConfigFile(config: AppConfig): Result<Unit> {
        var tempFile: File? = null
        return try {
            if (Files.isSymbolicLink(configFile.toPath())) {
                return Result.failure(IllegalStateException("配置文件不能是符号链接：${configFile.absolutePath}"))
            }
            if (!rootDir.exists() && !rootDir.mkdirs()) {
                return Result.failure(IllegalStateException("无法创建配置目录：${rootDir.absolutePath}"))
            }
            // Studio 的配置由 Studio 自己负责读写。这里必须保留 Studio 扩展字段
            //（例如 header_overrides、parameter_overrides、UI 设置与宿主路径），
            // 否则每次保存后这些字段都会被旧版 byok 契约投影静默丢弃。
            val content = json.encodeToString(
                AppConfig.serializer(),
                normalizeConfig(config)
            )
            val createdTempFile = File.createTempFile("config-", ".tmp", rootDir)
            tempFile = createdTempFile
            createdTempFile.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    createdTempFile.toPath(),
                    configFile.toPath(),
                    ATOMIC_MOVE,
                    REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(createdTempFile.toPath(), configFile.toPath(), REPLACE_EXISTING)
            }
            secureConfigPermissions(configFile).getOrElse { error -> return Result.failure(error) }
            writeStudioSettings(config).getOrElse { error ->
                return Result.failure(error)
            }
            if (!configFile.exists()) {
                return Result.failure(IllegalStateException("配置文件原子替换后未找到：${configFile.absolutePath}"))
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("配置文件写入失败：${error.message ?: "未知错误"}", error))
        } finally {
            tempFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun recordLoadFailure(error: Throwable?) {
        _loadError.value = error?.message ?: "配置加载失败：未知错误"
    }

    @Synchronized
    fun saveConfig(newConfig: AppConfig) {
        val normalized = normalizeConfig(newConfig)
        validateConfig(normalized)
        writeConfigFile(normalized).getOrElse { error ->
            _loadError.value = error.message ?: "配置保存失败：未知错误"
            throw error
        }
        _configState.value = normalized
        _loadError.value = null
        OfficialCatalogProbe.clearRawOfficialCatalog()
    }

    @Synchronized
    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        val updated = transform(currentConfig)
        saveConfig(updated)
    }

    /** 将 Studio 运行时扩展字段归一化为可与 byok 共享的领域对象。 */
    private fun normalizeConfig(config: AppConfig): AppConfig {
        val providers = config.providers.map(::normalizeProvider)
        val upstreams = config.upstreamModels.map { upstream ->
            upstream.copy(capabilities = normalizeCapabilities(upstream.capabilities))
        }
        val virtuals = config.virtualModels.map { virtual ->
            val upstream = upstreams.firstOrNull { model -> model.id == virtual.upstreamModelId }
            virtual.copy(
                name = virtual.name.ifBlank { virtual.displayName.orEmpty() },
                capabilities = upstream?.capabilities ?: virtual.capabilities
            )
        }
        return config.copy(
            providers = providers,
            upstreamModels = upstreams,
            virtualModels = virtuals
        )
    }

    /** 将 Studio 的旧 vision 开关折叠进正式模态与 MIME 字段，保证后续校验和路由一致。 */
    private fun normalizeCapabilities(capabilities: ModelCapabilities): ModelCapabilities {
        val modalities = capabilities.inputModalities.toMutableSet()
        if (capabilities.vision) modalities += ModelModality.IMAGE
        if (modalities.isEmpty()) modalities += ModelModality.TEXT
        val mimeTypes = capabilities.inputMimeTypes.map { it.trim().lowercase() }.toMutableSet()
        if (ModelModality.IMAGE in modalities && mimeTypes.none { it.startsWith("image/", ignoreCase = true) }) {
            mimeTypes += listOf("image/png", "image/jpeg", "image/webp")
        }
        if (mimeTypes.any { mime ->
                !mime.startsWith("image/", ignoreCase = true) &&
                        !mime.startsWith("audio/", ignoreCase = true) &&
                        !mime.startsWith("video/", ignoreCase = true)
            }
        ) {
            modalities += ModelModality.DOCUMENT
        }
        return capabilities.copy(
            inputModalities = modalities.toList(),
            outputModalities = capabilities.outputModalities.ifEmpty { listOf(ModelModality.TEXT) },
            roles = capabilities.roles.ifEmpty { listOf(com.yuzhiqiang.antigravity.domain.model.ModelRole.AGENT) },
            inputMimeTypes = mimeTypes.map { it.trim().lowercase() }.distinct().sorted()
        )
    }

    private fun normalizeProvider(provider: Provider): Provider {
        val base = provider.baseUrl.trimEnd('/').ifBlank {
            deriveBaseUrl(provider.generateEndpoint ?: provider.modelsEndpoint ?: "")
        }
        val modelsEndpoint = provider.modelsEndpoint?.takeIf { it.isNotBlank() }
            ?: appendPath(base, "/models")
        val generateEndpoint = provider.generateEndpoint?.takeIf { it.isNotBlank() }
            ?: when (provider.protocol) {
                ProviderProtocol.ANTHROPIC_MESSAGES -> appendPath(base, "/messages")
                ProviderProtocol.GEMINI_GENERATE_CONTENT -> "$base/models/{model}:generateContent"
                ProviderProtocol.OPENAI_RESPONSES -> appendPath(base, "/responses")
                ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> appendPath(base, "/chat/completions")
            }
        return provider.copy(
            baseUrl = base,
            modelsEndpoint = modelsEndpoint,
            generateEndpoint = generateEndpoint
        )
    }

    private fun validateEndpoint(endpoint: String?, label: String) {
        endpoint?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            val uri = runCatching { URI(value.replace("{model}", "model")) }.getOrNull()
            require(uri?.let { parsed ->
                parsed.scheme in setOf("http", "https") &&
                        !parsed.host.isNullOrBlank() &&
                        parsed.userInfo == null &&
                        parsed.fragment == null &&
                        (parsed.scheme.equals("https", ignoreCase = true) || isLoopbackHost(parsed.host))
            } == true) {
                "$label 必须是无内嵌凭据/片段的绝对 HTTPS 地址（回环地址可使用 HTTP）"
            }
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
    }

    private fun isValidCustomHostModelId(value: String): Boolean {
        val number = value.removePrefix("MODEL_PLACEHOLDER_M")
        if (number.isEmpty() || (number.length > 1 && number.startsWith('0')) ||
            value != "MODEL_PLACEHOLDER_M$number"
        ) return false
        return number.toIntOrNull()?.let { it in 400 until 600 } == true
    }

    private fun validateHeaders(headers: Map<String, String>, label: String) {
        headers.forEach { (name, value) ->
            require(name.isNotBlank() && name.all(::isHeaderNameChar)) {
                "$label 的 Header 名称无效：$name"
            }
            require(value.none { it == '\r' || it == '\n' || it.code == 0 }) {
                "$label 的 Header $name 包含非法控制字符"
            }
        }
    }

    private fun isHeaderNameChar(value: Char): Boolean {
        return value.isLetterOrDigit() || "!#$%&'*+-.^_`|~".contains(value)
    }

    private fun validateParameters(
        parameters: com.yuzhiqiang.antigravity.domain.model.ParameterOverrides?,
        label: String
    ) {
        parameters ?: return
        parameters.temperature?.let { value ->
            require(value.isFinite() && value >= 0f) { "$label temperature 必须是非负有限数字" }
        }
        parameters.topP?.let { value ->
            require(value.isFinite() && value in 0f..1f) { "$label top_p 必须位于 0 到 1 之间" }
        }
        parameters.maxTokens?.let { value ->
            require(value > 0) { "$label max_tokens 必须大于 0" }
        }
        parameters.topK?.let { value ->
            require(value > 0) { "$label top_k 必须大于 0" }
        }
    }

    private fun validateModelCapabilities(model: com.yuzhiqiang.antigravity.domain.model.UpstreamModel) {
        val capabilities = model.capabilities
        require(capabilities.roles.isNotEmpty() && capabilities.roles.all { role ->
            role == com.yuzhiqiang.antigravity.domain.model.ModelRole.AGENT ||
                    role == com.yuzhiqiang.antigravity.domain.model.ModelRole.IMAGE_GENERATION
        }) {
            "模型 ${model.id} 只能声明 agent 或 image_generation 角色"
        }
        require(com.yuzhiqiang.antigravity.domain.model.ModelModality.TEXT in capabilities.inputModalities) {
            "模型 ${model.id} 必须支持 text 输入"
        }
        require(capabilities.outputModalities.isNotEmpty() && capabilities.outputModalities.all { modality ->
            modality == com.yuzhiqiang.antigravity.domain.model.ModelModality.TEXT ||
                    modality == com.yuzhiqiang.antigravity.domain.model.ModelModality.IMAGE
        }) {
            "模型 ${model.id} 的输出模态只能是 text 或 image"
        }
        val isAgent = com.yuzhiqiang.antigravity.domain.model.ModelRole.AGENT in capabilities.roles
        val hasTextOutput = com.yuzhiqiang.antigravity.domain.model.ModelModality.TEXT in capabilities.outputModalities
        require(isAgent == hasTextOutput) {
            "模型 ${model.id} 的 agent 角色必须与 text 输出配对"
        }
        val isImage = com.yuzhiqiang.antigravity.domain.model.ModelRole.IMAGE_GENERATION in capabilities.roles
        val hasImageOutput =
            com.yuzhiqiang.antigravity.domain.model.ModelModality.IMAGE in capabilities.outputModalities
        require(isImage == hasImageOutput) {
            "模型 ${model.id} 的 image_generation 角色必须与 image 输出配对"
        }

        val normalizedMimeTypes = capabilities.inputMimeTypes.map { it.trim().lowercase() }
        require(normalizedMimeTypes.all { it.isNotBlank() && it.contains('/') }) {
            "模型 ${model.id} 的输入 MIME 类型无效"
        }
        require(normalizedMimeTypes.size == normalizedMimeTypes.toSet().size) {
            "模型 ${model.id} 的输入 MIME 类型不能重复"
        }
        val declaredModalities = normalizedMimeTypes.map { mime ->
            when {
                mime.startsWith("image/") -> com.yuzhiqiang.antigravity.domain.model.ModelModality.IMAGE
                mime.startsWith("audio/") || mime.startsWith("video/audio/") -> com.yuzhiqiang.antigravity.domain.model.ModelModality.AUDIO
                mime.startsWith("video/") -> com.yuzhiqiang.antigravity.domain.model.ModelModality.VIDEO
                else -> com.yuzhiqiang.antigravity.domain.model.ModelModality.DOCUMENT
            }
        }.toSet()
        listOf(
            com.yuzhiqiang.antigravity.domain.model.ModelModality.IMAGE,
            com.yuzhiqiang.antigravity.domain.model.ModelModality.AUDIO,
            com.yuzhiqiang.antigravity.domain.model.ModelModality.VIDEO,
            com.yuzhiqiang.antigravity.domain.model.ModelModality.DOCUMENT
        ).forEach { modality ->
            require((modality in capabilities.inputModalities) == (modality in declaredModalities)) {
                "模型 ${model.id} 的 $modality 输入模态必须与 MIME 类型声明一致"
            }
        }
    }

    private fun validateReasoningCapabilities(
        model: com.yuzhiqiang.antigravity.domain.model.UpstreamModel,
        provider: ProviderProtocol
    ) {
        val reasoning = model.capabilities.reasoning
        val hasBudget = reasoning.thinkingBudget != null || reasoning.minThinkingBudget != null
        require(reasoning.thinkingBudget == null || reasoning.thinkingBudget >= -1) {
            "模型 ${model.id} 的 thinking_budget 必须是 -1、0 或正整数"
        }
        require(reasoning.minThinkingBudget == null || reasoning.minThinkingBudget > 0) {
            "模型 ${model.id} 的 min_thinking_budget 必须大于 0"
        }
        if (hasBudget) {
            require(provider == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
                "模型 ${model.id} 只有 Gemini Provider 可以声明模型级 thinking budget"
            }
        }
        require(!(reasoning.supported == false && (hasBudget || ReasoningMappingSupport.hasConfiguredLevels(reasoning.levels)))) {
            "模型 ${model.id} 不能在关闭推理时保留推理配置"
        }
        val minimum = reasoning.minThinkingBudget
        val default = reasoning.thinkingBudget
        if (minimum != null && default != null) {
            require(default == -1 || (default > 0 && minimum <= default)) {
                "模型 ${model.id} 的 min_thinking_budget 不能超过 thinking_budget"
            }
        }
        val mappings = ReasoningMappingSupport.parse(reasoning.levels)
        if (minimum != null) {
            mappings.values
                .filter { mapping -> mapping.kind.equals("budget_tokens", ignoreCase = true) }
                .mapNotNull(ReasoningMappingSupport::mappingValueAsInt)
                .forEach { budget ->
                    require(budget >= minimum) {
                        "模型 ${model.id} 的推理预算 $budget 低于 min_thinking_budget $minimum"
                    }
                }
        }
        mappings.forEach { (_, mapping) ->
            require(ReasoningMappingSupport.isSupported(provider, mapping, model.tokenLimits.outputTokenLimit)) {
                "模型 ${model.id} 的推理映射不受 ${provider.displayName} 支持"
            }
        }
    }


    private fun deriveBaseUrl(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return trimmed
            .substringBefore("/chat/completions")
            .substringBefore("/responses")
            .substringBefore("/messages")
            .substringBefore("/models/{model}")
            .substringBefore("/models")
    }

    private fun appendPath(base: String, path: String): String {
        val normalized = base.trimEnd('/')
        val queryIndex = normalized.indexOf('?')
        if (queryIndex < 0) return if (normalized.endsWith(path)) normalized else "$normalized$path"
        val pathPart = normalized.substring(0, queryIndex)
        val queryPart = normalized.substring(queryIndex)
        return if (pathPart.endsWith(path)) normalized else "${pathPart.trimEnd('/')}$path$queryPart"
    }

    private fun readStudioSettings(): JsonObject {
        if (!studioSettingsFile.exists()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(studioSettingsFile.readText(Charsets.UTF_8)).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    }

    private fun requiresCompatibilityRewrite(file: File): Boolean {
        val root = runCatching { json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject }
            .getOrNull() ?: return false
        val legacyPolicyKeys = setOf(
            "trigger_threshold_tokens",
            "target_context_tokens",
            "max_checkpoint_tokens",
            "reserve_output_tokens",
            "worker_model_id"
        )
        val policyMaps = listOfNotNull(
            root["model_compression_policies"] as? JsonObject,
            (root["upstream_models"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("compression_policy") as? JsonObject }
                ?.let { values -> JsonObject(values.mapIndexed { index, value -> index.toString() to value }.toMap()) }
        )
        if (policyMaps.any { map ->
                map.values.any { value ->
                    val policy = value as? JsonObject ?: return@any false
                    policy.keys.any(legacyPolicyKeys::contains)
                }
            }) return true
        return false
    }

    private fun writeStudioSettings(config: AppConfig): Result<Unit> {
        return try {
            val parent = studioSettingsFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return Result.failure(IllegalStateException("无法创建 Studio 设置目录：${parent.absolutePath}"))
            }
            val content = buildJsonObject {
                put("language", config.language)
                put("theme_mode", config.themeMode)
            }.toString()
            val temp = File.createTempFile("studio-settings-", ".tmp", parent ?: File("."))
            try {
                temp.writeText(content, Charsets.UTF_8)
                Files.move(temp.toPath(), studioSettingsFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), studioSettingsFile.toPath(), REPLACE_EXISTING)
            } finally {
                if (temp.exists()) temp.delete()
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Studio 设置写入失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun secureConfigPermissions(file: File): Result<Unit> {
        if (Files.isSymbolicLink(file.toPath())) {
            return Result.failure(IllegalStateException("配置文件不能是符号链接：${file.absolutePath}"))
        }
        if (System.getProperty("os.name").lowercase().contains("win")) return Result.success(Unit)
        return try {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
            )
            Result.success(Unit)
        } catch (error: UnsupportedOperationException) {
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("配置文件权限收紧失败：${error.message ?: "未知错误"}", error))
        }
    }
}
