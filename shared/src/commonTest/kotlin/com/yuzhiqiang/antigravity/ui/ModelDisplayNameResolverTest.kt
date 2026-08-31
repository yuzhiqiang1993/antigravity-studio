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
        assertEquals("GPT 5.6 Sol (X-High)", ModelDisplayNameResolver.resolve("custom-gpt-5-6-sol-x-high"))
        assertEquals("Claude 3.7 Sonnet (High)", ModelDisplayNameResolver.resolve("claude-3-7-sonnet-high"))
    }

    @Test
    fun testResolvesWithOfficialCatalog() {
        val officialModels = listOf(
            OfficialCatalogModel(
                id = "gemini-3.7-flash-high",
                displayName = "Gemini 3.7 Flash (Thinking High)"
            ),
            OfficialCatalogModel(
                id = "gemini-3.1-flash-lite",
                displayName = "Gemini 3.1 Flash Lite"
            )
        )

        assertEquals("Gemini 3.7 Flash (Thinking High)", ModelDisplayNameResolver.resolve("gemini-3.7-flash-high", officialModels = officialModels))
        assertEquals("Gemini 3.1 Flash Lite", ModelDisplayNameResolver.resolve("gemini-3.1-flash-lite", officialModels = officialModels))
    }

    @Test
    fun testResolvesWithConfig() {
        val upstream = UpstreamModel(
            id = "up-gpt-5-6",
            providerId = "p1",
            displayName = "GPT 5.6 Custom",
            upstreamModelId = "gpt-5.6",
            capabilities = ModelCapabilities(reasoning = ReasoningCapability(supported = true))
        )
        val virtualExplicit = VirtualModel(
            id = "custom-gpt-5-6-sol-x-high",
            displayName = "GPT-5.6 Sol Max (X-High)",
            upstreamModelId = "up-gpt-5-6",
            hostModelId = "MODEL_PLACEHOLDER_M401",
            defaultReasoningLevel = ReasoningLevel.X_HIGH
        )
        val virtualDerived = VirtualModel(
            id = "custom-gpt-5-6-auto",
            name = "GPT-5.6 Auto",
            upstreamModelId = "up-gpt-5-6",
            hostModelId = "MODEL_PLACEHOLDER_M402",
            defaultReasoningLevel = ReasoningLevel.HIGH
        )
        val config = AppConfig(
            providers = listOf(Provider(id = "p1", name = "OpenAI", enabled = true)),
            upstreamModels = listOf(upstream),
            virtualModels = listOf(virtualExplicit, virtualDerived)
        )

        assertEquals("GPT-5.6 Sol Max (X-High)", ModelDisplayNameResolver.resolve("custom-gpt-5-6-sol-x-high", config = config))
        assertEquals("GPT-5.6 (High)", ModelDisplayNameResolver.resolve("custom-gpt-5-6-auto", config = config))
    }
}
