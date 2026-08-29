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
        val upstream = UpstreamModel(
            id = "provider-model",
            providerId = provider.id,
            upstreamModelId = "model",
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
            upstreamModels = listOf(upstream),
            virtualModels = listOf(
                VirtualModel(
                    id = "model-low",
                    upstreamModelId = upstream.id,
                    hostModelId = "MODEL_PLACEHOLDER_M400",
                    defaultReasoningLevel = ReasoningLevel.LOW
                ),
                VirtualModel(
                    id = "model-high",
                    upstreamModelId = upstream.id,
                    hostModelId = "MODEL_PLACEHOLDER_M401",
                    defaultReasoningLevel = ReasoningLevel.HIGH
                )
            )
        )
        val selectedModel = upstream.copy(
            capabilities = upstream.capabilities.copy(
                reasoning = upstream.capabilities.reasoning.copy(
                    levels = ReasoningMappingSupport.encode(mapOf(ReasoningLevel.HIGH to highMapping))
                )
            )
        )

        val result = ProviderModelSynchronizer.synchronize(
            config = config,
            provider = provider,
            selectedModels = listOf(selectedModel)
        ).getOrThrow()

        val retained = result.virtualModels.single()
        assertEquals(ReasoningLevel.HIGH, retained.defaultReasoningLevel)
        assertEquals("MODEL_PLACEHOLDER_M401", retained.hostModelId)
    }

    @Test
    fun synchronizerCreatesVirtualModelAsOnlyHostIdentityOwner() {
        val provider = Provider(
            id = "provider",
            name = "Provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com/v1"
        )
        val selectedModel = UpstreamModel(
            id = "provider-model",
            providerId = provider.id,
            name = "Model",
            upstreamModelId = "model"
        )

        val firstResult = ProviderModelSynchronizer.synchronize(
            config = AppConfig(),
            provider = provider,
            selectedModels = listOf(selectedModel)
        ).getOrThrow()

        val upstream = firstResult.upstreamModels.single()
        val virtual = firstResult.virtualModels.single()
        assertEquals(upstream.id, virtual.upstreamModelId)
        assertTrue(virtual.hostModelId.startsWith(ModelIdentity.CUSTOM_HOST_MODEL_ID_PREFIX))

        val secondResult = ProviderModelSynchronizer.synchronize(
            config = AppConfig(
                providers = listOf(provider),
                upstreamModels = firstResult.upstreamModels,
                virtualModels = firstResult.virtualModels
            ),
            provider = provider,
            selectedModels = firstResult.upstreamModels
        ).getOrThrow()

        assertEquals(virtual.hostModelId, secondResult.virtualModels.single().hostModelId)
    }
}
