package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityLog(
    @SerialName("id") val id: String,
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("method") val method: String,
    @SerialName("path") val path: String,
    @SerialName("model_identity") val modelIdentity: ActivityModelIdentity? = null,
    @SerialName("client_source") val clientSource: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("status_code") val statusCode: Int = 200,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("is_official_passthrough") val isOfficialPassthrough: Boolean = false,
    @SerialName("is_pending") val isPending: Boolean = false,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("error_source") val errorSource: String? = null,
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long? = null,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Long? = null,
    @SerialName("reasoning_tokens") val reasoningTokens: Long? = null,
    @SerialName("unattributed_tokens") val unattributedTokens: Long? = null,
    @SerialName("total_tokens") val totalTokens: Long? = null,
    @SerialName("first_byte_ms") val firstByteMs: Long? = null,
    @SerialName("first_token_ms") val firstTokenMs: Long? = null,
    @SerialName("last_token_ms") val lastTokenMs: Long? = null,
    @SerialName("generation_duration_ms") val generationDurationMs: Long? = null,
    @SerialName("tokens_per_second") val tokensPerSecond: Double? = null,
    @SerialName("time_per_output_token_ms") val timePerOutputTokenMs: Double? = null,
    @SerialName("max_chunk_gap_ms") val maxChunkGapMs: Long? = null,
    @SerialName("stall_count") val stallCount: Int = 0,
    @SerialName("stall_duration_ms") val stallDurationMs: Long? = null,
    @SerialName("queue_wait_ms") val queueWaitMs: Long? = null,
    @SerialName("retry_count") val retryCount: Int = 0,
    @SerialName("request_headers") val requestHeaders: Map<String, String>? = null,
    @SerialName("request_body") val requestBody: String? = null,
    @SerialName("response_headers") val responseHeaders: Map<String, String>? = null,
    @SerialName("response_body") val responseBody: String? = null
)
