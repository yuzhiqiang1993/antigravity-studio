package com.yuzhiqiang.antigravity.data

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

class AccountStoreTest {

    private lateinit var tempDir: File
    private lateinit var accountStore: AccountStore

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("account_store_test_", "_dir").apply {
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
    fun testOAuthTokensExpiryCalculation() {
        val now = System.currentTimeMillis() / 1000L
        val expiredTokens = OAuthTokens(
            accessToken = "token1",
            refreshToken = "refresh1",
            expiryTimestamp = now - 10L
        )
        assertTrue(expiredTokens.isExpired())
        assertTrue(expiredTokens.isExpiringSoon(300L))
        assertEquals(0L, expiredTokens.remainingSeconds())

        val expiringSoonTokens = OAuthTokens(
            accessToken = "token2",
            refreshToken = "refresh2",
            expiryTimestamp = now + 120L // 2分钟后过期
        )
        assertFalse(expiringSoonTokens.isExpired())
        assertTrue(expiringSoonTokens.isExpiringSoon(300L))

        val healthyTokens = OAuthTokens(
            accessToken = "token3",
            refreshToken = "refresh3",
            expiryTimestamp = now + 3600L // 1小时后过期
        )
        assertFalse(healthyTokens.isExpired())
        assertFalse(healthyTokens.isExpiringSoon(300L))
        assertTrue(healthyTokens.remainingSeconds() > 3000L)
    }

    @Test
    fun testUpsertAndSwitchAccount() = runBlocking {
        val account1 = AccountInfo(
            id = "acc_1",
            profile = AccountProfile(
                email = "user1@gmail.com",
                name = "User One",
                tier = AccountTier.FREE
            ),
            tokens = OAuthTokens(
                accessToken = "access_1",
                refreshToken = "refresh_1",
                expiryTimestamp = System.currentTimeMillis() / 1000L + 3600L
            ),
            isActive = true
        )

        accountStore.upsertAccount(account1)
        assertEquals(1, accountStore.currentAccounts().size)
        assertEquals("user1@gmail.com", accountStore.currentActiveAccount()?.email)

        val account2 = AccountInfo(
            id = "acc_2",
            profile = AccountProfile(
                email = "user2@gmail.com",
                name = "User Two",
                tier = AccountTier.PRO
            ),
            tokens = OAuthTokens(
                accessToken = "access_2",
                refreshToken = "refresh_2",
                expiryTimestamp = System.currentTimeMillis() / 1000L + 7200L
            ),
            isActive = false
        )

        accountStore.upsertAccount(account2)
        assertEquals(2, accountStore.currentAccounts().size)
        assertEquals("user1@gmail.com", accountStore.currentActiveAccount()?.email)

        // 切换激活账号
        val switchResult = accountStore.setActiveAccount("acc_2")
        assertTrue(switchResult.isSuccess)
        assertEquals("user2@gmail.com", accountStore.currentActiveAccount()?.email)

        // 重新从磁盘加载校验持久化
        val reloadedStore = AccountStore(customRootDir = tempDir)
        assertEquals(2, reloadedStore.currentAccounts().size)
        assertEquals("user2@gmail.com", reloadedStore.currentActiveAccount()?.email)
    }

    @Test
    fun testRemoveAccount() = runBlocking {
        val account1 = AccountInfo(
            id = "acc_1",
            profile = AccountProfile(email = "user1@gmail.com"),
            tokens = OAuthTokens("a1", "r1", System.currentTimeMillis() / 1000L + 3600L),
            isActive = true
        )
        val account2 = AccountInfo(
            id = "acc_2",
            profile = AccountProfile(email = "user2@gmail.com"),
            tokens = OAuthTokens("a2", "r2", System.currentTimeMillis() / 1000L + 3600L),
            isActive = false
        )

        accountStore.upsertAccount(account1)
        accountStore.upsertAccount(account2)

        // 移除激活账号，验证自动降级到剩余账号
        accountStore.removeAccount("acc_1")
        assertEquals(1, accountStore.currentAccounts().size)
        assertEquals("user2@gmail.com", accountStore.currentActiveAccount()?.email)

        // 移除最后一个账号
        accountStore.removeAccount("acc_2")
        assertTrue(accountStore.currentAccounts().isEmpty())
        assertNull(accountStore.currentActiveAccount())
    }

    @Test
    fun testUpdateNoteAndTogglePin() = runBlocking {
        val account = AccountInfo(
            id = "acc_note",
            profile = AccountProfile(email = "note@gmail.com", name = "Original Name"),
            tokens = OAuthTokens("a", "r", System.currentTimeMillis() / 1000L + 3600L)
        )
        accountStore.upsertAccount(account)

        // 初始备注为空，displayName 为 Original Name
        assertEquals("Original Name", accountStore.currentAccounts().first().displayName)
        assertFalse(accountStore.currentAccounts().first().isPinned)

        // 更新备注
        accountStore.updateAccountNote("acc_note", "开发主号")
        assertEquals("开发主号", accountStore.currentAccounts().first().displayName)
        assertEquals("开发主号", accountStore.currentAccounts().first().customNote)

        // 切换置顶
        accountStore.togglePinAccount("acc_note")
        assertTrue(accountStore.currentAccounts().first().isPinned)

        accountStore.togglePinAccount("acc_note")
        assertFalse(accountStore.currentAccounts().first().isPinned)
    }

    @Test
    fun testRefreshResultPreservesMissingIdentityTokens() {
        val current = OAuthTokens(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiryTimestamp = 1_000L,
            idToken = "old-id"
        )
        val refreshed = OAuthTokens(
            accessToken = "new-access",
            refreshToken = "",
            expiryTimestamp = 2_000L,
            idToken = null
        )

        val merged = current.mergeRefreshResult(refreshed)

        assertEquals("new-access", merged.accessToken)
        assertEquals("old-refresh", merged.refreshToken)
        assertEquals("old-id", merged.idToken)
        assertEquals(2_000L, merged.expiryTimestamp)
    }

    @Test
    fun testUpdateTokensUsesRefreshMergeRules() = runBlocking {
        val account = AccountInfo(
            id = "acc_update_tokens",
            profile = AccountProfile("update@example.com"),
            tokens = OAuthTokens(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiryTimestamp = 1_000L,
                idToken = "old-id"
            ),
            isActive = true
        )
        accountStore.upsertAccount(account).getOrThrow()

        accountStore.updateTokens(
            email = account.email,
            tokens = OAuthTokens(
                accessToken = "new-access",
                refreshToken = "",
                expiryTimestamp = 2_000L,
                tokenType = "",
                idToken = ""
            )
        ).getOrThrow()

        val updated = accountStore.currentActiveAccount()!!
        assertEquals("new-access", updated.tokens.accessToken)
        assertEquals("old-refresh", updated.tokens.refreshToken)
        assertEquals("Bearer", updated.tokens.tokenType)
        assertEquals("old-id", updated.tokens.idToken)
    }

    @Test
    fun testCommitSwitchedAccountUpdatesTokensAndActiveStateOnce() = runBlocking {
        val first = AccountInfo(
            id = "acc_first",
            profile = AccountProfile("first@example.com"),
            tokens = OAuthTokens("first-access", "first-refresh", 1_000L),
            isActive = true
        )
        val target = AccountInfo(
            id = "acc_target",
            profile = AccountProfile("target@example.com", name = "Target"),
            tokens = OAuthTokens("old-access", "old-refresh", 1_000L, idToken = "old-id"),
            isPinned = true,
            customNote = "保留备注"
        )
        accountStore.upsertAccount(first).getOrThrow()
        accountStore.upsertAccount(target).getOrThrow()

        val refreshedTarget = target.copy(
            tokens = OAuthTokens(
                accessToken = "new-access",
                refreshToken = "",
                expiryTimestamp = 2_000L,
                idToken = null
            )
        )
        val committed = accountStore.commitSwitchedAccount(refreshedTarget).getOrThrow()

        assertEquals("acc_target", accountStore.currentActiveAccount()?.id)
        assertEquals("new-access", committed.tokens.accessToken)
        assertEquals("old-refresh", committed.tokens.refreshToken)
        assertEquals("old-id", committed.tokens.idToken)
        assertTrue(committed.isPinned)
        assertEquals("保留备注", committed.customNote)
        assertEquals(1, accountStore.currentAccounts().count { it.isActive })

        val reloaded = AccountStore(customRootDir = tempDir)
        assertEquals("acc_target", reloaded.currentActiveAccount()?.id)
        assertEquals("new-access", reloaded.currentActiveAccount()?.tokens?.accessToken)
    }

    @Test
    fun testOfficialCredentialSnapshotRestoresExactBytes() {
        val credentialFile = accountStore.officialCredentialsFile()
        val originalBytes = byteArrayOf(0x7B, 0x0A, 0x20, 0x7D)
        credentialFile.parentFile.mkdirs()
        credentialFile.writeBytes(originalBytes)
        val snapshot = accountStore.captureOfficialCredentialsSnapshot().getOrThrow()
        val account = AccountInfo(
            id = "acc_credentials",
            profile = AccountProfile("credentials@example.com"),
            tokens = OAuthTokens("access", "refresh", 3_000L, idToken = "id-token")
        )

        assertTrue(accountStore.syncToOfficialCredentials(account, snapshot))
        assertTrue(credentialFile.readText().contains("credentials@example.com"))
        assertTrue(accountStore.restoreOfficialCredentialsSnapshot(snapshot))
        assertContentEquals(originalBytes, credentialFile.readBytes())
    }

    @Test
    fun testOfficialCredentialSnapshotRestoresCompleteWritePlan() {
        val officialFile = accountStore.officialCredentialsFile()
        val mirrorDirectory = File(tempDir, "mirrors").apply { mkdirs() }
        val existingMirror = File(mirrorDirectory, "existing.json").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val newMirror = File(mirrorDirectory, "new.json")
        officialFile.writeText("{\"marker\":\"official\"}")
        val officialBytes = officialFile.readBytes()
        val mirrorBytes = existingMirror.readBytes()
        val snapshot = accountStore.captureOfficialCredentialsSnapshot(
            listOf(officialFile, existingMirror, newMirror, File(existingMirror.absolutePath))
        ).getOrThrow()
        val account = AccountInfo(
            id = "acc_credentials",
            profile = AccountProfile("credentials@example.com"),
            tokens = OAuthTokens("access", "refresh", 3_000L, idToken = "id-token")
        )

        assertEquals(3, snapshot.files.size)
        assertTrue(accountStore.syncToOfficialCredentials(account, snapshot))
        snapshot.files.forEach { fileSnapshot ->
            assertTrue(fileSnapshot.file.readText().contains("credentials@example.com"))
        }
        assertTrue(accountStore.restoreOfficialCredentialsSnapshot(snapshot))
        assertContentEquals(officialBytes, officialFile.readBytes())
        assertContentEquals(mirrorBytes, existingMirror.readBytes())
        assertFalse(newMirror.exists())
        snapshot.close()
    }

    @Test
    fun testOfficialCredentialSnapshotDeletesNewFileOnRollback() {
        val credentialFile = accountStore.officialCredentialsFile()
        assertFalse(credentialFile.exists())
        val snapshot = accountStore.captureOfficialCredentialsSnapshot().getOrThrow()
        val account = AccountInfo(
            id = "acc_credentials",
            profile = AccountProfile("credentials@example.com"),
            tokens = OAuthTokens("access", "refresh", 3_000L)
        )

        assertTrue(accountStore.syncToOfficialCredentials(account, snapshot))
        assertTrue(credentialFile.exists())
        assertTrue(accountStore.restoreOfficialCredentialsSnapshot(snapshot))
        assertFalse(credentialFile.exists())
    }

    @Test
    fun testCleanInvalidAccountsAndExport() = runBlocking {
        val now = System.currentTimeMillis() / 1000L
        val activeAcc = AccountInfo(
            id = "acc_active",
            profile = AccountProfile("active@gmail.com"),
            tokens = OAuthTokens("a1", "r1", now + 3600L),
            isActive = true,
            status = AccountStatus.ACTIVE
        )
        val expiredAcc = AccountInfo(
            id = "acc_expired",
            profile = AccountProfile("expired@gmail.com"),
            tokens = OAuthTokens("a2", "r2", now - 100L),
            isActive = false,
            status = AccountStatus.ACTIVE
        )
        val errorAcc = AccountInfo(
            id = "acc_error",
            profile = AccountProfile("error@gmail.com"),
            tokens = OAuthTokens("a3", "r3", now + 3600L),
            isActive = false,
            status = AccountStatus.ERROR
        )

        accountStore.upsertAccount(activeAcc)
        accountStore.upsertAccount(expiredAcc)
        accountStore.upsertAccount(errorAcc)
        assertEquals(3, accountStore.currentAccounts().size)

        // 导出测试
        val jsonExport = accountStore.exportAccountsJson()
        assertTrue(jsonExport.contains("active@gmail.com"))
        assertTrue(jsonExport.contains("expired@gmail.com"))

        // 清理失效与过期账号
        val cleanResult = accountStore.cleanInvalidAccounts()
        assertTrue(cleanResult.isSuccess)
        assertEquals(2, cleanResult.getOrThrow()) // 清理掉了 expired 和 error

        assertEquals(1, accountStore.currentAccounts().size)
        assertEquals("active@gmail.com", accountStore.currentAccounts().first().email)
    }

    @Test
    fun testActiveAccountSnapshotSerialization() = runBlocking {
        val acc = AccountInfo(
            id = "test_acc_123",
            profile = AccountProfile("test_user@example.com"),
            tokens = OAuthTokens("access", "refresh", System.currentTimeMillis() / 1000L + 3600L),
            isActive = true,
            status = AccountStatus.ACTIVE
        )
        accountStore.upsertAccount(acc)
        accountStore.commitSwitchedAccount(acc)

        val snapshotFile = File(tempDir, "active-account.json")
        assertTrue(snapshotFile.exists(), "active-account.json should exist")
        val content = snapshotFile.readText()
        assertTrue(content.contains("test_acc_123"))
        assertTrue(content.contains("test_user@example.com"))
        assertTrue(content.contains("antigravity-studio"))
    }
}
