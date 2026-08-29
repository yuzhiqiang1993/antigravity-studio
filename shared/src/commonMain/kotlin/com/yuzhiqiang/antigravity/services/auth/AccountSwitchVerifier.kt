package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 负责账号切换后的生效状态验证与多目标检测报告生成。
 */
internal class AccountSwitchVerifier(
    private val accountStore: AccountStore
) {
    private fun log(stage: String, message: String) {
        AppLog.i("Auth/Switch") { "[$stage] $message" }
    }

    suspend fun verifyTargets(
        request: AccountSwitchSession.Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): HotSwitchCoordinator.SwitchResultReport {
        log("开始验证", "正在分别探测运行态账号与静态凭据配置...")
        var ideObservations = AccountObservations()
        var appObservations = AccountObservations()
        var report: HotSwitchCoordinator.SwitchResultReport? = null
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS

        do {
            if (request.applyToIde) {
                ideObservations = HostAccountDetector.detectIdeAccountProbes(
                    request.ideInstallationPath
                ).toObservations(
                    configuredSource = HostAccountDetector.AccountProbeSource.STATE_DB
                )
            }
            if (request.applyToAppCli) {
                appObservations = HostAccountDetector.detectAppCliAccountProbes(
                    credentialsFile = accountStore.officialCredentialsFile(),
                    installationPath = request.appInstallationPath
                ).toObservations(
                    configuredSource = HostAccountDetector.AccountProbeSource.SHARED_CREDENTIALS
                )
            }

            val ideIsRunning = IdeHostManager.isRunning(request.ideInstallationPath)
            val appIsRunning = AppHostManager.isRunning(request.appInstallationPath)
            report = buildReport(
                request = request,
                ideWasRunning = ideWasRunning,
                appWasRunning = appWasRunning,
                ideIsRunning = ideIsRunning,
                appIsRunning = appIsRunning,
                ideObservations = ideObservations,
                appObservations = appObservations,
                sharedCredentialTokenMatches = credentialRefreshTokenMatches(
                    accountStore.officialCredentialsFile(),
                    request.targetAccount
                ),
                changes = changes
            )

            val ideDone = !request.applyToIde ||
                    !request.restartIde ||
                    report.ide.isConfirmed ||
                    report.ide.status == HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE ||
                    !ideIsRunning
            val appInstalled = AppHostManager.isInstalled(request.appInstallationPath)
            val appDone = !request.applyToAppCli ||
                    !request.restartApp ||
                    !appInstalled ||
                    report.appCli.isConfirmed ||
                    !appIsRunning
            if (ideDone && appDone) {
                break
            }
            delay(VERIFY_INTERVAL_MS)
        } while (System.currentTimeMillis() < deadline)

        val finalReport = requireNotNull(report)
        log(
            "验证完成",
            "IDE(runtime=${ideObservations.runtimeEmail}, configured=${ideObservations.configuredEmail}), " +
                    "App(runtime=${appObservations.runtimeEmail}, configured=${appObservations.configuredEmail}), " +
                    "target=${request.targetAccount.email}"
        )
        return finalReport
    }

    internal fun buildReport(
        request: AccountSwitchSession.Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        ideIsRunning: Boolean,
        appIsRunning: Boolean,
        ideObservations: AccountObservations,
        appObservations: AccountObservations,
        sharedCredentialTokenMatches: Boolean,
        changes: AppliedChanges
    ): HotSwitchCoordinator.SwitchResultReport {
        val sharedCredentialsMatch = changes.sharedCredentialsWritten &&
                appObservations.configuredEmail?.equals(request.targetAccount.email, ignoreCase = true) == true &&
                sharedCredentialTokenMatches
        return HotSwitchCoordinator.SwitchResultReport(
            targetEmail = request.targetAccount.email,
            appliedAccount = request.targetAccount,
            ide = AccountSwitchVerificationPolicy.ideResult(
                requested = request.applyToIde,
                targetEmail = request.targetAccount.email,
                observations = ideObservations,
                wasRunning = ideWasRunning,
                restartRequested = request.restartIde,
                isRunning = ideIsRunning,
                isUnavailable = changes.ideUnavailable
            ),
            appCli = AccountSwitchVerificationPolicy.appCliResult(
                requested = request.applyToAppCli,
                targetEmail = request.targetAccount.email,
                observations = appObservations,
                credentialsMatchTarget = sharedCredentialsMatch,
                wasRunning = appWasRunning,
                restartRequested = request.restartApp,
                isRunning = appIsRunning,
                isUnavailable = changes.appUnavailable
            ),
            ideWasRunning = ideWasRunning,
            appWasRunning = appWasRunning
        )
    }

    fun buildVerificationError(report: HotSwitchCoordinator.SwitchResultReport): String {
        return listOf(report.ide, report.appCli)
            .filter { result -> result.status == HotSwitchCoordinator.TargetStatus.FAILED }
            .mapNotNull { result -> result.message }
            .ifEmpty { listOf("目标应用未确认账号切换结果") }
            .joinToString("；")
    }

    private fun List<HostAccountDetector.AccountProbeResult>.toObservations(
        configuredSource: HostAccountDetector.AccountProbeSource
    ): AccountObservations {
        return AccountObservations(
            runtimeEmail = firstOrNull { probe ->
                probe.source == HostAccountDetector.AccountProbeSource.RUNTIME_API
            }?.profile?.email,
            configuredEmail = firstOrNull { probe ->
                probe.source == configuredSource
            }?.profile?.email
        )
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

    companion object {
        private const val VERIFY_TIMEOUT_MS = 8_000L
        private const val VERIFY_INTERVAL_MS = 500L
    }
}
