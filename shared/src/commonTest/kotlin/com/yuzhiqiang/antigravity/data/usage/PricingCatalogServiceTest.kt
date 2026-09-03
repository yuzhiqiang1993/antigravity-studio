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
            // 未提供 cache-write 专属价格时，按普通输入价格回退，避免低估缓存创建费用。
            assertEquals(1.0, rate.cacheWrite, 0.000001)
            assertEquals(3.0, rate.reasoning)
            assertEquals(2.0, rate.above272k?.input)
            assertEquals(4.0, rate.above272k?.output)
            assertEquals(0.1, rate.above272k?.cacheRead ?: 0.0, 0.000001)
            assertEquals(2.0, rate.above272k?.cacheWrite ?: 0.0, 0.000001)
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
            assertEquals(10.0, rate.above272k?.cacheWrite)
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

    @Test
    fun testRequiresCanonicalProviderIdentityWithoutTierOrVendorGuessing() {
        val root = tempRoot()
        try {
            val cacheFile = File(root, "pricing_catalog.json")
            cacheFile.writeText(
                """
                {
                  "google/gemini-3.7-flash": {"cost": {"input": 0.15, "output": 0.6, "cache_read": 0.0375, "cache_write": 0.15, "reasoning": 0.6}},
                  "anthropic/claude-3-5-sonnet": {"cost": {"input": 3.0, "output": 15.0, "cache_read": 0.3, "cache_write": 3.75, "reasoning": 15.0}},
                  "gpt-4o": {"cost": {"input": 2.5, "output": 10.0, "cache_read": 1.25, "cache_write": 2.5, "reasoning": 10.0}}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)

            val geminiRes = service.resolvePricing(canonicalModelId = "google/gemini-3.7-flash")
            assertTrue(geminiRes.matched)
            assertEquals(PricingConfidence.HIGH, geminiRes.confidence)
            assertEquals(0.15, geminiRes.rate.input)

            // 降级模糊策略：缺少厂商前缀时通过 fallback 自动探测，返回 LOW 置信度
            val unqualifiedResult = service.resolvePricing(canonicalModelId = "gemini-3.7-flash")
            assertTrue(unqualifiedResult.matched)
            assertEquals(PricingConfidence.LOW, unqualifiedResult.confidence)
            assertEquals(0.15, unqualifiedResult.rate.input)

            val displayNameResult = service.resolvePricing(canonicalModelId = "Gemini 3.7 Flash (High)")
            assertFalse(displayNameResult.matched)

            val tierResult = service.resolvePricing(canonicalModelId = "google/gemini-3.7-flash-high")
            assertFalse(tierResult.matched)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testFallbackFuzzyCandidateMatchingWhenExactMatchFails() {
        val root = tempRoot()
        try {
            val cacheFile = File(root, "pricing_catalog.json")
            cacheFile.writeText(
                """
                {
                  "google/gemini-2.5-pro": {"cost": {"input": 1.25, "output": 5.0}},
                  "deepseek/deepseek-chat": {"cost": {"input": 0.14, "output": 0.28}},
                  "anthropic/claude-3-7-sonnet": {"cost": {"input": 3.0, "output": 15.0}}
                }
                """.trimIndent()
            )
            val service = PricingCatalogService(customRootDir = root)

            // 1. 精确匹配：返回 HIGH 置信度
            val exact = service.resolvePricing("google/gemini-2.5-pro")
            assertTrue(exact.matched)
            assertEquals(PricingConfidence.HIGH, exact.confidence)

            // 2. 缺少厂商前缀：降级匹配成功，返回 LOW 置信度
            val noPrefix = service.resolvePricing("gemini-2.5-pro")
            assertTrue(noPrefix.matched)
            assertEquals(PricingConfidence.LOW, noPrefix.confidence)
            assertEquals(1.25, noPrefix.rate.input)

            // 3. 下划线：降级转换为短横线连字符后匹配
            val underscore = service.resolvePricing("deepseek_chat")
            assertTrue(underscore.matched)
            assertEquals(PricingConfidence.LOW, underscore.confidence)
            assertEquals(0.14, underscore.rate.input)

            // 4. 带日期快照后缀：降级剥离日期后缀后命中基础模型单价
            val dated = service.resolvePricing("claude-3-7-sonnet-20250219")
            assertTrue(dated.matched)
            assertEquals(PricingConfidence.LOW, dated.confidence)
            assertEquals(3.0, dated.rate.input)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun tempRoot(): File = File.createTempFile("usage-pricing-", "-root").apply {
        delete()
        mkdirs()
    }
}
