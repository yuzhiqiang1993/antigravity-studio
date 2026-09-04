package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import java.io.File
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageLogScannerTest {

    @Test
    fun testDiscoverTargetsFromTempDirectories() {
        val tempDir = File.createTempFile("usage_scan_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val convoDir = File(tempDir, "conversations")
            convoDir.mkdirs()

            val mockDb = File(convoDir, "02e01540-f4d6-482b-902e-9a5d0a790bd7.db")
            mockDb.writeBytes(byteArrayOf(1, 2, 3))

            val brainDir = File(tempDir, "brain/03ab7ef0-eade-496f-8a54-b506cbae04c6/.system_generated/logs")
            brainDir.mkdirs()
            val mockLog = File(brainDir, "transcript.jsonl")
            mockLog.writeText("{\"type\":\"MODEL\",\"tool_calls\":[]}\n")

            val scanner = UsageLogScanner(customRootDir = tempDir)
            val targets = scanner.discoverTargets()

            assertTrue(targets.isNotEmpty())
            val ids = targets.map { it.conversationId }
            assertTrue(ids.contains("02e01540-f4d6-482b-902e-9a5d0a790bd7"))
            assertTrue(ids.contains("03ab7ef0-eade-496f-8a54-b506cbae04c6"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testFallsBackToLanguageServerWhenLocalDatabaseIsIncomplete() = runBlocking {
        val tempDir = File.createTempFile("usage_scan_remote_", "_dir").apply {
            delete()
            mkdirs()
        }
        try {
            val id = "04ab7ef0-eade-496f-8a54-b506cbae04c6"
            val db = File(tempDir, "conversations/$id.db").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(0))
            }
            val reader = object : UsageRemoteReader {
                override suspend fun read(conversationId: String, appSource: String): RemoteUsageReadResult =
                    RemoteUsageReadResult(
                        entries = listOf(
                            TokenEntry(
                                input = 123,
                                output = 45,
                                modelObservation = ModelObservation(responseModelId = "vendor/model"),
                                timestamp = "2026-08-31T00:00:00Z",
                                conversationId = conversationId,
                                appSource = appSource
                            )
                        ),
                        complete = true
                    )
            }
            val scanner = UsageLogScanner(tempDir, reader)
            val parsed = scanner.parseConversationResults(scanner.discoverTargets())

            assertEquals(setOf("ide:$id"), parsed.successfulKeys)
            assertEquals(123L, parsed.conversations.single().entries.single().input)
            assertTrue(db.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMergesRemoteStepsMissingFromCompleteLocalDatabase() = runBlocking {
        val tempDir = File.createTempFile("usage_scan_steps_", "_dir").apply {
            delete()
            mkdirs()
        }
        try {
            val id = "05ab7ef0-eade-496f-8a54-b506cbae04c6"
            val db = File(tempDir, "conversations/$id.db")
            createDatabase(
                dbFile = db,
                metadataEntries = listOf(0 to metadataBlob(input = 10, responseId = "local-response")),
                stepMetadata = listOf(0 to stepMetadataBlob("local-response"))
            )
            val remoteCalls = mutableListOf<Pair<String, String>>()
            var remoteReady = false
            val reader = object : UsageRemoteReader {
                override suspend fun read(conversationId: String, appSource: String): RemoteUsageReadResult {
                    remoteCalls += conversationId to appSource
                    if (!remoteReady) {
                        return RemoteUsageReadResult(
                            entries = listOf(
                                TokenEntry(
                                    responseId = "local-response",
                                    input = 10,
                                    timestamp = "2026-08-31T00:00:00Z",
                                    conversationId = conversationId,
                                    appSource = appSource
                                )
                            ),
                            complete = true
                        )
                    }
                    return RemoteUsageReadResult(
                        entries = listOf(
                            TokenEntry(
                                responseId = "local-response",
                                input = 10,
                                timestamp = "2026-08-31T00:00:00Z",
                                conversationId = conversationId,
                                appSource = appSource
                            ),
                            TokenEntry(
                                responseId = "steps-only-response",
                                input = 20,
                                cacheRead = 30,
                                timestamp = "2026-08-31T00:01:00Z",
                                conversationId = conversationId,
                                appSource = appSource
                            )
                        ),
                        complete = true
                    )
                }
            }

            val scanner = UsageLogScanner(tempDir, reader)
            val initial = scanner.parseConversationResults(scanner.discoverTargets())
            assertTrue(remoteCalls.isEmpty())

            insertStepMetadata(db, idx = 1, metadata = stepMetadataBlob("steps-only-response"))
            val existingByKey = initial.conversations.associateBy { "${it.appSource}:${it.conversationId}" }
            val pending = scanner.parseConversationResults(scanner.discoverTargets(), existingByKey)
            assertEquals(setOf("ide:$id"), pending.successfulKeys)
            assertEquals(setOf("ide:$id"), pending.retryKeys)
            assertEquals(listOf("local-response"), pending.conversations.single().entries.map { it.responseId })

            remoteReady = true
            val pendingByKey = pending.conversations.associateBy { "${it.appSource}:${it.conversationId}" }
            val parsed = scanner.parseConversationResults(scanner.discoverTargets(), pendingByKey)

            assertEquals(listOf(id to "ide", id to "ide"), remoteCalls)
            assertEquals(setOf("ide:$id"), parsed.successfulKeys)
            assertEquals(emptySet(), parsed.failedKeys)
            assertEquals(emptySet(), parsed.retryKeys)
            assertEquals(
                setOf("local-response", "steps-only-response"),
                parsed.conversations.single().entries.mapNotNull { it.responseId }.toSet()
            )
            assertEquals(30L, parsed.conversations.single().entries.sumOf { it.input })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRetriesRemoteSupplementWhenLocalStepsCannotBeRead() = runBlocking {
        val tempDir = File.createTempFile("usage_scan_steps_error_", "_dir").apply {
            delete()
            mkdirs()
        }
        try {
            val id = "06ab7ef0-eade-496f-8a54-b506cbae04c6"
            val db = File(tempDir, "conversations/$id.db")
            createDatabase(
                dbFile = db,
                metadataEntries = listOf(0 to metadataBlob(input = 10, responseId = "local-response")),
                stepMetadata = emptyList()
            )
            DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
                connection.createStatement().use { it.execute("DROP TABLE steps") }
            }
            val reader = object : UsageRemoteReader {
                override suspend fun read(conversationId: String, appSource: String): RemoteUsageReadResult =
                    RemoteUsageReadResult(emptyList(), complete = false)
            }

            val scanner = UsageLogScanner(tempDir, reader)
            val parsed = scanner.parseConversationResults(scanner.discoverTargets())

            assertEquals(setOf("ide:$id"), parsed.successfulKeys)
            assertEquals(setOf("ide:$id"), parsed.retryKeys)
            assertEquals(10L, parsed.conversations.single().entries.single().input)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDiscoverRealIdeDirectory() {
        val userHome = System.getProperty("user.home") ?: return
        val ideDir = File(userHome, ".gemini/antigravity-ide/conversations")
        if (!ideDir.exists()) return

        val scanner = UsageLogScanner()
        val targets = scanner.discoverTargets()
        val ideTargets = targets.filter { it.appSource == "ide" }

        assertTrue(ideTargets.isNotEmpty(), "应该至少发现 1 个 IDE 会话")
        println("发现 IDE 会话数量: ${ideTargets.size}")
    }

    private fun createDatabase(
        dbFile: File,
        metadataEntries: List<Pair<Int, ByteArray>>,
        stepMetadata: List<Pair<Int, ByteArray>>
    ) {
        dbFile.parentFile?.mkdirs()
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE gen_metadata (idx INTEGER PRIMARY KEY, data BLOB, size INTEGER NOT NULL DEFAULT 0)")
                statement.execute("CREATE TABLE steps (idx INTEGER PRIMARY KEY, metadata BLOB)")
            }
            connection.prepareStatement("INSERT INTO gen_metadata(idx, data, size) VALUES (?, ?, ?)").use { statement ->
                for ((idx, data) in metadataEntries) {
                    statement.setInt(1, idx)
                    statement.setBytes(2, data)
                    statement.setInt(3, data.size)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.prepareStatement("INSERT INTO steps(idx, metadata) VALUES (?, ?)").use { statement ->
                for ((idx, metadata) in stepMetadata) {
                    statement.setInt(1, idx)
                    statement.setBytes(2, metadata)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun metadataBlob(input: Long, responseId: String): ByteArray {
        val usage = concat(
            varintField(2, input),
            varintField(3, 0),
            stringField(11, responseId)
        )
        return messageField(1, messageField(4, usage))
    }

    private fun insertStepMetadata(dbFile: File, idx: Int, metadata: ByteArray) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.prepareStatement("INSERT INTO steps(idx, metadata) VALUES (?, ?)").use { statement ->
                statement.setInt(1, idx)
                statement.setBytes(2, metadata)
                statement.executeUpdate()
            }
        }
    }

    private fun stepMetadataBlob(responseId: String): ByteArray = messageField(
        9,
        stringField(11, responseId)
    )

    private fun stringField(field: Int, value: String): ByteArray =
        messageField(field, value.toByteArray(Charsets.UTF_8))

    private fun messageField(field: Int, payload: ByteArray): ByteArray =
        concat(varint((field shl 3 or 2).toLong()), varint(payload.size.toLong()), payload)

    private fun varintField(field: Int, value: Long): ByteArray =
        concat(varint((field shl 3).toLong()), varint(value))

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val bytes = mutableListOf<Byte>()
        do {
            var current = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) current = current or 0x80
            bytes += current.toByte()
        } while (remaining != 0L)
        return bytes.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val result = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        for (array in arrays) {
            array.copyInto(result, offset)
            offset += array.size
        }
        return result
    }
}
