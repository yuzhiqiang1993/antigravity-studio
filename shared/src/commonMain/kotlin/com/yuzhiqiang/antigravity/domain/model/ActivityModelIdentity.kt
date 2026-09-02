package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityModelIdentity(
    @SerialName("requested_model_id") val requestedModelId: String? = null,
    @SerialName("variant_id") val variantId: String? = null,
    @SerialName("catalog_model_id") val catalogModelId: String? = null,
    @SerialName("runtime_model_id") val runtimeModelId: String? = null,
    @SerialName("binding_id") val bindingId: String? = null,
    @SerialName("provider_config_id") val providerConfigId: String? = null,
    @SerialName("provider_model_id") val providerModelId: String? = null,
    @SerialName("response_model_id") val responseModelId: String? = null,
    @SerialName("canonical_model_id") val canonicalModelId: String? = null,
    @SerialName("base_model_id") val baseModelId: String? = null,
    @SerialName("provider_vendor") val providerVendor: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("reasoning_profile") val reasoningProfile: ReasoningProfile? = null,
    @SerialName("identity_resolution") val identityResolution: ModelIdentityResolution = ModelIdentityResolution()
) {
    val primaryModelId: String?
        get() = (catalogModelId ?: runtimeModelId ?: variantId ?: providerModelId ?: responseModelId ?: requestedModelId)
            ?.trim()
            ?.removePrefix("official-variant:")
            ?.removePrefix("official-binding:")
            ?.removePrefix("variant:")
            ?.removePrefix("binding:")
            ?.removePrefix("catalog:")
            ?.removePrefix("runtime:")
            ?.removePrefix("models/")

    val groupingKey: String
        get() = when {
            !canonicalModelId.isNullOrBlank() -> "canonical:$canonicalModelId"
            !bindingId.isNullOrBlank() -> "binding:$bindingId"
            !variantId.isNullOrBlank() -> "variant:$variantId"
            !responseModelId.isNullOrBlank() -> "response:${providerConfigId.orEmpty()}:$responseModelId"
            !providerModelId.isNullOrBlank() -> "provider:${providerConfigId.orEmpty()}:$providerModelId"
            !catalogModelId.isNullOrBlank() -> "catalog:$catalogModelId"
            !runtimeModelId.isNullOrBlank() -> "runtime:$runtimeModelId"
            !requestedModelId.isNullOrBlank() -> "requested:$requestedModelId"
            else -> "unknown"
        }

    val searchTerms: List<String>
        get() = listOfNotNull(
            requestedModelId,
            variantId,
            catalogModelId,
            runtimeModelId,
            bindingId,
            providerConfigId,
            providerModelId,
            responseModelId,
            canonicalModelId,
            baseModelId,
            providerVendor,
            displayName
        )
}
