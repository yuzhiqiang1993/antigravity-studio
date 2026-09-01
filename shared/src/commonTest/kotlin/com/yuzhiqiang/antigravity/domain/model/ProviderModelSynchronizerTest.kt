package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderModelSynchronizerTest {

    @Test
    fun removingReasoningVariantKeepsRetainedVariantHostIdentity() {
        val provider = Provider(
            id = "provider",
            name = "Provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com/v1"
        )
        val lowMapping = ReasoningMapping("effort", JsonPrimitive("low"))
        val highMapping = ReasoningMapping("effort", JsonPrimitive("high"))
        val binding = ProviderModelBinding(
            bindingId = "provider-model",
            providerConfigId = provider.id,
            providerModelId = "model",
            displayName = "Model",
            capabilities = ModelCapabilities(
                reasoning = ReasoningCapability(
                    supported = true,
                    levels = ReasoningMappingSupport.encode(
                        mapOf(
                            ReasoningLevel.LOW to lowMapping,
                            ReasoningLevel.HIGH to highMapping
                        )
                    )
                )
            )
        )
        val config = AppConfig(
            providers = listOf(provider),
            providerModelBindings = listOf(binding),
            modelRouteVariants = listOf(
                ModelRouteVariant(
                    variantId = "model-low",
                    bindingId = binding.bindingId,
                    catalogModelId = "byok-model-low",
                    runtimeModelId = "MODEL_PLACEHOLDER_M400",
                    displayName = "Model (Low)",
                    kind = ModelVariantKind.REASONING_VARIANT,
                    reasoningProfile = ReasoningProfile(
                        level = ReasoningLevel.LOW,
                        source = ModelIdentitySource.PROVIDER_CATALOG
                    )
                ),
                ModelRouteVariant(
                    variantId = "model-high",
                    bindingId = binding.bindingId,
                    catalogModelId = "byok-model-high",
                    runtimeModelId = "MODEL_PLACEHOLDER_M401",
                    displayName = "Model (High)",
                    kind = ModelVariantKind.REASONING_VARIANT,
                    reasoningProfile = ReasoningProfile(
                        level = ReasoningLevel.HIGH,
                        source = ModelIdentitySource.PROVIDER_CATALOG
                    )
                )
            )
        )
        val selectedBinding = binding.copy(
            capabilities = binding.capabilities.copy(
                reasoning = binding.capabilities.reasoning.copy(
                    levels = ReasoningMappingSupport.encode(mapOf(ReasoningLevel.HIGH to highMapping))
                )
            )
        )

        val result = ProviderModelSynchronizer.synchronize(
            config = config,
            provider = provider,
            selectedBindings = listOf(selectedBinding)
        ).getOrThrow()

        val retained = result.modelRouteVariants.single()
        assertEquals(ReasoningLevel.HIGH, retained.reasoningProfile?.level)
        assertEquals("MODEL_PLACEHOLDER_M401", retained.runtimeModelId)
    }

    @Test
    fun synchronizerCreatesRouteVariantAsOnlyRuntimeIdentityOwner() {
        val provider = Provider(
            id = "provider",
            name = "Provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com/v1"
        )
        val selectedBinding = ProviderModelBinding(
            bindingId = "provider-model",
            providerConfigId = provider.id,
            providerModelId = "model",
            displayName = "Model"
        )

        val firstResult = ProviderModelSynchronizer.synchronize(
            config = AppConfig(providers = listOf(provider)),
            provider = provider,
            selectedBindings = listOf(selectedBinding)
        ).getOrThrow()

        val binding = firstResult.providerModelBindings.single()
        val variant = firstResult.modelRouteVariants.single()
        assertEquals(binding.bindingId, variant.bindingId)
        assertTrue(variant.runtimeModelId.startsWith(ModelIdentity.CUSTOM_RUNTIME_MODEL_ID_PREFIX))

        val secondResult = ProviderModelSynchronizer.synchronize(
            config = AppConfig(
                providers = listOf(provider),
                providerModelBindings = firstResult.providerModelBindings,
                modelRouteVariants = firstResult.modelRouteVariants
            ),
            provider = provider,
            selectedBindings = firstResult.providerModelBindings
        ).getOrThrow()

        assertEquals(variant.runtimeModelId, secondResult.modelRouteVariants.single().runtimeModelId)
    }
}
