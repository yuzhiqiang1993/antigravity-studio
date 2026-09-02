package com.yuzhiqiang.antigravity.i18n.sections

object UsageStringsZh : UsageStrings {
    override val navUsage: String = "用量统计"
    override val usageSubtitle: String = "多端 AI Token 消耗走势、构成拆解与费用折算"
    override val usageStatsBadge: (conversations: Long, activeDays: Int) -> String = { conversations, activeDays ->
        "$conversations 个会话 · $activeDays 活跃天"
    }
    override val usageTokensCount: (tokens: String) -> String = { tokens -> "$tokens Tokens" }
    override val usageCostValue: (cost: String) -> String = { cost -> "$$cost" }
    override val usageCostLowerBound: (cost: String) -> String = { cost -> "≥$$cost" }
    override val usageCostUnavailable: String = "—"
    override val usagePricingSource: (source: String) -> String = { source ->
        when (source.lowercase()) {
            "custom" -> "自定义"
            "external" -> "LiteLLM"
            "builtin" -> "内置"
            "mixed" -> "多来源"
            else -> "未匹配价格"
        }
    }

    override val usageTimeRangeToday: String = "今天"
    override val usageTimeRange24h: String = "1天"
    override val usageTimeRange7d: String = "7天"
    override val usageTimeRange14d: String = "14天"
    override val usageTimeRange30d: String = "30天"
    override val usageTimeRangeThisWeek: String = "本周"
    override val usageTimeRangeThisMonth: String = "本月"
    override val usageTimeRangeAllTime: String = "全部"
    override val usageTimeRangeCustom: String = "日期选择"

    override val usageSourceAll: String = "全部来源"
    override val usageSourceIde: String = "IDE"
    override val usageSourceApp: String = "App"
    override val usageSourceCli: String = "CLI"
    override val usageModelAll: String = "全部模型"
    override val usageModelFilterTitle: String = "模型筛选"

    override val usageAutoRefreshOff: String = "关闭"
    override val usageAutoRefreshSeconds: (seconds: Int) -> String = { seconds -> "${seconds}s" }

    override val usageSupportDateAndTime: String = "支持日期与时间"
    override val usageStartTimeLabel: String = "开始时间"
    override val usageEndTimeLabel: String = "结束时间"
    override val usageFollowNowLabel: String = "结束时间跟随当前时刻"
    override val usagePresetToday: String = "今天"
    override val usagePreset1Day: String = "1 天"
    override val usagePreset7Days: String = "7 天"
    override val usagePreset14Days: String = "14 天"
    override val usagePreset30Days: String = "30 天"

    override val usageOverviewTitle: String = "用量概览"
    override val usageKpiCostTitle: String = "预估总花费"
    override val usageKpiCostSavings: (savings: String) -> String = { savings -> "通过缓存已省 $$savings" }
    override val usageKpiTotalTokensTitle: String = "总 Token 消耗"
    override val usageTotalInputTitle: String = "输入用量 (Prompt Input)"
    override val usageTotalOutputTitle: String = "输出用量 (Model Output)"
    override val usageCacheCardTitle: String = "缓存命中 (Cache Hit)"
    override val usageSavedAmountLabel: String = "已省成本"
    override val usageUncachedInputLabel: String = "普通输入"
    override val usageCacheHitLabel: String = "缓存命中"
    override val usageCacheWriteLabel: String = "缓存创建"
    override val usageGenerationLabel: String = "内容生成"
    override val usageKpiTokensDetail: (input: String, output: String, reasoning: String) -> String =
        { input, output, reasoning ->
            "入 $input · 出 $output · 思 $reasoning"
        }
    override val usageKpiCacheHitRatioTitle: String = "缓存读取率"
    override val usageKpiCacheReadDetail: (cacheRead: String) -> String = { cacheRead -> "缓存读取 $cacheRead Tokens" }
    override val usageKpiCallsTitle: String = "API 调用次数"
    override val usageKpiConversationsDetail: (count: Long) -> String = { count -> "涉及 $count 个会话" }
    override val usageKpiActiveDays: (days: Int) -> String = { days -> "活跃 $days 天" }
    override val usageKpiPricingWarning: String = "部分模型未匹配价格，费用不含这些模型"
    override val usageCacheRate: (pct: String) -> String = { pct -> "$pct%" }
    override val usageCacheHitRateTitle: String = "缓存命中率"

    override val usageTodaySummaryTitle: String = "今日用量"
    override val usageTodayActiveModels: (count: Long) -> String = { count -> "$count 个活跃模型" }
    override val usageTodayConversations: (count: Long) -> String = { count -> "$count 个会话" }
    override val usageTodayEmpty: String = "今天还没有 Token 消耗"

    override val usageTrendChartTitle: String = "每日消耗走势"
    override val usageHourlyTrendChartTitle: String = "每小时消耗走势"
    override val usageTrendChartEmpty: String = "当前时间范围暂无 Token 消耗数据"
    override val usageDailyTooltipHeader: (date: String, calls: Long) -> String =
        { date, calls -> "$date（$calls 次调用）" }
    override val usageDailyTooltipTotal: (tokens: String) -> String = { tokens -> "总消耗：$tokens Tokens" }
    override val usageDailyTooltipCost: (cost: String) -> String = { cost -> "预估：$$cost" }
    override val usageDailyTooltipCostLabel: (cost: String) -> String = { cost -> "预估：$cost" }
    override val usageCompositionTitle: String = "Token 构成占比"
    override val usageTokenPromptInput: String = "Prompt 输入"
    override val usageTokenCacheTotal: String = "缓存总量"
    override val usageTokenCacheRead: String = "缓存读取"
    override val usageTokenCacheWrite: String = "缓存写入"
    override val usageTokenModelOutput: String = "模型输出"
    override val usageTokenThinking: String = "思考推理"
    override val usageTokenUnattributed: String = "未归因"

    override val usageActivityHeatmapTitle: String = "年度活跃度贡献"
    override val usageActivityHeatmapTip: (date: String, tokens: String, calls: Long) -> String =
        { date, tokens, calls ->
            "$date：消耗 $tokens Tokens，发起 $calls 次调用"
        }
    override val usageWeekdayPatternTitle: String = "按周使用规律"
    override val usageWeekdayAvgCalls: (avg: Long) -> String = { avg -> "平均每天 $avg 次调用" }
    override val usageWeekdayLabel: String = "工作日"
    override val usageWeekendLabel: String = "周末"
    override val usagePeakLabel: String = "峰值"

    override val usageMonthlyBreakdownTitle: String = "月度账单汇总"
    override val usageMonthlyEmpty: String = "暂无月度账单数据"

    override val usageTopModelsTitle: String = "热门模型使用排行"
    override val usageTopModelsEmpty: String = "暂无模型使用记录"
    override val usageModelCallsCount: (calls: Long) -> String = { calls -> "$calls 次调用" }
    override val usageModelTokensDetail: (input: String, output: String, cacheRead: String, cacheWrite: String, reasoning: String) -> String =
        { input, output, cacheRead, cacheWrite, reasoning ->
            "输入 $input · 缓存读取 $cacheRead · 缓存写入 $cacheWrite · 输出 $output · 思考 $reasoning"
        }
    override val usageModelUsageIncomplete: String = "部分用量维度缺失，费用仅为下界估算"
    override val usageSourceBreakdownTitle: String = "数据来源分布"
    override val usageSourceTokensCost: (tokens: String, cost: String) -> String =
        { tokens, cost -> "$tokens Tokens · $$cost" }
    override val usageSourceTokensCostLabel: (tokens: String, cost: String) -> String =
        { tokens, cost -> "$tokens Tokens · $cost" }
    override val usageModelCount: (count: Int) -> String = { count -> "$count 个模型" }

    override val usageTopConversationsTitle: String = "高消耗会话排行"
    override val usageTopConversationsEmpty: String = "暂无会话消耗记录"
    override val usageConversationTokensDetail: (tokens: String, calls: Long) -> String = { tokens, calls ->
        "共 $tokens Tokens · $calls 轮交互"
    }
    override val usageConversationCount: (count: Int) -> String = { count -> "$count 个会话" }

    override val usageRefreshSuccessNotice: (convoCount: Long) -> String = { convoCount ->
        "用量数据已更新，共统计 $convoCount 个会话"
    }
    override val usageCustomDateDialogTitle: String = "自定义日期筛选区间"
    override val usageCustomDateStartLabel: String = "起始日期 (YYYY-MM-DD)"
    override val usageCustomDateEndLabel: String = "结束日期 (YYYY-MM-DD)"
    override val usageCustomDateFollowNow: String = "跟随当前时间（统计截止至此刻）"
    override val usageCustomDateConfirm: String = "应用筛选"
    override val usageCustomDateNowValue: String = "现在（跟随当前时间）"
    override val usageCustomDatePlaceholder: String = "YYYY-MM-DD"
    override val usageCustomDateInvalid: String = "请输入有效日期范围，结束日期不能早于起始日期。"
    override val usageCustomDatePreset3Days: String = "近 3 天"
    override val usageCustomDatePreset10Days: String = "近 10 天"
    override val usageCustomDatePreset60Days: String = "近 60 天"
    override val usageCustomDatePresetLastMonth: String = "上个月"

    override val settingsCustomPricingTitle: String = "自定义费率来源"
    override val settingsCustomPricingDesc: String =
        "指定本地 LiteLLM 费率 JSON 文件或自定义覆盖路径，用于用量大盘费用折算"
    override val settingsCustomPricingPlaceholder: String = "本地 LiteLLM JSON 价格表物理路径（可选）"
    override val settingsCustomPricingSelectFile: String = "选择 JSON"
}
