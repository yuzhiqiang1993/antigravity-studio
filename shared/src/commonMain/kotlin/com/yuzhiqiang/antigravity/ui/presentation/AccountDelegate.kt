package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore

import com.yuzhiqiang.antigravity.domain.model.account.*
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.services.auth.HotSwitchCoordinator
import com.yuzhiqiang.antigravity.services.auth.HostAccountDetector
import com.yuzhiqiang.antigravity.services.auth.TokenRenewalManager
import com.yuzhiqiang.antigravity.services.quota.QuotaPoller
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 负责 Studio 账号与外部宿主账号之间的同步、认证和切换协调。
 *
 * StateFlow 由 AppViewModel 持有并注入，保证现有 UI 继续观察原有状态实例。
 */
class AccountDelegate(
    private val scope: CoroutineScope,
    private val configStore: ConfigStore,
    private val accountStore: AccountStore,
    private val googleAuthService: GoogleAuthService,
    private val hotSwitchCoordinator: HotSwitchCoordinator,
    private val tokenRenewalManager: TokenRenewalManager,
    private val quotaPoller: QuotaPoller,
    private val appCliActiveEmailFlow: MutableStateFlow<String?>,
    private val ideActiveEmailFlow: MutableStateFlow<String?>,
    private val isAccountSwitchingFlow: MutableStateFlow<Boolean>,
    private val isOAuthAuthorizingFlow: MutableStateFlow<Boolean>,
    private val oauthAuthUrlFlow: MutableStateFlow<String?>,
    private val showNotice: (String, NoticeKind) -> Unit,
    private val onRefreshHostStatus: () -> Unit,
    private val onFetchOfficialModels: () -> Job
) {

    private val accountSwitchRequestActive = AtomicBoolean(false)
    private val s get() = com.yuzhiqiang.antigravity.i18n.I18nManager.strings

    fun syncHostAccounts(): Job {
        return scope.launch(Dispatchers.IO) {
            try {
                val customHostPaths = configStore.currentConfig.customHostPaths
                val appCliProbes = HostAccountDetector.detectAppCliAccountProbes(
                    credentialsFile = accountStore.officialCredentialsFile(),
                    installationPath = customHostPaths["app"]
                )
                val ideProbes = HostAccountDetector.detectIdeAccountProbes(customHostPaths["ide"])
                val appCliRuntimeProfile = appCliProbes.firstOrNull { probe ->
                    probe.source == HostAccountDetector.AccountProbeSource.RUNTIME_API
                }?.profile
                val ideRuntimeProfile = ideProbes.firstOrNull { probe ->
                    probe.source == HostAccountDetector.AccountProbeSource.RUNTIME_API
                }?.profile
                val appCliProfile = appCliRuntimeProfile ?: appCliProbes.firstOrNull { probe ->
                    probe.source == HostAccountDetector.AccountProbeSource.SHARED_CREDENTIALS
                }?.profile
                val ideProfile = ideRuntimeProfile ?: ideProbes.firstOrNull { probe ->
                    probe.source == HostAccountDetector.AccountProbeSource.STATE_DB
                }?.profile

                // 活跃账号标签只接受运行态证据；静态配置仍可用于账号资料导入。
                val prevAppCliEmail = appCliActiveEmailFlow.value
                val prevIdeEmail = ideActiveEmailFlow.value

                appCliActiveEmailFlow.value = appCliRuntimeProfile?.email
                ideActiveEmailFlow.value = ideRuntimeProfile?.email

                val activeChanged = (prevAppCliEmail != appCliActiveEmailFlow.value) ||
                        (prevIdeEmail != ideActiveEmailFlow.value)

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
                            isActive = ideRuntimeProfile != null,
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
                            isActive = appCliRuntimeProfile != null,
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

                if (activeChanged) {
                    quotaPoller.refreshActiveAccountsNow()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun startHostAccountDetectionLoop() {
        scope.launch(Dispatchers.IO) {
            while (this.isActive) {
                try {
                    syncHostAccounts().join()
                    onRefreshHostStatus()
                } catch (_: Exception) {
                }
                // 启动事件无法跨平台可靠订阅，保留 10 秒心跳补齐外部启动发现。
                delay(10000)
            }
        }
    }

    /**
     * 窗口获得焦点时由 UI 层调用，立即刷新宿主账号与运行状态。
     *
     * 用户从其他应用切换回 Studio 时（例如在 App/IDE 中完成了切号操作），
     * 此方法确保 Studio 能立即感知到最新的运行态账号，并立即发起活跃账号配额刷新。
     */
    fun onWindowFocusGained() {
        scope.launch(Dispatchers.IO) {
            try {
                syncHostAccounts().join()
                onRefreshHostStatus()
                quotaPoller.refreshActiveAccountsNow()
            } catch (_: Exception) {
            }
        }
    }

    fun submitManualOAuthCallback(callbackUrl: String): Boolean {
        return googleAuthService.submitManualCallback(callbackUrl)
    }

    fun cancelOAuthFlow() {
        googleAuthService.cancelOAuthFlow()
        isOAuthAuthorizingFlow.value = false
        oauthAuthUrlFlow.value = null
    }

    fun startGoogleOAuthFlow(
        openBrowserDirectly: Boolean = true,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        if (isOAuthAuthorizingFlow.value) {
            val url = oauthAuthUrlFlow.value
            if (!url.isNullOrBlank()) {
                if (openBrowserDirectly) {
                    googleAuthService.openBrowser(url)
                } else if (copyToClipboard(url)) {
                    showNotice(s.noticeAuthLinkCopied, NoticeKind.SUCCESS)
                }
            }
            return
        }
        isOAuthAuthorizingFlow.value = true
        oauthAuthUrlFlow.value = null

        scope.launch(Dispatchers.IO) {
            val result = googleAuthService.startOAuthFlow(openBrowserDirectly = openBrowserDirectly) { authUrl ->
                oauthAuthUrlFlow.value = authUrl
                if (!openBrowserDirectly && copyToClipboard(authUrl)) {
                    showNotice(s.noticeAuthLinkCopiedBrowser, NoticeKind.SUCCESS)
                }
            }
            isOAuthAuthorizingFlow.value = false
            oauthAuthUrlFlow.value = null

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
        scope.launch(Dispatchers.IO) {
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
        isAccountSwitchingFlow.value = true

        scope.launch(Dispatchers.IO) {
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
                        onRefreshHostStatus()

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
                        if (report.appCli.isApplied ||
                            report.appCli.status == HotSwitchCoordinator.TargetStatus.PENDING_RESTART
                        ) {
                            quotaPoller.refreshSingle(report.appliedAccount, true)
                            onFetchOfficialModels()
                        }
                    },
                    onFailure = { error ->
                        syncHostAccounts().join()
                        onRefreshHostStatus()
                        showNotice(s.noticeSwitchFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
                    }
                )
            } finally {
                isAccountSwitchingFlow.value = false
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
        quotaPoller.restart()
        showNotice(if (enabled) s.noticeQuotaAutoRefreshEnabled else s.noticeQuotaAutoRefreshDisabled, NoticeKind.INFO)
    }

    fun removeAccount(idOrEmail: String) {
        scope.launch(Dispatchers.IO) {
            accountStore.removeAccount(idOrEmail)
            showNotice(s.noticeAccountRemoved, NoticeKind.INFO)
        }
    }

    fun refreshAccountTokens(email: String) {
        scope.launch(Dispatchers.IO) {
            val result = tokenRenewalManager.refreshAccount(email)
            result.fold(
                onSuccess = { showNotice(s.noticeTokenRefreshed, NoticeKind.SUCCESS) },
                onFailure = { showNotice(s.noticeTokenRefreshFailed(it.message ?: s.commonUnknown), NoticeKind.ERROR) }
            )
        }
    }

    fun updateAccountNote(id: String, note: String?) {
        scope.launch(Dispatchers.IO) {
            accountStore.updateAccountNote(id, note)
            showNotice(s.noticeRemarkUpdated, NoticeKind.SUCCESS)
        }
    }

    fun togglePinAccount(id: String) {
        scope.launch(Dispatchers.IO) {
            accountStore.togglePinAccount(id)
        }
    }

    fun cleanInvalidAccounts() {
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
                quotaPoller.refreshAllNow(accountStore.currentAccounts(), currentActiveAccounts())
            } else if (successCount > 0 && failedCount > 0) {
                showNotice(s.noticeBatchImportPartial(successCount, failedCount), NoticeKind.SUCCESS)
                quotaPoller.refreshAllNow(accountStore.currentAccounts(), currentActiveAccounts())
            } else if (failedCount > 0) {
                showNotice(s.noticeBatchImportFailedAll(failedCount), NoticeKind.ERROR)
            }
            onFinished?.invoke(successCount, failedCount)
        }
    }

    fun currentActiveAccounts(): List<AccountInfo> {
        val allAccounts = accountStore.currentAccounts()
        val ideEmail = ideActiveEmailFlow.value
        val appCliEmail = appCliActiveEmailFlow.value
        val defaultActive = accountStore.currentActiveAccount()

        val list = mutableListOf<AccountInfo>()
        if (!ideEmail.isNullOrBlank()) {
            allAccounts.firstOrNull { it.email.equals(ideEmail, ignoreCase = true) }?.let { list.add(it) }
        }
        if (!appCliEmail.isNullOrBlank()) {
            allAccounts.firstOrNull { it.email.equals(appCliEmail, ignoreCase = true) }?.let { list.add(it) }
        }
        if (list.isEmpty() && defaultActive != null) {
            list.add(defaultActive)
        }
        return list.distinctBy { it.id }
    }

    fun refreshAllQuotas() {
        scope.launch(Dispatchers.IO) {
            val result = quotaPoller.refreshAllNow(accountStore.currentAccounts(), currentActiveAccounts())
            result.fold(
                onSuccess = { showNotice(s.noticeQuotasUpdatedAll, NoticeKind.SUCCESS) },
                onFailure = {
                    showNotice(
                        s.noticeQuotasUpdateFailedAll(it.message ?: s.commonUnknown),
                        NoticeKind.ERROR
                    )
                }
            )
        }
    }

    fun refreshSingleAccountQuota(accountId: String) {
        val account = accountStore.currentAccounts().firstOrNull { it.id == accountId } ?: return
        val activeIds = currentActiveAccounts().map { it.id }.toSet()
        val isActive = account.id in activeIds
        scope.launch(Dispatchers.IO) {
            val result = quotaPoller.refreshSingle(account, isActive)
            result.fold(
                onSuccess = { showNotice(s.noticeQuotaRefreshedSingle, NoticeKind.SUCCESS) },
                onFailure = {
                    showNotice(
                        s.noticeQuotaRefreshFailedSingle(it.message ?: s.commonError),
                        NoticeKind.ERROR
                    )
                }
            )
        }
    }
}
