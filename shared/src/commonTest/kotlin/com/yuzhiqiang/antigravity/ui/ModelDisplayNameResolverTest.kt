package com.yuzhiqiang.antigravity.ui

import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.ui.utils.ModelDisplayNameResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelDisplayNameResolverTest {

    @Test
    fun testFallbackFormattingForCommonModels() {
        assertEquals("Gemini 3.7 Flash (High)", ModelDisplayNameResolver.resolve("gemini-3.7-flash-high"))
        assertEquals("Gemini 3.1 Flash Lite", ModelDisplayNameResolver.resolve("gemini-3.1-flash-lite"))
        assertEquals("Gemini 3.7 Flash (High)", ModelDisplayNameResolver.resolve("official-variant:gemini-3.7-flash-high"))
        assertEquals("Gemini 3.1 Flash Lite", ModelDisplayNameResolver.resolve("official-variant:gemini-3.1-flash-lite"))
        assertEquals("GPT 5.6 Sol (X-High)", ModelDisplayNameResolver.resolve("custom-gpt-5-6-sol-x-high"))
        assertEquals("Claude 3.7 Sonnet (High)", ModelDisplayNameResolver.resolve("claude-3-7-sonnet-high"))
    }

    @Test
    fun testResolvesWithOfficialCatalog() {
        val officialModels = listOf(
            OfficialCatalogModel(
                catalogModelId = "gemini-3.7-flash-high",
                displayName = "Gemini 3.7 Flash (Thinking High)"
            ),
            OfficialCatalogModel(
                catalogModelId = "gemini-3.1-flash-lite",
                displayName = "Gemini 3.1 Flash Lite"
            )
        )

        assertEquals(
            "Gemini 3.7 Flash (Thinking High)",
            ModelDisplayNameResolver.resolve("gemini-3.7-flash-high", officialModels = officialModels)
        )
        assertEquals(
            "Gemini 3.1 Flash Lite",
            ModelDisplayNameResolver.resolve("gemini-3.1-flash-lite", officialModels = officialModels)
        )
    }

    @Test
    fun testResolvesWithConfig() {
        val binding = ProviderModelBinding(
            bindingId = "binding-gpt-5-6",
            providerConfigId = "p1",
            providerModelId = "gpt-5.6",
            displayName = "GPT 5.6 Custom",
            capabilities = ModelCapabilities(reasoning = ReasoningCapability(supported = true))
        )
        val explicitVariant = ModelRouteVariant(
            variantId = "custom-gpt-5-6-sol-x-high",
            bindingId = binding.bindingId,
            catalogModelId = "custom-gpt-5-6-sol-x-high",
            runtimeModelId = "MODEL_PLACEHOLDER_M401",
            displayName = "GPT-5.6 Sol Max (X-High)",
            reasoningProfile = ReasoningProfile(level = ReasoningLevel.X_HIGH)
        )
        val derivedVariant = ModelRouteVariant(
            variantId = "custom-gpt-5-6-auto",
            bindingId = binding.bindingId,
            catalogModelId = "custom-gpt-5-6-auto",
            runtimeModelId = "MODEL_PLACEHOLDER_M402",
            displayName = "GPT-5.6 (High)",
            reasoningProfile = ReasoningProfile(level = ReasoningLevel.HIGH)
        )
        val config = AppConfig(
            providers = listOf(Provider(id = "p1", name = "OpenAI", enabled = true)),
            providerModelBindings = listOf(binding),
            modelRouteVariants = listOf(explicitVariant, derivedVariant)
        )

        assertEquals(
            "GPT-5.6 Sol Max (X-High)",
            ModelDisplayNameResolver.resolve("custom-gpt-5-6-sol-x-high", config = config)
        )
        assertEquals("GPT-5.6 (High)", ModelDisplayNameResolver.resolve("custom-gpt-5-6-auto", config = config))
    }
}
