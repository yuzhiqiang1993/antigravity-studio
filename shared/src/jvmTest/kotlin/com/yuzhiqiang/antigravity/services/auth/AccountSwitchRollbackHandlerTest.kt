package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountSwitchRollbackHandlerTest {

    private lateinit var tempDir: File
    private lateinit var accountStore: AccountStore

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("account_switch_rollback_", "_dir").apply {
            delete()
            mkdirs()
        }
        accountStore = AccountStore(customRootDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun restoresSystemCredentialAfterMutationAttempt() = runBlocking {
        val snapshot = SystemCredentialSnapshot.Absent(SystemCredentialBackend.MACOS_KEYCHAIN)
        val store = FakeSystemCredentialStore()
        val handler = AccountSwitchRollbackHandler(accountStore, store)

        val errors = handler.rollbackNonCancellable(
            request = request(),
            originalState = originalState(snapshot),
            ideWasRunning = false,
            appWasRunning = false,
            changes = AppliedChanges(systemCredentialWriteAttempted = true)
        )

        assertTrue(errors.isEmpty())
        assertSame(snapshot, store.restoredSnapshot)
    }

    @Test
    fun reportsSystemCredentialRestoreFailureWithoutStoppingOtherRollback() = runBlocking {
        val snapshot = SystemCredentialSnapshot.Absent(SystemCredentialBackend.MACOS_KEYCHAIN)
        val store = FakeSystemCredentialStore(restoreFailure = IllegalStateException("restore failed"))
        val handler = AccountSwitchRollbackHandler(accountStore, store)

        val errors = handler.rollbackNonCancellable(
            request = request(),
            originalState = originalState(snapshot),
            ideWasRunning = false,
            appWasRunning = false,
            changes = AppliedChanges(systemCredentialWriteAttempted = true)
        )

        assertEquals(listOf("系统安全凭据恢复失败"), errors)
        assertSame(snapshot, store.restoredSnapshot)
    }

    private fun originalState(snapshot: SystemCredentialSnapshot): OriginalState {
        return OriginalState(
            ideSnapshot = null,
            appDbSnapshot = null,
            sharedCredentialsSnapshot = null,
            jetskiTokenSnapshot = null,
            systemCredentialSnapshot = snapshot
        )
    }

    private fun request(): AccountSwitchSession.Request {
        return AccountSwitchSession.Request(
            targetAccount = AccountInfo(
                id = "acc_target",
                profile = AccountProfile("target@example.com"),
                tokens = OAuthTokens("access", "refresh", 5_000L)
            ),
            applyToIde = false,
            applyToAppCli = true,
            restartIde = false,
            restartApp = false,
            ideInstallationPath = null,
            appInstallationPath = null,
            proxyPort = null,
            progressCallback = null
        )
    }

    private class FakeSystemCredentialStore(
        private val restoreFailure: Throwable? = null
    ) : SystemCredentialStore {
        var restoredSnapshot: SystemCredentialSnapshot? = null

        override fun capture(): Result<SystemCredentialSnapshot> {
            return Result.success(SystemCredentialSnapshot.NoOp)
        }

        override fun inject(account: AccountInfo): Result<Unit> {
            return Result.success(Unit)
        }

        override fun restore(snapshot: SystemCredentialSnapshot): Result<Unit> {
            restoredSnapshot = snapshot
            return restoreFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
        }
    }
}
