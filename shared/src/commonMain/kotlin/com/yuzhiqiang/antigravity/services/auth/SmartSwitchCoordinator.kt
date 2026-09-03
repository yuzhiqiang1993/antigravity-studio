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
    private val roundRobinIndex = java.util.concurrent.atomic.AtomicInteger(0)

    data class SmartSwitchOutcome(
        val triggered: Boolean,
        val targetAccount: AccountInfo? = null,
        val reason: String? = null,
        val requiresUserAction: Boolean = false
    )

    /**
     * 当代理层捕获到 429 Resource Exhausted 状态码时，触发自动切号
     */
    suspend fun trySmartSwitchOn429(failedModelId: String?): SmartSwitchOutcome = mutex.withLock {
        val s = com.yuzhiqiang.antigravity.i18n.currentStrings()
        val config = configStore.currentConfig.smartSwitchConfig
        if (!config.enabled) {
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonDisabled)
        }

        val now = System.currentTimeMillis()
        val cooldownMs = config.cooldownSeconds * 1000L
        if ((now - lastAutoSwitchTime) < cooldownMs) {
            val remainingSec = (cooldownMs - (now - lastAutoSwitchTime)) / 1000
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonCooldown(remainingSec))
        }

        if (config.protectActiveGeneration && WorkflowLeaseManager.isLocked()) {
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonWorkflowLocked)
        }

        val currentActive = accountStore.currentActiveAccount()
        val accounts = accountStore.currentAccounts().filter {
            it.id != currentActive?.id && !it.tokens.isExpired()
        }

        if (accounts.isEmpty()) {
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonNoBackupAccounts)
        }

        val candidate = selectBestCandidate(accounts, config.strategy, failedModelId)
            ?: return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonNoEligibleCandidate)

        lastAutoSwitchTime = now
        return buildConfirmationOutcome(candidate, s.smartSwitchTriggerReason429)
    }

    /**
     * 周期性检查当前激活账号额度是否低于下限阈值，并在必要时切号
     */
    suspend fun evaluateAndSwitchIfDepleted(): SmartSwitchOutcome = mutex.withLock {
        val s = com.yuzhiqiang.antigravity.i18n.currentStrings()
        val config = configStore.currentConfig.smartSwitchConfig
        if (!config.enabled) {
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonDisabled)
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
            val remainingSec = (cooldownMs - (now - lastAutoSwitchTime)) / 1000
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonCooldown(remainingSec))
        }

        val candidates = accountStore.currentAccounts().filter {
            it.id != currentActive.id && !it.tokens.isExpired()
        }
        if (candidates.isEmpty()) {
            return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonNoBackupAccounts)
        }

        val candidate = selectBestCandidate(candidates, config.strategy, null)
            ?: return SmartSwitchOutcome(triggered = false, reason = s.smartSwitchReasonNoEligibleCandidate)

        lastAutoSwitchTime = now
        return buildConfirmationOutcome(candidate, s.smartSwitchTriggerReasonLowQuota)
    }

    private fun buildConfirmationOutcome(
        candidate: AccountInfo,
        triggerReason: String
    ): SmartSwitchOutcome {
        val s = com.yuzhiqiang.antigravity.i18n.currentStrings()
        val reason = if (hotSwitchCoordinator.isSwitching.value) {
            s.smartSwitchReasonTaskRunning(triggerReason)
        } else {
            s.smartSwitchReasonSuggestSwitch(triggerReason, candidate.email)
        }
        return SmartSwitchOutcome(
            triggered = false,
            targetAccount = candidate,
            reason = reason,
            requiresUserAction = true
        )
    }

    private fun selectBestCandidate(
        candidates: List<AccountInfo>,
        strategy: SmartSwitchStrategy,
        targetModelId: String?
    ): AccountInfo? {
        if (candidates.isEmpty()) return null
        val quotas = quotasProvider()
        return when (strategy) {
            SmartSwitchStrategy.HIGHEST_QUOTA_FIRST -> {
                candidates.maxByOrNull { acc ->
                    val snapshot = quotas[acc.id] ?: return@maxByOrNull 0.0
                    if (!targetModelId.isNullOrBlank()) {
                        val specific = snapshot.models.firstOrNull { it.id.contains(targetModelId, ignoreCase = true) }
                        specific?.remainingFraction ?: 0.0
                    } else {
                        val fractions = snapshot.primaryQuotas().map { it.remainingFraction }
                        if (fractions.isEmpty()) 0.0 else (fractions.minOrNull() ?: 0.0)
                    }
                } ?: candidates.firstOrNull()
            }

            SmartSwitchStrategy.ROUND_ROBIN -> {
                val idx = (roundRobinIndex.getAndIncrement() and Int.MAX_VALUE) % candidates.size
                candidates[idx]
            }
        }
    }
}
