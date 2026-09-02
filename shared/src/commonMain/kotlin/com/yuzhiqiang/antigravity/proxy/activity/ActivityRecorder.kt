package com.yuzhiqiang.antigravity.proxy.activity

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.domain.model.ActivityModelIdentity
import com.yuzhiqiang.antigravity.domain.model.calculateSpeedMetrics
import com.yuzhiqiang.antigravity.domain.model.withResponseModelId
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ActivityRecorder {
    private const val MAX_LOGS = 2000
    private val _logs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val logs: StateFlow<List<ActivityLog>> = _logs.asStateFlow()

    private const val MAX_PAYLOAD_CHARS = 1_000_000 // 约 1MB 字符限制，避免大 Payload 导致内存暴涨
    private const val IGNORED_LOG_ID = "__ignored_probe__"

    internal fun isIgnoredProbe(method: String, path: String): Boolean {
        val trimmed = path.trim().lowercase()
        val isProbePath = trimmed == "/" || trimmed.isEmpty() || trimmed == "/health" || trimmed == "/healthz"
        return isProbePath && (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true))
    }

    /**
     * 请求刚到达时立即调用，生成一条处于 isPending = true 状态的日志并推送到列表头部
     */
    fun startActivity(
        method: String,
        path: String,
        modelIdentity: ActivityModelIdentity? = null,
        clientSource: String? = null,
        providerName: String?,
        isOfficialPassthrough: Boolean,
        timestamp: Long = System.currentTimeMillis(),
        queueWaitMs: Long? = null,
        requestHeaders: Map<String, String>? = null,
        requestBody: String? = null
    ): String {
        if (isIgnoredProbe(method, path)) {
            return IGNORED_LOG_ID
        }
        val id = UUID.randomUUID().toString()
        val newLog = ActivityLog(
            id = id,
            timestamp = timestamp,
            method = method,
            path = path,
            modelIdentity = modelIdentity,
            clientSource = clientSource,
            providerName = providerName,
            statusCode = 0,
            durationMs = 0L,
            isOfficialPassthrough = isOfficialPassthrough,
            isPending = true,
            queueWaitMs = queueWaitMs?.coerceAtLeast(0L),
            requestHeaders = requestHeaders,
            requestBody = sanitizePayload(requestBody)
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
        return id
    }

    /**
     * 流式产生首字时，原位更新首字耗时
     */
    fun updateFirstToken(id: String, firstTokenMs: Long) {
        if (id == IGNORED_LOG_ID) return
        _logs.update { current ->
            current.map { log ->
                if (log.id == id) log.copy(firstTokenMs = firstTokenMs) else log
            }
        }
    }

    /**
     * 触发重试时，原位更新该条请求的已重试次数
     */
    fun updateRetryCount(id: String, retryCount: Int) {
        if (id == IGNORED_LOG_ID) return
        _logs.update { current ->
            current.map { log ->
                if (log.id == id) log.copy(retryCount = retryCount) else log
            }
        }
    }

    /**
     * 请求完成或异常中断时，原位更新该条日志为完成态
     */
    fun finishActivity(
        id: String,
        statusCode: Int,
        durationMs: Long,
        modelIdentity: ActivityModelIdentity? = null,
        responseModelId: String? = null,
        providerName: String? = null,
        errorMessage: String? = null,
        errorSource: String? = null,
        usage: NeutralUsage? = null,
        firstByteMs: Long? = null,
        firstTokenMs: Long? = null,
        lastTokenMs: Long? = null,
        maxChunkGapMs: Long? = null,
        stallCount: Int? = null,
        stallDurationMs: Long? = null,
        retryCount: Int = 0,
        responseHeaders: Map<String, String>? = null,
        responseBody: String? = null
    ) {
        if (id == IGNORED_LOG_ID) return
        _logs.update { current ->
            current.map { log ->
                if (log.id == id) {
                    val resolvedFirstTokenMs = firstTokenMs ?: log.firstTokenMs
                    val resolvedLastTokenMs = lastTokenMs ?: log.lastTokenMs
                    val resolvedOutputTokens = usage?.outputTokens ?: log.outputTokens
                    val speedMetrics = calculateSpeedMetrics(
                        outputTokens = resolvedOutputTokens,
                        firstTokenMs = resolvedFirstTokenMs,
                        lastTokenMs = resolvedLastTokenMs,
                        durationMs = durationMs
                    )
                    log.copy(
                        statusCode = statusCode,
                        durationMs = durationMs,
                        modelIdentity = (modelIdentity ?: log.modelIdentity)
                            ?.withResponseModelId(responseModelId),
                        providerName = providerName ?: log.providerName,
                        errorMessage = errorMessage,
                        errorSource = errorSource,
                        inputTokens = usage?.inputTokens ?: log.inputTokens,
                        outputTokens = resolvedOutputTokens,
                        cacheReadTokens = usage?.cacheReadTokens ?: log.cacheReadTokens,
                        cacheWriteTokens = usage?.cacheWriteTokens ?: log.cacheWriteTokens,
                        reasoningTokens = usage?.reasoningTokens ?: log.reasoningTokens,
                        unattributedTokens = usage?.unattributedTokens ?: log.unattributedTokens,
                        totalTokens = usage?.totalTokens ?: log.totalTokens,
                        firstByteMs = firstByteMs ?: log.firstByteMs,
                        firstTokenMs = resolvedFirstTokenMs,
                        lastTokenMs = resolvedLastTokenMs,
                        generationDurationMs = speedMetrics.generationDurationMs,
                        tokensPerSecond = speedMetrics.tokensPerSecond,
                        timePerOutputTokenMs = speedMetrics.timePerOutputTokenMs,
                        maxChunkGapMs = maxChunkGapMs ?: log.maxChunkGapMs,
                        stallCount = (stallCount ?: log.stallCount).coerceAtLeast(0),
                        stallDurationMs = (stallDurationMs ?: log.stallDurationMs)?.coerceAtLeast(0L),
                        retryCount = if (retryCount > 0) retryCount else log.retryCount,
                        responseHeaders = responseHeaders ?: log.responseHeaders,
                        responseBody = sanitizePayload(responseBody) ?: log.responseBody,
                        isPending = false
                    )
                } else {
                    log
                }
            }
        }
    }

    fun record(
        method: String,
        path: String,
        modelIdentity: ActivityModelIdentity? = null,
        clientSource: String? = null,
        providerName: String?,
        statusCode: Int,
        durationMs: Long,
        isOfficialPassthrough: Boolean,
        timestamp: Long = System.currentTimeMillis(),
        errorMessage: String? = null,
        errorSource: String? = null,
        usage: NeutralUsage? = null,
        firstByteMs: Long? = null,
        firstTokenMs: Long? = null,
        lastTokenMs: Long? = null,
        maxChunkGapMs: Long? = null,
        stallCount: Int = 0,
        stallDurationMs: Long? = null,
        queueWaitMs: Long? = null,
        retryCount: Int = 0,
        requestHeaders: Map<String, String>? = null,
        requestBody: String? = null,
        responseHeaders: Map<String, String>? = null,
        responseBody: String? = null
    ) {
        if (isIgnoredProbe(method, path)) return
        val speedMetrics = calculateSpeedMetrics(
            outputTokens = usage?.outputTokens,
            firstTokenMs = firstTokenMs,
            lastTokenMs = lastTokenMs,
            durationMs = durationMs
        )
        val newLog = ActivityLog(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            method = method,
            path = path,
            modelIdentity = modelIdentity,
            clientSource = clientSource,
            providerName = providerName,
            statusCode = statusCode,
            durationMs = durationMs,
            isOfficialPassthrough = isOfficialPassthrough,
            isPending = false,
            errorMessage = errorMessage,
            errorSource = errorSource,
            inputTokens = usage?.inputTokens,
            outputTokens = usage?.outputTokens,
            cacheReadTokens = usage?.cacheReadTokens,
            cacheWriteTokens = usage?.cacheWriteTokens,
            reasoningTokens = usage?.reasoningTokens,
            unattributedTokens = usage?.unattributedTokens,
            totalTokens = usage?.totalTokens,
            firstByteMs = firstByteMs,
            firstTokenMs = firstTokenMs,
            lastTokenMs = lastTokenMs,
            generationDurationMs = speedMetrics.generationDurationMs,
            tokensPerSecond = speedMetrics.tokensPerSecond,
            timePerOutputTokenMs = speedMetrics.timePerOutputTokenMs,
            maxChunkGapMs = maxChunkGapMs,
            stallCount = stallCount.coerceAtLeast(0),
            stallDurationMs = stallDurationMs?.coerceAtLeast(0L),
            queueWaitMs = queueWaitMs?.coerceAtLeast(0L),
            retryCount = retryCount,
            requestHeaders = requestHeaders,
            requestBody = sanitizePayload(requestBody),
            responseHeaders = responseHeaders,
            responseBody = sanitizePayload(responseBody)
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


    private fun sanitizePayload(payload: String?): String? {
        if (payload == null) return null
        if (payload.length <= MAX_PAYLOAD_CHARS) return payload
        return payload.substring(0, MAX_PAYLOAD_CHARS) +
                "\n\n... [Truncated: ${payload.length - MAX_PAYLOAD_CHARS} chars omitted for performance]"
    }
}
