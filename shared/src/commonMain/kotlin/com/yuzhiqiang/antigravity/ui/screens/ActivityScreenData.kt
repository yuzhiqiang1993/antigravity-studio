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
    val averageFirstTokenMs: Long,
    val minFirstTokenMs: Long,
    val maxFirstTokenMs: Long,
    val averageDurationMs: Long,
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
            val firstTokenLogs = modelLogs.mapNotNull { it.firstTokenMs?.takeIf { ms -> ms > 0 } }
            val avgFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.average().toLong() else 0L
            val minFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.minOrNull() ?: 0L else 0L
            val maxFirstToken = if (firstTokenLogs.isNotEmpty()) firstTokenLogs.maxOrNull() ?: 0L else 0L
            val completedLogs = modelLogs.filter { !it.isPending && it.durationMs > 0 }
            val avgDuration = if (completedLogs.isNotEmpty()) completedLogs.map { it.durationMs }.average().toLong() else 0L

            ModelLatencyStat(
                modelId = modelId,
                sampleCount = firstTokenLogs.size,
                averageFirstTokenMs = avgFirstToken,
                minFirstTokenMs = minFirstToken,
                maxFirstTokenMs = maxFirstToken,
                averageDurationMs = avgDuration,
                totalRequests = modelLogs.size
            )
        }
        .sortedWith(
            compareByDescending<ModelLatencyStat> { it.sampleCount > 0 }
                .thenByDescending { it.sampleCount }
                .thenBy { it.averageFirstTokenMs }
        )
}

