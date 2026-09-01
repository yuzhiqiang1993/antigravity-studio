package com.yuzhiqiang.antigravity.ui.dialogs.activity

import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 格式化完整时间戳
 */
fun formatFullTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        "--"
    }
}

/**
 * 将 ActivityLog 转换为结构化 JSON 字符串
 */
fun ActivityLog.toJsonString(): String {
    val log = this
    return buildJsonObject {
        put("id", log.id)
        put("timestamp", log.timestamp)
        put("timeFormatted", formatFullTime(log.timestamp))
        put("method", log.method)
        put("path", log.path)
        put("statusCode", log.statusCode)
        put("durationMs", log.durationMs)
        put("isPending", log.isPending)
        if (log.retryCount > 0) put("retryCount", log.retryCount)
        log.queueWaitMs?.let { put("queueWaitMs", it) }
        log.firstByteMs?.let { put("firstByteMs", it) }
        log.firstTokenMs?.let { put("firstTokenMs", it) }
        log.lastTokenMs?.let { put("lastTokenMs", it) }
        log.generationDurationMs?.let { put("generationDurationMs", it) }
        log.tokensPerSecond?.let { put("tokensPerSecond", it) }
        log.timePerOutputTokenMs?.let { put("timePerOutputTokenMs", it) }
        log.maxChunkGapMs?.let { put("maxChunkGapMs", it) }
        if (log.stallCount > 0) put("stallCount", log.stallCount)
        log.stallDurationMs?.let { put("stallDurationMs", it) }
        put("clientSource", log.clientSource)
        put("isOfficialPassthrough", log.isOfficialPassthrough)
        log.modelIdentity?.let { put("modelIdentity", Json.encodeToJsonElement(it)) }
        put("providerName", log.providerName)
        put("inputTokens", log.inputTokens)
        put("outputTokens", log.outputTokens)
        put("cacheReadTokens", log.cacheReadTokens)
        put("cacheWriteTokens", log.cacheWriteTokens)
        put("reasoningTokens", log.reasoningTokens)
        put("totalTokens", log.totalTokens)
        put("errorMessage", log.errorMessage)
        put("errorSource", log.errorSource)
        log.requestHeaders?.let { headers ->
            put("requestHeaders", buildJsonObject {
                headers.forEach { (k, v) -> put(k, v) }
            })
        }
        log.requestBody?.let { put("requestBody", it) }
        log.responseHeaders?.let { headers ->
            put("responseHeaders", buildJsonObject {
                headers.forEach { (k, v) -> put(k, v) }
            })
        }
        log.responseBody?.let { put("responseBody", it) }
    }.toString()
}
