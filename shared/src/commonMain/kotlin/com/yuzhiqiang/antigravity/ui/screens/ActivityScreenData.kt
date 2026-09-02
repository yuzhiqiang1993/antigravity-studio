package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.runtime.*
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ActivityFilterCounts(
    val clientCounts: Map<ActivityClientKind, Int> = emptyMap(),
    val endpointCounts: List<Pair<String, Int>> = emptyList(),
    val routeCounts: List<Pair<String, Int>> = emptyList(),
    val statusCounts: Map<ActivityStatusKind, Int> = emptyMap()
)

internal fun calculateFilterCounts(logs: List<ActivityLog>): ActivityFilterCounts {
    val clientCounts = ActivityClientKind.values().associateWith { kind -> logs.count { it.clientKind() == kind } }
    val endpointCounts = logs.groupingBy(ActivityLog::path)
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    val routeCounts = logs.groupingBy(ActivityLog::routeKey)
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    val statusCounts = ActivityStatusKind.values().associateWith { status -> logs.count(status::matches) }

    return ActivityFilterCounts(
        clientCounts = clientCounts,
        endpointCounts = endpointCounts,
        routeCounts = routeCounts,
        statusCounts = statusCounts
    )
}

@Composable
internal fun rememberActivityFilterCounts(logs: List<ActivityLog>): ActivityFilterCounts {
    return produceState(initialValue = remember(logs.isEmpty()) { calculateFilterCounts(logs) }, key1 = logs) {
        if (logs.isNotEmpty()) {
            delay(80)
        }
        val result = withContext(Dispatchers.Default) {
            calculateFilterCounts(logs)
        }
        value = result
    }.value
}

@Composable
internal fun rememberActivityDisplayedLogs(
    logs: List<ActivityLog>,
    normalizedQuery: String,
    activityFilter: ActivityLogFilter,
    s: Strings
): List<ActivityLog> = remember(logs, normalizedQuery, activityFilter, s) {
    filterActivityLogs(logs, normalizedQuery, activityFilter) { log ->
        buildList {
            add(if (log.isOfficialPassthrough) s.activityPassthrough else s.activityRouted)
            if (log.retryCount > 0) add(s.activityRetryBadge(log.retryCount))
            add(activityClientLabel(log.clientKind(), s))
        }
    }
}

internal data class ActivityStatistics(
    val failedCount: Int,
    val averageDuration: Long,
    val averageFirstTokenMs: Long,
    val averageTps: Double? = null,
    val overallCacheHitRate: Double?
)

data class ModelLatencyStat(
    val modelId: String,
    val displayName: String? = null,
    val sampleCount: Int,
    val p50FirstByteMs: Long = 0L,
    val p95FirstByteMs: Long = 0L,
    val averageFirstTokenMs: Long,
    val minFirstTokenMs: Long,
    val maxFirstTokenMs: Long,
    val p50FirstTokenMs: Long = 0L,
    val p95FirstTokenMs: Long = 0L,
    val averageDurationMs: Long,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val p50DurationMs: Long = 0L,
    val p95DurationMs: Long = 0L,
    val averageTps: Double? = null,
    val minTps: Double? = null,
    val maxTps: Double? = null,
    val p50Tps: Double? = null,
    val averageTpotMs: Double? = null,
    val p95MaxChunkGapMs: Long = 0L,
    val averageQueueWaitMs: Long? = null,
    val totalStallCount: Int = 0,
    val completedCount: Int,
    val totalRequests: Int
)

@Composable
internal fun rememberActivityStatistics(logs: List<ActivityLog>): ActivityStatistics {
    return produceState(initialValue = remember(logs.isEmpty()) { calculateActivityStatistics(logs) }, key1 = logs) {
        if (logs.isNotEmpty()) {
            delay(80)
        }
        val result = withContext(Dispatchers.Default) {
            calculateActivityStatistics(logs)
        }
        value = result
    }.value
}

internal fun calculateActivityStatistics(logs: List<ActivityLog>): ActivityStatistics {
    val failedCount = logs.count { !it.isPending && it.statusCode >= 400 }
    val successfulLogs = logs.filter { !it.isPending && it.statusCode in 200..399 }
    val averageDuration = successfulLogs
        .takeIf { it.isNotEmpty() }
        ?.map { it.durationMs }
        ?.average()
        ?.toLong() ?: 0L
    val averageFirstTokenMs = successfulLogs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toLong() ?: 0L
    val averageTps = successfulLogs.mapNotNull {
        it.tokensPerSecond?.takeIf { tps ->
            tps > 0.0 && tps <= com.yuzhiqiang.antigravity.domain.model.MAX_REASONABLE_TPS
        }
    }
        .takeIf { it.isNotEmpty() }
        ?.average()
    val cacheUsageSamples = logs.mapNotNull { log ->
        val input = log.inputTokens?.takeIf { it >= 0L } ?: return@mapNotNull null
        val cacheRead = log.cacheReadTokens?.takeIf { it >= 0L } ?: return@mapNotNull null
        if (log.cacheWriteTokens != null && log.cacheWriteTokens < 0L) return@mapNotNull null
        Triple(cacheRead, input, log.cacheWriteTokens ?: 0L)
    }
    val overallCacheHitRate = calculateCacheHitRate(
        cacheReadTokens = cacheUsageSamples.sumOf { it.first },
        uncachedInputTokens = cacheUsageSamples.sumOf { it.second },
        cacheWriteTokens = cacheUsageSamples.sumOf { it.third }
    )

    return ActivityStatistics(
        failedCount = failedCount,
        averageDuration = averageDuration,
        averageFirstTokenMs = averageFirstTokenMs,
        averageTps = averageTps,
        overallCacheHitRate = overallCacheHitRate
    )
}

@Composable
internal fun rememberModelLatencyStats(logs: List<ActivityLog>): List<ModelLatencyStat> {
    return produceState(initialValue = remember(logs.isEmpty()) { calculateModelLatencyStats(logs) }, key1 = logs) {
        if (logs.isNotEmpty()) {
            delay(80)
        }
        val result = withContext(Dispatchers.Default) {
            calculateModelLatencyStats(logs)
        }
        value = result
    }.value
}

internal fun calculateModelLatencyStats(logs: List<ActivityLog>): List<ModelLatencyStat> {
    return logs
        .mapNotNull { log ->
            val identity = log.modelIdentity ?: return@mapNotNull null
            val modelId = identity.primaryModelId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val displayName = identity.displayName?.takeIf { it.isNotBlank() }
            Triple(identity.groupingKey, modelId to displayName, log)
        }
        .groupBy({ it.first }, { it.second to it.third })
        .map { (_, groupedLogs) ->
            val (modelId, resolvedDisplayName) = groupedLogs.first().first
            val modelLogs = groupedLogs.map { it.second }
            val successfulLogs = modelLogs.filter { !it.isPending && it.statusCode in 200..399 }
            val firstByteLogs = successfulLogs.mapNotNull { it.firstByteMs?.takeIf { ms -> ms > 0L } }
            val firstTokenLogs = successfulLogs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }
            val avgFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.average().toLong() else 0L
            val minFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.minOrNull() ?: 0L else 0L
            val maxFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.maxOrNull() ?: 0L else 0L
            val p50FirstToken = calculatePercentile(firstTokenLogs, 50.0)
            val p95FirstToken = calculatePercentile(firstTokenLogs, 95.0)

            val completedLogs = successfulLogs.filter { it.durationMs > 0 }
            val durationLogs = completedLogs.map { it.durationMs }
            val avgDuration = if (durationLogs.isNotEmpty()) durationLogs.average().toLong() else 0L
            val minDuration = durationLogs.minOrNull() ?: 0L
            val maxDuration = durationLogs.maxOrNull() ?: 0L
            val p50Duration = calculatePercentile(durationLogs, 50.0)
            val p95Duration = calculatePercentile(durationLogs, 95.0)

            val tpsList = successfulLogs.mapNotNull {
                it.tokensPerSecond?.takeIf { tps ->
                    tps > 0.0 && tps <= com.yuzhiqiang.antigravity.domain.model.MAX_REASONABLE_TPS
                }
            }
            val avgTps = if (tpsList.isNotEmpty()) tpsList.average() else null
            val minTps = tpsList.minOrNull()
            val maxTps = tpsList.maxOrNull()
            val p50Tps = calculateDoublePercentile(tpsList, 50.0)

            val tpotList = successfulLogs.mapNotNull { it.timePerOutputTokenMs?.takeIf { tpot -> tpot > 0.0 } }
            val avgTpot = if (tpotList.isNotEmpty()) tpotList.average() else null

            val gapList = successfulLogs.mapNotNull { it.maxChunkGapMs?.takeIf { gap -> gap > 0 } }
            val p95MaxGap = calculatePercentile(gapList, 95.0)

            val queueWaitList = successfulLogs.mapNotNull { it.queueWaitMs }
            val avgQueueWait = if (queueWaitList.isNotEmpty()) queueWaitList.average().toLong() else null

            val totalStalls = successfulLogs.sumOf { it.stallCount }

            ModelLatencyStat(
                modelId = modelId,
                displayName = resolvedDisplayName,
                sampleCount = firstTokenLogs.size,
                p50FirstByteMs = calculatePercentile(firstByteLogs, 50.0),
                p95FirstByteMs = calculatePercentile(firstByteLogs, 95.0),
                averageFirstTokenMs = avgFirstToken,
                minFirstTokenMs = minFirstToken,
                maxFirstTokenMs = maxFirstToken,
                p50FirstTokenMs = p50FirstToken,
                p95FirstTokenMs = p95FirstToken,
                averageDurationMs = avgDuration,
                minDurationMs = minDuration,
                maxDurationMs = maxDuration,
                p50DurationMs = p50Duration,
                p95DurationMs = p95Duration,
                averageTps = avgTps,
                minTps = minTps,
                maxTps = maxTps,
                p50Tps = p50Tps,
                averageTpotMs = avgTpot,
                p95MaxChunkGapMs = p95MaxGap,
                averageQueueWaitMs = avgQueueWait,
                totalStallCount = totalStalls,
                completedCount = completedLogs.size,
                totalRequests = modelLogs.size
            )
        }
        .sortedWith(
            compareByDescending<ModelLatencyStat> { it.sampleCount > 0 || it.completedCount > 0 }
                .thenByDescending { it.totalRequests }
                .thenBy { if (it.averageFirstTokenMs > 0) it.averageFirstTokenMs else it.averageDurationMs }
        )
}

internal fun calculatePercentile(values: List<Long>, percentile: Double): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    val index = (kotlin.math.ceil((percentile / 100.0) * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
    return sorted[index]
}

internal fun calculateDoublePercentile(values: List<Double>, percentile: Double): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val index = (kotlin.math.ceil((percentile / 100.0) * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
    return sorted[index]
}
