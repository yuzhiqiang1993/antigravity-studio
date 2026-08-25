package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 账号 Token 自动续签与生命周期巡检管理器。
 * 周期性扫描所有托管账号，在 Access Token 到期前 5 分钟自动静默刷新。
 */
class TokenRenewalManager(
    private val accountStore: AccountStore,
    private val googleAuthService: GoogleAuthService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val mutex = Mutex()
    private var renewalJob: Job? = null
    private var isRunning = false

    companion object {
        const val CHECK_INTERVAL_MS = 60_000L // 1 分钟巡检一次
        const val EXPIRY_BUFFER_SECONDS = 300L // 提前 5 分钟刷新
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        renewalJob = coroutineScope.launch {
            while (isActive) {
                try {
                    checkAndRenewTokens()
                } catch (_: Exception) {
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        isRunning = false
        renewalJob?.cancel()
        renewalJob = null
    }

    /**
     * 手动触发对指定或所有账号的 Token 检查与续期
     */
    suspend fun refreshAllNow(): Result<Int> = mutex.withLock {
        var refreshedCount = 0
        val accounts = accountStore.currentAccounts()
        for (account in accounts) {
            try {
                val newTokensResult = googleAuthService.refreshAccessToken(account.tokens.refreshToken)
                if (newTokensResult.isSuccess) {
                    val newTokens = newTokensResult.getOrThrow()
                    accountStore.updateTokens(account.email, newTokens)
                    refreshedCount++
                } else {
                    val errorMsg = newTokensResult.exceptionOrNull()?.message ?: "刷新 Token 失败"
                    accountStore.markAccountError(account.email, errorMsg)
                }
            } catch (e: Exception) {
                accountStore.markAccountError(account.email, e.message ?: "刷新 Token 发生异常")
            }
        }
        Result.success(refreshedCount)
    }

    /**
     * 刷新单个指定账号
     */
    suspend fun refreshAccount(email: String): Result<Unit> = mutex.withLock {
        val account = accountStore.currentAccounts().firstOrNull { it.email.equals(email, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("未找到账号: $email"))

        try {
            val newTokensResult = googleAuthService.refreshAccessToken(account.tokens.refreshToken)
            if (newTokensResult.isSuccess) {
                val newTokens = newTokensResult.getOrThrow()
                accountStore.updateTokens(account.email, newTokens)
                Result.success(Unit)
            } else {
                val error = newTokensResult.exceptionOrNull() ?: IllegalStateException("刷新失败")
                accountStore.markAccountError(account.email, error.message ?: "刷新失败")
                Result.failure(error)
            }
        } catch (e: Exception) {
            accountStore.markAccountError(account.email, e.message ?: "刷新异常")
            Result.failure(e)
        }
    }

    private suspend fun checkAndRenewTokens() = mutex.withLock {
        val accounts = accountStore.currentAccounts()
        for (account in accounts) {
            if (account.tokens.isExpiringSoon(EXPIRY_BUFFER_SECONDS)) {
                try {
                    val newTokensResult = googleAuthService.refreshAccessToken(account.tokens.refreshToken)
                    if (newTokensResult.isSuccess) {
                        accountStore.updateTokens(account.email, newTokensResult.getOrThrow())
                    } else {
                        accountStore.markAccountError(account.email, newTokensResult.exceptionOrNull()?.message ?: "自动续期失败")
                    }
                } catch (e: Exception) {
                    accountStore.markAccountError(account.email, e.message ?: "自动续期异常")
                }
            }
        }
    }
}
