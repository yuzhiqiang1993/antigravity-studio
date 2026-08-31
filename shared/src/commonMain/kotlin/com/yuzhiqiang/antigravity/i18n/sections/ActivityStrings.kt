package com.yuzhiqiang.antigravity.i18n.sections

interface ActivityStrings {
    // Activity Screen & Detail Dialog
    val activityTitle: String
    val activitySubtitle: String
    val activityFilterAll: String
    val activityFilterFailed: String
    val activityClear: String
    val activityEmpty: String
    val activityEmptyDesc: String
    val activityNoMatchingLogs: String
    val activityNoMatchingDesc: String
    val activityPassthrough: String
    val activityRouted: String
    val activitySearchPlaceholder: String
    val activityRecent: String
    val activityTotal: String
    val activityFailedTotal: String
    val activityAverage: String
    val activityCacheHitRate: String
    val activityModelLatencyDialogTitle: String
    val activityModelLatencyDialogSubtitle: String
    val activityModelLatencyTotalSamples: String
    val activityModelLatencyActiveModels: String
    val activityModelLatencyOverallAvg: String
    val activityModelLatencyOverallAvgDuration: String
    val activityModelLatencyOverallAvgTps: String
    val activityModelLatencyEmpty: String
    val activityModelLatencyEmptyDesc: String
    val activityModelLatencyColModel: String
    val activityModelLatencyColSamples: String
    val activityModelLatencyColAvgTtft: String
    val activityModelLatencyColAvgTps: String
    val activityModelLatencyColAvgDuration: String
    val activityModelLatencyRange: String
    val activityModelLatencyMedian: String
    fun activityModelLatencyCallsCount(count: Int): String
    fun activityModelLatencySampleCount(count: Int): String
    fun activityModelLatencyCompletedCalls(completed: Int, total: Int): String
    fun activityModelLatencyStallBadge(count: Int): String
    val activityModelLatencyP95Gap: String
    val activityFirstTokenLabel: String
    fun activityStallsCount(count: Int): String
    val activityPending: String
    val activityProcessing: String
    val activityAllTags: String
    val activityTagFilterTitle: String
    val activitySelectAll: String
    val activityClearFilter: String
    fun activitySelectedTagsCount(count: Int): String
    val activityFilterClients: String
    val activityFilterEndpoints: String
    val activityFilterRoutes: String
    val activityFilterStatuses: String
    val activityFilterAllOption: String
    val activityFilterSuccess: String
    val activityFilterFailedStatus: String
    val activityFilterRetried: String
    val activityFilterClientIde: String
    val activityFilterClientCli: String
    val activityFilterClientApp: String
    val activityFilterClientPlugin: String
    val activityFilterOtherClient: String
    val activityFilterEndpointSearch: String
    val activityFilterNoEndpoints: String
    val activityFilterNoRoutes: String
    val activityFilterResetAll: String
    fun activityFilterMatches(shown: Int, total: Int): String
    fun activityFilterSelectedDimension(label: String, count: Int): String
    val activityTokenInput: String
    val activityTokenOutput: String
    val activityTokenCache: String
    val activityTokenTotal: String
    val activityDetailCacheHitRate: String
    val activityAutoScroll: String
    val activityInMemory: String
    val activityHealthy: String
    val activityHasErrors: String
    val activityUnknownProvider: String
    val activityDetailTitle: String
    val activityDetailRouteSection: String
    val activityDetailMethod: String
    val activityDetailPath: String
    val activityDetailDuration: String
    val activityDetailQueueWait: String
    val activityDetailFirstByte: String
    val activityDetailFirstToken: String
    val activityDetailSpeedSection: String
    val activityDetailTps: String
    val activityDetailTpot: String
    val activityDetailGenerationDuration: String
    val activityDetailLastToken: String
    val activityDetailMaxChunkGap: String
    val activityDetailStallCount: String
    val activityDetailStallDuration: String
    val activityDetailTimestamp: String
    val activityDetailClientSource: String
    val activityClientIde: String
    val activityClientApp: String
    val activityClientCli: String
    val activityDetailRouteMode: String
    val activityDetailPassthroughMode: String
    val activityDetailForwardMode: String
    val activityDetailTargetModel: String
    val activityDetailRequestedModel: String
    val activityDetailProvider: String
    val activityDetailTokenSection: String
    val activityDetailPromptTokens: String
    val activityDetailCompletionTokens: String
    val activityDetailTotalTokens: String
    val activityDetailReasoningTokens: String
    val activityDetailCacheReadTokens: String
    val activityDetailCacheWriteTokens: String
    val activityDetailErrorSection: String
    val activityDetailErrorSource: String
    val activityErrorSourceUpstreamResponse: String
    val activityErrorSourceUpstreamTransport: String
    val activityErrorSourceStudioAdapter: String
    val activityErrorSourceStudioProxy: String
    val activityDetailCopyJson: String
    val activityDetailCopyError: String
    val activityDetailCopiedError: String
    val activityRetryCount: String
    fun activityRetryBadge(count: Int): String
    val activityDetailDebugSection: String
    val activityDetailRequestHeaders: String
    val activityDetailRequestBody: String
    val activityDetailResponseHeaders: String
    val activityDetailResponseBody: String
    val activityDetailEmptyPayload: String
    val activityDetailFormatJson: String
    val activityDetailRawText: String
    val activityDetailCopyHeaders: String
    val activityDetailCopyBody: String
}

object ActivityStringsZh : ActivityStrings {
    // Activity Screen & Detail Dialog
    override val activityTitle = "调用日志"
    override val activitySubtitle = "查看请求状态、路由来源与响应耗时"
    override val activityFilterAll = "全部请求"
    override val activityFilterFailed = "仅看失败"
    override val activityClear = "清空日志"
    override val activityEmpty = "暂无请求日志"
    override val activityEmptyDesc = "当 Antigravity 发起模型代理调用时，此处将实时展示调用明细"
    override val activityNoMatchingLogs = "未找到匹配日志"
    override val activityNoMatchingDesc = "尝试输入其他关键词或清除筛选条件"
    override val activityPassthrough = "官方直连"
    override val activityRouted = "三方路由"
    override val activitySearchPlaceholder = "搜索接口、模型、服务商或错误..."
    override val activityRecent = "最近日志"
    override val activityTotal = "总请求量"
    override val activityFailedTotal = "异常请求"
    override val activityAverage = "首字平均耗时"
    override val activityCacheHitRate = "缓存命中率"
    override val activityModelLatencyDialogTitle = "模型速度与耗时统计"
    override val activityModelLatencyDialogSubtitle = "汇总各模型的首字响应速度、输出速率与总耗时"
    override val activityModelLatencyTotalSamples = "总请求样本"
    override val activityModelLatencyActiveModels = "模型数量"
    override val activityModelLatencyOverallAvg = "首字均值"
    override val activityModelLatencyOverallAvgDuration = "总耗时均值"
    override val activityModelLatencyOverallAvgTps = "速率均值"
    override val activityModelLatencyEmpty = "暂无模型耗时数据"
    override val activityModelLatencyEmptyDesc = "发起模型对话后，首字响应、生成速率与总耗时将自动汇总展示于此处"
    override val activityModelLatencyColModel = "模型名称"
    override val activityModelLatencyColSamples = "样本数"
    override val activityModelLatencyColAvgTtft = "首字响应 (TTFT)"
    override val activityModelLatencyColAvgTps = "输出速率 (TPS)"
    override val activityModelLatencyColAvgDuration = "会话总耗时"
    override val activityModelLatencyRange = "波动区间"
    override val activityModelLatencyMedian = "中位数"
    override fun activityModelLatencyCallsCount(count: Int) = "$count 次调用"
    override fun activityModelLatencySampleCount(count: Int) = "$count 次首字"
    override fun activityModelLatencyCompletedCalls(completed: Int, total: Int) = "$completed / $total 次完成"
    override fun activityModelLatencyStallBadge(count: Int) = "卡顿 $count 次"
    override val activityModelLatencyP95Gap = "长尾间隔"
    override val activityFirstTokenLabel = "首字耗时"
    override fun activityStallsCount(count: Int) = "$count 次卡顿"
    override val activityPending = "请求中"
    override val activityProcessing = "处理中..."
    override val activityAllTags = "日志筛选"
    override val activityTagFilterTitle = "日志筛选"
    override val activitySelectAll = "全选"
    override val activityClearFilter = "清空"
    override fun activitySelectedTagsCount(count: Int) = "已选 $count 项"
    override val activityFilterClients = "客户端"
    override val activityFilterEndpoints = "请求接口"
    override val activityFilterRoutes = "路由 / 服务商"
    override val activityFilterStatuses = "请求状态"
    override val activityFilterAllOption = "全部"
    override val activityFilterSuccess = "成功"
    override val activityFilterFailedStatus = "失败"
    override val activityFilterRetried = "有重试"
    override val activityFilterClientIde = "IDE"
    override val activityFilterClientCli = "CLI"
    override val activityFilterClientApp = "App"
    override val activityFilterClientPlugin = "插件"
    override val activityFilterOtherClient = "其他"
    override val activityFilterEndpointSearch = "搜索接口路径..."
    override val activityFilterNoEndpoints = "暂无匹配接口"
    override val activityFilterNoRoutes = "暂无路由记录"
    override val activityFilterResetAll = "重置全部"
    override fun activityFilterMatches(shown: Int, total: Int) = "匹配 $shown / $total 条"
    override fun activityFilterSelectedDimension(label: String, count: Int) = "$label $count"
    override val activityTokenInput = "输入"
    override val activityTokenOutput = "输出"
    override val activityTokenCache = "缓存"
    override val activityTokenTotal = "总计"
    override val activityDetailCacheHitRate = "缓存命中率"
    override val activityAutoScroll = "自动滚动"
    override val activityInMemory = "内存日志"
    override val activityHealthy = "运行正常"
    override val activityHasErrors = "存在错误"
    override val activityUnknownProvider = "未知服务商"
    override val activityDetailTitle = "请求调用详情"
    override val activityDetailRouteSection = "路由与调用信息"
    override val activityDetailMethod = "请求方法"
    override val activityDetailPath = "完整请求路径"
    override val activityDetailDuration = "总响应耗时"
    override val activityDetailQueueWait = "Studio 排队耗时"
    override val activityDetailFirstByte = "上游首包耗时 (TTFB)"
    override val activityDetailFirstToken = "首字响应耗时 (TTFT)"
    override val activityDetailSpeedSection = "速度与流式指标"
    override val activityDetailTps = "输出速率 (TPS)"
    override val activityDetailTpot = "单 Token 耗时 (TPOT)"
    override val activityDetailGenerationDuration = "纯生成耗时"
    override val activityDetailLastToken = "末字到达耗时"
    override val activityDetailMaxChunkGap = "流式最大等待间隔"
    override val activityDetailStallCount = "流式卡顿次数 (≥2s)"
    override val activityDetailStallDuration = "累计卡顿时长"
    override val activityDetailTimestamp = "请求发起时间"
    override val activityDetailClientSource = "请求客户端"
    override val activityClientIde = "Antigravity IDE"
    override val activityClientApp = "Antigravity App"
    override val activityClientCli = "Antigravity CLI"
    override val activityDetailRouteMode = "路由模式"
    override val activityDetailPassthroughMode = "官方直连透传"
    override val activityDetailForwardMode = "三方服务商转发 (BYOK)"
    override val activityDetailTargetModel = "目标匹配模型"
    override val activityDetailRequestedModel = "原始请求模型"
    override val activityDetailProvider = "接入服务商"
    override val activityDetailTokenSection = "Token 消耗明细"
    override val activityDetailPromptTokens = "输入 (Prompt)"
    override val activityDetailCompletionTokens = "输出 (Completion)"
    override val activityDetailTotalTokens = "总计 (Total)"
    override val activityDetailReasoningTokens = "推理 (Thinking)"
    override val activityDetailCacheReadTokens = "缓存读取 (Read)"
    override val activityDetailCacheWriteTokens = "缓存写入 (Write)"
    override val activityDetailErrorSection = "错误详情与服务端原始响应"
    override val activityDetailErrorSource = "错误来源"
    override val activityErrorSourceUpstreamResponse = "上游服务响应"
    override val activityErrorSourceUpstreamTransport = "上游网络传输"
    override val activityErrorSourceStudioAdapter = "Studio 协议适配"
    override val activityErrorSourceStudioProxy = "Studio 代理内部"
    override val activityDetailCopyJson = "复制完整 JSON"
    override val activityDetailCopyError = "复制错误信息"
    override val activityDetailCopiedError = "已复制错误信息"
    override val activityRetryCount = "重试次数"
    override fun activityRetryBadge(count: Int) = "重试 $count 次"
    override val activityDetailDebugSection = "调试明细 (Debug)"
    override val activityDetailRequestHeaders = "请求头 (Headers)"
    override val activityDetailRequestBody = "请求数据 (Body)"
    override val activityDetailResponseHeaders = "响应头 (Headers)"
    override val activityDetailResponseBody = "响应数据 (Body)"
    override val activityDetailEmptyPayload = "（无数据）"
    override val activityDetailFormatJson = "格式化 JSON"
    override val activityDetailRawText = "原始报文"
    override val activityDetailCopyHeaders = "复制请求头"
    override val activityDetailCopyBody = "复制数据"
}

object ActivityStringsEn : ActivityStrings {
    // Activity Screen & Detail Dialog
    override val activityTitle = "Activity Logs"
    override val activitySubtitle = "Inspect request status, route source and response latency"
    override val activityFilterAll = "All Requests"
    override val activityFilterFailed = "Failed Only"
    override val activityClear = "Clear Logs"
    override val activityEmpty = "No activity recorded"
    override val activityEmptyDesc =
        "When Antigravity routes requests through the proxy, detailed logs will appear here in real-time"
    override val activityNoMatchingLogs = "No matching logs found"
    override val activityNoMatchingDesc = "Try searching with different keywords or clearing active filters"
    override val activityPassthrough = "Official Passthrough"
    override val activityRouted = "Custom Route"
    override val activitySearchPlaceholder = "Search endpoint, model, provider, or error"
    override val activityRecent = "Recent logs"
    override val activityTotal = "Total requests"
    override val activityFailedTotal = "Failed requests"
    override val activityAverage = "Avg First Token"
    override val activityCacheHitRate = "Cache Hit Rate"
    override val activityModelLatencyDialogTitle = "Model Speed & Latency"
    override val activityModelLatencyDialogSubtitle = "Overview of first token response, generation speed, and total duration across models"
    override val activityModelLatencyTotalSamples = "Total Samples"
    override val activityModelLatencyActiveModels = "Models"
    override val activityModelLatencyOverallAvg = "Avg First Token"
    override val activityModelLatencyOverallAvgDuration = "Avg Duration"
    override val activityModelLatencyOverallAvgTps = "Avg Speed"
    override val activityModelLatencyEmpty = "No model latency recorded"
    override val activityModelLatencyEmptyDesc = "First token response, speed, and session duration will be summarized here after model requests"
    override val activityModelLatencyColModel = "Model"
    override val activityModelLatencyColSamples = "Samples"
    override val activityModelLatencyColAvgTtft = "First Token (TTFT)"
    override val activityModelLatencyColAvgTps = "Speed (TPS)"
    override val activityModelLatencyColAvgDuration = "Total Duration"
    override val activityModelLatencyRange = "Range"
    override val activityModelLatencyMedian = "Median"
    override fun activityModelLatencyCallsCount(count: Int) = "$count calls"
    override fun activityModelLatencySampleCount(count: Int) = "$count TTFT"
    override fun activityModelLatencyCompletedCalls(completed: Int, total: Int) = "$completed / $total completed"
    override fun activityModelLatencyStallBadge(count: Int) = "$count stalls"
    override val activityModelLatencyP95Gap = "Tail Gap"
    override val activityFirstTokenLabel = "First Token"
    override fun activityStallsCount(count: Int) = "$count stalls"
    override val activityPending = "Processing"
    override val activityProcessing = "In progress..."
    override val activityAllTags = "Log Filter"
    override val activityTagFilterTitle = "Log Filter"
    override val activitySelectAll = "Select All"
    override val activityClearFilter = "Reset"
    override fun activitySelectedTagsCount(count: Int) = "$count selected"
    override val activityFilterClients = "Clients"
    override val activityFilterEndpoints = "Endpoints"
    override val activityFilterRoutes = "Routes / Providers"
    override val activityFilterStatuses = "Request Status"
    override val activityFilterAllOption = "All"
    override val activityFilterSuccess = "Successful"
    override val activityFilterFailedStatus = "Failed"
    override val activityFilterRetried = "Retried"
    override val activityFilterClientIde = "IDE"
    override val activityFilterClientCli = "CLI"
    override val activityFilterClientApp = "App"
    override val activityFilterClientPlugin = "Plugin"
    override val activityFilterOtherClient = "Other"
    override val activityFilterEndpointSearch = "Search endpoint path"
    override val activityFilterNoEndpoints = "No matching endpoints"
    override val activityFilterNoRoutes = "No route records"
    override val activityFilterResetAll = "Reset All"
    override fun activityFilterMatches(shown: Int, total: Int) = "$shown of $total matching"
    override fun activityFilterSelectedDimension(label: String, count: Int) = "$label: $count"
    override val activityTokenInput = "Input"
    override val activityTokenOutput = "Output"
    override val activityTokenCache = "Cache"
    override val activityTokenTotal = "Total"
    override val activityDetailCacheHitRate = "Cache Hit Rate"
    override val activityAutoScroll = "Auto Scroll"
    override val activityInMemory = "In-memory log"
    override val activityHealthy = "Healthy"
    override val activityHasErrors = "Errors found"
    override val activityUnknownProvider = "Unknown Provider"
    override val activityDetailTitle = "Request Activity Details"
    override val activityDetailRouteSection = "Route & Invocation Details"
    override val activityDetailMethod = "HTTP Method"
    override val activityDetailPath = "Full Request Path"
    override val activityDetailDuration = "Total Response Latency"
    override val activityDetailQueueWait = "Studio Queue Wait"
    override val activityDetailFirstByte = "Time to First Byte (TTFB)"
    override val activityDetailFirstToken = "Time to First Token (TTFT)"
    override val activityDetailSpeedSection = "Speed & Streaming Metrics"
    override val activityDetailTps = "Output Speed (TPS)"
    override val activityDetailTpot = "Time per Token (TPOT)"
    override val activityDetailGenerationDuration = "Generation Duration"
    override val activityDetailLastToken = "Time to Last Token"
    override val activityDetailMaxChunkGap = "Max Stream Chunk Gap"
    override val activityDetailStallCount = "Stream Stalls (≥2s)"
    override val activityDetailStallDuration = "Total Stall Duration"
    override val activityDetailTimestamp = "Request Start Time"
    override val activityDetailClientSource = "Client Source"
    override val activityClientIde = "Antigravity IDE"
    override val activityClientApp = "Antigravity App"
    override val activityClientCli = "Antigravity CLI"
    override val activityDetailRouteMode = "Route Mode"
    override val activityDetailPassthroughMode = "Official Direct (Cloud Code Passthrough)"
    override val activityDetailForwardMode = "Custom Forward (BYOK Forward)"
    override val activityDetailTargetModel = "Target Model"
    override val activityDetailRequestedModel = "Requested Model"
    override val activityDetailProvider = "Upstream Provider"
    override val activityDetailTokenSection = "Token Usage Metrics (Unmasked)"
    override val activityDetailPromptTokens = "Prompt Tokens"
    override val activityDetailCompletionTokens = "Completion Tokens"
    override val activityDetailTotalTokens = "Total Tokens"
    override val activityDetailReasoningTokens = "Thinking Tokens"
    override val activityDetailCacheReadTokens = "Cache Read Tokens"
    override val activityDetailCacheWriteTokens = "Cache Write Tokens"
    override val activityDetailErrorSection = "Error Details & Upstream Response"
    override val activityDetailErrorSource = "Error Source"
    override val activityErrorSourceUpstreamResponse = "Upstream provider response"
    override val activityErrorSourceUpstreamTransport = "Upstream network transport"
    override val activityErrorSourceStudioAdapter = "Studio protocol adapter"
    override val activityErrorSourceStudioProxy = "Studio proxy internals"
    override val activityDetailCopyJson = "Copy Full JSON"
    override val activityDetailCopyError = "Copy Error Message"
    override val activityDetailCopiedError = "Error message copied to clipboard"
    override val activityRetryCount = "Retry Count"
    override fun activityRetryBadge(count: Int) = "Retry ×$count"
    override val activityDetailDebugSection = "Debug Details"
    override val activityDetailRequestHeaders = "Request Headers"
    override val activityDetailRequestBody = "Request Body"
    override val activityDetailResponseHeaders = "Response Headers"
    override val activityDetailResponseBody = "Response Body"
    override val activityDetailEmptyPayload = "(Empty)"
    override val activityDetailFormatJson = "Format JSON"
    override val activityDetailRawText = "Raw Text"
    override val activityDetailCopyHeaders = "Copy Headers"
    override val activityDetailCopyBody = "Copy Body"
}
