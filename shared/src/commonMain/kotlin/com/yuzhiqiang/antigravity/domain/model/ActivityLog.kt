package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityLog(
    @SerialName("id")
    val id: String,
    @SerialName("timestamp")
    val timestamp: Long = 0L,
    @SerialName("method")
    val method: String,
    @SerialName("path")
    val path: String,
    @SerialName("model_id")
    val modelId: String? = null,
    /** 请求最初选择的模型 ID（宿主原始请求值）。 */
    @SerialName("requested_model_id")
    val requestedModelId: String? = null,
    @SerialName("client_source")
    val clientSource: String? = null,
    @SerialName("provider_name")
    val providerName: String? = null,
    @SerialName("status_code")
    val statusCode: Int = 200,
    @SerialName("duration_ms")
    val durationMs: Long = 0L,
    @SerialName("is_official_passthrough")
    val isOfficialPassthrough: Boolean = false,
    @SerialName("is_pending")
    val isPending: Boolean = false,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("error_source")
    val errorSource: String? = null,
    @SerialName("input_tokens")
    val inputTokens: Long? = null,
    @SerialName("output_tokens")
    val outputTokens: Long? = null,
    @SerialName("cache_read_tokens")
    val cacheReadTokens: Long? = null,
    @SerialName("cache_write_tokens")
    val cacheWriteTokens: Long? = null,
    @SerialName("reasoning_tokens")
    val reasoningTokens: Long? = null,
    @SerialName("total_tokens")
    val totalTokens: Long? = null,
    /** 从请求进入 Studio 到收到首个上游响应的耗时；流式为首批字节，非流式为响应头就绪。 */
    @SerialName("first_byte_ms")
    val firstByteMs: Long? = null,
    @SerialName("first_token_ms")
    val firstTokenMs: Long? = null,
    /** 从请求进入 Studio 到最后一个有效内容事件写出的耗时。 */
    @SerialName("last_token_ms")
    val lastTokenMs: Long? = null,
    @SerialName("generation_duration_ms")
    val generationDurationMs: Long? = null,
    @SerialName("tokens_per_second")
    val tokensPerSecond: Double? = null,
    @SerialName("time_per_output_token_ms")
    val timePerOutputTokenMs: Double? = null,
    @SerialName("max_chunk_gap_ms")
    val maxChunkGapMs: Long? = null,
    @SerialName("stall_count")
    val stallCount: Int = 0,
    /** 所有达到卡顿阈值的完整内容间隔之和。 */
    @SerialName("stall_duration_ms")
    val stallDurationMs: Long? = null,
    @SerialName("queue_wait_ms")
    val queueWaitMs: Long? = null,
    @SerialName("retry_count")
    val retryCount: Int = 0,
    @SerialName("request_headers")
    val requestHeaders: Map<String, String>? = null,
    @SerialName("request_body")
    val requestBody: String? = null,
    @SerialName("response_headers")
    val responseHeaders: Map<String, String>? = null,
    @SerialName("response_body")
    val responseBody: String? = null
)

internal const val MIN_STREAM_GENERATION_DURATION_MS = 100L
internal const val MAX_REASONABLE_TPS = 2_000.0

internal data class SpeedMetrics(
    val generationDurationMs: Long?,
    val tokensPerSecond: Double?,
    val timePerOutputTokenMs: Double?
)

internal fun calculateSpeedMetrics(
    outputTokens: Long?,
    firstTokenMs: Long?,
    lastTokenMs: Long?,
    durationMs: Long? = null
): SpeedMetrics {
    if (outputTokens == null || outputTokens <= 0L) {
        val rawGenMs = when {
            firstTokenMs != null && lastTokenMs != null && lastTokenMs >= firstTokenMs -> lastTokenMs - firstTokenMs
            firstTokenMs != null && durationMs != null && durationMs >= firstTokenMs -> durationMs - firstTokenMs
            durationMs != null && durationMs > 0L -> durationMs
            else -> null
        }
        return SpeedMetrics(rawGenMs, null, null)
    }

    // 1. 优先使用实际流式末字与首字跨度（必须满足持续吐字跨度 >= 100ms）
    // 2. 其次使用首字到请求完成跨度（>= 100ms）
    // 3. 突发交付/单包返回或生成跨度极短时，降级采用总响应耗时 durationMs（避免 1ms 毫秒除法放大产生几十万 TPS 脏数据）
    val rawGenMs = when {
        firstTokenMs != null && lastTokenMs != null && (lastTokenMs - firstTokenMs) >= MIN_STREAM_GENERATION_DURATION_MS -> {
            lastTokenMs - firstTokenMs
        }
        firstTokenMs != null && durationMs != null && (durationMs - firstTokenMs) >= MIN_STREAM_GENERATION_DURATION_MS -> {
            durationMs - firstTokenMs
        }
        firstTokenMs != null && lastTokenMs != null && lastTokenMs == firstTokenMs && (durationMs == null || firstTokenMs >= durationMs) -> {
            0L
        }
        durationMs != null && durationMs >= MIN_STREAM_GENERATION_DURATION_MS -> {
            durationMs
        }
        durationMs != null && durationMs > 0L -> {
            durationMs
        }
        firstTokenMs != null && lastTokenMs != null && lastTokenMs > firstTokenMs -> {
            lastTokenMs - firstTokenMs
        }
        else -> null
    }

    if (rawGenMs == null || rawGenMs <= 0L) {
        return SpeedMetrics(rawGenMs, null, null)
    }

    val seconds = rawGenMs / 1000.0
    val rawTps = outputTokens.toDouble() / seconds

    // 针对极限突发场景做合理性上限兜底（防止毫秒级时钟抖动溢出）
    val finalTps = if (rawTps > MAX_REASONABLE_TPS) {
        if (durationMs != null && durationMs >= MIN_STREAM_GENERATION_DURATION_MS) {
            val fallbackTps = outputTokens.toDouble() / (durationMs / 1000.0)
            if (fallbackTps <= MAX_REASONABLE_TPS) fallbackTps else null
        } else {
            null
        }
    } else {
        rawTps
    }

    val finalGenMs = if (finalTps != null && finalTps != rawTps && durationMs != null) {
        durationMs
    } else {
        rawGenMs
    }

    val tpot = if (finalGenMs > 0L && outputTokens > 0L) {
        finalGenMs.toDouble() / maxOf(1L, if (outputTokens > 1L) outputTokens - 1 else 1L).toDouble()
    } else {
        null
    }

    return SpeedMetrics(
        generationDurationMs = finalGenMs,
        tokensPerSecond = finalTps,
        timePerOutputTokenMs = tpot
    )
}
