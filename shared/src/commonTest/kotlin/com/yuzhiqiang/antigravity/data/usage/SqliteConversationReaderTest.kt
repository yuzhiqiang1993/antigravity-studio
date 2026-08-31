package com.yuzhiqiang.antigravity.data.usage

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
            assertTrue(first.model.isNotBlank())
            assertTrue(dbResult.title.isNotBlank())
        }
    }

    @Test
    fun testReadsModelIdentityAndMatchesStepTimestampByUuid() {
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
            assertEquals("openai/gpt-5.6-luna", entry.model)
            assertEquals("GPT 5.6 Luna max(CPA)", entry.modelDisplayName)
            assertEquals("openai/gpt-5.6-luna", entry.modelCanonicalId)
            assertEquals("MODEL_PLACEHOLDER_M400", entry.modelRuntimeId)
            assertEquals("session-display:gpt-5-6-luna-max-cpa", entry.modelAggregationId)
            assertEquals(listOf("openai/gpt-5.6-luna"), entry.modelPricingIds)
            assertEquals("response-model", entry.modelEvidenceSource)
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
