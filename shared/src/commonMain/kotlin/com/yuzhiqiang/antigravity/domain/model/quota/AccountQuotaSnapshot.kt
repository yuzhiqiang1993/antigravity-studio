package com.yuzhiqiang.antigravity.domain.model.quota

import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import kotlinx.serialization.Serializable

@Serializable
data class QuotaGroup(
    val family: String, // claude, gemini
    val label: String,
    val displayName: String,
    val buckets: List<ModelQuotaInfo> = emptyList()
)

/**
 * 账号配额全量快照
 */

@Serializable
data class AccountQuotaSnapshot(
    val accountId: String,
    val email: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    val tierName: String? = null,
    val tier: AccountTier = AccountTier.FREE,
    val isPro: Boolean = false,
    val aiCredits: Double? = null,
    val models: List<ModelQuotaInfo> = emptyList(),
    val groups: List<QuotaGroup> = emptyList(),
    val isForbidden: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {

    /**
     * 查找 Claude 3.7 / 3.5 主力配额
     */
    fun claudeQuota(): ModelQuotaInfo? {
        return models.firstOrNull { it.id.contains("claude-3-7") || it.id.contains("claude-3.7") }
            ?: models.firstOrNull { it.id.contains("claude") }
            ?: groups.firstOrNull { it.family == "claude" }?.buckets?.firstOrNull()
    }

    /**
     * 查找 Gemini 2.5 Pro 主力配额
     */
    fun geminiProQuota(): ModelQuotaInfo? {
        return models.firstOrNull { it.id.contains("gemini-2.5-pro") || it.id.contains("gemini-2-pro") }
            ?: models.firstOrNull { it.id.contains("pro") && it.family == "gemini" }
            ?: groups.firstOrNull { it.family == "gemini" }?.buckets?.firstOrNull()
    }

    /**
     * 查找 Gemini 2.5 Flash 主力配额
     */
    fun geminiFlashQuota(): ModelQuotaInfo? {
        return models.firstOrNull { it.id.contains("gemini-2.5-flash") || it.id.contains("gemini-2-flash") }
            ?: models.firstOrNull { it.id.contains("flash") && (it.family == "gemini" || it.family == null) }
            ?: groups.firstOrNull { it.family == "gemini" && it.displayName.contains("flash", ignoreCase = true) }?.buckets?.firstOrNull()
            ?: groups.firstOrNull { it.family == "gemini" }?.buckets?.getOrNull(1)
    }

    /**
     * 获取用于快捷展示的重点模型配额列表
     */
    fun primaryQuotas(): List<ModelQuotaInfo> {
        val result = mutableListOf<ModelQuotaInfo>()
        claudeQuota()?.let { result.add(it) }
        geminiProQuota()?.let { result.add(it) }
        geminiFlashQuota()?.let { result.add(it) }
        if (result.isEmpty() && models.isNotEmpty()) {
            result.addAll(models.take(3))
        }
        return result.distinctBy { it.id }
    }

    /**
     * 规范化展示用的模型族配额分组（完全对齐 Cockpit 插件的 Gemini/Claude 5h/Weekly 结构，且保证 Gemini 优先置顶）
     */
    fun normalizedDisplayGroups(): List<QuotaGroup> {
        val list = if (groups.isNotEmpty()) {
            groups
        } else {
            val result = mutableListOf<QuotaGroup>()
            val geminiModels = models.filter { it.family == "gemini" || it.id.contains("gemini") }
            val claudeModels = models.filter { it.family == "claude" || it.id.contains("claude") || it.id.contains("gpt") }

            if (geminiModels.isNotEmpty()) {
                val fiveHour = geminiModels.firstOrNull { it.window == QuotaWindow.FIVE_HOUR } ?: geminiModels.first()
                val weekly = geminiModels.firstOrNull { it.window == QuotaWindow.WEEKLY }
                    ?: fiveHour.copy(id = "gemini-weekly", displayName = "周额度", window = QuotaWindow.WEEKLY)
                result.add(
                    QuotaGroup(
                        family = "gemini",
                        label = "Gemini",
                        displayName = "Gemini 模型",
                        buckets = listOf(
                            fiveHour.copy(displayName = "五小时额度", window = QuotaWindow.FIVE_HOUR),
                            weekly.copy(displayName = "周额度", window = QuotaWindow.WEEKLY)
                        )
                    )
                )
            }

            if (claudeModels.isNotEmpty()) {
                val fiveHour = claudeModels.firstOrNull { it.window == QuotaWindow.FIVE_HOUR } ?: claudeModels.first()
                val weekly = claudeModels.firstOrNull { it.window == QuotaWindow.WEEKLY }
                    ?: fiveHour.copy(id = "claude-weekly", displayName = "周额度", window = QuotaWindow.WEEKLY)
                result.add(
                    QuotaGroup(
                        family = "claude",
                        label = "Claude",
                        displayName = "Claude 模型",
                        buckets = listOf(
                            fiveHour.copy(displayName = "五小时额度", window = QuotaWindow.FIVE_HOUR),
                            weekly.copy(displayName = "周额度", window = QuotaWindow.WEEKLY)
                        )
                    )
                )
            }

            result
        }

        // 严格排序：Gemini 在前 (0)，Claude 在后 (1)
        return list.sortedBy { if (it.family == "gemini") 0 else 1 }
    }

    /**
     * 获取当前账号全部配额中最低的健康度分值 (0~100)
     */
    fun lowestQuotaPct(): Int {
        if (isError) return 0
        val allBuckets = normalizedDisplayGroups().flatMap { it.buckets }
        if (allBuckets.isNotEmpty()) {
            return allBuckets.minOf { it.percentage }
        }
        if (models.isNotEmpty()) {
            return models.minOf { it.percentage }
        }
        return 100
    }
}

