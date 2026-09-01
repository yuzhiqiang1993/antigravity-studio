package com.yuzhiqiang.antigravity.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelIdentityRegistryTest {
    @Test
    fun canonicalMetadataLookupUsesNormalizedId() {
        val config = AppConfig(
            canonicalModels = listOf(
                CanonicalModel(
                    canonicalModelId = "Vendor/Model-A",
                    providerVendor = "vendor",
                    displayName = "Canonical Model A",
                    pricingAliases = listOf("vendor/model-a-pricing")
                )
            ),
            providerModelBindings = listOf(
                ProviderModelBinding(
                    bindingId = "binding-a",
                    providerConfigId = "provider-a",
                    providerModelId = "vendor/model-a",
                    canonicalModelId = "Vendor/Model-A",
                    displayName = "Binding Model A"
                )
            ),
            modelRouteVariants = listOf(
                ModelRouteVariant(
                    variantId = "variant-a",
                    bindingId = "binding-a",
                    catalogModelId = "catalog-a",
                    runtimeModelId = "MODEL_PLACEHOLDER_M400",
                    displayName = "Variant Model A"
                )
            )
        )

        val resolved = ModelIdentityRegistry.from(config).resolve(
            ModelObservation(variantId = "variant-a")
        )

        assertEquals("Canonical Model A", resolved.displayName)
        assertTrue("vendor/model-a-pricing" in resolved.pricingModelIds)
        assertTrue("Vendor/Model-A" in resolved.pricingModelIds)
    }
}
