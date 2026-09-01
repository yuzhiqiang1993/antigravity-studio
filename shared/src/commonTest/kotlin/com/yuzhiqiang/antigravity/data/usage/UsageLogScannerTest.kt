package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import java.io.File
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
}
