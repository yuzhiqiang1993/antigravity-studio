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

data class HostInspectionState(
    val type: HostType,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isActive: Boolean,
    val configPath: String? = null,
    val description: String = ""
)
