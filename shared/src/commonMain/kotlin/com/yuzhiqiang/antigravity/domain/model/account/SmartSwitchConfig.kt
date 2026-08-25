package com.yuzhiqiang.antigravity.domain.model.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SmartSwitchStrategy {
    @SerialName("HIGHEST_QUOTA_FIRST")
    HIGHEST_QUOTA_FIRST,

    @SerialName("ROUND_ROBIN")
    ROUND_ROBIN;

    val displayName: String
        get() = when (this) {
            HIGHEST_QUOTA_FIRST -> "高额度优先 (推荐)"
            ROUND_ROBIN -> "循环轮询"
        }
}

/**
 * 自动智能切号配置策略
 */
@Serializable
data class SmartSwitchConfig(
    @SerialName("enabled")
    val enabled: Boolean = false,

    /**
     * 触发自动切号的配额下限百分比 (如当前账号任一主力模型额度低于此阈值时触发)
     */
    @SerialName("trigger_threshold_percent")
    val triggerThresholdPercent: Int = 5,

    /**
     * 账号调度策略
     */
    @SerialName("strategy")
    val strategy: SmartSwitchStrategy = SmartSwitchStrategy.HIGHEST_QUOTA_FIRST,

    /**
     * 两次自动切号之间的最小冷却时间（秒），防止频繁来回切换
     */
    @SerialName("cooldown_seconds")
    val cooldownSeconds: Int = 60,

    /**
     * 是否在长对话/工作流生成中加锁保护（生成期间禁止自动切号）
     */
    @SerialName("protect_active_generation")
    val protectActiveGeneration: Boolean = true
)
