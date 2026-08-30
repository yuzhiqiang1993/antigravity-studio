package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 执行单次账号切换事务，负责宿主启停、共享凭据写入、运行态确认和失败回滚。
 */
internal class AccountSwitchSession(
    private val accountStore: AccountStore,
    private val googleAuthService: GoogleAuthService = GoogleAuthService(),
    private val systemCredentialStore: SystemCredentialStore = SystemCredentialInjector
) {
    private val credentialApplier = AccountSwitchCredentialApplier(
        accountStore,
        googleAuthService::refreshAccessToken,
        systemCredentialStore
    )
    private val verifier = AccountSwitchVerifier(accountStore)
    private val rollbackHandler = AccountSwitchRollbackHandler(accountStore, systemCredentialStore)

    private fun log(stage: String, message: String) {
        AppLog.i("Auth/Switch") { "[$stage] $message" }
    }

    data class Request(
        val targetAccount: AccountInfo,
        val applyToIde: Boolean,
        val applyToAppCli: Boolean,
        val restartIde: Boolean,
        val restartApp: Boolean,
        val ideInstallationPath: String?,
        val appInstallationPath: String?,
        val proxyPort: Int?,
        val progressCallback: ((phase: String) -> Unit)?
    )

    suspend fun execute(request: Request): Result<HotSwitchCoordinator.SwitchResultReport> {
        if (!request.applyToIde && !request.applyToAppCli) {
            return Result.failure(IllegalArgumentException("至少选择一个账号生效目标"))
        }

        val preparedRequest = try {
            request.copy(targetAccount = credentialApplier.prepareTargetAccount(request))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(IllegalStateException("目标账号 Token 预处理失败", error))
        }

        val ideWasRunning = IdeHostManager.isRunning(preparedRequest.ideInstallationPath)
        val appWasRunning = AppHostManager.isRunning(preparedRequest.appInstallationPath)
        val originalState = try {
            credentialApplier.captureOriginalState(preparedRequest)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(IllegalStateException("切号前物理状态快照失败", error))
        }
        val changes = AppliedChanges()
        return try {
            stopRequestedHosts(preparedRequest, ideWasRunning, appWasRunning, changes)
            val updatedTargetAccount = credentialApplier.applyCredentials(
                request = preparedRequest,
                ideWasRunning = ideWasRunning,
                appWasRunning = appWasRunning,
                changes = changes,
                sharedCredentialsSnapshot = originalState.sharedCredentialsSnapshot
            )
            val activeRequest = if (updatedTargetAccount != preparedRequest.targetAccount) {
                preparedRequest.copy(targetAccount = updatedTargetAccount)
            } else {
                preparedRequest
            }
            launchRequestedHosts(activeRequest, changes)
            activeRequest.progressCallback?.invoke("4/4 正在确认 IDE 与 App & CLI 共享账号...")
            val report = verifier.verifyTargets(activeRequest, ideWasRunning, appWasRunning, changes)
            if (report.overallStatus == HotSwitchCoordinator.OverallStatus.ERROR) {
                throw IllegalStateException(verifier.buildVerificationError(report))
            }
            val appliedAccount = if (activeRequest.applyToAppCli) {
                accountStore.commitSwitchedAccount(activeRequest.targetAccount).getOrThrow()
            } else {
                activeRequest.targetAccount
            }
            Result.success(report.copy(appliedAccount = appliedAccount))
        } catch (error: CancellationException) {
            val rollbackErrors = rollbackHandler.rollbackNonCancellable(
                preparedRequest,
                originalState,
                ideWasRunning,
                appWasRunning,
                changes
            )
            if (rollbackErrors.isNotEmpty()) {
                error.addSuppressed(
                    IllegalStateException("切号取消后的回滚不完整：" + rollbackErrors.joinToString("、"))
                )
            }
            throw error
        } catch (error: Exception) {
            val rollbackErrors = rollbackHandler.rollbackNonCancellable(
                preparedRequest,
                originalState,
                ideWasRunning,
                appWasRunning,
                changes
            )
            val rollbackSuffix = if (rollbackErrors.isEmpty()) {
                "；已恢复切换前状态"
            } else {
                "；回滚不完整：" + rollbackErrors.joinToString("、")
            }
            Result.failure(
                IllegalStateException(
                    "切号执行失败：" + (error.message ?: "未知错误") + rollbackSuffix,
                    error
                )
            )
        } finally {
            originalState.close()
        }
    }

    private suspend fun stopRequestedHosts(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ) {
        if (request.applyToIde && request.restartIde && ideWasRunning) {
            request.progressCallback?.invoke("1/4 正在安全停止 Antigravity IDE...")
            log("停止IDE", "检测到 IDE 正在运行，正在请求安全退出...")
            requireStep(
                IdeHostManager.terminate(request.ideInstallationPath, force = true),
                "Antigravity IDE 退出失败，已取消切号"
            )
            changes.ideTerminated = true
            log("停止IDE完成", "Antigravity IDE 进程已安全退出")
        }

        if (request.applyToAppCli && request.restartApp) {
            request.progressCallback?.invoke("1/4 正在安全停止 Antigravity App...")
            log("停止App", "正在请求安全退出 Antigravity App 及其语言服务...")
            val terminated = AppHostManager.terminate(request.appInstallationPath, force = true)
            if (appWasRunning) {
                requireStep(terminated, "Antigravity App 退出失败，已取消切号")
                changes.appTerminated = true
            }
            log("停止App完成", "Antigravity App 已停止 (wasRunning=" + appWasRunning + ")")
            delay(100)
        }
    }

    private suspend fun launchRequestedHosts(request: Request, changes: AppliedChanges) {
        if (request.applyToIde && request.restartIde && IdeHostManager.isInstalled(request.ideInstallationPath)) {
            val shouldLaunch = changes.ideTerminated || !IdeHostManager.isRunning(request.ideInstallationPath)
            if (shouldLaunch) {
                request.progressCallback?.invoke(
                    if (changes.ideTerminated) "3/4 正在重启 Antigravity IDE..." else "3/4 正在启动 Antigravity IDE..."
                )
                log("启动IDE", "正在拉起 Antigravity IDE...")
                changes.ideLaunchAttempted = true
                requireStep(IdeHostManager.launch(request.ideInstallationPath), "Antigravity IDE 启动请求失败")
                requireStep(
                    waitUntilRunning { IdeHostManager.isRunning(request.ideInstallationPath) },
                    "Antigravity IDE 启动后未进入运行状态"
                )
                log("启动IDE完成", "Antigravity IDE 已进入运行状态")
            }
        }

        if (request.applyToAppCli && request.restartApp && AppHostManager.isInstalled(request.appInstallationPath)) {
            val shouldLaunch = changes.appTerminated || !AppHostManager.isRunning(request.appInstallationPath)
            if (shouldLaunch) {
                request.progressCallback?.invoke(
                    if (changes.appTerminated) "3/4 正在重启 Antigravity App..." else "3/4 正在启动 Antigravity App..."
                )
                log("启动App", "正在拉起 Antigravity App (open -n)...")
                changes.appLaunchAttempted = true
                requireStep(
                    AppHostManager.launch(request.appInstallationPath, request.proxyPort),
                    "Antigravity App 启动请求失败"
                )
                requireStep(
                    waitUntilRunning { AppHostManager.isRunning(request.appInstallationPath) },
                    "Antigravity App 启动后未进入运行状态"
                )
                log("启动App完成", "Antigravity App 已进入运行状态")
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


    private fun requireStep(isSuccess: Boolean, message: String) {
        if (!isSuccess) {
            throw IllegalStateException(message)
        }
    }

    private companion object {
        private const val HOST_START_TIMEOUT_MS = 8_000L
        private const val HOST_START_INTERVAL_MS = 100L
    }
}
