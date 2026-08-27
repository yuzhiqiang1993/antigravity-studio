package com.yuzhiqiang.antigravity.proxy.activity

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ActivityRecorder {
    private const val MAX_LOGS = 500
    private val _logs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val logs: StateFlow<List<ActivityLog>> = _logs.asStateFlow()

    /**
     * 请求刚到达时立即调用，生成一条处于 isPending = true 状态的日志并推送到列表头部
     */
    fun startActivity(
        method: String,
        path: String,
        modelId: String?,
        requestedModelId: String? = null,
        providerName: String?,
        isOfficialPassthrough: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val id = UUID.randomUUID().toString()
        val newLog = ActivityLog(
            id = id,
            timestamp = timestamp,
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = requestedModelId,
            providerName = providerName,
            statusCode = 0,
            durationMs = 0L,
            isOfficialPassthrough = isOfficialPassthrough,
            isPending = true
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
        modelId: String? = null,
        providerName: String? = null,
        errorMessage: String? = null,
        usage: NeutralUsage? = null,
        firstTokenMs: Long? = null,
        retryCount: Int = 0
    ) {
        _logs.update { current ->
            current.map { log ->
                if (log.id == id) {
                    log.copy(
                        statusCode = statusCode,
                        durationMs = durationMs,
                        modelId = modelId ?: log.modelId,
                        providerName = providerName ?: log.providerName,
                        errorMessage = errorMessage,
                        inputTokens = usage?.inputTokens ?: log.inputTokens,
                        outputTokens = usage?.outputTokens ?: log.outputTokens,
                        cacheReadTokens = usage?.cacheReadTokens ?: log.cacheReadTokens,
                        cacheWriteTokens = usage?.cacheWriteTokens ?: log.cacheWriteTokens,
                        reasoningTokens = usage?.reasoningTokens ?: log.reasoningTokens,
                        totalTokens = usage?.totalTokens ?: log.totalTokens,
                        firstTokenMs = firstTokenMs ?: log.firstTokenMs,
                        retryCount = if (retryCount > 0) retryCount else log.retryCount,
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
        modelId: String?,
        requestedModelId: String? = null,
        providerName: String?,
        statusCode: Int,
        durationMs: Long,
        isOfficialPassthrough: Boolean,
        errorMessage: String? = null,
        usage: NeutralUsage? = null,
        firstTokenMs: Long? = null,
        retryCount: Int = 0
    ) {
        val newLog = ActivityLog(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            method = method,
            path = path,
            modelId = modelId,
            requestedModelId = requestedModelId,
            providerName = providerName,
            statusCode = statusCode,
            durationMs = durationMs,
            isOfficialPassthrough = isOfficialPassthrough,
            isPending = false,
            errorMessage = errorMessage,
            inputTokens = usage?.inputTokens,
            outputTokens = usage?.outputTokens,
            cacheReadTokens = usage?.cacheReadTokens,
            cacheWriteTokens = usage?.cacheWriteTokens,
            reasoningTokens = usage?.reasoningTokens,
            totalTokens = usage?.totalTokens,
            firstTokenMs = firstTokenMs,
            retryCount = retryCount
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
}
