package com.yuzhiqiang.antigravity.ui.screens

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityLogFilterTest {

    private val logs = listOf(
        log(
            id = "ide-official",
            path = "/v1internal:listExperiments",
            client = "Antigravity IDE",
            provider = "Official Cloud Code",
            official = true,
            status = 200
        ),
        log(
            id = "cli-modelgate",
            path = "/v1internal:streamGenerateContent",
            client = "Antigravity CLI",
            provider = "ModelGate",
            status = 502,
            retries = 2,
            error = "upstream timeout"
        ),
        log(
            id = "app-modelgate",
            path = "/v1internal:streamGenerateContent",
            client = "Antigravity App",
            provider = "ModelGate",
            status = 200
        ),
        log(
            id = "plugin-cockpit",
            path = "/v1internal:fetchUserInfo",
            client = "Cockpit Plugin",
            provider = "Official Cloud Code",
            status = 200
        ),
        log(
            id = "other-pending",
            path = "/v1/models",
            client = "Custom Client",
            provider = null,
            status = 0,
            pending = true
        )
    )

    @Test
    fun filtersClientKindsWithinOneDimensionUsingOr() {
        val result = filterActivityLogs(
            logs = logs,
            query = "",
            filter = ActivityLogFilter(
                clients = setOf(ActivityClientKind.IDE, ActivityClientKind.CLI, ActivityClientKind.PLUGIN)
            )
        )

        assertEquals(listOf("ide-official", "cli-modelgate", "plugin-cockpit"), result.map { it.id })
    }


    @Test
    fun combinesEndpointRouteAndStatusDimensionsUsingAnd() {
        val result = filterActivityLogs(
            logs = logs,
            query = "",
            filter = ActivityLogFilter(
                endpoints = setOf("/v1internal:streamGenerateContent"),
                routes = setOf("ModelGate"),
                statuses = setOf(ActivityStatusKind.FAILED)
            )
        )

        assertEquals(listOf("cli-modelgate"), result.map { it.id })
    }

    @Test
    fun supportsPendingAndRetriedStatusFilters() {
        val retried = filterActivityLogs(
            logs,
            "",
            ActivityLogFilter(statuses = setOf(ActivityStatusKind.RETRIED))
        )
        val pending = filterActivityLogs(
            logs,
            "",
            ActivityLogFilter(statuses = setOf(ActivityStatusKind.PENDING))
        )

        assertEquals(listOf("cli-modelgate"), retried.map { it.id })
        assertEquals(listOf("other-pending"), pending.map { it.id })
    }

    @Test
    fun combinesGlobalSearchWithActiveFiltersUsingAnd() {
        val result = filterActivityLogs(
            logs = logs,
            query = "timeout",
            filter = ActivityLogFilter(clients = setOf(ActivityClientKind.APP))
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun combinesStatusesWithinOneDimensionUsingOr() {
        val result = filterActivityLogs(
            logs = logs,
            query = "",
            filter = ActivityLogFilter(
                statuses = setOf(ActivityStatusKind.FAILED, ActivityStatusKind.PENDING)
            )
        )

        assertEquals(listOf("cli-modelgate", "other-pending"), result.map { it.id })
    }

    @Test
    fun classifiesUnknownClientAsOther() {
        assertEquals(ActivityClientKind.OTHER, logs.last().clientKind())
    }

    @Test
    fun searchesBuiltInAndLocalizedTerms() {
        val byError = filterActivityLogs(logs, "timeout", ActivityLogFilter())
        val byLocalizedRoute = filterActivityLogs(logs, "官方直连", ActivityLogFilter()) { log ->
            if (log.isOfficialPassthrough) listOf("官方直连") else emptyList()
        }

        assertEquals(listOf("cli-modelgate"), byError.map { it.id })
        assertEquals(listOf("ide-official"), byLocalizedRoute.map { it.id })
    }

    private fun log(
        id: String,
        path: String,
        client: String,
        provider: String?,
        official: Boolean = false,
        status: Int,
        retries: Int = 0,
        pending: Boolean = false,
        error: String? = null
    ) = ActivityLog(
        id = id,
        method = "POST",
        path = path,
        modelId = "test-model",
        clientSource = client,
        providerName = provider,
        statusCode = status,
        durationMs = 100L,
        isOfficialPassthrough = official,
        isPending = pending,
        errorMessage = error,
        retryCount = retries
    )
}
