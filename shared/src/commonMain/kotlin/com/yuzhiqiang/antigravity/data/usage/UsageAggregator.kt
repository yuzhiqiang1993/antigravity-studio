package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.ModelIdentityRegistryHolder
import com.yuzhiqiang.antigravity.domain.model.ModelIdentityStatus
import com.yuzhiqiang.antigravity.domain.model.ResolvedModelIdentity
import com.yuzhiqiang.antigravity.domain.model.usage.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 纯函数用量数据聚合器；输出字段语义与插件端 DeepUsageStats 保持一致。
 */
object UsageAggregator {

    private data class TimeBounds(
        val from: Instant?,
        val toExclusive: Instant?,
        val valid: Boolean = true
    )

    /**
     * 聚合所有会话的 Token 数据并输出 DeepUsageStats
     */
    fun aggregate(
        conversations: List<ConversationUsageData>,
        pricingService: PricingCatalogService,
        timeRange: UsageTimeRange = UsageTimeRange.CALENDAR_TODAY,
        customDateRange: CustomDateRange? = null,
        selectedSources: Set<String> = setOf("all"),
        selectedModel: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowInstant: Instant = Instant.ofEpochMilli(System.currentTimeMillis()),
        cnyRate: Double = 7.25,
        isCancelled: (() -> Boolean)? = null
    ): DeepUsageStats {
        val nowMillis = nowInstant.toEpochMilli()

        val timeBounds = resolveTimeBounds(timeRange, nowInstant, zoneId, customDateRange)
        val fromMillis = timeBounds.from?.toEpochMilli()
        val toExclusiveMillis = timeBounds.toExclusive?.toEpochMilli()

        val identityRegistry = ModelIdentityRegistryHolder.snapshot()
        val normalizedSelectedSources = selectedSources.mapTo(mutableSetOf(), ::normalizeSource)
        val allowAllSources = normalizedSelectedSources.isEmpty() || normalizedSelectedSources.contains("all")
        val isModelFilterActive = !selectedModel.isNullOrBlank() && selectedModel != "all"

        var totalInput = 0L
        var totalOutput = 0L
        var totalCacheRead = 0L
        var totalCacheWrite = 0L
        var totalReasoning = 0L
        var totalUnattributed = 0L
        var totalCalls = 0L
        var totalCost = 0.0
        var totalSavings = 0.0
        var totalCostLowerBound = false

        val conversationIdSet = mutableSetOf<String>()
        val activeDatesSet = mutableSetOf<String>()

        val dailyMap = mutableMapOf<String, DailyBucketAccumulator>()
        val hourlyMap = (0..23).associateWith { HourlyBucketAccumulator(it) }.toMutableMap()
        val monthlyMap = mutableMapOf<String, MonthlyBucketAccumulator>()
        val modelMap = mutableMapOf<String, ModelBucketAccumulator>()
        val sourceMap = mutableMapOf<String, SourceBucketAccumulator>()
        val convoMap = mutableMapOf<String, ConversationBucketAccumulator>()
        val scopedModelCounts = mutableMapOf<String, ModelAccumulatorCounter>()

        // 今日统计始终按本地日 [00:00, now) 聚合，不随当前时间范围变化。
        val todayDate = nowInstant.atZone(zoneId).toLocalDate()
        val todayStart = todayDate.atStartOfDay(zoneId).toInstant()
        val todayStartMillis = todayStart.toEpochMilli()
        var todayInput = 0L
        var todayOutput = 0L
        var todayCacheRead = 0L
        var todayCacheWrite = 0L
        var todayReasoning = 0L
        var todayUnattributed = 0L
        var todayCalls = 0L
        var todayCost = 0.0
        var todaySavings = 0.0
        var todayPricingMatched = true
        var todayCostLowerBound = false
        val todayConversationKeys = mutableSetOf<String>()
        val todayModelKeys = mutableSetOf<String>()

        for ((convoIdx, convo) in conversations.withIndex()) {
            if (convoIdx % 30 == 0 && isCancelled?.invoke() == true) {
                return DeepUsageStats()
            }
            val appSource = normalizeSource(convo.appSource)
            if (!allowAllSources && !normalizedSelectedSources.contains(appSource)) continue

            val conversationKey = "$appSource:${convo.conversationId}"
            var convoHasMatchedEntry = false

            for (entry in convo.entries) {
                val entrySource = normalizeSource(entry.appSource.ifBlank { appSource })
                if (!allowAllSources && !normalizedSelectedSources.contains(entrySource)) continue
                if (entry.totalTokens <= 0L) continue

                val identity = identityRegistry.resolve(entry.modelObservation)
                val modelGroupingKey = identity.groupingKey

                val entryMillis = entry.epochMillis
                val isTimestampValid = entryMillis > 0L
                val outsideFilter = !timeBounds.valid ||
                        (fromMillis != null && (!isTimestampValid || entryMillis < fromMillis)) ||
                        (toExclusiveMillis != null && (!isTimestampValid || entryMillis >= toExclusiveMillis))

                val input = entry.input.coerceAtLeast(0L)
                val output = entry.output.coerceAtLeast(0L)
                val cacheRead = entry.cacheRead.coerceAtLeast(0L)
                val cacheWrite = entry.cacheWrite.coerceAtLeast(0L)
                val reasoning = entry.reasoning.coerceAtLeast(0L)
                val unattributed = entry.unattributed.coerceAtLeast(0L)
                val costResult = if (identity.status == ModelIdentityStatus.RESOLVED) {
                    pricingService.calculateCostAndSavings(
                        input = input,
                        output = output,
                        cacheRead = cacheRead,
                        cacheWrite = cacheWrite,
                        reasoning = reasoning,
                        canonicalModelId = identity.canonicalModelId,
                        registeredPricingIds = identity.pricingModelIds,
                        missingUsageFields = entry.missingUsageFields,
                        unattributed = unattributed
                    )
                } else {
                    unpricedCostResult(entry.missingUsageFields, unattributed)
                }

                if (isTimestampValid && entryMillis >= todayStartMillis && entryMillis < nowMillis) {
                    todayInput += input
                    todayOutput += output
                    todayCacheRead += cacheRead
                    todayCacheWrite += cacheWrite
                    todayReasoning += reasoning
                    todayUnattributed += unattributed
                    todayCalls += 1
                    todayCost += costResult.costUsd
                    todaySavings += costResult.savingsUsd
                    todayPricingMatched = todayPricingMatched && costResult.pricingMatched
                    todayCostLowerBound = todayCostLowerBound || costResult.lowerBound
                    todayConversationKeys += conversationKey
                    todayModelKeys += identity.groupingKey
                }

                // 月度账单与插件一致：只要时间戳有效，就不受当前日/周筛选影响。
                val instant = if (isTimestampValid) Instant.ofEpochMilli(entryMillis) else null
                if (timeBounds.valid && instant != null) {
                    val monthDate = instant.atZone(zoneId).toLocalDate()
                    val ymStr = monthDate.toString().substring(0, 7)
                    val monthlyAcc = monthlyMap.getOrPut(ymStr) {
                        MonthlyBucketAccumulator(ymStr, formatMonthLabel(ymStr))
                    }
                    monthlyAcc.add(input, output, cacheRead, cacheWrite, reasoning, unattributed, costResult)
                    monthlyAcc.addModel(
                        identity = identity,
                        input = input,
                        output = output,
                        cacheRead = cacheRead,
                        cacheWrite = cacheWrite,
                        reasoning = reasoning,
                        unattributed = unattributed,
                        costResult = costResult,
                        missingUsageFields = entry.missingUsageFields
                    )
                }

                if (outsideFilter) continue

                // 收集当前选定时间范围与来源范围内的模型筛选候选列表
                val modelDisplay = identity.displayName.ifBlank { identity.canonicalModelId ?: modelGroupingKey }
                val modelCounter = scopedModelCounts.getOrPut(modelGroupingKey) {
                    ModelAccumulatorCounter(modelGroupingKey, modelDisplay)
                }
                modelCounter.calls += 1
                modelCounter.tokens += entry.totalTokens

                // 若启用了指定模型筛选，排除不匹配的模型记录
                if (isModelFilterActive) {
                    val matches = modelGroupingKey == selectedModel ||
                            identity.canonicalModelId.equals(selectedModel, ignoreCase = true) ||
                            identity.displayName.equals(selectedModel, ignoreCase = true)
                    if (!matches) continue
                }

                convoHasMatchedEntry = true

                totalInput += input
                totalOutput += output
                totalCacheRead += cacheRead
                totalCacheWrite += cacheWrite
                totalReasoning += reasoning
                totalUnattributed += unattributed
                totalCalls += 1
                totalCost += costResult.costUsd
                totalSavings += costResult.savingsUsd
                totalCostLowerBound = totalCostLowerBound || costResult.lowerBound

                val zonedDateTime = instant?.atZone(zoneId)
                if (zonedDateTime != null) {
                    val dateStr = zonedDateTime.toLocalDate().toString()
                    activeDatesSet.add(dateStr)

                    val dailyAcc = dailyMap.getOrPut(dateStr) { DailyBucketAccumulator(dateStr) }
                    dailyAcc.add(
                        input,
                        output,
                        cacheRead,
                        cacheWrite,
                        reasoning,
                        unattributed,
                        costResult
                    )

                    hourlyMap[zonedDateTime.hour]?.add(
                        input,
                        output,
                        cacheRead,
                        cacheWrite,
                        reasoning,
                        unattributed,
                        costResult
                    )
                }

                // 一级按 registry 的稳定 groupingKey 聚合，二级在桶内按 variantId 聚合。
                val modelKey = identity.groupingKey
                val modelAcc = modelMap.getOrPut(modelKey) {
                    ModelBucketAccumulator(identity)
                }
                modelAcc.add(
                    input = input,
                    output = output,
                    cacheRead = cacheRead,
                    cacheWrite = cacheWrite,
                    reasoning = reasoning,
                    unattributed = unattributed,
                    identity = identity,
                    missingUsageFields = entry.missingUsageFields,
                    costResult = costResult,
                    isLongContext = costResult.usedLongContextPricing
                )

                val srcDisplayName = formatAppSourceDisplayName(entrySource)
                val srcAcc = sourceMap.getOrPut(entrySource) { SourceBucketAccumulator(entrySource, srcDisplayName) }
                srcAcc.add(input, output, cacheRead, cacheWrite, reasoning, unattributed, costResult)

                val convoAcc = convoMap.getOrPut(conversationKey) {
                    ConversationBucketAccumulator(
                        convo.conversationId,
                        convo.title.ifBlank { "会话 ${convo.conversationId.take(8)}" },
                        appSource
                    )
                }
                convoAcc.add(
                    input,
                    output,
                    cacheRead,
                    cacheWrite,
                    reasoning,
                    unattributed,
                    costResult,
                    entry.timestamp
                )
            }

            if (convoHasMatchedEntry) conversationIdSet.add(conversationKey)
        }

        // 仅对合法范围补齐连续日期；无效自定义范围不能退化为全量统计。
        val resolvedFromDate = if (timeBounds.valid) {
            timeBounds.from?.atZone(zoneId)?.toLocalDate()
                ?: (dailyMap.keys.minOrNull()?.let { parseIsoLocalDate(it) })
        } else {
            null
        }
        val candidateToDate = if (timeBounds.valid) {
            timeBounds.toExclusive?.let { endExclusive ->
                val displayInstant = if (timeBounds.from != null && !timeBounds.from.isBefore(endExclusive)) {
                    timeBounds.from
                } else {
                    endExclusive.minusNanos(1)
                }
                displayInstant.atZone(zoneId).toLocalDate()
            } ?: (dailyMap.keys.maxOrNull()?.let { parseIsoLocalDate(it) })
        } else {
            null
        }
        val resolvedToDate = when {
            resolvedFromDate == null -> candidateToDate
            candidateToDate == null -> null
            candidateToDate.isBefore(resolvedFromDate) -> resolvedFromDate
            else -> candidateToDate
        }

        if (resolvedFromDate != null && resolvedToDate != null && !resolvedFromDate.isAfter(resolvedToDate)) {
            var curr: LocalDate = resolvedFromDate
            while (!curr.isAfter(resolvedToDate)) {
                val dStr = curr.toString()
                if (!dailyMap.containsKey(dStr)) dailyMap[dStr] = DailyBucketAccumulator(dStr)
                curr = curr.plusDays(1)
            }
        }

        fillMissingMonthlyBuckets(monthlyMap)

        val sortedDaily = dailyMap.values
            .map { it.toDailyBucket() }
            .sortedBy { it.date }

        val sortedHourly = hourlyMap.values
            .map { it.toHourlyBucket() }
            .sortedBy { it.hour }

        val sortedMonthly = monthlyMap.values
            .map { it.toMonthlyBucket() }
            .sortedByDescending { it.yearMonth }

        val sortedModels = modelMap.values
            .map { it.toModelBucket() }
            .sortedByDescending { it.totalTokens }

        val sortedSources = sourceMap.values
            .map { it.toAppSourceBucket() }
            .sortedWith(compareByDescending<AppSourceUsageBucket> { it.costUsd }.thenByDescending { it.totalTokens })

        val sortedTopConvos = convoMap.values
            .map { it.toConversationBucket() }
            .sortedByDescending { it.totalTokens }
            .take(30)

        val modelOptions = scopedModelCounts.values
            .sortedByDescending { it.tokens }
            .map {
                ModelFilterOption(
                    id = it.id,
                    displayName = it.displayName,
                    callCount = it.calls,
                    totalTokens = it.tokens
                )
            }

        return DeepUsageStats(
            totalInput = totalInput,
            totalOutput = totalOutput,
            totalCacheRead = totalCacheRead,
            totalCacheWrite = totalCacheWrite,
            totalReasoning = totalReasoning,
            totalUnattributed = totalUnattributed,
            totalCalls = totalCalls,
            totalConversations = conversationIdSet.size.toLong(),
            daysActive = activeDatesSet.size,
            estimatedCostUsd = totalCost,
            estimatedSavingsUsd = totalSavings,
            costLowerBound = totalCostLowerBound,
            cnyRate = cnyRate,
            todayDate = todayDate.toString(),
            todayInput = todayInput,
            todayOutput = todayOutput,
            todayCacheRead = todayCacheRead,
            todayCacheWrite = todayCacheWrite,
            todayReasoning = todayReasoning,
            todayUnattributed = todayUnattributed,
            todayCalls = todayCalls,
            todayConversations = todayConversationKeys.size.toLong(),
            todayActiveModels = todayModelKeys.size.toLong(),
            todayCostUsd = todayCost,
            todaySavingsUsd = todaySavings,
            todayPricingMatched = todayPricingMatched,
            todayCostLowerBound = todayCostLowerBound,
            dateRangeFrom = resolvedFromDate?.toString().orEmpty(),
            dateRangeTo = resolvedToDate?.toString().orEmpty(),
            dailyBuckets = sortedDaily,
            hourlyBuckets = sortedHourly,
            weekdayBuckets = emptyList(),
            monthlyBuckets = sortedMonthly,
            modelBuckets = sortedModels,
            sourceBuckets = sortedSources,
            topConversations = sortedTopConvos,
            availableModels = modelOptions,
            timeRange = timeRange,
            generatedAt = nowMillis
        )
    }

    private fun resolveTimeBounds(
        timeRange: UsageTimeRange,
        nowInstant: Instant,
        zoneId: ZoneId,
        customDateRange: CustomDateRange?
    ): TimeBounds {
        val today = nowInstant.atZone(zoneId).toLocalDate()
        return when (timeRange) {
            UsageTimeRange.CALENDAR_TODAY -> TimeBounds(today.atStartOfDay(zoneId).toInstant(), nowInstant)
            UsageTimeRange.ROLLING_24H -> TimeBounds(nowInstant.minusSeconds(24 * 3600), nowInstant)
            UsageTimeRange.ROLLING_7D -> TimeBounds(nowInstant.minusSeconds(7 * 24 * 3600), nowInstant)
            UsageTimeRange.ROLLING_14D -> TimeBounds(nowInstant.minusSeconds(14 * 24 * 3600), nowInstant)
            UsageTimeRange.ROLLING_30D -> TimeBounds(nowInstant.minusSeconds(30 * 24 * 3600), nowInstant)
            UsageTimeRange.CALENDAR_THIS_WEEK -> TimeBounds(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId).toInstant(),
                nowInstant
            )

            UsageTimeRange.CALENDAR_THIS_MONTH -> TimeBounds(
                LocalDate.of(today.year, today.monthValue, 1).atStartOfDay(zoneId).toInstant(),
                nowInstant
            )

            UsageTimeRange.ALL_TIME -> TimeBounds(null, null)
            UsageTimeRange.CUSTOM -> {
                if (customDateRange == null) return TimeBounds(null, null, valid = false)
                val start = parseFlexibleInstant(
                    dateText = customDateRange.startDate,
                    timeText = customDateRange.startTime,
                    isEnd = false,
                    zoneId = zoneId
                )
                val end = if (customDateRange.followNow || customDateRange.endDate.isBlank()) {
                    nowInstant
                } else {
                    parseFlexibleInstant(
                        dateText = customDateRange.endDate,
                        timeText = customDateRange.endTime,
                        isEnd = true,
                        zoneId = zoneId
                    )
                }
                if (start == null || end == null || !start.isBefore(end)) {
                    TimeBounds(start, end, valid = false)
                } else {
                    TimeBounds(start, end)
                }
            }
        }
    }

    private fun parseFlexibleInstant(
        dateText: String,
        timeText: String = "",
        isEnd: Boolean,
        zoneId: ZoneId
    ): Instant? {
        val trimmedDate = dateText.trim()
        if (trimmedDate.isBlank()) return null

        val direct = parseInstant(trimmedDate)
        if (direct != null) return direct

        val localDate = parseIsoLocalDate(trimmedDate) ?: parseSlashLocalDate(trimmedDate)
        if (localDate != null) {
            val localTime = parseLocalTime(timeText)
            return if (localTime != null) {
                if (isEnd) {
                    localDate.atTime(localTime).plusMinutes(1).atZone(zoneId).toInstant()
                } else {
                    localDate.atTime(localTime).atZone(zoneId).toInstant()
                }
            } else {
                if (isEnd) {
                    localDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                } else {
                    localDate.atStartOfDay(zoneId).toInstant()
                }
            }
        }

        return null
    }

    private fun parseSlashLocalDate(str: String): LocalDate? {
        return try {
            val normalized = str.replace('/', '-')
            LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLocalTime(str: String): java.time.LocalTime? {
        val trimmed = str.trim()
        if (trimmed.isBlank()) return null
        return try {
            java.time.LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            try {
                java.time.LocalTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_TIME)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseInstant(ts: String): Instant? {
        if (ts.isBlank()) return null
        return try {
            Instant.parse(ts)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIsoLocalDate(str: String): LocalDate? {
        return try {
            LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun isLaterTimestamp(candidate: String, current: String): Boolean {
        if (current.isBlank()) return true
        return try {
            Instant.parse(candidate).isAfter(Instant.parse(current))
        } catch (_: Exception) {
            candidate > current
        }
    }

    private fun unpricedCostResult(
        missingUsageFields: Collection<String>,
        unattributed: Long
    ): CostCalculationResult = CostCalculationResult(
        costUsd = 0.0,
        savingsUsd = 0.0,
        lowerBound = missingUsageFields.isNotEmpty() || unattributed > 0L
    )

    private fun fillMissingMonthlyBuckets(monthlyMap: MutableMap<String, MonthlyBucketAccumulator>) {
        val first = monthlyMap.keys.minOrNull() ?: return
        val last = monthlyMap.keys.maxOrNull() ?: return
        var cursor = parseIsoLocalDate("$first-01") ?: return
        val end = parseIsoLocalDate("$last-01") ?: return
        while (!cursor.isAfter(end)) {
            val yearMonth = cursor.toString().substring(0, 7)
            monthlyMap.getOrPut(yearMonth) {
                MonthlyBucketAccumulator(yearMonth, formatMonthLabel(yearMonth))
            }
            cursor = cursor.plusMonths(1)
        }
    }

    private fun formatMonthLabel(ym: String): String {
        return try {
            val parts = ym.split("-")
            "${parts[0]}年${parts[1].toInt()}月"
        } catch (_: Exception) {
            ym
        }
    }


    private fun normalizeSource(source: String): String = when (source.trim().lowercase()) {
        "app" -> "standalone"
        "ide", "standalone", "cli" -> source.trim().lowercase()
        else -> source.trim().lowercase().ifBlank { "ide" }
    }

    private fun formatAppSourceDisplayName(source: String): String {
        return when (source.lowercase()) {
            "ide" -> "Antigravity IDE"
            "standalone", "app" -> "Antigravity App"
            "cli" -> "Antigravity CLI"
            else -> source
        }
    }

    private class DailyBucketAccumulator(val date: String) {
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var savingsUsd: Double = 0.0
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        var hasPricingResolution: Boolean = false

        fun add(i: Long, o: Long, cr: Long, cw: Long, r: Long, u: Long, costResult: CostCalculationResult) {
            input += i
            output += o
            cacheRead += cr
            cacheWrite += cw
            reasoning += r
            unattributed += u
            calls += 1
            costUsd += costResult.costUsd
            savingsUsd += costResult.savingsUsd
            pricingMatched = if (hasPricingResolution) {
                pricingMatched && costResult.pricingMatched
            } else {
                costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            hasPricingResolution = true
        }

        fun toDailyBucket(): DailyUsageBucket = DailyUsageBucket(
            date = date,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            savingsUsd = savingsUsd,
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class HourlyBucketAccumulator(val hour: Int) {
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        var hasPricingResolution: Boolean = false

        fun add(i: Long, o: Long, cr: Long, cw: Long, r: Long, u: Long, costResult: CostCalculationResult) {
            input += i
            output += o
            cacheRead += cr
            cacheWrite += cw
            reasoning += r
            unattributed += u
            calls += 1
            costUsd += costResult.costUsd
            pricingMatched = if (hasPricingResolution) {
                pricingMatched && costResult.pricingMatched
            } else {
                costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            hasPricingResolution = true
        }

        fun toHourlyBucket(): HourlyUsageBucket = HourlyUsageBucket(
            hour = hour,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class MonthlyBucketAccumulator(val yearMonth: String, val label: String) {
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var hasPricingResolution: Boolean = false
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        val modelMap = mutableMapOf<String, ModelBucketAccumulator>()

        fun add(i: Long, o: Long, cr: Long, cw: Long, r: Long, u: Long, costResult: CostCalculationResult) {
            input += i
            output += o
            cacheRead += cr
            cacheWrite += cw
            reasoning += r
            unattributed += u
            calls += 1
            costUsd += costResult.costUsd
            if (!hasPricingResolution) {
                pricingMatched = costResult.pricingMatched
                hasPricingResolution = true
            } else {
                pricingMatched = pricingMatched && costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
        }

        fun addModel(
            identity: ResolvedModelIdentity,
            input: Long,
            output: Long,
            cacheRead: Long,
            cacheWrite: Long,
            reasoning: Long,
            unattributed: Long,
            costResult: CostCalculationResult,
            missingUsageFields: List<String>
        ) {
            val key = identity.groupingKey
            val acc = modelMap.getOrPut(key) { ModelBucketAccumulator(identity) }
            acc.add(
                input = input,
                output = output,
                cacheRead = cacheRead,
                cacheWrite = cacheWrite,
                reasoning = reasoning,
                unattributed = unattributed,
                identity = identity,
                missingUsageFields = missingUsageFields,
                costResult = costResult,
                isLongContext = costResult.usedLongContextPricing
            )
        }

        fun toMonthlyBucket(): MonthlyUsageBucket = MonthlyUsageBucket(
            yearMonth = yearMonth,
            label = label,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            topModels = modelMap.values.map { it.toModelBucket() }.sortedByDescending { it.totalTokens }.take(5),
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class ConversationBucketAccumulator(
        val conversationId: String,
        val title: String,
        val appSource: String
    ) {
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var lastActiveTs: String = ""
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        var hasPricingResolution: Boolean = false

        fun add(
            i: Long,
            o: Long,
            cr: Long,
            cw: Long,
            r: Long,
            u: Long,
            costResult: CostCalculationResult,
            ts: String
        ) {
            input += i
            output += o
            cacheRead += cr
            cacheWrite += cw
            reasoning += r
            unattributed += u
            calls += 1
            costUsd += costResult.costUsd
            pricingMatched = if (hasPricingResolution) {
                pricingMatched && costResult.pricingMatched
            } else {
                costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            hasPricingResolution = true
            if (ts.isNotBlank() && isLaterTimestamp(ts, lastActiveTs)) {
                lastActiveTs = ts
            }
        }

        fun toConversationBucket(): ConversationUsageBucket = ConversationUsageBucket(
            conversationId = conversationId,
            title = title,
            appSource = appSource,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            lastActiveTimestamp = lastActiveTs,
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class ModelBucketAccumulator(identity: ResolvedModelIdentity) {
        val groupingKey: String = identity.groupingKey
        val displayName: String = identity.displayName
        var identityStatus: ModelIdentityStatus = identity.status
        var canonicalModelId: String? = identity.canonicalModelId
        val registeredPricingIds = identity.pricingModelIds.toMutableSet()
        val variantMap = mutableMapOf<String?, ModelVariantBucketAccumulator>()
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var savingsUsd: Double = 0.0
        var longContextInput: Long = 0L
        var longContextOutput: Long = 0L
        var longContextCacheRead: Long = 0L
        var longContextCacheWrite: Long = 0L
        var longContextReasoning: Long = 0L
        var longContextUnattributed: Long = 0L
        var longContextCalls: Long = 0L
        var missingInputCalls: Long = 0L
        var missingOutputCalls: Long = 0L
        var missingCacheCalls: Long = 0L
        var missingCacheWriteCalls: Long = 0L
        var missingReasoningCalls: Long = 0L
        var hasPricingResolution: Boolean = false
        var pricingSource: PricingSource = PricingSource.UNMATCHED
        var pricingMatched: Boolean = true
        var pricingConfidence: PricingConfidence = PricingConfidence.HIGH
        var costLowerBound: Boolean = false
        var longContextPricingApplied: Boolean = false

        fun add(
            input: Long,
            output: Long,
            cacheRead: Long,
            cacheWrite: Long,
            reasoning: Long,
            unattributed: Long,
            identity: ResolvedModelIdentity,
            missingUsageFields: List<String>,
            costResult: CostCalculationResult,
            isLongContext: Boolean
        ) {
            this.input += input
            this.output += output
            this.cacheRead += cacheRead
            this.cacheWrite += cacheWrite
            this.reasoning += reasoning
            this.unattributed += unattributed
            calls += 1
            costUsd += costResult.costUsd
            savingsUsd += costResult.savingsUsd
            if (!hasPricingResolution) {
                pricingSource = costResult.pricingSource
                pricingMatched = costResult.pricingMatched
                pricingConfidence = costResult.pricingConfidence
                hasPricingResolution = true
            } else {
                pricingMatched = pricingMatched && costResult.pricingMatched
                if (pricingSource != costResult.pricingSource) {
                    pricingSource = PricingSource.MIXED
                    pricingConfidence = PricingConfidence.LOW
                }
                if (pricingConfidence != costResult.pricingConfidence) pricingConfidence = PricingConfidence.LOW
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            longContextPricingApplied = longContextPricingApplied || costResult.usedLongContextPricing
            if (identityStatus != identity.status) identityStatus = ModelIdentityStatus.CONFLICT
            if (canonicalModelId == null) canonicalModelId = identity.canonicalModelId
            if (identity.status == ModelIdentityStatus.RESOLVED) {
                registeredPricingIds += identity.pricingModelIds
            }
            if ("input" in missingUsageFields) missingInputCalls += 1
            if ("output" in missingUsageFields) missingOutputCalls += 1
            if ("cache" in missingUsageFields) missingCacheCalls += 1
            if ("cacheWrite" in missingUsageFields) missingCacheWriteCalls += 1
            if ("reasoning" in missingUsageFields) missingReasoningCalls += 1
            if (isLongContext) {
                longContextInput += input
                longContextOutput += output
                longContextCacheRead += cacheRead
                longContextCacheWrite += cacheWrite
                longContextReasoning += reasoning
                longContextUnattributed += unattributed
                longContextCalls += 1
            }
            variantMap.getOrPut(identity.variantId) {
                ModelVariantBucketAccumulator(identity)
            }.add(
                input = input,
                output = output,
                cacheRead = cacheRead,
                cacheWrite = cacheWrite,
                reasoning = reasoning,
                unattributed = unattributed,
                identity = identity,
                costResult = costResult
            )
        }

        fun toModelBucket(): ModelUsageBucket = ModelUsageBucket(
            groupingKey = groupingKey,
            displayName = displayName,
            identityStatus = identityStatus,
            canonicalModelId = canonicalModelId,
            registeredPricingIds = registeredPricingIds.toList(),
            variantBuckets = variantMap.values
                .map { it.toVariantBucket() }
                .sortedByDescending { it.totalTokens },
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            savingsUsd = savingsUsd,
            missingUsage = if (
                missingInputCalls + missingOutputCalls + missingCacheCalls +
                missingCacheWriteCalls + missingReasoningCalls > 0
            ) {
                MissingUsageCounts(
                    input = missingInputCalls,
                    output = missingOutputCalls,
                    cache = missingCacheCalls,
                    cacheWrite = missingCacheWriteCalls,
                    reasoning = missingReasoningCalls
                )
            } else null,
            longContext = if (longContextCalls > 0) LongContextUsageBucket(
                input = longContextInput,
                output = longContextOutput,
                cacheRead = longContextCacheRead,
                cacheWrite = longContextCacheWrite,
                reasoning = longContextReasoning,
                unattributed = longContextUnattributed,
                calls = longContextCalls
            ) else null,
            pricingSource = pricingSource.id,
            pricingMatched = pricingMatched,
            pricingConfidence = pricingConfidence.name.lowercase(),
            costLowerBound = costLowerBound,
            longContextPricingApplied = longContextPricingApplied
        )
    }

    private class ModelVariantBucketAccumulator(identity: ResolvedModelIdentity) {
        val variantId: String? = identity.variantId
        val displayName: String = identity.displayName
        var identityStatus: ModelIdentityStatus = identity.status
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var savingsUsd: Double = 0.0
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        var hasPricingResolution: Boolean = false

        fun add(
            input: Long,
            output: Long,
            cacheRead: Long,
            cacheWrite: Long,
            reasoning: Long,
            unattributed: Long,
            identity: ResolvedModelIdentity,
            costResult: CostCalculationResult
        ) {
            this.input += input
            this.output += output
            this.cacheRead += cacheRead
            this.cacheWrite += cacheWrite
            this.reasoning += reasoning
            this.unattributed += unattributed
            calls += 1
            costUsd += costResult.costUsd
            savingsUsd += costResult.savingsUsd
            pricingMatched = if (hasPricingResolution) {
                pricingMatched && costResult.pricingMatched
            } else {
                costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            hasPricingResolution = true
            if (identityStatus != identity.status) identityStatus = ModelIdentityStatus.CONFLICT
        }

        fun toVariantBucket(): ModelVariantUsageBucket = ModelVariantUsageBucket(
            variantKey = variantId.orEmpty(),
            variantId = variantId,
            displayName = displayName,
            identityStatus = identityStatus,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            savingsUsd = savingsUsd,
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class SourceBucketAccumulator(val appSource: String, val displayName: String) {
        var input: Long = 0L
        var output: Long = 0L
        var cacheRead: Long = 0L
        var cacheWrite: Long = 0L
        var reasoning: Long = 0L
        var unattributed: Long = 0L
        var calls: Long = 0L
        var costUsd: Double = 0.0
        var pricingMatched: Boolean = true
        var costLowerBound: Boolean = false
        var hasPricingResolution: Boolean = false

        fun add(i: Long, o: Long, cr: Long, cw: Long, r: Long, u: Long, costResult: CostCalculationResult) {
            input += i
            output += o
            cacheRead += cr
            cacheWrite += cw
            reasoning += r
            unattributed += u
            calls += 1
            costUsd += costResult.costUsd
            pricingMatched = if (hasPricingResolution) {
                pricingMatched && costResult.pricingMatched
            } else {
                costResult.pricingMatched
            }
            costLowerBound = costLowerBound || costResult.lowerBound
            hasPricingResolution = true
        }

        fun toAppSourceBucket(): AppSourceUsageBucket = AppSourceUsageBucket(
            appSource = appSource,
            displayName = displayName,
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            unattributed = unattributed,
            calls = calls,
            costUsd = costUsd,
            pricingMatched = pricingMatched,
            costLowerBound = costLowerBound
        )
    }

    private class ModelAccumulatorCounter(
        val id: String,
        val displayName: String,
        var calls: Long = 0L,
        var tokens: Long = 0L
    )
}
