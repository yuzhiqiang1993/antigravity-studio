package com.yuzhiqiang.antigravity.ui.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.data.usage.UsageRepository
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.domain.model.account.*
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.network.PlatformNetworkConfig
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.services.auth.HotSwitchCoordinator
import com.yuzhiqiang.antigravity.services.auth.SmartSwitchCoordinator
import com.yuzhiqiang.antigravity.services.auth.TokenRenewalManager
import com.yuzhiqiang.antigravity.services.quota.QuotaFetchService
import com.yuzhiqiang.antigravity.services.quota.QuotaPoller
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.NoticeState
import com.yuzhiqiang.antigravity.update.model.AppUpdateDownloadState
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import com.yuzhiqiang.antigravity.update.model.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class AppViewModel(


    val configStore: ConfigStore = ConfigStore(),
    val proxyServer: LocalProxyServer = LocalProxyServer(configStore),
    val doctorEngine: DoctorEngine = DoctorEngine(configStore, proxyServer),
    val accountStore: AccountStore = AccountStore(),
    val googleAuthService: GoogleAuthService = GoogleAuthService(),
    val quotaFetchService: QuotaFetchService = QuotaFetchService(
        tokenRefreshCallback = { refreshToken ->
            googleAuthService.refreshAccessToken(refreshToken).map { it.accessToken }
        }
    )
) : ViewModel() {


    val hotSwitchCoordinator = HotSwitchCoordinator(
        accountStore = accountStore,
        customHostPathsProvider = { configStore.currentConfig.customHostPaths },
        proxyPortProvider = { actualProxyPort.value },
        googleAuthService = googleAuthService
    )
    val tokenRenewalManager = TokenRenewalManager(accountStore, googleAuthService, viewModelScope)
    val quotaPoller = QuotaPoller(quotaFetchService, viewModelScope) { snapshot ->
        val acc = accountStore.currentAccounts().firstOrNull { it.id == snapshot.accountId }
        if (acc != null && acc.profile.tier != snapshot.tier && snapshot.tier != com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE) {
            val updated = acc.copy(profile = acc.profile.copy(tier = snapshot.tier))
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                accountStore.upsertAccount(updated)
            }
        }
    }
    val smartSwitchCoordinator =
        SmartSwitchCoordinator(accountStore, configStore, hotSwitchCoordinator) { quotaPoller.quotaSnapshots.value }


    val config: StateFlow<AppConfig> = configStore.configState
    val configLoadError: StateFlow<String?> = configStore.loadError
    val isProxyRunning: StateFlow<Boolean> = proxyServer.isRunning
    val actualProxyPort: StateFlow<Int> = proxyServer.actualPort
    val activityLogs: StateFlow<List<ActivityLog>> = ActivityRecorder.logs

    val accounts: StateFlow<List<AccountInfo>> = accountStore.accountsState
    val activeAccount: StateFlow<AccountInfo?> = accountStore.activeAccountState

    private val _appCliActiveEmail = MutableStateFlow<String?>(null)
    val appCliActiveEmail: StateFlow<String?> = _appCliActiveEmail.asStateFlow()

    // App 与 CLI 共用同一运行态账号来源，保留现有公开访问入口。
    val appActiveEmail: StateFlow<String?> get() = appCliActiveEmail
    val cliActiveEmail: StateFlow<String?> get() = appCliActiveEmail

    private val _ideActiveEmail = MutableStateFlow<String?>(null)
    val ideActiveEmail: StateFlow<String?> = _ideActiveEmail.asStateFlow()

    val cliActiveAccount: StateFlow<AccountInfo?> = accountStore.activeAccountState
    val ideActiveAccount: StateFlow<AccountInfo?> = hotSwitchCoordinator.ideActiveAccount
    private val _isAccountSwitching = MutableStateFlow(false)
    val isAccountSwitching: StateFlow<Boolean> = _isAccountSwitching.asStateFlow()


    val accountQuotas: StateFlow<Map<String, AccountQuotaSnapshot>> get() = quotaPoller.quotaSnapshots
    val isRefreshingQuotas: StateFlow<Boolean> get() = quotaPoller.isRefreshing
    val refreshingAccountIds: StateFlow<Set<String>> get() = quotaPoller.refreshingAccountIds

    private val _isOAuthAuthorizing = MutableStateFlow(false)

    val isOAuthAuthorizing: StateFlow<Boolean> = _isOAuthAuthorizing.asStateFlow()


    private val _oauthAuthUrl = MutableStateFlow<String?>(null)
    val oauthAuthUrl: StateFlow<String?> = _oauthAuthUrl.asStateFlow()


    private val _currentTab = MutableStateFlow(NavTab.OVERVIEW)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _networkSettingsRequest = MutableStateFlow(0L)
    val networkSettingsRequest: StateFlow<Long> = _networkSettingsRequest.asStateFlow()

    private val _isTestingOutboundProxy = MutableStateFlow(false)
    val isTestingOutboundProxy: StateFlow<Boolean> = _isTestingOutboundProxy.asStateFlow()

    private val _outboundProxyTestResult = MutableStateFlow<ConnectionTester.OutboundProxyTestResult?>(null)
    val outboundProxyTestResult: StateFlow<ConnectionTester.OutboundProxyTestResult?> =
        _outboundProxyTestResult.asStateFlow()
    private var outboundProxyTestJob: Job? = null

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
    val ideDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> =
        _ideDetailedStatus.asStateFlow()

    private val _appDetailedStatus = MutableStateFlow(
        com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            com.yuzhiqiang.antigravity.host.model.HostType.APP,
            isInstalled = false,
            isRunning = false,
            integrationState = com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE,
            configurationState = com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
        )
    )
    val appDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> =
        _appDetailedStatus.asStateFlow()

    private val _cliDetailedStatus = MutableStateFlow(
        com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            com.yuzhiqiang.antigravity.host.model.HostType.CLI,
            isInstalled = false,
            isRunning = false,
            integrationState = com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE,
            configurationState = com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
        )
    )
    val cliDetailedStatus: StateFlow<com.yuzhiqiang.antigravity.host.model.HostDetailedStatus> =
        _cliDetailedStatus.asStateFlow()

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

    private val providerModelDelegate = ProviderModelDelegate(
        scope = viewModelScope,
        configStore = configStore,
        accountStore = accountStore,
        googleAuthService = googleAuthService,
        configFlow = config,
        officialModelsFlow = _officialModels,
        isFetchingOfficialModelsFlow = _isFetchingOfficialModels,
        officialModelsErrorFlow = _officialModelsError,
        modelTestStatusesFlow = _modelTestStatuses,
        providerTestingIdsFlow = _providerTestingIds,
        showNotice = ::showNotice
    )

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
    fun toggleSidebar() {
        _isSidebarCollapsed.value = !_isSidebarCollapsed.value
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _showOnboardingDialog = MutableStateFlow(false)
    val showOnboardingDialog: StateFlow<Boolean> = _showOnboardingDialog.asStateFlow()

    fun openOnboardingDialog() {
        _showOnboardingDialog.value = true
    }

    fun dismissOnboardingDialog() {
        _showOnboardingDialog.value = false
    }

    fun completeOnboarding() {
        _showOnboardingDialog.value = false
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            configStore.updateConfig { current ->
                current.copy(hasCompletedOnboarding = true)
            }
        }
    }

    private val _activeRelease = MutableStateFlow<ReleaseInfo?>(null)
    val activeRelease: StateFlow<ReleaseInfo?> = _activeRelease.asStateFlow()

    private val _downloadState = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()
    val usageRepository = UsageRepository()
    private val usageDelegate = UsageDelegate(
        scope = viewModelScope,
        usageRepository = usageRepository,
        showNotice = ::showNotice
    )

    val usageStats get() = usageDelegate.usageStats
    val isRefreshingUsage get() = usageDelegate.isRefreshing
    val usageTimeRange get() = usageDelegate.selectedTimeRange
    val usageCustomDateRange get() = usageDelegate.customDateRange
    val usageSelectedSources get() = usageDelegate.selectedSources

    fun refreshUsageStats(force: Boolean = true) = usageDelegate.refresh(force)
    fun setUsageTimeRange(
        timeRange: UsageTimeRange,
        customRange: CustomDateRange? = null
    ) =
        usageDelegate.setTimeRange(timeRange, customRange)

    fun toggleUsageSource(source: String) = usageDelegate.toggleSource(source)
    fun updateCustomPricingPath(path: String?) {
        val trimmed = path?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            configStore.updateConfig { it.copy(customPricingPath = trimmed) }
            usageRepository.pricingService.setCustomPricingSource(trimmed)
            usageDelegate.recompute()
        }
    }

    private val updateDelegate = UpdateDelegate(
        scope = viewModelScope,
        configStore = configStore,
        updateStateFlow = _updateState,
        showUpdateDialogFlow = _showUpdateDialog,
        activeReleaseFlow = _activeRelease,
        downloadStateFlow = _downloadState,
        showNotice = ::showNotice
    )

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
        onOpenNetworkSettings = {
            openNetworkSettings()
        },
        onRefreshHostStatus = ::refreshHostStatus
    )

    private val accountDelegate = AccountDelegate(
        scope = viewModelScope,
        configStore = configStore,
        accountStore = accountStore,
        googleAuthService = googleAuthService,
        hotSwitchCoordinator = hotSwitchCoordinator,
        tokenRenewalManager = tokenRenewalManager,
        quotaPoller = quotaPoller,
        appCliActiveEmailFlow = _appCliActiveEmail,
        ideActiveEmailFlow = _ideActiveEmail,
        isAccountSwitchingFlow = _isAccountSwitching,
        isOAuthAuthorizingFlow = _isOAuthAuthorizing,
        oauthAuthUrlFlow = _oauthAuthUrl,
        showNotice = ::showNotice,
        onRefreshHostStatus = ::refreshHostStatus,
        onFetchOfficialModels = providerModelDelegate::fetchOfficialModels
    )

    init {
        val initialLang = AppLanguage.fromCode(configStore.currentConfig.language)
        I18nManager.currentLanguage = initialLang
        PlatformNetworkConfig.applyOutboundProxy(configStore.currentConfig.outboundProxy)

        // 启动底层文件与进程生命周期异步监听器
        com.yuzhiqiang.antigravity.services.events.HostFileWatcher.start()
        com.yuzhiqiang.antigravity.services.events.HostProcessWatcher.start()

        // 订阅全局跨进程被动事件流
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.yuzhiqiang.antigravity.services.events.HostEventHub.events.collect { event ->
                when (event) {
                    is com.yuzhiqiang.antigravity.services.events.HostEvent.FileModified -> {
                        syncHostAccounts()
                    }

                    is com.yuzhiqiang.antigravity.services.events.HostEvent.ProcessExited -> {
                        refreshHostStatus()
                    }

                    else -> Unit
                }
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            startHostAccountDetectionLoop()
            tokenRenewalManager.start()
            quotaPoller.start(
                accountsProvider = { accountStore.currentAccounts() },
                activeAccountProvider = { accountStore.currentActiveAccount() },
                configProvider = { configStore.currentConfig }
            )
            proxyServer.start(configStore.currentConfig.proxyPort)
            refreshHostStatus()
            fetchOfficialModels().join()
            val savedCustomPricing = configStore.currentConfig.customPricingPath
            if (!savedCustomPricing.isNullOrBlank()) {
                usageRepository.pricingService.setCustomPricingSource(savedCustomPricing)
            }
            // 重新计算已加载的磁盘快照，再启动首次扫描，避免价格变更只在下一次手动刷新生效。
            usageDelegate.recompute()
            // 价格配置完成后再启动首次用量扫描，确保首屏费用不会使用旧费率。
            usageDelegate.startInitialRefresh()
            if (configStore.currentConfig.autoCheckUpdate) {
                checkForUpdates(isManual = false)
            }
            if (!configStore.currentConfig.hasCompletedOnboarding) {
                _showOnboardingDialog.value = true
            }
        }
    }

    fun syncHostAccounts(): Job = accountDelegate.syncHostAccounts()

    private fun startHostAccountDetectionLoop() {
        accountDelegate.startHostAccountDetectionLoop()
    }

    /**
     * 窗口获得焦点时由 UI 层调用，立即刷新宿主账号与运行状态。
     */
    fun onWindowFocusGained() {
        accountDelegate.onWindowFocusGained()
    }


    fun fetchOfficialModels(): Job = providerModelDelegate.fetchOfficialModels()

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun openNetworkSettings() {
        _showDoctorDialog.value = false
        _currentTab.value = NavTab.SETTINGS
        _networkSettingsRequest.value += 1L
    }

    fun saveOutboundProxy(config: OutboundProxyConfig) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                configStore.updateConfig { current -> current.copy(outboundProxy = config) }
                PlatformNetworkConfig.applyOutboundProxy(configStore.currentConfig.outboundProxy)
                _outboundProxyTestResult.value = null
                showNotice(s.settingsOutboundProxySaved, NoticeKind.SUCCESS)
            } catch (error: Exception) {
                showNotice(
                    s.settingsOutboundProxySaveFailed(error.message ?: s.commonUnknown),
                    NoticeKind.ERROR
                )
            }
        }
    }

    fun testOutboundProxy(config: OutboundProxyConfig) {
        outboundProxyTestJob?.cancel()
        outboundProxyTestJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isTestingOutboundProxy.value = true
            _outboundProxyTestResult.value = null
            try {
                _outboundProxyTestResult.value = ConnectionTester.testOutboundProxy(config)
            } finally {
                _isTestingOutboundProxy.value = false
            }
        }
    }

    fun clearOutboundProxyTestResult() {
        _outboundProxyTestResult.value = null
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

    fun updateDefaultSwitchTarget(target: String) {
        configStore.updateConfig { current ->
            current.copy(defaultSwitchTarget = target)
        }
    }

    fun saveLastSwitchChoice(applyToIde: Boolean, applyToAppCli: Boolean) {
        configStore.updateConfig { current ->
            current.copy(
                defaultSwitchTarget = DefaultSwitchTarget.REMEMBER_LAST.value,
                lastSwitchApplyToIde = applyToIde,
                lastSwitchApplyToAppCli = applyToAppCli
            )
        }
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

    fun toggleOfficialModel(modelId: String) = providerModelDelegate.toggleOfficialModel(modelId)

    fun toggleOfficialModelGroup(modelIds: Set<String>, enable: Boolean) =
        providerModelDelegate.toggleOfficialModelGroup(modelIds, enable)

    fun toggleCustomModel(modelId: String) = providerModelDelegate.toggleCustomModel(modelId)

    fun saveProvider(provider: Provider, bindings: List<ProviderModelBinding>): Boolean =
        providerModelDelegate.saveProvider(provider, bindings)

    fun deleteProvider(providerId: String) = providerModelDelegate.deleteProvider(providerId)

    fun deleteSingleModel(modelId: String) = providerModelDelegate.deleteSingleModel(modelId)

    fun updateSingleModel(updatedBinding: ProviderModelBinding): Boolean =
        providerModelDelegate.updateSingleModel(updatedBinding)

    fun saveCompressionPolicy(
        targetType: CompressionPolicyTargetType,
        targetId: String,
        policy: ModelCompressionPolicy?
    ) = providerModelDelegate.saveCompressionPolicy(targetType, targetId, policy)

    fun clearActivityLogs() {
        ActivityRecorder.clear()
    }

    fun setActivityAutoScroll(enabled: Boolean) {
        configStore.updateConfig { it.copy(activityAutoScroll = enabled) }
    }

    fun testSingleModel(binding: ProviderModelBinding, provider: Provider) =
        providerModelDelegate.testSingleModel(binding, provider)

    fun testProviderModels(providerId: String) = providerModelDelegate.testProviderModels(providerId)

    fun updateLanguage(lang: AppLanguage) {
        I18nManager.currentLanguage = lang
        configStore.updateConfig { it.copy(language = lang.code) }
    }

    fun updateThemeMode(mode: String) {
        configStore.updateConfig { it.copy(themeMode = mode) }
    }

    fun updateThemePalette(palette: String) {
        configStore.updateConfig { it.copy(themePalette = palette) }
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
                    showNotice(
                        s.settingsPortRestartFailed(result.exceptionOrNull()?.message ?: s.commonUnknown),
                        NoticeKind.ERROR
                    )
                }
            } catch (error: Exception) {
                showNotice(s.settingsPortUpdateFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    fun checkForUpdates(isManual: Boolean = true) = updateDelegate.checkForUpdates(isManual)

    fun dismissUpdateDialog() = updateDelegate.dismissUpdateDialog()

    fun openUpdateDialog() = updateDelegate.openUpdateDialog()

    fun startDownloadUpdate(release: ReleaseInfo) = updateDelegate.startDownloadUpdate(release)

    fun cancelDownloadUpdate() = updateDelegate.cancelDownloadUpdate()

    fun installUpdate(file: java.io.File, exitCurrentApp: Boolean = true) =
        updateDelegate.installUpdate(file, exitCurrentApp)

    fun showDownloadedFileInFolder(file: java.io.File) = updateDelegate.showDownloadedFileInFolder(file)

    fun resetDownloadState() = updateDelegate.resetDownloadState()

    fun ignoreUpdateVersion(version: String) = updateDelegate.ignoreUpdateVersion(version)

    fun updateAutoCheckUpdate(enabled: Boolean) = updateDelegate.updateAutoCheckUpdate(enabled)

    fun updateIncludePrerelease(enabled: Boolean) = updateDelegate.updateIncludePrerelease(enabled)

    fun updateDeveloperMode(enabled: Boolean) {
        configStore.updateConfig { it.copy(developerMode = enabled) }
        showNotice(if (enabled) s.settingsDeveloperModeEnabled else s.settingsDeveloperModeDisabled, NoticeKind.INFO)
    }

    fun toggleDeveloperMode() {
        val current = configStore.currentConfig.developerMode
        updateDeveloperMode(!current)
    }

    // --- 账号管理与 OAuth 交互方法 ---

    fun submitManualOAuthCallback(callbackUrl: String): Boolean =
        accountDelegate.submitManualOAuthCallback(callbackUrl)

    fun cancelOAuthFlow() = accountDelegate.cancelOAuthFlow()

    fun startGoogleOAuthFlow(
        openBrowserDirectly: Boolean = true,
        onFinished: ((Boolean) -> Unit)? = null
    ) = accountDelegate.startGoogleOAuthFlow(openBrowserDirectly, onFinished)

    fun importAccountViaRefreshToken(token: String, onFinished: ((Boolean) -> Unit)? = null) =
        accountDelegate.importAccountViaRefreshToken(token, onFinished)

    fun switchAccount(
        targetAccount: AccountInfo,
        applyToIde: Boolean = true,
        applyToAppCli: Boolean = true,
        restartIde: Boolean = true,
        restartApp: Boolean = true
    ) = accountDelegate.switchAccount(targetAccount, applyToIde, applyToAppCli, restartIde, restartApp)

    fun setActiveAccount(idOrEmail: String) = accountDelegate.setActiveAccount(idOrEmail)

    fun updateQuotaRefreshConfig(enabled: Boolean, activeIntervalSec: Int, backgroundIntervalSec: Int) =
        accountDelegate.updateQuotaRefreshConfig(enabled, activeIntervalSec, backgroundIntervalSec)

    fun removeAccount(idOrEmail: String) = accountDelegate.removeAccount(idOrEmail)

    fun refreshAccountTokens(email: String) = accountDelegate.refreshAccountTokens(email)

    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    fun togglePrivacyMode() {
        _isPrivacyMode.value = !_isPrivacyMode.value
    }

    fun updateAccountNote(id: String, note: String?) = accountDelegate.updateAccountNote(id, note)

    fun togglePinAccount(id: String) = accountDelegate.togglePinAccount(id)

    fun cleanInvalidAccounts() = accountDelegate.cleanInvalidAccounts()

    fun exportAccountsJson(): String = accountDelegate.exportAccountsJson()

    fun importAccountsBatch(
        rawText: String,
        onFinished: ((successCount: Int, failedCount: Int) -> Unit)? = null
    ) = accountDelegate.importAccountsBatch(rawText, onFinished)

    fun refreshAllQuotas() = accountDelegate.refreshAllQuotas()

    fun refreshSingleAccountQuota(accountId: String) = accountDelegate.refreshSingleAccountQuota(accountId)

    override fun onCleared() {
        tokenRenewalManager.stop()
        quotaPoller.stop()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            proxyServer.stop()
        }
        super.onCleared()
    }
}
