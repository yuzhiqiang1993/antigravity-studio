package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    @SerialName("proxy_port")
    val proxyPort: Int = 8321,
    @SerialName("providers")
    val providers: List<Provider> = emptyList(),
    @SerialName("upstream_models")
    val upstreamModels: List<UpstreamModel> = emptyList(),
    @SerialName("virtual_models")
    val virtualModels: List<VirtualModel> = emptyList(),
    @SerialName("model_compression_policies")
    val modelCompressionPolicies: Map<String, ModelCompressionPolicy> = emptyMap(),
    @SerialName("disabled_official_models")
    val disabledOfficialModels: List<String> = emptyList(),
    @SerialName("custom_host_paths")
    val customHostPaths: Map<String, String?> = emptyMap(),
    @SerialName("language")
    val language: String = "zh-CN",
    @SerialName("theme_mode")
    val themeMode: String = "system"
)
