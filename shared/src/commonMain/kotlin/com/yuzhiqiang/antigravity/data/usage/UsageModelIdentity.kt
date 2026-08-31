package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import java.text.Normalizer

/**
 * 本地会话中的模型身份证据。
 *
 * field 19 是响应/计费模型，field 21 是用户可见名称，field 20 的 model_enum
 * 是运行时路由标识。三者含义不同，不能压缩为一个字符串，否则占位符会被
 * 错误计费，或不同真实模型会被错误合并。
 */
data class UsageModelIdentity(
    val model: String,
    val displayName: String? = null,
    val canonicalId: String? = null,
    val runtimeId: String? = null,
    val aggregationId: String? = null,
    val pricingModelIds: List<String> = emptyList(),
    val evidenceSource: String = "unknown"
)

object UsageModelIdentityResolver {
    private val missingValues = setOf("", "unknown", "?")
    private val opaqueModelPattern = Regex("^MODEL_(?:PLACEHOLDER|CHAT)_", RegexOption.IGNORE_CASE)
    private val customModelPattern = Regex("^custom-[a-z0-9-]+$", RegexOption.IGNORE_CASE)

    fun resolve(
        responseModel: String?,
        displayName: String?,
        runtimeModelId: String?
    ): UsageModelIdentity {
        val normalizedResponse = knownValue(responseModel)
        val normalizedDisplay = knownValue(displayName)
        val normalizedRuntime = knownValue(runtimeModelId)
        val concrete = when {
            normalizedResponse != null && !isOpaqueModelReference(normalizedResponse) ->
                normalizedResponse to "response-model"

            normalizedDisplay != null && !isOpaqueModelReference(normalizedDisplay) ->
                normalizedDisplay to "display-name"

            else -> null
        }
        val runtime = normalizedRuntime
            ?: listOfNotNull(normalizedResponse, normalizedDisplay)
                .firstOrNull(::isOpaqueModelReference)

        if (concrete != null) {
            val (model, evidenceSource) = concrete
            val aggregationId = normalizedDisplay
                ?.takeUnless(::isOpaqueModelReference)
                ?.let(::createDisplayAggregationId)
                ?: "session:$model"
            return UsageModelIdentity(
                model = model,
                displayName = normalizedDisplay?.takeUnless(::isOpaqueModelReference),
                canonicalId = model,
                runtimeId = runtime,
                aggregationId = aggregationId,
                pricingModelIds = listOf(model),
                evidenceSource = evidenceSource
            )
        }

        if (runtime != null) {
            return UsageModelIdentity(
                model = runtime,
                runtimeId = runtime,
                aggregationId = "session:$runtime",
                evidenceSource = "runtime-model"
            )
        }

        return UsageModelIdentity(model = "unknown")
    }

    fun fromEntry(entry: TokenEntry): UsageModelIdentity {
        val model = knownValue(entry.model)
        val displayName = knownValue(entry.modelDisplayName)
        val canonicalId = knownValue(entry.modelCanonicalId)
        val catalogId = knownValue(entry.modelCatalogId)
        val runtimeId = knownValue(entry.modelRuntimeId)
        val concrete = when {
            canonicalId != null && !isOpaqueModelReference(canonicalId) -> canonicalId
            catalogId != null && !isOpaqueModelReference(catalogId) -> catalogId
            model != null && !isOpaqueModelReference(model) -> model
            displayName != null && !isOpaqueModelReference(displayName) -> displayName
            else -> null
        }
        val resolvedDisplayName = displayName?.takeUnless(::isOpaqueModelReference)
        val fallbackModel = model ?: runtimeId ?: "unknown"
        val aggregationId = knownValue(entry.modelAggregationId)
            ?: resolvedDisplayName?.let(::createDisplayAggregationId)
            ?: concrete?.let { "session:$it" }
            ?: "session:$fallbackModel"
        val pricingIds = (
                entry.modelPricingIds
                    .mapNotNull(::knownValue)
                    .filterNot(::isOpaqueModelReference) +
                        listOfNotNull(canonicalId, catalogId, concrete)
                            .filterNot(::isOpaqueModelReference)
                ).distinct()
        return UsageModelIdentity(
            model = model ?: concrete ?: fallbackModel,
            displayName = resolvedDisplayName,
            canonicalId = canonicalId?.takeUnless(::isOpaqueModelReference)
                ?: catalogId?.takeUnless(::isOpaqueModelReference)
                ?: concrete?.takeUnless(::isOpaqueModelReference),
            runtimeId = runtimeId,
            aggregationId = aggregationId,
            pricingModelIds = pricingIds,
            evidenceSource = knownValue(entry.modelEvidenceSource) ?: "unknown"
        )
    }

    fun isOpaqueModelReference(value: String): Boolean {
        val normalized = value.trim()
        return opaqueModelPattern.matches(normalized) || customModelPattern.matches(normalized)
    }

    fun createDisplayAggregationId(displayName: String): String? {
        val normalized = Normalizer.normalize(displayName, Normalizer.Form.NFKC)
            .trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
        return normalized.takeIf { it.isNotEmpty() }?.let { "session-display:$it" }
    }

    private fun knownValue(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeUnless { it.lowercase() in missingValues }
    }

}
