package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import java.io.File
import java.sql.DriverManager
import java.time.Instant

/** 本地 SQLite 会话读取结果；complete=false 时不能推进增量检查点。 */
data class ConversationDbResult(
    val entries: List<TokenEntry>,
    val title: String,
    val lastActiveTimestamp: String,
    val complete: Boolean = false,
    val maxIdx: Int = -1
)

data class StepTimestampIndex(
    val byUuid: Map<String, String> = emptyMap(),
    val byStepIdx: Map<Int, String> = emptyMap()
)

private data class StepMetadataTimestamp(
    val timestamp: String?,
    val stepId: String?
)

private data class MetadataParseResult(
    val entry: TokenEntry?,
    val valid: Boolean
)

/**
 * 本地 SQLite 会话数据库解析器。
 *
 * gen_metadata 的 data 是嵌套 Protobuf：
 * top-level field 1 = chat model，field 4 = step UUID；
 * chat model field 4 = usage，field 19 = 响应模型，field 20 = model_enum，
 * field 21 = 展示名称。解析失败必须向上报告，避免把半条数据库写成成功缓存。
 */
object SqliteConversationReader {

    fun readConversationDb(
        dbFile: File,
        conversationId: String,
        appSource: String,
        lastKnownIdx: Int = -1,
        existingEntries: List<TokenEntry> = emptyList(),
        existingTitle: String = ""
    ): ConversationDbResult {
        if (!dbFile.exists() || !dbFile.isFile || dbFile.length() <= 0L) {
            return ConversationDbResult(emptyList(), "", "", complete = false, maxIdx = -1)
        }

        val newResults = mutableListOf<TokenEntry>()
        val fileFallbackTs = fileTimestamp(dbFile)
        var lastActiveTs = fileFallbackTs
        var complete = true
        var querySucceeded = false
        var observedMaxIdx = lastKnownIdx
        var isIncrementalRead = false

        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                var canIncremental = lastKnownIdx >= 0 && existingEntries.isNotEmpty()
                if (canIncremental) {
                    var dbMaxIdx = -1
                    connection.createStatement().use { stmt ->
                        stmt.queryTimeout = 5
                        stmt.executeQuery("SELECT MAX(idx) FROM gen_metadata").use { rs ->
                            if (rs.next()) {
                                val m = rs.getInt(1)
                                if (!rs.wasNull()) {
                                    dbMaxIdx = m
                                }
                            }
                        }
                    }
                    if (dbMaxIdx < lastKnownIdx) {
                        // 数据库可能被截断、重置或回滚，回退到全量重新解析
                        canIncremental = false
                        observedMaxIdx = -1
                    } else {
                        // 校验边界行：验证 lastKnownIdx 处记录的 responseId 是否与已知缓存一致，防止数据库被重写/替换
                        val lastExpectedResponseId = existingEntries.lastOrNull { it.responseId != null }?.responseId
                        var anchorMatches = false
                        if (lastExpectedResponseId != null) {
                            connection.prepareStatement("SELECT data FROM gen_metadata WHERE idx = ?").use { stmt ->
                                stmt.queryTimeout = 5
                                stmt.setInt(1, lastKnownIdx)
                                stmt.executeQuery().use { rs ->
                                    if (rs.next()) {
                                        val blob = rs.getBytes("data")
                                        if (blob != null) {
                                            val currentResponseId = extractResponseId(blob, lastKnownIdx)
                                            if (currentResponseId == lastExpectedResponseId) {
                                                anchorMatches = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!anchorMatches) {
                            // 锚点不匹配（可能数据库被替换或会话重置），回退全量扫描
                            canIncremental = false
                            observedMaxIdx = -1
                        } else if (dbMaxIdx == lastKnownIdx) {
                            // 极速快径：锚点匹配且最大 idx 未变，说明当前数据库无需任何增量解析
                            val title = existingTitle.takeIf { it.isNotBlank() && !it.startsWith("会话 ") }
                                ?: resolveBrainTitle(dbFile.parentFile?.parentFile, conversationId)
                                ?: existingTitle.ifBlank { "会话 ${conversationId.take(8)}" }
                            return ConversationDbResult(
                                entries = existingEntries,
                                title = title,
                                lastActiveTimestamp = existingEntries.maxOfOrNull { it.timestamp } ?: fileFallbackTs,
                                complete = true,
                                maxIdx = lastKnownIdx
                            )
                        }
                    }
                }

                isIncrementalRead = canIncremental
                if (isIncrementalRead) {
                    lastActiveTs = existingEntries.maxOfOrNull { it.timestamp } ?: fileFallbackTs
                }

                val stepIndex = readStepInfo(dbFile)
                if (canIncremental) {
                    val sql = "SELECT idx, data FROM gen_metadata WHERE idx > ? ORDER BY idx"
                    connection.prepareStatement(sql).use { stmt ->
                        stmt.queryTimeout = 10
                        stmt.setInt(1, lastKnownIdx)
                        stmt.executeQuery().use { rs ->
                            querySucceeded = true
                            while (rs.next()) {
                                val idx = rs.getInt("idx")
                                if (idx > observedMaxIdx) observedMaxIdx = idx
                                val blobBytes = rs.getBytes("data")
                                if (blobBytes == null) {
                                    complete = false
                                    continue
                                }

                                val parsed = parseMetadataBlob(
                                    data = blobBytes,
                                    idx = idx,
                                    conversationId = conversationId,
                                    appSource = appSource,
                                    stepIndex = stepIndex,
                                    fileFallbackTs = fileFallbackTs
                                )
                                if (!parsed.valid) complete = false
                                val entry = parsed.entry
                                if (entry != null && entry.totalTokens > 0L) {
                                    newResults += entry
                                    if (isLater(entry.timestamp, lastActiveTs)) {
                                        lastActiveTs = entry.timestamp
                                    }
                                }
                            }
                        }
                    }
                } else {
                    observedMaxIdx = -1
                    connection.createStatement().use { statement ->
                        statement.queryTimeout = 10
                        statement.executeQuery("SELECT idx, data FROM gen_metadata ORDER BY idx").use { rs ->
                            querySucceeded = true
                            while (rs.next()) {
                                val idx = rs.getInt("idx")
                                if (idx > observedMaxIdx) observedMaxIdx = idx
                                val blobBytes = rs.getBytes("data")
                                if (blobBytes == null) {
                                    complete = false
                                    continue
                                }

                                val parsed = parseMetadataBlob(
                                    data = blobBytes,
                                    idx = idx,
                                    conversationId = conversationId,
                                    appSource = appSource,
                                    stepIndex = stepIndex,
                                    fileFallbackTs = fileFallbackTs
                                )
                                if (!parsed.valid) complete = false
                                val entry = parsed.entry
                                if (entry != null && entry.totalTokens > 0L) {
                                    newResults += entry
                                    if (isLater(entry.timestamp, lastActiveTs)) {
                                        lastActiveTs = entry.timestamp
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 数据库可能正被宿主写入、被锁定或已损坏；partial data 不能作为成功结果。
            complete = false
        }

        val resolvedTitle = existingTitle.takeIf { it.isNotBlank() && !it.startsWith("会话 ") }
            ?: resolveBrainTitle(dbFile.parentFile?.parentFile, conversationId)
            ?: existingTitle.takeIf { it.isNotBlank() }
            ?: "会话 ${conversationId.take(8)}"

        val combinedEntries = if (isIncrementalRead && existingEntries.isNotEmpty()) {
            if (newResults.isEmpty()) {
                existingEntries
            } else {
                UsageExtractor.dedupEntries(existingEntries + newResults)
            }
        } else {
            UsageExtractor.dedupEntries(newResults)
        }

        return ConversationDbResult(
            entries = combinedEntries,
            title = resolvedTitle,
            lastActiveTimestamp = lastActiveTs,
            complete = querySucceeded && complete,
            maxIdx = observedMaxIdx
        )
    }

    private fun readStepInfo(dbFile: File): StepTimestampIndex {
        val byUuid = mutableMapOf<String, String>()
        val byStepIdx = mutableMapOf<Int, String>()

        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.queryTimeout = 5
                    statement.executeQuery(
                        "SELECT idx, metadata FROM steps WHERE metadata IS NOT NULL ORDER BY idx"
                    ).use { rs ->
                        while (rs.next()) {
                            val idx = rs.getInt("idx")
                            val metadataBlob = rs.getBytes("metadata") ?: continue
                            val parsed = parseStepMetadata(metadataBlob)
                            val timestamp = parsed.timestamp ?: continue
                            byStepIdx[idx] = timestamp
                            parsed.stepId?.takeIf { it.isNotBlank() }?.let { byUuid[it] = timestamp }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Step 时间只影响回退；主 metadata 仍可使用文件 mtime 完成读取。
        }
        return StepTimestampIndex(byUuid = byUuid, byStepIdx = byStepIdx)
    }

    private fun resolveBrainTitle(rootDir: File?, conversationId: String): String? {
        if (rootDir == null) return null
        return try {
            val brainDir = File(rootDir, "brain/$conversationId")
            if (!brainDir.exists()) return null
            val taskFile = File(brainDir, "task.md")
            if (!taskFile.exists()) return null
            taskFile.useLines { lines ->
                lines.firstOrNull { it.isNotBlank() }
                    ?.removePrefix("#")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(40)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMetadataBlob(
        data: ByteArray,
        idx: Int,
        conversationId: String,
        appSource: String,
        stepIndex: StepTimestampIndex,
        fileFallbackTs: String
    ): MetadataParseResult {
        if (data.isEmpty()) return MetadataParseResult(entry = null, valid = true)

        val topFields = ProtobufLite.readFieldsStrict(data)
            ?: return MetadataParseResult(entry = null, valid = false)
        val chatModelBytes = ProtobufLite.findField(topFields, 1, wireType = 2)?.bytes
            ?: return MetadataParseResult(entry = null, valid = true)
        val chatFields = ProtobufLite.readFieldsStrict(chatModelBytes)
            ?: return MetadataParseResult(entry = null, valid = false)

        val usageBytes = ProtobufLite.findField(chatFields, 4, wireType = 2)?.bytes
            ?: return MetadataParseResult(entry = null, valid = true)
        val usageFields = ProtobufLite.readFieldsStrict(usageBytes)
            ?: return MetadataParseResult(entry = null, valid = false)

        val input = tokenCount(usageFields, 1)
        val output = tokenCount(usageFields, 9).takeIf { it > 0L }
            ?: tokenCount(usageFields, 2)
        val cacheRead = tokenCount(usageFields, 3)
        val cacheWrite = tokenCount(usageFields, 5)
        val reasoning = tokenCount(usageFields, 10)
        val missingUsageFields = missingUsageFields(usageFields)

        if (input == 0L && output == 0L && cacheRead == 0L && cacheWrite == 0L && reasoning == 0L) {
            return MetadataParseResult(entry = null, valid = true)
        }

        val responseId = ProtobufLite.findField(usageFields, 11, wireType = 2)
            ?.asString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "cli-gen-$idx"
        val responseModel = ProtobufLite.findField(chatFields, 19, wireType = 2)?.asString()?.observedValue()
        val displayName = ProtobufLite.findField(chatFields, 21, wireType = 2)?.asString()?.observedValue()
        val runtimeModelId = readStringMetadata(chatFields, "model_enum")?.observedValue()

        val topStepId = ProtobufLite.findField(topFields, 4, wireType = 2)
            ?.asString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val timestamp = readCreatedAtTimestamp(chatFields)
            ?: topStepId?.let { stepIndex.byUuid[it] }
            ?: stepIndex.byStepIdx[idx]
            ?: fileFallbackTs
            ?: Instant.EPOCH.toString()

        return MetadataParseResult(
            entry = TokenEntry(
                responseId = responseId,
                input = input,
                output = output,
                cacheRead = cacheRead,
                cacheWrite = cacheWrite,
                reasoning = reasoning,
                modelObservation = ModelObservation(
                    runtimeModelId = runtimeModelId,
                    responseModelId = responseModel,
                    displayName = displayName
                ),
                missingUsageFields = missingUsageFields,
                provider = "",
                timestamp = timestamp,
                conversationId = conversationId,
                appSource = appSource
            ),
            valid = true
        )
    }

    private fun readStringMetadata(
        chatFields: List<ProtobufLite.Field>,
        key: String
    ): String? {
        return chatFields.asSequence()
            .filter { it.number == 20 && it.wireType == 2 }
            .mapNotNull { it.bytes }
            .mapNotNull { ProtobufLite.readFieldsStrict(it) }
            .firstNotNullOfOrNull { pairFields ->
                val pairKey = ProtobufLite.findField(pairFields, 1, wireType = 2)?.asString()
                if (pairKey == key) {
                    ProtobufLite.findField(pairFields, 2, wireType = 2)?.asString()?.trim()
                } else {
                    null
                }
            }
    }

    private fun missingUsageFields(fields: List<ProtobufLite.Field>): List<String> {
        fun hasValid(fieldNumber: Int): Boolean = fields.any {
            it.number == fieldNumber && it.wireType == 0 && it.varint?.let { value -> value >= 0L } == true
        }

        return buildList {
            if (!hasValid(1)) add("input")
            if (!hasValid(9) && !hasValid(2)) add("output")
            if (!hasValid(3)) add("cache")
            if (!hasValid(5)) add("cacheWrite")
            if (!hasValid(10)) add("reasoning")
        }
    }

    private fun tokenCount(fields: List<ProtobufLite.Field>, fieldNumber: Int): Long {
        return ProtobufLite.findField(fields, fieldNumber, wireType = 0)
            ?.varint
            ?.takeIf { it >= 0L }
            ?: 0L
    }

    private fun readCreatedAtTimestamp(chatFields: List<ProtobufLite.Field>): String? {
        val timeBytes = ProtobufLite.findField(chatFields, 9, wireType = 2)?.bytes ?: return null
        val timeFields = ProtobufLite.readFieldsStrict(timeBytes) ?: return null
        val timestampBytes = ProtobufLite.findField(timeFields, 4, wireType = 2)?.bytes ?: return null
        return parseTimestampMessage(timestampBytes)
    }

    private fun parseStepMetadata(metadataBytes: ByteArray): StepMetadataTimestamp {
        val fields = ProtobufLite.readFieldsStrict(metadataBytes) ?: return StepMetadataTimestamp(null, null)
        val timestampBytes = ProtobufLite.findField(fields, 1, wireType = 2)?.bytes
        val timestamp = timestampBytes?.let(::parseTimestampMessage)
        val stepId = ProtobufLite.findField(fields, 12, wireType = 2)
            ?.asString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return StepMetadataTimestamp(timestamp, stepId)
    }

    private fun parseTimestampMessage(timestampBytes: ByteArray): String? {
        val fields = ProtobufLite.readFieldsStrict(timestampBytes) ?: return null
        val seconds = ProtobufLite.findField(fields, 1, wireType = 0)?.varint ?: return null
        val nanos = ProtobufLite.findField(fields, 2, wireType = 0)?.varint ?: 0L
        if (seconds <= 0L || nanos !in 0L..999_999_999L) return null
        return try {
            Instant.ofEpochSecond(seconds, nanos).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun fileTimestamp(file: File): String {
        return try {
            Instant.ofEpochMilli(file.lastModified().coerceAtLeast(0L)).toString()
        } catch (_: Exception) {
            Instant.EPOCH.toString()
        }
    }

    private fun isLater(candidate: String, current: String): Boolean {
        return try {
            Instant.parse(candidate).isAfter(Instant.parse(current))
        } catch (_: Exception) {
            candidate > current
        }
    }

    private fun extractResponseId(data: ByteArray, idx: Int): String? {
        if (data.isEmpty()) return null
        val topFields = ProtobufLite.readFieldsStrict(data) ?: return null
        val chatModelBytes = ProtobufLite.findField(topFields, 1, wireType = 2)?.bytes ?: return null
        val chatFields = ProtobufLite.readFieldsStrict(chatModelBytes) ?: return null
        val usageBytes = ProtobufLite.findField(chatFields, 4, wireType = 2)?.bytes ?: return null
        val usageFields = ProtobufLite.readFieldsStrict(usageBytes) ?: return null
        return ProtobufLite.findField(usageFields, 11, wireType = 2)
            ?.asString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "cli-gen-$idx"
    }

    private fun String.observedValue(): String? = trim().takeIf(String::isNotEmpty)
}
