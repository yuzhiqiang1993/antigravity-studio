package com.yuzhiqiang.antigravity.domain.model

import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityLog(
    @SerialName("id")
    val id: String,
    @SerialName("timestamp")
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    @SerialName("method")
    val method: String,
    @SerialName("path")
    val path: String,
    @SerialName("model_id")
    val modelId: String? = null,
    @SerialName("provider_name")
    val providerName: String? = null,
    @SerialName("status_code")
    val statusCode: Int = 200,
    @SerialName("duration_ms")
    val durationMs: Long = 0L,
    @SerialName("is_official_passthrough")
    val isOfficialPassthrough: Boolean = false,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("fallback_attempted")
    val fallbackAttempted: Boolean = false,
    @SerialName("fallback_succeeded")
    val fallbackSucceeded: Boolean = false
)
