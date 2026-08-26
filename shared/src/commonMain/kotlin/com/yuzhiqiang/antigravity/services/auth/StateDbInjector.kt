package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * 宿主底层 SQLite 数据库 (state.vscdb) 物理凭据注入器。
 * 严格按宿主类型（IDE vs App）隔离写入路径，杜绝切 IDE 账号污染 App 或反之。
 */
object StateDbInjector {

    enum class TargetHost {
        IDE,
        APP
    }

    /**
     * 单个宿主 canonical 状态数据库的目标键快照。
     */
    class Snapshot internal constructor(
        internal val database: DatabaseSnapshot
    )

    internal class DatabaseSnapshot(
        val file: File,
        val existed: Boolean,
        val values: Map<String, StoredValue>
    )

    internal class StoredValue(
        val existed: Boolean,
        val value: String?
    )

    /**
     * 将账号凭据写入指定的宿主 state.vscdb 数据库
     */
    fun inject(account: AccountInfo, targetHost: TargetHost): Boolean {
        val dbFiles = resolveCandidateDbFiles(targetHost)
        if (dbFiles.isEmpty()) {
            return false
        }

        val oauthPayload = ProtobufEncoder.createOAuthInfo(
            accessToken = account.tokens.accessToken,
            refreshToken = account.tokens.refreshToken,
            expirySeconds = account.tokens.expiryTimestamp,
            email = account.email,
            idToken = account.tokens.idToken
        )
        val oauthTopic = ProtobufEncoder.createUnifiedStateEntry("oauthTokenInfoSentinelKey", oauthPayload)

        val userStatusPayload = ProtobufEncoder.createMinimalUssStatus(
            email = account.email,
            name = account.profile.name
        )
        val userStatusTopic = ProtobufEncoder.createUnifiedStateEntry("userStatusSentinelKey", userStatusPayload)
        val updateEntries = listOf(
            "antigravityUnifiedStateSync.oauthToken" to oauthTopic,
            "antigravityUnifiedStateSync.userStatus" to userStatusTopic,
            "antigravityIdeUnifiedStateSync.oauthToken" to oauthTopic,
            "antigravityIdeUnifiedStateSync.userStatus" to userStatusTopic,
            "antigravity.oauthToken" to oauthTopic,
            "antigravity.userStatus" to userStatusTopic,
            "antigravityIde.oauthToken" to oauthTopic,
            "antigravityIde.userStatus" to userStatusTopic,
            "antigravityOnboarding" to "true"
        )

        var allSucceeded = true
        for (dbFile in dbFiles) {
            if (!injectDatabase(dbFile, updateEntries)) {
                allSucceeded = false
            }
        }

        return allSucceeded
    }

    /**
     * 捕获指定宿主 canonical 数据库及三个账号状态键的原始状态。
     */
    fun capture(targetHost: TargetHost): Result<Snapshot> {
        val dbFile = resolveCandidateDbFiles(targetHost).singleOrNull()
            ?: return Result.failure(IllegalStateException("宿主 canonical 状态数据库路径不唯一"))
        if (!dbFile.exists()) {
            return Result.success(
                Snapshot(DatabaseSnapshot(dbFile, existed = false, values = emptyMap()))
            )
        }
        if (!dbFile.isFile) {
            return Result.failure(IllegalStateException("宿主状态数据库路径不是普通文件: ${dbFile.absolutePath}"))
        }

        return captureDatabase(dbFile).map(::Snapshot)
    }

    /**
     * 在单库事务内恢复快照记录的三个账号状态键。
     */
    fun restore(snapshot: Snapshot): Boolean {
        val database = snapshot.database
        if (!database.existed) {
            return true
        }
        if (!database.file.isFile) {
            return false
        }
        return restoreDatabase(database)
    }

    /**
     * 解析目标宿主唯一的 canonical globalStorage 数据库路径。
     */
    fun resolveCandidateDbFiles(targetHost: TargetHost): List<File> {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { path -> path.isNotBlank() }
            ?: "$userHome/.config"

        val ideDbFile = when {
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/Antigravity IDE/User/globalStorage/state.vscdb")
            }
            os.contains("win") -> {
                File(appData, "Antigravity IDE/User/globalStorage/state.vscdb")
            }
            else -> {
                File(configHome, "Antigravity IDE/User/globalStorage/state.vscdb")
            }
        }

        val appDbFile = when {
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/Antigravity/User/globalStorage/state.vscdb")
            }
            os.contains("win") -> {
                File(appData, "Antigravity/User/globalStorage/state.vscdb")
            }
            else -> {
                File(configHome, "Antigravity/User/globalStorage/state.vscdb")
            }
        }

        return when (targetHost) {
            TargetHost.IDE -> listOf(ideDbFile)
            TargetHost.APP -> listOf(appDbFile)
        }
    }

    private fun injectDatabase(dbFile: File, entries: List<Pair<String, String>>): Boolean {
        if (!dbFile.isFile) {
            return false
        }

        return try {
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MILLIS")
                }
                connection.autoCommit = false
                try {
                    connection.createStatement().use { statement ->
                        statement.executeUpdate(
                            "CREATE TABLE IF NOT EXISTS ItemTable (key TEXT PRIMARY KEY, value TEXT)"
                        )
                    }
                    connection.prepareStatement(
                        "INSERT OR REPLACE INTO ItemTable (key, value) VALUES (?, ?)"
                    ).use { statement ->
                        for ((key, value) in entries) {
                            statement.setString(1, key)
                            statement.setString(2, value)
                            if (statement.executeUpdate() != 1) {
                                throw SQLException("状态键写入行数异常: key=$key")
                            }
                        }
                    }
                    connection.commit()
                } catch (exception: Exception) {
                    try {
                        connection.rollback()
                    } catch (rollbackException: Exception) {
                        exception.addSuppressed(rollbackException)
                    }
                    throw exception
                }
            }
            true
        } catch (exception: Exception) {
            System.err.println("写入宿主状态数据库失败: ${dbFile.absolutePath}")
            exception.printStackTrace(System.err)
            false
        }
    }

    private fun captureDatabase(dbFile: File): Result<DatabaseSnapshot> {
        return try {
            val values = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                configureConnection(connection)
                connection.autoCommit = false
                try {
                    val capturedValues = readStoredValues(connection)
                    connection.commit()
                    capturedValues
                } catch (exception: Exception) {
                    rollback(connection, exception)
                    throw exception
                }
            }
            Result.success(DatabaseSnapshot(dbFile, existed = true, values = values))
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun readStoredValues(connection: Connection): Map<String, StoredValue> {
        val values = TARGET_STATE_KEYS.associateWith { StoredValue(existed = false, value = null) }.toMutableMap()
        if (!hasItemTable(connection)) {
            return values
        }

        connection.prepareStatement(
            "SELECT key, value FROM ItemTable WHERE key IN (?, ?, ?)"
        ).use { statement ->
            TARGET_STATE_KEYS.forEachIndexed { index, key ->
                statement.setString(index + 1, key)
            }
            statement.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val key = resultSet.getString("key")
                    values[key] = StoredValue(existed = true, value = resultSet.getString("value"))
                }
            }
        }
        return values
    }

    private fun restoreDatabase(snapshot: DatabaseSnapshot): Boolean {
        return try {
            DriverManager.getConnection("jdbc:sqlite:${snapshot.file.absolutePath}").use { connection ->
                configureConnection(connection)
                connection.autoCommit = false
                try {
                    restoreStoredValues(connection, snapshot.values)
                    connection.commit()
                } catch (exception: Exception) {
                    rollback(connection, exception)
                    throw exception
                }
            }
            true
        } catch (exception: Exception) {
            System.err.println("恢复宿主状态数据库失败: ${snapshot.file.absolutePath}")
            exception.printStackTrace(System.err)
            false
        }
    }

    private fun restoreStoredValues(
        connection: Connection,
        values: Map<String, StoredValue>
    ) {
        val tableExists = hasItemTable(connection)
        if (!tableExists && values.values.none { storedValue -> storedValue.existed }) {
            return
        }
        if (!tableExists) {
            throw SQLException("宿主状态数据库缺少 ItemTable，无法恢复原值")
        }

        for (key in TARGET_STATE_KEYS) {
            val storedValue = values[key]
                ?: throw SQLException("状态快照缺少目标键: key=$key")
            if (storedValue.existed) {
                restoreStoredValue(connection, key, storedValue.value)
            } else {
                deleteStoredValue(connection, key)
            }
        }
    }

    private fun restoreStoredValue(
        connection: Connection,
        key: String,
        value: String?
    ) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO ItemTable (key, value) VALUES (?, ?)"
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            if (statement.executeUpdate() != 1) {
                throw SQLException("状态键恢复行数异常: key=$key")
            }
        }
    }

    private fun deleteStoredValue(connection: Connection, key: String) {
        connection.prepareStatement("DELETE FROM ItemTable WHERE key = ?").use { statement ->
            statement.setString(1, key)
            val affectedRows = statement.executeUpdate()
            if (affectedRows !in 0..1) {
                throw SQLException("状态键删除行数异常: key=$key")
            }
        }
    }

    private fun hasItemTable(connection: Connection): Boolean {
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'ItemTable' LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                return resultSet.next()
            }
        }
    }

    private fun configureConnection(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MILLIS")
        }
    }

    private fun rollback(connection: Connection, exception: Exception) {
        try {
            connection.rollback()
        } catch (rollbackException: Exception) {
            exception.addSuppressed(rollbackException)
        }
    }

    private val TARGET_STATE_KEYS = listOf(
        "antigravityUnifiedStateSync.oauthToken",
        "antigravityUnifiedStateSync.userStatus",
        "antigravityOnboarding"
    )
    private const val BUSY_TIMEOUT_MILLIS = 3_000
}
