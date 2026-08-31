package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate

internal data class ActivityFilterCounts(
    val clientCounts: Map<ActivityClientKind, Int>,
    val endpointCounts: List<Pair<String, Int>>,
    val routeCounts: List<Pair<String, Int>>,
    val statusCounts: Map<ActivityStatusKind, Int>
)

@Composable
internal fun rememberActivityFilterCounts(logs: List<ActivityLog>): ActivityFilterCounts {
    val clientCounts = remember(logs) {
        ActivityClientKind.values().associateWith { kind -> logs.count { it.clientKind() == kind } }
    }
    val endpointCounts = remember(logs) {
        logs.groupingBy(ActivityLog::path)
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }
    val routeCounts = remember(logs) {
        logs.groupingBy(ActivityLog::routeKey)
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }
    val statusCounts = remember(logs) {
        ActivityStatusKind.values().associateWith { status -> logs.count(status::matches) }
    }

    return ActivityFilterCounts(
        clientCounts = clientCounts,
        endpointCounts = endpointCounts,
        routeCounts = routeCounts,
        statusCounts = statusCounts
    )
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
    val overallCacheHitRate: Double?
)

data class ModelLatencyStat(
    val modelId: String,
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
    val failedCount = remember(logs) { logs.count { !it.isPending && it.statusCode >= 400 } }
    val averageDuration = remember(logs) {
        logs.filter { !it.isPending && it.statusCode > 0 }
            .takeIf { it.isNotEmpty() }
            ?.map { it.durationMs }
            ?.average()
            ?.toLong() ?: 0L
    }
    val averageFirstTokenMs = remember(logs) {
        logs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toLong() ?: 0L
    }
    val totalInputTokens = remember(logs) { logs.mapNotNull { it.inputTokens }.sum() }
    val totalCacheReadTokens = remember(logs) { logs.mapNotNull { it.cacheReadTokens }.sum() }
    val totalCacheWriteTokens = remember(logs) { logs.mapNotNull { it.cacheWriteTokens }.sum() }
    val overallCacheHitRate = remember(totalInputTokens, totalCacheReadTokens, totalCacheWriteTokens) {
        calculateCacheHitRate(totalCacheReadTokens, totalInputTokens, totalCacheWriteTokens)
    }

    return ActivityStatistics(
        failedCount = failedCount,
        averageDuration = averageDuration,
        averageFirstTokenMs = averageFirstTokenMs,
        overallCacheHitRate = overallCacheHitRate
    )
}

@Composable
internal fun rememberModelLatencyStats(logs: List<ActivityLog>): List<ModelLatencyStat> = remember(logs) {
    calculateModelLatencyStats(logs)
}

internal fun calculateModelLatencyStats(logs: List<ActivityLog>): List<ModelLatencyStat> {
    return logs
        .mapNotNull { log ->
            val model = (log.modelId ?: log.requestedModelId)?.takeIf { it.isNotBlank() }
            if (model != null) model to log else null
        }
        .groupBy({ it.first }, { it.second })
        .map { (modelId, modelLogs) ->
            val firstByteLogs = modelLogs.mapNotNull { it.firstByteMs?.takeIf { ms -> ms > 0L } }
            val firstTokenLogs = modelLogs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }
            val avgFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.average().toLong() else 0L
            val minFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.minOrNull() ?: 0L else 0L
            val maxFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.maxOrNull() ?: 0L else 0L
            val p50FirstToken = calculatePercentile(firstTokenLogs, 50.0)
            val p95FirstToken = calculatePercentile(firstTokenLogs, 95.0)

            val completedLogs = modelLogs.filter { !it.isPending && it.durationMs > 0 }
            val durationLogs = completedLogs.map { it.durationMs }
            val avgDuration = if (durationLogs.isNotEmpty()) durationLogs.average().toLong() else 0L
            val minDuration = durationLogs.minOrNull() ?: 0L
            val maxDuration = durationLogs.maxOrNull() ?: 0L
            val p50Duration = calculatePercentile(durationLogs, 50.0)
            val p95Duration = calculatePercentile(durationLogs, 95.0)

            val tpsList = modelLogs.mapNotNull { it.tokensPerSecond?.takeIf { tps -> tps > 0.0 } }
            val avgTps = if (tpsList.isNotEmpty()) tpsList.average() else null
            val minTps = tpsList.minOrNull()
            val maxTps = tpsList.maxOrNull()
            val p50Tps = calculateDoublePercentile(tpsList, 50.0)

            val tpotList = modelLogs.mapNotNull { it.timePerOutputTokenMs?.takeIf { tpot -> tpot > 0.0 } }
            val avgTpot = if (tpotList.isNotEmpty()) tpotList.average() else null

            val gapList = modelLogs.mapNotNull { it.maxChunkGapMs?.takeIf { gap -> gap > 0 } }
            val p95MaxGap = calculatePercentile(gapList, 95.0)

            val queueWaitList = modelLogs.mapNotNull { it.queueWaitMs }
            val avgQueueWait = if (queueWaitList.isNotEmpty()) queueWaitList.average().toLong() else null

            val totalStalls = modelLogs.sumOf { it.stallCount }

            ModelLatencyStat(
                modelId = modelId,
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
