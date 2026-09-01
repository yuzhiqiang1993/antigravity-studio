package com.yuzhiqiang.antigravity.ui.dialogs.provider

import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ModelTokenLimits
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningCapability
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.TokenLimitSource
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.proxy.catalog.DiscoveredModelInfo
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderModelConfigMapperTest {

    @Test
    fun toCatalogModelConfigUsesSourceOfEffectiveInputLimit() {
        val inputOnly = createModel(
            tokenLimits = ModelTokenLimits(
                inputTokenLimit = 32_768L,
                inputTokenLimitSource = TokenLimitSource.ESTIMATED
            )
        )
        val inputOnlyConfig = ProviderModelConfigMapper.toCatalogModelConfig(inputOnly)

        assertEquals(32_768L, inputOnlyConfig.inputTokenLimit)
        assertEquals(TokenLimitSource.ESTIMATED, inputOnlyConfig.inputTokenLimitSource)

        val contextPreferred = createModel(
            tokenLimits = ModelTokenLimits(
                contextWindow = 131_072L,
                contextWindowSource = TokenLimitSource.CATALOG,
                inputTokenLimit = 65_536L,
                inputTokenLimitSource = TokenLimitSource.CONFIGURED
            )
        )
        val contextPreferredConfig = ProviderModelConfigMapper.toCatalogModelConfig(contextPreferred)

        assertEquals(131_072L, contextPreferredConfig.inputTokenLimit)
        assertEquals(TokenLimitSource.CATALOG, contextPreferredConfig.inputTokenLimitSource)
    }

    @Test
    fun manualConfigurationPreservesExistingModelCapabilitiesAndReasoning() {
        val reasoningMappings = mapOf(
            ReasoningLevel.OFF to ReasoningMapping(kind = "disabled"),
            ReasoningLevel.LOW to ReasoningMapping(
                kind = "budget_tokens",
                value = JsonPrimitive(1_024)
            ),
            ReasoningLevel.HIGH to ReasoningMapping(
                kind = "budget_tokens",
                value = JsonPrimitive(4_096)
            ),
            ReasoningLevel.AUTO to ReasoningMapping(kind = "disabled")
        )
        val original = createModel(
            capabilities = ModelCapabilities(
                roles = listOf(ModelRole.AGENT),
                inputModalities = listOf(
                    ModelModality.TEXT,
                    ModelModality.IMAGE,
                    ModelModality.AUDIO,
                    ModelModality.DOCUMENT
                ),
                outputModalities = listOf(ModelModality.TEXT),
                tools = true,
                inputMimeTypes = listOf("image/png", "audio/mpeg", "application/pdf"),
                reasoning = ReasoningCapability(
                    supported = true,
                    thinkingBudget = 4_096,
                    minThinkingBudget = 512,
                    levels = ReasoningMappingSupport.encode(reasoningMappings),
                    type = "budget"
                )
            ),
            tokenLimits = ModelTokenLimits(
                inputTokenLimit = 131_072L,
                inputTokenLimitSource = TokenLimitSource.CONFIGURED,
                outputTokenLimit = 8_192L,
                outputTokenLimitSource = TokenLimitSource.CATALOG
            )
        )

        val manualConfig = ProviderModelConfigMapper.createManualCatalogConfigs(listOf(original)).single()

        assertEquals(original.capabilities.roles.toSet(), manualConfig.roles)
        assertEquals(original.capabilities.inputModalities.toSet(), manualConfig.inputModalities)
        assertEquals(original.capabilities.outputModalities.toSet(), manualConfig.outputModalities)
        assertEquals(original.capabilities.inputMimeTypes, manualConfig.inputMimeTypes)
        assertEquals(reasoningMappings, manualConfig.reasoningMappings)
        assertEquals(4_096, manualConfig.reasoningDraft.thinkingBudget)
        assertEquals(512, manualConfig.reasoningDraft.minThinkingBudget)
        assertEquals(TokenLimitSource.CONFIGURED, manualConfig.inputTokenLimitSource)

        val saved = ProviderModelConfigMapper.buildFinalProviderModelBindings(
            fetchedModelConfigs = listOf(manualConfig),
            selectedModelIds = setOf(manualConfig.id),
            initialModels = listOf(original),
            providerId = original.providerConfigId,
            protocol = ProviderProtocol.GEMINI_GENERATE_CONTENT
        ).single()

        assertEquals(original.bindingId, saved.bindingId)
        assertEquals(original.capabilities.roles.toSet(), saved.capabilities.roles.toSet())
        assertEquals(original.capabilities.inputModalities.toSet(), saved.capabilities.inputModalities.toSet())
        assertEquals(original.capabilities.outputModalities.toSet(), saved.capabilities.outputModalities.toSet())
        assertEquals(original.capabilities.inputMimeTypes, saved.capabilities.inputMimeTypes)
        assertEquals(reasoningMappings, ReasoningMappingSupport.parse(saved.capabilities.reasoning.levels))
        assertEquals(4_096, saved.capabilities.reasoning.thinkingBudget)
        assertEquals(512, saved.capabilities.reasoning.minThinkingBudget)
        assertEquals("budget", saved.capabilities.reasoning.type)
        assertEquals(TokenLimitSource.CONFIGURED, saved.tokenLimits.contextWindowSource)
        assertEquals(TokenLimitSource.CONFIGURED, saved.tokenLimits.inputTokenLimitSource)
    }

    @Test
    fun newlyDiscoveredImageGenerationModelStillUsesImageDefaults() {
        val discovered = DiscoveredModelInfo(
            id = "new-image-model",
            inputTokenLimit = 32_768L,
            inputTokenLimitSource = TokenLimitSource.CATALOG,
            outputTokenLimit = 4_096L,
            outputTokenLimitSource = TokenLimitSource.CATALOG,
            supportsVision = true,
            supportsTools = true,
            supportsReasoning = true,
            inputModalities = setOf(ModelModality.TEXT, ModelModality.IMAGE),
            outputModalities = setOf(ModelModality.IMAGE),
            inputMimeTypes = listOf("image/png"),
            isImageGeneration = true
        )
        val discoveredConfig = ProviderModelConfigMapper.mergeDiscoveredCatalogConfigs(
            discoveredList = listOf(discovered),
            initialModels = emptyList()
        ).single()

        val saved = ProviderModelConfigMapper.buildFinalProviderModelBindings(
            fetchedModelConfigs = listOf(discoveredConfig),
            selectedModelIds = setOf(discoveredConfig.id),
            initialModels = emptyList(),
            providerId = "provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ).single()

        assertEquals(listOf(ModelRole.IMAGE_GENERATION), saved.capabilities.roles)
        assertEquals(listOf(ModelModality.TEXT), saved.capabilities.inputModalities)
        assertEquals(listOf(ModelModality.IMAGE), saved.capabilities.outputModalities)
        assertFalse(saved.capabilities.tools)
        assertFalse(saved.capabilities.reasoning.supportsReasoning)
        assertTrue(saved.capabilities.inputMimeTypes.isEmpty())
        assertEquals(ModelTokenLimits(), saved.tokenLimits)
        assertEquals(null, saved.compressionPolicy)
    }

    @Test
    fun manualConfigurationKeepsExistingImageGenerationModelIdentity() {
        val original = createModel(
            capabilities = ModelCapabilities(
                roles = listOf(ModelRole.IMAGE_GENERATION),
                inputModalities = listOf(ModelModality.TEXT, ModelModality.IMAGE),
                outputModalities = listOf(ModelModality.IMAGE),
                tools = false,
                inputMimeTypes = listOf("image/png")
            ),
            tokenLimits = ModelTokenLimits(
                contextWindow = 16_384L,
                contextWindowSource = TokenLimitSource.CONFIGURED,
                outputTokenLimit = 2_048L,
                outputTokenLimitSource = TokenLimitSource.CONFIGURED
            )
        )

        val manualConfig = ProviderModelConfigMapper.createManualCatalogConfigs(listOf(original)).single()
        assertTrue(manualConfig.isImageGeneration)

        val saved = ProviderModelConfigMapper.buildFinalProviderModelBindings(
            fetchedModelConfigs = listOf(manualConfig),
            selectedModelIds = setOf(manualConfig.id),
            initialModels = listOf(original),
            providerId = original.providerConfigId,
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ).single()

        assertEquals(setOf(ModelRole.IMAGE_GENERATION), saved.capabilities.roles.toSet())
        assertFalse(ModelRole.AGENT in saved.capabilities.roles)
        assertEquals(setOf(ModelModality.TEXT, ModelModality.IMAGE), saved.capabilities.inputModalities.toSet())
        assertEquals(setOf(ModelModality.IMAGE), saved.capabilities.outputModalities.toSet())
        assertEquals(listOf("image/png"), saved.capabilities.inputMimeTypes)
        assertEquals(16_384L, saved.tokenLimits.contextWindow)
        assertEquals(2_048L, saved.tokenLimits.outputTokenLimit)
    }

    @Test
    fun newBindingUsesUuidAndPreservesProviderModelIdExactly() {
        val providerModelId = "models/acme/model:latest"
        val saved = ProviderModelConfigMapper.buildFinalProviderModelBindings(
            fetchedModelConfigs = listOf(
                CatalogModelConfig(
                    id = providerModelId,
                    name = "Acme Model"
                )
            ),
            selectedModelIds = setOf(providerModelId),
            initialModels = emptyList(),
            providerId = "provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ).single()

        assertEquals(providerModelId, saved.providerModelId)
        assertEquals(saved.bindingId, UUID.fromString(saved.bindingId).toString())
    }

    private fun createModel(
        capabilities: ModelCapabilities = ModelCapabilities(),
        tokenLimits: ModelTokenLimits = ModelTokenLimits()
    ): ProviderModelBinding {
        return ProviderModelBinding(
            bindingId = "provider-model",
            providerConfigId = "provider",
            providerModelId = "model",
            displayName = "Display Model",
            capabilities = capabilities,
            tokenLimits = tokenLimits
        )
    }
}
