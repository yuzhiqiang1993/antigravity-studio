package com.yuzhiqiang.antigravity.ui.screens

import com.yuzhiqiang.antigravity.domain.model.ActivityLog

internal const val OFFICIAL_ROUTE_KEY = "__official_passthrough__"
internal const val UNKNOWN_ROUTE_KEY = "__unknown_provider__"

internal enum class ActivityClientKind {
    IDE,
    CLI,
    APP,
    OTHER
}

internal enum class ActivityStatusKind {
    SUCCESS,
    FAILED,
    PENDING,
    RETRIED;

    fun matches(log: ActivityLog): Boolean = when (this) {
        SUCCESS -> !log.isPending && log.statusCode in 200..399
        FAILED -> !log.isPending && log.statusCode >= 400
        PENDING -> log.isPending
        RETRIED -> log.retryCount > 0
    }
}

internal data class ActivityLogFilter(
    val clients: Set<ActivityClientKind> = emptySet(),
    val endpoints: Set<String> = emptySet(),
    val routes: Set<String> = emptySet(),
    val statuses: Set<ActivityStatusKind> = emptySet()
) {
    val activeCount: Int
        get() = clients.size + endpoints.size + routes.size + statuses.size

    val isActive: Boolean
        get() = activeCount > 0
}

internal fun ActivityLog.clientKind(): ActivityClientKind {
    val normalized = clientSource.orEmpty().lowercase()
    val tokens = normalized.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotEmpty() }.toSet()
    return when {
        "ide" in tokens || "vscode" in tokens || "codeium" in tokens -> ActivityClientKind.IDE
        "cli" in tokens || "agy" in tokens || "terminal" in tokens -> ActivityClientKind.CLI
        "app" in tokens || "hub" in tokens || "desktop" in tokens || "electron" in tokens -> ActivityClientKind.APP
        else -> ActivityClientKind.OTHER
    }
}

internal fun ActivityLog.routeKey(): String = when {
    isOfficialPassthrough -> OFFICIAL_ROUTE_KEY
    providerName.isNullOrBlank() -> UNKNOWN_ROUTE_KEY
    else -> providerName.trim()
}

internal fun filterActivityLogs(
    logs: List<ActivityLog>,
    query: String,
    filter: ActivityLogFilter,
    additionalSearchTerms: (ActivityLog) -> List<String> = { emptyList() }
): List<ActivityLog> {
    val normalizedQuery = query.trim().lowercase()
    return logs.filter { log ->
        val matchesClient = filter.clients.isEmpty() || log.clientKind() in filter.clients
        val matchesEndpoint = filter.endpoints.isEmpty() || log.path in filter.endpoints
        val matchesRoute = filter.routes.isEmpty() || log.routeKey() in filter.routes
        val matchesStatus = filter.statuses.isEmpty() || filter.statuses.any { it.matches(log) }
        val matchesQuery = normalizedQuery.isEmpty() || buildList {
            add(log.method)
            add(log.path)
            add(log.statusCode.toString())
            listOfNotNull(
                log.clientSource,
                log.modelId,
                log.requestedModelId,
                log.providerName,
                log.errorMessage,
                log.errorSource
            ).forEach(::add)
            addAll(additionalSearchTerms(log))
        }.any { it.lowercase().contains(normalizedQuery) }

        matchesClient && matchesEndpoint && matchesRoute && matchesStatus && matchesQuery
    }
}

internal fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
