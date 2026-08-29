package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSwitchVerificationPolicyTest {

    @Test
    fun ideRequiresRuntimeSourceForConfirmed() {
        val result = AccountSwitchVerificationPolicy.ideResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(
                runtimeEmail = TARGET,
                configuredEmail = "old@example.com"
            ),
            wasRunning = true,
            restartRequested = true,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.CONFIRMED, result.status)
    }

    @Test
    fun ideStaticMatchWithoutRuntimeIsConfigured() {
        val result = AccountSwitchVerificationPolicy.ideResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(configuredEmail = TARGET),
            wasRunning = false,
            restartRequested = false,
            isRunning = false,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.CONFIGURED, result.status)
    }

    @Test
    fun ideOldRuntimeAndTargetConfigWithoutRestartIsPending() {
        val result = AccountSwitchVerificationPolicy.ideResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(
                runtimeEmail = "old@example.com",
                configuredEmail = TARGET
            ),
            wasRunning = true,
            restartRequested = false,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.PENDING_RESTART, result.status)
    }

    @Test
    fun ideCannotClaimPendingWithoutTargetConfig() {
        val result = AccountSwitchVerificationPolicy.ideResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(runtimeEmail = "old@example.com"),
            wasRunning = true,
            restartRequested = false,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.FAILED, result.status)
    }

    @Test
    fun ideOldRuntimeAfterRestartFailsEvenWhenDatabaseMatches() {
        val result = AccountSwitchVerificationPolicy.ideResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(
                runtimeEmail = "old@example.com",
                configuredEmail = TARGET
            ),
            wasRunning = true,
            restartRequested = true,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.FAILED, result.status)
    }

    @Test
    fun appRuntimeAndSharedCredentialMatchIsConfirmed() {
        val result = AccountSwitchVerificationPolicy.appCliResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(runtimeEmail = TARGET, configuredEmail = TARGET),
            credentialsMatchTarget = true,
            wasRunning = true,
            restartRequested = true,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.CONFIRMED, result.status)
    }

    @Test
    fun appOldRuntimeWithTargetCredentialsWithoutRestartIsPending() {
        val result = AccountSwitchVerificationPolicy.appCliResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(
                runtimeEmail = "old@example.com",
                configuredEmail = TARGET
            ),
            credentialsMatchTarget = true,
            wasRunning = true,
            restartRequested = false,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.PENDING_RESTART, result.status)
    }

    @Test
    fun appSharedCredentialMatchWithoutInstalledAppIsConfigured() {
        val result = AccountSwitchVerificationPolicy.appCliResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(configuredEmail = TARGET),
            credentialsMatchTarget = true,
            wasRunning = false,
            restartRequested = true,
            isRunning = false,
            isUnavailable = true
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.CONFIGURED, result.status)
    }

    @Test
    fun appOldRuntimeAfterRestartFails() {
        val result = AccountSwitchVerificationPolicy.appCliResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(
                runtimeEmail = "old@example.com",
                configuredEmail = TARGET
            ),
            credentialsMatchTarget = true,
            wasRunning = true,
            restartRequested = true,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.FAILED, result.status)
    }

    @Test
    fun reportCarriesTheAccountUsedForCredentialApplication() {
        val tempDir = File.createTempFile("verification_report_", "_dir").apply {
            delete()
            mkdirs()
        }
        try {
            val account = AccountInfo(
                id = "acc_target",
                profile = AccountProfile(TARGET),
                tokens = OAuthTokens("access", "refresh", 8_000L, idToken = "id-token")
            )
            val request = AccountSwitchSession.Request(
                targetAccount = account,
                applyToIde = true,
                applyToAppCli = true,
                restartIde = true,
                restartApp = true,
                ideInstallationPath = null,
                appInstallationPath = null,
                proxyPort = null,
                progressCallback = null
            )
            val report = AccountSwitchVerifier(AccountStore(customRootDir = tempDir)).buildReport(
                request = request,
                ideWasRunning = true,
                appWasRunning = true,
                ideIsRunning = true,
                appIsRunning = true,
                ideObservations = AccountObservations(runtimeEmail = TARGET, configuredEmail = TARGET),
                appObservations = AccountObservations(runtimeEmail = TARGET, configuredEmail = TARGET),
                sharedCredentialTokenMatches = true,
                changes = AppliedChanges(sharedCredentialsWritten = true)
            )

            assertEquals(account, report.appliedAccount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun appRuntimeCannotHideMismatchedSharedCredentials() {
        val result = AccountSwitchVerificationPolicy.appCliResult(
            requested = true,
            targetEmail = TARGET,
            observations = AccountObservations(runtimeEmail = TARGET, configuredEmail = "old@example.com"),
            credentialsMatchTarget = false,
            wasRunning = true,
            restartRequested = true,
            isRunning = true,
            isUnavailable = false
        )

        assertEquals(HotSwitchCoordinator.TargetStatus.FAILED, result.status)
    }

    private companion object {
        const val TARGET = "target@example.com"
    }
}
