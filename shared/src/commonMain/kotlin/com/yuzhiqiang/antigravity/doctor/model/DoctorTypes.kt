package com.yuzhiqiang.antigravity.doctor.model

enum class DoctorCheckCategory {
    PROXY,
    NETWORK,
    CONFIG,
    PROVIDER,
    HOST
}

enum class DoctorCheckStatus {
    PASSED,
    INFO,
    WARNING,
    FAILED
}

sealed class DoctorFixAction {
    data object StartProxy : DoctorFixAction()
    data object OpenAddProvider : DoctorFixAction()
    data object RepairIdeSettings : DoctorFixAction()
    data object RepairAppEnvironment : DoctorFixAction()
    data object UpdateIdeSettings : DoctorFixAction()
    data object UpdateAppEnvironment : DoctorFixAction()
    data object UpdateCliConfig : DoctorFixAction()
    data object ResetIdeHostToOfficial : DoctorFixAction()
    data object ResetAppHostToOfficial : DoctorFixAction()
    data object ResetCliHostToOfficial : DoctorFixAction()
    data object RestartAppHost : DoctorFixAction()
    data object RestartIdeHost : DoctorFixAction()
    data class PruneInvalidModels(val providerId: String, val invalidModelIds: List<String>) : DoctorFixAction()
    data object RetestNetwork : DoctorFixAction()
}

data class DoctorCheckItem(
    val id: String = "",
    val category: DoctorCheckCategory,
    val title: String,
    val status: DoctorCheckStatus,
    val message: String,
    val suggestion: String? = null,
    val autoFixable: Boolean = false,
    val fixAction: DoctorFixAction? = null
)

data class DoctorReport(
    val items: List<DoctorCheckItem>,
    val overallStatus: DoctorCheckStatus,
    val timestamp: Long = System.currentTimeMillis()
) {
    val overallPassed: Boolean
        get() = overallStatus == DoctorCheckStatus.PASSED || overallStatus == DoctorCheckStatus.INFO
}
