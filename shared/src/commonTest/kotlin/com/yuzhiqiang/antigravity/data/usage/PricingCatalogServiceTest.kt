package com.yuzhiqiang.antigravity.data.usage

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PricingCatalogServiceTest {

    @Test
    fun testParsesLiteLlmReasoningCacheWriteFallbackAndLongContextTier() = runBlocking {
        val root = tempRoot()
        val pricingFile = File(root, "pricing.json")
        try {
            pricingFile.writeText(
                """
                {
                  "vendor/exact-model": {
                    "mode": "chat",
                    "input_cost_per_token": 0.000001,
                    "output_cost_per_token": 0.000002,
                    "cache_read_input_token_cost": 0.0000001,
                    "output_cost_per_reasoning_token": 0.000003,
                    "input_cost_per_token_above_272k_tokens": 0.000002,
                    "output_cost_per_token_above_272k_tokens": 0.000004
                  },
                  "embedding-model": {
                    "mode": "embedding",
                    "input_cost_per_token": 0.000009
                  }
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            service.setCustomPricingSource(pricingFile.absolutePath)

            val resolution = service.resolvePricing(
                modelId = "MODEL_PLACEHOLDER_M400",
                displayName = "Private Model",
                modelPricingIds = listOf("vendor/exact-model")
            )
            assertEquals(PricingSource.CUSTOM, resolution.source)
            assertTrue(resolution.matched)
            assertEquals(PricingConfidence.HIGH, resolution.confidence)

            val rate = resolution.rate
            assertEquals(1.0, rate.input)
            assertEquals(2.0, rate.output)
            assertEquals(0.1, rate.cacheRead, 0.000001)
            // LiteLLM 未提供 cache-write 时，按已提供 cache-read 价格回退。
            assertEquals(0.1, rate.cacheWrite, 0.000001)
            assertEquals(3.0, rate.reasoning)
            assertEquals(2.0, rate.above272k?.input)
            assertEquals(4.0, rate.above272k?.output)
            assertEquals(0.1, rate.above272k?.cacheRead ?: 0.0, 0.000001)
            assertEquals(0.1, rate.above272k?.cacheWrite ?: 0.0, 0.000001)
            assertEquals(3.0, rate.above272k?.reasoning)

            val normal = service.calculateCostAndSavings(
                input = 100_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                modelId = "opaque",
                modelPricingIds = listOf("vendor/exact-model")
            )
            val long = service.calculateCostAndSavings(
                input = 300_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                modelId = "opaque",
                modelPricingIds = listOf("vendor/exact-model")
            )
            assertEquals(0.1, normal.costUsd, 0.000001)
            assertEquals(0.6, long.costUsd, 0.000001)
            assertEquals(PricingSource.CUSTOM, long.pricingSource)
            assertTrue(long.pricingMatched)
            assertTrue(long.usedLongContextPricing)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testCostResultMarksUnmatchedModelsInsteadOfReturningAnAmbiguousZeroPrice() {
        val root = tempRoot()
        try {
            val cacheFile = File(root, "pricing_catalog.json")
            cacheFile.writeText(
                """
                {
                  "gpt-4o": {"cost": {"input": 2.5, "output": 10.0, "cache_read": 1.25, "cache_write": 2.5, "reasoning": 10.0}}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            val result = service.calculateCostAndSavings(
                input = 1_000_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                modelId = "MODEL_PLACEHOLDER_M999"
            )

            assertEquals(PricingSource.UNMATCHED, result.pricingSource)
            assertFalse(result.pricingMatched)
            assertFalse(result.lowerBound)
            assertEquals(0.0, result.costUsd)

            val lowerBound = service.calculateCostAndSavings(
                input = 1_000_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                modelId = "gpt-4o",
                missingUsageFields = listOf("output")
            )
            assertTrue(lowerBound.pricingMatched)
            assertTrue(lowerBound.lowerBound)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testOnlyUsesUniqueUnqualifiedProviderAlias() = runBlocking {
        val root = tempRoot()
        val pricingFile = File(root, "pricing.json")
        try {
            pricingFile.writeText(
                """
                {
                  "provider-a/shared-model": {"input_cost_per_token": 0.000001, "output_cost_per_token": 0.000002},
                  "provider-b/shared-model": {"input_cost_per_token": 0.000003, "output_cost_per_token": 0.000004},
                  "provider-a/unique-model": {"input_cost_per_token": 0.000005, "output_cost_per_token": 0.000006}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            service.setCustomPricingSource(pricingFile.absolutePath)

            assertEquals(5.0, service.resolveRate("other/unique-model").input)
            assertEquals(1.0, service.resolveRate("provider-a/shared-model").input)
            assertEquals(0.0, service.resolveRate("other/shared-model").input)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testExternalResolutionIsReportedAsExternal() {
        val root = tempRoot()
        try {
            val cacheFile = File(root, "pricing_catalog.json")
            cacheFile.writeText(
                """
                {
                  "gpt-4o": {"cost": {"input": 2.5, "output": 10.0, "cache_read": 1.25, "cache_write": 2.5, "reasoning": 10.0}}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            val result = service.resolvePricing("gpt-4o")

            assertEquals(PricingSource.EXTERNAL, result.source)
            assertTrue(result.matched)
            assertEquals(2.5, result.rate.input)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testDoesNotGuessPriceFromUnknownModelFamilyOrOpaqueRuntimeId() {
        val root = tempRoot()
        try {
            val cacheFile = File(root, "pricing_catalog.json")
            cacheFile.writeText(
                """
                {
                  "gpt-4o": {"cost": {"input": 2.5, "output": 10.0, "cache_read": 1.25, "cache_write": 2.5, "reasoning": 10.0}}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            assertEquals(ModelPricingRate(), service.resolveRate("gemini-3.6-flash-high"))
            assertEquals(ModelPricingRate(), service.resolveRate("MODEL_PLACEHOLDER_M999"))
            assertTrue(service.resolveRate("gpt-4o", modelPricingIds = listOf("MODEL_PLACEHOLDER_M999")).input > 0.0)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun tempRoot(): File = File.createTempFile("usage-pricing-", "-root").apply {
        delete()
        mkdirs()
    }
}
