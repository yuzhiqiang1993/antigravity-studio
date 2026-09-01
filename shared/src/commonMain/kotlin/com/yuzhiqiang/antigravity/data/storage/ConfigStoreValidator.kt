package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import java.net.URI

/**
 * canonical config.v2.json 的领域约束校验。
 *
 * 校验只接受当前 AppConfig 契约，不负责补救不符合契约的数据。
 */
internal object ConfigStoreValidator {
    fun validate(config: AppConfig) {
        require(config.schemaVersion == AppConfig.CURRENT_SCHEMA_VERSION) {
            "不支持的配置 schema_version：${config.schemaVersion}"
        }
        require(config.proxyPort in 1024..65535) {
            "代理端口必须位于 1024 - 65535 之间"
        }
        if (config.outboundProxy.mode == OutboundProxyMode.MANUAL) {
            require(
                config.outboundProxy.host.isNotBlank() &&
                        "://" !in config.outboundProxy.host &&
                        "/" !in config.outboundProxy.host
            ) { "手动代理地址必须是不带协议和路径的主机名或 IP" }
            require(config.outboundProxy.port in 1..65535) { "手动代理端口必须位于 1 - 65535 之间" }
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
            require(provider.maxRetries >= 0) {
                "Provider ${provider.id} 的重试次数不能为负数"
            }
            require(provider.retryDelayMs >= 0L) {
                "Provider ${provider.id} 的重试间隔不能为负数"
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

        val canonicalIds = config.canonicalModels.map { model ->
            require(model.canonicalModelId.isNotBlank()) { "Canonical Model ID 不能为空" }
            require(model.providerVendor.isNotBlank()) { "Canonical Model ${model.canonicalModelId} 的 Provider Vendor 不能为空" }
            require(model.displayName.isNotBlank()) { "Canonical Model ${model.canonicalModelId} 的显示名称不能为空" }
            model.canonicalModelId
        }
        require(canonicalIds.size == canonicalIds.toSet().size) { "Canonical Model ID 不能重复" }

        val bindingIds = config.providerModelBindings.map { model ->
            require(model.bindingId.isNotBlank()) { "Provider Model Binding ID 不能为空" }
            require(model.providerConfigId in providerIds) {
                "Binding ${model.bindingId} 引用了不存在的 Provider ${model.providerConfigId}"
            }
            require(model.providerModelId.isNotBlank()) {
                "Binding ${model.bindingId} 的 provider_model_id 不能为空"
            }
            require(model.displayName.isNotBlank()) {
                "Binding ${model.bindingId} 的 display_name 不能为空"
            }
            model.canonicalModelId?.let { canonicalModelId ->
                require(canonicalModelId in canonicalIds) {
                    "Binding ${model.bindingId} 引用了不存在的 Canonical Model $canonicalModelId"
                }
            }
            validateModelCapabilities(model)
            validateReasoningCapabilities(
                model,
                config.providers.first { provider -> provider.id == model.providerConfigId }.protocol
            )
            listOf(
                model.tokenLimits.contextWindow,
                model.tokenLimits.inputTokenLimit,
                model.tokenLimits.outputTokenLimit
            ).filterNotNull().forEach { limit ->
                require(limit in 1L..4_294_967_295L) {
                    "Binding ${model.bindingId} 的 Token 上限超出 BYOK 支持范围"
                }
            }
            require(model.tokenLimits.contextWindow == null || model.tokenLimits.contextWindow >= 2L) {
                "Binding ${model.bindingId} 的 context_window 至少需要 2 Token"
            }
            require(model.tokenLimits.inputTokenLimit == null || model.tokenLimits.inputTokenLimit >= 2L) {
                "Binding ${model.bindingId} 的 input_token_limit 至少需要 2 Token"
            }
            require(model.tokenLimits.outputTokenLimit == null || model.tokenLimits.outputTokenLimit > 0L) {
                "Binding ${model.bindingId} 的 output_token_limit 必须大于 0"
            }
            require(model.aliases.all { alias -> alias.value.isNotBlank() }) {
                "Binding ${model.bindingId} 的 alias 不能为空"
            }
            validateParameters(model.parameterOverrides, "Binding ${model.bindingId} parameter overrides")
            model.compressionPolicy?.validate("Binding ${model.bindingId} compression_policy")
            model.bindingId
        }
        require(bindingIds.size == bindingIds.toSet().size) { "Provider Model Binding ID 不能重复" }

        val variantIds = config.modelRouteVariants.map { variant ->
            require(variant.variantId.isNotBlank()) { "Model Route Variant ID 不能为空" }
            require(variant.bindingId == null || variant.bindingId in bindingIds) {
                "Route Variant ${variant.variantId} 引用了不存在的 Binding ${variant.bindingId}"
            }
            require(variant.catalogModelId.isNotBlank()) {
                "Route Variant ${variant.variantId} 的 catalog_model_id 不能为空"
            }
            require(isValidCustomRuntimeModelId(variant.runtimeModelId)) {
                "Route Variant ${variant.variantId} 的 runtime_model_id 必须位于 MODEL_PLACEHOLDER_M400-M599 槽位"
            }
            require(variant.displayName.isNotBlank()) {
                "Route Variant ${variant.variantId} 的 display_name 不能为空"
            }
            variant.variantId
        }
        require(variantIds.size == variantIds.toSet().size) { "Model Route Variant ID 不能重复" }
        val linkedBindingIds = config.modelRouteVariants.mapNotNull { variant -> variant.bindingId }.toSet()
        require(bindingIds.all(linkedBindingIds::contains)) {
            "每个 Provider Model Binding 都必须至少关联一个 Model Route Variant"
        }
        val acceptedIds = mutableMapOf<String, String>()
        config.modelRouteVariants.forEach { variant ->
            ModelIdentity.acceptedIds(variant).forEach { acceptedId ->
                val normalized = ModelIdentity.normalizeModelId(acceptedId)
                val existingOwner = acceptedIds.putIfAbsent(normalized, variant.variantId)
                require(existingOwner == null || existingOwner == variant.variantId) {
                    "Route Variant ${variant.variantId} 与 $existingOwner 的可接受模型标识冲突：$normalized"
                }
            }
        }
        config.modelRouteVariants.forEach { variant ->
            validateParameters(variant.parameterOverrides, "Route Variant ${variant.variantId} parameter overrides")
            val level = variant.reasoningProfile?.level ?: return@forEach
            if (level != com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.OFF &&
                level != com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.AUTO
            ) {
                val binding = config.providerModelBindings.first { it.bindingId == variant.bindingId }
                val provider = config.providers.first { it.id == binding.providerConfigId }
                val mapping = ReasoningMappingSupport.resolveMapping(
                    provider.protocol,
                    level,
                    ReasoningMappingSupport.parse(binding.capabilities.reasoning.levels),
                    binding.tokenLimits.outputTokenLimit
                )
                require(mapping != null) {
                    "Route Variant ${variant.variantId} 的推理档位 ${level.label} 不受 Provider/Binding 支持"
                }
            }
        }
        val policyTargets = config.compressionPolicyAssignments.map { assignment ->
            require(assignment.targetId.isNotBlank()) { "压缩策略目标 ID 不能为空" }
            val targetExists = when (assignment.targetType) {
                CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL -> true
                CompressionPolicyTargetType.PROVIDER_MODEL_BINDING -> assignment.targetId in bindingIds
                CompressionPolicyTargetType.MODEL_ROUTE_VARIANT -> assignment.targetId in variantIds
            }
            require(targetExists) {
                "压缩策略引用了不存在的目标 ${assignment.targetType}:${assignment.targetId}"
            }
            assignment.policy.validate("compression_policy_assignments[${assignment.targetId}]")
            assignment.targetType to assignment.targetId
        }
        require(policyTargets.size == policyTargets.toSet().size) { "同一目标不能重复配置压缩策略" }
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

    private fun isValidCustomRuntimeModelId(value: String): Boolean {
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
        parameters: ParameterOverrides?,
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

    private fun validateModelCapabilities(model: ProviderModelBinding) {
        val capabilities = model.capabilities
        require(capabilities.roles.isNotEmpty() && capabilities.roles.all { role ->
            role == ModelRole.AGENT || role == ModelRole.IMAGE_GENERATION
        }) {
            "Binding ${model.bindingId} 只能声明 agent 或 image_generation 角色"
        }
        require(ModelModality.TEXT in capabilities.inputModalities) {
            "Binding ${model.bindingId} 必须支持 text 输入"
        }
        require(capabilities.outputModalities.isNotEmpty() && capabilities.outputModalities.all { modality ->
            modality == ModelModality.TEXT || modality == ModelModality.IMAGE
        }) {
            "Binding ${model.bindingId} 的输出模态只能是 text 或 image"
        }
        val isAgent = ModelRole.AGENT in capabilities.roles
        val hasTextOutput = ModelModality.TEXT in capabilities.outputModalities
        require(isAgent == hasTextOutput) {
            "Binding ${model.bindingId} 的 agent 角色必须与 text 输出配对"
        }
        val isImage = ModelRole.IMAGE_GENERATION in capabilities.roles
        val hasImageOutput = ModelModality.IMAGE in capabilities.outputModalities
        require(isImage == hasImageOutput) {
            "Binding ${model.bindingId} 的 image_generation 角色必须与 image 输出配对"
        }

        val normalizedMimeTypes = capabilities.inputMimeTypes.map { it.trim().lowercase() }
        require(normalizedMimeTypes.all { it.isNotBlank() && it.contains('/') }) {
            "Binding ${model.bindingId} 的输入 MIME 类型无效"
        }
        require(normalizedMimeTypes.size == normalizedMimeTypes.toSet().size) {
            "Binding ${model.bindingId} 的输入 MIME 类型不能重复"
        }
        val declaredModalities = normalizedMimeTypes.map { mime ->
            when {
                mime.startsWith("image/") -> ModelModality.IMAGE
                mime.startsWith("audio/") || mime.startsWith("video/audio/") -> ModelModality.AUDIO
                mime.startsWith("video/") -> ModelModality.VIDEO
                else -> ModelModality.DOCUMENT
            }
        }.toSet()
        listOf(
            ModelModality.IMAGE,
            ModelModality.AUDIO,
            ModelModality.VIDEO,
            ModelModality.DOCUMENT
        ).forEach { modality ->
            require((modality in capabilities.inputModalities) == (modality in declaredModalities)) {
                "Binding ${model.bindingId} 的 $modality 输入模态必须与 MIME 类型声明一致"
            }
        }
    }

    private fun validateReasoningCapabilities(
        model: ProviderModelBinding,
        provider: ProviderProtocol
    ) {
        val reasoning = model.capabilities.reasoning
        val hasBudget = reasoning.thinkingBudget != null || reasoning.minThinkingBudget != null
        require(reasoning.thinkingBudget == null || reasoning.thinkingBudget >= -1) {
            "Binding ${model.bindingId} 的 thinking_budget 必须是 -1、0 或正整数"
        }
        require(reasoning.minThinkingBudget == null || reasoning.minThinkingBudget > 0) {
            "Binding ${model.bindingId} 的 min_thinking_budget 必须大于 0"
        }
        if (hasBudget) {
            require(provider == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
                "Binding ${model.bindingId} 只有 Gemini Provider 可以声明模型级 thinking budget"
            }
        }
        require(!(reasoning.supported == false && (hasBudget || ReasoningMappingSupport.hasConfiguredLevels(reasoning.levels)))) {
            "Binding ${model.bindingId} 不能在关闭推理时保留推理配置"
        }
        val minimum = reasoning.minThinkingBudget
        val default = reasoning.thinkingBudget
        if (minimum != null && default != null) {
            require(default == -1 || (default > 0 && minimum <= default)) {
                "Binding ${model.bindingId} 的 min_thinking_budget 不能超过 thinking_budget"
            }
        }
        val mappings = ReasoningMappingSupport.parse(reasoning.levels)
        if (minimum != null) {
            mappings.values
                .filter { mapping -> mapping.kind.equals("budget_tokens", ignoreCase = true) }
                .mapNotNull(ReasoningMappingSupport::mappingValueAsInt)
                .forEach { budget ->
                    require(budget >= minimum) {
                        "Binding ${model.bindingId} 的推理预算 $budget 低于 min_thinking_budget $minimum"
                    }
                }
        }
        mappings.forEach { (_, mapping) ->
            require(ReasoningMappingSupport.isSupported(provider, mapping, model.tokenLimits.outputTokenLimit)) {
                "Binding ${model.bindingId} 的推理映射不受 ${provider.displayName} 支持"
            }
        }
    }
}
