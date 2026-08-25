package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.SmartSwitchStrategy
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 自动按额度/429 智能切号调度器。
 */
class SmartSwitchCoordinator(
    private val accountStore: AccountStore,
    private val configStore: ConfigStore,
    private val hotSwitchCoordinator: HotSwitchCoordinator,
    private val quotasProvider: () -> Map<String, AccountQuotaSnapshot>
) {
    private val mutex = Mutex()
    private var lastAutoSwitchTime = 0L

    data class SmartSwitchOutcome(
        val triggered: Boolean,
        val targetAccount: AccountInfo? = null,
        val reason: String? = null
    )

    /**
     * 当代理层捕获到 429 Resource Exhausted 状态码时，触发自动切号
     */
    suspend fun trySmartSwitchOn429(failedModelId: String?): SmartSwitchOutcome = mutex.withLock {
        val config = configStore.currentConfig.smartSwitchConfig
        if (!config.enabled) {
            return SmartSwitchOutcome(triggered = false, reason = "智能切号未启用")
        }

        val now = System.currentTimeMillis()
        val cooldownMs = config.cooldownSeconds * 1000L
        if ((now - lastAutoSwitchTime) < cooldownMs) {
            return SmartSwitchOutcome(triggered = false, reason = "处于切号冷却期 (${(cooldownMs - (now - lastAutoSwitchTime)) / 1000}s 剩余)")
        }

        if (config.protectActiveGeneration && WorkflowLeaseManager.isLocked()) {
            return SmartSwitchOutcome(triggered = false, reason = "当前工作流处于锁定保护状态")
        }

        val currentActive = accountStore.currentActiveAccount()
        val accounts = accountStore.currentAccounts().filter {
            it.id != currentActive?.id && !it.tokens.isExpired()
        }

        if (accounts.isEmpty()) {
            return SmartSwitchOutcome(triggered = false, reason = "无可用备用账号")
        }

        val candidate = selectBestCandidate(accounts, config.strategy, failedModelId)
            ?: return SmartSwitchOutcome(triggered = false, reason = "未找到满足配额要求的备用账号")

        val switchResult = hotSwitchCoordinator.switchAccount(candidate)
        return if (switchResult.isSuccess) {
            lastAutoSwitchTime = now
            SmartSwitchOutcome(triggered = true, targetAccount = candidate, reason = "遭遇 429，已自动切号至 ${candidate.email}")
        } else {
            SmartSwitchOutcome(triggered = false, reason = switchResult.exceptionOrNull()?.message ?: "切号执行失败")
        }
    }

    /**
     * 周期性检查当前激活账号额度是否低于下限阈值，并在必要时切号
     */
    suspend fun evaluateAndSwitchIfDepleted(): SmartSwitchOutcome = mutex.withLock {
        val config = configStore.currentConfig.smartSwitchConfig
        if (!config.enabled) {
            return SmartSwitchOutcome(triggered = false, reason = "智能切号未启用")
        }

        val currentActive = accountStore.currentActiveAccount() ?: return SmartSwitchOutcome(triggered = false)
        val quotas = quotasProvider()
        val activeQuota = quotas[currentActive.id] ?: return SmartSwitchOutcome(triggered = false)

        val primaryQuotas = activeQuota.primaryQuotas()
        val anyDepleted = primaryQuotas.any { it.percentage <= config.triggerThresholdPercent || it.isExhausted }
        if (!anyDepleted) {
            return SmartSwitchOutcome(triggered = false)
        }

        val now = System.currentTimeMillis()
        val cooldownMs = config.cooldownSeconds * 1000L
        if ((now - lastAutoSwitchTime) < cooldownMs) {
            return SmartSwitchOutcome(triggered = false, reason = "处于切号冷却期")
        }

        val candidates = accountStore.currentAccounts().filter {
            it.id != currentActive.id && !it.tokens.isExpired()
        }
        if (candidates.isEmpty()) {
            return SmartSwitchOutcome(triggered = false, reason = "无可用备用账号")
        }

        val candidate = selectBestCandidate(candidates, config.strategy, null)
            ?: return SmartSwitchOutcome(triggered = false, reason = "未找到额度充足的备用账号")

        val switchResult = hotSwitchCoordinator.switchAccount(candidate)
        return if (switchResult.isSuccess) {
            lastAutoSwitchTime = now
            SmartSwitchOutcome(triggered = true, targetAccount = candidate, reason = "配额低于阈值，已自动切号至 ${candidate.email}")
        } else {
            SmartSwitchOutcome(triggered = false, reason = switchResult.exceptionOrNull()?.message ?: "切号失败")
        }
    }

    private fun selectBestCandidate(
        candidates: List<AccountInfo>,
        strategy: SmartSwitchStrategy,
        targetModelId: String?
    ): AccountInfo? {
        val quotas = quotasProvider()
        return when (strategy) {
            SmartSwitchStrategy.HIGHEST_QUOTA_FIRST -> {
                candidates.maxByOrNull { acc ->
                    val snapshot = quotas[acc.id] ?: return@maxByOrNull 0.0
                    if (!targetModelId.isNullOrBlank()) {
                        val specific = snapshot.models.firstOrNull { it.id.contains(targetModelId, ignoreCase = true) }
                        specific?.remainingFraction ?: 0.0
                    } else {
                        snapshot.primaryQuotas().map { it.remainingFraction }.average().takeIf { !it.isNaN() } ?: 0.0
                    }
                } ?: candidates.firstOrNull()
            }

            SmartSwitchStrategy.ROUND_ROBIN -> {
                candidates.firstOrNull()
            }
        }
    }
}
