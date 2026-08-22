package com.yuzhiqiang.antigravity.proxy.routing

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ParameterOverrides
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import kotlinx.serialization.json.JsonPrimitive

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

        // Studio 旧配置没有对应 virtual_models 时，继续允许按 upstream 模型直连。
        val directUpstream = findUpstream(config, requestedModelId)
            ?: return failure(404, "Model is not configured: $requestedModelId")
        return buildResolvedRoute(config, requestedModelId, null, directUpstream, request)
    }

    /**
     * 只解析当前虚拟模型声明的一层备用路由；不会沿 fallback_virtual_model_id 链继续递归。
     * 备用路由仍经过完整的启用状态、Provider、Upstream 与参数合并校验。
     */
    fun resolveFallback(
        config: AppConfig,
        failedRoute: ResolvedRoute
    ): Result<ResolvedRoute?> {
        val fallbackId = failedRoute.virtualModel?.fallbackVirtualModelId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Result.success(null)
        val fallbackVirtual = config.virtualModels.firstOrNull { it.accepts(fallbackId) }
            ?: return failure(404, "Fallback virtual model is not configured: $fallbackId")
        if (failedRoute.virtualModel?.id == fallbackVirtual.id) {
            return failure(422, "Fallback virtual model must differ from the primary model")
        }
        return resolve(
            config,
            failedRoute.originalRequest.copy(
                originalModelId = normalizeModelId(fallbackId),
                // 推理档位是请求级语义；保留主路由解析出的虚拟模型默认值。
                reasoningLevel = failedRoute.request.reasoningLevel
            )
        ).fold(
            onSuccess = { Result.success<ResolvedRoute?>(it) },
            onFailure = { Result.failure(it) }
        )
    }

    fun isPotentialCustomModelId(config: AppConfig, modelId: String): Boolean {
        val normalized = normalizeModelId(modelId)
        return config.virtualModels.any { it.accepts(normalized) } ||
                config.upstreamModels.any { it.accepts(normalized) } ||
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

        val providerParameters = provider.defaultParameters
            ?: provider.parameterOverrides
            ?: ParameterOverrides()
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
        val finalRequest = request.copy(
            originalModelId = requestedModelId,
            targetUpstreamModelId = upstream.upstreamModelId,
            temperature = finalParameters.temperature,
            maxTokens = finalParameters.maxTokens,
            topP = finalParameters.topP,
            topK = finalParameters.topK,
            extraBody = finalParameters.extraBody.orEmpty(),
            reasoningLevel = finalReasoningLevel,
            reasoningBudgetTokens = effectiveReasoningBudget,
            reasoningMapping = reasoningMapping
        )
        return Result.success(
            ResolvedRoute(
                requestedModelId = requestedModelId,
                virtualModel = virtualModel,
                upstreamModel = upstream,
                provider = provider,
                originalRequest = request,
                request = finalRequest,
                finalParameters = finalParameters
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
        if (requestedBudget != null) {
            val mapping = ReasoningMapping("budget_tokens", JsonPrimitive(requestedBudget))
            return if (ReasoningMappingSupport.isSupported(provider.protocol, mapping, outputLimit)) {
                Result.success(mapping)
            } else {
                Result.failure(IllegalArgumentException("${provider.protocol.displayName} 不支持思考预算 $requestedBudget"))
            }
        }
        if (reasoningLevel == null) {
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
        val mapping = ReasoningMappingSupport.resolveMapping(
            protocol = provider.protocol,
            level = reasoningLevel,
            configured = configuredMappings,
            outputTokenLimit = outputLimit
        )
        if (mapping == null && reasoningLevel != ReasoningLevel.OFF) {
            return Result.failure(IllegalArgumentException("${provider.protocol.displayName} 不支持推理档位 ${reasoningLevel.label}"))
        }
        return Result.success(mapping)
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

    private fun normalizeModelId(value: String): String {
        return value.trim().removePrefix(MODEL_NAMESPACE_PREFIX)
    }

    private fun failure(statusCode: Int, message: String): Result<ResolvedRoute> {
        return Result.failure(RouteResolutionException(statusCode, message))
    }
}
