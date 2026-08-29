package com.yuzhiqiang.antigravity.i18n.sections

interface AdvancedStrings {
    val accountsSwitchDialogTitle: String
    val accountsSwitchSelectTargetTitle: String
    val accountsSwitchStatusIdeNotInstalled: String
    val accountsSwitchStatusIdeRunning: String
    val accountsSwitchStatusIdeStopped: String
    val accountsSwitchSharedTitleCli: String
    val accountsSwitchSharedTitleSystem: String
    val accountsSwitchStatusAppRunning: String
    val accountsSwitchStatusAppStopped: String
    val accountsSwitchStatusCliOnly: String
    val accountsSwitchStatusNone: String
    val accountsSwitchRememberChoice: String
    val accountsSwitching: String
    val accountsSwitchConfirmRestart: String
    val accountsSwitchConfirmLaunch: String
    val accountsSwitchConfirm: String
    val accountsSwitchTargetIde: String
    val accountsSwitchTargetAppCli: String

    // Smart Switch Dialog & Strategy
    val smartSwitchTitle: String
    val smartSwitchSubtitle: String
    val smartSwitchEnableTitle: String
    val smartSwitchEnableDesc: String
    val smartSwitchThresholdLabel: String
    val smartSwitchStrategyLabel: String
    val smartSwitchStrategyHighestQuota: String
    val smartSwitchStrategyRoundRobin: String
    val smartSwitchCooldownLabel: String
    fun smartSwitchSeconds(seconds: Int): String
    val smartSwitchProtectGenerationTitle: String
    val smartSwitchProtectGenerationDesc: String
    val smartSwitchInterruptTip: String
    val smartSwitchReasonDisabled: String
    fun smartSwitchReasonCooldown(remainingSec: Long): String
    val smartSwitchReasonWorkflowLocked: String
    val smartSwitchReasonNoBackupAccounts: String
    val smartSwitchReasonNoEligibleCandidate: String
    val smartSwitchTriggerReason429: String
    val smartSwitchTriggerReasonLowQuota: String
    fun smartSwitchReasonTaskRunning(trigger: String): String
    fun smartSwitchReasonSuggestSwitch(trigger: String, email: String): String
    val hotSwitchTaskAlreadyRunning: String
    val hotSwitchIdeNotApplied: String
    val hotSwitchNotAllTargetsApplied: String

    // Quota Auto-Refresh Dialog
    val quotaRefreshTitle: String
    val quotaRefreshSubtitle: String
    val quotaRefreshActiveIntervalTitle: String
    val quotaRefreshBackgroundIntervalTitle: String
    val quotaRefreshCustomOption: String
    val quotaRefreshPlaceholderActive: String
    val quotaRefreshPlaceholderBackground: String
    val quotaRefreshActiveHint: String
    val quotaRefreshDefaultSummary: String
    val quotaRefreshResetDefault: String
    val quotaRefreshUnitSecond: String
    val quotaRefreshUnitMinute: String
    val quotaRefreshUnitHour: String
    fun quotaRefreshPresetRecommended(label: String): String
    val quotaRefreshInputInvalid: String
    fun quotaRefreshMinActiveSeconds(sec: Int): String
    fun quotaRefreshMinBackgroundMinutes(min: Int): String
    fun quotaRefreshMaxHours(hr: Int): String

    // Account Cards & Screen
    fun accountsLastSyncTime(time: String): String
    val accountsSyncToOtherHost: String
    val accountsSetAsActiveTooltip: String
    val accountsRefreshThisTooltip: String
    val accountsRefreshingTooltip: String
    val accountsDeleteThisTooltip: String
    fun accountsModelFamily(label: String): String
    val accountsQuotaFiveHour: String
    val accountsQuotaWeekly: String
    val accountsFetchingQuota: String
    val accountsNoQuotaData: String
    val accountsQuotaResetSoon: String
    val accountsQuotaFull: String
    val accountsQuotaResetInSuffix: String

    // Time & Countdown formatting
    fun formatCountdownDaysHours(days: Long, hours: Long): String
    fun formatCountdownDays(days: Long): String
    fun formatCountdownHoursMinutes(hours: Long, minutes: Long): String
    fun formatCountdownHours(hours: Long): String
    fun formatCountdownMinutes(minutes: Long): String
    val formatCountdownLessThanMinute: String

    // Quota natural language descriptions
    val quotaDescFiveHourFull: String
    val quotaDescWeeklyFull: String
    val quotaDescGeneralFull: String
    fun quotaDescFiveHourResetting(timeStr: String): String
    fun quotaDescWeeklyResetting(timeStr: String): String
    fun quotaDescGeneralResetting(timeStr: String): String

    // Quota Window
    val quotaWindowFiveHour: String
    val quotaWindowWeekly: String
    val quotaWindowDaily: String
    val quotaWindowGeneral: String
    val accountsSearchPlaceholder: String
    val accountsSortByQuotaDesc: String
    val accountsSortByQuotaDescActive: String
    val accountsSortChipLabel: String
    val accountsAddAccountTooltip: String
    val accountsRefreshAllTooltip: String
    fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int): String
    val accountsPrivacyHideTooltip: String
    val accountsPrivacyShowTooltip: String
    val accountsExportTooltip: String
    val accountsExportCopyToClipboard: String
    val accountsExportSaveJson: String
    fun accountsExportCopiedNotice(count: Int): String
    fun accountsExportSuccessNotice(count: Int, filename: String): String
    fun accountsExportFailedNotice(error: String): String
    val accountsExportDialogTitle: String
    val accountsSmartSwitchTooltip: String
    fun accountsSearchNoMatch(query: String): String
    val accountsDeleteConfirmTitle: String
    fun accountsDeleteConfirmMsg(email: String): String
    val accountsDeleteConfirmBtn: String

    // Overview Screen & Hero Card
    val overviewTodayRequests: String
    fun overviewRequestsUnit(count: Long): String
    val overviewServiceUptime: String
    val overviewAvgLatency: String
    val overviewRouteUpstreamStatus: String
    val overviewOfficialDirect: String
    fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int): String
    fun overviewSourceInUse(sources: String): String
    val overviewActiveAccountBadge: String
    val overviewSyncingQuotas: String

    // Notices & ViewModel Messages
    val noticeAuthLinkCopied: String
    val noticeAuthLinkCopiedBrowser: String
    val noticeSwitchAlreadyRunning: String
    fun noticeSwitchResult(summary: String): String
    fun noticeSwitchFailed(error: String): String
    fun noticeAccountNotFound(idOrEmail: String): String
    val noticeSmartSwitchEnabled: String
    val noticeSmartSwitchDisabled: String
    val noticeQuotaAutoRefreshEnabled: String
    val noticeQuotaAutoRefreshDisabled: String
    val noticeAccountRemoved: String
    val noticeTokenRefreshed: String
    fun noticeTokenRefreshFailed(error: String): String
    val noticeRemarkUpdated: String
    fun noticeCleanAccountsSuccess(count: Int): String
    fun noticeCleanAccountsFailed(error: String): String
    fun noticeBatchImportSuccess(count: Int): String
    fun noticeBatchImportPartial(successCount: Int, failedCount: Int): String
    fun noticeBatchImportFailedAll(failedCount: Int): String
    val noticeQuotasUpdatedAll: String
    fun noticeQuotasUpdateFailedAll(error: String): String
    val noticeQuotaRefreshedSingle: String
    fun noticeQuotaRefreshFailedSingle(error: String): String
    fun switchStatusNotAvailable(target: String): String
    fun switchStatusConfigured(target: String): String
    fun switchStatusConfirmed(target: String): String
    fun switchStatusPendingRestart(target: String): String
    fun switchStatusFailed(target: String): String

    // Add Account Dialog & Import
    val accountsAddTabOAuth: String
    val accountsAddTabTokenImport: String
    val accountsAddSelectJsonFileTitle: String
    val accountsAddInvalidAuthCode: String
    val accountsAddReopenBrowser: String
    val accountsAddOpenBrowser: String
    val accountsAddCopyAuthUrl: String
    val accountsAddCancelAuth: String
    val accountsAddFallbackManualHint: String
    val accountsAddFallbackManualPlaceholder: String
    val accountsAddSubmit: String
    val accountsAddTokenBatchDesc: String
    val accountsAddImportJsonFile: String
    val accountsAddPasteClipboard: String
    val accountsAddTokenPlaceholder: String
    fun accountsAddRecognizedCount(count: Int, preview: String): String
    val accountsAddUnrecognizedTokens: String
    val accountsAddImporting: String
    fun accountsAddConfirmImport(count: Int): String

    // Other UI Components & Relative Times
    val settingsAccountAndAppCardTitle: String
    fun accountsCountSummary(count: Int): String
    val accountsFiveHourLabel: String
    val accountsWeeklyLabel: String
    fun accountsResetInCountdown(countdown: String): String
    val timeNeverRefreshed: String
    val timeJustNow: String
    fun timeMinutesAgo(min: Long): String
    fun timeHoursAgo(hours: Long): String
    fun timeDaysAgo(days: Long): String

    // Local Proxy Server Notices
    fun proxyStarted(port: Int): String
    fun proxyStartFailed(error: String): String
    val proxyStopped: String
    fun proxyRestarted(port: Int): String
    fun proxyRestartFailed(error: String): String
    fun proxyTestSuccess(latencyMs: Long): String
    fun proxyTestFailed(error: String): String

}

object AdvancedStringsZh : AdvancedStrings {
    override val accountsSwitchDialogTitle = "切换账号"
    override val accountsSwitchSelectTargetTitle = "选择目标应用"
    override val accountsSwitchStatusIdeNotInstalled = "未安装 · 本机未检测到 IDE"
    override val accountsSwitchStatusIdeRunning = "运行中 · 选中后将安全退出并重启"
    override val accountsSwitchStatusIdeStopped = "未运行 · 选中后将写入凭据并直接启动"
    override val accountsSwitchSharedTitleCli = "Antigravity CLI (共享凭据)"
    override val accountsSwitchSharedTitleSystem = "系统共享凭据 (CLI / 本地)"
    override val accountsSwitchStatusAppRunning = "App 运行中 · 选中后将安全退出并重启"
    override val accountsSwitchStatusAppStopped = "App 未运行 · 选中后将写入凭据并直接启动"
    override val accountsSwitchStatusCliOnly = "未安装 App · 将同步 ~/.gemini/ 凭据供 CLI 使用"
    override val accountsSwitchStatusNone = "未检测到客户端 · 仅更新 Studio 活跃账号与共享凭据"
    override val accountsSwitchRememberChoice = "记住本次选择"
    override val accountsSwitching = "正在切换..."
    override val accountsSwitchConfirmRestart = "确定并重启"
    override val accountsSwitchConfirmLaunch = "确定并启动"
    override val accountsSwitchConfirm = "确定切换"
    override val accountsSwitchTargetIde = "Antigravity IDE"
    override val accountsSwitchTargetAppCli = "Antigravity App & CLI"

    // Smart Switch Dialog & Strategy
    override val smartSwitchTitle = "自动智能切号"
    override val smartSwitchSubtitle = "当配额耗尽或遇到 429 限流时，自动无缝切换至最佳备用账号"
    override val smartSwitchEnableTitle = "启用自动智能切号"
    override val smartSwitchEnableDesc = "遇到 429 限流或配额不足时自动切换账号"
    override val smartSwitchThresholdLabel = "触发切号的配额阈值"
    override val smartSwitchStrategyLabel = "备用账号选择策略"
    override val smartSwitchStrategyHighestQuota = "剩余配额最多优先 (推荐)"
    override val smartSwitchStrategyRoundRobin = "循环轮询"
    override val smartSwitchCooldownLabel = "两次切号最小间隔"
    override fun smartSwitchSeconds(seconds: Int) = "$seconds 秒"
    override val smartSwitchProtectGenerationTitle = "生成中防打断保护"
    override val smartSwitchProtectGenerationDesc = "在模型流式回复或智能体执行期间，暂缓自动切号"
    override val smartSwitchInterruptTip = "保护说明：模型流式生成或智能体正在执行时，暂缓切号以避免请求中断"
    override val smartSwitchReasonDisabled = "智能切号未启用"
    override fun smartSwitchReasonCooldown(remainingSec: Long) = "处于切号冷却期 (${remainingSec}s 剩余)"
    override val smartSwitchReasonWorkflowLocked = "当前工作流处于锁定保护状态"
    override val smartSwitchReasonNoBackupAccounts = "无可用备用账号"
    override val smartSwitchReasonNoEligibleCandidate = "未找到满足配额要求的备用账号"
    override val smartSwitchTriggerReason429 = "遭遇 429 配额耗尽"
    override val smartSwitchTriggerReasonLowQuota = "配额低于阈值"
    override fun smartSwitchReasonTaskRunning(trigger: String) = "$trigger，但当前已有切号任务正在执行"
    override fun smartSwitchReasonSuggestSwitch(trigger: String, email: String) =
        "$trigger，建议切换至 $email；请在账号管理中确认应用重启"

    override val hotSwitchTaskAlreadyRunning = "已有账号切换任务正在执行，请稍后再试"
    override val hotSwitchIdeNotApplied = "IDE 账号尚未生效"
    override val hotSwitchNotAllTargetsApplied = "账号尚未在所有目标应用生效"

    // Quota Auto-Refresh Dialog
    override val quotaRefreshTitle = "设置配额自动刷新频率"
    override val quotaRefreshSubtitle = "配置多账号配额后台自动同步频率（每个卡片左下角展示最后更新时间）"
    override val quotaRefreshActiveIntervalTitle = "当前活跃账号刷新间隔"
    override val quotaRefreshBackgroundIntervalTitle = "其他后台账号刷新间隔"
    override val quotaRefreshCustomOption = "自定义…"
    override val quotaRefreshPlaceholderActive = "例如 45"
    override val quotaRefreshPlaceholderBackground = "例如 15"
    override val quotaRefreshActiveHint = "提示：当前活跃账号的刷新间隔会直接影响配额更新及时性与自动切号时机。"
    override val quotaRefreshDefaultSummary = "默认：当前账号 1 分钟，其他账号 10 分钟"
    override val quotaRefreshResetDefault = "恢复默认"
    override val quotaRefreshUnitSecond = "秒"
    override val quotaRefreshUnitMinute = "分钟"
    override val quotaRefreshUnitHour = "小时"
    override fun quotaRefreshPresetRecommended(label: String) = "$label (推荐)"
    override val quotaRefreshInputInvalid = "请输入有效的刷新时间"
    override fun quotaRefreshMinActiveSeconds(sec: Int) = "最短刷新时间为 $sec 秒"
    override fun quotaRefreshMinBackgroundMinutes(min: Int) = "最短刷新时间为 $min 分钟"
    override fun quotaRefreshMaxHours(hr: Int) = "最长刷新时间为 $hr 小时"

    // Account Cards & Screen
    override fun accountsLastSyncTime(time: String) = "配额最后同步时间: $time"
    override val accountsSyncToOtherHost = "同步生效到其他客户端"
    override val accountsSetAsActiveTooltip = "设为当前生效账号"
    override val accountsRefreshThisTooltip = "刷新此账号实时配额"
    override val accountsRefreshingTooltip = "正在刷新配额..."
    override val accountsDeleteThisTooltip = "删除此账号"
    override fun accountsModelFamily(label: String) = "$label 模型"
    override val accountsQuotaFiveHour = "5 小时配额"
    override val accountsQuotaWeekly = "周配额"
    override val accountsFetchingQuota = "正在获取配额数据..."
    override val accountsNoQuotaData = "暂无数据"
    override val accountsQuotaResetSoon = "即将重置"
    override val accountsQuotaFull = "● 满额可用"
    override val accountsQuotaResetInSuffix = " 后重置"

    // Time & Countdown formatting
    override fun formatCountdownDaysHours(days: Long, hours: Long) = "${days}天 ${hours}小时"
    override fun formatCountdownDays(days: Long) = "${days}天"
    override fun formatCountdownHoursMinutes(hours: Long, minutes: Long) = "${hours}小时 ${minutes}分钟"
    override fun formatCountdownHours(hours: Long) = "${hours}小时"
    override fun formatCountdownMinutes(minutes: Long) = "${minutes}分钟"
    override val formatCountdownLessThanMinute = "< 1分钟"

    // Quota natural language descriptions
    override val quotaDescFiveHourFull = "您的五小时额度目前处于完全可用状态。"
    override val quotaDescWeeklyFull = "您的周额度目前处于完全可用状态。"
    override val quotaDescGeneralFull = "您的额度目前处于完全可用状态。"
    override fun quotaDescFiveHourResetting(timeStr: String) = "您已消耗部分五小时额度，将在 $timeStr 后完全重置。"
    override fun quotaDescWeeklyResetting(timeStr: String) = "您已消耗部分周额度，将在 $timeStr 后完全重置。"
    override fun quotaDescGeneralResetting(timeStr: String) = "额度将在 $timeStr 后完全重置。"

    // Quota Window
    override val quotaWindowFiveHour = "5 小时额度"
    override val quotaWindowWeekly = "周度额度"
    override val quotaWindowDaily = "每日额度"
    override val quotaWindowGeneral = "周期额度"
    override val accountsSearchPlaceholder = "按邮箱快速检索..."
    override val accountsSortByQuotaDesc = "按账号剩余配额从高到低排序"
    override val accountsSortByQuotaDescActive = "当前按剩余配额从高到低排序 (点击切换为默认排序)"
    override val accountsSortChipLabel = "按配额排序"
    override val accountsAddAccountTooltip = "添加单个 Refresh Token 或批量导入多个凭据"
    override val accountsRefreshAllTooltip = "立即并发刷新所有账号的最新配额数据"
    override fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int) =
        "配置配额自动刷新频率 (当前: 活跃账号 $activeSec 秒 / 后台账号 $bgMin 分钟)"

    override val accountsPrivacyHideTooltip = "开启隐私脱敏，隐藏邮箱敏感字符"
    override val accountsPrivacyShowTooltip = "关闭脱敏，显示完整邮箱地址"
    override val accountsExportTooltip = "导出账号凭据 (支持复制到剪贴板或保存为 JSON 文件)"
    override val accountsExportCopyToClipboard = "复制到剪贴板"
    override val accountsExportSaveJson = "保存为 JSON 文件..."
    override fun accountsExportCopiedNotice(count: Int) = "已将 $count 个账号凭据复制到剪贴板"
    override fun accountsExportSuccessNotice(count: Int, filename: String) = "已成功导出 $count 个账号凭据至 $filename"
    override fun accountsExportFailedNotice(error: String) = "导出文件失败: $error"
    override val accountsExportDialogTitle = "保存账号凭据"
    override val accountsSmartSwitchTooltip = "配置配额不足或遇到 429 限流时的自动切号策略"
    override fun accountsSearchNoMatch(query: String) = "未找到与「$query」匹配的账号"
    override val accountsDeleteConfirmTitle = "删除账号"
    override fun accountsDeleteConfirmMsg(email: String) =
        "确定要从 Studio 移除账号「$email」吗？移除后将停止自动刷新与配额监控。"

    override val accountsDeleteConfirmBtn = "确定删除"

    // Overview Screen & Hero Card
    override val overviewTodayRequests = "今日请求总量"
    override fun overviewRequestsUnit(count: Long) = "$count 次"
    override val overviewServiceUptime = "服务正常率"
    override val overviewAvgLatency = "平均响应延迟"
    override val overviewRouteUpstreamStatus = "路由上游状态"
    override val overviewOfficialDirect = "官方默认直连"
    override fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int) =
        "$providerCount 个服务商 · $modelCount 个模型"

    override fun overviewSourceInUse(sources: String) = "$sources 正在使用"
    override val overviewActiveAccountBadge = "当前生效账号"
    override val overviewSyncingQuotas = "正在同步配额数据..."

    // Notices & ViewModel Messages
    override val noticeAuthLinkCopied = "授权链接已复制到剪贴板"
    override val noticeAuthLinkCopiedBrowser = "授权链接已复制到剪贴板，请在浏览器中打开"
    override val noticeSwitchAlreadyRunning = "已有账号切换任务正在执行，请稍后再试"
    override fun noticeSwitchResult(summary: String) = "账号切换结果：$summary"
    override fun noticeSwitchFailed(error: String) = "切换账号失败: $error"
    override fun noticeAccountNotFound(idOrEmail: String) = "未找到账号: $idOrEmail"
    override val noticeSmartSwitchEnabled = "已启用自动智能切号"
    override val noticeSmartSwitchDisabled = "已停用自动智能切号"
    override val noticeQuotaAutoRefreshEnabled = "已更新配额自动刷新配置"
    override val noticeQuotaAutoRefreshDisabled = "已停用配额自动刷新"
    override val noticeAccountRemoved = "已移除账号"
    override val noticeTokenRefreshed = "账号凭据已成功刷新"
    override fun noticeTokenRefreshFailed(error: String) = "凭据刷新失败: $error"
    override val noticeRemarkUpdated = "已更新账号备注"
    override fun noticeCleanAccountsSuccess(count: Int) = "已清理 $count 个异常/过期账号"
    override fun noticeCleanAccountsFailed(error: String) = "清理失败: $error"
    override fun noticeBatchImportSuccess(count: Int) = "成功批量导入 $count 个账号"
    override fun noticeBatchImportPartial(successCount: Int, failedCount: Int) =
        "批量导入完成：成功 $successCount 个，已跳过 $failedCount 个无效 Token"

    override fun noticeBatchImportFailedAll(failedCount: Int) =
        "批量导入失败：所有输入的 $failedCount 个 Token 均已失效或被撤销"

    override val noticeQuotasUpdatedAll = "已更新所有账号配额数据"
    override fun noticeQuotasUpdateFailedAll(error: String) = "配额刷新异常: $error"
    override val noticeQuotaRefreshedSingle = "已刷新账号配额"
    override fun noticeQuotaRefreshFailedSingle(error: String) = "配额拉取失败: $error"
    override fun switchStatusNotAvailable(target: String) = "$target 当前不可用"
    override fun switchStatusConfigured(target: String) = "$target 已配置"
    override fun switchStatusConfirmed(target: String) = "$target 已生效"
    override fun switchStatusPendingRestart(target: String) = "$target 待确认重启"
    override fun switchStatusFailed(target: String) = "$target 未确认生效"

    // Add Account Dialog & Import
    override val accountsAddTabOAuth = "Google 浏览器登录 (OAuth)"
    override val accountsAddTabTokenImport = "Token / JSON 导入"
    override val accountsAddSelectJsonFileTitle = "选择账号备份 JSON 文件"
    override val accountsAddInvalidAuthCode = "未识别出有效授权码，请确认完整 URL"
    override val accountsAddReopenBrowser = "重新在浏览器打开"
    override val accountsAddOpenBrowser = "在浏览器中打开授权"
    override val accountsAddCopyAuthUrl = "复制授权链接"
    override val accountsAddCancelAuth = "取消授权"
    override val accountsAddFallbackManualHint = "若自动回调受阻，可将浏览器地址栏中的完整网址复制粘贴至下方："
    override val accountsAddFallbackManualPlaceholder = "http://127.0.0.1:41321/... 或授权码"
    override val accountsAddSubmit = "提交"
    override val accountsAddTokenBatchDesc =
        "支持粘贴单个/多行 Refresh Token（每行一个）、Cockpit 导出的 JSON 数组，或直接选择备份文件。"
    override val accountsAddImportJsonFile = "导入 JSON 文件"
    override val accountsAddPasteClipboard = "从剪贴板粘贴"
    override val accountsAddTokenPlaceholder = "粘贴 1//0g... 字符串（支持多行批量粘贴或 JSON 数组/对象）"
    override fun accountsAddRecognizedCount(count: Int, preview: String) = "已识别 $count 个有效账号凭据 $preview"
    override val accountsAddUnrecognizedTokens = "未能识别出有效的 Refresh Token 或 JSON 数据"
    override val accountsAddImporting = "正在验证并导入账号..."
    override fun accountsAddConfirmImport(count: Int) = when {
        count > 1 -> "批量导入账号 ($count 个)"
        count == 1 -> "确认导入账号 (1 个)"
        else -> "确认导入账号"
    }

    // Other UI Components & Relative Times
    override val settingsAccountAndAppCardTitle = "账号与应用设置"
    override fun accountsCountSummary(count: Int) = "$count 个账号"
    override val accountsFiveHourLabel = "5小时"
    override val accountsWeeklyLabel = "周"
    override fun accountsResetInCountdown(countdown: String) = "$countdown 后重置"
    override val timeNeverRefreshed = "从未刷新"
    override val timeJustNow = "刚刚更新"
    override fun timeMinutesAgo(min: Long) = "上次刷新 $min 分钟前"
    override fun timeHoursAgo(hours: Long) = "上次刷新 $hours 小时前"
    override fun timeDaysAgo(days: Long) = "上次刷新 $days 天前"

    override fun proxyStarted(port: Int) = "本地代理已启动 ($port)"
    override fun proxyStartFailed(error: String) = "本地代理启动失败：$error"
    override val proxyStopped = "本地代理已停止"
    override fun proxyRestarted(port: Int) = "本地代理已重启 ($port)"
    override fun proxyRestartFailed(error: String) = "本地代理重启失败：$error"
    override fun proxyTestSuccess(latencyMs: Long) = "代理连接测试成功 (${latencyMs}ms)"
    override fun proxyTestFailed(error: String) = "代理连接测试失败：$error"

}

object AdvancedStringsEn : AdvancedStrings {
    override val accountsSwitchDialogTitle = "Switch Account"
    override val accountsSwitchSelectTargetTitle = "Select Target Applications"
    override val accountsSwitchStatusIdeNotInstalled = "Not installed · Antigravity IDE not detected"
    override val accountsSwitchStatusIdeRunning = "Running · Will safely exit and restart"
    override val accountsSwitchStatusIdeStopped = "Stopped · Will inject credentials and launch"
    override val accountsSwitchSharedTitleCli = "Antigravity CLI (Shared Credentials)"
    override val accountsSwitchSharedTitleSystem = "System Shared Credentials (CLI / Local)"
    override val accountsSwitchStatusAppRunning = "App is running · Will safely exit and restart"
    override val accountsSwitchStatusAppStopped = "App is stopped · Will inject credentials and launch"
    override val accountsSwitchStatusCliOnly = "App not installed · Syncs ~/.gemini/ credentials for CLI"
    override val accountsSwitchStatusNone =
        "No client detected · Only updates Studio active account and shared credentials"
    override val accountsSwitchRememberChoice = "Remember selection"
    override val accountsSwitching = "Switching..."
    override val accountsSwitchConfirmRestart = "Confirm & Restart"
    override val accountsSwitchConfirmLaunch = "Confirm & Launch"
    override val accountsSwitchConfirm = "Confirm Switch"
    override val accountsSwitchTargetIde = "Antigravity IDE"
    override val accountsSwitchTargetAppCli = "Antigravity App & CLI"

    // Smart Switch Dialog & Strategy
    override val smartSwitchTitle = "Smart Account Switch"
    override val smartSwitchSubtitle =
        "Automatically switch to the best fallback account on quota exhaustion or 429 errors"
    override val smartSwitchEnableTitle = "Enable Smart Account Switch"
    override val smartSwitchEnableDesc = "Automatically switch account upon 429 rate limit or insufficient quota"
    override val smartSwitchThresholdLabel = "Switch Trigger Quota Threshold"
    override val smartSwitchStrategyLabel = "Fallback Account Strategy"
    override val smartSwitchStrategyHighestQuota = "Highest Quota First (Recommended)"
    override val smartSwitchStrategyRoundRobin = "Round Robin"
    override val smartSwitchCooldownLabel = "Minimum Switch Cooldown"
    override fun smartSwitchSeconds(seconds: Int) = "$seconds s"
    override val smartSwitchProtectGenerationTitle = "Active Generation Protection"
    override val smartSwitchProtectGenerationDesc = "Pause auto-switch during streaming responses or agent runs"
    override val smartSwitchInterruptTip =
        "Protection note: Auto-switch is deferred during generation to avoid stream interruptions."
    override val smartSwitchReasonDisabled = "Smart switch is disabled"
    override fun smartSwitchReasonCooldown(remainingSec: Long) = "In cooldown period (${remainingSec}s remaining)"
    override val smartSwitchReasonWorkflowLocked = "Active workflow is protected"
    override val smartSwitchReasonNoBackupAccounts = "No backup accounts available"
    override val smartSwitchReasonNoEligibleCandidate = "No candidate account with sufficient quota found"
    override val smartSwitchTriggerReason429 = "Hit 429 Quota Exceeded"
    override val smartSwitchTriggerReasonLowQuota = "Quota fell below threshold"
    override fun smartSwitchReasonTaskRunning(trigger: String) = "$trigger, but another switch task is in progress"
    override fun smartSwitchReasonSuggestSwitch(trigger: String, email: String) =
        "$trigger. Suggested switch to $email; please restart app to apply."

    override val hotSwitchTaskAlreadyRunning = "Another account switch task is already running, please try again later"
    override val hotSwitchIdeNotApplied = "IDE account is not yet active"
    override val hotSwitchNotAllTargetsApplied = "Account not yet active in all target applications"

    // Quota Auto-Refresh Dialog
    override val quotaRefreshTitle = "Quota Auto-Refresh Frequency"
    override val quotaRefreshSubtitle = "Configure background sync frequency for multiple accounts"
    override val quotaRefreshActiveIntervalTitle = "Active Account Refresh Interval"
    override val quotaRefreshBackgroundIntervalTitle = "Background Accounts Refresh Interval"
    override val quotaRefreshCustomOption = "Custom…"
    override val quotaRefreshPlaceholderActive = "e.g. 45"
    override val quotaRefreshPlaceholderBackground = "e.g. 15"
    override val quotaRefreshActiveHint =
        "Tip: Active account refresh interval directly impacts quota freshness and auto-switch timing."
    override val quotaRefreshDefaultSummary = "Default: Active account 1 min, background accounts 10 min"
    override val quotaRefreshResetDefault = "Reset to Default"
    override val quotaRefreshUnitSecond = "sec"
    override val quotaRefreshUnitMinute = "min"
    override val quotaRefreshUnitHour = "hr"
    override fun quotaRefreshPresetRecommended(label: String) = "$label (Recommended)"
    override val quotaRefreshInputInvalid = "Please enter a valid refresh interval"
    override fun quotaRefreshMinActiveSeconds(sec: Int) = "Minimum interval is $sec seconds"
    override fun quotaRefreshMinBackgroundMinutes(min: Int) = "Minimum interval is $min minutes"
    override fun quotaRefreshMaxHours(hr: Int) = "Maximum interval is $hr hour(s)"

    // Account Cards & Screen
    override fun accountsLastSyncTime(time: String) = "Quota last synced: $time"
    override val accountsSyncToOtherHost = "Sync to other clients"
    override val accountsSetAsActiveTooltip = "Set as current active account"
    override val accountsRefreshThisTooltip = "Refresh quota for this account"
    override val accountsRefreshingTooltip = "Refreshing quota..."
    override val accountsDeleteThisTooltip = "Delete this account"
    override fun accountsModelFamily(label: String) = "$label Models"
    override val accountsQuotaFiveHour = "5-Hour Quota"
    override val accountsQuotaWeekly = "Weekly Quota"
    override val accountsFetchingQuota = "Fetching quota data..."
    override val accountsNoQuotaData = "No Data"
    override val accountsQuotaResetSoon = "Resetting soon"
    override val accountsQuotaFull = "● 100% Available"
    override val accountsQuotaResetInSuffix = " left"

    // Time & Countdown formatting
    override fun formatCountdownDaysHours(days: Long, hours: Long) = "${days}d ${hours}h"
    override fun formatCountdownDays(days: Long) = "${days}d"
    override fun formatCountdownHoursMinutes(hours: Long, minutes: Long) = "${hours}h ${minutes}m"
    override fun formatCountdownHours(hours: Long) = "${hours}h"
    override fun formatCountdownMinutes(minutes: Long) = "${minutes}m"
    override val formatCountdownLessThanMinute = "< 1m"

    // Quota natural language descriptions
    override val quotaDescFiveHourFull = "Your 5-hour quota is currently fully available."
    override val quotaDescWeeklyFull = "Your weekly quota is currently fully available."
    override val quotaDescGeneralFull = "Your quota is currently fully available."
    override fun quotaDescFiveHourResetting(timeStr: String) = "Partially consumed 5-hour quota, resets in $timeStr."
    override fun quotaDescWeeklyResetting(timeStr: String) = "Partially consumed weekly quota, resets in $timeStr."
    override fun quotaDescGeneralResetting(timeStr: String) = "Quota will fully reset in $timeStr."

    // Quota Window
    override val quotaWindowFiveHour = "5-Hour Quota"
    override val quotaWindowWeekly = "Weekly Quota"
    override val quotaWindowDaily = "Daily Quota"
    override val quotaWindowGeneral = "Period Quota"
    override val accountsSearchPlaceholder = "Search by email..."
    override val accountsSortByQuotaDesc = "Sort by remaining quota (High to Low)"
    override val accountsSortByQuotaDescActive = "Currently sorted by quota (Click to switch to default)"
    override val accountsSortChipLabel = "Sort by Quota"
    override val accountsAddAccountTooltip = "Add single Refresh Token or import in bulk"
    override val accountsRefreshAllTooltip = "Concurrently refresh quotas for all accounts"
    override fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int) =
        "Configure auto-refresh (Current: active ${activeSec}s / background ${bgMin}m)"

    override val accountsPrivacyHideTooltip = "Enable privacy mode to mask email"
    override val accountsPrivacyShowTooltip = "Disable privacy mode to show full email"
    override val accountsExportTooltip = "Export credentials (clipboard or JSON file)"
    override val accountsExportCopyToClipboard = "Copy to Clipboard"
    override val accountsExportSaveJson = "Save as JSON File..."
    override fun accountsExportCopiedNotice(count: Int) = "Copied $count account credentials to clipboard"
    override fun accountsExportSuccessNotice(count: Int, filename: String) = "Exported $count credentials to $filename"
    override fun accountsExportFailedNotice(error: String) = "Failed to export file: $error"
    override val accountsExportDialogTitle = "Save Account Credentials"
    override val accountsSmartSwitchTooltip = "Configure smart auto-switch for low quota or 429 errors"
    override fun accountsSearchNoMatch(query: String) = "No accounts found matching '$query'"
    override val accountsDeleteConfirmTitle = "Delete Account"
    override fun accountsDeleteConfirmMsg(email: String) =
        "Are you sure you want to remove '$email'? Auto-refresh and monitoring will stop."

    override val accountsDeleteConfirmBtn = "Delete"

    // Overview Screen & Hero Card
    override val overviewTodayRequests = "Today's Requests"
    override fun overviewRequestsUnit(count: Long) = "$count requests"
    override val overviewServiceUptime = "Service Uptime"
    override val overviewAvgLatency = "Avg Latency"
    override val overviewRouteUpstreamStatus = "Route Upstream"
    override val overviewOfficialDirect = "Official Direct"
    override fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int) =
        "$providerCount providers · $modelCount models"

    override fun overviewSourceInUse(sources: String) = "$sources in use"
    override val overviewActiveAccountBadge = "Active Account"
    override val overviewSyncingQuotas = "Syncing quota data..."

    // Notices & ViewModel Messages
    override val noticeAuthLinkCopied = "Authorization link copied to clipboard"
    override val noticeAuthLinkCopiedBrowser = "Auth link copied, please open in browser"
    override val noticeSwitchAlreadyRunning = "Another account switch task is already running, please try again later"
    override fun noticeSwitchResult(summary: String) = "Switch result: $summary"
    override fun noticeSwitchFailed(error: String) = "Account switch failed: $error"
    override fun noticeAccountNotFound(idOrEmail: String) = "Account not found: $idOrEmail"
    override val noticeSmartSwitchEnabled = "Smart auto-switch enabled"
    override val noticeSmartSwitchDisabled = "Smart auto-switch disabled"
    override val noticeQuotaAutoRefreshEnabled = "Quota auto-refresh config updated"
    override val noticeQuotaAutoRefreshDisabled = "Quota auto-refresh disabled"
    override val noticeAccountRemoved = "Account removed"
    override val noticeTokenRefreshed = "Account credentials refreshed successfully"
    override fun noticeTokenRefreshFailed(error: String) = "Failed to refresh credentials: $error"
    override val noticeRemarkUpdated = "Account note updated"
    override fun noticeCleanAccountsSuccess(count: Int) = "Cleaned up $count invalid/expired accounts"
    override fun noticeCleanAccountsFailed(error: String) = "Failed to clean accounts: $error"
    override fun noticeBatchImportSuccess(count: Int) = "Successfully imported $count accounts"
    override fun noticeBatchImportPartial(successCount: Int, failedCount: Int) =
        "Batch import completed: $successCount succeeded, $failedCount skipped"

    override fun noticeBatchImportFailedAll(failedCount: Int) =
        "Batch import failed: all $failedCount tokens are invalid"

    override val noticeQuotasUpdatedAll = "All account quotas updated"
    override fun noticeQuotasUpdateFailedAll(error: String) = "Quota refresh error: $error"
    override val noticeQuotaRefreshedSingle = "Account quota refreshed"
    override fun noticeQuotaRefreshFailedSingle(error: String) = "Failed to fetch quota: $error"
    override fun switchStatusNotAvailable(target: String) = "$target currently unavailable"
    override fun switchStatusConfigured(target: String) = "$target configured"
    override fun switchStatusConfirmed(target: String) = "$target active"
    override fun switchStatusPendingRestart(target: String) = "$target pending restart"
    override fun switchStatusFailed(target: String) = "$target verification failed"

    // Add Account Dialog & Import
    override val accountsAddTabOAuth = "Google Sign In (OAuth)"
    override val accountsAddTabTokenImport = "Token / JSON Import"
    override val accountsAddSelectJsonFileTitle = "Select Accounts JSON Backup File"
    override val accountsAddInvalidAuthCode = "Invalid auth code, please verify full URL"
    override val accountsAddReopenBrowser = "Reopen in Browser"
    override val accountsAddOpenBrowser = "Open in Browser"
    override val accountsAddCopyAuthUrl = "Copy Auth Link"
    override val accountsAddCancelAuth = "Cancel Auth"
    override val accountsAddFallbackManualHint = "If auto-callback is blocked, paste the full URL from browser below:"
    override val accountsAddFallbackManualPlaceholder = "http://127.0.0.1:41321/... or Auth Code"
    override val accountsAddSubmit = "Submit"
    override val accountsAddTokenBatchDesc =
        "Supports pasting single/multi-line Refresh Tokens, Cockpit exported JSON arrays, or backup files."
    override val accountsAddImportJsonFile = "Import JSON File"
    override val accountsAddPasteClipboard = "Paste from Clipboard"
    override val accountsAddTokenPlaceholder = "Paste 1//0g... tokens (Supports multi-line batch or JSON)"
    override fun accountsAddRecognizedCount(count: Int, preview: String) =
        "Recognized $count valid account credentials $preview"

    override val accountsAddUnrecognizedTokens = "Unable to recognize valid Refresh Token or JSON data"
    override val accountsAddImporting = "Validating and importing accounts..."
    override fun accountsAddConfirmImport(count: Int) = when {
        count > 1 -> "Import $count Accounts"
        count == 1 -> "Import 1 Account"
        else -> "Confirm Import"
    }

    // Other UI Components & Relative Times
    override val settingsAccountAndAppCardTitle = "Accounts & Applications"
    override fun accountsCountSummary(count: Int) = "$count accounts"
    override val accountsFiveHourLabel = "5h"
    override val accountsWeeklyLabel = "Weekly"
    override fun accountsResetInCountdown(countdown: String) = "$countdown left"
    override val timeNeverRefreshed = "Never synced"
    override val timeJustNow = "Just now"
    override fun timeMinutesAgo(min: Long) = "Synced $min min ago"
    override fun timeHoursAgo(hours: Long) = "Synced $hours hr ago"
    override fun timeDaysAgo(days: Long) = "Synced $days d ago"

    override fun proxyStarted(port: Int) = "Local proxy started ($port)"
    override fun proxyStartFailed(error: String) = "Failed to start local proxy: $error"
    override val proxyStopped = "Local proxy stopped"
    override fun proxyRestarted(port: Int) = "Local proxy restarted ($port)"
    override fun proxyRestartFailed(error: String) = "Failed to restart local proxy: $error"
    override fun proxyTestSuccess(latencyMs: Long) = "Proxy connection test succeeded (${latencyMs}ms)"
    override fun proxyTestFailed(error: String) = "Proxy connection test failed: $error"
}
