package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 负责账号切换异常或取消时的回滚与宿主状态恢复。
 */
internal class AccountSwitchRollbackHandler(
    private val accountStore: AccountStore
) {
    suspend fun rollbackNonCancellable(
        request: AccountSwitchSession.Request,
        originalState: OriginalState,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): List<String> {
        return withContext(NonCancellable) {
            rollback(request, originalState, ideWasRunning, appWasRunning, changes)
        }
    }

    private suspend fun rollback(
        request: AccountSwitchSession.Request,
        originalState: OriginalState,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): List<String> {
        val errors = mutableListOf<String>()
        val canRestoreIde = stopLaunchedIdeForRollback(request, changes, errors)
        val canRestoreApp = stopLaunchedAppForRollback(request, changes, errors)

        if (canRestoreIde) {
            restoreIde(originalState, changes, errors)
        }
        if (canRestoreApp) {
            restoreApp(originalState, changes, errors)
        }
        restoreSharedCredentialsAndStudio(originalState, changes, errors)

        ensureOriginalHostsRunning(request, ideWasRunning, appWasRunning, errors)
        return errors
    }

    private suspend fun stopLaunchedIdeForRollback(
        request: AccountSwitchSession.Request,
        changes: AppliedChanges,
        errors: MutableList<String>
    ): Boolean {
        if (!changes.ideLaunchAttempted || !IdeHostManager.isRunning(request.ideInstallationPath)) {
            return true
        }
        if (IdeHostManager.terminate(request.ideInstallationPath)) {
            return true
        }
        errors.add("无法停止已重新启动的 IDE")
        return false
    }

    private suspend fun stopLaunchedAppForRollback(
        request: AccountSwitchSession.Request,
        changes: AppliedChanges,
        errors: MutableList<String>
    ): Boolean {
        if (!changes.appLaunchAttempted || !AppHostManager.isRunning(request.appInstallationPath)) {
            return true
        }
        if (AppHostManager.terminate(request.appInstallationPath)) {
            return true
        }
        errors.add("无法停止已重新启动的 App")
        return false
    }

    private fun restoreIde(
        originalState: OriginalState,
        changes: AppliedChanges,
        errors: MutableList<String>
    ) {
        if (!changes.ideDbWritten) {
            return
        }
        val snapshot = originalState.ideSnapshot
        if (snapshot == null || !StateDbInjector.restore(snapshot)) {
            errors.add("IDE 原账号恢复失败")
        }
    }

    private fun restoreApp(
        originalState: OriginalState,
        changes: AppliedChanges,
        errors: MutableList<String>
    ) {
        if (changes.appDbWritten) {
            val snapshot = originalState.appDbSnapshot
            if (snapshot == null || !StateDbInjector.restore(snapshot)) {
                errors.add("App 原账号数据库恢复失败")
            }
        }

        if (changes.jetskiTokenWriteAttempted) {
            val snapshot = originalState.jetskiTokenSnapshot
            if (snapshot == null || !AccountSwitchCredentialApplier.restoreFileSnapshot(snapshot)) {
                errors.add("App 原 jetski 凭据文件恢复失败")
            }
        }
        if (changes.appOauthFileWriteAttempted) {
            val snapshot = originalState.appOauthFileSnapshot
            if (snapshot == null || !AccountSwitchCredentialApplier.restoreFileSnapshot(snapshot)) {
                errors.add("App 原 OAuth 凭据文件恢复失败")
            }
        }
    }

    private suspend fun restoreSharedCredentialsAndStudio(
        originalState: OriginalState,
        changes: AppliedChanges,
        errors: MutableList<String>
    ) {
        if (changes.sharedCredentialsWritten) {
            val snapshot = originalState.sharedCredentialsSnapshot
            if (snapshot == null || !accountStore.restoreOfficialCredentialsSnapshot(snapshot)) {
                errors.add("App & CLI 共享凭据恢复失败")
            }
        }
        if (changes.studioAccountChanged && originalState.studioAccount != null &&
            accountStore.setActiveAccount(originalState.studioAccount.id).isFailure
        ) {
            errors.add("Studio 活跃账号恢复失败")
        }
    }

    private suspend fun ensureOriginalHostsRunning(
        request: AccountSwitchSession.Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        errors: MutableList<String>
    ) {
        if (ideWasRunning && !IdeHostManager.isRunning(request.ideInstallationPath)) {
            val launched = IdeHostManager.launch(request.ideInstallationPath) &&
                    waitUntilRunning { IdeHostManager.isRunning(request.ideInstallationPath) }
            if (!launched) {
                errors.add("IDE 恢复启动失败")
            }
        }
        if (appWasRunning && !AppHostManager.isRunning(request.appInstallationPath)) {
            val launched = AppHostManager.launch(request.appInstallationPath, request.proxyPort) &&
                    waitUntilRunning { AppHostManager.isRunning(request.appInstallationPath) }
            if (!launched) {
                errors.add("App 恢复启动失败")
            }
        }
    }

    private suspend fun waitUntilRunning(isRunning: () -> Boolean): Boolean {
        var waitedMillis = 0L
        while (waitedMillis < HOST_START_TIMEOUT_MS) {
            if (isRunning()) {
                return true
            }
            delay(HOST_START_INTERVAL_MS)
            waitedMillis += HOST_START_INTERVAL_MS
        }
        return isRunning()
    }

    companion object {
        private const val HOST_START_TIMEOUT_MS = 10_000L
        private const val HOST_START_INTERVAL_MS = 250L
    }
}
