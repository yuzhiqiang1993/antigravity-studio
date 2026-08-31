package com.yuzhiqiang.antigravity.domain.model.usage

import kotlinx.serialization.Serializable

/**
 * 单次模型调用的 Token 原始明细
 */
@Serializable
data class TokenEntry(
    val responseId: String? = null,
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    /** 真实响应/计费模型；不要用运行时占位符覆盖它。 */
    val model: String = "",
    val modelDisplayName: String? = null,
    val modelCanonicalId: String? = null,
    val modelCatalogId: String? = null,
    val modelRuntimeId: String? = null,
    val modelAggregationId: String? = null,
    val modelPricingIds: List<String> = emptyList(),
    /** 例如 response-model、display-name、runtime-model、unknown。 */
    val modelEvidenceSource: String? = null,
    /** 数据源没有返回的维度；与明确返回 0 区分。 */
    val missingUsageFields: List<String> = emptyList(),
    val provider: String = "",
    val timestamp: String = "", // ISO-8601 UTC 字符串，例如 2026-08-31T06:15:09Z
    val conversationId: String = "",
    val appSource: String = "" // "ide", "standalone", "cli"
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning

    /** 用于去重的唯一特征指纹 */
    fun fingerprint(): String {
        if (!responseId.isNullOrBlank()) return "rid:$responseId"
        val cleanModel = model.trim().lowercase()
        val cleanProvider = provider.trim().lowercase()
        // 保留毫秒，避免同一秒内没有 responseId 的两次调用被错误合并。
        val tsKey = if (timestamp.length >= 23) timestamp.substring(0, 23) else timestamp
        return "$input:$output:$cacheRead:$cacheWrite:$reasoning:$tsKey:$cleanModel:$cleanProvider"
    }
}

/**
 * 单个会话维度的 Token 数据包装
 */
@Serializable
data class ConversationUsageData(
    val conversationId: String,
    val title: String = "",
    val appSource: String = "ide",
    val entries: List<TokenEntry> = emptyList()
) {
    val totalTokens: Long
        get() = entries.sumOf { it.totalTokens }
    val totalCalls: Long
        get() = entries.count { it.totalTokens > 0L }.toLong()
}

/**
 * 每日消耗聚合桶
 */
@Serializable
data class DailyUsageBucket(
    val date: String, // YYYY-MM-DD
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val savingsUsd: Double = 0.0,
    val pricingMatched: Boolean = true,
    val costLowerBound: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 小时消耗聚合桶 (0-23)
 */
@Serializable
data class HourlyUsageBucket(
    val hour: Int, // 0..23
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val pricingMatched: Boolean = true,
    val costLowerBound: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 星期分布聚合桶 (0=周一 .. 6=周日)
 */
@Serializable
data class WeekdayUsageBucket(
    val day: Int, // 0=Mon .. 6=Sun
    val label: String, // "Mon", "Tue", etc.
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 自然月度账单聚合桶 (YYYY-MM)
 */
@Serializable
data class MonthlyUsageBucket(
    val yearMonth: String, // "2026-08"
    val label: String,     // "2026年8月" / "Aug 2026"
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val topModels: List<ModelUsageBucket> = emptyList(),
    val pricingMatched: Boolean = false,
    val costLowerBound: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 会话用量排行桶
 */
@Serializable
data class ConversationUsageBucket(
    val conversationId: String,
    val title: String,
    val appSource: String,
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val lastActiveTimestamp: String = "",
    val pricingMatched: Boolean = true,
    val costLowerBound: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/** 数据源未返回的计费维度调用数；与明确返回 0 区分。 */
@Serializable
data class MissingUsageCounts(
    val output: Long = 0L,
    val cache: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L
)

/**
 * 模型消耗聚合桶
 */
@Serializable
data class ModelUsageBucket(
    val modelId: String,
    val displayName: String,
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val savingsUsd: Double = 0.0,
    val canonicalId: String? = null,
    val aggregationId: String? = null,
    val pricingModelIds: List<String> = emptyList(),
    val rawModelIds: List<String> = emptyList(),
    val modelEvidenceSource: String? = null,
    val missingUsage: MissingUsageCounts? = null,
    val longContext: LongContextUsageBucket? = null,
    /** 计费解析元数据；与插件的 matched/source/confidence 语义对应。 */
    val pricingSource: String = "unknown",
    val pricingMatched: Boolean = false,
    val pricingConfidence: String = "low",
    val costLowerBound: Boolean = false,
    val longContextPricingApplied: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/** 单次输入超过 272K Token 的模型调用子桶，用于展示长上下文计费范围。 */
@Serializable
data class LongContextUsageBucket(
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 来源应用聚合桶
 */
@Serializable
data class AppSourceUsageBucket(
    val appSource: String, // "ide", "standalone", "cli"
    val displayName: String,
    val input: Long = 0L,
    val output: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val reasoning: Long = 0L,
    val calls: Long = 0L,
    val costUsd: Double = 0.0,
    val pricingMatched: Boolean = true,
    val costLowerBound: Boolean = false
) {
    val totalTokens: Long
        get() = input + output + cacheRead + cacheWrite + reasoning
}

/**
 * 深度聚合用量统计结果；字段语义与插件端 DeepUsageStats 保持一致。
 */
@Serializable
data class DeepUsageStats(
    val totalInput: Long = 0L,
    val totalOutput: Long = 0L,
    val totalCacheRead: Long = 0L,
    val totalCacheWrite: Long = 0L,
    val totalReasoning: Long = 0L,
    val totalCalls: Long = 0L,
    val totalConversations: Long = 0L,
    val daysActive: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val estimatedSavingsUsd: Double = 0.0,
    /** 固定按本地时区计算的“今天”快照，不受当前时间范围筛选影响。 */
    val todayDate: String = "",
    val todayInput: Long = 0L,
    val todayOutput: Long = 0L,
    val todayCacheRead: Long = 0L,
    val todayCacheWrite: Long = 0L,
    val todayReasoning: Long = 0L,
    val todayCalls: Long = 0L,
    val todayConversations: Long = 0L,
    val todayActiveModels: Long = 0L,
    val todayCostUsd: Double = 0.0,
    val todaySavingsUsd: Double = 0.0,
    val todayPricingMatched: Boolean = true,
    val todayCostLowerBound: Boolean = false,
    val dateRangeFrom: String = "",
    val dateRangeTo: String = "",
    val dailyBuckets: List<DailyUsageBucket> = emptyList(),
    val hourlyBuckets: List<HourlyUsageBucket> = emptyList(),
    val weekdayBuckets: List<WeekdayUsageBucket> = emptyList(),
    val monthlyBuckets: List<MonthlyUsageBucket> = emptyList(),
    val modelBuckets: List<ModelUsageBucket> = emptyList(),
    val sourceBuckets: List<AppSourceUsageBucket> = emptyList(),
    val topConversations: List<ConversationUsageBucket> = emptyList(),
    val generatedAt: Long = 0L
) {
    val totalTokens: Long
        get() = totalInput + totalOutput + totalCacheRead + totalCacheWrite + totalReasoning

    /** 插件 DeepUsageStats 的 totalCache 代表 cache read；cache write 单独暴露。 */
    val totalCache: Long
        get() = totalCacheRead

    /** 与插件 cacheRate 一致：Cache Read / 全部 Token（cache write 不计入命中率）。 */
    val cacheHitRatio: Double
        get() = if (totalTokens > 0L) totalCacheRead.toDouble() / totalTokens.toDouble() else 0.0

    val todayTokens: Long
        get() = todayInput + todayOutput + todayCacheRead + todayCacheWrite + todayReasoning

    /** 今日缓存读取率与插件一致，分母包含全部五类 Token。 */
    val todayCacheHitRatio: Double
        get() = if (todayTokens > 0L) todayCacheRead.toDouble() / todayTokens.toDouble() else 0.0

    /** 便于 UI 与插件字段名 cacheRate 对照。 */
    val todayCacheRate: Double
        get() = todayCacheHitRatio

    val hasTodayUsage: Boolean
        get() = todayCalls > 0L
}

/**
 * 时间筛选范围枚举（完全对齐插件端 滚动与日历周期）
 */
enum class UsageTimeRange(val id: String) {
    ROLLING_24H("24h"),
    ROLLING_7D("7d"),
    ROLLING_14D("14d"),
    ROLLING_30D("30d"),
    CALENDAR_TODAY("today"),
    CALENDAR_THIS_WEEK("this-week"),
    CALENDAR_THIS_MONTH("this-month"),
    ALL_TIME("all"),
    CUSTOM("custom")
}

/**
 * 自定义日期范围
 */
@Serializable
data class CustomDateRange(
    val startDate: String, // YYYY-MM-DD 或 ISO 字符串
    val endDate: String = "", // YYYY-MM-DD 或 ISO 字符串，若为空或 followNow 为 true 则截止到当前
    val followNow: Boolean = false
)
