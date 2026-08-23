package com.yuzhiqiang.antigravity.studio

import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.proxy.catalog.UniversalModelCatalogParser
import com.yuzhiqiang.antigravity.proxy.encoder.ResponseEncoder
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.parser.AntigravityRequestParser
import com.yuzhiqiang.antigravity.proxy.adapters.ProviderAdapter
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.proxy.server.CatalogInjector
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByokParityTest {

    @Test
    fun catalogDiscoveryPreservesMediaRolesAndNestedLimits() {
        val models = UniversalModelCatalogParser.parse(
            """
            {
              "data": [{
                "id": "gpt-image-1",
                "display_name": "GPT Image 1",
                "limits": {"context_window": 100000},
                "max_output_tokens": 4096,
                "input_modalities": ["text", "image", "audio"],
                "roles": ["image_generation"]
              }]
            }
            """.trimIndent()
        )

        val model = models.single()
        assertEquals(100000L, model.inputTokenLimit)
        assertTrue(ModelModality.IMAGE in model.inputModalities)
        assertTrue(ModelModality.AUDIO in model.inputModalities)
        assertTrue(ModelModality.IMAGE in model.outputModalities)
        assertTrue(ModelRole.IMAGE_GENERATION in model.roles)
        assertTrue(model.isImageGeneration)
        assertTrue("image/png" in model.inputMimeTypes)
    }

    @Test
    fun incompleteCheckpointExperimentIsNotPromotedWithFabricatedDefaults() {
        val models = UniversalModelCatalogParser.parse(
            """
            {
              "models": [{
                "id": "missing-policy-fields",
                "modelExperiments": {"experiments": {
                  "CASCADE_USE_EXPERIMENT_CHECKPOINTER": {
                    "stringValue": "{\"enabled\":true,\"token_threshold\":\"80000\"}"
                  }
                }}
              }]
            }
            """.trimIndent()
        )

        assertEquals(null, models.single().compressionPolicy)
    }

    @Test
    fun cpaCatalogUsesContextAsInputAndDiscoversReasoningLevels() {
        val model = UniversalModelCatalogParser.parse(
            """
            {"models":[{
              "slug":"gpt-5.6-sol",
              "display_name":"GPT 5.6 Sol",
              "context_window":372000,
              "max_tokens":128000,
              "supported_reasoning_levels":[{"effort":"low"},{"effort":"high"}],
              "supports_parallel_tool_calls":false
            }]}
            """.trimIndent(),
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            isCpaCatalog = true
        ).single()

        assertEquals(372000L, model.inputTokenLimit)
        assertEquals(128000L, model.outputTokenLimit)
        assertTrue(model.supportsTools)
        assertTrue(model.supportedReasoningLevels.containsAll(setOf("low", "high")))
    }

    @Test
    fun ordinaryOpenAiMaxTokensIsNotMistakenForInputLimit() {
        val model = UniversalModelCatalogParser.parse(
            """{"data":[{"id":"plain","max_tokens":8192}]}""",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ).single()
        assertEquals(null, model.inputTokenLimit)
        assertEquals(null, model.outputTokenLimit)
    }

    @Test
    fun explicitReasoningFalseWinsOverAdvertisedLevels() {
        val model = UniversalModelCatalogParser.parse(
            """{"data":[{"id":"disabled","reasoning":{"supported":false,"levels":["high"]}}]}""",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ).single()
        assertEquals(false, model.supportsReasoning)
    }

    @Test
    fun sseReaderSupportsCommentsCrlfAndMultilineData() = runBlocking {
        val channel = ByteReadChannel(
            ":keep-alive\r\nevent:message\r\ndata: {\"text\":\r\ndata: \"ok\"}\r\n\r\n"
        )
        assertEquals("{\"text\":\n\"ok\"}", ProviderAdapter.readSseDataEvent(channel).getOrThrow())
    }

    @Test
    fun localProxyExposesHealthAndCustomCatalog() {
        val root = File.createTempFile("studio-parity-", ".dir").apply {
            delete()
            mkdirs()
        }
        try {
            val store = ConfigStore(root)
            store.saveConfig(
                AppConfig(
                    providers = listOf(
                        Provider(
                            id = "provider",
                            name = "Provider",
                            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                            baseUrl = "https://example.com/v1",
                            modelsEndpoint = "https://example.com/v1/models",
                            generateEndpoint = "https://example.com/v1/chat/completions"
                        )
                    ),
                    upstreamModels = listOf(
                        UpstreamModel(
                            id = "upstream",
                            providerId = "provider",
                            upstreamModelId = "gpt-test"
                        )
                    ),
                    virtualModels = listOf(
                        VirtualModel(
                            id = "custom-gpt-test",
                            upstreamModelId = "upstream",
                            hostModelId = "MODEL_PLACEHOLDER_M400"
                        )
                    )
                )
            )
            val server = LocalProxyServer(store)
            val port = server.start(24_321).getOrThrow()
            try {
                fun get(path: String): Pair<Int, String> {
                    val connection = java.net.URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    return connection.responseCode to connection.inputStream.bufferedReader().use { it.readText() }
                }
                val (healthStatus, health) = get("/health")
                val (catalogStatus, catalog) = get("/v1/models")
                assertEquals(200, healthStatus)
                assertTrue(health.contains("\"status\":\"ok\""))
                assertEquals(200, catalogStatus)
                assertTrue(catalog.contains("custom-gpt-test"))
            } finally {
                server.stop()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun responseEncoderKeepsProviderCandidateIndex() {
        val body = ResponseEncoder.encodeChunksToGeminiJson(
            listOf(
                NeutralStreamChunk.TextDelta("answer", choiceIndex = 4),
                NeutralStreamChunk.Completed("STOP", choiceIndex = 4)
            )
        )

        assertTrue(body.contains("\"index\":4"))
        assertTrue(body.contains("\"text\":\"answer\""))
    }

    @Test
    fun proxyErrorsStayTopLevelOnCloudCodeRoutes() {
        val body = ResponseEncoder.encodeErrorToGeminiJson("stream interrupted", 502, cloudCodeEnvelope = true)
        assertTrue(body.contains("\"error\""))
        assertTrue(!body.contains("\"response\""))
        assertTrue(body.contains("\"category\":\"stream_interrupted\""))
    }

    @Test
    fun parserMergesThinkingPartsAndKeepsStableGeneratedToolIds() {
        val request = AntigravityRequestParser.parse(
            """
            {
              "model":"vm-1",
              "contents":[{
                "role":"model",
                "parts":[
                  {"thought":true,"text":"reason "},
                  {"thought":true,"text":"summary","thoughtSignature":"signed"},
                  {"functionCall":{"name":"lookup","args":{"id":1}}},
                  {"functionCall":{"name":"lookup","args":{"id":2}}}
                ]
              }]
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals("reason summary", (request.messages[0].contents[0] as com.yuzhiqiang.antigravity.proxy.model.NeutralContent.Thinking).text)
        assertEquals("signed", (request.messages[0].contents[0] as com.yuzhiqiang.antigravity.proxy.model.NeutralContent.Thinking).signature)
        assertEquals("call_0_2", (request.messages[0].contents[1] as com.yuzhiqiang.antigravity.proxy.model.NeutralContent.ToolCall).id)
        assertEquals("call_0_3", (request.messages[0].contents[2] as com.yuzhiqiang.antigravity.proxy.model.NeutralContent.ToolCall).id)
    }

    @Test
    fun streamEncoderKeepsAllCandidatesUntilTheUpstreamStreamEnds() {
        val encoder = ResponseEncoder.newStreamEncoder()
        assertTrue(
            encoder.encode(NeutralStreamChunk.TextDelta("first", choiceIndex = 2))
                .single()
                .contains("\"index\":2")
        )
        assertTrue(encoder.encode(NeutralStreamChunk.Completed("stop", choiceIndex = 2)).isEmpty())
        assertTrue(
            encoder.encode(NeutralStreamChunk.TextDelta("second", choiceIndex = 7))
                .single()
                .contains("\"index\":7")
        )
        assertTrue(encoder.encode(NeutralStreamChunk.Completed("length", choiceIndex = 7)).isEmpty())

        val endFrames = encoder.finish()
        assertEquals(2, endFrames.size)
        assertTrue(endFrames[0].contains("\"index\":2"))
        assertTrue(endFrames[0].contains("\"finishReason\":\"STOP\""))
        assertTrue(endFrames[0].contains("\"index\":7"))
        assertTrue(endFrames[0].contains("\"finishReason\":\"MAX_TOKENS\""))
        assertEquals("data: [DONE]\n\n", endFrames[1])
        assertTrue(encoder.finish().isEmpty())
    }

    @Test
    fun streamEncoderAttachesFinalUsageToFinishFrame() {
        val encoder = ResponseEncoder.newStreamEncoder()
        encoder.encode(NeutralStreamChunk.Completed(
            finishReason = "stop",
            choiceIndex = 2
        ))
        encoder.encode(NeutralStreamChunk.Completed(
            usage = NeutralUsage(
                inputTokens = 7,
                outputTokens = 4,
                cacheReadTokens = 3,
                reasoningTokens = 5,
                totalTokens = 19
            ),
            choiceIndex = 2
        ))

        val frame = encoder.finish().first()
        assertTrue(frame.contains("\"index\":2"))
        assertTrue(frame.contains("\"text\":\"\""))
        assertTrue(frame.contains("\"promptTokenCount\":10"))
        assertTrue(frame.contains("\"thoughtsTokenCount\":5"))
    }
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
                    capabilities = com.yuzhiqiang.antigravity.domain.model.ModelCapabilities(
                        reasoning = com.yuzhiqiang.antigravity.domain.model.ReasoningCapability(supported = true)
                    )
                )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-max",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M402",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.MAX
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

       val route = com.yuzhiqiang.antigravity.proxy.routing.RouteResolver.resolve(config, request).getOrThrow()
       assertEquals("effort", route.request.reasoningMapping?.kind)
       assertEquals("max", com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport.mappingValueAsString(route.request.reasoningMapping!!))
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
                   capabilities = com.yuzhiqiang.antigravity.domain.model.ModelCapabilities(
                        roles = listOf(com.yuzhiqiang.antigravity.domain.model.ModelRole.AGENT),
                       reasoning = com.yuzhiqiang.antigravity.domain.model.ReasoningCapability(supported = true)
                   )
               )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-low",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M400",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.LOW
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-high",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M401",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.HIGH
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-max",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M402",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.MAX
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
                kotlinx.serialization.json.buildJsonObject {
                    put("response", kotlinx.serialization.json.buildJsonObject {
                        put("models", kotlinx.serialization.json.buildJsonObject {})
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
                    capabilities = com.yuzhiqiang.antigravity.domain.model.ModelCapabilities(
                        reasoning = com.yuzhiqiang.antigravity.domain.model.ReasoningCapability(supported = true)
                    )
                )
            ),
            virtualModels = listOf(
                VirtualModel(
                    id = "custom-stealthox-alpha-low",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M400",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.LOW
                ),
                VirtualModel(
                    id = "custom-stealthox-alpha-high",
                    upstreamModelId = "um-1",
                    hostModelId = "MODEL_PLACEHOLDER_M401",
                    defaultReasoningLevel = com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.HIGH
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

        val resolved = com.yuzhiqiang.antigravity.proxy.routing.RouteResolver.resolve(config, requestTiered).getOrThrow()
        assertEquals("custom-stealthox-alpha-high", resolved.virtualModel?.id)
    }
}
