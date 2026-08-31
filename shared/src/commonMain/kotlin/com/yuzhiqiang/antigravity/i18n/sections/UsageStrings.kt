package com.yuzhiqiang.antigravity.i18n.sections

interface UsageStrings {
    val navUsage: String
    val usageSubtitle: String
    val usageStatsBadge: (conversations: Long, activeDays: Int) -> String
    val usageTokensCount: (tokens: String) -> String
    val usageCostValue: (cost: String) -> String
    val usageCostLowerBound: (cost: String) -> String
    val usageCostUnavailable: String
    val usagePricingSource: (source: String) -> String

    // 时间范围（与插件的滚动/日历范围语义一致）
    val usageTimeRange24h: String
    val usageTimeRange7d: String
    val usageTimeRange14d: String
    val usageTimeRange30d: String
    val usageTimeRangeToday: String
    val usageTimeRangeThisWeek: String
    val usageTimeRangeThisMonth: String
    val usageTimeRangeAllTime: String
    val usageTimeRangeCustom: String

    // 数据来源
    val usageSourceAll: String
    val usageSourceIde: String
    val usageSourceApp: String
    val usageSourceCli: String

    // Hero KPI 卡片
    val usageOverviewTitle: String
    val usageKpiCostTitle: String
    val usageKpiCostSavings: (savings: String) -> String
    val usageKpiTotalTokensTitle: String
    val usageKpiTokensDetail: (input: String, output: String, reasoning: String) -> String
    val usageKpiCacheHitRatioTitle: String
    val usageKpiCacheReadDetail: (cacheRead: String) -> String
    val usageKpiCallsTitle: String
    val usageKpiConversationsDetail: (count: Long) -> String
    val usageKpiActiveDays: (days: Int) -> String
    val usageKpiPricingWarning: String
    val usageCacheRate: (pct: String) -> String

    // 固定本地日统计
    val usageTodaySummaryTitle: String
    val usageTodayActiveModels: (count: Long) -> String
    val usageTodayConversations: (count: Long) -> String
    val usageTodayEmpty: String

    // 图表与构成
    val usageTrendChartTitle: String
    val usageHourlyTrendChartTitle: String
    val usageTrendChartEmpty: String
    val usageDailyTooltipHeader: (date: String, calls: Long) -> String
    val usageDailyTooltipTotal: (tokens: String) -> String
    val usageDailyTooltipCost: (cost: String) -> String
    val usageDailyTooltipCostLabel: (cost: String) -> String
    val usageCompositionTitle: String
    val usageTokenPromptInput: String
    val usageTokenCacheTotal: String
    val usageTokenCacheRead: String
    val usageTokenCacheWrite: String
    val usageTokenModelOutput: String
    val usageTokenThinking: String

    // 活跃度与周模式
    val usageActivityHeatmapTitle: String
    val usageActivityHeatmapTip: (date: String, tokens: String, calls: Long) -> String
    val usageWeekdayPatternTitle: String
    val usageWeekdayAvgCalls: (avg: Long) -> String
    val usageWeekdayLabel: String
    val usageWeekendLabel: String
    val usagePeakLabel: String

    // 月度账单
    val usageMonthlyBreakdownTitle: String
    val usageMonthlyEmpty: String

    // 热门模型与来源
    val usageTopModelsTitle: String
    val usageTopModelsEmpty: String
    val usageModelCallsCount: (calls: Long) -> String
    val usageModelTokensDetail: (input: String, output: String, cacheRead: String, cacheWrite: String, reasoning: String) -> String
    val usageModelUsageIncomplete: String
    val usageSourceBreakdownTitle: String
    val usageSourceTokensCost: (tokens: String, cost: String) -> String
    val usageSourceTokensCostLabel: (tokens: String, cost: String) -> String
    val usageModelCount: (count: Int) -> String

    // 高消耗会话
    val usageTopConversationsTitle: String
    val usageTopConversationsEmpty: String
    val usageConversationTokensDetail: (tokens: String, calls: Long) -> String
    val usageConversationCount: (count: Int) -> String

    val usageRefreshSuccessNotice: (convoCount: Long) -> String
    val usageCustomDateDialogTitle: String
    val usageCustomDateStartLabel: String
    val usageCustomDateEndLabel: String
    val usageCustomDateFollowNow: String
    val usageCustomDateConfirm: String
    val usageCustomDateNowValue: String
    val usageCustomDatePlaceholder: String
    val usageCustomDateInvalid: String
    val usageCustomDatePreset3Days: String
    val usageCustomDatePreset10Days: String
    val usageCustomDatePreset60Days: String
    val usageCustomDatePresetLastMonth: String

    val settingsCustomPricingTitle: String
    val settingsCustomPricingDesc: String
    val settingsCustomPricingPlaceholder: String
    val settingsCustomPricingSelectFile: String
}
