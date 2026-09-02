package com.yuzhiqiang.antigravity.services.quota

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class QuotaStoreData(
    val version: Int = 1,
    val snapshots: Map<String, AccountQuotaSnapshot> = emptyMap()
)

/**
 * 多线程/高并发配额智能轮询器。
 * 支持磁盘缓存极速秒开、多协程并发极速拉取配额（Semaphore 并发限流为 8）。
 */
class QuotaPoller(
    private val quotaFetchService: QuotaFetchService = QuotaFetchService(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onAccountSnapshotUpdated: ((snapshot: AccountQuotaSnapshot) -> Unit)? = null
) {
    private val concurrencySemaphore = Semaphore(permits = 8)
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val quotasFile: File by lazy {
        AppDataPaths.resolve(AppDataPaths.QUOTAS_FILE_NAME)
    }

    private var pollerJob: Job? = null
    private var isRunning = false

    private val _quotaSnapshots = MutableStateFlow<Map<String, AccountQuotaSnapshot>>(emptyMap())
    val quotaSnapshots: StateFlow<Map<String, AccountQuotaSnapshot>> = _quotaSnapshots.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshingAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingAccountIds: StateFlow<Set<String>> = _refreshingAccountIds.asStateFlow()

    init {
        loadCachedSnapshots()
    }

    private fun loadCachedSnapshots() {
        try {
            if (quotasFile.exists()) {
                val text = quotasFile.readText(Charsets.UTF_8)
                if (text.isNotBlank()) {
                    val data = json.decodeFromString(QuotaStoreData.serializer(), text)
                    if (data.snapshots.isNotEmpty()) {
                        _quotaSnapshots.value = data.snapshots
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private val persistMutex = Mutex()

    private fun persistSnapshots(snapshots: Map<String, AccountQuotaSnapshot>? = null) {
        coroutineScope.launch {
            persistMutex.withLock {
                try {
                    val latestSnapshots = _quotaSnapshots.value
                    val content = json.encodeToString(
                        QuotaStoreData.serializer(),
                        QuotaStoreData(snapshots = latestSnapshots)
                    )
                    AtomicFileWriter.writeText(
                        target = quotasFile,
                        content = content,
                        permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
                        disallowSymlinks = true
                    ).getOrThrow()
                } catch (error: Exception) {
                    AppLog.w("Quota/Poller", error) {
                        "配额快照持久化失败：${error.message ?: "未知错误"}"
                    }
                }
            }
        }
    }

    companion object {
        const val ACTIVE_INTERVAL_MS = 60_000L      // 激活账号 1 分钟刷新一次
        const val BACKGROUND_INTERVAL_MS = 600_000L // 后台账号 10 分钟刷新一次

        /**
         * 根据账号总数自适应计算最优并发数：
         * 账号少时保底 8 并发，账号多时为账号总数的 1/2，上限封顶 32 并发。
         */
        fun calculateConcurrency(accountsCount: Int): Int {
            val dynamic = (accountsCount + 1) / 2
            return dynamic.coerceIn(8, 32)
        }
    }

    private var lastBackgroundFetchTime = 0L

    fun start(
        accountsProvider: () -> List<AccountInfo>,
        activeAccountProvider: () -> AccountInfo?,
        configProvider: (() -> com.yuzhiqiang.antigravity.domain.model.AppConfig)? = null
    ) {
        if (isRunning) return
        isRunning = true
        pollerJob = coroutineScope.launch {
            while (isActive) {
                val cfg = configProvider?.invoke()
                val isEnabled = cfg?.quotaAutoRefreshEnabled ?: true
                val activeIntervalMs = (cfg?.quotaActiveIntervalSeconds ?: 60).toLong() * 1000L
                val backgroundIntervalMs = (cfg?.quotaBackgroundIntervalSeconds ?: 600).toLong() * 1000L

                if (isEnabled) {
                    try {
                        val accounts = accountsProvider()
                        val active = activeAccountProvider()
                        if (accounts.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val shouldFetchBg = (now - lastBackgroundFetchTime) >= backgroundIntervalMs

                            // 1. 优先刷新激活账号
                            if (active != null) {
                                launch { refreshSingleInternal(active, isActiveAccount = true) }
                            }

                            // 2. 非阻塞异步并发刷新后台账号
                            if (shouldFetchBg) {
                                lastBackgroundFetchTime = now
                                val backgroundAccounts = accounts.filter { it.id != active?.id }
                                val bgSemaphore = Semaphore(calculateConcurrency(backgroundAccounts.size))
                                backgroundAccounts.forEach { bgAccount ->
                                    launch {
                                        refreshSingleInternal(
                                            bgAccount,
                                            isActiveAccount = false,
                                            customSemaphore = bgSemaphore
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                val sleepMs = if (isEnabled) activeIntervalMs.coerceAtLeast(10_000L) else 30_000L
                delay(sleepMs)
            }
        }
    }

    fun stop() {
        isRunning = false
        pollerJob?.cancel()
        pollerJob = null
    }

    /**
     * 手动触发全量动态自适应并发极速刷新
     */
    suspend fun refreshAllNow(
        accounts: List<AccountInfo>,
        activeAccount: AccountInfo?
    ): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.success(Unit)
        }

        _isRefreshing.value = true
        val dynamicPermits = calculateConcurrency(accounts.size)
        val dynamicSemaphore = Semaphore(permits = dynamicPermits)

        return try {
            coroutineScope.launch {
                val tasks = accounts.map { account ->
                    val isActive = account.id == activeAccount?.id
                    async {
                        refreshSingleInternal(account, isActive, customSemaphore = dynamicSemaphore)
                    }
                }
                tasks.awaitAll()
            }.join()
            lastBackgroundFetchTime = System.currentTimeMillis()
            Result.success(Unit)
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * 刷新单个账号配额（支持多卡片同时并发点击触发）
     */
    suspend fun refreshSingle(account: AccountInfo, isActive: Boolean): Result<AccountQuotaSnapshot> {
        return refreshSingleInternal(account, isActive)
    }

    private suspend fun refreshSingleInternal(
        account: AccountInfo,
        isActiveAccount: Boolean,
        customSemaphore: Semaphore? = null
    ): Result<AccountQuotaSnapshot> {
        val semaphoreToUse = customSemaphore ?: concurrencySemaphore
        return semaphoreToUse.withPermit {
            _refreshingAccountIds.update { it + account.id }
            try {
                val result = if (isActiveAccount) {
                    quotaFetchService.fetchActiveAccountQuota(account)
                } else {
                    quotaFetchService.fetchRemoteAccountQuota(account)
                }

                result.fold(
                    onSuccess = { snapshot ->
                        val currentMap = synchronized(this) {
                            val updated = _quotaSnapshots.value.toMutableMap()
                            updated[account.id] = snapshot
                            _quotaSnapshots.value = updated
                            updated
                        }
                        persistSnapshots(currentMap)
                        onAccountSnapshotUpdated?.invoke(snapshot)
                    },
                    onFailure = { error ->
                        // 如果失败且当前本地尚未有此账号快照，生成一个保底的基础快照，避免 UI 永远卡在骨架屏
                        synchronized(this) {
                            if (!_quotaSnapshots.value.containsKey(account.id)) {
                                val fallbackSnapshot = AccountQuotaSnapshot(
                                    accountId = account.id,
                                    email = account.email,
                                    fetchedAt = System.currentTimeMillis(),
                                    tierName = account.profile.tier.name,
                                    tier = account.profile.tier,
                                    isPro = account.profile.tier != com.yuzhiqiang.antigravity.domain.model.account.AccountTier.FREE,
                                    models = emptyList(),
                                    groups = emptyList(),
                                    isError = true,
                                    errorMessage = error.message
                                )
                                val updated = _quotaSnapshots.value.toMutableMap()
                                updated[account.id] = fallbackSnapshot
                                _quotaSnapshots.value = updated
                            }
                        }
                    }
                )

                result
            } finally {
                _refreshingAccountIds.update { it - account.id }
            }
        }
    }
}
