package com.yuzhiqiang.antigravity.domain.model

fun ModelObservation.resolveActivityIdentity(): ActivityModelIdentity {
    val observedResponseModelId = responseModelId?.trim()?.takeIf(String::isNotEmpty)
    if (observedResponseModelId != null) {
        return copy(responseModelId = null)
            .resolveActivityIdentity()
            .withResponseModelId(observedResponseModelId)
    }
    val resolved = ModelIdentityRegistryHolder.snapshot().resolve(this)
    val source = when {
        !responseModelId.isNullOrBlank() -> ModelIdentitySource.PROVIDER_RESPONSE
        !variantId.isNullOrBlank() || !bindingId.isNullOrBlank() -> ModelIdentitySource.ROUTE_SNAPSHOT
        !providerModelId.isNullOrBlank() -> ModelIdentitySource.PROVIDER_CATALOG
        else -> ModelIdentitySource.UNKNOWN
    }
    return ActivityModelIdentity(
        requestedModelId = requestedModelId,
        variantId = resolved.variantId ?: variantId,
        catalogModelId = resolved.catalogModelId ?: catalogModelId,
        runtimeModelId = resolved.runtimeModelId ?: runtimeModelId,
        bindingId = resolved.bindingId ?: bindingId,
        providerConfigId = resolved.providerConfigId ?: providerConfigId,
        providerModelId = resolved.providerModelId ?: providerModelId,
        responseModelId = responseModelId,
        canonicalModelId = resolved.canonicalModelId,
        baseModelId = resolved.baseModelId,
        providerVendor = providerVendor,
        displayName = resolved.displayName.takeUnless { it == "Unknown" } ?: displayName,
        reasoningProfile = resolved.reasoningProfile,
        identityResolution = ModelIdentityResolution(
            status = resolved.status,
            source = source,
            confidence = if (resolved.status == ModelIdentityStatus.RESOLVED) {
                ModelIdentityConfidence.EXACT
            } else {
                ModelIdentityConfidence.UNKNOWN
            }
        )
    )
}

internal fun ActivityModelIdentity.withResponseModelId(value: String?): ActivityModelIdentity {
    val responseId = value?.trim()?.takeIf(String::isNotEmpty) ?: return this
    val resolved = ModelIdentityRegistryHolder.snapshot().resolve(
        ModelObservation(
            providerConfigId = providerConfigId,
            responseModelId = responseId,
            providerVendor = providerVendor
        )
    )
    val conflictsWithRoute = resolved.status == ModelIdentityStatus.RESOLVED && when {
        !canonicalModelId.isNullOrBlank() && !resolved.canonicalModelId.isNullOrBlank() ->
            !sameModelId(canonicalModelId, resolved.canonicalModelId)

        canonicalModelId.isNullOrBlank() && resolved.canonicalModelId.isNullOrBlank() &&
                !bindingId.isNullOrBlank() && !resolved.bindingId.isNullOrBlank() ->
            !sameModelId(bindingId, resolved.bindingId)

        else -> false
    }
    val status = if (conflictsWithRoute) ModelIdentityStatus.CONFLICT else resolved.status
    val confidence = when {
        resolved.status != ModelIdentityStatus.RESOLVED -> ModelIdentityConfidence.UNKNOWN
        sameModelId(responseId, resolved.providerModelId) -> ModelIdentityConfidence.EXACT
        else -> ModelIdentityConfidence.REGISTERED
    }
    return copy(
        responseModelId = responseId,
        canonicalModelId = resolved.canonicalModelId ?: canonicalModelId,
        baseModelId = resolved.baseModelId ?: baseModelId,
        identityResolution = ModelIdentityResolution(
            status = status,
            source = ModelIdentitySource.PROVIDER_RESPONSE,
            confidence = confidence
        )
    )
}

private fun sameModelId(first: String?, second: String?): Boolean {
    val normalizedFirst = first?.trim()?.removePrefix("models/")
    val normalizedSecond = second?.trim()?.removePrefix("models/")
    return !normalizedFirst.isNullOrEmpty() && normalizedFirst.equals(normalizedSecond, ignoreCase = true)
}
