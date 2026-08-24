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
    /** 请求最初选择的模型；model_id 在 fallback 后记录实际成功路由。 */
    @SerialName("requested_model_id")
    val requestedModelId: String? = null,
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
    @SerialName("fallback_attempted")
    val fallbackAttempted: Boolean = false,
    @SerialName("fallback_succeeded")
    val fallbackSucceeded: Boolean = false,
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
    @SerialName("first_token_ms")
    val firstTokenMs: Long? = null
)
