package com.yuzhiqiang.antigravity.proxy.activity

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ActivityRecorder {
    private const val MAX_LOGS = 200
    private val _logs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val logs: StateFlow<List<ActivityLog>> = _logs.asStateFlow()

    @Synchronized
    fun record(
        method: String,
        path: String,
        modelId: String?,
        providerName: String?,
        statusCode: Int,
        durationMs: Long,
        isOfficialPassthrough: Boolean,
        errorMessage: String? = null,
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false
    ) {
        val newLog = ActivityLog(
            id = UUID.randomUUID().toString(),
            method = method,
            path = path,
            modelId = modelId,
            providerName = providerName,
            statusCode = statusCode,
            durationMs = durationMs,
            isOfficialPassthrough = isOfficialPassthrough,
            errorMessage = errorMessage,
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded
        )
        val current = _logs.value.toMutableList()
        current.add(0, newLog)
        if (current.size > MAX_LOGS) {
            _logs.value = current.take(MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
