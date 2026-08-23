package com.yuzhiqiang.antigravity.proxy.activity

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ActivityRecorder {
    private const val MAX_LOGS = 200
    private val _logs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val logs: StateFlow<List<ActivityLog>> = _logs.asStateFlow()

    fun record(
        method: String,
        path: String,
        modelId: String?,
        requestedModelId: String? = null,
        providerName: String?,
        statusCode: Int,
        durationMs: Long,
        isOfficialPassthrough: Boolean,
        errorMessage: String? = null,
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false,
        usage: NeutralUsage? = null
    ) {
        val newLog = ActivityLog(
            id = UUID.randomUUID().toString(),
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = requestedModelId,
            providerName = providerName,
            statusCode = statusCode,
            durationMs = durationMs,
            isOfficialPassthrough = isOfficialPassthrough,
            errorMessage = errorMessage?.let(::sanitizeLogText),
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            inputTokens = usage?.inputTokens,
            outputTokens = usage?.outputTokens,
            cacheReadTokens = usage?.cacheReadTokens,
            cacheWriteTokens = usage?.cacheWriteTokens,
            reasoningTokens = usage?.reasoningTokens,
            totalTokens = usage?.totalTokens
        )
        _logs.update { current ->
            val next = ArrayList<ActivityLog>(minOf(current.size + 1, MAX_LOGS))
            next.add(newLog)
            val limit = minOf(current.size, MAX_LOGS - 1)
            for (i in 0 until limit) {
                next.add(current[i])
            }
            next
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun sanitizeLogText(value: String): String {
        var redactNext = false
        return value.split(Regex("\\s+")).map { token ->
            val comparable = token.trim { char -> !char.isLetterOrDigit() && char !in "-_=" }.lowercase()
            when {
                redactNext -> {
                    redactNext = false
                    "[REDACTED]"
                }
                comparable == "bearer" -> {
                    redactNext = true
                    "Bearer"
                }
                comparable.startsWith("sk-") ||
                        comparable.startsWith("api_key=") ||
                        comparable.startsWith("apikey=") ||
                        comparable.startsWith("authorization=") -> "[REDACTED]"
                else -> token
            }
        }.joinToString(" ").take(500)
    }
}
