package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import java.io.File
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StateDbInjectorTest {

    private lateinit var tempDir: File
    private lateinit var databaseFile: File

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("state_db_injector_", "_dir").apply {
            delete()
            mkdirs()
        }
        databaseFile = File(tempDir, "state.vscdb")
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").close()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun restoresAllCredentialKeysAndPreservesUnrelatedRows() {
        assertEquals(EXPECTED_STATE_KEYS, StateDbInjector.TARGET_STATE_KEYS.toSet())
        val originalValues = EXPECTED_STATE_KEYS.associateWith { key -> "old-$key" }
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE TABLE ItemTable (key TEXT PRIMARY KEY, value TEXT)")
            }
            connection.prepareStatement("INSERT INTO ItemTable (key, value) VALUES (?, ?)").use { statement ->
                (originalValues + ("unrelated" to "keep-me")).forEach { (key, value) ->
                    statement.setString(1, key)
                    statement.setString(2, value)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
        val snapshot = StateDbInjector.captureDatabase(databaseFile).getOrThrow()

        assertTrue(StateDbInjector.inject(testAccount(), databaseFile))
        val injected = readValues()
        EXPECTED_STATE_KEYS.forEach { key ->
            assertNotEquals(originalValues[key], injected[key], "目标键必须全部写入: $key")
        }
        assertEquals(1, OAUTH_STATE_KEYS.map(injected::get).toSet().size)
        assertEquals(1, USER_STATUS_STATE_KEYS.map(injected::get).toSet().size)
        assertEquals("true", injected["antigravityOnboarding"])
        assertTrue(StateDbInjector.restoreDatabase(snapshot))

        val restored = readValues()
        originalValues.forEach { (key, value) -> assertEquals(value, restored[key]) }
        assertEquals("keep-me", restored["unrelated"])
    }

    @Test
    fun dropsItemTableWhenInjectionCreatedIt() {
        val snapshot = StateDbInjector.captureDatabase(databaseFile).getOrThrow()
        assertFalse(snapshot.itemTableExisted)

        assertTrue(StateDbInjector.inject(testAccount(), databaseFile))
        assertTrue(hasItemTable())
        assertTrue(StateDbInjector.restoreDatabase(snapshot))
        assertFalse(hasItemTable())
    }

    private fun testAccount(): AccountInfo {
        return AccountInfo(
            id = "acc_state_db",
            profile = AccountProfile("state-db@example.com", name = "State DB"),
            tokens = OAuthTokens(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                expiryTimestamp = 4_000L,
                idToken = "new-id"
            )
        )
    }

    private fun readValues(): Map<String, String?> {
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT key, value FROM ItemTable").use { resultSet ->
                    buildMap {
                        while (resultSet.next()) {
                            put(resultSet.getString("key"), resultSet.getString("value"))
                        }
                    }
                }
            }
        }
    }

    private fun hasItemTable(): Boolean {
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'ItemTable' LIMIT 1"
            ).use { statement ->
                statement.executeQuery().use { resultSet -> resultSet.next() }
            }
        }
    }

    private companion object {
        val OAUTH_STATE_KEYS = setOf(
            "antigravityUnifiedStateSync.oauthToken",
            "antigravityIdeUnifiedStateSync.oauthToken",
            "antigravity.oauthToken",
            "antigravityIde.oauthToken"
        )
        val USER_STATUS_STATE_KEYS = setOf(
            "antigravityUnifiedStateSync.userStatus",
            "antigravityIdeUnifiedStateSync.userStatus",
            "antigravity.userStatus",
            "antigravityIde.userStatus"
        )
        val EXPECTED_STATE_KEYS = OAUTH_STATE_KEYS + USER_STATUS_STATE_KEYS + "antigravityOnboarding"
    }
}
