package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol

/**
 * 负责将配置中的运行时扩展字段归一化为稳定的领域对象。
 *
 * 这里只做格式标准化，不补齐缺失能力；不符合当前契约的配置由校验直接拒绝。
 */
internal object ConfigStoreNormalizer {
    fun normalize(config: AppConfig): AppConfig {
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
            outboundProxy = config.outboundProxy.copy(host = config.outboundProxy.host.trim()),
            providers = providers,
            upstreamModels = upstreams,
            virtualModels = virtuals
        )
    }

    private fun normalizeCapabilities(capabilities: ModelCapabilities): ModelCapabilities {
        return capabilities.copy(
            roles = capabilities.roles.distinct(),
            inputModalities = capabilities.inputModalities.distinct(),
            outputModalities = capabilities.outputModalities.distinct(),
            inputMimeTypes = capabilities.inputMimeTypes
                .map { mime -> mime.trim().lowercase() }
                .distinct()
                .sorted()
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
}
