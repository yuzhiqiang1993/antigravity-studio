package com.yuzhiqiang.antigravity.proxy.routing

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 路由解析失败时携带可直接映射为 HTTP 4xx 的状态码。 */
class RouteResolutionException(
    val statusCode: Int,
    message: String
) : IllegalArgumentException(message)

data class ResolvedRoute(
    val requestedModelId: String,
    val virtualModel: VirtualModel?,
    val upstreamModel: UpstreamModel,
    val provider: Provider,
    /** 进入路由器时的请求；不含当前路由的参数默认值，供备用路由重新合并。 */
    val originalRequest: NeutralChatRequest,
    val request: NeutralChatRequest,
    val finalParameters: ParameterOverrides
)

/** 按 VirtualModel -> UpstreamModel -> Provider 解析请求，并完成参数优先级合并。 */
object RouteResolver {
    private const val MODEL_NAMESPACE_PREFIX = "models/"

    fun resolve(config: AppConfig, request: NeutralChatRequest): Result<ResolvedRoute> {
        val requestedModelId = normalizeModelId(request.originalModelId)
        val matchedVirtual = config.virtualModels.firstOrNull { it.accepts(requestedModelId) }
        if (matchedVirtual != null) {
            if (!matchedVirtual.enabled) {
                return failure(404, "Virtual model is disabled: $requestedModelId")
            }
            val upstream = findUpstream(config, matchedVirtual.upstreamModelId)
                ?: return failure(
                    404,
                    "Virtual model is not linked to an enabled upstream model: ${matchedVirtual.id}"
                )
            return buildResolvedRoute(config, requestedModelId, matchedVirtual, upstream, request)
        }

        // 新版宿主会请求 tiered 母条目或未带推理后缀的模型族 ID；按 byok 的优先级
        // 选择一个可路由的具体档位，但未知 ID 不做模糊猜测。
        val tieredVirtual = resolveTieredVirtualModel(config, requestedModelId)
        if (tieredVirtual != null) {
            val upstream = findUpstream(config, tieredVirtual.upstreamModelId)
                ?: return failure(404, "Tiered model is not linked to an enabled upstream model: ${tieredVirtual.id}")
            return buildResolvedRoute(config, requestedModelId, tieredVirtual, upstream, request)
        }

        // 官方图片模型 ID 或显式图片输出请求可以重定向到已配置的自定义生图模型。
        val imageVirtual = resolveImageGenerationVirtualModel(config, requestedModelId, request)
        if (imageVirtual != null) {
            val upstream = findUpstream(config, imageVirtual.upstreamModelId)
                ?: return failure(404, "Image model is not linked to an enabled upstream model: ${imageVirtual.id}")
            return buildResolvedRoute(config, requestedModelId, imageVirtual, upstream, request)
        }

        // Studio 旧配置没有对应 virtual_models 时，继续允许按 upstream 模型直连。
        val directUpstream = findUpstream(config, requestedModelId)
            ?: return failure(404, "Model is not configured: $requestedModelId")
        return buildResolvedRoute(config, requestedModelId, null, directUpstream, request)
    }


    fun isPotentialCustomModelId(config: AppConfig, modelId: String): Boolean {
        val normalized = normalizeModelId(modelId)
        return config.virtualModels.any { it.accepts(normalized) } ||
                config.upstreamModels.any { it.accepts(normalized) } ||
                resolveTieredVirtualModel(config, normalized) != null ||
                (isOfficialImageModelId(normalized) && findActiveCustomImageModel(config) != null) ||
                normalized.startsWith("byok-") ||
                normalized.startsWith("custom-") ||
                normalized.startsWith(ModelIdentity.CUSTOM_HOST_MODEL_ID_PREFIX)
    }

    fun isRoutableVirtualModel(config: AppConfig, virtualModel: VirtualModel): Boolean {
        if (!virtualModel.enabled) return false
        val upstream = findUpstream(config, virtualModel.upstreamModelId) ?: return false
        return upstream.enabled && config.providers.any {
            it.id == upstream.providerId && it.enabled
        }
    }

    fun effectiveHostModelId(virtualModel: VirtualModel): String {
        return ModelIdentity.effectiveHostModelId(virtualModel)
    }

    fun catalogKey(virtualModel: VirtualModel): String {
        return ModelIdentity.catalogKey(virtualModel)
    }

    fun acceptedIds(virtualModel: VirtualModel): List<String> {
        return ModelIdentity.acceptedIds(virtualModel)
    }

    private fun buildResolvedRoute(
        config: AppConfig,
        requestedModelId: String,
        virtualModel: VirtualModel?,
        upstream: UpstreamModel,
        request: NeutralChatRequest
    ): Result<ResolvedRoute> {
        if (!upstream.enabled) {
            return failure(404, "Upstream model is disabled: ${upstream.id}")
        }
        val provider = config.providers.firstOrNull { it.id == upstream.providerId }
            ?: return failure(422, "Provider is not configured: ${upstream.providerId}")
        if (!provider.enabled) {
            return failure(422, "Provider is disabled: ${provider.name}")
        }

        // Studio 同时承接 default_parameters 与历史 parameter_overrides 时，
        // 两者都应参与 Provider 层合并，不能因 default_parameters 存在而丢掉另一层。
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
            .mergeWith(upstream.parameterOverrides)
            .mergeWith(virtualModel?.parameterOverrides)
            .mergeWith(requestParameters)
            .withoutControlledExtraBody()
        val finalReasoningLevel = request.reasoningLevel ?: virtualModel?.defaultReasoningLevel
        validateInputTokenBudget(
            provider,
            upstream,
            request.copy(extraBody = finalParameters.extraBody.orEmpty())
        ).onFailure { error ->
            return Result.failure(error)
        }
        val reasoningMappingResult = resolveReasoningMapping(
            provider = provider,
            upstream = upstream,
            reasoningLevel = finalReasoningLevel,
            requestedBudget = request.reasoningBudgetTokens
        )
        if (reasoningMappingResult.isFailure) {
            return Result.failure(
                reasoningMappingResult.exceptionOrNull() ?: IllegalArgumentException("Reasoning mapping is invalid")
            )
        }
        val reasoningMapping = reasoningMappingResult.getOrNull()
        val effectiveReasoningBudget = request.reasoningBudgetTokens
            ?: reasoningMapping?.let(ReasoningMappingSupport::mappingValueAsInt)
        val effectiveMaxTokens = finalParameters.maxTokens ?: if (
            provider.protocol == ProviderProtocol.ANTHROPIC_MESSAGES &&
                reasoningMapping?.kind.equals("budget_tokens", ignoreCase = true)
        ) {
            val budget = (effectiveReasoningBudget ?: 0).toLong()
            val generated = (budget + 4_096L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            upstream.tokenLimits.outputTokenLimit
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?.coerceAtMost(generated)
                ?: generated
        } else {
            null
        }
        val effectiveParameters = finalParameters.copy(maxTokens = effectiveMaxTokens)
        val finalRequest = request.copy(
            originalModelId = requestedModelId,
            targetUpstreamModelId = upstream.upstreamModelId,
            temperature = effectiveParameters.temperature,
            maxTokens = effectiveParameters.maxTokens,
            topP = effectiveParameters.topP,
            topK = effectiveParameters.topK,
            extraBody = effectiveParameters.extraBody.orEmpty(),
            reasoningLevel = finalReasoningLevel,
            reasoningBudgetTokens = effectiveReasoningBudget,
            reasoningMapping = reasoningMapping,
            outputModalities = request.outputModalities.ifEmpty {
                if (upstream.capabilities.roles.contains(ModelRole.IMAGE_GENERATION) &&
                    !upstream.capabilities.roles.contains(ModelRole.AGENT)
                ) {
                    setOf(ModelModality.IMAGE)
                } else {
                    emptySet()
                }
            }
        )
        return Result.success(
            ResolvedRoute(
                requestedModelId = requestedModelId,
                virtualModel = virtualModel,
                upstreamModel = upstream,
                provider = provider,
                originalRequest = request,
                request = finalRequest,
                finalParameters = effectiveParameters
            )
        )
    }

    private fun resolveReasoningMapping(
        provider: Provider,
        upstream: UpstreamModel,
        reasoningLevel: ReasoningLevel?,
        requestedBudget: Int?
    ): Result<ReasoningMapping?> {
        val reasoning = upstream.capabilities.reasoning
        val configuredMappings = ReasoningMappingSupport.parse(reasoning.levels)
        val outputLimit = upstream.tokenLimits.outputTokenLimit
        if (reasoning.supported == false &&
            (reasoningLevel != null && reasoningLevel != ReasoningLevel.OFF)
        ) {
            return Result.failure(IllegalArgumentException("${provider.protocol.displayName} 不支持模型 ${upstream.upstreamModelId} 的推理"))
        }
        if (reasoningLevel != null) {
            if (reasoningLevel != ReasoningLevel.OFF) {
                if (reasoning.supported == false) {
                    return Result.failure(IllegalArgumentException("${provider.protocol.displayName} 不支持模型 ${upstream.upstreamModelId} 的推理"))
                }
                val hasReasoningCapability = reasoning.supported == true ||
                        configuredMappings.isNotEmpty() ||
                        reasoning.thinkingBudget != null ||
                        reasoning.minThinkingBudget != null
                if (!hasReasoningCapability) {
                    return Result.failure(IllegalArgumentException("模型 ${upstream.upstreamModelId} 未声明推理能力"))
                }
            }
            val mapping = ReasoningMappingSupport.resolveMapping(
                protocol = provider.protocol,
                level = reasoningLevel,
                configured = configuredMappings,
                outputTokenLimit = outputLimit
            )
            if (mapping == null && reasoningLevel != ReasoningLevel.OFF && reasoningLevel != ReasoningLevel.AUTO) {
                return Result.failure(IllegalArgumentException("${provider.protocol.displayName} 不支持推理档位 ${reasoningLevel.label}"))
            }
            val finalMapping = if (mapping?.kind.equals("budget_tokens", ignoreCase = true) && requestedBudget != null && requestedBudget > 0) {
                ReasoningMapping("budget_tokens", JsonPrimitive(requestedBudget))
            } else {
                mapping
            }
            return Result.success(finalMapping)
        }

        if (requestedBudget != null && requestedBudget > 0) {
            val mapping = ReasoningMapping("budget_tokens", JsonPrimitive(requestedBudget))
            if (ReasoningMappingSupport.isSupported(provider.protocol, mapping, outputLimit)) {
                return Result.success(mapping)
            }
        }
        if (provider.protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
            val budget = reasoning.thinkingBudget
            if (budget != null) {
                val mapping = ReasoningMapping("budget_tokens", JsonPrimitive(budget))
                if (ReasoningMappingSupport.isSupported(provider.protocol, mapping, outputLimit)) {
                    return Result.success(mapping)
                }
            }
        }
        return Result.success(null)
    }

    private fun findUpstream(config: AppConfig, modelId: String): UpstreamModel? {
        val normalized = normalizeModelId(modelId)
        val strippedByok = normalized.removePrefix("byok-")
        return config.upstreamModels.firstOrNull { upstream ->
            val accepted = setOf(
                normalizeModelId(upstream.id),
                normalizeModelId(upstream.upstreamModelId),
                ModelIdentity.effectiveUpstreamHostModelId(upstream).let(::normalizeModelId)
            )
            normalized in accepted || strippedByok in accepted
        }
    }

    private fun UpstreamModel.accepts(modelId: String): Boolean {
        val normalized = normalizeModelId(modelId)
        return normalized == normalizeModelId(id) ||
                normalized == normalizeModelId(upstreamModelId) ||
                normalized == ModelIdentity.effectiveUpstreamHostModelId(this).let(::normalizeModelId)
    }

    private fun VirtualModel.accepts(modelId: String): Boolean {
        return normalizeModelId(modelId) in acceptedIds(this)
    }

   private fun resolveTieredVirtualModel(config: AppConfig, requestedModelId: String): VirtualModel? {
       val cleanId = normalizeModelId(requestedModelId)
       if (config.virtualModels.any { it.accepts(cleanId) }) return null
       val isTieredParent = cleanId.endsWith("-tiered")
        val baseId = if (isTieredParent) {
            cleanId.removeSuffix("-tiered")
        } else {
            val base = ModelIdentity.stripReasoningLevelSuffix(cleanId)
            // 具体推理档位必须精确命中，只有母条目/无后缀族名才允许选择默认档位。
            if (base != cleanId) return null
            base
        }
        val tieredFamilyBases = if (isTieredParent) {
            config.virtualModels
                .filter { ModelIdentity.matchesFamilyBase(it, baseId) }
                .map { ModelIdentity.catalogFamilyBase(it) }
        } else {
            emptyList()
        }
       val candidates = config.virtualModels.filter { virtual ->
           isRoutableVirtualModel(config, virtual) &&
                    (ModelIdentity.matchesFamilyBase(virtual, baseId) ||
                            (isTieredParent && tieredFamilyBases.any { ModelIdentity.catalogFamilyBase(virtual) == it }))
       }
       if (candidates.isEmpty()) return null
        return ModelIdentity.REASONING_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
           candidates.firstOrNull { it.defaultReasoningLevel == level }
       } ?: candidates.first()
   }

    private fun resolveImageGenerationVirtualModel(
        config: AppConfig,
        requestedModelId: String,
        request: NeutralChatRequest
    ): VirtualModel? {
        val wantsImage = request.outputModalities.contains(ModelModality.IMAGE) ||
                isOfficialImageModelId(requestedModelId)
        if (!wantsImage) return null
        return findActiveCustomImageModel(config)
    }

    private fun findActiveCustomImageModel(config: AppConfig): VirtualModel? {
        return config.virtualModels.firstOrNull { virtual ->
                    isRoutableVirtualModel(config, virtual) &&
                    config.upstreamModels.firstOrNull { it.id == virtual.upstreamModelId }
                        ?.capabilities
                        ?.let { capabilities ->
                            ModelRole.IMAGE_GENERATION in capabilities.roles ||
                                    ModelModality.IMAGE in capabilities.outputModalities
                        } == true
        }
    }

    private fun isOfficialImageModelId(modelId: String): Boolean {
        val lower = modelId.lowercase()
        val keywords = listOf(
            "flash-image", "imagen", "nano-banana", "image-generation", "image_generation",
            "text-to-image", "text2image", "image-to-image", "image2image", "text-to-video",
            "text2video", "dall-e", "dalle", "gpt-image", "gpt_image", "flux", "midjourney",
            "sdxl", "stable-diffusion", "stable_diffusion", "stable-image", "recraft", "ideogram",
            "kling", "cogview", "grok-imagine", "imagine", "hunyuan-image", "hunyuan-video", "doubao-image", "wanx"
        )
        if (keywords.any(lower::contains)) return true
        val imageIndex = lower.indexOf("image")
        if (imageIndex < 0) return false
        val suffix = lower.substring(imageIndex + "image".length)
            .trimStart('-', '_', ' ')
        return suffix.firstOrNull()?.let { it.isDigit() || it == 'v' } == true
    }

    private fun normalizeModelId(value: String): String {
        return value.trim().removePrefix(MODEL_NAMESPACE_PREFIX)
    }

    private fun failure(statusCode: Int, message: String): Result<ResolvedRoute> {
        return Result.failure(RouteResolutionException(statusCode, message))
    }

    /**
     * byok 对 OpenAI 请求执行本地输入 Token 预检。Studio 没有引入 tiktoken 运行库，
     * 这里使用保守字符估算并保留 5%/256 Token 安全余量；包含媒体时跳过，避免误拒绝。
     */
    private fun validateInputTokenBudget(
        provider: Provider,
        upstream: UpstreamModel,
        request: NeutralChatRequest
    ): Result<Unit> {
        if (provider.protocol != ProviderProtocol.OPENAI_CHAT_COMPLETIONS &&
            provider.protocol != ProviderProtocol.OPENAI_RESPONSES
        ) return Result.success(Unit)
        val limit = upstream.tokenLimits.inputTokenLimit ?: return Result.success(Unit)
        if (upstream.tokenLimits.inputTokenLimitSource != com.yuzhiqiang.antigravity.domain.model.TokenLimitSource.CATALOG &&
            upstream.tokenLimits.inputTokenLimitSource != com.yuzhiqiang.antigravity.domain.model.TokenLimitSource.CONFIGURED
        ) return Result.success(Unit)
        val tokenizer = upstream.tokenizer as? JsonObject ?: return Result.success(Unit)
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
                    is NeutralContent.ToolCall -> 8L + estimate(content.id) + estimate(content.functionName) + estimate(content.argumentsJson)
                    is NeutralContent.ToolResult -> 8L + estimate(content.toolCallId) + estimate(content.content)
                    is NeutralContent.Image -> 0L
                }
            }
        }
        request.tools.forEach { tool ->
            tokens += 8L + estimate(tool.name) + estimate(tool.description) + estimate(tool.parametersJson.toString())
        }
        request.extraBody.forEach { (key, value) ->
            tokens += 8L + estimate(key) + estimate(value.toString())
        }
        val protected = tokens + maxOf(256L, (tokens * 5L + 99L) / 100L)
        return if (protected <= limit) {
            Result.success(Unit)
        } else {
            Result.failure(
                RouteResolutionException(
                    400,
                    "本地 Token 预检拒绝请求：估算 ${protected} Token 超过模型输入上限 ${limit}"
                )
            )
        }
    }
}
