package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry
import java.io.File
import java.sql.DriverManager
import java.time.Instant

/** 本地 SQLite 会话读取结果；complete=false 时不能推进增量检查点。 */
data class ConversationDbResult(
    val entries: List<TokenEntry>,
    val title: String,
    val lastActiveTimestamp: String,
    val complete: Boolean = false
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
        appSource: String
    ): ConversationDbResult {
        if (!dbFile.exists() || !dbFile.isFile || dbFile.length() <= 0L) {
            return ConversationDbResult(emptyList(), "", "", complete = false)
        }

        val results = mutableListOf<TokenEntry>()
        val fileFallbackTs = fileTimestamp(dbFile)
        val stepIndex = readStepInfo(dbFile)
        var lastActiveTs = fileFallbackTs
        var complete = true
        var querySucceeded = false

        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.queryTimeout = 10
                    statement.executeQuery("SELECT idx, data FROM gen_metadata ORDER BY idx").use { rs ->
                        querySucceeded = true
                        while (rs.next()) {
                            val idx = rs.getInt("idx")
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
                                results += entry
                                if (isLater(entry.timestamp, lastActiveTs)) {
                                    lastActiveTs = entry.timestamp
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

        val brainTitle = resolveBrainTitle(dbFile.parentFile?.parentFile, conversationId)
        val resolvedTitle = brainTitle
            ?: "会话 ${conversationId.take(8)}"

        return ConversationDbResult(
            entries = UsageExtractor.dedupEntries(results),
            title = resolvedTitle,
            lastActiveTimestamp = lastActiveTs,
            complete = querySucceeded && complete
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
        val field3 = tokenCount(usageFields, 3)
        val field5 = tokenCount(usageFields, 5)
        val (cacheRead, cacheWrite) = if (field5 > 0L) {
            field5 to field3
        } else {
            field3 to 0L
        }
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
        val responseModel = ProtobufLite.findField(chatFields, 19, wireType = 2)?.asString()
        val displayName = ProtobufLite.findField(chatFields, 21, wireType = 2)?.asString()
        val runtimeModelId = readStringMetadata(chatFields, "model_enum")
        val identity = UsageModelIdentityResolver.resolve(responseModel, displayName, runtimeModelId)

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
                model = identity.model,
                modelDisplayName = identity.displayName,
                modelCanonicalId = identity.canonicalId,
                modelRuntimeId = identity.runtimeId,
                modelAggregationId = identity.aggregationId,
                modelPricingIds = identity.pricingModelIds,
                modelEvidenceSource = identity.evidenceSource,
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
}
