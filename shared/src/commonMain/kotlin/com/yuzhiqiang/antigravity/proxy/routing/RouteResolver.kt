package com.yuzhiqiang.antigravity.proxy.routing

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelIdentityResolution
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.ModelVariantKind
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.ReasoningProfile
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 路由解析失败时携带可直接映射为 HTTP 4xx 的状态码。 */
class RouteResolutionException(
    val statusCode: Int,
    message: String
) : IllegalArgumentException(message)

/**
 * 请求开始执行时冻结的模型身份。Provider 请求只使用 [providerModelId]，宿主目录与
 * Runtime slot 不参与 Provider 侧模型选择。
 */
data class ModelExecutionIdentity(
    val requestedModelId: String,
    val variantId: String,
    val catalogModelId: String,
    val runtimeModelId: String,
    val bindingId: String,
    val providerConfigId: String,
    val providerModelId: String,
    val canonicalModelId: String?,
    val baseModelId: String?,
    val providerVendor: String?,
    val displayName: String,
    val reasoningProfile: ReasoningProfile?,
    val identityResolution: ModelIdentityResolution
)

data class ResolvedRoute(
    val requestedModelId: String,
    val modelRouteVariant: ModelRouteVariant,
    val providerModelBinding: ProviderModelBinding,
    val provider: Provider,
    val modelExecutionIdentity: ModelExecutionIdentity,
    /** 进入路由器时的请求；不含当前路由的参数默认值，供备用路由重新合并。 */
    val originalRequest: NeutralChatRequest,
    val request: NeutralChatRequest,
    val finalParameters: ParameterOverrides
)

/** 按 ModelRouteVariant -> ProviderModelBinding -> Provider 解析请求并合并参数。 */
object RouteResolver {

    fun resolve(config: AppConfig, request: NeutralChatRequest): Result<ResolvedRoute> {
        val requestedModelId = ModelIdentity.normalizeModelId(request.originalModelId)
        val exactVariant = config.modelRouteVariants.firstOrNull { variant ->
            requestedModelId in ModelIdentity.acceptedIds(variant)
        }
        if (exactVariant != null) {
            if (!exactVariant.enabled) {
                return failure(404, "Model route variant is disabled: $requestedModelId")
            }
            val executionVariant = if (exactVariant.kind == ModelVariantKind.TIERED) {
                resolveTierMember(config, exactVariant)
                    ?: return failure(404, "Tiered model has no routable member: ${exactVariant.variantId}")
            } else {
                exactVariant
            }
            return buildResolvedRoute(config, requestedModelId, executionVariant, request)
        }

        val tieredVariant = resolveSyntheticTieredVariant(config, requestedModelId)
        if (tieredVariant != null) {
            return buildResolvedRoute(config, requestedModelId, tieredVariant, request)
        }

        // 官方图片模型 ID 或显式图片输出请求可以重定向到已配置的 BYOK 生图模型。
        val imageVariant = resolveImageGenerationVariant(config, requestedModelId, request)
        if (imageVariant != null) {
            return buildResolvedRoute(config, requestedModelId, imageVariant, request)
        }

        return failure(404, "Model route variant is not configured: $requestedModelId")
    }

    fun isPotentialCustomModelId(config: AppConfig, modelId: String): Boolean {
        val normalized = ModelIdentity.normalizeModelId(modelId)
        return config.modelRouteVariants.any { normalized in ModelIdentity.acceptedIds(it) } ||
                resolveSyntheticTieredVariant(config, normalized) != null ||
                (isOfficialImageModelId(normalized) && findActiveCustomImageVariant(config) != null) ||
                normalized.startsWith("byok-") ||
                normalized.startsWith("variant-") ||
                normalized.startsWith(ModelIdentity.CUSTOM_RUNTIME_MODEL_ID_PREFIX)
    }

    fun isRoutableModelRouteVariant(config: AppConfig, variant: ModelRouteVariant): Boolean {
        if (!variant.enabled) return false
        if (variant.kind == ModelVariantKind.TIERED) return resolveTierMember(config, variant) != null
        val binding = findBinding(config, variant) ?: return false
        return binding.enabled && config.providers.any { provider ->
            provider.id == binding.providerConfigId && provider.enabled
        }
    }

    fun effectiveRuntimeModelId(variant: ModelRouteVariant): String =
        ModelIdentity.effectiveRuntimeModelId(variant)

    fun catalogKey(variant: ModelRouteVariant): String = ModelIdentity.catalogKey(variant)

    fun acceptedIds(variant: ModelRouteVariant): List<String> = ModelIdentity.acceptedIds(variant)

    private fun buildResolvedRoute(
        config: AppConfig,
        requestedModelId: String,
        variant: ModelRouteVariant,
        request: NeutralChatRequest
    ): Result<ResolvedRoute> {
        if (!variant.enabled) {
            return failure(404, "Model route variant is disabled: ${variant.variantId}")
        }
        val binding = findBinding(config, variant)
            ?: return failure(
                404,
                "Model route variant is not linked to a Provider model binding: ${variant.variantId}"
            )
        if (!binding.enabled) {
            return failure(404, "Provider model binding is disabled: ${binding.bindingId}")
        }
        val provider = config.providers.firstOrNull { it.id == binding.providerConfigId }
            ?: return failure(422, "Provider is not configured: ${binding.providerConfigId}")
        if (!provider.enabled) {
            return failure(422, "Provider is disabled: ${provider.name}")
        }

        val providerParameters = (provider.parameterOverrides ?: ParameterOverrides())
            .mergeWith(provider.defaultParameters)
        val requestParameters = ParameterOverrides(
            temperature = request.temperature,
            maxTokens = request.maxTokens,
            topP = request.topP,
            topK = request.topK,
            extraBody = request.extraBody
        )
        val finalParameters = providerParameters
            .mergeWith(binding.parameterOverrides)
            .mergeWith(variant.parameterOverrides)
            .mergeWith(requestParameters)
            .withoutControlledExtraBody()
        val configuredProfile = variant.reasoningProfile
        val finalReasoningLevel = request.reasoningLevel ?: configuredProfile?.level
        val requestedBudget = request.reasoningBudgetTokens ?: configuredProfile?.budgetTokens

        validateInputTokenBudget(
            provider,
            binding,
            request.copy(extraBody = finalParameters.extraBody.orEmpty())
        ).onFailure { error -> return Result.failure(error) }

        val reasoningMappingResult = resolveReasoningMapping(
            provider = provider,
            binding = binding,
            configuredProfile = configuredProfile,
            reasoningLevel = finalReasoningLevel,
            requestedBudget = requestedBudget
        )
        if (reasoningMappingResult.isFailure) {
            return Result.failure(
                reasoningMappingResult.exceptionOrNull()
                    ?: IllegalArgumentException("Reasoning mapping is invalid")
            )
        }
        val reasoningMapping = reasoningMappingResult.getOrNull()
        val effectiveReasoningBudget = requestedBudget
            ?: reasoningMapping?.let(ReasoningMappingSupport::mappingValueAsInt)
        val effectiveMaxTokens = finalParameters.maxTokens ?: if (
            provider.protocol == ProviderProtocol.ANTHROPIC_MESSAGES &&
            reasoningMapping?.kind.equals("budget_tokens", ignoreCase = true)
        ) {
            val budget = (effectiveReasoningBudget ?: 0).toLong()
            val generated = (budget + 4_096L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            binding.tokenLimits.outputTokenLimit
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?.coerceAtMost(generated)
                ?: generated
        } else {
            null
        }
        val effectiveParameters = finalParameters.copy(maxTokens = effectiveMaxTokens)
        val executionProfile = if (
            finalReasoningLevel != null || reasoningMapping != null || effectiveReasoningBudget != null ||
            configuredProfile?.minBudgetTokens != null
        ) {
            ReasoningProfile(
                level = finalReasoningLevel,
                mapping = reasoningMapping,
                budgetTokens = effectiveReasoningBudget,
                minBudgetTokens = configuredProfile?.minBudgetTokens,
                source = configuredProfile?.source
                    ?: com.yuzhiqiang.antigravity.domain.model.ModelIdentitySource.ROUTE_SNAPSHOT
            )
        } else {
            null
        }
        val finalRequest = request.copy(
            originalModelId = requestedModelId,
            targetUpstreamModelId = binding.providerModelId,
            temperature = effectiveParameters.temperature,
            maxTokens = effectiveParameters.maxTokens,
            topP = effectiveParameters.topP,
            topK = effectiveParameters.topK,
            extraBody = effectiveParameters.extraBody.orEmpty(),
            reasoningLevel = finalReasoningLevel,
            reasoningBudgetTokens = effectiveReasoningBudget,
            reasoningMapping = reasoningMapping,
            outputModalities = request.outputModalities.ifEmpty {
                if (binding.capabilities.roles.contains(ModelRole.IMAGE_GENERATION) &&
                    !binding.capabilities.roles.contains(ModelRole.AGENT)
                ) {
                    setOf(ModelModality.IMAGE)
                } else {
                    emptySet()
                }
            }
        )
        val identity = ModelExecutionIdentity(
            requestedModelId = requestedModelId,
            variantId = variant.variantId,
            catalogModelId = ModelIdentity.catalogKey(variant),
            runtimeModelId = ModelIdentity.effectiveRuntimeModelId(variant),
            bindingId = binding.bindingId,
            providerConfigId = binding.providerConfigId,
            providerModelId = binding.providerModelId,
            canonicalModelId = binding.canonicalModelId,
            baseModelId = binding.canonicalModelId?.let { canonicalId ->
                config.canonicalModels.firstOrNull { it.canonicalModelId == canonicalId }?.baseModelId
            },
            providerVendor = binding.providerVendor,
            displayName = variant.displayName.ifBlank { binding.effectiveName },
            reasoningProfile = executionProfile,
            identityResolution = binding.identityResolution
        )
        return Result.success(
            ResolvedRoute(
                requestedModelId = requestedModelId,
                modelRouteVariant = variant,
                providerModelBinding = binding,
                provider = provider,
                modelExecutionIdentity = identity,
                originalRequest = request,
                request = finalRequest,
                finalParameters = effectiveParameters
            )
        )
    }

    private fun resolveReasoningMapping(
        provider: Provider,
        binding: ProviderModelBinding,
        configuredProfile: ReasoningProfile?,
        reasoningLevel: ReasoningLevel?,
        requestedBudget: Int?
    ): Result<ReasoningMapping?> {
        val reasoning = binding.capabilities.reasoning
        val configuredMappings = ReasoningMappingSupport.parse(reasoning.levels)
        val outputLimit = binding.tokenLimits.outputTokenLimit
        if (reasoning.supported == false && reasoningLevel != null && reasoningLevel != ReasoningLevel.OFF) {
            return Result.failure(
                IllegalArgumentException("${provider.protocol.displayName} 不支持模型 ${binding.providerModelId} 的推理")
            )
        }
        if (reasoningLevel != null) {
            if (reasoningLevel != ReasoningLevel.OFF) {
                val hasReasoningCapability = reasoning.supported == true ||
                        configuredMappings.isNotEmpty() ||
                        reasoning.thinkingBudget != null ||
                        reasoning.minThinkingBudget != null ||
                        configuredProfile?.mapping != null ||
                        configuredProfile?.budgetTokens != null
                if (!hasReasoningCapability) {
                    return Result.failure(IllegalArgumentException("模型 ${binding.providerModelId} 未声明推理能力"))
                }
            }
            val profileMapping = configuredProfile?.mapping
                ?.takeIf { configuredProfile.level == null || configuredProfile.level == reasoningLevel }
                ?.takeIf { ReasoningMappingSupport.isSupported(provider.protocol, it, outputLimit) }
            val mapping = profileMapping ?: ReasoningMappingSupport.resolveMapping(
                protocol = provider.protocol,
                level = reasoningLevel,
                configured = configuredMappings,
                outputTokenLimit = outputLimit
            )
            if (mapping == null && reasoningLevel != ReasoningLevel.OFF && reasoningLevel != ReasoningLevel.AUTO) {
                return Result.failure(
                    IllegalArgumentException("${provider.protocol.displayName} 不支持推理档位 ${reasoningLevel.label}")
                )
            }
            val finalMapping = if (
                mapping?.kind.equals("budget_tokens", ignoreCase = true) && requestedBudget != null
            ) {
                ReasoningMapping("budget_tokens", JsonPrimitive(requestedBudget))
                    .takeIf { ReasoningMappingSupport.isSupported(provider.protocol, it, outputLimit) }
                    ?: return Result.failure(
                        IllegalArgumentException("推理预算 $requestedBudget 不受 ${provider.protocol.displayName} 支持")
                    )
            } else {
                mapping
            }
            return Result.success(finalMapping)
        }

        configuredProfile?.mapping
            ?.takeIf { ReasoningMappingSupport.isSupported(provider.protocol, it, outputLimit) }
            ?.let { return Result.success(it) }
        val budget = requestedBudget ?: reasoning.thinkingBudget
        if (budget != null) {
            val mapping = ReasoningMapping("budget_tokens", JsonPrimitive(budget))
            if (ReasoningMappingSupport.isSupported(provider.protocol, mapping, outputLimit)) {
                return Result.success(mapping)
            }
        }
        return Result.success(null)
    }

    private fun findBinding(config: AppConfig, variant: ModelRouteVariant): ProviderModelBinding? {
        val bindingId = variant.bindingId ?: return null
        return config.providerModelBindings.firstOrNull { binding -> binding.bindingId == bindingId }
    }

    private fun resolveTierMember(config: AppConfig, tiered: ModelRouteVariant): ModelRouteVariant? {
        val explicitMembers = tiered.tierMemberVariantIds.mapNotNull { memberId ->
            config.modelRouteVariants.firstOrNull { variant -> variant.variantId == memberId }
        }
        val candidates = (explicitMembers.ifEmpty {
            val familyBase = ModelIdentity.catalogFamilyBase(tiered)
            config.modelRouteVariants.filter { variant ->
                variant.kind != ModelVariantKind.TIERED &&
                        variant.variantId != tiered.variantId &&
                        ModelIdentity.catalogFamilyBase(variant) == familyBase
            }
        }).filter { variant -> isConcreteVariantRoutable(config, variant) }
        return preferredVariant(candidates)
    }

    private fun resolveSyntheticTieredVariant(
        config: AppConfig,
        requestedModelId: String
    ): ModelRouteVariant? {
        val normalized = ModelIdentity.normalizeModelId(requestedModelId)
        if (!normalized.endsWith("-tiered")) return null
        val familyBase = normalized.removeSuffix("-tiered")
        val candidates = config.modelRouteVariants.filter { variant ->
            variant.kind != ModelVariantKind.TIERED &&
                    ModelIdentity.catalogFamilyBase(variant) == familyBase &&
                    isConcreteVariantRoutable(config, variant)
        }
        return preferredVariant(candidates)
    }

    private fun preferredVariant(candidates: List<ModelRouteVariant>): ModelRouteVariant? {
        if (candidates.isEmpty()) return null
        return ModelIdentity.REASONING_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
            candidates.firstOrNull { variant -> variant.reasoningProfile?.level == level }
        } ?: candidates.first()
    }

    private fun isConcreteVariantRoutable(config: AppConfig, variant: ModelRouteVariant): Boolean {
        if (!variant.enabled || variant.kind == ModelVariantKind.TIERED) return false
        val binding = findBinding(config, variant) ?: return false
        return binding.enabled && config.providers.any { provider ->
            provider.id == binding.providerConfigId && provider.enabled
        }
    }

    private fun resolveImageGenerationVariant(
        config: AppConfig,
        requestedModelId: String,
        request: NeutralChatRequest
    ): ModelRouteVariant? {
        val wantsImage = request.outputModalities.contains(ModelModality.IMAGE) ||
                isOfficialImageModelId(requestedModelId)
        if (!wantsImage) return null
        return findActiveCustomImageVariant(config)
    }

    private fun findActiveCustomImageVariant(config: AppConfig): ModelRouteVariant? {
        return config.modelRouteVariants.firstOrNull { variant ->
            if (!isConcreteVariantRoutable(config, variant)) return@firstOrNull false
            val capabilities = findBinding(config, variant)?.capabilities ?: return@firstOrNull false
            ModelRole.IMAGE_GENERATION in capabilities.roles ||
                    ModelModality.IMAGE in capabilities.outputModalities
        }
    }

    private fun isOfficialImageModelId(modelId: String): Boolean {
        val lower = modelId.lowercase()
        val keywords = listOf(
            "flash-image", "imagen", "nano-banana", "image-generation", "image_generation",
            "text-to-image", "text2image", "image-to-image", "image2image", "text-to-video",
            "text2video", "dall-e", "dalle", "gpt-image", "gpt_image", "flux", "midjourney",
            "sdxl", "stable-diffusion", "stable_diffusion", "stable-image", "recraft", "ideogram",
            "kling", "cogview", "grok-imagine", "imagine", "hunyuan-image", "hunyuan-video",
            "doubao-image", "wanx"
        )
        if (keywords.any(lower::contains)) return true
        val imageIndex = lower.indexOf("image")
        if (imageIndex < 0) return false
        val suffix = lower.substring(imageIndex + "image".length).trimStart('-', '_', ' ')
        return suffix.firstOrNull()?.let { it.isDigit() || it == 'v' } == true
    }

    private fun failure(statusCode: Int, message: String): Result<ResolvedRoute> =
        Result.failure(RouteResolutionException(statusCode, message))

    /**
     * 对 OpenAI 请求执行本地输入 Token 预检。没有可靠 tokenizer 或包含媒体时跳过，
     * 避免把估算值当作精确 Token 计数。
     */
    private fun validateInputTokenBudget(
        provider: Provider,
        binding: ProviderModelBinding,
        request: NeutralChatRequest
    ): Result<Unit> {
        if (provider.protocol != ProviderProtocol.OPENAI_CHAT_COMPLETIONS &&
            provider.protocol != ProviderProtocol.OPENAI_RESPONSES
        ) return Result.success(Unit)
        val limit = binding.tokenLimits.inputTokenLimit ?: return Result.success(Unit)
        if (binding.tokenLimits.inputTokenLimitSource != com.yuzhiqiang.antigravity.domain.model.TokenLimitSource.CATALOG &&
            binding.tokenLimits.inputTokenLimitSource != com.yuzhiqiang.antigravity.domain.model.TokenLimitSource.CONFIGURED
        ) return Result.success(Unit)
        val tokenizer = binding.tokenizer ?: return Result.success(Unit)
        val encoding = tokenizer["encoding"]?.jsonPrimitive?.contentOrNull?.lowercase()
        if (encoding !in setOf("cl100k_base", "o200k_base")) return Result.success(Unit)
        if (request.messages.any { message ->
                message.contents.any { content -> content is NeutralContent.Image }
            }) return Result.success(Unit)

        fun estimate(text: String): Long = (text.length + 3L) / 4L
        var tokens = 3L
        request.systemPrompt?.let { tokens += 4L + estimate(it) }
        request.messages.forEach { message ->
            tokens += 4L
            message.contents.forEach { content ->
                tokens += when (content) {
                    is NeutralContent.Text -> estimate(content.text)
                    is NeutralContent.Thinking -> if (provider.protocol == ProviderProtocol.OPENAI_RESPONSES) {
                        0L
                    } else {
                        8L + estimate(content.text)
                    }

                    is NeutralContent.ToolCall -> 8L + estimate(content.id) +
                            estimate(content.functionName) + estimate(content.argumentsJson)

                    is NeutralContent.ToolResult -> 8L + estimate(content.toolCallId) + estimate(content.content)
                    is NeutralContent.Image -> 0L
                }
            }
        }
        request.tools.forEach { tool ->
            tokens += 8L + estimate(tool.name) + estimate(tool.description) +
                    estimate(tool.parametersJson.toString())
        }
        request.extraBody.forEach { (key, value) ->
            tokens += 8L + estimate(key) + estimate(value.toString())
        }
        val protectedTokens = tokens + maxOf(256L, (tokens * 5L + 99L) / 100L)
        return if (protectedTokens <= limit) {
            Result.success(Unit)
        } else {
            Result.failure(
                RouteResolutionException(
                    400,
                    "本地 Token 预检拒绝请求：估算 $protectedTokens Token 超过模型输入上限 $limit"
                )
            )
        }
    }
}
