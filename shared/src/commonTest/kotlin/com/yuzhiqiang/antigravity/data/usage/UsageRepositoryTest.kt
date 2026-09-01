package com.yuzhiqiang.antigravity.data.usage

import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageRepositoryTest {

    @Test
    fun testRemovesDeletedConversationsAndRestoresDiskCache() = runBlocking {
        val root = createTempRoot()
        try {
            val id = "11111111-1111-4111-8111-111111111111"
            val log = transcriptFile(root, id)
            log.writeText(
                """{"type":"MODEL","created_at":"${Instant.now()}","usage":{"input_tokens":10,"output_tokens":2,"model":"gpt-4o","response_id":"r1"}}""" + "\n"
            )

            val repository = repository(root)
            repository.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)
            val first = repository.refresh(force = false).getOrThrow()
            assertEquals(10L, first.totalInput)
            assertEquals(1L, first.totalConversations)

            val restored = repository(root)
            restored.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)
            assertEquals(10L, restored.usageStats.value.totalInput)

            log.writeText("")
            val afterTruncate = repository.refresh(force = false).getOrThrow()
            assertEquals(10L, afterTruncate.totalInput)

            log.delete()
            val afterDelete = repository.refresh(force = false).getOrThrow()
            assertEquals(0L, afterDelete.totalInput)
            assertEquals(0L, afterDelete.totalConversations)
            assertTrue(!File(root, ".deep_stats_cache.json").readText().contains(id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testIgnoresOutdatedDiskCacheVersion() = runBlocking {
        val root = createTempRoot()
        try {
            File(root, ".deep_stats_cache.json").writeText(
                """
                {
                  "version": 1,
                  "updatedAt": 1,
                  "sourceMtimes": {"ide:cached": 1},
                  "conversations": [
                    {
                      "conversationId": "cached",
                      "appSource": "ide",
                      "entries": [{"input": 999, "model": "gpt-4o"}]
                    }
                  ]
                }
                """.trimIndent()
            )

            val repository = repository(root)
            repository.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)

            assertEquals(0L, repository.usageStats.value.totalInput)
            assertEquals(0L, repository.usageStats.value.totalConversations)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testFallsBackToLocalTranscriptWhenSqliteReadIsIncomplete() = runBlocking {
        val root = createTempRoot()
        val id = "44444444-4444-4444-8444-444444444444"
        try {
            databaseFile(root, id).writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            val transcript = transcriptFile(root, id)
            transcript.writeText(
                """{"created_at":"2026-08-31T00:00:00Z","usage":{"input_tokens":40,"output_tokens":5,"cache_read_tokens":0,"cache_write_tokens":0,"reasoning_tokens":0,"model":"gpt-4o","response_id":"fallback"}}""" + "\n"
            )

            val repository = repository(root)
            repository.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)
            val stats = repository.refresh(false).getOrThrow()

            assertEquals(40L, stats.totalInput)
            assertEquals(5L, stats.totalOutput)
            assertEquals(1L, stats.totalConversations)

            transcript.writeText(
                """{"created_at":"2026-08-31T00:00:00Z","usage":{"input_tokens":60,"output_tokens":5,"cache_read_tokens":0,"cache_write_tokens":0,"reasoning_tokens":0,"model":"gpt-4o","response_id":"fallback"}}""" + "\n"
            )
            transcript.setLastModified(transcript.lastModified() + 2_000L)
            assertEquals(60L, repository.refresh(false).getOrThrow().totalInput)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testMtimeRollbackStillReplacesConversation() = runBlocking {
        val root = createTempRoot()
        val db = databaseFile(root, "22222222-2222-4222-8222-222222222222")
        try {
            createMetadataDatabase(db, input = 10, responseId = "old")
            db.setLastModified(2_000L)
            val repository = repository(root)
            repository.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)
            assertEquals(10L, repository.refresh(false).getOrThrow().totalInput)

            db.delete()
            createMetadataDatabase(db, input = 20, responseId = "new")
            // 新文件的 mtime 比上次检查点更早，仍必须重新读取。
            db.setLastModified(1_000L)

            val refreshed = repository.refresh(false).getOrThrow()
            assertEquals(20L, refreshed.totalInput)
            assertEquals(20L, refreshed.modelBuckets.single().input)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testFailedReadDoesNotAdvanceCheckpointAndRetriesAfterReplacement() = runBlocking {
        val root = createTempRoot()
        val db = databaseFile(root, "33333333-3333-4333-8333-333333333333")
        try {
            db.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            val repository = repository(root)
            repository.setTimeRange(com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange.ALL_TIME)
            assertEquals(0L, repository.refresh(false).getOrThrow().totalInput)

            db.delete()
            createMetadataDatabase(db, input = 30, responseId = "retried")
            val retried = repository.refresh(false).getOrThrow()
            assertEquals(30L, retried.totalInput)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun repository(root: File): UsageRepository = UsageRepository(
        pricingService = PricingCatalogService(customRootDir = root),
        customRootDir = root,
        refreshPricingCatalog = false
    )

    private fun createTempRoot(): File = File.createTempFile("usage-repository-", "-root").apply {
        delete()
        mkdirs()
    }

    private fun transcriptFile(root: File, id: String): File =
        File(root, "brain/$id/.system_generated/logs/transcript.jsonl").apply {
            parentFile?.mkdirs()
        }

    private fun databaseFile(root: File, id: String): File =
        File(root, "conversations/$id.db").apply {
            parentFile?.mkdirs()
        }

    private fun createMetadataDatabase(dbFile: File, input: Long, responseId: String) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE gen_metadata (idx INTEGER, data BLOB)")
                statement.execute("CREATE TABLE steps (idx INTEGER, metadata BLOB)")
            }
            connection.prepareStatement("INSERT INTO gen_metadata(idx, data) VALUES (?, ?)").use { statement ->
                statement.setInt(1, 0)
                statement.setBytes(2, metadataBlob(input, responseId))
                statement.executeUpdate()
            }
        }
    }

    private fun metadataBlob(input: Long, responseId: String): ByteArray {
        val usage = concat(
            varintField(1, input),
            varintField(9, 2),
            stringField(11, responseId)
        )
        return messageField(1, messageField(4, usage))
    }

    private fun stringField(field: Int, value: String): ByteArray =
        messageField(field, value.toByteArray(Charsets.UTF_8))

    private fun messageField(field: Int, payload: ByteArray): ByteArray =
        concat(varint((field shl 3 or 2).toLong()), varint(payload.size.toLong()), payload)

    private fun varintField(field: Int, value: Long): ByteArray =
        concat(varint((field shl 3).toLong()), varint(value))

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
