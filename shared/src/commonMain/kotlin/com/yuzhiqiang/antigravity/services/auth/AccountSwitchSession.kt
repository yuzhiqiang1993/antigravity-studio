package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 执行单次账号切换事务，负责宿主启停、共享凭据写入、运行态确认和失败回滚。
 */
internal class AccountSwitchSession(
    private val accountStore: AccountStore,
    private val googleAuthService: GoogleAuthService = GoogleAuthService()
) {
    private fun log(tag: String, message: String) {
        println("[AccountSwitchSession][$tag] $message")
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

        val ideWasRunning = IdeHostManager.isRunning(request.ideInstallationPath)
        val appWasRunning = AppHostManager.isRunning(request.appInstallationPath)
        val originalState = try {
            captureOriginalState(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(IllegalStateException("切号前物理状态快照失败", error))
        }
        val changes = AppliedChanges()
        return try {
            stopRequestedHosts(request, ideWasRunning, appWasRunning, changes)
            applyCredentials(request, ideWasRunning, appWasRunning, changes)
            launchRequestedHosts(request, changes)
            request.progressCallback?.invoke("4/4 正在确认 IDE 与 App & CLI 共享账号...")
            val report = verifyTargets(request, ideWasRunning, appWasRunning, changes)
            if (report.overallStatus == HotSwitchCoordinator.OverallStatus.ERROR) {
                throw IllegalStateException(buildVerificationError(report))
            }
            refreshOfficialCatalog(changes)
            Result.success(report)
        } catch (error: CancellationException) {
            val rollbackErrors = rollbackNonCancellable(
                request,
                originalState,
                ideWasRunning,
                appWasRunning,
                changes
            )
            if (rollbackErrors.isNotEmpty()) {
                error.addSuppressed(
                    IllegalStateException("切号取消后的回滚不完整：${rollbackErrors.joinToString("、")}")
                )
            }
            throw error
        } catch (error: Exception) {
            val rollbackErrors = rollbackNonCancellable(
                request,
                originalState,
                ideWasRunning,
                appWasRunning,
                changes
            )
            val rollbackSuffix = if (rollbackErrors.isEmpty()) {
                "；已恢复切换前状态"
            } else {
                "；回滚不完整：${rollbackErrors.joinToString("、")}"
            }
            Result.failure(
                IllegalStateException(
                    "切号执行失败：${error.message ?: "未知错误"}$rollbackSuffix",
                    error
                )
            )
        }
    }

    private suspend fun rollbackNonCancellable(
        request: Request,
        originalState: OriginalState,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): List<String> {
        return withContext(NonCancellable) {
            rollback(request, originalState, ideWasRunning, appWasRunning, changes)
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
            log("停止App完成", "Antigravity App 已停止 (wasRunning=$appWasRunning)")
            delay(500)
        }
    }

    private suspend fun applyCredentials(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ) {
        request.progressCallback?.invoke("2/4 正在写入目标账号与 App & CLI 共享凭据...")
        log("切号开始", "目标账号: ${request.targetAccount.email}, applyToIde=${request.applyToIde}, applyToAppCli=${request.applyToAppCli}")

        // 1. 若包含 App & CLI 目标且有 Refresh Token，先联网向 Google 刷新最新的 ID Token 与 Access Token (对齐 Cockpit 插件机制)
        val targetAccount = if (request.applyToAppCli && request.targetAccount.tokens.refreshToken.isNotBlank()) {
            log("联网刷新Token", "正在向 Google 刷新 ${request.targetAccount.email} 的最新 Access/ID Token...")
            val refreshResult = googleAuthService.refreshAccessToken(request.targetAccount.tokens.refreshToken).getOrNull()
            if (refreshResult != null) {
                log("Token刷新成功", "已获取最新 ID Token (len=${refreshResult.idToken?.length ?: 0})")
                val updated = request.targetAccount.copy(tokens = refreshResult)
                accountStore.updateTokens(
                    email = updated.email,
                    tokens = updated.tokens,
                    name = updated.profile.name,
                    avatarUrl = updated.profile.avatarUrl
                )
                updated
            } else {
                log("Token刷新失败", "使用当前缓存的 Token 继续切号")
                request.targetAccount
            }
        } else {
            request.targetAccount
        }

        if (request.applyToIde && (!ideWasRunning || request.restartIde)) {
            val ideDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.IDE)
                .any { file -> file.isFile }
            if (IdeHostManager.isInstalled(request.ideInstallationPath) && ideDbExists) {
                log("IDE凭据注入", "正在向 IDE state.vscdb 写入账号数据...")
                requireStep(
                    StateDbInjector.inject(targetAccount, StateDbInjector.TargetHost.IDE),
                    "IDE 账号数据库写入失败"
                )
                changes.ideDbWritten = true
                log("IDE凭据注入完成", "已更新 IDE state.vscdb")
            } else {
                changes.ideUnavailable = true
            }
        }

        if (!request.applyToAppCli) {
            return
        }

        requireStep(
            accountStore.setActiveAccount(targetAccount.id).isSuccess,
            "Studio 活跃账号更新失败"
        )
        changes.studioAccountChanged = true

        // 同步写入官方与镜像 OAuth 凭据文件
        log("凭据文件同步", "正在写入 ~/.gemini/oauth_creds.json 及镜像文件...")
        requireStep(
            accountStore.syncToOfficialCredentials(targetAccount),
            "App & CLI 共享 OAuth 凭据文件写入失败"
        )
        changes.sharedCredentialsWritten = true
        changes.appUnavailable = !AppHostManager.isInstalled(request.appInstallationPath)

        // 核心突破：注入系统级安全存储 (macOS Keychain: service=gemini, account=antigravity)
        log("Keychain注入", "正在向系统钥匙串写入 Antigravity 认证凭据...")
        val keychainResult = SystemCredentialInjector.inject(targetAccount)
        requireStep(
            keychainResult.isSuccess,
            "系统钥匙串凭据注入失败: ${keychainResult.exceptionOrNull()?.message}"
        )

        if (appWasRunning && !request.restartApp) {
            return
        }

        val appDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.APP)
            .any { file -> file.isFile }
        changes.jetskiTokenWriteAttempted = true
        // 这些文件是 App 对共享 OAuth 的兼容投影，不是独立于 CLI 的另一套认证凭据。
        requireStep(
            writeJetskiStandaloneToken(targetAccount),
            "App 共享凭据兼容投影写入失败"
        )
        changes.jetskiTokenWritten = true
        changes.appOauthFileWriteAttempted = true
        requireStep(
            writeAppOauthCredentials(targetAccount),
            "App 共享 OAuth 兼容投影写入失败"
        )
        changes.appOauthFileWritten = true
        if (appDbExists) {
            requireStep(
                StateDbInjector.inject(targetAccount, StateDbInjector.TargetHost.APP),
                "App 共享凭据运行态投影写入失败"
            )
            changes.appDbWritten = true
        }
        log("App凭据写入完成", "所有文件与系统钥匙串已就绪")
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


    private suspend fun verifyTargets(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): HotSwitchCoordinator.SwitchResultReport {
        log("开始验证", "正在探测 IDE 与 App 的运行态生效账号...")
        val idePending = request.applyToIde && ideWasRunning && !request.restartIde
        val appPending = request.applyToAppCli && appWasRunning && !request.restartApp
        var detectedIdeEmail: String? = null
        var detectedAppEmail: String? = null
        var detectedSharedEmail: String? = null
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            if (request.applyToIde) {
                detectedIdeEmail = HostAccountDetector.detectIdeActiveEmail()
            }
            if (request.applyToAppCli) {
                val appCliEmail = HostAccountDetector.detectAppCliActiveEmail()
                detectedAppEmail = appCliEmail
                detectedSharedEmail = appCliEmail
            }

            val ideRunning =
                !request.applyToIde || !request.restartIde || IdeHostManager.isRunning(request.ideInstallationPath)
            val appInstalled = request.applyToAppCli && AppHostManager.isInstalled(request.appInstallationPath)
            val appRunning =
                !request.applyToAppCli || !request.restartApp || !appInstalled || AppHostManager.isRunning(
                    request.appInstallationPath
                )
            val ideDone = idePending || !request.applyToIde ||
                    (ideRunning && matchesTarget(detectedIdeEmail, request.targetAccount.email))
            val appRuntimeDone = appPending || !request.applyToAppCli || !request.restartApp || !appInstalled ||
                    (appRunning && matchesTarget(detectedAppEmail, request.targetAccount.email))
            val sharedCredentialsDone = !request.applyToAppCli ||
                    matchesTarget(detectedSharedEmail, request.targetAccount.email)
            if (ideDone && appRuntimeDone && sharedCredentialsDone) {
                log("验证通过", "IDE: $detectedIdeEmail, App: $detectedAppEmail (目标: ${request.targetAccount.email})")
                break
            }
            delay(VERIFY_INTERVAL_MS)
        }

        return buildReport(
            request,
            ideWasRunning,
            appWasRunning,
            idePending,
            appPending,
            detectedIdeEmail,
            detectedAppEmail,
            detectedSharedEmail,
            changes
        )
    }

    private fun buildReport(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        idePending: Boolean,
        appPending: Boolean,
        detectedIdeEmail: String?,
        detectedAppEmail: String?,
        detectedSharedEmail: String?,
        changes: AppliedChanges
    ): HotSwitchCoordinator.SwitchResultReport {
        val ideIsRunning = IdeHostManager.isRunning(request.ideInstallationPath)
        val appIsRunning = AppHostManager.isRunning(request.appInstallationPath)
        return HotSwitchCoordinator.SwitchResultReport(
            targetEmail = request.targetAccount.email,
            ide = buildIdeResult(
                request,
                ideWasRunning,
                ideIsRunning,
                idePending,
                detectedIdeEmail,
                changes.ideUnavailable
            ),
            appCli = buildAppCliResult(
                request,
                appIsRunning,
                appPending,
                detectedAppEmail,
                detectedSharedEmail,
                changes.sharedCredentialsWritten,
                changes.appUnavailable
            ),
            ideWasRunning = ideWasRunning,
            appWasRunning = appWasRunning
        )
    }

    private fun buildIdeResult(
        request: Request,
        wasRunning: Boolean,
        isRunning: Boolean,
        isPending: Boolean,
        actualEmail: String?,
        isUnavailable: Boolean
    ): HotSwitchCoordinator.TargetResult {
        if (!request.applyToIde) {
            return targetResult(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }
        if (isPending) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.PENDING_RESTART,
                actualEmail,
                "IDE 运行中，需用户确认重启后切换"
            )
        }
        if (isUnavailable) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE,
                actualEmail,
                "IDE 未安装或尚未初始化账号数据库"
            )
        }
        if (request.restartIde && !isRunning) {
            return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "IDE 未进入或未保持运行状态")
        }
        if (matchesTarget(actualEmail, request.targetAccount.email)) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIRMED,
                actualEmail,
                "IDE 已生效"
            )
        }
        return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "IDE 未确认目标账号")
    }

    private fun buildAppCliResult(
        request: Request,
        isRunning: Boolean,
        isPending: Boolean,
        appRuntimeEmail: String?,
        sharedEmail: String?,
        sharedCredentialsWritten: Boolean,
        isUnavailable: Boolean
    ): HotSwitchCoordinator.TargetResult {
        if (!request.applyToAppCli) {
            return targetResult(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }

        val actualEmail = appRuntimeEmail ?: sharedEmail
        val credentialMatchesTarget = sharedCredentialsWritten &&
                matchesTarget(sharedEmail, request.targetAccount.email) &&
                accountStore.officialCredentialsFile()
                    .takeIf { file -> file.isFile }
                    ?.let { file -> credentialRefreshTokenMatches(file, request.targetAccount) } == true
        if (!credentialMatchesTarget) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.FAILED,
                actualEmail,
                "App & CLI 共享凭据文件未确认目标账号"
            )
        }
        if (isPending) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.PENDING_RESTART,
                actualEmail,
                "App 仍在运行；App & CLI 共享凭据已写入，需重启 App 后加载"
            )
        }
        if (isUnavailable) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE,
                actualEmail,
                "App & CLI 共享凭据已写入；未安装 App，无法进行运行态确认"
            )
        }
        if (matchesTarget(appRuntimeEmail, request.targetAccount.email)) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIRMED,
                actualEmail,
                "App & CLI 共享凭据已写入，App 运行态已确认"
            )
        }

        val message = when {
            !request.restartApp -> "App & CLI 共享凭据已写入，App 将在下次启动时加载"
            !isRunning -> "App & CLI 共享凭据已写入，但 App 未能保持运行"
            else -> "App & CLI 共享凭据已写入，App 运行态尚未确认"
        }
        return targetResult(
            HotSwitchCoordinator.TargetStatus.CONFIGURED,
            actualEmail,
            message
        )
    }

    private fun captureOriginalState(request: Request): OriginalState {
        val ideSnapshot = if (request.applyToIde && hasStateDb(StateDbInjector.TargetHost.IDE)) {
            StateDbInjector.capture(StateDbInjector.TargetHost.IDE).getOrThrow()
        } else {
            null
        }
        val appDbSnapshot = if (request.applyToAppCli && hasStateDb(StateDbInjector.TargetHost.APP)) {
            StateDbInjector.capture(StateDbInjector.TargetHost.APP).getOrThrow()
        } else {
            null
        }

        val sharedCredentialsSnapshot = if (request.applyToAppCli) {
            accountStore.captureOfficialCredentialsSnapshot().getOrThrow()
        } else {
            null
        }
        val jetskiTokenSnapshot = if (request.applyToAppCli) {
            captureFileSnapshot(resolveJetskiStandaloneTokenFile())
        } else {
            null
        }
        val appOauthFileSnapshot = if (request.applyToAppCli) {
            captureFileSnapshot(resolveAppOauthCredentialsFile())
        } else {
            null
        }
        return OriginalState(
            studioAccount = accountStore.currentActiveAccount(),
            ideSnapshot = ideSnapshot,
            appDbSnapshot = appDbSnapshot,
            sharedCredentialsSnapshot = sharedCredentialsSnapshot,
            jetskiTokenSnapshot = jetskiTokenSnapshot,
            appOauthFileSnapshot = appOauthFileSnapshot
        )
    }

    private suspend fun rollback(
        request: Request,
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
        request: Request,
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
        request: Request,
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
            if (snapshot == null || !restoreFileSnapshot(snapshot)) {
                errors.add("App 原 jetski 凭据文件恢复失败")
            }
        }
        if (changes.appOauthFileWriteAttempted) {
            val snapshot = originalState.appOauthFileSnapshot
            if (snapshot == null || !restoreFileSnapshot(snapshot)) {
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
        request: Request,
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

    private suspend fun refreshOfficialCatalog(changes: AppliedChanges) {
        OfficialCatalogProbe.clearRawOfficialCatalog()
        if (changes.ideLaunchAttempted || changes.appLaunchAttempted) {
            OfficialCatalogProbe.fetchOfficialModels()
        }
    }

    private fun targetResult(
        status: HotSwitchCoordinator.TargetStatus,
        actualEmail: String? = null,
        message: String? = null
    ): HotSwitchCoordinator.TargetResult {
        return HotSwitchCoordinator.TargetResult(status, actualEmail, message)
    }

    private fun buildVerificationError(report: HotSwitchCoordinator.SwitchResultReport): String {
        return listOf(report.ide, report.appCli)
            .filter { result -> result.status == HotSwitchCoordinator.TargetStatus.FAILED }
            .mapNotNull { result -> result.message }
            .ifEmpty { listOf("目标宿主未确认账号切换结果") }
            .joinToString("；")
    }

    private fun requireStep(isSuccess: Boolean, message: String) {
        if (!isSuccess) {
            throw IllegalStateException(message)
        }
    }

    private fun matchesTarget(actualEmail: String?, targetEmail: String): Boolean {
        return actualEmail?.equals(targetEmail, ignoreCase = true) == true
    }

    private fun credentialRefreshTokenMatches(file: File, targetAccount: AccountInfo): Boolean {
        if (targetAccount.tokens.refreshToken.isBlank()) {
            return false
        }
        return try {
            val content = file.readText(Charsets.UTF_8)
            val expectedToken = kotlinx.serialization.json.Json.parseToJsonElement(content)
                .jsonObject["refresh_token"]
                ?.jsonPrimitive
                ?.contentOrNull
            expectedToken == targetAccount.tokens.refreshToken
        } catch (_: Exception) {
            false
        }
    }

    private fun writeJetskiStandaloneToken(account: AccountInfo): Boolean {
        return try {
            val tokenFile = resolveJetskiStandaloneTokenFile()
            val expiryIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(account.tokens.expiryTimestamp * 1000L))

            val payload = buildJsonObject {
                putJsonObject("token") {
                    put("access_token", account.tokens.accessToken)
                    put("token_type", "Bearer")
                    put("refresh_token", account.tokens.refreshToken)
                    put("expiry", expiryIso)
                }
                put("auth_method", "consumer")
            }.toString()

            tokenFile.parentFile?.mkdirs()
            writeSensitiveTextAtomically(tokenFile, payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeAppOauthCredentials(account: AccountInfo): Boolean {
        return try {
            val appCredsFile = resolveAppOauthCredentialsFile()
            if (!appCredsFile.parentFile.exists()) {
                return true
            }
            val expiryTimestamp = account.tokens.expiryTimestamp
            val idToken = account.tokens.idToken?.takeIf { it.isNotBlank() }
            val payload = buildJsonObject {
                put("access_token", account.tokens.accessToken)
                put("refresh_token", account.tokens.refreshToken)
                put("email", account.email)
                put("name", account.profile.name ?: "")
                put("expiry_timestamp", expiryTimestamp)
                put("expiry_date", expiryTimestamp * 1000L)
                put("token_type", "Bearer")
                put("antigravity_cockpit_active_email", account.email)
                if (idToken != null) {
                    put("id_token", idToken)
                }
            }.toString()
            writeSensitiveTextAtomically(appCredsFile, payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveJetskiStandaloneTokenFile(): File {
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        val dataDir = customDataDir
            ?.takeIf { path -> path.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.home"), ".gemini")
        return File(dataDir, "jetski-standalone-oauth-token")
    }

    private fun resolveAppOauthCredentialsFile(): File {
        return HostAccountDetector.resolvePlatformAppCredentialsFile()
    }

    private fun captureFileSnapshot(file: File): FileSnapshot {
        val existed = file.exists()
        val originalBytes = if (existed) file.readBytes() else byteArrayOf()
        return FileSnapshot(file, existed, originalBytes)
    }

    private fun writeSensitiveTextAtomically(file: File, content: String) {
        val parent = file.parentFile ?: throw IllegalStateException("凭据文件缺少父目录")
        parent.mkdirs()
        val tempFile = File.createTempFile(".${file.name}.", ".tmp", parent)
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            tempFile.setReadable(false, false)
            tempFile.setWritable(false, false)
            tempFile.setExecutable(false, false)
            tempFile.setReadable(true, true)
            tempFile.setWritable(true, true)
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun restoreFileSnapshot(snapshot: FileSnapshot): Boolean {
        return try {
            if (snapshot.existed) {
                writeSensitiveTextAtomically(
                    snapshot.file,
                    snapshot.originalBytes.toString(Charsets.UTF_8)
                )
                true
            } else {
                !snapshot.file.exists() || (snapshot.file.isFile && snapshot.file.delete())
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasStateDb(targetHost: StateDbInjector.TargetHost): Boolean {
        return StateDbInjector.resolveCandidateDbFiles(targetHost).any { file -> file.isFile }
    }


    private data class OriginalState(
        val studioAccount: AccountInfo?,
        val ideSnapshot: StateDbInjector.Snapshot?,
        val appDbSnapshot: StateDbInjector.Snapshot?,
        val sharedCredentialsSnapshot: AccountStore.OfficialCredentialsSnapshot?,
        val jetskiTokenSnapshot: FileSnapshot?,
        val appOauthFileSnapshot: FileSnapshot?
    )

    private data class FileSnapshot(
        val file: File,
        val existed: Boolean,
        val originalBytes: ByteArray
    )

    private data class AppliedChanges(
        var studioAccountChanged: Boolean = false,
        var ideDbWritten: Boolean = false,
        var appDbWritten: Boolean = false,
        var jetskiTokenWriteAttempted: Boolean = false,
        var jetskiTokenWritten: Boolean = false,
        var appOauthFileWriteAttempted: Boolean = false,
        var appOauthFileWritten: Boolean = false,
        var sharedCredentialsWritten: Boolean = false,
        var ideTerminated: Boolean = false,
        var appTerminated: Boolean = false,
        var ideLaunchAttempted: Boolean = false,
        var appLaunchAttempted: Boolean = false,
        var ideUnavailable: Boolean = false,
        var appUnavailable: Boolean = false
    )

    private companion object {
        private const val VERIFY_TIMEOUT_MS = 8_000L
        private const val VERIFY_INTERVAL_MS = 500L
        private const val HOST_START_TIMEOUT_MS = 10_000L
        private const val HOST_START_INTERVAL_MS = 250L
    }
}
