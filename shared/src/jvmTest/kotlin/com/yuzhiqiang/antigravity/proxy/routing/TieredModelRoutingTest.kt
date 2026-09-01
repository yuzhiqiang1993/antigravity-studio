package com.yuzhiqiang.antigravity.proxy.routing

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningCapability
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.ReasoningProfile
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.server.CatalogInjector

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertTrue

class TieredModelRoutingTest {

    @Test
    fun providerRequestUsesBindingProviderModelIdAndFreezesIdentity() {
        val config = configWithVariants(ReasoningLevel.MAX)
        val request = request("catalog-stealth-max")

        val route = RouteResolver.resolve(config, request).getOrThrow()

        assertEquals("stealth/ox-alpha", route.request.targetUpstreamModelId)
        assertEquals("variant-max", route.modelRouteVariant.variantId)
        assertEquals("binding-1", route.providerModelBinding.bindingId)
        assertEquals("catalog-stealth-max", route.modelExecutionIdentity.catalogModelId)
        assertEquals("MODEL_PLACEHOLDER_M402", route.modelExecutionIdentity.runtimeModelId)
        assertEquals("stealth/ox-alpha", route.modelExecutionIdentity.providerModelId)
        assertEquals("effort", route.request.reasoningMapping?.kind)
        assertEquals(
            "max",
            ReasoningMappingSupport.mappingValueAsString(route.request.reasoningMapping!!)
        )
    }

    @Test
    fun catalogKeepsCatalogRuntimeAndProviderIdsSeparate() {
        val config = configWithVariants(ReasoningLevel.LOW, ReasoningLevel.HIGH)
        val response = CatalogInjector.injectCustomModels(
            buildJsonObject {
                put("response", buildJsonObject { put("models", buildJsonObject {}) })
            },
            config
        )
        val container = response["response"]!!.jsonObject
        val models = container["models"]!!.jsonObject
        val low = models["catalog-stealth-low"]!!.jsonObject

        assertEquals("MODEL_PLACEHOLDER_M400", low["model"]?.jsonPrimitive?.content)
        assertEquals("MODEL_PLACEHOLDER_M400", low["runtimeModelId"]?.jsonPrimitive?.content)
        assertEquals("stealth/ox-alpha", low["providerModelId"]?.jsonPrimitive?.content)
        assertTrue("catalog-stealth-tiered" in models)
        assertTrue(container.toString().contains("catalog-stealth-tiered"))
    }

    @Test
    fun bindingCannotBeRoutedWithoutRouteVariant() {
        val base = configWithVariants(ReasoningLevel.LOW)
        val config = base.copy(modelRouteVariants = emptyList())

        val result = RouteResolver.resolve(config, request("binding-1"))

        assertTrue(result.isFailure)
    }

    @Test
    fun tieredCatalogKeyResolvesToPreferredConcreteVariant() {
        val config = configWithVariants(ReasoningLevel.LOW, ReasoningLevel.HIGH)

        val resolved = RouteResolver.resolve(config, request("catalog-stealth-tiered")).getOrThrow()

        assertEquals("variant-high", resolved.modelRouteVariant.variantId)
        assertEquals("stealth/ox-alpha", resolved.request.targetUpstreamModelId)
    }

    private fun configWithVariants(vararg levels: ReasoningLevel): AppConfig {
        val provider = Provider(
            id = "provider-1",
            name = "OpenRouter",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://openrouter.ai/api/v1"
        )
        val binding = ProviderModelBinding(
            bindingId = "binding-1",
            providerConfigId = provider.id,
            providerModelId = "stealth/ox-alpha",
            displayName = "Stealth Ox Alpha",
            capabilities = ModelCapabilities(
                roles = listOf(ModelRole.AGENT),
                reasoning = ReasoningCapability(supported = true)
            )
        )
        val variants = levels.map { level ->
            val suffix = level.name.lowercase()
            val slot = when (level) {
                ReasoningLevel.LOW -> 400
                ReasoningLevel.HIGH -> 401
                ReasoningLevel.MAX -> 402
                else -> 403
            }
            ModelRouteVariant(
                variantId = "variant-$suffix",
                bindingId = binding.bindingId,
                catalogModelId = "catalog-stealth-$suffix",
                runtimeModelId = "MODEL_PLACEHOLDER_M$slot",
                displayName = "Stealth Ox Alpha (${level.label})",
                reasoningProfile = ReasoningProfile(level = level)
            )
        }
        return AppConfig(
            providers = listOf(provider),
            providerModelBindings = listOf(binding),
            modelRouteVariants = variants
        )
    }

    private fun request(modelId: String) = AntigravityRequestParser.parse(
        """
        {
          "model": "$modelId",
          "contents": [{"role": "user", "parts": [{"text": "hi"}]}]
        }
        """.trimIndent()
    ).getOrThrow()
}
