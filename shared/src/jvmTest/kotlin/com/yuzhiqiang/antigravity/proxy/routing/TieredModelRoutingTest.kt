package com.yuzhiqiang.antigravity.proxy.routing

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningCapability
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.server.CatalogInjector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TieredModelRoutingTest {

    @Test
    fun openAiChatCompletionsHandlesDynamicThinkingBudgetWithReasoningLevel() {
        val config = AppConfig(
            providers = listOf(
                Provider(
                    id = "p-1",
                    name = "OpenRouter",
                    protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://openrouter.ai/api/v1"
                )
            ),
            upstreamModels = listOf(
                UpstreamModel(
                    id = "um-1",
                    providerId = "p-1",
                    upstreamModelId = "stealth/ox-alpha",
                    capabilities = ModelCapabilities(
                        roles = listOf(ModelRole.AGENT),
                        reasoning = ReasoningCapability(supported = true)
                    )
                )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-max",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M402",
                    defaultReasoningLevel = ReasoningLevel.MAX
                )
            )
        )

        val request = AntigravityRequestParser.parse(
            """
            {
              "model": "custom-stealthox-alpha-max",
              "generationConfig": {
                "thinkingConfig": {
                  "thinkingBudget": -1
                }
              },
              "contents": [{
                "role": "user",
                "parts": [{"text": "hello"}]
              }]
            }
            """.trimIndent()
        ).getOrThrow()

        val route = RouteResolver.resolve(config, request).getOrThrow()
        assertEquals("effort", route.request.reasoningMapping?.kind)
        assertEquals(
            "max",
            ReasoningMappingSupport.mappingValueAsString(route.request.reasoningMapping!!)
        )
    }

    @Test
    fun modelCatalogRegistersTieredParentForReasoningVariants() {
        val config = AppConfig(
            providers = listOf(
                Provider(
                    id = "p-1",
                    name = "OpenRouter",
                    protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://openrouter.ai/api/v1"
                )
            ),
            upstreamModels = listOf(
                UpstreamModel(
                    id = "um-1",
                    providerId = "p-1",
                    upstreamModelId = "stealth/ox-alpha",
                    displayName = "stealth/ox-alpha",
                    capabilities = ModelCapabilities(
                        roles = listOf(ModelRole.AGENT),
                        reasoning = ReasoningCapability(supported = true)
                    )
                )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-low",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M400",
                    defaultReasoningLevel = ReasoningLevel.LOW
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-high",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M401",
                    defaultReasoningLevel = ReasoningLevel.HIGH
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-max",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M402",
                    defaultReasoningLevel = ReasoningLevel.MAX
                )
            )
        )

        val root = File.createTempFile("studio-tiered-", ".dir").apply {
            delete()
            mkdirs()
        }
        try {
            val store = ConfigStore(root)
            store.saveConfig(config)
            val response = CatalogInjector.injectCustomModels(
                buildJsonObject {
                    put("response", buildJsonObject {
                        put("models", buildJsonObject {})
                    })
                },
                config
            ).toString()

            assertTrue(response.contains("custom-stealthox-alpha-tiered"))
            assertTrue(response.contains("stealth/ox-alpha"))
            assertTrue(response.contains("stealth/ox-alpha (High)"))
            assertTrue(response.contains("stealth/ox-alpha (Low)"))
            assertTrue(response.contains("stealth/ox-alpha (Max)"))
            assertTrue(response.contains("tieredModelIds") && response.contains("custom-stealthox-alpha-tiered"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tieredParentResolvesToPreferredVariant() {
        val config = AppConfig(
            providers = listOf(
                Provider(
                    id = "p-1",
                    name = "OpenRouter",
                    protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://openrouter.ai/api/v1"
                )
            ),
            upstreamModels = listOf(
                UpstreamModel(
                    id = "um-1",
                    providerId = "p-1",
                    upstreamModelId = "stealth/ox-alpha",
                    capabilities = ModelCapabilities(
                        reasoning = ReasoningCapability(supported = true)
                    )
                )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-low",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M400",
                    defaultReasoningLevel = ReasoningLevel.LOW
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-high",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M401",
                    defaultReasoningLevel = ReasoningLevel.HIGH
                )
            )
        )

        val requestTiered = AntigravityRequestParser.parse(
            """
            {
              "model": "custom-stealthox-alpha-tiered",
              "contents": [{"role": "user", "parts": [{"text": "hi"}]}]
            }
            """.trimIndent()
        ).getOrThrow()

        val resolved = RouteResolver.resolve(config, requestTiered).getOrThrow()
        assertEquals("custom-stealthox-alpha-high", resolved.virtualModel?.id)
    }
}
