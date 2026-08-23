package com.yuzhiqiang.antigravity.ui.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.NoticeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

class AppViewModel(
    val configStore: ConfigStore = ConfigStore(),
    val proxyServer: LocalProxyServer = LocalProxyServer(configStore),
    val doctorEngine: DoctorEngine = DoctorEngine(configStore, proxyServer)
) : ViewModel() {

    val config: StateFlow<AppConfig> = configStore.configState
    val configLoadError: StateFlow<String?> = configStore.loadError
    val isProxyRunning: StateFlow<Boolean> = proxyServer.isRunning
    val actualProxyPort: StateFlow<Int> = proxyServer.actualPort
    val activityLogs: StateFlow<List<ActivityLog>> = ActivityRecorder.logs

    private val _currentTab = MutableStateFlow(NavTab.OVERVIEW)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _openProviderEditorRequest = MutableStateFlow(false)
    val openProviderEditorRequest: StateFlow<Boolean> = _openProviderEditorRequest.asStateFlow()

    private val _isIdeHostActive = MutableStateFlow(false)
    val isIdeHostActive: StateFlow<Boolean> = _isIdeHostActive.asStateFlow()

    private val _isIdeRunning = MutableStateFlow(false)
    val isIdeRunning: StateFlow<Boolean> = _isIdeRunning.asStateFlow()

    private val _isIdeInstalled = MutableStateFlow(false)
    val isIdeInstalled: StateFlow<Boolean> = _isIdeInstalled.asStateFlow()

    private val _ideHostError = MutableStateFlow<String?>(null)
    val ideHostError: StateFlow<String?> = _ideHostError.asStateFlow()

    private val _doctorReport = MutableStateFlow<DoctorReport?>(null)
    val doctorReport: StateFlow<DoctorReport?> = _doctorReport.asStateFlow()

    private val _isDoctorRunning = MutableStateFlow(false)
    val isDoctorRunning: StateFlow<Boolean> = _isDoctorRunning.asStateFlow()

    private val _showDoctorDialog = MutableStateFlow(false)
    val showDoctorDialog: StateFlow<Boolean> = _showDoctorDialog.asStateFlow()

    private val _notice = MutableStateFlow<NoticeState?>(null)
    val notice: StateFlow<NoticeState?> = _notice.asStateFlow()

    data class ConfirmDialogState(
        val title: String,
        val message: String,
        val confirmLabel: String? = null,
        val cancelLabel: String? = null,
        val isDestructive: Boolean = false,
        val onConfirm: () -> Unit
    )

    private val _confirmDialog = MutableStateFlow<ConfirmDialogState?>(null)
    val confirmDialog: StateFlow<ConfirmDialogState?> = _confirmDialog.asStateFlow()

    private val _isCliHostActive = MutableStateFlow(false)
    val isCliHostActive: StateFlow<Boolean> = _isCliHostActive.asStateFlow()

    private val _isCliInstalled = MutableStateFlow(false)
    val isCliInstalled: StateFlow<Boolean> = _isCliInstalled.asStateFlow()

    private val _isAppHostActive = MutableStateFlow(false)
    val isAppHostActive: StateFlow<Boolean> = _isAppHostActive.asStateFlow()

    private val _isAppRunning = MutableStateFlow(false)
    val isAppRunning: StateFlow<Boolean> = _isAppRunning.asStateFlow()

    private val _isAppInstalled = MutableStateFlow(false)
    val isAppInstalled: StateFlow<Boolean> = _isAppInstalled.asStateFlow()

    private val _ideDetailedStatus = MutableStateFlow(
        com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            com.yuzhiqiang.antigravity.host.model.HostType.IDE,
            isInstalled = false,
            isRunning = false,
            integrationState = com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE,
            configurationState = com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
        )
    )
    val ideDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> = _ideDetailedStatus.asStateFlow()

    private val _appDetailedStatus = MutableStateFlow(
        com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            com.yuzhiqiang.antigravity.host.model.HostType.APP,
            isInstalled = false,
            isRunning = false,
            integrationState = com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE,
            configurationState = com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
        )
    )
    val appDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> = _appDetailedStatus.asStateFlow()

    private val _cliDetailedStatus = MutableStateFlow(
        com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            com.yuzhiqiang.antigravity.host.model.HostType.CLI,
            isInstalled = false,
            isRunning = false,
            integrationState = com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE,
            configurationState = com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
        )
    )
    val cliDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> = _cliDetailedStatus.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTester.TestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTester.TestResult?> = _connectionTestResult.asStateFlow()

    private val _officialModels = MutableStateFlow<List<OfficialCatalogModel>>(emptyList())
    val officialModels: StateFlow<List<OfficialCatalogModel>> = _officialModels.asStateFlow()

    private val _isFetchingOfficialModels = MutableStateFlow(false)
    val isFetchingOfficialModels: StateFlow<Boolean> = _isFetchingOfficialModels.asStateFlow()

    private val _officialModelsError = MutableStateFlow<String?>(null)
    val officialModelsError: StateFlow<String?> = _officialModelsError.asStateFlow()

    enum class ModelTestStatusKind {
        PENDING, SUCCESS, ERROR
    }

    data class ModelTestStatus(
        val status: ModelTestStatusKind,
        val latencyMs: Long? = null,
        val error: String? = null
    )

    private val _modelTestStatuses = MutableStateFlow<Map<String, ModelTestStatus>>(emptyMap())
    val modelTestStatuses: StateFlow<Map<String, ModelTestStatus>> = _modelTestStatuses.asStateFlow()

    private val _providerTestingIds = MutableStateFlow<Set<String>>(emptySet())
    val providerTestingIds: StateFlow<Set<String>> = _providerTestingIds.asStateFlow()
    
    private val hostDelegate = HostLifecycleDelegate(
        scope = viewModelScope,
        configStore = configStore,
        proxyServer = proxyServer,
        ideDetailedStatusFlow = _ideDetailedStatus,
        appDetailedStatusFlow = _appDetailedStatus,
        cliDetailedStatusFlow = _cliDetailedStatus,
        isIdeHostActiveFlow = _isIdeHostActive,
        isIdeInstalledFlow = _isIdeInstalled,
        isIdeRunningFlow = _isIdeRunning,
        ideHostErrorFlow = _ideHostError,
        isAppHostActiveFlow = _isAppHostActive,
        isAppInstalledFlow = _isAppInstalled,
        isAppRunningFlow = _isAppRunning,
        isCliInstalledFlow = _isCliInstalled,
        isCliHostActiveFlow = _isCliHostActive,
        showNotice = ::showNotice,
        showConfirmDialog = ::showConfirmDialog
    )
    val operatingHostKeys: StateFlow<Set<String>> = hostDelegate.operatingHostKeys
    private val _isSidebarCollapsed = MutableStateFlow(false)
    val isSidebarCollapsed: StateFlow<Boolean> = _isSidebarCollapsed.asStateFlow()
    fun toggleSidebar() { _isSidebarCollapsed.value = !_isSidebarCollapsed.value }

    private val s get() = com.yuzhiqiang.antigravity.i18n.I18nManager.strings

    private val doctorDelegate = DoctorDelegate(
        scope = viewModelScope,
        doctorEngine = doctorEngine,
        isDoctorRunningFlow = _isDoctorRunning,
        doctorReportFlow = _doctorReport,
        showDoctorDialogFlow = _showDoctorDialog,
        showNotice = ::showNotice,
        onOpenAddProvider = {
            selectTab(NavTab.MODELS)
            requestOpenProviderEditor()
        },
        onRefreshHostStatus = ::refreshHostStatus
    )

    init {
        val initialLang = AppLanguage.fromCode(configStore.currentConfig.language)
        I18nManager.currentLanguage = initialLang

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            proxyServer.start(configStore.currentConfig.proxyPort)
            refreshHostStatus()
            fetchOfficialModels().join()
        }
    }

    fun fetchOfficialModels(): Job {
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFetchingOfficialModels.value = true
            _officialModelsError.value = null
            try {
                val excludedCustomIds = configStore.currentConfig.upstreamModels
                    .map(UpstreamModel::id)
                    .toSet()
                val result = OfficialCatalogProbe.fetchOfficialModels(
                    excludedModelIds = excludedCustomIds
                )
                result.fold(
                    onSuccess = { models -> _officialModels.value = models },
                    onFailure = { error ->
                        _officialModelsError.value = error.message ?: s.modelsOfficialSyncFailed(s.commonUnknown)
                    }
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _officialModelsError.value = error.message ?: s.modelsOfficialSyncFailed(s.commonUnknown)
            } finally {
                _isFetchingOfficialModels.value = false
            }
        }
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun requestOpenProviderEditor() {
        _openProviderEditorRequest.value = true
    }

    fun consumeOpenProviderEditorRequest() {
        _openProviderEditorRequest.value = false
    }

    fun openDoctorDialog() = doctorDelegate.openDoctorDialog()
    fun closeDoctorDialog() = doctorDelegate.closeDoctorDialog()
    fun runDoctor() = doctorDelegate.runDoctor()
    fun runDoctorAutoFix(action: DoctorFixAction) = doctorDelegate.runDoctorAutoFix(action)

    fun refreshHostStatus() = hostDelegate.refreshHostStatus(actualProxyPort.value)

    fun showNotice(message: String, kind: NoticeKind = NoticeKind.SUCCESS) {
        _notice.value = NoticeState(message = message, kind = kind)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    data class HostPathDialogState(
        val hostKey: String,
        val hostTitle: String,
        val currentPath: String
    )

    private val _hostPathDialogState = MutableStateFlow<HostPathDialogState?>(null)
    val hostPathDialogState: StateFlow<HostPathDialogState?> = _hostPathDialogState.asStateFlow()

    fun openHostPathDialog(hostKey: String, hostTitle: String) {
        val currentPath = configStore.currentConfig.customHostPaths[hostKey].orEmpty()
        _hostPathDialogState.value = HostPathDialogState(hostKey, hostTitle, currentPath)
    }

    fun closeHostPathDialog() {
        _hostPathDialogState.value = null
    }

    fun saveCustomHostPath(hostKey: String, path: String?) {
        val cleanPath = path?.trim()?.takeIf { it.isNotEmpty() }
        configStore.updateConfig { current ->
            val updatedPaths = current.customHostPaths.toMutableMap()
            if (cleanPath != null) {
                updatedPaths[hostKey] = cleanPath
            } else {
                updatedPaths.remove(hostKey)
            }
            current.copy(customHostPaths = updatedPaths)
        }
        refreshHostStatus()
        showNotice(if (cleanPath != null) s.hostPathSavedCustom else s.hostPathResetNotice, NoticeKind.SUCCESS)
        closeHostPathDialog()
    }

    fun showConfirmDialog(state: ConfirmDialogState) {
        _confirmDialog.value = state
    }

    fun dismissConfirmDialog() {
        _confirmDialog.value = null
    }

    fun startProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = proxyServer.start(configStore.currentConfig.proxyPort)
            refreshHostStatus()
            if (result.isSuccess) {
                showNotice(s.proxyStarted(result.getOrThrow()), NoticeKind.SUCCESS)
            } else {
                showNotice(s.proxyStartFailed(result.exceptionOrNull()?.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    fun stopProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            proxyServer.stop()
            refreshHostStatus()
            showNotice(s.proxyStopped, NoticeKind.SUCCESS)
        }
    }

    fun restartProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = restartProxyInternal()
            refreshHostStatus()
            if (result.isSuccess) {
                showNotice(s.proxyRestarted(result.getOrThrow()), NoticeKind.SUCCESS)
            } else {
                showNotice(s.proxyRestartFailed(result.exceptionOrNull()?.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    private suspend fun restartProxyInternal(): Result<Int> {
        proxyServer.stop()
        return proxyServer.start(configStore.currentConfig.proxyPort)
    }

    fun toggleIdeHost() = hostDelegate.requestToggleIdeHost(actualProxyPort.value)
    fun requestToggleIdeHost() = hostDelegate.requestToggleIdeHost(actualProxyPort.value)
    fun forceResetIdeHost() = hostDelegate.requestForceResetHost("ide", actualProxyPort.value)
    fun requestRestartOrLaunchIde(isIdeRunning: Boolean) {
        if (isIdeRunning) {
            showConfirmDialog(
                ConfirmDialogState(
                    title = s.hostRestartConfirmTitle(s.hostIdeTitle),
                    message = s.hostRestartConfirmMessage(s.hostIdeTitle),
                    confirmLabel = s.hostRestart,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { hostDelegate.restartIde(actualProxyPort.value) }
                )
            )
        } else {
            hostDelegate.launchIde(actualProxyPort.value)
        }
    }
    fun restartIde() = hostDelegate.restartIde(actualProxyPort.value)
    fun launchIde() = hostDelegate.launchIde(actualProxyPort.value)

    fun toggleCliHost() = hostDelegate.requestToggleCliHost(actualProxyPort.value)
    fun requestToggleCliHost() = hostDelegate.requestToggleCliHost(actualProxyPort.value)
    fun forceResetCliHost() = hostDelegate.requestForceResetHost("cli", actualProxyPort.value)

    fun toggleAppHost() = hostDelegate.requestToggleAppHost(actualProxyPort.value)
    fun requestToggleAppHost() = hostDelegate.requestToggleAppHost(actualProxyPort.value)
    fun forceResetAppHost() = hostDelegate.requestForceResetHost("app", actualProxyPort.value)
    fun forceResetHost(hostKey: String) = hostDelegate.requestForceResetHost(hostKey, actualProxyPort.value)
    fun requestRestartOrLaunchApp(isAppRunning: Boolean) {
        if (isAppRunning) {
            showConfirmDialog(
                ConfirmDialogState(
                    title = s.hostRestartConfirmTitle(s.hostAppTitle),
                    message = s.hostRestartConfirmMessage(s.hostAppTitle),
                    confirmLabel = s.hostRestart,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { hostDelegate.restartApp(actualProxyPort.value) }
                )
            )
        } else {
            hostDelegate.launchApp(actualProxyPort.value)
        }
    }
    fun restartApp() = hostDelegate.restartApp(actualProxyPort.value)
    fun launchApp() = hostDelegate.launchApp(actualProxyPort.value)

    fun testProxyConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = ConnectionTester.testProxy(actualProxyPort.value)
            _connectionTestResult.value = result
            _isTestingConnection.value = false
            if (result.success) {
                showNotice(s.proxyTestSuccess(result.latencyMs), NoticeKind.SUCCESS)
            } else {
                showNotice(s.proxyTestFailed(result.error ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    fun toggleOfficialModel(modelId: String) {
        val current = configStore.currentConfig
        val list = current.disabledOfficialModels.toMutableList()
        if (list.contains(modelId)) list.remove(modelId) else list.add(modelId)
        configStore.saveConfig(current.copy(disabledOfficialModels = list))
    }

    fun toggleOfficialModelGroup(modelIds: Set<String>, enable: Boolean) {
        val current = configStore.currentConfig
        val set = current.disabledOfficialModels.toMutableSet()
        if (enable) set.removeAll(modelIds) else set.addAll(modelIds)
        configStore.saveConfig(current.copy(disabledOfficialModels = set.toList()))
    }

    fun toggleCustomModel(modelId: String) {
        val current = configStore.currentConfig
        val updated = current.upstreamModels.map {
            if (it.id == modelId) it.copy(enabled = !it.enabled) else it
        }
        val affectedUpstream = updated.find { it.id == modelId }
        val updatedVirtuals = if (affectedUpstream != null) {
            current.virtualModels.map { virtual ->
                if (virtual.upstreamModelId == modelId) virtual.copy(enabled = affectedUpstream.enabled) else virtual
            }
        } else {
            current.virtualModels
        }
        configStore.saveConfig(current.copy(upstreamModels = updated, virtualModels = updatedVirtuals))
    }

    fun saveProvider(provider: Provider, models: List<UpstreamModel>): Boolean {
        return try {
            val syncResult = ProviderModelSynchronizer.synchronize(
                config = configStore.currentConfig,
                provider = provider,
                selectedModels = models
            ).getOrThrow()

            configStore.updateConfig { current ->
                val updatedProviders = current.providers.filterNot { it.id == provider.id } + provider
                val removedUpstreams = current.upstreamModels
                    .filter { it.providerId == provider.id && it.id !in syncResult.upstreamModels.map(UpstreamModel::id).toSet() }
                val removedVirtualModels = current.virtualModels.filter { previous ->
                    previous.upstreamModelId in removedUpstreams.map { it.id }.toSet() &&
                            previous.id !in syncResult.virtualModels.map { it.id }.toSet()
                }
                val cleaned = current.removeFallbackReferences(
                    removedUpstreams = removedUpstreams,
                    removedVirtualModels = removedVirtualModels
                )
                cleaned.removeVirtualReferences(removedVirtualModels).copy(
                    providers = updatedProviders,
                    upstreamModels = syncResult.upstreamModels,
                    virtualModels = syncResult.virtualModels
                )
            }
            showNotice(s.modelsProviderSaved(provider.name), NoticeKind.SUCCESS)
            true
        } catch (e: Exception) {
            showNotice(s.modelsProviderSaveFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    private fun AppConfig.removeFallbackReferences(
        removedUpstreams: List<UpstreamModel> = emptyList(),
        removedVirtualModels: List<VirtualModel> = emptyList()
    ): AppConfig {
        val blockers = fallbackBlockers(removedUpstreams, removedVirtualModels)
        if (blockers.isNotEmpty()) {
            throw IllegalStateException(
                s.modelsDeleteBlockers(blockers.joinToString("、") { it.name.ifBlank { it.id } })
            )
        }
        return this
    }

    private fun AppConfig.fallbackBlockers(
        removedUpstreams: List<UpstreamModel> = emptyList(),
        removedVirtualModels: List<VirtualModel> = emptyList()
    ): List<VirtualModel> {
        if (removedUpstreams.isEmpty() && removedVirtualModels.isEmpty()) return emptyList()
        val removedUpstreamIds = removedUpstreams.map { upstream -> upstream.id }.toSet()
        val removedUpstreamRefs = removedUpstreams.flatMap(::upstreamModelReferences).toSet()
        val upstreamVirtualModels = virtualModels.filter { virtual ->
            virtual.upstreamModelId in removedUpstreamIds
        }
        val allRemovedVirtualModels =
            (upstreamVirtualModels + removedVirtualModels).distinctBy { virtual -> virtual.id }
        val removedVirtualIds = allRemovedVirtualModels.map { virtual -> virtual.id }.toSet()
        val removedVirtualRefs = allRemovedVirtualModels
            .flatMap(::virtualModelReferences)
            .toSet()
        return virtualModels
            .filterNot { virtual -> virtual.id in removedVirtualIds }
            .filter { virtual ->
                val fallback = virtual.fallbackVirtualModelId?.let(::normalizeModelReference)
                fallback != null && (fallback in removedVirtualRefs || fallback in removedUpstreamRefs)
            }
    }

    private fun AppConfig.removeVirtualReferences(removedVirtualModels: List<VirtualModel>): AppConfig {
        if (removedVirtualModels.isEmpty()) return this
        val removedVirtualRefs = removedVirtualModels
            .flatMap(::virtualModelReferences)
            .toSet()
        return copy(
            virtualModels = virtualModels.map { virtual ->
                val fallback = virtual.fallbackVirtualModelId?.let(::normalizeModelReference)
                if (fallback != null && fallback in removedVirtualRefs) {
                    virtual.copy(fallbackVirtualModelId = null)
                } else {
                    virtual
                }
            }
        )
    }

    fun deleteProvider(providerId: String) {
        val current = configStore.currentConfig
        val targetProvider = current.providers.find { it.id == providerId } ?: return
        val removedUpstreams = current.upstreamModels.filter { it.providerId == providerId }
        val removedVirtualModels = current.virtualModels.filter {
            it.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
        }
        try {
            val cleaned = current.removeFallbackReferences(
                removedUpstreams = removedUpstreams,
                removedVirtualModels = removedVirtualModels
            )
            configStore.saveConfig(
                cleaned.copy(
                    providers = cleaned.providers.filterNot { it.id == providerId },
                    upstreamModels = cleaned.upstreamModels.filterNot { it.providerId == providerId },
                    virtualModels = cleaned.virtualModels.filterNot { virtual ->
                        virtual.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
                    },
                    modelCompressionPolicies = cleaned.modelCompressionPolicies.filterKeys { key ->
                        key !in removedUpstreams.map(UpstreamModel::id) &&
                                key !in removedVirtualModels.map(VirtualModel::id)
                    }
                )
            )
            showNotice(s.modelsProviderDeleted(targetProvider.name), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsProviderDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun deleteSingleModel(modelId: String) {
        val current = configStore.currentConfig
        val targetModel = current.upstreamModels.find { it.id == modelId } ?: return
        val removedVirtuals = current.virtualModels.filter { it.upstreamModelId == modelId }
        try {
            val cleaned = current.removeFallbackReferences(
                removedUpstreams = listOf(targetModel),
                removedVirtualModels = removedVirtuals
            )
            configStore.saveConfig(
                cleaned.copy(
                    upstreamModels = cleaned.upstreamModels.filterNot { it.id == modelId },
                    virtualModels = cleaned.virtualModels.filterNot { it.upstreamModelId == modelId },
                    modelCompressionPolicies = cleaned.modelCompressionPolicies - modelId
                )
            )
            showNotice(s.modelsModelDeleted(targetModel.displayName ?: targetModel.name), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsModelDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun updateSingleModel(updatedModel: UpstreamModel): Boolean {
        return try {
            configStore.updateConfig { current ->
                val provider = current.providers.firstOrNull { item -> item.id == updatedModel.providerId }
                    ?: throw IllegalArgumentException(s.modelsProviderNotFound)
                val providerModels = current.upstreamModels.map { model ->
                    if (model.id == updatedModel.id) updatedModel else model
                }.filter { model -> model.providerId == provider.id }
                val synchronized = ProviderModelSynchronizer.synchronize(
                    config = current,
                    provider = provider,
                    selectedModels = providerModels
                ).getOrThrow()
                val removedVirtualModels = current.virtualModels.filter { previous ->
                    synchronized.virtualModels.none { updated -> updated.id == previous.id }
                }
                val cleaned = current.removeFallbackReferences(
                    removedUpstreams = emptyList(),
                    removedVirtualModels = removedVirtualModels
                )
                cleaned.removeVirtualReferences(removedVirtualModels).copy(
                    upstreamModels = synchronized.upstreamModels,
                    virtualModels = synchronized.virtualModels
                )
            }
            showNotice(s.modelsModelUpdated(updatedModel.displayName ?: updatedModel.name), NoticeKind.SUCCESS)
            true
        } catch (e: Exception) {
            showNotice(s.modelsModelUpdateFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    fun updateVirtualModelFallback(
        virtualModelId: String,
        fallbackVirtualModelId: String?
    ): Boolean {
        return try {
            configStore.updateConfig { current ->
                val source = current.virtualModels.firstOrNull { it.id == virtualModelId }
                    ?: throw IllegalArgumentException(s.modelsVirtualModelNotFound(virtualModelId))
                val normalizedTarget = fallbackVirtualModelId
                    ?.trim()
                    ?.removePrefix("models/")
                    ?.takeIf { it.isNotEmpty() }
                val target = normalizedTarget?.let { targetId ->
                    current.virtualModels.firstOrNull { virtual ->
                        virtual.id == targetId || targetId in ModelIdentity.acceptedIds(virtual)
                    }
                }
                if (normalizedTarget != null && target == null) {
                    throw IllegalArgumentException(s.modelsVirtualModelNotFound(normalizedTarget))
                }
                if (target?.id == source.id) {
                    throw IllegalArgumentException(s.modelsFallbackSelfError)
                }
                current.copy(
                    virtualModels = current.virtualModels.map { virtual ->
                        if (virtual.id == source.id) {
                            virtual.copy(fallbackVirtualModelId = target?.id)
                        } else {
                            virtual
                        }
                    }
                )
            }
            showNotice(
                if (fallbackVirtualModelId.isNullOrBlank()) s.modelsFallbackCleared else s.modelsFallbackSaved,
                NoticeKind.SUCCESS
            )
            true
        } catch (error: Exception) {
            showNotice(s.modelsFallbackSaveFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    fun saveCompressionPolicy(modelId: String, policy: ModelCompressionPolicy?) {
        configStore.updateConfig { current ->
            val updatedPolicies = current.modelCompressionPolicies.toMutableMap()
            var updatedUpstreams = current.upstreamModels
            val currentOfficial = _officialModels.value
            val matchedOfficial = currentOfficial.find { it.id == modelId }
            if (matchedOfficial != null) {
                val regex = Regex("""^(.*?)(?:\s*\((.*?)\))?$""")
                val targetBaseName = regex.find(matchedOfficial.displayName.ifBlank { matchedOfficial.id })
                    ?.groupValues?.getOrNull(1)?.trim() ?: matchedOfficial.id

                val relatedIds = currentOfficial.filter { m ->
                    val mBase = regex.find(m.displayName.ifBlank { m.id })?.groupValues?.getOrNull(1)?.trim() ?: m.id
                    mBase.equals(targetBaseName, ignoreCase = true) ||
                            m.replacementModelId == modelId || matchedOfficial.replacementModelId == m.id
                }.map { it.id }.toMutableSet()
                relatedIds.add(modelId)

                // 补充模型族基础 ID 与 -tiered 父条目 ID，确保全量覆盖
                val baseSlug = modelId.removeSuffix("-high")
                    .removeSuffix("-medium")
                    .removeSuffix("-low")
                    .removeSuffix("-tiered")
                relatedIds.add(baseSlug)
                relatedIds.add("$baseSlug-tiered")

                relatedIds.forEach { id ->
                    if (policy != null) updatedPolicies[id] = policy else updatedPolicies.remove(id)
                }
            } else {
                if (policy != null) updatedPolicies[modelId] = policy else updatedPolicies.remove(modelId)
                // 同步更新 UpstreamModel 实体内部的 compressionPolicy 字段，确保双向一致
                updatedUpstreams = current.upstreamModels.map { upstream ->
                    val isDirectMatch = upstream.id == modelId || upstream.upstreamModelId == modelId
                    val isVirtualMatch = current.virtualModels.any { it.id == modelId && it.upstreamModelId == upstream.id }
                    if (isDirectMatch || isVirtualMatch) {
                        upstream.copy(compressionPolicy = policy)
                    } else {
                        upstream
                    }
                }
            }
            current.copy(
                upstreamModels = updatedUpstreams,
                modelCompressionPolicies = updatedPolicies
            )
        }
    }

    fun clearActivityLogs() {
        ActivityRecorder.clear()
    }

    fun testSingleModel(model: UpstreamModel, provider: Provider) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val updatedMap = _modelTestStatuses.value.toMutableMap()
            updatedMap[model.id] = ModelTestStatus(ModelTestStatusKind.PENDING)
            _modelTestStatuses.value = updatedMap

            val result = ConnectionTester.testProvider(provider, model)
            val finalMap = _modelTestStatuses.value.toMutableMap()
            if (result.success) {
                finalMap[model.id] = ModelTestStatus(
                    status = ModelTestStatusKind.SUCCESS,
                    latencyMs = result.latencyMs
                )
                showNotice(
                    s.modelsModelTestSuccess(model.displayName ?: model.upstreamModelId, result.latencyMs),
                    NoticeKind.SUCCESS
                )
            } else {
                finalMap[model.id] = ModelTestStatus(
                    status = ModelTestStatusKind.ERROR,
                    error = result.error ?: "HTTP ${result.statusCode}"
                )
                showNotice(
                    s.modelsModelTestFailed(model.displayName ?: model.upstreamModelId, result.error ?: result.statusCode.toString()),
                    NoticeKind.ERROR
                )
            }
            _modelTestStatuses.value = finalMap
        }
    }

    fun testProviderModels(providerId: String) {
        val current = config.value
        val provider = current.providers.find { it.id == providerId } ?: return
        val models = current.upstreamModels.filter { it.providerId == providerId }
        if (models.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _providerTestingIds.value = _providerTestingIds.value + providerId
            val pendingMap = _modelTestStatuses.value.toMutableMap()
            models.forEach {
                pendingMap[it.id] = ModelTestStatus(ModelTestStatusKind.PENDING)
            }
            _modelTestStatuses.value = pendingMap

            var successCount = 0
            val updatedMap = _modelTestStatuses.value.toMutableMap()
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            val testMutex = Mutex()
            val jobs = models.map { model ->
                launch {
                    semaphore.acquire()
                    try {
                        val result = ConnectionTester.testProvider(provider, model)
                        testMutex.withLock {
                            if (result.success) {
                                successCount++
                                updatedMap[model.id] = ModelTestStatus(
                                    status = ModelTestStatusKind.SUCCESS,
                                    latencyMs = result.latencyMs
                                )
                            } else {
                                updatedMap[model.id] = ModelTestStatus(
                                    status = ModelTestStatusKind.ERROR,
                                    error = result.error ?: "HTTP ${result.statusCode}"
                                )
                            }
                            _modelTestStatuses.value = updatedMap.toMap()
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
            _providerTestingIds.value = _providerTestingIds.value - providerId

            val total = models.size
            if (successCount == total) {
                showNotice(s.modelsBatchTestSuccess(successCount, total), NoticeKind.SUCCESS)
            } else {
                showNotice(
                    s.modelsBatchTestPartial(successCount, total, total - successCount),
                    NoticeKind.ERROR
                )
            }
        }
    }

    fun updateLanguage(lang: AppLanguage) {
        I18nManager.currentLanguage = lang
        configStore.updateConfig { it.copy(language = lang.code) }
    }

    fun updateThemeMode(mode: String) {
        configStore.updateConfig { it.copy(themeMode = mode) }
    }

    fun updateProxyPort(port: Int) {
        if (port !in 1024..65535) {
            showNotice(s.settingsPortInvalid, NoticeKind.ERROR)
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                configStore.updateConfig { current -> current.copy(proxyPort = port) }
                val result = restartProxyInternal()
                refreshHostStatus()
                if (result.isSuccess) {
                    showNotice(s.settingsPortUpdated(result.getOrThrow()), NoticeKind.SUCCESS)
                } else {
                    showNotice(s.settingsPortRestartFailed(result.exceptionOrNull()?.message ?: s.commonUnknown), NoticeKind.ERROR)
                }
            } catch (error: Exception) {
                showNotice(s.settingsPortUpdateFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    private fun normalizeModelReference(value: String): String {
        return value.trim().removePrefix("models/")
    }

    private fun upstreamModelReferences(model: UpstreamModel): Set<String> {
        return setOf(
            normalizeModelReference(model.id),
            normalizeModelReference(model.upstreamModelId),
            normalizeModelReference(ModelIdentity.effectiveUpstreamHostModelId(model))
        )
    }

    private fun virtualModelReferences(model: VirtualModel): Set<String> {
        return ModelIdentity.acceptedIds(model)
            .map(::normalizeModelReference)
            .toSet()
    }

    override fun onCleared() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            proxyServer.stop()
        }
        super.onCleared()
    }
}
