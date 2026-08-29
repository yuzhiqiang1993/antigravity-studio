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

class AccountSwitchCredentialApplierTest {

    private lateinit var tempDir: File
    private lateinit var accountStore: AccountStore

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("credential_applier_", "_dir").apply {
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
    fun rotatedRefreshTokenIsPersistedWithoutChangingActiveAccount() = runBlocking {
        val active = account("active", "active@example.com", "active-refresh", isActive = true)
        val target = account(
            id = "target",
            email = "target@example.com",
            refreshToken = "old-refresh",
            idToken = "old-id"
        )
        accountStore.upsertAccount(active).getOrThrow()
        accountStore.upsertAccount(target).getOrThrow()
        val applier = AccountSwitchCredentialApplier(
            accountStore = accountStore,
            tokenRefresher = {
                Result.success(
                    OAuthTokens(
                        accessToken = "new-access",
                        refreshToken = "rotated-refresh",
                        expiryTimestamp = 9_000L,
                        idToken = null
                    )
                )
            },
            systemCredentialStore = NoOpSystemCredentialStore
        )

        val prepared = applier.prepareTargetAccount(request(target))

        assertEquals("rotated-refresh", prepared.tokens.refreshToken)
        assertEquals("old-id", prepared.tokens.idToken)
        assertEquals("active", accountStore.currentActiveAccount()?.id)
        val persistedTarget = accountStore.currentAccounts().first { it.id == "target" }
        assertEquals("new-access", persistedTarget.tokens.accessToken)
        assertEquals("rotated-refresh", persistedTarget.tokens.refreshToken)
        assertEquals("old-id", persistedTarget.tokens.idToken)
    }

    private fun request(target: AccountInfo): AccountSwitchSession.Request {
        return AccountSwitchSession.Request(
            targetAccount = target,
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

    private fun account(
        id: String,
        email: String,
        refreshToken: String,
        idToken: String? = null,
        isActive: Boolean = false
    ): AccountInfo {
        return AccountInfo(
            id = id,
            profile = AccountProfile(email),
            tokens = OAuthTokens(
                accessToken = "old-access-$id",
                refreshToken = refreshToken,
                expiryTimestamp = 1_000L,
                idToken = idToken
            ),
            isActive = isActive
        )
    }

    private data object NoOpSystemCredentialStore : SystemCredentialStore {
        override fun capture(): Result<SystemCredentialSnapshot> {
            return Result.success(SystemCredentialSnapshot.NoOp)
        }

        override fun inject(account: AccountInfo): Result<Unit> {
            return Result.success(Unit)
        }

        override fun restore(snapshot: SystemCredentialSnapshot): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
