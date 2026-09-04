package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.services.auth.RuntimeAccountProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 可替换的 LS 用量回退接口，便于离线运行和单元测试。 */
interface UsageRemoteReader {
    suspend fun read(
        conversationId: String,
        appSource: String
    ): RemoteUsageReadResult?

    suspend fun readSteps(
        conversationId: String,
        appSource: String,
        requiredResponseIds: Set<String> = emptySet()
    ): RemoteUsageReadResult? = read(conversationId, appSource)
}

data class RemoteUsageReadResult(
    val entries: List<com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry>,
    val complete: Boolean
)

/**
 * 从运行中的 Language Server 补齐本地数据库尚未完整落盘的会话。
 * 每个 endpoint 都必须完整拿到 metadata 与 steps；只拿到部分页面不会覆盖本地旧快照。
 */
internal class LanguageServerUsageReader(
    private val discoverEndpoints: () -> Result<List<RuntimeAccountProbe.LanguageServerEndpoint>> =
        RuntimeAccountProbe::discoverLanguageServerEndpoints,
    private val requestJson: (RuntimeAccountProbe.LanguageServerEndpoint, String, String, Int) -> String =
        RuntimeAccountProbe::requestLanguageServerJson
) : UsageRemoteReader {
    companion object {
        private const val MAX_PAGINATION_ATTEMPTS = 30
        private const val REQUEST_TIMEOUT_MS = 6_000
        private const val ENDPOINT_CACHE_TTL_MS = 5_000L
        private const val SERVICE = "GetCascadeTrajectoryGeneratorMetadata"
        private const val STEPS_SERVICE = "GetCascadeTrajectorySteps"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val endpointMutex = Mutex()
    private var endpointCacheAt = 0L
    private var endpointCache: List<RuntimeAccountProbe.LanguageServerEndpoint> = emptyList()

    override suspend fun read(
        conversationId: String,
        appSource: String
    ): RemoteUsageReadResult? = withContext(Dispatchers.IO) {
        val endpoints = getEndpoints()
        if (endpoints.isEmpty()) return@withContext null

        for (endpoint in endpoints) {
            val metadata = fetchPages(endpoint, SERVICE, conversationId, "generator_metadata_offset", appSource)
            val steps = fetchPages(endpoint, STEPS_SERVICE, conversationId, "step_offset", appSource)
            if (!metadata.complete || !steps.complete) continue

            val entries = UsageExtractor.dedupEntries(metadata.entries + steps.entries)
            // 空远程结果不应覆盖本地损坏快照；只有拿到真实 Token 才作为回退成功。
            if (entries.isNotEmpty()) {
                return@withContext RemoteUsageReadResult(entries, complete = true)
            }
        }
        null
    }

    override suspend fun readSteps(
        conversationId: String,
        appSource: String,
        requiredResponseIds: Set<String>
    ): RemoteUsageReadResult? = withContext(Dispatchers.IO) {
        val endpoints = getEndpoints()
        if (endpoints.isEmpty()) return@withContext null
        val collectedEntries = mutableListOf<com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry>()

        for (endpoint in endpoints) {
            val steps = fetchPages(endpoint, STEPS_SERVICE, conversationId, "step_offset", appSource)
            if (!steps.complete) continue
            collectedEntries += steps.entries
            val entries = UsageExtractor.dedupEntries(collectedEntries)
            val responseIds = entries.mapNotNullTo(mutableSetOf()) { it.responseId }
            if (entries.isNotEmpty() && (requiredResponseIds.isEmpty() || responseIds.containsAll(requiredResponseIds))) {
                return@withContext RemoteUsageReadResult(entries, complete = true)
            }
        }
        val entries = UsageExtractor.dedupEntries(collectedEntries)
        entries.takeIf { it.isNotEmpty() }?.let { RemoteUsageReadResult(it, complete = false) }
    }

    private suspend fun getEndpoints(): List<RuntimeAccountProbe.LanguageServerEndpoint> =
        endpointMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - endpointCacheAt < ENDPOINT_CACHE_TTL_MS) return@withLock endpointCache
            endpointCache = try {
                discoverEndpoints().getOrNull().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            endpointCacheAt = now
            endpointCache
        }

    private fun fetchPages(
        endpoint: RuntimeAccountProbe.LanguageServerEndpoint,
        method: String,
        conversationId: String,
        offsetField: String,
        appSource: String
    ): PageResult {
        val entries = mutableListOf<com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry>()
        var offset = 0
        var rawCount = 0

        for (page in 0 until MAX_PAGINATION_ATTEMPTS) {
            try {
                val response = requestJson(
                    endpoint,
                    method,
                    buildRequestBody(conversationId, offsetField, offset),
                    REQUEST_TIMEOUT_MS
                )
                val root = json.parseToJsonElement(response).jsonObject
                val items = readItems(root, method)
                if (items.isEmpty()) return PageResult(entries, complete = true)

                val pageEntries = UsageExtractor.extractFromJsonObjects(
                    objects = items.mapNotNull { it as? JsonObject },
                    conversationId = conversationId,
                    appSource = appSource
                )
                entries += pageEntries
                rawCount += items.size
                offset = rawCount
            } catch (_: Exception) {
                return PageResult(entries, complete = false)
            }
        }
        return PageResult(entries, complete = false)
    }

    private fun readItems(root: JsonObject, method: String): JsonArray {
        val key = if (method == SERVICE) "generatorMetadata" else "steps"
        val snakeKey = if (method == SERVICE) "generator_metadata" else "steps"
        return (root[key] as? JsonArray)
            ?: (root[snakeKey] as? JsonArray)
            ?: JsonArray(emptyList())
    }

    private fun buildRequestBody(conversationId: String, offsetField: String, offset: Int): String =
        buildJsonObject {
            put("cascade_id", conversationId)
            put(offsetField, offset)
        }.toString()

    private data class PageResult(
        val entries: List<com.yuzhiqiang.antigravity.domain.model.usage.TokenEntry>,
        val complete: Boolean
    )
}
