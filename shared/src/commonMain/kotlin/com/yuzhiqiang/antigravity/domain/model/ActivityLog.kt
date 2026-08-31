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
    if (outputTokens == null || outputTokens <= 1L) {
        val rawGenMs = when {
            firstTokenMs != null && lastTokenMs != null && lastTokenMs >= firstTokenMs -> lastTokenMs - firstTokenMs
            firstTokenMs != null && durationMs != null && durationMs >= firstTokenMs -> durationMs - firstTokenMs
            else -> null
        }
        return SpeedMetrics(rawGenMs, null, null)
    }

    // 优先使用实际流式末字与首字差值，若无明确差值且有总耗时则使用 durationMs - firstTokenMs
    val rawGenMs = when {
        firstTokenMs != null && lastTokenMs != null && lastTokenMs > firstTokenMs -> lastTokenMs - firstTokenMs
        firstTokenMs != null && durationMs != null && durationMs > firstTokenMs -> durationMs - firstTokenMs
        durationMs != null && durationMs > 0L -> durationMs
        else -> null
    }

    if (rawGenMs == null || rawGenMs <= 0L) {
        return SpeedMetrics(rawGenMs, null, null)
    }
    val seconds = rawGenMs / 1000.0
    val tps = outputTokens.toDouble() / seconds
    val tpot = rawGenMs.toDouble() / maxOf(1L, outputTokens - 1).toDouble()
    return SpeedMetrics(
        generationDurationMs = rawGenMs,
        tokensPerSecond = tps,
        timePerOutputTokenMs = tpot
    )
}
