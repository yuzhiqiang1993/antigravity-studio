package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqliteConversationReaderTest {

    @Test
    fun testReadRealConversationDbIfPresent() {
        val userHome = File(System.getProperty("user.home") ?: "")
        val convoDir = File(userHome, ".gemini/antigravity-ide/conversations")
        if (!convoDir.exists()) return

        val dbFiles = convoDir.listFiles { _, name -> name.endsWith(".db") } ?: return
        if (dbFiles.isEmpty()) return

        val sampleDb = dbFiles.firstOrNull { it.length() > 100_000 } ?: dbFiles[0]
        val convoId = sampleDb.name.substringBeforeLast(".db")
        val dbResult = SqliteConversationReader.readConversationDb(sampleDb, convoId, "ide")

        if (dbResult.entries.isNotEmpty()) {
            val first = dbResult.entries[0]
            assertTrue(first.input >= 0)
            assertTrue(first.output >= 0)
            assertTrue(
                listOf(
                    first.modelObservation.responseModelId,
                    first.modelObservation.runtimeModelId,
                    first.modelObservation.displayName
                ).any { !it.isNullOrBlank() }
            )
            assertTrue(dbResult.title.isNotBlank())
        }
    }

    @Test
    fun testReadsRawModelObservationAndMatchesStepTimestampByUuid() {
        val dbFile = File.createTempFile("usage-reader-", ".db")
        try {
            val stepId = "step-not-matching-row-index"
            val stepTimestamp = timestampMessage(1_785_850_000L, 123_000_000L)
            val stepMetadata = concat(
                messageField(1, stepTimestamp),
                stringField(12, stepId)
            )
            val metadata = metadataBlob(
                responseModel = "openai/gpt-5.6-luna",
                displayName = "GPT 5.6 Luna max(CPA)",
                runtimeModel = "MODEL_PLACEHOLDER_M400",
                stepId = stepId,
                createdAtSeconds = 0L
            )
            createDatabase(dbFile, listOf(7 to metadata), listOf(99 to stepMetadata))

            val result = SqliteConversationReader.readConversationDb(dbFile, "cid", "ide")

            assertTrue(result.complete)
            assertEquals(1, result.entries.size)
            val entry = result.entries.single()
            assertEquals(
                ModelObservation(
                    runtimeModelId = "MODEL_PLACEHOLDER_M400",
                    responseModelId = "openai/gpt-5.6-luna",
                    displayName = "GPT 5.6 Luna max(CPA)"
                ),
                entry.modelObservation
            )
            assertEquals(
                java.time.Instant.ofEpochSecond(1_785_850_000L, 123_000_000L).toString(),
                entry.timestamp
            )
        } finally {
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    }

    @Test
    fun testMalformedMetadataRowMarksReadIncompleteButKeepsValidEntries() {
        val dbFile = File.createTempFile("usage-reader-malformed-", ".db")
        try {
            createDatabase(
                dbFile,
                listOf(
                    0 to metadataBlob(responseId = "valid-row"),
                    1 to byteArrayOf(0)
                ),
                emptyList()
            )

            val result = SqliteConversationReader.readConversationDb(dbFile, "cid", "cli")

            assertFalse(result.complete)
            assertEquals(1, result.entries.size)
            assertEquals("valid-row", result.entries.single().responseId)
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun testTracksMissingUsageFieldsPerDimension() {
        val dbFile = File.createTempFile("usage-reader-missing-", ".db")
        try {
            val usage = concat(
                varintField(9, 7),
                stringField(11, "output-only")
            )
            createDatabase(
                dbFile,
                listOf(0 to messageField(1, messageField(4, usage))),
                emptyList()
            )

            val result = SqliteConversationReader.readConversationDb(dbFile, "cid", "ide")

            assertTrue(result.complete)
            assertEquals(7L, result.entries.single().output)
            assertEquals(
                listOf("input", "cache", "cacheWrite", "reasoning"),
                result.entries.single().missingUsageFields
            )
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun testUsesFileMtimeWhenChatAndStepTimestampsAreUnavailable() {
        val dbFile = File.createTempFile("usage-reader-mtime-", ".db")
        try {
            createDatabase(
                dbFile,
                listOf(0 to metadataBlob(createdAtSeconds = 0L)),
                emptyList()
            )
            val expected = java.time.Instant.ofEpochMilli(dbFile.lastModified()).toString()

            val result = SqliteConversationReader.readConversationDb(dbFile, "cid", "ide")

            assertTrue(result.complete)
            assertEquals(expected, result.entries.single().timestamp)
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun testIncrementalReadAppendsNewRowsAndFastPaths() {
        val dbFile = File.createTempFile("usage-reader-inc-", ".db")
        try {
            createDatabase(
                dbFile,
                listOf(
                    0 to metadataBlob(input = 10, responseId = "resp-0"),
                    1 to metadataBlob(input = 20, responseId = "resp-1")
                ),
                emptyList()
            )

            // 1. 初次全量读取
            val initialResult = SqliteConversationReader.readConversationDb(dbFile, "cid-inc", "ide")
            assertTrue(initialResult.complete)
            assertEquals(2, initialResult.entries.size)
            assertEquals(1, initialResult.maxIdx)
            assertEquals("resp-0", initialResult.entries[0].responseId)
            assertEquals("resp-1", initialResult.entries[1].responseId)

            // 2. 数据库追加第 3 条记录 (idx = 2)
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.prepareStatement("INSERT INTO gen_metadata(idx, data) VALUES (?, ?)").use { stmt ->
                    stmt.setInt(1, 2)
                    stmt.setBytes(2, metadataBlob(input = 30, responseId = "resp-2"))
                    stmt.executeUpdate()
                }
            }

            // 3. 增量读取：传入上次已知的 maxIdx = 1 以及现有条目
            val incrementalResult = SqliteConversationReader.readConversationDb(
                dbFile = dbFile,
                conversationId = "cid-inc",
                appSource = "ide",
                lastKnownIdx = initialResult.maxIdx,
                existingEntries = initialResult.entries,
                existingTitle = initialResult.title
            )
            assertTrue(incrementalResult.complete)
            assertEquals(3, incrementalResult.entries.size)
            assertEquals(2, incrementalResult.maxIdx)
            assertEquals(listOf("resp-0", "resp-1", "resp-2"), incrementalResult.entries.map { it.responseId })

            // 4. 快径测试：无新增数据时极速返回
            val fastPathResult = SqliteConversationReader.readConversationDb(
                dbFile = dbFile,
                conversationId = "cid-inc",
                appSource = "ide",
                lastKnownIdx = incrementalResult.maxIdx,
                existingEntries = incrementalResult.entries,
                existingTitle = incrementalResult.title
            )
            assertTrue(fastPathResult.complete)
            assertEquals(3, fastPathResult.entries.size)
            assertEquals(2, fastPathResult.maxIdx)

            // 5. 校验边界替换回滚：若数据库被删除重建且 idx=0 处的 responseId 发生变化
            dbFile.delete()
            createDatabase(
                dbFile,
                listOf(0 to metadataBlob(input = 99, responseId = "resp-rebuilt")),
                emptyList()
            )
            val fallbackResult = SqliteConversationReader.readConversationDb(
                dbFile = dbFile,
                conversationId = "cid-inc",
                appSource = "ide",
                lastKnownIdx = 2,
                existingEntries = incrementalResult.entries,
                existingTitle = incrementalResult.title
            )
            assertTrue(fallbackResult.complete)
            assertEquals(1, fallbackResult.entries.size)
            assertEquals(0, fallbackResult.maxIdx)
            assertEquals("resp-rebuilt", fallbackResult.entries.single().responseId)
            assertEquals(99L, fallbackResult.entries.single().input)
        } finally {
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    }

    private fun createDatabase(
        dbFile: File,
        metadataRows: List<Pair<Int, ByteArray>>,
        stepRows: List<Pair<Int, ByteArray>>
    ) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE gen_metadata (idx INTEGER, data BLOB)")
                statement.execute("CREATE TABLE steps (idx INTEGER, metadata BLOB)")
            }
            connection.prepareStatement("INSERT INTO gen_metadata(idx, data) VALUES (?, ?)").use { statement ->
                metadataRows.forEach { (idx, data) ->
                    statement.setInt(1, idx)
                    statement.setBytes(2, data)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.prepareStatement("INSERT INTO steps(idx, metadata) VALUES (?, ?)").use { statement ->
                stepRows.forEach { (idx, data) ->
                    statement.setInt(1, idx)
                    statement.setBytes(2, data)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun metadataBlob(
        input: Long = 10,
        output: Long = 7,
        cacheRead: Long = 2,
        cacheWrite: Long = 5,
        reasoning: Long = 1,
        responseId: String = "rid-default",
        responseModel: String? = null,
        displayName: String? = null,
        runtimeModel: String? = null,
        stepId: String? = null,
        createdAtSeconds: Long = 1_785_850_093L
    ): ByteArray {
        val usage = concat(
            varintField(1, input),
            varintField(3, cacheRead),
            varintField(5, cacheWrite),
            varintField(9, output),
            varintField(10, reasoning),
            stringField(11, responseId)
        )
        val chatFields = mutableListOf(
            messageField(4, usage),
            messageField(20, stringField(1, "model_enum") + stringField(2, runtimeModel ?: "MODEL_PLACEHOLDER_M132"))
        )
        if (createdAtSeconds > 0L) {
            chatFields += messageField(9, messageField(4, timestampMessage(createdAtSeconds, 900_000_000L)))
        }
        if (responseModel != null) chatFields += stringField(19, responseModel)
        if (displayName != null) chatFields += stringField(21, displayName)

        return concat(
            messageField(1, concat(*chatFields.toTypedArray())),
            stepId?.let { stringField(4, it) } ?: byteArrayOf()
        )
    }

    private fun timestampMessage(seconds: Long, nanos: Long): ByteArray = concat(
        varintField(1, seconds),
        varintField(2, nanos)
    )

    private fun stringField(field: Int, value: String): ByteArray =
        messageField(field, value.toByteArray(Charsets.UTF_8))

    private fun messageField(field: Int, payload: ByteArray): ByteArray =
        concat(varint((field shl 3 or 2).toLong()), varint(payload.size.toLong()), payload)

    private fun varintField(field: Int, value: Long): ByteArray =
        concat(varintFieldTag(field), varint(value))

    private fun varintFieldTag(field: Int): ByteArray = varint((field shl 3 or 0).toLong())

    private fun varint(value: Long): ByteArray {
        var current = value
        val output = ByteArrayOutputStream()
        do {
            var byte = (current and 0x7f).toInt()
            current = current ushr 7
            if (current != 0L) byte = byte or 0x80
            output.write(byte)
        } while (current != 0L)
        return output.toByteArray()
    }

    private fun concat(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArrayOutputStream()) { output, part ->
            output.write(part)
            output
        }.toByteArray()
}
