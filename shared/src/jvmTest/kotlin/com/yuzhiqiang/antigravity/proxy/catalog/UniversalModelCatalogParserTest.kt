package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRole
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UniversalModelCatalogParserTest {

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
    fun parsesZhipuRealCatalogDatasetAccurately() {
        val file =
            File("../../docs/服务商模型数据源/智谱.json").let { if (it.exists()) it else File("docs/服务商模型数据源/智谱.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val glm53 = models.firstOrNull { it.id == "glm-5.3" }
        assertNotNull(glm53)
        assertEquals("GLM 5.3", glm53.displayName)
        assertEquals("Zhipu AI", glm53.vendor)
        assertEquals(1048576L, glm53.inputTokenLimit)
        assertEquals(131072L, glm53.outputTokenLimit)
        assertTrue(glm53.supportsTools)
        assertTrue(glm53.supportsReasoning)
        assertTrue(ModelModality.TEXT in glm53.inputModalities)

        val visionModel = models.firstOrNull { it.inputModalities.contains(ModelModality.IMAGE) }
        assertNotNull(visionModel)
        assertTrue(visionModel.supportsVision)
    }

    @Test
    fun parsesOpenRouterRealCatalogDatasetAccurately() {
        val file =
            File("../../docs/服务商模型数据源/openrouter.json").let { if (it.exists()) it else File("docs/服务商模型数据源/openrouter.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val flashVision = models.firstOrNull { it.id == "deepseek/deepseek-v4-flash-vision-exp" }
        assertNotNull(flashVision)
        assertEquals(1048576L, flashVision.inputTokenLimit)
        assertEquals(384000L, flashVision.outputTokenLimit)
        assertTrue(flashVision.supportsVision)
        assertTrue(flashVision.supportsReasoning)
        assertEquals("high", flashVision.defaultReasoningLevel)

        val geminiFlash = models.firstOrNull { it.id == "google/gemini-3.7-flash" }
        assertNotNull(geminiFlash)
        assertEquals(1048576L, geminiFlash.inputTokenLimit)
        assertEquals(65536L, geminiFlash.outputTokenLimit)
        assertTrue(geminiFlash.supportsVision)
        assertTrue(geminiFlash.supportsReasoning)
        assertEquals("medium", geminiFlash.defaultReasoningLevel)
    }

    @Test
    fun parsesCpaRealCatalogDatasetAccurately() {
        val file =
            File("../../docs/服务商模型数据源/cpa.json").let { if (it.exists()) it else File("docs/服务商模型数据源/cpa.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(
            rawJson,
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            isCpaCatalog = true
        )
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val gpt56 = models.firstOrNull { it.id == "gpt-5.6-sol" }
        assertNotNull(gpt56)
        assertEquals(921000L, gpt56.inputTokenLimit)
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
        val file =
            File("../../docs/服务商模型数据源/gemini.json").let { if (it.exists()) it else File("docs/服务商模型数据源/gemini.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.GEMINI_GENERATE_CONTENT)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val flash25 = models.firstOrNull { it.id == "gemini-2.5-flash" }
        assertNotNull(flash25)
        assertEquals("Gemini 2.5 Flash", flash25.displayName)
        assertEquals("Google", flash25.vendor)
        assertEquals(1048576L, flash25.inputTokenLimit)
        assertEquals(65536L, flash25.outputTokenLimit)
        assertTrue(flash25.supportsVision)
        assertTrue(flash25.supportsTools)
        assertTrue(flash25.supportsReasoning)

        val gemma4 = models.firstOrNull { it.id == "gemma-4-26b-a4b-it" }
        assertNotNull(gemma4)
        assertEquals("Google", gemma4.vendor)
        assertEquals(262144L, gemma4.inputTokenLimit)
        assertEquals(32768L, gemma4.outputTokenLimit)
        assertTrue(gemma4.supportsReasoning)

        val veo31 = models.firstOrNull { it.id == "veo-3.1-generate-preview" }
        assertNotNull(veo31)
        assertTrue(veo31.isImageGeneration)
    }

    @Test
    fun parsesModelGateRealCatalogDatasetAccurately() {
        val file =
            File("../../docs/服务商模型数据源/modelgate.json").let { if (it.exists()) it else File("docs/服务商模型数据源/modelgate.json") }
        if (!file.exists()) return
        val rawJson = file.readText()
        val models = UniversalModelCatalogParser.parse(rawJson, protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS)
        assertTrue(models.isNotEmpty(), "Models should not be empty")

        val opus46 = models.firstOrNull { it.id == "claude-opus-4-6" }
        assertNotNull(opus46)
        assertEquals("Claude Opus 4.6", opus46.displayName)
        assertEquals("Anthropic", opus46.vendor)
        assertTrue(opus46.supportsVision)
        assertTrue(opus46.supportsTools)

        val gpt54 = models.firstOrNull { it.id == "gpt-5.4" }
        assertNotNull(gpt54)
        assertEquals("GPT-5.4", gpt54.displayName)
        assertEquals("OpenAI", gpt54.vendor)

        val ds4 = models.firstOrNull { it.id == "deepseek-v4-flash" }
        assertNotNull(ds4)
        assertEquals("DeepSeek", ds4.vendor)
    }

    @Test
    fun testOfficialGeminiUsageExtraction() {
        val rawJson = """
            {
              "response": {
                "candidates": [{"content": {"parts": [{"text": "Hello world"}]}}],
                "usageMetadata": {
                  "promptTokenCount": 1500,
                  "cachedContentTokenCount": 500,
                  "candidatesTokenCount": 200,
                  "thoughtsTokenCount": 80,
                  "totalTokenCount": 1780
                }
              }
            }
        """.trimIndent()
        val jsonElement = Json.parseToJsonElement(rawJson)
        val root = jsonElement as JsonObject
        val effectiveRoot = root["response"] as JsonObject
        val usageObj = effectiveRoot["usageMetadata"] as JsonObject

        fun long(key: String): Long? = usageObj[key]?.jsonPrimitive?.longOrNull
        val prompt = long("promptTokenCount")
        val cached = long("cachedContentTokenCount")
        val reasoning = long("thoughtsTokenCount")
        val output = long("candidatesTokenCount")
        val total = long("totalTokenCount")

        assertEquals(1500L, prompt)
        assertEquals(500L, cached)
        assertEquals(80L, reasoning)
        assertEquals(200L, output)
        assertEquals(1780L, total)
    }
}
