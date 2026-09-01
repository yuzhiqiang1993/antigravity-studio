package com.yuzhiqiang.antigravity.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityIdentityResolverTest {
    @Test
    fun responseResolutionUsesOnlyActualResponseEvidence() {
        ModelIdentityRegistryHolder.updateConfig(identityConfig())
        try {
            val routeIdentity = ModelObservation(variantId = "variant-a").resolveActivityIdentity()

            val unknown = routeIdentity.withResponseModelId("unknown-response")
            assertEquals(ModelIdentityStatus.UNRESOLVED, unknown.identityResolution.status)
            assertEquals(ModelIdentitySource.PROVIDER_RESPONSE, unknown.identityResolution.source)
            assertEquals(ModelIdentityConfidence.UNKNOWN, unknown.identityResolution.confidence)
            assertEquals("canonical-a", unknown.canonicalModelId)

            val exact = routeIdentity.withResponseModelId("provider-model-a")
            assertEquals(ModelIdentityStatus.RESOLVED, exact.identityResolution.status)
            assertEquals(ModelIdentityConfidence.EXACT, exact.identityResolution.confidence)

            val alias = routeIdentity.withResponseModelId("response-alias-a")
            assertEquals(ModelIdentityStatus.RESOLVED, alias.identityResolution.status)
            assertEquals(ModelIdentityConfidence.REGISTERED, alias.identityResolution.confidence)

            val conflict = routeIdentity.withResponseModelId("provider-model-b")
            assertEquals(ModelIdentityStatus.CONFLICT, conflict.identityResolution.status)
            assertEquals("canonical-b", conflict.canonicalModelId)
        } finally {
            ModelIdentityRegistryHolder.updateConfig(AppConfig())
        }
    }
}

private fun identityConfig(): AppConfig = AppConfig(
    canonicalModels = listOf(
        CanonicalModel(
            canonicalModelId = "canonical-a",
            providerVendor = "vendor",
            displayName = "Canonical A"
        ),
        CanonicalModel(
            canonicalModelId = "canonical-b",
            providerVendor = "vendor",
            displayName = "Canonical B"
        )
    ),
    providerModelBindings = listOf(
        ProviderModelBinding(
            bindingId = "binding-a",
            providerConfigId = "provider-a",
            providerModelId = "provider-model-a",
            canonicalModelId = "canonical-a",
            displayName = "Binding A",
            aliases = listOf(
                ModelAlias(
                    value = "response-alias-a",
                    kind = ModelAliasKind.PROVIDER_RESPONSE
                )
            )
        ),
        ProviderModelBinding(
            bindingId = "binding-b",
            providerConfigId = "provider-a",
            providerModelId = "provider-model-b",
            canonicalModelId = "canonical-b",
            displayName = "Binding B"
        )
    ),
    modelRouteVariants = listOf(
        ModelRouteVariant(
            variantId = "variant-a",
            bindingId = "binding-a",
            catalogModelId = "catalog-a",
            runtimeModelId = "MODEL_PLACEHOLDER_M400",
            displayName = "Variant A"
        ),
        ModelRouteVariant(
            variantId = "variant-b",
            bindingId = "binding-b",
            catalogModelId = "catalog-b",
            runtimeModelId = "MODEL_PLACEHOLDER_M401",
            displayName = "Variant B"
        )
    )
)
