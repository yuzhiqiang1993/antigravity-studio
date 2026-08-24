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
            val port = runBlocking { server.start(24_321).getOrThrow() }
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
                runBlocking { server.stop() }
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

    @Test
    fun parsesZhipuRealCatalogDatasetAccurately() {
        val file = File("../../docs/服务商模型数据源/智谱.json").let { if (it.exists()) it else File("docs/服务商模型数据源/智谱.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val glm53 = models.firstOrNull { it.id == "glm-5.3" }
        kotlin.test.assertNotNull(glm53)
        assertEquals("GLM 5.3", glm53.displayName)
        assertEquals("Zhipu AI", glm53.vendor)
        assertEquals(1048576L, glm53.inputTokenLimit)
        assertEquals(131072L, glm53.outputTokenLimit)
        assertTrue(glm53.supportsTools)
        assertTrue(glm53.supportsReasoning)
        assertTrue(ModelModality.TEXT in glm53.inputModalities)

        val visionModel = models.firstOrNull { it.inputModalities.contains(ModelModality.IMAGE) }
        kotlin.test.assertNotNull(visionModel)
        assertTrue(visionModel.supportsVision)
    }

    @Test
    fun parsesOpenRouterRealCatalogDatasetAccurately() {
        val file = File("../../docs/服务商模型数据源/openrouter.json").let { if (it.exists()) it else File("docs/服务商模型数据源/openrouter.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val flashVision = models.firstOrNull { it.id == "deepseek/deepseek-v4-flash-vision-exp" }
        kotlin.test.assertNotNull(flashVision)
        assertEquals(1048576L, flashVision.inputTokenLimit)
        assertEquals(384000L, flashVision.outputTokenLimit)
        assertTrue(flashVision.supportsVision)
        assertTrue(flashVision.supportsReasoning)
        assertEquals("high", flashVision.defaultReasoningLevel)

        val geminiFlash = models.firstOrNull { it.id == "google/gemini-3.7-flash" }
        kotlin.test.assertNotNull(geminiFlash)
        assertEquals(1048576L, geminiFlash.inputTokenLimit)
        assertEquals(65536L, geminiFlash.outputTokenLimit)
        assertTrue(geminiFlash.supportsVision)
        assertTrue(geminiFlash.supportsReasoning)
        assertEquals("medium", geminiFlash.defaultReasoningLevel)
    }

    @Test
    fun parsesCpaRealCatalogDatasetAccurately() {
        val file = File("../../docs/服务商模型数据源/cpa.json").let { if (it.exists()) it else File("docs/服务商模型数据源/cpa.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS, isCpaCatalog = true)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val gpt56 = models.firstOrNull { it.id == "gpt-5.6-sol" }
        kotlin.test.assertNotNull(gpt56)
        assertEquals(372000L, gpt56.inputTokenLimit)
        assertEquals(128000L, gpt56.outputTokenLimit)
        assertTrue(gpt56.supportsVision)
        assertTrue(gpt56.supportsTools)
        assertTrue(gpt56.supportsReasoning)
        assertEquals("low", gpt56.defaultReasoningLevel)
        assertTrue(gpt56.supportedReasoningLevels.contains("xhigh"))
        assertTrue(gpt56.supportedReasoningLevels.contains("ultra"))
    }

    @Test
    fun parsesGeminiRealCatalogDatasetAccurately() {
        val file = File("../../docs/服务商模型数据源/gemini.json").let { if (it.exists()) it else File("docs/服务商模型数据源/gemini.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.GEMINI_GENERATE_CONTENT)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val flash25 = models.firstOrNull { it.id == "gemini-2.5-flash" }
        kotlin.test.assertNotNull(flash25)
        assertEquals("Gemini 2.5 Flash", flash25.displayName)
        assertEquals("Google", flash25.vendor)
        assertEquals(1048576L, flash25.inputTokenLimit)
        assertEquals(65536L, flash25.outputTokenLimit)
        assertTrue(flash25.supportsVision)
        assertTrue(flash25.supportsTools)
        assertTrue(flash25.supportsReasoning)

        val gemma4 = models.firstOrNull { it.id == "gemma-4-26b-a4b-it" }
        kotlin.test.assertNotNull(gemma4)
        assertEquals("Google", gemma4.vendor)
        assertEquals(262144L, gemma4.inputTokenLimit)
        assertEquals(32768L, gemma4.outputTokenLimit)
        assertTrue(gemma4.supportsReasoning)

        val veo31 = models.firstOrNull { it.id == "veo-3.1-generate-preview" }
        kotlin.test.assertNotNull(veo31)
        assertTrue(veo31.isImageGeneration)
    }

    @Test
    fun parsesModelGateRealCatalogDatasetAccurately() {
        val file = File("../../docs/服务商模型数据源/modelgate.json").let { if (it.exists()) it else File("docs/服务商模型数据源/modelgate.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val opus46 = models.firstOrNull { it.id == "claude-opus-4-6" }
        kotlin.test.assertNotNull(opus46)
        assertEquals("Claude Opus 4.6", opus46.displayName)
        assertEquals("Anthropic", opus46.vendor)
        assertTrue(opus46.supportsVision)
        assertTrue(opus46.supportsTools)

        val gpt54 = models.firstOrNull { it.id == "gpt-5.4" }
        kotlin.test.assertNotNull(gpt54)
        assertEquals("GPT-5.4", gpt54.displayName)
        assertEquals("OpenAI", gpt54.vendor)

        val ds4 = models.firstOrNull { it.id == "deepseek-v4-flash" }
        kotlin.test.assertNotNull(ds4)
        assertEquals("DeepSeek", ds4.vendor)
    }
}



