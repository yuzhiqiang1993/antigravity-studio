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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 多线程/高并发配额智能轮询器。
 * 激活账号 60 秒高频刷新，后台账号 300 秒（5分钟）低频轮询，支持多协程并发极速拉取配额（Semaphore 并发限流为 8）。
 */
class QuotaPoller(
    private val quotaFetchService: QuotaFetchService = QuotaFetchService(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onAccountSnapshotUpdated: ((snapshot: AccountQuotaSnapshot) -> Unit)? = null
) {
    private val concurrencySemaphore = Semaphore(permits = 8)

    private var pollerJob: Job? = null
    private var isRunning = false

    private val _quotaSnapshots = MutableStateFlow<Map<String, AccountQuotaSnapshot>>(emptyMap())
    val quotaSnapshots: StateFlow<Map<String, AccountQuotaSnapshot>> = _quotaSnapshots.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshingAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingAccountIds: StateFlow<Set<String>> = _refreshingAccountIds.asStateFlow()

    companion object {
        const val ACTIVE_INTERVAL_MS = 60_000L      // 激活账号 1 分钟刷新一次
        const val BACKGROUND_INTERVAL_MS = 300_000L // 后台账号 5 分钟刷新一次
    }

    private var lastBackgroundFetchTime = 0L

    fun start(accountsProvider: () -> List<AccountInfo>, activeAccountProvider: () -> AccountInfo?) {
        if (isRunning) return
        isRunning = true
        pollerJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val accounts = accountsProvider()
                    val active = activeAccountProvider()
                    if (accounts.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val shouldFetchBg = (now - lastBackgroundFetchTime) >= BACKGROUND_INTERVAL_MS

                        // 1. 并发刷新激活账号
                        if (active != null) {
                            launch { refreshSingleInternal(active, isActiveAccount = true) }
                        }

                        // 2. 周期性多协程并发刷新后台账号
                        if (shouldFetchBg) {
                            val backgroundAccounts = accounts.filter { it.id != active?.id }
                            val deferredList = backgroundAccounts.map { bgAccount ->
                                async { refreshSingleInternal(bgAccount, isActiveAccount = false) }
                            }
                            deferredList.awaitAll()
                            lastBackgroundFetchTime = now
                        }
                    }
                } catch (_: Exception) {
                }
                delay(ACTIVE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        isRunning = false
        pollerJob?.cancel()
        pollerJob = null
    }

    /**
     * 手动触发全量并发极速刷新
     */
    suspend fun refreshAllNow(
        accounts: List<AccountInfo>,
        activeAccount: AccountInfo?
    ): Result<Unit> {
        if (accounts.isEmpty()) {
            return Result.success(Unit)
        }

        _isRefreshing.value = true
        return try {
            coroutineScope.launch {
                val tasks = accounts.map { account ->
                    val isActive = account.id == activeAccount?.id
                    async {
                        refreshSingleInternal(account, isActive)
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

    private suspend fun refreshSingleInternal(account: AccountInfo, isActiveAccount: Boolean): Result<AccountQuotaSnapshot> {
        return concurrencySemaphore.withPermit {
            _refreshingAccountIds.value = _refreshingAccountIds.value + account.id
            try {
                val result = if (isActiveAccount) {
                    quotaFetchService.fetchActiveAccountQuota(account)
                } else {
                    quotaFetchService.fetchRemoteAccountQuota(account)
                }

                result.onSuccess { snapshot ->
                    synchronized(this) {
                        val currentMap = _quotaSnapshots.value.toMutableMap()
                        currentMap[account.id] = snapshot
                        _quotaSnapshots.value = currentMap
                    }
                    onAccountSnapshotUpdated?.invoke(snapshot)
                }

                result
            } finally {

                _refreshingAccountIds.value = _refreshingAccountIds.value - account.id
            }
        }
    }
}




