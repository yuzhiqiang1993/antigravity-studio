package com.yuzhiqiang.antigravity.domain.model

import com.yuzhiqiang.antigravity.domain.model.account.SmartSwitchConfig

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
    @SerialName("smart_switch_config")
    val smartSwitchConfig: SmartSwitchConfig = SmartSwitchConfig(),
    @SerialName("language")
    val language: String = "zh-CN",
    @SerialName("theme_mode")
    val themeMode: String = "system",
    @SerialName("theme_palette")
    val themePalette: String = "indigo",
    @SerialName("auto_check_update")
    val autoCheckUpdate: Boolean = true,
    @SerialName("include_prerelease")
    val includePrerelease: Boolean = false,
    @SerialName("ignored_version")
    val ignoredVersion: String? = null,
    @SerialName("last_check_update_timestamp")
    val lastCheckUpdateTimestamp: Long = 0L,
    @SerialName("developer_mode")
    val developerMode: Boolean = false,
    @SerialName("activity_auto_scroll")
    val activityAutoScroll: Boolean = true,
    @SerialName("quota_auto_refresh_enabled")
    val quotaAutoRefreshEnabled: Boolean = true,
    @SerialName("quota_active_interval_seconds")
    val quotaActiveIntervalSeconds: Int = 60,
    @SerialName("quota_background_interval_seconds")
    val quotaBackgroundIntervalSeconds: Int = 600
) {

    val isDebugMode: Boolean
        get() = developerMode
}
