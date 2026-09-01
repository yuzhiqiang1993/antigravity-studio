package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
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
        val bindings = config.providerModelBindings.map { binding ->
            binding.copy(
                providerModelId = binding.providerModelId.trim(),
                canonicalModelId = binding.canonicalModelId?.trim()?.takeIf(String::isNotEmpty),
                providerVendor = binding.providerVendor?.trim()?.takeIf(String::isNotEmpty),
                displayName = binding.displayName.trim(),
                aliases = binding.aliases
                    .map { alias -> alias.copy(value = alias.value.trim()) }
                    .filter { alias -> alias.value.isNotEmpty() }
                    .distinctBy { alias -> alias.kind to alias.value },
                capabilities = normalizeCapabilities(binding.capabilities)
            )
        }
        val variants = config.modelRouteVariants.map { variant ->
            variant.copy(
                catalogModelId = ModelIdentity.normalizeModelId(variant.catalogModelId),
                runtimeModelId = ModelIdentity.normalizeModelId(variant.runtimeModelId),
                displayName = variant.displayName.trim()
            )
        }
        return config.copy(
            outboundProxy = config.outboundProxy.copy(host = config.outboundProxy.host.trim()),
            providers = providers,
            canonicalModels = config.canonicalModels.map { model ->
                model.copy(
                    canonicalModelId = model.canonicalModelId.trim(),
                    providerVendor = model.providerVendor.trim(),
                    baseModelId = model.baseModelId?.trim()?.takeIf(String::isNotEmpty),
                    version = model.version?.trim()?.takeIf(String::isNotEmpty),
                    displayName = model.displayName.trim(),
                    pricingAliases = model.pricingAliases.map(String::trim).filter(String::isNotEmpty).distinct()
                )
            },
            providerModelBindings = bindings,
            modelRouteVariants = variants,
            disabledOfficialCatalogModelIds = config.disabledOfficialCatalogModelIds
                .map(ModelIdentity::normalizeModelId)
                .filter(String::isNotEmpty)
                .distinct()
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
