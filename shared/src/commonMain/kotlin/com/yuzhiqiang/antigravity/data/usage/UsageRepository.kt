package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.domain.model.usage.ConversationUsageData
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.DeepUsageStats
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val USAGE_DISK_CACHE_VERSION = 6

@Serializable
data class UsageDiskCache(
    val version: Int = USAGE_DISK_CACHE_VERSION,
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
    private val scanMutex = Mutex()

    private val _conversations = MutableStateFlow<List<ConversationUsageData>>(emptyList())
    private val sourceMtimes = mutableMapOf<String, Long>()

    private val _usageStats = MutableStateFlow(DeepUsageStats())
    val usageStats: StateFlow<DeepUsageStats> = _usageStats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(UsageTimeRange.CALENDAR_TODAY)
    val selectedTimeRange: StateFlow<UsageTimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<CustomDateRange?>(null)
    val customDateRange: StateFlow<CustomDateRange?> = _customDateRange.asStateFlow()

    private val _selectedSources = MutableStateFlow<Set<String>>(setOf("all"))
    val selectedSources: StateFlow<Set<String>> = _selectedSources.asStateFlow()
    private val aggregationRevision = java.util.concurrent.atomic.AtomicLong(0L)

    private val _selectedModel = MutableStateFlow<String?>("all")
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    init {
        loadDiskCache()
    }

    /**
     * 切换时间范围：顶部选中状态立即生效，底部数据在后台异步极速聚合后顺滑刷新。
     */
    suspend fun setTimeRange(timeRange: UsageTimeRange, customRange: CustomDateRange? = null) {
        val rev = aggregationRevision.incrementAndGet()
        AppLog.d("Usage/Repository") { "切换时间范围: $timeRange, customRange=$customRange, rev=$rev" }
        _selectedTimeRange.value = timeRange
        _customDateRange.value = customRange
        withContext(Dispatchers.Default) {
            if (rev != aggregationRevision.get()) return@withContext
            val stats = UsageAggregator.aggregate(
                conversations = _conversations.value,
                pricingService = pricingService,
                timeRange = timeRange,
                customDateRange = customRange,
                selectedSources = _selectedSources.value,
                selectedModel = _selectedModel.value,
                isCancelled = { rev != aggregationRevision.get() }
            )
            if (rev == aggregationRevision.get()) {
                _usageStats.value = stats
            }
        }
        AppLog.i("Usage/Repository") { "时间范围切换完成: $timeRange, 当前总Token=${_usageStats.value.totalTokens}, 调用=${_usageStats.value.totalCalls}" }
    }

    /**
     * 切换数据来源筛选：顶部选中状态立即生效，底部数据在后台异步极速聚合后顺滑刷新。
     */
    suspend fun setSelectedSources(sources: Set<String>) {
        val rev = aggregationRevision.incrementAndGet()
        val nextSources = if (sources.isEmpty()) setOf("all") else sources
        AppLog.d("Usage/Repository") { "切换数据来源: $nextSources, rev=$rev" }
        _selectedSources.value = nextSources
        withContext(Dispatchers.Default) {
            if (rev != aggregationRevision.get()) return@withContext
            val stats = UsageAggregator.aggregate(
                conversations = _conversations.value,
                pricingService = pricingService,
                timeRange = _selectedTimeRange.value,
                customDateRange = _customDateRange.value,
                selectedSources = nextSources,
                selectedModel = _selectedModel.value,
                isCancelled = { rev != aggregationRevision.get() }
            )
            if (rev == aggregationRevision.get()) {
                _usageStats.value = stats
            }
        }
        AppLog.i("Usage/Repository") { "数据来源切换完成: ${_selectedSources.value}, 聚合会话数=${_usageStats.value.totalConversations}" }
    }

    /**
     * 切换模型筛选：顶部选中状态立即生效，底部数据在后台异步极速聚合后顺滑刷新。
     */
    suspend fun setSelectedModel(model: String?) {
        val rev = aggregationRevision.incrementAndGet()
        val nextModel = if (model.isNullOrBlank() || model == "all") "all" else model
        AppLog.d("Usage/Repository") { "切换模型筛选: $nextModel, rev=$rev" }
        _selectedModel.value = nextModel
        withContext(Dispatchers.Default) {
            if (rev != aggregationRevision.get()) return@withContext
            val stats = UsageAggregator.aggregate(
                conversations = _conversations.value,
                pricingService = pricingService,
                timeRange = _selectedTimeRange.value,
                customDateRange = _customDateRange.value,
                selectedSources = _selectedSources.value,
                selectedModel = nextModel,
                isCancelled = { rev != aggregationRevision.get() }
            )
            if (rev == aggregationRevision.get()) {
                _usageStats.value = stats
            }
        }
        AppLog.i("Usage/Repository") { "模型筛选切换完成: $nextModel, 当前总Token=${_usageStats.value.totalTokens}, 调用=${_usageStats.value.totalCalls}" }
    }

    suspend fun refresh(force: Boolean = false): Result<DeepUsageStats> = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            AppLog.i("Usage/Repository") { "开始刷新用量统计 (force=$force, 当前已缓存会话=${_conversations.value.size})" }
            _isRefreshing.value = true
            try {
                if (refreshPricingCatalog) {
                    val priceStartMs = System.currentTimeMillis()
                    // 价格目录失败/超时不应阻断本地 Token 统计与即时呈现
                    val priceResult = runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(2500L) {
                            pricingService.refreshCatalog(force = force)
                        }
                    }
                    AppLog.d("Usage/Repository") { "价格目录刷新尝试完毕 (耗时=${System.currentTimeMillis() - priceStartMs}ms, 结果=${priceResult.getOrNull()})" }
                }

                val snapshotStartMs = System.currentTimeMillis()
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
                AppLog.d("Usage/Repository") { "扫描发现目标完成: 发现总数=${targets.size}, 待增量/全量解析数=${changedTargets.size}, 发现耗时=${System.currentTimeMillis() - snapshotStartMs}ms" }

                val cachedByKey = if (force) {
                    emptyMap()
                } else {
                    _conversations.value.associateBy { sourceKey(it.appSource, it.conversationId) }
                }
                val parseStartMs = System.currentTimeMillis()
                val parsed = scanner.parseConversationResults(changedTargets, cachedByKey)
                val freshByKey = parsed.conversations.associateBy { sourceKey(it.appSource, it.conversationId) }
                val incompleteSources = snapshot.incompleteSources
                AppLog.d("Usage/Repository") { "目标解析完成: 成功解析=${parsed.successfulKeys.size}, 失败=${parsed.failedKeys.size}, 解析耗时=${System.currentTimeMillis() - parseStartMs}ms" }

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

                val rev = aggregationRevision.incrementAndGet()
                val stats = aggregateCurrent()
                if (rev == aggregationRevision.get()) {
                    _usageStats.value = stats
                }
                val totalCostMs = System.currentTimeMillis() - startMs
                AppLog.i("Usage/Repository") { "用量刷新全流程完成! 总耗时=${totalCostMs}ms, 最终会话总数=${merged.size}, 当前时间范围(${_selectedTimeRange.value})聚合Token=${stats.totalTokens}, 调用=${stats.totalCalls}" }
                Result.success(stats)
            } catch (error: Exception) {
                AppLog.e("Usage/Repository", error) { "用量刷新流程发生异常: ${error.message}" }
                Result.failure(error)
            } finally {
                _isRefreshing.value = false
                _isInitialLoading.value = false
            }
        }
    }

    suspend fun recomputeStats() {
        val rev = aggregationRevision.incrementAndGet()
        withContext(Dispatchers.Default) {
            if (rev != aggregationRevision.get()) return@withContext
            val stats = aggregateCurrent(isCancelled = { rev != aggregationRevision.get() })
            if (rev == aggregationRevision.get()) {
                _usageStats.value = stats
            }
        }
    }

    private fun aggregateCurrent(isCancelled: (() -> Boolean)? = null): DeepUsageStats = UsageAggregator.aggregate(
        conversations = _conversations.value,
        pricingService = pricingService,
        timeRange = _selectedTimeRange.value,
        customDateRange = _customDateRange.value,
        selectedSources = _selectedSources.value,
        selectedModel = _selectedModel.value,
        isCancelled = isCancelled
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
            if (!cacheFile.exists()) {
                AppLog.d("Usage/Repository") { "未发现本地磁盘用量缓存: ${cacheFile.absolutePath}" }
                return
            }
            val startMs = System.currentTimeMillis()
            val cache = json.decodeFromString<UsageDiskCache>(cacheFile.readText(Charsets.UTF_8))
            if (cache.version != USAGE_DISK_CACHE_VERSION) {
                AppLog.d("Usage/Repository") { "磁盘用量缓存版本过期 (cache.version=${cache.version}, current=$USAGE_DISK_CACHE_VERSION), 忽略旧缓存" }
                return
            }
            _conversations.value = cache.conversations
                .distinctBy { sourceKey(it.appSource, it.conversationId) }
            sourceMtimes.clear()
            sourceMtimes.putAll(cache.sourceMtimes)
            _usageStats.value = aggregateCurrent()
            if (_conversations.value.isNotEmpty()) {
                _isInitialLoading.value = false
            }
            AppLog.i("Usage/Repository") { "成功恢复本地用量磁盘缓存: 会话数=${_conversations.value.size}, 缓存版本=${cache.version}, 耗时=${System.currentTimeMillis() - startMs}ms, 初始Token=${_usageStats.value.totalTokens}" }
        } catch (error: Exception) {
            AppLog.w("Usage/Repository", error) { "读取本地用量磁盘缓存失败 (将回退至冷扫描): ${error.message}" }
        }
    }

    private fun saveDiskCache(
        conversations: List<ConversationUsageData>,
        mtimes: Map<String, Long>
    ) {
        try {
            val startMs = System.currentTimeMillis()
            val cache = UsageDiskCache(
                version = USAGE_DISK_CACHE_VERSION,
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
            AppLog.d("Usage/Repository") { "持久化用量磁盘缓存成功: 会话数=${conversations.size}, 耗时=${System.currentTimeMillis() - startMs}ms" }
        } catch (error: Exception) {
            AppLog.w("Usage/Repository", error) { "持久化用量磁盘缓存失败: ${error.message}" }
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
