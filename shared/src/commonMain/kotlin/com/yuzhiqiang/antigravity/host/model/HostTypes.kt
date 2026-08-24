package com.yuzhiqiang.antigravity.host.model

enum class HostType {
    IDE,
    APP,
    CLI
}

enum class HostStatus {
    ACTIVE,
    INACTIVE,
    NOT_INSTALLED
}

enum class ClientIntegrationState {
    OFFICIAL,
    MANAGED,
    EXTERNAL,
    MISMATCH,
    CONFLICT,
    UNAVAILABLE;

    val isReady: Boolean
        get() = this == MANAGED || this == EXTERNAL
}

enum class ClientConfigurationState {
    NOT_ENABLED,
    MATCHED,
    NOT_RUNNING,
    SERVICE_STOPPED,
    NEEDS_UPDATE,
    UNAVAILABLE
}

data class HostDetailedStatus(
    val type: HostType,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val integrationState: ClientIntegrationState,
    val configurationState: ClientConfigurationState,
    val configuredEndpoint: String? = null,
    val targetEndpoint: String = "",
    val configPath: String? = null,
    val canEnable: Boolean = false,
    val canDisable: Boolean = false,
    val canLaunch: Boolean = false,
    val customPath: String? = null,
    val version: String? = null
) {
    val needsUpdate: Boolean
        get() = integrationState == ClientIntegrationState.MISMATCH || configurationState == ClientConfigurationState.NEEDS_UPDATE

    val isProxyActive: Boolean
        get() = integrationState.isReady && !needsUpdate
}

data class HostInspectionState(
    val type: HostType,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isActive: Boolean,
    val configPath: String? = null,
    val description: String = ""
)

