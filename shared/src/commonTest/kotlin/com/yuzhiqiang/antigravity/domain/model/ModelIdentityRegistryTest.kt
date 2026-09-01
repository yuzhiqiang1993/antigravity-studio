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

    @Test
    fun officialReasoningVariantsShareProviderResponseCanonicalIdentity() {
        val registry = ModelIdentityRegistry.from(
            config = AppConfig(),
            officialModels = listOf(
                OfficialCatalogModel(
                    catalogModelId = "gemini-3.7-flash-high",
                    runtimeModelId = "MODEL_PLACEHOLDER_M298",
                    displayName = "Gemini 3.7 Flash (Thinking High)",
                    providerVendor = "MODEL_PROVIDER_GOOGLE"
                ),
                OfficialCatalogModel(
                    catalogModelId = "gemini-3.7-flash-low",
                    runtimeModelId = "MODEL_PLACEHOLDER_M300",
                    displayName = "Gemini 3.7 Flash (Low)",
                    providerVendor = "MODEL_PROVIDER_GOOGLE"
                )
            )
        )

        val high = registry.resolve(
            ModelObservation(
                runtimeModelId = "MODEL_PLACEHOLDER_M298",
                responseModelId = "gemini-3.7-flash"
            )
        )
        val low = registry.resolve(
            ModelObservation(
                runtimeModelId = "MODEL_PLACEHOLDER_M300",
                responseModelId = "gemini-3.7-flash-control"
            )
        )

        assertEquals(ModelIdentityStatus.RESOLVED, high.status)
        assertEquals("google/gemini-3.7-flash", high.canonicalModelId)
        assertEquals("Gemini 3.7 Flash", high.displayName)
        assertEquals(listOf("google/gemini-3.7-flash"), high.pricingModelIds)
        assertEquals(high.canonicalModelId, low.canonicalModelId)
        assertEquals(high.groupingKey, low.groupingKey)
        assertEquals(high.displayName, low.displayName)
        assertTrue(high.variantId != low.variantId)

        val responseOnly = registry.resolve(ModelObservation(responseModelId = "gemini-3.7-flash"))
        val responseAliasOnly = registry.resolve(ModelObservation(responseModelId = "gemini-3.7-flash-safety-le"))
        assertEquals("google/gemini-3.7-flash", responseOnly.canonicalModelId)
        assertEquals("Gemini 3.7 Flash", responseOnly.displayName)
        assertEquals(null, responseOnly.variantId)
        assertEquals(responseOnly.groupingKey, responseAliasOnly.groupingKey)
    }

    @Test
    fun knownProviderFamiliesResolveHistoricalResponseModelsWithoutCurrentRoute() {
        val registry = ModelIdentityRegistry.empty()
        val cases = listOf(
            Triple("gpt-5.6-sol", "openai/gpt-5.6-sol", "GPT 5.6 Sol"),
            Triple("gpt-5.6-sol-high", "openai/gpt-5.6-sol", "GPT 5.6 Sol"),
            Triple("gpt-5.6-luna", "openai/gpt-5.6-luna", "GPT 5.6 Luna"),
            Triple("claude-opus-4-6-thinking", "anthropic/claude-opus-4-6", "Claude Opus 4.6"),
            Triple("claude-fable-5", "anthropic/claude-fable-5", "Claude Fable 5"),
            Triple("claude-opus-5", "anthropic/claude-opus-5", "Claude Opus 5"),
            Triple("grok-4.6-high", "xai/grok-4.6", "Grok 4.6")
        )

        cases.forEach { (responseModelId, expectedCanonicalId, expectedDisplayName) ->
            val resolved = registry.resolve(
                ModelObservation(
                    responseModelId = responseModelId,
                    displayName = "$expectedDisplayName (High)"
                )
            )
            assertEquals(ModelIdentityStatus.RESOLVED, resolved.status, responseModelId)
            assertEquals(expectedCanonicalId, resolved.canonicalModelId, responseModelId)
            assertEquals(expectedDisplayName, resolved.displayName, responseModelId)
            assertEquals(listOf(expectedCanonicalId), resolved.pricingModelIds, responseModelId)
        }
    }

    @Test
    fun officialCanonicalAndVertexDeploymentIdsAreProviderQualified() {
        val registry = ModelIdentityRegistry.from(
            config = AppConfig(),
            officialModels = listOf(
                OfficialCatalogModel(
                    catalogModelId = "claude-opus-route",
                    runtimeModelId = "MODEL_PLACEHOLDER_M26",
                    providerModelId = "claude-opus-4-6@default",
                    canonicalModelId = "claude-opus-4-6",
                    displayName = "Claude Opus 4.6 (Thinking)",
                    providerVendor = "MODEL_PROVIDER_ANTHROPIC"
                )
            )
        )

        val resolved = registry.resolve(ModelObservation(runtimeModelId = "MODEL_PLACEHOLDER_M26"))

        assertEquals("anthropic/claude-opus-4-6", resolved.canonicalModelId)
        assertEquals("Claude Opus 4.6", resolved.displayName)
        assertEquals(listOf("anthropic/claude-opus-4-6"), resolved.pricingModelIds)
    }

    @Test
    fun officialReasoningRouteWithoutResponseIdentityUsesCatalogFamilyCanonical() {
        val registry = ModelIdentityRegistry.from(
            config = AppConfig(),
            officialModels = listOf(
                OfficialCatalogModel(
                    catalogModelId = "gemini-3.7-flash-high",
                    runtimeModelId = "MODEL_PLACEHOLDER_M298",
                    displayName = "Gemini 3.7 Flash (High)",
                    providerVendor = "MODEL_PROVIDER_GOOGLE"
                )
            )
        )

        val resolved = registry.resolve(ModelObservation(runtimeModelId = "MODEL_PLACEHOLDER_M298"))

        assertEquals(ModelIdentityStatus.RESOLVED, resolved.status)
        assertEquals("google/gemini-3.7-flash", resolved.canonicalModelId)
        assertEquals("Gemini 3.7 Flash", resolved.displayName)
        assertEquals(listOf("google/gemini-3.7-flash"), resolved.pricingModelIds)
    }
}
