package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * 管理账号切换互斥状态，并将单次宿主切换交给 [AccountSwitchSession] 执行。
 */
class HotSwitchCoordinator(
    private val accountStore: AccountStore,
    private val customHostPathsProvider: () -> Map<String, String?> = { emptyMap() },
    private val proxyPortProvider: () -> Int? = { null }
) {
    private val switchMutex = Mutex()

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    private val _ideActiveAccount = MutableStateFlow<AccountInfo?>(null)
    val ideActiveAccount: StateFlow<AccountInfo?> = _ideActiveAccount.asStateFlow()

    init {
        _ideActiveAccount.value = accountStore.currentActiveAccount()
    }

    enum class TargetStatus {
        NOT_REQUESTED,
        NOT_AVAILABLE,
        CONFIGURED,
        CONFIRMED,
        PENDING_RESTART,
        FAILED
    }

    enum class OverallStatus {
        SUCCESS,
        WARNING,
        ERROR
    }

    data class TargetResult(
        val status: TargetStatus,
        val actualEmail: String? = null,
        val message: String? = null
    ) {
        val isConfirmed: Boolean
            get() = status == TargetStatus.CONFIRMED
        val isApplied: Boolean
            get() = status == TargetStatus.CONFIRMED || status == TargetStatus.CONFIGURED
    }

    data class SwitchResultReport(
        val targetEmail: String,
        val ide: TargetResult,
        val appCli: TargetResult,
        val ideWasRunning: Boolean,
        val appWasRunning: Boolean
    ) {
        val overallStatus: OverallStatus
            get() {
                val statuses = listOf(ide.status, appCli.status)
                    .filter { it != TargetStatus.NOT_REQUESTED }
                return when {
                    statuses.any { it == TargetStatus.FAILED } -> OverallStatus.ERROR
                    statuses.any {
                        it == TargetStatus.CONFIGURED ||
                                it == TargetStatus.PENDING_RESTART ||
                                it == TargetStatus.NOT_AVAILABLE
                    } -> OverallStatus.WARNING

                    else -> OverallStatus.SUCCESS
                }
            }

        val ideConfirmed: Boolean get() = ide.isConfirmed
        val appCliConfirmed: Boolean get() = appCli.isConfirmed
        val actualIdeEmail: String? get() = ide.actualEmail
        val actualAppCliEmail: String? get() = appCli.actualEmail
    }

    /**
     * 执行一次分目标账号切换；已有任务执行时立即拒绝新请求。
     */
    suspend fun switchAccountWithRestart(
        targetAccount: AccountInfo,
        applyToIde: Boolean = true,
        applyToAppCli: Boolean = true,
        restartIde: Boolean = true,
        restartApp: Boolean = true,
        progressCallback: ((phase: String) -> Unit)? = null
    ): Result<SwitchResultReport> {
        if (!switchMutex.tryLock()) {
            return Result.failure(IllegalStateException("已有账号切换任务正在执行，请稍后再试"))
        }

        _isSwitching.value = true
        return try {
            val paths = customHostPathsProvider()
            val request = AccountSwitchSession.Request(
                targetAccount = targetAccount,
                applyToIde = applyToIde,
                applyToAppCli = applyToAppCli,
                restartIde = restartIde,
                restartApp = restartApp,
                ideInstallationPath = paths["ide"],
                appInstallationPath = paths["app"],
                proxyPort = proxyPortProvider(),
                progressCallback = progressCallback
            )
            val result = withContext(Dispatchers.IO) {
                AccountSwitchSession(accountStore).execute(request)
            }
            result.onSuccess { report ->
                if (report.ide.isApplied) {
                    _ideActiveAccount.value = targetAccount
                }
            }
        } finally {
            _isSwitching.value = false
            switchMutex.unlock()
        }
    }

    /**
     * 兼容原有全局切号入口；运行中的宿主会采用可靠的重启流程。
     */
    suspend fun switchAccount(
        targetAccount: AccountInfo,
        progressCallback: ((phase: String) -> Unit)? = null
    ): Result<Unit> {
        val paths = customHostPathsProvider()
        return switchAccountWithRestart(
            targetAccount = targetAccount,
            restartIde = IdeHostManager.isRunning(paths["ide"]),
            restartApp = AppHostManager.isRunning(paths["app"]),
            progressCallback = progressCallback
        ).fold(
            onSuccess = { report ->
                if (report.overallStatus == OverallStatus.SUCCESS) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException(buildReportMessage(report)))
                }
            },
            onFailure = { error -> Result.failure(error) }
        )
    }

    /**
     * 仅切换 IDE；运行中但不允许重启时返回待重启失败。
     */
    suspend fun switchIdeOnly(
        targetAccount: AccountInfo,
        restartIde: Boolean = true,
        progressCallback: ((phase: String) -> Unit)? = null
    ): Result<Unit> {
        return switchAccountWithRestart(
            targetAccount = targetAccount,
            applyToIde = true,
            applyToAppCli = false,
            restartIde = restartIde,
            restartApp = false,
            progressCallback = progressCallback
        ).fold(
            onSuccess = { report ->
                if (report.ide.isApplied) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException(report.ide.message ?: "IDE 账号尚未生效"))
                }
            },
            onFailure = { error -> Result.failure(error) }
        )
    }

    private fun buildReportMessage(report: SwitchResultReport): String {
        return listOf(report.ide, report.appCli)
            .mapNotNull { result -> result.message }
            .ifEmpty { listOf("账号尚未在所有目标宿主生效") }
            .joinToString("；")
    }
}
