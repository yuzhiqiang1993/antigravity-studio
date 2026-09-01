package com.yuzhiqiang.antigravity.i18n.sections

object UsageStringsEn : UsageStrings {
    override val navUsage: String = "Usage"
    override val usageSubtitle: String = "Multi-client AI Token trends, composition & cost estimation"
    override val usageStatsBadge: (conversations: Long, activeDays: Int) -> String = { conversations, activeDays ->
        "$conversations conversations · $activeDays active days"
    }
    override val usageTokensCount: (tokens: String) -> String = { tokens -> "$tokens Tokens" }
    override val usageCostValue: (cost: String) -> String = { cost -> "$$cost" }
    override val usageCostLowerBound: (cost: String) -> String = { cost -> "≥$$cost" }
    override val usageCostUnavailable: String = "—"
    override val usagePricingSource: (source: String) -> String = { source ->
        when (source.lowercase()) {
            "custom" -> "Custom"
            "external" -> "LiteLLM"
            "builtin" -> "Built-in"
            "mixed" -> "Mixed"
            else -> "Unmatched"
        }
    }

    override val usageTimeRange24h: String = "24h"
    override val usageTimeRange7d: String = "7d"
    override val usageTimeRange14d: String = "14d"
    override val usageTimeRange30d: String = "30d"
    override val usageTimeRangeToday: String = "Today"
    override val usageTimeRangeThisWeek: String = "Week"
    override val usageTimeRangeThisMonth: String = "Month"
    override val usageTimeRangeAllTime: String = "All Time"
    override val usageTimeRangeCustom: String = "Custom"

    override val usageSourceAll: String = "All Sources"
    override val usageSourceIde: String = "IDE"
    override val usageSourceApp: String = "App"
    override val usageSourceCli: String = "CLI"

    override val usageOverviewTitle: String = "Usage Overview"
    override val usageKpiCostTitle: String = "Estimated Cost"
    override val usageKpiCostSavings: (savings: String) -> String = { savings -> "Saved $$savings via caching" }
    override val usageKpiTotalTokensTitle: String = "Total Tokens"
    override val usageTotalInputTitle: String = "Input Usage (Prompt Input)"
    override val usageTotalOutputTitle: String = "Output Usage (Model Output)"
    override val usageCacheCardTitle: String = "Cache Performance (Hit)"
    override val usageSavedAmountLabel: String = "Cost Saved"
    override val usageUncachedInputLabel: String = "Uncached Input"
    override val usageCacheHitLabel: String = "Cache Hit"
    override val usageCacheWriteLabel: String = "Cache Write"
    override val usageGenerationLabel: String = "Generation"
    override val usageKpiTokensDetail: (input: String, output: String, reasoning: String) -> String =
        { input, output, reasoning ->
            "In $input · Out $output · Think $reasoning"
        }
    override val usageKpiCacheHitRatioTitle: String = "Cache Hit Ratio"
    override val usageKpiCacheReadDetail: (cacheRead: String) -> String =
        { cacheRead -> "Cache read $cacheRead Tokens" }
    override val usageKpiCallsTitle: String = "API Requests"
    override val usageKpiConversationsDetail: (count: Long) -> String = { count -> "Across $count conversations" }
    override val usageKpiActiveDays: (days: Int) -> String = { days -> "Active $days days" }
    override val usageKpiPricingWarning: String = "Some models have no matched price; their cost is excluded"
    override val usageCacheRate: (pct: String) -> String = { pct -> "$pct%" }
    override val usageCacheHitRateTitle: String = "Cache Hit Rate"

    override val usageTodaySummaryTitle: String = "Today's Usage"
    override val usageTodayActiveModels: (count: Long) -> String = { count -> "$count active models" }
    override val usageTodayConversations: (count: Long) -> String = { count -> "$count conversations" }
    override val usageTodayEmpty: String = "No token usage yet today"

    override val usageTrendChartTitle: String = "Daily Consumption Trend"
    override val usageHourlyTrendChartTitle: String = "Hourly Consumption Trend"
    override val usageTrendChartEmpty: String = "No token usage data in the selected time range"
    override val usageDailyTooltipHeader: (date: String, calls: Long) -> String =
        { date, calls -> "$date ($calls calls)" }
    override val usageDailyTooltipTotal: (tokens: String) -> String = { tokens -> "Total: $tokens Tokens" }
    override val usageDailyTooltipCost: (cost: String) -> String = { cost -> "Estimated: $$cost" }
    override val usageDailyTooltipCostLabel: (cost: String) -> String = { cost -> "Estimated: $cost" }
    override val usageCompositionTitle: String = "Token Composition"
    override val usageTokenPromptInput: String = "Prompt Input"
    override val usageTokenCacheTotal: String = "Cache Total"
    override val usageTokenCacheRead: String = "Cache Read"
    override val usageTokenCacheWrite: String = "Cache Write"
    override val usageTokenModelOutput: String = "Model Output"
    override val usageTokenThinking: String = "Thinking"
    override val usageTokenUnattributed: String = "Unattributed"

    override val usageActivityHeatmapTitle: String = "Activity & Contributions"
    override val usageActivityHeatmapTip: (date: String, tokens: String, calls: Long) -> String =
        { date, tokens, calls ->
            "$date: $tokens Tokens across $calls calls"
        }
    override val usageWeekdayPatternTitle: String = "Day-of-Week Pattern"
    override val usageWeekdayAvgCalls: (avg: Long) -> String = { avg -> "Average $avg calls / day" }
    override val usageWeekdayLabel: String = "Weekday"
    override val usageWeekendLabel: String = "Weekend"
    override val usagePeakLabel: String = "Peak"

    override val usageMonthlyBreakdownTitle: String = "Monthly Breakdown"
    override val usageMonthlyEmpty: String = "No monthly bill records available"

    override val usageTopModelsTitle: String = "Model Distribution"
    override val usageTopModelsEmpty: String = "No model usage recorded"
    override val usageModelCallsCount: (calls: Long) -> String = { calls -> "$calls calls" }
    override val usageModelTokensDetail: (input: String, output: String, cacheRead: String, cacheWrite: String, reasoning: String) -> String =
        { input, output, cacheRead, cacheWrite, reasoning ->
            "In $input · Cache read $cacheRead · Cache write $cacheWrite · Out $output · Think $reasoning"
        }
    override val usageModelUsageIncomplete: String =
        "Some usage dimensions are unavailable; cost is a lower-bound estimate"
    override val usageSourceBreakdownTitle: String = "Usage by Source"
    override val usageSourceTokensCost: (tokens: String, cost: String) -> String =
        { tokens, cost -> "$tokens Tokens · $$cost" }
    override val usageSourceTokensCostLabel: (tokens: String, cost: String) -> String =
        { tokens, cost -> "$tokens Tokens · $cost" }
    override val usageModelCount: (count: Int) -> String = { count -> "$count models" }

    override val usageTopConversationsTitle: String = "Top Conversations"
    override val usageTopConversationsEmpty: String = "No conversation usage recorded"
    override val usageConversationTokensDetail: (tokens: String, calls: Long) -> String = { tokens, calls ->
        "$tokens Tokens · $calls turns"
    }
    override val usageConversationCount: (count: Int) -> String = { count -> "$count conversations" }

    override val usageRefreshSuccessNotice: (convoCount: Long) -> String = { convoCount ->
        "Usage stats refreshed across $convoCount conversations"
    }
    override val usageCustomDateDialogTitle: String = "Custom Date Range"
    override val usageCustomDateStartLabel: String = "Start Date (YYYY-MM-DD)"
    override val usageCustomDateEndLabel: String = "End Date (YYYY-MM-DD)"
    override val usageCustomDateFollowNow: String = "Follow Now (continuously track up to current time)"
    override val usageCustomDateConfirm: String = "Apply Range"
    override val usageCustomDateNowValue: String = "Now (Follow Now)"
    override val usageCustomDatePlaceholder: String = "YYYY-MM-DD"
    override val usageCustomDateInvalid: String =
        "Enter a valid date range; the end date must not be before the start date."
    override val usageCustomDatePreset3Days: String = "Last 3 days"
    override val usageCustomDatePreset10Days: String = "Last 10 days"
    override val usageCustomDatePreset60Days: String = "Last 60 days"
    override val usageCustomDatePresetLastMonth: String = "Last month"

    override val settingsCustomPricingTitle: String = "Custom Pricing Source"
    override val settingsCustomPricingDesc: String =
        "Specify a local LiteLLM pricing JSON file path to override model cost estimation"
    override val settingsCustomPricingPlaceholder: String = "Local LiteLLM JSON pricing file path (optional)"
    override val settingsCustomPricingSelectFile: String = "Select JSON"
}
