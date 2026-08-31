package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.domain.model.usage.ConversationUsageData
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.DeepUsageStats
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UsageDiskCache(
    val version: Int = 2,
    val updatedAt: Long = 0L,
    val sourceMtimes: Map<String, Long> = emptyMap(),
    val conversations: List<ConversationUsageData> = emptyList()
)

/**
 * 用量数据仓库。
 *
 * 增量检查点只在“成功读完当前目标”后推进；当前物理库存之外的旧目标会被
 * 删除，但扫描目录本身读取失败时会保护对应来源的旧数据，避免瞬时权限/锁问题
 * 被误判为用户删除了全部会话。
 */
class UsageRepository(
    val pricingService: PricingCatalogService = PricingCatalogService(),
    scanner: UsageLogScanner? = null,
    private val customRootDir: File? = null,
    private val refreshPricingCatalog: Boolean = true
) {
    private val scanner = scanner ?: UsageLogScanner(customRootDir)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val rootDir: File by lazy { customRootDir ?: AppDataPaths.rootDir() }
    private val cacheFile: File by lazy { File(rootDir, ".deep_stats_cache.json") }
    private val mutex = Mutex()

    private val _conversations = MutableStateFlow<List<ConversationUsageData>>(emptyList())
    private val sourceMtimes = mutableMapOf<String, Long>()

    private val _usageStats = MutableStateFlow(DeepUsageStats())
    val usageStats: StateFlow<DeepUsageStats> = _usageStats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(UsageTimeRange.ROLLING_7D)
    val selectedTimeRange: StateFlow<UsageTimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<CustomDateRange?>(null)
    val customDateRange: StateFlow<CustomDateRange?> = _customDateRange.asStateFlow()

    private val _selectedSources = MutableStateFlow<Set<String>>(setOf("all"))
    val selectedSources: StateFlow<Set<String>> = _selectedSources.asStateFlow()

    init {
        loadDiskCache()
    }

    suspend fun setTimeRange(timeRange: UsageTimeRange, customRange: CustomDateRange? = null) {
        _selectedTimeRange.value = timeRange
        _customDateRange.value = customRange
        recomputeStats()
    }

    suspend fun setSelectedSources(sources: Set<String>) {
        _selectedSources.value = if (sources.isEmpty()) setOf("all") else sources
        recomputeStats()
    }

    suspend fun refresh(force: Boolean = false): Result<DeepUsageStats> = mutex.withLock {
        withContext(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                if (refreshPricingCatalog) {
                    // 价格目录失败不应阻断本地 Token 统计；服务本身会保留旧目录。
                    pricingService.refreshCatalog(force = force)
                }

                val snapshot = scanner.discoverSnapshot()
                val targets = snapshot.targets
                val currentKeys = targets.mapTo(mutableSetOf()) { sourceKey(it.appSource, it.conversationId) }
                val cachedConversationKeys = _conversations.value.mapTo(mutableSetOf()) {
                    sourceKey(it.appSource, it.conversationId)
                }
                val changedTargets = targets.filter { target ->
                    val key = sourceKey(target.appSource, target.conversationId)
                    force || key !in cachedConversationKeys || sourceMtimes[key] != target.lastModified
                }
                val parsed = scanner.parseConversationResults(changedTargets)
                val freshByKey = parsed.conversations.associateBy { sourceKey(it.appSource, it.conversationId) }
                val incompleteSources = snapshot.incompleteSources

                val merged = _conversations.value
                    .filter { conversation ->
                        val key = sourceKey(conversation.appSource, conversation.conversationId)
                        val sourceIsProtected = normalizeSource(conversation.appSource) in incompleteSources
                        (key in currentKeys || sourceIsProtected) && key !in parsed.successfulKeys
                    }
                    .toMutableList()
                merged += freshByKey.values
                _conversations.value = merged

                updateSourceMtimes(
                    targets = targets,
                    currentKeys = currentKeys,
                    successfulKeys = parsed.successfulKeys,
                    snapshot = snapshot
                )
                saveDiskCache(merged, sourceMtimes)

                val stats = aggregateCurrent()
                _usageStats.value = stats
                Result.success(stats)
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun recomputeStats() {
        _usageStats.value = aggregateCurrent()
    }

    private fun aggregateCurrent(): DeepUsageStats = UsageAggregator.aggregate(
        conversations = _conversations.value,
        pricingService = pricingService,
        timeRange = _selectedTimeRange.value,
        customDateRange = _customDateRange.value,
        selectedSources = _selectedSources.value
    )

    private fun updateSourceMtimes(
        targets: List<ScannedConversationTarget>,
        currentKeys: Set<String>,
        successfulKeys: Set<String>,
        snapshot: UsageScanSnapshot
    ) {
        val incompleteSources = snapshot.incompleteSources
        val targetByKey = targets.associateBy { sourceKey(it.appSource, it.conversationId) }

        // 已完整扫描的来源可以安全清除不再存在的会话 checkpoint；不完整来源必须保留。
        for (key in sourceMtimes.keys.toList()) {
            val source = key.substringBefore(':')
            val mayPrune = source !in incompleteSources
            if (mayPrune && key !in currentKeys) sourceMtimes.remove(key)
        }

        // 只有成功解析的目标才推进 mtime。失败目标保留旧 mtime（若有），下轮会重试。
        for (key in successfulKeys) {
            targetByKey[key]?.let { sourceMtimes[key] = it.lastModified }
        }
    }

    private fun loadDiskCache() {
        try {
            if (!cacheFile.exists()) return
            val cache = json.decodeFromString<UsageDiskCache>(cacheFile.readText(Charsets.UTF_8))
            _conversations.value = cache.conversations
                .distinctBy { sourceKey(it.appSource, it.conversationId) }
            sourceMtimes.clear()
            sourceMtimes.putAll(cache.sourceMtimes)
            recomputeStats()
        } catch (_: Exception) {
            // 快照损坏时从磁盘重新扫描；不能让损坏缓存阻断应用启动。
        }
    }

    private fun saveDiskCache(
        conversations: List<ConversationUsageData>,
        mtimes: Map<String, Long>
    ) {
        try {
            val cache = UsageDiskCache(
                version = 2,
                updatedAt = System.currentTimeMillis(),
                sourceMtimes = mtimes.toMap(),
                conversations = conversations
            )
            val text = json.encodeToString(UsageDiskCache.serializer(), cache)
            AtomicFileWriter.writeText(
                target = cacheFile,
                content = text,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY
            )
        } catch (_: Exception) {
            // 统计仍可使用内存结果；下一轮刷新会再次尝试持久化。
        }
    }

    private fun sourceKey(appSource: String, conversationId: String): String =
        "${normalizeSource(appSource)}:$conversationId"

    private fun normalizeSource(source: String): String = when (source.trim().lowercase()) {
        "app" -> "standalone"
        "ide", "standalone", "cli" -> source.trim().lowercase()
        else -> source.trim().lowercase().ifBlank { "ide" }
    }
}
