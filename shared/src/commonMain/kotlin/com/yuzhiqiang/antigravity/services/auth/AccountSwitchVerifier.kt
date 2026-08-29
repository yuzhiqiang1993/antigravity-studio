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
                log("验证通过", "IDE: " + detectedIdeEmail + ", App: " + detectedAppEmail + " (目标: " + request.targetAccount.email + ")")
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

    fun buildReport(
        request: AccountSwitchSession.Request,
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
        request: AccountSwitchSession.Request,
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
        request: AccountSwitchSession.Request,
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

    fun buildVerificationError(report: HotSwitchCoordinator.SwitchResultReport): String {
        return listOf(report.ide, report.appCli)
            .filter { result -> result.status == HotSwitchCoordinator.TargetStatus.FAILED }
            .mapNotNull { result -> result.message }
            .ifEmpty { listOf("目标应用未确认账号切换结果") }
            .joinToString("；")
    }

    private fun targetResult(
        status: HotSwitchCoordinator.TargetStatus,
        actualEmail: String? = null,
        message: String? = null
    ): HotSwitchCoordinator.TargetResult {
        return HotSwitchCoordinator.TargetResult(status, actualEmail, message)
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

    companion object {
        private const val VERIFY_TIMEOUT_MS = 8_000L
        private const val VERIFY_INTERVAL_MS = 500L
    }
}
