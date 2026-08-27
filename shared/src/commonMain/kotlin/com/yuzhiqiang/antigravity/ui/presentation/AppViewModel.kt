package com.yuzhiqiang.antigravity.ui.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.NoticeState
import com.yuzhiqiang.antigravity.update.engine.UpdateChecker
import com.yuzhiqiang.antigravity.update.model.AppUpdateDownloadState
import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import com.yuzhiqiang.antigravity.update.model.UpdateState
import kotlinx.coroutines.Job
import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.*
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.services.auth.TokenRenewalManager
import kotlinx.coroutines.flow.*
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.services.quota.QuotaFetchService
import com.yuzhiqiang.antigravity.services.quota.QuotaPoller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import com.yuzhiqiang.antigravity.services.auth.HostAccountDetector
import com.yuzhiqiang.antigravity.services.auth.HotSwitchCoordinator
import com.yuzhiqiang.antigravity.services.auth.SmartSwitchCoordinator
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

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
        proxyPortProvider = { actualProxyPort.value }
    )
    val smartSwitchCoordinator =
        SmartSwitchCoordinator(accountStore, configStore, hotSwitchCoordinator) { quotaPoller.quotaSnapshots.value }
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


    val config: StateFlow<AppConfig> = configStore.configState
    val configLoadError: StateFlow<String?> = configStore.loadError
    val isProxyRunning: StateFlow<Boolean> = proxyServer.isRunning
    val actualProxyPort: StateFlow<Int> = proxyServer.actualPort
    val activityLogs: StateFlow<List<ActivityLog>> = ActivityRecorder.logs

    val accounts: StateFlow<List<AccountInfo>> = accountStore.accountsState
    val activeAccount: StateFlow<AccountInfo?> = accountStore.activeAccountState

    private val _appCliActiveEmail = MutableStateFlow<String?>(null)
    val appCliActiveEmail: StateFlow<String?> = _appCliActiveEmail.asStateFlow()

    // 兼容历史属性：统一指向 App & CLI 共享激活邮箱
    val appActiveEmail: StateFlow<String?> get() = appCliActiveEmail
    val cliActiveEmail: StateFlow<String?> get() = appCliActiveEmail

    private val _ideActiveEmail = MutableStateFlow<String?>(null)
    val ideActiveEmail: StateFlow<String?> = _ideActiveEmail.asStateFlow()

    val cliActiveAccount: StateFlow<AccountInfo?> = accountStore.activeAccountState
    val ideActiveAccount: StateFlow<AccountInfo?> = hotSwitchCoordinator.ideActiveAccount
    private val accountSwitchRequestActive = AtomicBoolean(false)
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

    private val _activeRelease = MutableStateFlow<ReleaseInfo?>(null)
    val activeRelease: StateFlow<ReleaseInfo?> = _activeRelease.asStateFlow()

    private val _downloadState = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()
    private var downloadJob: kotlinx.coroutines.Job? = null

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
            if (configStore.currentConfig.autoCheckUpdate) {
                checkForUpdates(isManual = false)
            }
        }
    }

    fun syncHostAccounts(): Job {
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val appCliProfile = HostAccountDetector.detectAppCliActiveProfile()
                val ideProfile = HostAccountDetector.detectIdeActiveProfile()

                // 统一收敛为 IDE 宿主应用与 App & CLI 宿主应用
                _appCliActiveEmail.value = appCliProfile?.email
                _ideActiveEmail.value = ideProfile?.email

                // 1. 自动同步 IDE 宿主账号至 Studio 账号列表
                if (ideProfile != null && ideProfile.email.isNotBlank()) {
                    val currentList = accountStore.currentAccounts()
                    val existing = currentList.firstOrNull { it.email.equals(ideProfile.email, ignoreCase = true) }
                    val tier = when {
                        ideProfile.tierText?.contains("ultra", ignoreCase = true) == true -> AccountTier.ULTRA
                        ideProfile.tierText?.contains("pro", ignoreCase = true) == true -> AccountTier.PRO
                        ideProfile.tierText?.contains("enterprise", ignoreCase = true) == true -> AccountTier.ENTERPRISE
                        else -> existing?.profile?.tier ?: AccountTier.FREE
                    }
                    val rt = existing?.tokens?.refreshToken?.takeIf { it.isNotBlank() }
                        ?: HostAccountDetector.findAvailableRefreshToken(ideProfile.email)
                        ?: ""

                    if (existing == null) {
                        val newAccount = AccountInfo(
                            id = "acc_${ideProfile.email.hashCode().toUInt().toString(16)}",
                            profile = AccountProfile(
                                email = ideProfile.email,
                                name = ideProfile.name,
                                avatarUrl = ideProfile.avatarUrl,
                                tier = tier
                            ),
                            tokens = OAuthTokens(
                                accessToken = "",
                                refreshToken = rt,
                                expiryTimestamp = System.currentTimeMillis() / 1000L + 3600L
                            ),
                            isActive = true,
                            status = AccountStatus.ACTIVE
                        )
                        accountStore.upsertAccount(newAccount)
                        if (rt.isNotBlank()) {
                            refreshSingleAccountQuota(newAccount.id)
                        }
                    } else {
                        val needsUpdate = (existing.profile.name != ideProfile.name && ideProfile.name != null) ||
                                (existing.profile.avatarUrl != ideProfile.avatarUrl && ideProfile.avatarUrl != null) ||
                                (existing.tokens.refreshToken.isBlank() && rt.isNotBlank())
                        if (needsUpdate) {
                            val updated = existing.copy(
                                profile = existing.profile.copy(
                                    name = ideProfile.name ?: existing.profile.name,
                                    avatarUrl = ideProfile.avatarUrl ?: existing.profile.avatarUrl,
                                    tier = tier
                                ),
                                tokens = if (existing.tokens.refreshToken.isBlank() && rt.isNotBlank()) {
                                    existing.tokens.copy(refreshToken = rt)
                                } else existing.tokens
                            )
                            accountStore.upsertAccount(updated)
                            if (rt.isNotBlank()) {
                                refreshSingleAccountQuota(updated.id)
                            }
                        }
                    }
                }

                // 2. 自动同步 App & CLI 宿主账号至 Studio 账号列表
                if (appCliProfile != null && appCliProfile.email.isNotBlank()) {
                    val currentList = accountStore.currentAccounts()
                    val existing = currentList.firstOrNull {
                        it.email.equals(appCliProfile.email, ignoreCase = true)
                    }
                    val rt = existing?.tokens?.refreshToken?.takeIf { it.isNotBlank() }
                        ?: HostAccountDetector.findAvailableRefreshToken(appCliProfile.email)
                        ?: ""

                    if (existing == null) {
                        val newAccount = AccountInfo(
                            id = "acc_${appCliProfile.email.hashCode().toUInt().toString(16)}",
                            profile = AccountProfile(
                                email = appCliProfile.email,
                                name = appCliProfile.name,
                                avatarUrl = appCliProfile.avatarUrl,
                                tier = AccountTier.PRO
                            ),
                            tokens = OAuthTokens(
                                accessToken = "",
                                refreshToken = rt,
                                expiryTimestamp = System.currentTimeMillis() / 1000L + 3600L
                            ),
                            isActive = true,
                            status = AccountStatus.ACTIVE
                        )
                        accountStore.upsertAccount(newAccount)
                        if (rt.isNotBlank()) {
                            refreshSingleAccountQuota(newAccount.id)
                        }
                    } else {
                        val needsUpdate = (existing.tokens.refreshToken.isBlank() && rt.isNotBlank()) ||
                                (existing.profile.name != appCliProfile.name && appCliProfile.name != null)
                        if (needsUpdate) {
                            val updated = existing.copy(
                                profile = existing.profile.copy(
                                    name = appCliProfile.name ?: existing.profile.name,
                                    avatarUrl = appCliProfile.avatarUrl ?: existing.profile.avatarUrl
                                ),
                                tokens = if (existing.tokens.refreshToken.isBlank() && rt.isNotBlank()) {
                                    existing.tokens.copy(refreshToken = rt)
                                } else existing.tokens
                            )
                            accountStore.upsertAccount(updated)
                            if (rt.isNotBlank()) {
                                refreshSingleAccountQuota(updated.id)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startHostAccountDetectionLoop() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (this.isActive) {
                try {
                    syncHostAccounts().join()
                    refreshHostStatus()
                } catch (_: Exception) {
                }
                // 启动事件无法跨平台可靠订阅，保留 10 秒心跳补齐外部启动发现。
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    /**
     * 窗口获得焦点时由 UI 层调用，立即刷新宿主账号与运行状态。
     *
     * 用户从其他应用切换回 Studio 时（例如在 App/IDE 中完成了切号操作），
     * 此方法确保 Studio 能立即感知到最新的运行态账号，而不必等待 10 秒心跳轮询。
     */
    fun onWindowFocusGained() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                syncHostAccounts().join()
                refreshHostStatus()
            } catch (_: Exception) {
            }
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
                current.copy(
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

    fun deleteProvider(providerId: String) {
        val current = configStore.currentConfig
        val targetProvider = current.providers.find { it.id == providerId } ?: return
        val removedUpstreams = current.upstreamModels.filter { it.providerId == providerId }
        val removedVirtualModels = current.virtualModels.filter {
            it.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
        }
        try {
            configStore.saveConfig(
                current.copy(
                    providers = current.providers.filterNot { it.id == providerId },
                    upstreamModels = current.upstreamModels.filterNot { it.providerId == providerId },
                    virtualModels = current.virtualModels.filterNot { virtual ->
                        virtual.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
                    },
                    modelCompressionPolicies = current.modelCompressionPolicies.filterKeys { key ->
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
        try {
            configStore.saveConfig(
                current.copy(
                    upstreamModels = current.upstreamModels.filterNot { it.id == modelId },
                    virtualModels = current.virtualModels.filterNot { it.upstreamModelId == modelId },
                    modelCompressionPolicies = current.modelCompressionPolicies - modelId
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
                current.copy(
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
                    val isVirtualMatch =
                        current.virtualModels.any { it.id == modelId && it.upstreamModelId == upstream.id }
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

    fun setActivityAutoScroll(enabled: Boolean) {
        configStore.updateConfig { it.copy(activityAutoScroll = enabled) }
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
                    s.modelsModelTestFailed(
                        model.displayName ?: model.upstreamModelId,
                        result.error ?: result.statusCode.toString()
                    ),
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

    fun checkForUpdates(isManual: Boolean = true) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _updateState.value = UpdateState.Checking(isManual)
            val result = UpdateChecker.checkUpdate(currentVersion = AppVersion.CURRENT)
            val now = System.currentTimeMillis()
            configStore.updateConfig { it.copy(lastCheckUpdateTimestamp = now) }

            result.fold(
                onSuccess = { release ->
                    if (release != null) {
                        _updateState.value = UpdateState.Available(
                            release = release,
                            currentVersion = AppVersion.CURRENT,
                            isManual = isManual
                        )
                        _activeRelease.value = release
                        val isIgnored =
                            configStore.currentConfig.ignoredVersion.equals(release.cleanVersion, ignoreCase = true)
                        if (isManual || !isIgnored) {
                            _showUpdateDialog.value = true
                        }
                        if (isManual) {
                            showNotice(s.updateAvailableTitle + ": v${release.cleanVersion}", NoticeKind.SUCCESS)
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate(
                            currentVersion = AppVersion.CURRENT,
                            lastCheckedTimestamp = now,
                            isManual = isManual
                        )
                        if (isManual) {
                            showNotice(s.updateUpToDate, NoticeKind.SUCCESS)
                        }
                    }
                },
                onFailure = { error ->
                    val msg = error.message ?: s.commonUnknown
                    _updateState.value = UpdateState.Error(msg, isManual)
                    if (isManual) {
                        showNotice(s.updateCheckFailed(msg), NoticeKind.ERROR)
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun openUpdateDialog() {
        if (_activeRelease.value != null) {
            _showUpdateDialog.value = true
        } else {
            checkForUpdates(isManual = true)
        }
    }

    fun startDownloadUpdate(release: ReleaseInfo) {
        val downloadUrl = release.resolvePlatformDownloadUrl()
        val filename = downloadUrl.substringAfterLast("/").takeIf { it.isNotBlank() && it.contains(".") }
            ?: "Antigravity-Studio-${release.cleanVersion}.dmg"
        val targetFile = com.yuzhiqiang.antigravity.update.engine.AppUpdateDownloader.resolveTargetFile(filename)

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _downloadState.value = AppUpdateDownloadState.Downloading(
                bytesDownloaded = 0L,
                totalBytes = -1L,
                progressRatio = 0f,
                speedBytesPerSec = 0L
            )
            try {
                com.yuzhiqiang.antigravity.update.engine.AppUpdateDownloader.download(downloadUrl, targetFile)
                    .collect { progress ->
                        when (progress) {
                            is com.yuzhiqiang.antigravity.update.engine.DownloadProgress.Progress -> {
                                _downloadState.value = AppUpdateDownloadState.Downloading(
                                    bytesDownloaded = progress.bytesDownloaded,
                                    totalBytes = progress.totalBytes,
                                    progressRatio = progress.progressRatio,
                                    speedBytesPerSec = progress.speedBytesPerSec
                                )
                            }

                            is com.yuzhiqiang.antigravity.update.engine.DownloadProgress.Completed -> {
                                _downloadState.value = AppUpdateDownloadState.Completed(progress.targetFile)
                                showNotice(s.updateDownloadCompleted, NoticeKind.SUCCESS)
                                // 下载完成自动预览挂载/打开，不强杀当前进程
                                installUpdate(progress.targetFile, exitCurrentApp = false)
                            }
                        }
                    }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                _downloadState.value = AppUpdateDownloadState.Idle
            } catch (e: Exception) {
                val errMsg = e.message ?: s.commonUnknown
                _downloadState.value = AppUpdateDownloadState.Failed(errMsg)
                showNotice(s.updateDownloadFailed(errMsg), NoticeKind.ERROR)
            }
        }
    }

    fun cancelDownloadUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = AppUpdateDownloadState.Idle
    }

    fun installUpdate(file: java.io.File, exitCurrentApp: Boolean = true) {
        viewModelScope.launch {
            val result = com.yuzhiqiang.antigravity.update.engine.AppUpdateInstaller.launchInstaller(
                file = file,
                exitCurrentApp = exitCurrentApp
            )
            result.onFailure { error ->
                showNotice(s.updateDownloadFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    fun showDownloadedFileInFolder(file: java.io.File) {
        viewModelScope.launch {
            com.yuzhiqiang.antigravity.update.engine.AppUpdateInstaller.showInFolder(file)
        }
    }

    fun resetDownloadState() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = AppUpdateDownloadState.Idle
    }

    fun ignoreUpdateVersion(version: String) {
        configStore.updateConfig { it.copy(ignoredVersion = version) }
        _showUpdateDialog.value = false
        showNotice(s.updateIgnoredNotice, NoticeKind.INFO)
    }

    fun updateAutoCheckUpdate(enabled: Boolean) {
        configStore.updateConfig { it.copy(autoCheckUpdate = enabled) }
    }

    fun updateIncludePrerelease(enabled: Boolean) {
        configStore.updateConfig { it.copy(includePrerelease = enabled) }
    }

    fun updateDeveloperMode(enabled: Boolean) {
        configStore.updateConfig { it.copy(developerMode = enabled) }
        showNotice(if (enabled) s.settingsDeveloperModeEnabled else s.settingsDeveloperModeDisabled, NoticeKind.INFO)
    }

    fun toggleDeveloperMode() {
        val current = configStore.currentConfig.developerMode
        updateDeveloperMode(!current)
    }

    // --- 账号管理与 OAuth 交互方法 ---

    fun submitManualOAuthCallback(callbackUrl: String): Boolean {
        return googleAuthService.submitManualCallback(callbackUrl)
    }

    fun cancelOAuthFlow() {
        googleAuthService.cancelOAuthFlow()
        _isOAuthAuthorizing.value = false
        _oauthAuthUrl.value = null
    }

    fun startGoogleOAuthFlow(
        openBrowserDirectly: Boolean = true,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        if (_isOAuthAuthorizing.value) {
            val url = _oauthAuthUrl.value
            if (!url.isNullOrBlank()) {
                if (openBrowserDirectly) {
                    googleAuthService.openBrowser(url)
                } else {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection(url),
                        null
                    )
                    showNotice(s.noticeAuthLinkCopied, NoticeKind.SUCCESS)
                }
            }
            return
        }
        _isOAuthAuthorizing.value = true
        _oauthAuthUrl.value = null

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = googleAuthService.startOAuthFlow(openBrowserDirectly = openBrowserDirectly) { authUrl ->
                _oauthAuthUrl.value = authUrl
                if (!openBrowserDirectly) {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection(authUrl),
                        null
                    )
                    showNotice(s.noticeAuthLinkCopiedBrowser, NoticeKind.SUCCESS)
                }
            }
            _isOAuthAuthorizing.value = false
            _oauthAuthUrl.value = null

            result.fold(
                onSuccess = { account ->
                    accountStore.upsertAccount(account)
                    showNotice(s.accountsAuthSuccess, NoticeKind.SUCCESS)
                    onFinished?.invoke(true)
                },
                onFailure = { error ->
                    showNotice("${s.accountsAuthFailed}: ${error.message ?: s.commonUnknown}", NoticeKind.ERROR)
                    onFinished?.invoke(false)
                }
            )
        }
    }

    fun importAccountViaRefreshToken(token: String, onFinished: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = googleAuthService.importViaRefreshToken(token)
            result.fold(
                onSuccess = { account ->
                    accountStore.upsertAccount(account)
                    showNotice(s.accountsAuthSuccess, NoticeKind.SUCCESS)
                    onFinished?.invoke(true)
                },
                onFailure = { error ->
                    showNotice("${s.accountsAuthFailed}: ${error.message ?: s.commonUnknown}", NoticeKind.ERROR)
                    onFinished?.invoke(false)
                }
            )
        }
    }

    fun switchAccount(
        targetAccount: AccountInfo,
        applyToIde: Boolean = true,
        applyToAppCli: Boolean = true,
        restartIde: Boolean = true,
        restartApp: Boolean = true
    ) {
        if (!accountSwitchRequestActive.compareAndSet(false, true)) {
            showNotice(s.noticeSwitchAlreadyRunning, NoticeKind.WARNING)
            return
        }
        _isAccountSwitching.value = true

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = hotSwitchCoordinator.switchAccountWithRestart(
                    targetAccount = targetAccount,
                    applyToIde = applyToIde,
                    applyToAppCli = applyToAppCli,
                    restartIde = restartIde,
                    restartApp = restartApp
                )
                result.fold(
                    onSuccess = { report ->
                        syncHostAccounts().join()
                        refreshHostStatus()

                        val statuses = listOfNotNull(
                            formatSwitchTarget("IDE", report.ide),
                            formatSwitchTarget("App & CLI", report.appCli)
                        )
                        val summary = statuses.joinToString("，")
                        val noticeKind = when (report.overallStatus) {
                            HotSwitchCoordinator.OverallStatus.SUCCESS -> NoticeKind.SUCCESS
                            HotSwitchCoordinator.OverallStatus.WARNING -> NoticeKind.WARNING
                            HotSwitchCoordinator.OverallStatus.ERROR -> NoticeKind.ERROR
                        }
                        showNotice(s.noticeSwitchResult(summary), noticeKind)
                        if (report.overallStatus == HotSwitchCoordinator.OverallStatus.SUCCESS) {
                            quotaPoller.refreshSingle(targetAccount, true)
                        }
                    },
                    onFailure = { error ->
                        syncHostAccounts().join()
                        refreshHostStatus()
                        showNotice(s.noticeSwitchFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
                    }
                )
            } finally {
                _isAccountSwitching.value = false
                accountSwitchRequestActive.set(false)
            }
        }
    }

    private fun formatSwitchTarget(
        label: String,
        result: HotSwitchCoordinator.TargetResult
    ): String? {
        return when (result.status) {
            HotSwitchCoordinator.TargetStatus.NOT_REQUESTED -> null
            HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE -> result.message ?: s.switchStatusNotAvailable(label)
            HotSwitchCoordinator.TargetStatus.CONFIGURED -> result.message ?: s.switchStatusConfigured(label)
            HotSwitchCoordinator.TargetStatus.CONFIRMED -> result.message ?: s.switchStatusConfirmed(label)
            HotSwitchCoordinator.TargetStatus.PENDING_RESTART -> s.switchStatusPendingRestart(label)
            HotSwitchCoordinator.TargetStatus.FAILED -> result.message ?: s.switchStatusFailed(label)
        }
    }

    fun setActiveAccount(idOrEmail: String) {
        val target = accountStore.currentAccounts()
            .firstOrNull { it.id == idOrEmail || it.email.equals(idOrEmail, ignoreCase = true) }
        if (target == null) {
            showNotice(s.noticeAccountNotFound(idOrEmail), NoticeKind.ERROR)
            return
        }
        switchAccount(
            target,
            restartIde = true,
            restartApp = true
        )
    }

    fun updateQuotaRefreshConfig(enabled: Boolean, activeIntervalSec: Int, backgroundIntervalSec: Int) {
        configStore.updateConfig {
            it.copy(
                quotaAutoRefreshEnabled = enabled,
                quotaActiveIntervalSeconds = activeIntervalSec,
                quotaBackgroundIntervalSeconds = backgroundIntervalSec
            )
        }
        showNotice(if (enabled) s.noticeQuotaAutoRefreshEnabled else s.noticeQuotaAutoRefreshDisabled, NoticeKind.INFO)
    }


    fun removeAccount(idOrEmail: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            accountStore.removeAccount(idOrEmail)
            showNotice(s.noticeAccountRemoved, NoticeKind.INFO)
        }
    }

    fun refreshAccountTokens(email: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = tokenRenewalManager.refreshAccount(email)
            result.fold(
                onSuccess = { showNotice(s.noticeTokenRefreshed, NoticeKind.SUCCESS) },
                onFailure = { showNotice(s.noticeTokenRefreshFailed(it.message ?: s.commonUnknown), NoticeKind.ERROR) }
            )
        }
    }

    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    fun togglePrivacyMode() {
        _isPrivacyMode.value = !_isPrivacyMode.value
    }

    fun updateAccountNote(id: String, note: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            accountStore.updateAccountNote(id, note)
            showNotice(s.noticeRemarkUpdated, NoticeKind.SUCCESS)
        }
    }

    fun togglePinAccount(id: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            accountStore.togglePinAccount(id)
        }
    }

    fun cleanInvalidAccounts() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = accountStore.cleanInvalidAccounts()
            result.fold(
                onSuccess = { count -> showNotice(s.noticeCleanAccountsSuccess(count), NoticeKind.SUCCESS) },
                onFailure = { showNotice(s.noticeCleanAccountsFailed(it.message ?: s.commonUnknown), NoticeKind.ERROR) }
            )
        }
    }

    fun exportAccountsJson(): String {
        return accountStore.exportAccountsJson()
    }

    fun importAccountsBatch(rawText: String, onFinished: ((successCount: Int, failedCount: Int) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val results = googleAuthService.importBatch(rawText)
            var successCount = 0
            var failedCount = 0
            for (res in results) {
                res.fold(
                    onSuccess = { acc ->
                        accountStore.upsertAccount(acc)
                        successCount++
                    },
                    onFailure = {
                        failedCount++
                    }
                )
            }
            if (successCount > 0 && failedCount == 0) {
                showNotice(s.noticeBatchImportSuccess(successCount), NoticeKind.SUCCESS)
                quotaPoller.refreshAllNow(accountStore.currentAccounts(), accountStore.currentActiveAccount())
            } else if (successCount > 0 && failedCount > 0) {
                showNotice(s.noticeBatchImportPartial(successCount, failedCount), NoticeKind.SUCCESS)
                quotaPoller.refreshAllNow(accountStore.currentAccounts(), accountStore.currentActiveAccount())
            } else if (failedCount > 0) {
                showNotice(s.noticeBatchImportFailedAll(failedCount), NoticeKind.ERROR)
            }
            onFinished?.invoke(successCount, failedCount)
        }
    }


    fun refreshAllQuotas() {

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = quotaPoller.refreshAllNow(accountStore.currentAccounts(), accountStore.currentActiveAccount())
            result.fold(
                onSuccess = { showNotice(s.noticeQuotasUpdatedAll, NoticeKind.SUCCESS) },
                onFailure = { showNotice(s.noticeQuotasUpdateFailedAll(it.message ?: s.commonUnknown), NoticeKind.ERROR) }
            )
        }
    }

    fun refreshSingleAccountQuota(accountId: String) {
        val account = accountStore.currentAccounts().firstOrNull { it.id == accountId } ?: return
        val isActive = account.id == accountStore.currentActiveAccount()?.id
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = quotaPoller.refreshSingle(account, isActive)
            result.fold(
                onSuccess = { showNotice(s.noticeQuotaRefreshedSingle, NoticeKind.SUCCESS) },
                onFailure = { showNotice(s.noticeQuotaRefreshFailedSingle(it.message ?: s.commonError), NoticeKind.ERROR) }
            )
        }
    }

    override fun onCleared() {
        tokenRenewalManager.stop()
        quotaPoller.stop()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            proxyServer.stop()
        }
        super.onCleared()
    }
}
