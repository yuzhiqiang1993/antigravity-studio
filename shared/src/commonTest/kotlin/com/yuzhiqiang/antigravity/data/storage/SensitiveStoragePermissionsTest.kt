package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitiveStoragePermissionsTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("sensitive-storage-test-")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun configAndAccountCredentialsUseOwnerOnlyPermissions() = runBlocking {
        if (!supportsPosixPermissions(tempDir)) return@runBlocking
        val root = tempDir.toFile()
        val configStore = ConfigStore(customRootDir = root)
        assertOwnerOnly(configStore.configFile.toPath())

        val account = AccountInfo(
            id = "account",
            profile = AccountProfile(email = "user@example.com"),
            tokens = OAuthTokens(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiryTimestamp = System.currentTimeMillis() / 1_000L + 3_600L
            ),
            isActive = true
        )
        val accountStore = AccountStore(customRootDir = root)
        accountStore.upsertAccount(account).getOrThrow()
        assertOwnerOnly(tempDir.resolve("accounts.v1.json"))

        accountStore.captureOfficialCredentialsSnapshot().getOrThrow().use { snapshot ->
            assertTrue(accountStore.syncToOfficialCredentials(account, snapshot))
        }
        assertOwnerOnly(accountStore.officialCredentialsFile().toPath())
    }

    private fun assertOwnerOnly(path: Path) {
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(path)
        )
    }

    private fun supportsPosixPermissions(path: Path): Boolean {
        return Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView::class.java)
    }
}
