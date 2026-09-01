package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.CanonicalModel
import com.yuzhiqiang.antigravity.domain.model.ModelIdentityRegistryHolder
import com.yuzhiqiang.antigravity.domain.model.ModelIdentityStatus
import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.usage.ConversationUsageData
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.MissingUsageCounts
import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageAggregatorTest {

    private companion object {
        const val TEST_PROVIDER_CONFIG_ID = "test-provider"
    }

    private val registeredModelIds = listOf(
        "claude-3-7-sonnet",
        "gemini-2.0-flash",
        "gpt-4o",
        "claude-3-5-sonnet",
        "gpt-5.6-sol",
        "model",
        "sparse-model",
        "model-a",
        "model-b",
        "provider/tiered-model"
    )

    @BeforeTest
    fun setUpModelIdentityRegistry() {
        ModelIdentityRegistryHolder.updateOfficialModels(emptyList())
        ModelIdentityRegistryHolder.updateConfig(
            AppConfig(
                canonicalModels = registeredModelIds.map { modelId ->
                    CanonicalModel(
                        canonicalModelId = modelId,
                        providerVendor = "test",
                        displayName = modelId
                    )
                },
                providerModelBindings = registeredModelIds.map { modelId ->
                    ProviderModelBinding(
                        bindingId = "binding:$modelId",
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        providerModelId = modelId,
                        canonicalModelId = modelId,
                        displayName = modelId
                    )
                },
                modelRouteVariants = registeredModelIds.map { modelId ->
                    ModelRouteVariant(
                        variantId = "variant:$modelId",
                        bindingId = "binding:$modelId",
                        catalogModelId = "catalog:$modelId",
                        runtimeModelId = "runtime:$modelId",
                        displayName = modelId
                    )
                }
            )
        )
    }

    @AfterTest
    fun tearDownModelIdentityRegistry() {
        ModelIdentityRegistryHolder.updateConfig(AppConfig())
        ModelIdentityRegistryHolder.updateOfficialModels(emptyList())
    }

    private fun createTestPricingService(): PricingCatalogService {
        val root = File.createTempFile("aggregator-pricing-", "-root").apply {
            delete()
            mkdirs()
        }
        val cacheFile = File(root, "pricing_catalog.json")
        cacheFile.writeText(
            """
            {
              "claude-3-7-sonnet": {"cost": {"input": 3.0, "output": 15.0, "cache_read": 0.3, "cache_write": 3.75, "reasoning": 15.0}},
              "gemini-2.0-flash": {"cost": {"input": 0.1, "output": 0.4, "cache_read": 0.025, "cache_write": 0.1, "reasoning": 0.4}},
              "gpt-4o": {"cost": {"input": 2.5, "output": 10.0, "cache_read": 1.25, "cache_write": 2.5, "reasoning": 10.0}}
            }
            """.trimIndent()
        )
        return PricingCatalogService(customRootDir = root)
    }

    @Test
    fun testAggregateAllTime() {
        val pricing = createTestPricingService()

        val convo1 = ConversationUsageData(
            conversationId = "c1",
            appSource = "ide",
            entries = listOf(
                TokenEntry(
                    input = 100_000,
                    output = 20_000,
                    cacheRead = 50_000,
                    cacheWrite = 0,
                    reasoning = 10_000,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "claude-3-7-sonnet"
                    ),
                    timestamp = "2026-08-31T00:00:00Z"
                )
            )
        )

        val convo2 = ConversationUsageData(
            conversationId = "c2",
            appSource = "cli",
            entries = listOf(
                TokenEntry(
                    input = 200_000,
                    output = 40_000,
                    cacheRead = 100_000,
                    cacheWrite = 0,
                    reasoning = 0,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "gemini-2.0-flash"
                    ),
                    timestamp = "2026-08-31T01:00:00Z"
                )
            )
        )

        val stats = UsageAggregator.aggregate(
            conversations = listOf(convo1, convo2),
            pricingService = pricing,
            timeRange = UsageTimeRange.ALL_TIME,
            selectedSources = setOf("all"),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(300_000L, stats.totalInput)
        assertEquals(60_000L, stats.totalOutput)
        assertEquals(150_000L, stats.totalCacheRead)
        assertEquals(10_000L, stats.totalReasoning)
        assertEquals(2L, stats.totalCalls)
        assertEquals(2L, stats.totalConversations)
        assertEquals(150_000.0 / 450_000.0, stats.cacheHitRatio, 0.000001)
        assertEquals(150_000.0 / 450_000.0, stats.promptCacheHitRatio ?: -1.0, 0.000001)
        assertTrue(stats.estimatedCostUsd > 0.0)
        assertTrue(stats.estimatedSavingsUsd > 0.0)
        assertTrue(stats.modelBuckets.all { it.pricingMatched })
        assertTrue(stats.modelBuckets.all { it.pricingSource in setOf("custom", "external") })

        // 测试来源过滤
        val cliOnlyStats = UsageAggregator.aggregate(
            conversations = listOf(convo1, convo2),
            pricingService = pricing,
            timeRange = UsageTimeRange.ALL_TIME,
            selectedSources = setOf("cli"),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(200_000L, cliOnlyStats.totalInput)
        assertEquals(1L, cliOnlyStats.totalCalls)

        val appAliasStats = UsageAggregator.aggregate(
            conversations = listOf(convo1, convo2.copy(appSource = "app")),
            pricingService = pricing,
            timeRange = UsageTimeRange.ALL_TIME,
            selectedSources = setOf("standalone"),
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(200_000L, appAliasStats.totalInput)
        assertEquals("standalone", appAliasStats.sourceBuckets.single().appSource)
    }

    @Test
    fun testCustomDateRangeWithFollowNow() {
        val pricing = PricingCatalogService()
        val now = Instant.now()
        val tsPast = now.minusSeconds(10 * 86400).toString()
        val tsRecent = now.minusSeconds(3600).toString()

        val convo = ConversationUsageData(
            conversationId = "c-custom",
            appSource = "standalone",
            entries = listOf(
                TokenEntry(
                    input = 50_000,
                    output = 10_000,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "gpt-4o"
                    ),
                    timestamp = tsPast
                ),
                TokenEntry(
                    input = 30_000,
                    output = 5_000,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "gpt-4o"
                    ),
                    timestamp = tsRecent
                )
            )
        )

        // 自定义只包含最近 2 天到当前 (Follow Now)
        val stats = UsageAggregator.aggregate(
            conversations = listOf(convo),
            pricingService = pricing,
            timeRange = UsageTimeRange.CUSTOM,
            customDateRange = CustomDateRange(
                startDate = java.time.LocalDate.now().minusDays(1).toString(),
                endDate = "",
                followNow = true
            ),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(30_000L, stats.totalInput)
        assertEquals(5_000L, stats.totalOutput)
        assertEquals(1L, stats.totalCalls)
    }

    @Test
    fun testDailyBucketDateFillingContinuity() {
        val pricing = PricingCatalogService()
        val convo = ConversationUsageData(
            conversationId = "c-continuous",
            appSource = "ide",
            entries = listOf(
                TokenEntry(
                    input = 10_000,
                    output = 2_000,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "claude-3-5-sonnet"
                    ),
                    timestamp = "2026-08-20T10:00:00Z"
                ),
                TokenEntry(
                    input = 20_000,
                    output = 4_000,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "claude-3-5-sonnet"
                    ),
                    timestamp = "2026-08-25T10:00:00Z"
                )
            )
        )

        val stats = UsageAggregator.aggregate(
            conversations = listOf(convo),
            pricingService = pricing,
            timeRange = UsageTimeRange.CUSTOM,
            customDateRange = CustomDateRange(
                startDate = "2026-08-20",
                endDate = "2026-08-25"
            ),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(6, stats.dailyBuckets.size) // 20, 21, 22, 23, 24, 25 共 6 天连续
        assertEquals("2026-08-20", stats.dailyBuckets.first().date)
        assertEquals("2026-08-25", stats.dailyBuckets.last().date)
        assertEquals(10_000L, stats.dailyBuckets[0].input)
        assertEquals(0L, stats.dailyBuckets[1].input) // 缺失日期自动补 0
        assertEquals(20_000L, stats.dailyBuckets[5].input)
    }

    @Test
    fun testCacheWriteIsIncludedInTotalsAndPricing() {
        val isolatedRoot = File.createTempFile("usage-aggregator-pricing-", "-root").apply {
            delete()
            mkdirs()
        }
        val cacheFile = File(isolatedRoot, "pricing_catalog.json")
        cacheFile.writeText(
            """
            {
              "gpt-5.6-sol": {"cost": {"input": 5.0, "output": 30.0, "cache_read": 0.5, "cache_write": 0.5, "reasoning": 30.0}}
            }
            """.trimIndent()
        )
        val pricing = PricingCatalogService(customRootDir = isolatedRoot)
        try {
            val stats = UsageAggregator.aggregate(
                conversations = listOf(
                    ConversationUsageData(
                        conversationId = "cache-write",
                        entries = listOf(
                            TokenEntry(
                                cacheWrite = 1_000_000,
                                modelObservation = ModelObservation(
                                    providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                    responseModelId = "gpt-5.6-sol"
                                ),
                                timestamp = "2026-08-31T00:00:00Z"
                            )
                        )
                    )
                ),
                pricingService = pricing,
                timeRange = UsageTimeRange.ALL_TIME,
                zoneId = ZoneId.of("UTC")
            )

            assertEquals(1_000_000L, stats.totalCacheWrite)
            assertEquals(1_000_000L, stats.totalTokens)
            assertEquals(0.5, stats.estimatedCostUsd, 0.000001)
            assertEquals(1_000_000L, stats.dailyBuckets.single().cacheWrite)
            assertEquals(1_000_000L, stats.hourlyBuckets.first { it.hour == 0 }.cacheWrite)
            assertEquals(0.5, stats.hourlyBuckets.first { it.hour == 0 }.costUsd, 0.000001)
            assertEquals(1_000_000L, stats.modelBuckets.single().cacheWrite)
        } finally {
            isolatedRoot.deleteRecursively()
        }
    }

    @Test
    fun testPromptCacheHitRatioIncludesCacheWriteTokens() {
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(
                    conversationId = "cache-ratio",
                    entries = listOf(
                        TokenEntry(
                            input = 1_000,
                            cacheRead = 6_000,
                            cacheWrite = 3_000,
                            modelObservation = ModelObservation(
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "gpt-4o"
                            ),
                            timestamp = "2026-08-31T00:00:00Z"
                        )
                    )
                )
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(0.6, stats.promptCacheHitRatio ?: -1.0, 0.000001)
        assertEquals(0.6, stats.cacheHitRatio, 0.000001)
        assertTrue(!stats.cacheHitRateIncomplete)
    }

    @Test
    fun testExplicitZeroCacheReadRemainsAValidAggregatedRatio() {
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(
                    conversationId = "zero-cache-hit",
                    entries = listOf(
                        TokenEntry(
                            input = 1_000,
                            cacheRead = 0,
                            modelObservation = ModelObservation(
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "gpt-4o"
                            ),
                            timestamp = "2026-08-31T00:00:00Z"
                        )
                    )
                )
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(0.0, stats.promptCacheHitRatio ?: -1.0)
        assertTrue(!stats.cacheHitRateIncomplete)
    }

    @Test
    fun testCustomEndDateIsInclusiveAndInvalidRangeDoesNotBecomeAllTime() {
        val pricing = createTestPricingService()
        val conversation = ConversationUsageData(
            conversationId = "date-boundary",
            entries = listOf(
                TokenEntry(
                    input = 10,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "gpt-4o"
                    ),
                    timestamp = "2026-08-25T23:59:59.999Z"
                ),
                TokenEntry(
                    input = 20,
                    modelObservation = ModelObservation(
                        providerConfigId = TEST_PROVIDER_CONFIG_ID,
                        responseModelId = "gpt-4o"
                    ),
                    timestamp = "2026-08-26T00:00:00Z"
                )
            )
        )

        val inclusive = UsageAggregator.aggregate(
            conversations = listOf(conversation),
            pricingService = pricing,
            timeRange = UsageTimeRange.CUSTOM,
            customDateRange = CustomDateRange(startDate = "2026-08-25", endDate = "2026-08-25"),
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(10L, inclusive.totalInput)
        assertEquals("2026-08-25", inclusive.dateRangeFrom)
        assertEquals("2026-08-25", inclusive.dateRangeTo)

        val invalid = UsageAggregator.aggregate(
            conversations = listOf(conversation),
            pricingService = pricing,
            timeRange = UsageTimeRange.CUSTOM,
            customDateRange = CustomDateRange(startDate = "not-a-date", endDate = "2026-08-25"),
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(0L, invalid.totalInput)
        assertEquals(0L, invalid.totalCalls)
        assertTrue(invalid.dailyBuckets.isEmpty())
        assertEquals("", invalid.dateRangeFrom)
        assertEquals("", invalid.dateRangeTo)
    }

    @Test
    fun testSameConversationIdFromDifferentSourcesStaysSeparate() {
        val entries = listOf(
            TokenEntry(
                input = 10,
                modelObservation = ModelObservation(
                    providerConfigId = TEST_PROVIDER_CONFIG_ID,
                    responseModelId = "model"
                ),
                timestamp = "2026-08-31T00:00:00Z"
            )
        )
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(conversationId = "same", appSource = "ide", entries = entries),
                ConversationUsageData(conversationId = "same", appSource = "cli", entries = entries)
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(2L, stats.totalConversations)
        assertEquals(2, stats.topConversations.size)
        assertEquals(setOf("ide", "cli"), stats.topConversations.map { it.appSource }.toSet())
    }

    @Test
    fun testMissingUsageDimensionsAreRetainedOnModelBucket() {
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(
                    conversationId = "sparse",
                    entries = listOf(
                        TokenEntry(
                            input = 100,
                            modelObservation = ModelObservation(
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "sparse-model"
                            ),
                            missingUsageFields = listOf("output", "cache", "cacheWrite", "reasoning"),
                            timestamp = "2026-08-31T00:00:00Z"
                        )
                    )
                )
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(
            MissingUsageCounts(output = 1, cache = 1, cacheWrite = 1, reasoning = 1),
            stats.modelBuckets.single().missingUsage
        )
        assertEquals("unmatched", stats.modelBuckets.single().pricingSource)
        assertTrue(stats.modelBuckets.single().cacheHitRateIncomplete)
        assertTrue(stats.cacheHitRateIncomplete)
        assertTrue(stats.costLowerBound)
        assertTrue(!stats.modelBuckets.single().pricingMatched)
        assertTrue(stats.modelBuckets.single().costLowerBound)
        assertTrue(!stats.monthlyBuckets.single().pricingMatched)
        assertTrue(stats.monthlyBuckets.single().costLowerBound)
        assertTrue(!stats.dailyBuckets.single().pricingMatched)
        assertTrue(stats.dailyBuckets.single().costLowerBound)
        assertTrue(!stats.hourlyBuckets.first { it.hour == 0 }.pricingMatched)
        assertTrue(stats.hourlyBuckets.first { it.hour == 0 }.costLowerBound)
        assertTrue(!stats.sourceBuckets.single().pricingMatched)
        assertTrue(stats.sourceBuckets.single().costLowerBound)
        assertTrue(!stats.topConversations.single().pricingMatched)
        assertTrue(stats.topConversations.single().costLowerBound)
    }

    @Test
    fun testDifferentConcreteModelsSharingRuntimePlaceholderStayInSeparateBuckets() {
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(
                    conversationId = "model-evidence",
                    entries = listOf(
                        TokenEntry(
                            input = 100,
                            modelObservation = ModelObservation(
                                runtimeModelId = "MODEL_PLACEHOLDER_M400",
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "model-a"
                            ),
                            timestamp = "2026-08-31T00:00:00Z"
                        ),
                        TokenEntry(
                            input = 200,
                            modelObservation = ModelObservation(
                                runtimeModelId = "MODEL_PLACEHOLDER_M400",
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "model-b"
                            ),
                            timestamp = "2026-08-31T00:01:00Z"
                        )
                    )
                )
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(2, stats.modelBuckets.size)
        assertEquals(
            setOf("model-a", "model-b"),
            stats.modelBuckets.mapNotNull { it.canonicalModelId }.toSet()
        )
        assertEquals(
            setOf("canonical:model-a", "canonical:model-b"),
            stats.modelBuckets.map { it.groupingKey }.toSet()
        )
        assertTrue(stats.modelBuckets.all { it.identityStatus == ModelIdentityStatus.RESOLVED })
        assertEquals(
            setOf(listOf("model-a"), listOf("model-b")),
            stats.modelBuckets.map { it.registeredPricingIds }.toSet()
        )
        assertEquals(
            setOf("variant:model-a", "variant:model-b"),
            stats.modelBuckets.flatMap { bucket ->
                bucket.variantBuckets.mapNotNull { it.variantId }
            }.toSet()
        )
    }

    @Test
    fun testLongContextBucketUsesFullPromptSizeAndDynamicThreshold() {
        val root = File.createTempFile("usage-long-context-", "-root").apply {
            delete()
            mkdirs()
        }
        File(root, "pricing_catalog.json").writeText(
            """
            {
              "provider": {
                "models": {
                  "tiered-model": {
                    "cost": {
                      "input": 5,
                      "output": 30,
                      "cache_read": 0.5,
                      "tiers": [
                        {
                          "input": 10,
                          "output": 45,
                          "cache_read": 1,
                          "tier": {"type": "context", "size": 200000}
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        try {
            val stats = UsageAggregator.aggregate(
                conversations = listOf(
                    ConversationUsageData(
                        conversationId = "cached-long-context",
                        entries = listOf(
                            TokenEntry(
                                input = 100_000,
                                cacheRead = 100_001,
                                modelObservation = ModelObservation(
                                    providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                    responseModelId = "provider/tiered-model"
                                ),
                                timestamp = "2026-08-31T00:00:00Z"
                            )
                        )
                    )
                ),
                pricingService = PricingCatalogService(customRootDir = root),
                timeRange = UsageTimeRange.ALL_TIME,
                zoneId = ZoneId.of("UTC")
            )

            val model = stats.modelBuckets.single()
            val longContext = model.longContext ?: error("长上下文调用应进入独立子桶")
            assertEquals(100_000L, longContext.input)
            assertEquals(100_001L, longContext.cacheRead)
            assertEquals(1L, longContext.calls)
            assertEquals(1.100001, model.costUsd, 0.000001)
            assertTrue(model.longContextPricingApplied)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testUnattributedTokensFlowThroughAllBucketsAndMarkCostAsLowerBound() {
        val zone = ZoneId.of("UTC")
        val timestamp = Instant.now().minusSeconds(1).toString()
        val stats = UsageAggregator.aggregate(
            conversations = listOf(
                ConversationUsageData(
                    conversationId = "unattributed",
                    appSource = "ide",
                    entries = listOf(
                        TokenEntry(
                            input = 1_000_000,
                            unattributed = 5,
                            modelObservation = ModelObservation(
                                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                                responseModelId = "gpt-4o"
                            ),
                            timestamp = timestamp
                        )
                    )
                )
            ),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = zone
        )

        assertEquals(5L, stats.totalUnattributed)
        assertEquals(1_000_005L, stats.totalTokens)
        assertEquals(2.5, stats.estimatedCostUsd, 0.000001)
        assertTrue(stats.costLowerBound)
        assertEquals(5L, stats.todayUnattributed)
        assertEquals(5L, stats.dailyBuckets.single().unattributed)
        assertTrue(stats.dailyBuckets.single().costLowerBound)
        assertEquals(5L, stats.hourlyBuckets.single { it.unattributed > 0L }.unattributed)
        assertEquals(5L, stats.weekdayBuckets.single { it.unattributed > 0L }.unattributed)
        assertEquals(5L, stats.monthlyBuckets.single().unattributed)
        assertEquals(5L, stats.monthlyBuckets.single().topModels.single().unattributed)
        assertEquals(5L, stats.modelBuckets.single().unattributed)
        assertEquals(5L, stats.sourceBuckets.single().unattributed)
        assertEquals(5L, stats.topConversations.single().unattributed)
        assertTrue(stats.modelBuckets.single().pricingMatched)
        assertTrue(stats.modelBuckets.single().costLowerBound)
    }

    @Test
    fun testTodaySummaryIsIndependentOfSelectedRange() {
        val zone = ZoneId.of("UTC")
        val now = Instant.now()
        val todayEntry = TokenEntry(
            input = 100,
            output = 50,
            cacheRead = 300,
            cacheWrite = 25,
            reasoning = 10,
            modelObservation = ModelObservation(
                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                responseModelId = "gpt-4o"
            ),
            timestamp = now.minusSeconds(60).toString()
        )
        val olderEntry = TokenEntry(
            input = 900,
            output = 100,
            modelObservation = ModelObservation(
                providerConfigId = TEST_PROVIDER_CONFIG_ID,
                responseModelId = "gpt-4o"
            ),
            timestamp = now.minusSeconds(8 * 24 * 3600L).toString()
        )
        val conversation = ConversationUsageData(
            conversationId = "today-summary",
            appSource = "ide",
            entries = listOf(todayEntry, olderEntry)
        )

        val allTime = UsageAggregator.aggregate(
            conversations = listOf(conversation),
            pricingService = createTestPricingService(),
            timeRange = UsageTimeRange.ALL_TIME,
            zoneId = zone
        )
        assertEquals(2L, allTime.totalCalls)
        assertEquals(1L, allTime.todayCalls)
        assertEquals(485L, allTime.todayTokens)
        assertEquals(100L, allTime.todayInput)
        assertEquals(300L, allTime.todayCacheRead)
        assertEquals(25L, allTime.todayCacheWrite)
        assertEquals(1L, allTime.todayConversations)
        assertEquals(1L, allTime.todayActiveModels)
        assertEquals(300.0 / 425.0, allTime.todayCacheHitRatio ?: -1.0, 0.000001)

        val outsideToday = UsageAggregator.aggregate(
            conversations = listOf(conversation),
            pricingService = PricingCatalogService(),
            timeRange = UsageTimeRange.CUSTOM,
            customDateRange = CustomDateRange(
                startDate = LocalDate.now(zone).minusDays(3).toString(),
                endDate = LocalDate.now(zone).minusDays(2).toString()
            ),
            zoneId = zone
        )
        assertEquals(0L, outsideToday.totalCalls)
        assertEquals(1L, outsideToday.todayCalls)
        assertEquals(485L, outsideToday.todayTokens)
    }
}
