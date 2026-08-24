package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("protocol")
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
    @SerialName("base_url")
    val baseUrl: String = "",
    @SerialName("models_endpoint")
    val modelsEndpoint: String? = null,
    @SerialName("generate_endpoint")
    val generateEndpoint: String? = null,
    @SerialName("api_key")
    val apiKey: String? = null,
    @SerialName("headers")
    val headers: Map<String, String>? = null,
    @SerialName("header_overrides")
    val headerOverrides: Map<String, String>? = null,
    @SerialName("parameter_overrides")
    val parameterOverrides: ParameterOverrides? = null,
    @SerialName("default_parameters")
    val defaultParameters: ParameterOverrides? = null,
    @SerialName("connect_timeout_ms")
    val connectTimeoutMs: Long = 10_000L,
    @SerialName("request_timeout_ms")
    val requestTimeoutMs: Long = 600_000L,
    @SerialName("stream_idle_timeout_ms")
    val streamIdleTimeoutMs: Long = 600_000L,
    @SerialName("enabled")
    val enabled: Boolean = true
) {
    val effectiveBaseUrl: String
        get() = when {
            baseUrl.isNotBlank() -> baseUrl
            generateEndpoint != null -> generateEndpoint
                .substringBefore("{model}")
                .substringBefore("/chat/completions")
                .substringBefore("/responses")
                .substringBefore("/messages")
                .substringBefore("/models")

            else -> "http://127.0.0.1"
        }
}
