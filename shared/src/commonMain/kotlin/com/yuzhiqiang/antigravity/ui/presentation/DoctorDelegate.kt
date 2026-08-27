package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DoctorDelegate(
    private val scope: CoroutineScope,
    private val doctorEngine: DoctorEngine,
    private val isDoctorRunningFlow: MutableStateFlow<Boolean>,
    private val doctorReportFlow: MutableStateFlow<DoctorReport?>,
    private val showDoctorDialogFlow: MutableStateFlow<Boolean>,
    private val showNotice: (String, NoticeKind) -> Unit,
    private val onOpenAddProvider: () -> Unit,
    private val onOpenNetworkSettings: () -> Unit = {},
    private val onRefreshHostStatus: () -> Unit
) {

    fun openDoctorDialog() {
        showDoctorDialogFlow.value = true
        runDoctor()
    }

    fun closeDoctorDialog() {
        showDoctorDialogFlow.value = false
    }

    fun runDoctor() {
        scope.launch {
            isDoctorRunningFlow.value = true
            try {
                doctorReportFlow.value = doctorEngine.diagnose()
            } finally {
                isDoctorRunningFlow.value = false
            }
        }
    }

    fun runDoctorAutoFix(action: DoctorFixAction) {
        if (action is DoctorFixAction.OpenAddProvider) {
            onOpenAddProvider()
            closeDoctorDialog()
            return
        }
        if (action is DoctorFixAction.OpenNetworkSettings) {
            onOpenNetworkSettings()
            closeDoctorDialog()
            return
        }
        scope.launch {
            val success = doctorEngine.autoFix(action)
            onRefreshHostStatus()
            val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
            if (success) {
                showNotice(s.doctorAutoFixSuccess, NoticeKind.SUCCESS)
            } else {
                showNotice(s.doctorAutoFixFailed, NoticeKind.ERROR)
            }
            runDoctor()
        }
    }
}
