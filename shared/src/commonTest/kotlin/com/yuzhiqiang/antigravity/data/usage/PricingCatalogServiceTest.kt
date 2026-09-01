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
                canonicalModelId = "MODEL_PLACEHOLDER_M400",
                registeredPricingIds = listOf("vendor/exact-model")
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
            assertEquals(272_000L, rate.above272k?.thresholdTokens)

            val normal = service.calculateCostAndSavings(
                input = 100_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                canonicalModelId = "opaque",
                registeredPricingIds = listOf("vendor/exact-model")
            )
            val long = service.calculateCostAndSavings(
                input = 300_000,
                output = 0,
                cacheRead = 0,
                cacheWrite = 0,
                reasoning = 0,
                canonicalModelId = "opaque",
                registeredPricingIds = listOf("vendor/exact-model")
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
    fun testParsesModelsDevContextTierPricing() = runBlocking {
        val root = tempRoot()
        val pricingFile = File(root, "pricing.json")
        try {
            pricingFile.writeText(
                """
                {
                  "provider": {
                    "models": {
                      "tiered-model": {
                        "cost": {
                          "input": 5,
                          "output": 30,
                          "tiers": [
                            {
                              "input": 10,
                              "output": 45,
                              "cache_read": 1,
                              "tier": {
                                "type": "context",
                                "size": 200000
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)
            service.setCustomPricingSource(pricingFile.absolutePath)

            val rate = service.resolveRate(canonicalModelId = "provider/tiered-model")
            assertEquals(10.0, rate.above272k?.input)
            assertEquals(45.0, rate.above272k?.output)
            assertEquals(1.0, rate.above272k?.cacheRead)
            assertEquals(0.5, rate.above272k?.cacheWrite)
            assertEquals(30.0, rate.above272k?.reasoning)
            assertEquals(200_000L, rate.above272k?.thresholdTokens)

            val atThreshold = service.calculateCostAndSavings(
                input = 100_000,
                output = 0,
                cacheRead = 100_000,
                cacheWrite = 0,
                reasoning = 0,
                canonicalModelId = "provider/tiered-model"
            )
            assertEquals(0.55, atThreshold.costUsd, 0.000001)
            assertFalse(atThreshold.usedLongContextPricing)

            val result = service.calculateCostAndSavings(
                input = 100_000,
                output = 0,
                cacheRead = 100_001,
                cacheWrite = 0,
                reasoning = 0,
                canonicalModelId = "provider/tiered-model"
            )
            assertEquals(1.100001, result.costUsd, 0.000001)
            assertTrue(result.usedLongContextPricing)
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
                canonicalModelId = "MODEL_PLACEHOLDER_M999"
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
                canonicalModelId = "gpt-4o",
                missingUsageFields = listOf("output")
            )
            assertTrue(lowerBound.pricingMatched)
            assertTrue(lowerBound.lowerBound)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testRequiresExactCanonicalOrRegisteredPricingId() = runBlocking {
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

            assertEquals(
                5.0,
                service.resolveRate(canonicalModelId = "provider-a/unique-model").input
            )
            assertEquals(
                1.0,
                service.resolveRate(canonicalModelId = "provider-a/shared-model").input
            )
            assertEquals(
                0.0,
                service.resolveRate(canonicalModelId = "other/unique-model").input
            )
            assertEquals(
                0.0,
                service.resolveRate(canonicalModelId = "shared-model").input
            )
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
            val result = service.resolvePricing(canonicalModelId = "gpt-4o")

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
            assertEquals(
                ModelPricingRate(),
                service.resolveRate(canonicalModelId = "gemini-3.6-flash-high")
            )
            assertEquals(
                ModelPricingRate(),
                service.resolveRate(canonicalModelId = "MODEL_PLACEHOLDER_M999")
            )
            assertTrue(
                service.resolveRate(
                    canonicalModelId = "gpt-4o",
                    registeredPricingIds = listOf("MODEL_PLACEHOLDER_M999")
                ).input > 0.0
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun tempRoot(): File = File.createTempFile("usage-pricing-", "-root").apply {
        delete()
        mkdirs()
    }
}
