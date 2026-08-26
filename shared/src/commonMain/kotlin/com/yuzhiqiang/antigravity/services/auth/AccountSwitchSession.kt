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
 * 执行单次账号切换事务，负责宿主启停、凭据写入、分端确认和失败回滚。
 */
internal class AccountSwitchSession(
    private val accountStore: AccountStore
) {
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
            captureOriginalState(request, appWasRunning)
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
            request.progressCallback?.invoke("4/4 正在分别确认 IDE、App 与 CLI 账号...")
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
            requireStep(
                IdeHostManager.terminate(request.ideInstallationPath),
                "Antigravity IDE 未安全退出，已取消切号"
            )
            changes.ideTerminated = true
        }

        if (request.applyToAppCli && request.restartApp && appWasRunning) {
            request.progressCallback?.invoke("1/4 正在安全停止 Antigravity App...")
            requireStep(
                AppHostManager.terminate(request.appInstallationPath),
                "Antigravity App 未安全退出，已取消切号"
            )
            changes.appTerminated = true
        }
    }

    private suspend fun applyCredentials(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ) {
        request.progressCallback?.invoke("2/4 正在按目标写入账号凭据...")

        if (request.applyToIde && (!ideWasRunning || request.restartIde)) {
            val ideDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.IDE)
                .any { file -> file.isFile }
            if (IdeHostManager.isInstalled(request.ideInstallationPath) && ideDbExists) {
                requireStep(
                    StateDbInjector.inject(request.targetAccount, StateDbInjector.TargetHost.IDE),
                    "IDE 账号数据库写入失败"
                )
                changes.ideDbWritten = true
            } else {
                changes.ideUnavailable = true
            }
        }

        if (!request.applyToAppCli) {
            return
        }

        requireStep(
            accountStore.setActiveAccount(request.targetAccount.id).isSuccess,
            "Studio 活跃账号更新失败"
        )
        changes.studioAccountChanged = true
        requireStep(
            accountStore.syncToOfficialCredentials(request.targetAccount),
            "CLI 官方凭据文件写入失败"
        )
        changes.cliCredentialsWritten = true

        if (appWasRunning && !request.restartApp) {
            return
        }

        val appDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.APP)
            .any { file -> file.isFile }
        val appInstalled = AppHostManager.isInstalled(request.appInstallationPath)
        if (isMacOs() && appInstalled) {
            requireStep(
                MacKeychainInjector.inject(request.targetAccount, request.appInstallationPath),
                "App Keychain 凭据写入失败"
            )
            changes.appKeychainWritten = true
        }
        changes.jetskiTokenWriteAttempted = true
        requireStep(
            writeJetskiStandaloneToken(request.targetAccount),
            "App jetski 凭据文件写入失败"
        )
        changes.jetskiTokenWritten = true
        changes.appOauthFileWriteAttempted = true
        requireStep(
            writeAppOauthCredentials(request.targetAccount),
            "App OAuth 凭据文件写入失败"
        )
        changes.appOauthFileWritten = true
        if (appDbExists) {
            requireStep(
                StateDbInjector.inject(request.targetAccount, StateDbInjector.TargetHost.APP),
                "App 账号数据库写入失败"
            )
            changes.appDbWritten = true
        }
        changes.appUnavailable = !appInstalled
    }

    private suspend fun launchRequestedHosts(request: Request, changes: AppliedChanges) {
        if (changes.ideTerminated) {
            request.progressCallback?.invoke("3/4 正在启动 Antigravity IDE...")
            changes.ideLaunchAttempted = true
            requireStep(IdeHostManager.launch(request.ideInstallationPath), "Antigravity IDE 启动请求失败")
            requireStep(
                waitUntilRunning { IdeHostManager.isRunning(request.ideInstallationPath) },
                "Antigravity IDE 启动后未进入运行状态"
            )
        }

        if (changes.appTerminated) {
            request.progressCallback?.invoke("3/4 正在启动 Antigravity App...")
            changes.appLaunchAttempted = true
            requireStep(
                AppHostManager.launch(request.appInstallationPath, request.proxyPort),
                "Antigravity App 启动请求失败"
            )
            requireStep(
                waitUntilRunning { AppHostManager.isRunning(request.appInstallationPath) },
                "Antigravity App 启动后未进入运行状态"
            )
        }
    }

    private suspend fun verifyTargets(
        request: Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): HotSwitchCoordinator.SwitchResultReport {
        val idePending = request.applyToIde && ideWasRunning && !request.restartIde
        val appPending = request.applyToAppCli && appWasRunning && !request.restartApp
        var detectedIdeEmail: String? = null
        var detectedAppEmail: String? = null
        var detectedCliEmail: String? = null
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            if (request.applyToIde) {
                detectedIdeEmail = HostAccountDetector.detectIdeActiveEmail()
            }
            if (request.applyToAppCli) {
                detectedAppEmail = HostAccountDetector.detectAppActiveEmail(
                    accountStore.currentAccounts()
                )
                detectedCliEmail = HostAccountDetector.detectCliAppActiveEmail(
                    accountStore.officialCredentialsFile()
                )
            }

            val ideRunning = !ideWasRunning || idePending || IdeHostManager.isRunning(request.ideInstallationPath)
            val appRunning = !appWasRunning || appPending || AppHostManager.isRunning(request.appInstallationPath)
            val ideDone = idePending || !request.applyToIde ||
                    (ideRunning && matchesTarget(detectedIdeEmail, request.targetAccount.email))
            val appDone = appPending || !request.applyToAppCli || !appWasRunning ||
                    (appRunning && matchesTarget(detectedAppEmail, request.targetAccount.email))
            val cliDone = !request.applyToAppCli || matchesTarget(detectedCliEmail, request.targetAccount.email)
            if (ideDone && appDone && cliDone) {
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
            detectedCliEmail,
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
        detectedCliEmail: String?,
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
            app = buildAppResult(
                request,
                appWasRunning,
                appIsRunning,
                appPending,
                detectedAppEmail,
                changes.appKeychainWritten || changes.appDbWritten || changes.jetskiTokenWritten,
                changes.appUnavailable
            ),
            cli = buildCliResult(request, detectedCliEmail),
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
        if (wasRunning && !isRunning) {
            return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "IDE 重启后未保持运行")
        }
        if (matchesTarget(actualEmail, request.targetAccount.email)) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIGURED,
                actualEmail,
                if (wasRunning) "IDE 已重启并写入目标账号，运行态仍待真实请求确认" else null
            )
        }
        return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "IDE 未确认目标账号")
    }

    private fun buildAppResult(
        request: Request,
        wasRunning: Boolean,
        isRunning: Boolean,
        isPending: Boolean,
        actualEmail: String?,
        isCredentialsConfigured: Boolean,
        isUnavailable: Boolean
    ): HotSwitchCoordinator.TargetResult {
        if (!request.applyToAppCli) {
            return targetResult(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }
        if (isPending) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.PENDING_RESTART,
                actualEmail,
                "App 运行中，需用户确认重启后切换"
            )
        }
        if (isUnavailable) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE,
                actualEmail,
                "App 未安装或尚未初始化账号存储，仅 CLI 已切换"
            )
        }
        if (wasRunning && !isRunning) {
            return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "App 重启后未保持运行")
        }
        if (matchesTarget(actualEmail, request.targetAccount.email)) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIRMED,
                actualEmail,
                "App 运行态已确认目标账号"
            )
        }
        if (!wasRunning && isCredentialsConfigured && actualEmail == null) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIGURED,
                message = "App 凭据已配置，启动后确认运行态"
            )
        }
        if (!wasRunning && !AppHostManager.isInstalled(request.appInstallationPath)) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE,
                actualEmail,
                "未检测到 Antigravity App，仅 CLI 已切换"
            )
        }
        return targetResult(HotSwitchCoordinator.TargetStatus.FAILED, actualEmail, "App 未确认目标账号")
    }

    private fun buildCliResult(request: Request, actualEmail: String?): HotSwitchCoordinator.TargetResult {
        if (!request.applyToAppCli) {
            return targetResult(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }
        val credentialMatchesTarget = (
                accountStore.officialCredentialsFile()
                    .takeIf { file -> file.isFile }
                    ?.let { file -> credentialRefreshTokenMatches(file, request.targetAccount) }
                ) == true
        if (matchesTarget(actualEmail, request.targetAccount.email) && credentialMatchesTarget) {
            return targetResult(
                HotSwitchCoordinator.TargetStatus.CONFIGURED,
                actualEmail,
                "CLI 凭据文件已配置"
            )
        }
        return targetResult(
            HotSwitchCoordinator.TargetStatus.FAILED,
            actualEmail,
            "CLI 凭据文件配置未确认目标账号"
        )
    }

    private fun captureOriginalState(request: Request, appWasRunning: Boolean): OriginalState {
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
        val keychainSnapshot = if (
            request.applyToAppCli &&
            isMacOs() &&
            AppHostManager.isInstalled(request.appInstallationPath) &&
            (!appWasRunning || request.restartApp)
        ) {
            MacKeychainInjector.capture().getOrThrow()
        } else {
            null
        }
        val cliSnapshot = if (request.applyToAppCli) {
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
            keychainSnapshot = keychainSnapshot,
            cliSnapshot = cliSnapshot,
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
            restoreApp(request, originalState, changes, errors)
        }
        restoreCliAndStudio(originalState, changes, errors)

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
        request: Request,
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
        if (changes.appKeychainWritten) {
            val snapshot = originalState.keychainSnapshot
            if (snapshot == null || !MacKeychainInjector.restore(snapshot, request.appInstallationPath)) {
                errors.add("App 原 Keychain 凭据恢复失败")
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

    private suspend fun restoreCliAndStudio(
        originalState: OriginalState,
        changes: AppliedChanges,
        errors: MutableList<String>
    ) {
        if (changes.cliCredentialsWritten) {
            val snapshot = originalState.cliSnapshot
            if (snapshot == null || !accountStore.restoreOfficialCredentialsSnapshot(snapshot)) {
                errors.add("CLI 原凭据恢复失败")
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
        return listOf(report.ide, report.app, report.cli)
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
            val payload = buildJsonObject {
                put("access_token", account.tokens.accessToken)
                put("refresh_token", account.tokens.refreshToken)
                put("email", account.email)
                put("name", account.profile.name ?: "")
                put("expiry_timestamp", expiryTimestamp)
                put("expiry_date", expiryTimestamp * 1000L)
                put("token_type", "Bearer")
                put("antigravity_cockpit_active_email", account.email)
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
        return File(
            System.getProperty("user.home"),
            "Library/Application Support/Antigravity/oauth_credentials.json"
        )
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

    private fun isMacOs(): Boolean {
        return System.getProperty("os.name", "").lowercase().contains("mac")
    }

    private data class OriginalState(
        val studioAccount: AccountInfo?,
        val ideSnapshot: StateDbInjector.Snapshot?,
        val appDbSnapshot: StateDbInjector.Snapshot?,
        val keychainSnapshot: MacKeychainInjector.Snapshot?,
        val cliSnapshot: AccountStore.OfficialCredentialsSnapshot?,
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
        var appKeychainWritten: Boolean = false,
        var jetskiTokenWriteAttempted: Boolean = false,
        var jetskiTokenWritten: Boolean = false,
        var appOauthFileWriteAttempted: Boolean = false,
        var appOauthFileWritten: Boolean = false,
        var cliCredentialsWritten: Boolean = false,
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
