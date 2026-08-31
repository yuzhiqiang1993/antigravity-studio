package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.usage.ConversationUsageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

private val USAGE_UUID_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

data class ScannedConversationTarget(
    val conversationId: String,
    val appSource: String,
    val targetFile: File,
    val isDatabase: Boolean,
    /** db、WAL 和共享日志中可见的最新修改时间。 */
    val lastModified: Long
)

data class UsageScanSnapshot(
    val targets: List<ScannedConversationTarget>,
    val complete: Boolean,
    val incompleteSources: Set<String> = emptySet()
)

data class UsageParseBatchResult(
    val conversations: List<ConversationUsageData>,
    val successfulKeys: Set<String>,
    val failedKeys: Set<String>
)

private data class ParsedTargetResult(
    val key: String,
    val conversation: ConversationUsageData?,
    val complete: Boolean
)

/**
 * 本地会话与轨迹日志扫描器，并在 SQLite 不完整时提供可选 LS 回退。
 *
 * 这里不把来源筛选提前到扫描阶段：缓存需要保留所有来源，重新勾选时才能
 * 直接恢复。扫描库存不完整时也会向仓库报告，仓库不能据此删除旧会话。
 */
class UsageLogScanner(
    private val customRootDir: File? = null,
    private val remoteReader: UsageRemoteReader? =
        if (customRootDir == null) LanguageServerUsageReader() else null
) {

    /** 兼容旧调用方，仅返回当前磁盘上可见的目标。 */
    fun discoverTargets(): List<ScannedConversationTarget> = discoverSnapshot().targets

    fun discoverSnapshot(): UsageScanSnapshot {
        val targets = mutableListOf<ScannedConversationTarget>()
        val seenKeys = mutableSetOf<String>()
        val incompleteSources = mutableSetOf<String>()

        val roots = if (customRootDir != null) {
            listOf(customRootDir to "ide")
        } else {
            val home = File(System.getProperty("user.home") ?: "")
            listOf(
                File(home, ".gemini/antigravity-ide") to "ide",
                File(home, ".gemini/antigravity") to "standalone",
                File(home, ".gemini/antigravity-cli") to "cli"
            )
        }

        for ((root, appSource) in roots) {
            if (root.exists() && !root.isDirectory) {
                incompleteSources += appSource
                continue
            }
            val conversationsComplete = scanConversationsDirectory(
                conversationsDir = File(root, "conversations"),
                appSource = appSource,
                out = targets,
                seenKeys = seenKeys
            )
            val brainComplete = scanBrainDirectory(
                brainDir = File(root, "brain"),
                appSource = appSource,
                out = targets,
                seenKeys = seenKeys
            )
            if (!conversationsComplete || !brainComplete) incompleteSources += appSource
        }

        return UsageScanSnapshot(
            targets = targets.sortedWith(compareBy<ScannedConversationTarget> { it.appSource }.thenBy { it.conversationId }),
            complete = incompleteSources.isEmpty(),
            incompleteSources = incompleteSources
        )
    }

    /**
     * 解析目标并返回成功/失败集合。
     * 合法的空数据库是 successful；数据库锁定、损坏或 Protobuf 截断是 failed，
     * 这样仓库可以保留旧快照并在下一轮继续重试。
     */
    suspend fun parseConversationResults(
        targets: List<ScannedConversationTarget>
    ): UsageParseBatchResult = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) {
            return@withContext UsageParseBatchResult(emptyList(), emptySet(), emptySet())
        }

        val results = mutableListOf<ParsedTargetResult>()
        for (chunk in targets.chunked(30)) {
            results += coroutineScope {
                chunk.map { target ->
                    async { parseTarget(target) }
                }.awaitAll()
            }
        }

        UsageParseBatchResult(
            conversations = results.mapNotNull { it.conversation },
            successfulKeys = results.filter { it.complete }.mapTo(mutableSetOf()) { it.key },
            failedKeys = results.filterNot { it.complete }.mapTo(mutableSetOf()) { it.key }
        )
    }

    /** 兼容旧 API；不返回不完整目标。 */
    suspend fun parseConversations(
        targets: List<ScannedConversationTarget>
    ): List<ConversationUsageData> = parseConversationResults(targets).conversations

    private suspend fun parseTarget(target: ScannedConversationTarget): ParsedTargetResult {
        val key = sourceKey(target.appSource, target.conversationId)
        return try {
            if (!target.targetFile.exists() || !target.targetFile.isFile) {
                return ParsedTargetResult(key, null, complete = false)
            }

            if (target.isDatabase) {
                val dbResult = SqliteConversationReader.readConversationDb(
                    dbFile = target.targetFile,
                    conversationId = target.conversationId,
                    appSource = target.appSource
                )
                if (dbResult.complete) {
                    val entries = if (dbResult.entries.any { it.missingUsageFields.isNotEmpty() }) {
                        val supplement = remoteReader?.read(target.conversationId, target.appSource)
                        if (supplement?.complete == true) {
                            UsageExtractor.dedupEntries(dbResult.entries + supplement.entries)
                        } else {
                            dbResult.entries
                        }
                    } else {
                        dbResult.entries
                    }
                    return ParsedTargetResult(
                        key = key,
                        conversation = ConversationUsageData(
                            conversationId = target.conversationId,
                            title = dbResult.title,
                            appSource = target.appSource,
                            entries = entries
                        ),
                        complete = true
                    )
                }

                val remote = remoteReader?.read(target.conversationId, target.appSource)
                if (remote?.complete == true && remote.entries.isNotEmpty()) {
                    return ParsedTargetResult(
                        key = key,
                        conversation = ConversationUsageData(
                            conversationId = target.conversationId,
                            title = "会话 ${target.conversationId.take(8)}",
                            appSource = target.appSource,
                            entries = remote.entries
                        ),
                        complete = true
                    )
                }
                return ParsedTargetResult(key, null, complete = false)
            } else {
                if (target.targetFile.length() <= 0L) {
                    return ParsedTargetResult(key, null, complete = false)
                }
                val lines = target.targetFile.useLines { it.toList() }
                val entries = UsageExtractor.extractFromTranscript(
                    lines = lines.asSequence(),
                    conversationId = target.conversationId,
                    appSource = target.appSource
                )
                ParsedTargetResult(
                    key = key,
                    conversation = ConversationUsageData(
                        conversationId = target.conversationId,
                        title = "会话 ${target.conversationId.take(8)}",
                        appSource = target.appSource,
                        entries = entries
                    ),
                    complete = true
                )
            }
        } catch (_: Exception) {
            ParsedTargetResult(key, null, complete = false)
        }
    }

    private fun scanConversationsDirectory(
        conversationsDir: File,
        appSource: String,
        out: MutableList<ScannedConversationTarget>,
        seenKeys: MutableSet<String>
    ): Boolean {
        if (!conversationsDir.exists()) return true
        if (!conversationsDir.isDirectory) return false
        val files = conversationsDir.listFiles() ?: return false

        for (file in files) {
            if (!file.isFile || !file.name.endsWith(".db", ignoreCase = true)) continue
            val id = file.name.substringBeforeLast(".db")
            if (!USAGE_UUID_REGEX.matches(id)) continue

            val key = sourceKey(appSource, id)
            if (!seenKeys.add(key)) continue
            out += ScannedConversationTarget(
                conversationId = id,
                appSource = appSource,
                targetFile = file,
                isDatabase = true,
                lastModified = databaseLastModified(file)
            )
        }
        return true
    }

    private fun scanBrainDirectory(
        brainDir: File,
        appSource: String,
        out: MutableList<ScannedConversationTarget>,
        seenKeys: MutableSet<String>
    ): Boolean {
        if (!brainDir.exists()) return true
        if (!brainDir.isDirectory) return false
        val children = brainDir.listFiles() ?: return false
        var complete = true

        for (directory in children) {
            if (!directory.isDirectory) continue
            val id = directory.name
            if (!USAGE_UUID_REGEX.matches(id)) continue

            val key = sourceKey(appSource, id)
            if (seenKeys.contains(key)) continue // .db 是更完整的来源

            val logs = listOf(
                File(directory, ".system_generated/logs/transcript.jsonl"),
                File(directory, ".system_generated/logs/transcript_full.jsonl")
            )
            val logFile = logs.firstOrNull { it.isFile && it.length() > 0L }
                ?: logs.firstOrNull { it.isFile }
            if (logFile == null) {
                val logsDirectory = File(directory, ".system_generated/logs")
                if (logsDirectory.exists() && logsDirectory.isDirectory && logsDirectory.listFiles() == null) {
                    complete = false
                }
                continue
            }
            seenKeys += key
            out += ScannedConversationTarget(
                conversationId = id,
                appSource = appSource,
                targetFile = logFile,
                isDatabase = false,
                lastModified = maxOf(logFile.lastModified(), directory.lastModified())
            )
        }
        return complete
    }

    private fun databaseLastModified(database: File): Long {
        return listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm")
        ).maxOf { it.takeIf(File::exists)?.lastModified() ?: 0L }
    }

    private fun sourceKey(source: String, conversationId: String): String = "$source:$conversationId"
}
