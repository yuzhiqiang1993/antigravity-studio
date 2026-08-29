package com.yuzhiqiang.antigravity.i18n.sections

interface DoctorStrings {
    // Doctor Diagnostics
    val doctorTitle: String
    val doctorSubtitle: String
    val doctorRunAll: String
    val doctorScanning: String
    val doctorPassed: String
    val doctorFailed: String
    val doctorWarning: String
    val doctorDirect: String
    val doctorFixSuggestions: String
    val doctorDialogTitle: String
    val doctorDialogSubtitle: String
    val doctorBannerGood: String
    val doctorBannerWarning: String
    val doctorBannerError: String
    fun doctorBannerIssueCount(count: Int): String
    fun doctorBannerStats(total: Int, passed: Int, issues: Int): String
    fun doctorCheckedAt(time: String): String
    val doctorCategoryProxy: String
    val doctorCategoryNetwork: String
    val doctorCategoryConfig: String
    val doctorCategoryProvider: String
    val doctorCategoryHost: String
    val doctorScanningStatus: String
    val doctorRealtimeStatus: String
    val doctorScanningTitle: String
    val doctorScanningDesc: String
    val doctorFixStartProxy: String
    val doctorFixGoConfigure: String
    val doctorFixOneClickEnable: String
    val doctorFixUpdateConfig: String
    val doctorFixResetOfficial: String
    val doctorFixRestartIde: String
    val doctorFixRestartApp: String
    val doctorFixPruneModels: String
    val doctorFixRetry: String
    val doctorFixOpenNetworkSettings: String
    val doctorSuggestionPrefix: String
    val doctorAutoFixSuccess: String
    val doctorAutoFixFailed: String

    // Doctor Engine Checks
    val doctorCheckProxyStoppedTitle: String
    fun doctorCheckProxyStoppedMsg(port: Int): String
    val doctorCheckProxyStoppedSugg: String
    val doctorCheckProxyOkTitle: String
    fun doctorCheckProxyOkMsg(port: Int): String
    val doctorCheckProxyUnreachableTitle: String
    fun doctorCheckProxyUnreachableMsg(port: Int): String
    val doctorCheckProxyUnreachableSugg: String
    val doctorCheckNetworkOkTitle: String
    fun doctorCheckNetworkOkMsg(latencyMs: Long): String
    val doctorCheckNetworkDirectRouteDesc: String
    val doctorCheckNetworkFallbackRouteDesc: String
    fun doctorCheckNetworkOkWithRouteMsg(latencyMs: Long, mode: String, route: String): String
    fun doctorCheckNetworkFallbackMsg(latencyMs: Long, mode: String): String
    val doctorCheckNetworkFallbackSugg: String
    val doctorCheckNetworkFailedTitle: String
    fun doctorCheckNetworkFailedMsg(error: String): String
    fun doctorCheckNetworkFailedWithModeMsg(error: String, mode: String): String
    val doctorCheckNetworkFailedSugg: String
    val doctorCheckProxyConfigIssueTitle: String
    val doctorCheckProxyConfigNoSystemMsg: String
    val doctorCheckProxyConfigNoSystemSugg: String
    val doctorCheckNoProvidersTitle: String
    val doctorCheckNoProvidersMsg: String
    val doctorCheckNoProvidersSugg: String
    fun doctorCheckProviderNoModelsTitle(provider: String): String
    val doctorCheckProviderNoModelsMsg: String
    val doctorCheckProviderNoModelsSugg: String
    val doctorCheckIdeMismatchTitle: String
    fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckIdeMismatchSugg: String
    val doctorCheckIdeRunningSuffix: String
    val doctorCheckIdeOkTitle: String
    fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String): String
    val doctorCheckIdeOfficialTitle: String
    val doctorCheckIdeOfficialMsg: String
    val doctorCheckAppMismatchTitle: String
    fun doctorCheckAppMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckAppMismatchSugg: String
    val doctorCheckAppRunningSuffix: String
    val doctorCheckAppOkTitle: String
    fun doctorCheckAppOkMsg(port: Int, runningSuffix: String): String
    val doctorCheckAppOfficialTitle: String
    val doctorCheckAppOfficialMsg: String
    val doctorCheckCliMismatchTitle: String
    fun doctorCheckCliMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckCliMismatchSugg: String
    val doctorCheckCliOkTitle: String
    fun doctorCheckCliOkMsg(port: Int): String
    val doctorCheckCliOfficialTitle: String
    val doctorCheckCliOfficialMsg: String
    fun doctorCheckProviderInvalidModelsTitle(provider: String): String
    fun doctorCheckProviderInvalidModelsMsg(models: String): String
    val doctorCheckProviderInvalidModelsSugg: String
    fun doctorCheckProviderOkTitle(provider: String): String
    fun doctorCheckProviderOkMsg(count: Int): String
    fun doctorCheckProviderUnverifiedTitle(provider: String): String
    val doctorCheckProviderUnverifiedMsg: String
    val doctorCheckProviderUnverifiedSugg: String

    // Common Feedback & Tray
    val commonSave: String
    val commonCancel: String
    val commonConfirm: String
    val commonDelete: String
    val commonEdit: String
    val commonClose: String
    val commonSuccess: String
    val commonError: String
    val commonCopied: String
    val commonGotIt: String
    val commonRefresh: String
    val commonRetry: String
    val commonSearch: String
    val commonClear: String
    val commonSelectAll: String
    val commonUnselectAll: String
    val commonNotSet: String
    val commonUnknown: String
    val commonAndMore: String
    val commonOptional: String
    val commonUnsaved: String
    val trayShowMainWindow: String
    val trayQuitApplication: String

}

object DoctorStringsZh : DoctorStrings {

    override val doctorTitle = "系统健康诊断"
    override val doctorSubtitle = "一键诊断网络连通性、本地配置、应用接入与代理服务健康状态"
    override val doctorRunAll = "重新诊断"
    override val doctorScanning = "诊断中..."
    override val doctorPassed = "正常"
    override val doctorFailed = "异常"
    override val doctorWarning = "警告"
    override val doctorDirect = "直连"
    override val doctorFixSuggestions = "修复建议"
    override val doctorDialogTitle = "系统健康诊断"
    override val doctorDialogSubtitle = "检测本地代理服务、模型服务商连通性与 Antigravity 应用接入状态"
    override val doctorBannerGood = "系统状态良好，各项配置已就绪"
    override val doctorBannerWarning = "部分配置需要处理"
    override val doctorBannerError = "检测到系统运行异常"
    override fun doctorBannerIssueCount(count: Int) = " • $count 项待处理"
    override fun doctorBannerStats(total: Int, passed: Int, issues: Int) =
        "共 $total 项检测 • $passed 项正常" + if (issues > 0) " • $issues 项待处理" else ""

    override fun doctorCheckedAt(time: String) = "诊断于 $time"
    override val doctorCategoryProxy = "本地代理服务"
    override val doctorCategoryNetwork = "Google 官方服务连通性"
    override val doctorCategoryConfig = "本地配置文件"
    override val doctorCategoryProvider = "模型服务商连通性"
    override val doctorCategoryHost = "Antigravity 应用客户端"
    override val doctorScanningStatus = "正在诊断系统环境..."
    override val doctorRealtimeStatus = "诊断结果已就绪"
    override val doctorScanningTitle = "正在执行系统健康诊断..."
    override val doctorScanningDesc = "逐项排查代理端口、模型服务商网络连通性与应用代理配置"
    override val doctorFixStartProxy = "启动代理"
    override val doctorFixGoConfigure = "去配置"
    override val doctorFixOneClickEnable = "一键接入"
    override val doctorFixUpdateConfig = "更新配置"
    override val doctorFixResetOfficial = "重置为官方直连"
    override val doctorFixRestartIde = "重启 IDE"
    override val doctorFixRestartApp = "重启 App"
    override val doctorFixPruneModels = "清理模型"
    override val doctorFixRetry = "重试"
    override val doctorFixOpenNetworkSettings = "网络设置"
    override val doctorSuggestionPrefix = "💡 建议: "
    override val doctorAutoFixSuccess = "已执行自动修复"
    override val doctorAutoFixFailed = "自动修复失败，请手动检查"

    override val doctorCheckProxyStoppedTitle = "本地代理服务未运行"
    override fun doctorCheckProxyStoppedMsg(port: Int) = "代理服务处于停止状态，无法拦截转发请求（配置端口：$port）。"
    override val doctorCheckProxyStoppedSugg = "请启动本地代理服务。"
    override val doctorCheckProxyOkTitle = "本地代理服务运行正常"
    override fun doctorCheckProxyOkMsg(port: Int) = "代理已就绪并正常监听 http://127.0.0.1:$port。"
    override val doctorCheckProxyUnreachableTitle = "本地代理端点无法连通"
    override fun doctorCheckProxyUnreachableMsg(port: Int) = "无法连接 127.0.0.1:$port，请检查端口占用或权限。"
    override val doctorCheckProxyUnreachableSugg = "尝试重启代理服务。"
    override val doctorCheckNetworkOkTitle = "连接官方服务"
    override fun doctorCheckNetworkOkMsg(latencyMs: Long) = "官方 Cloud Code 服务通信正常（${latencyMs}ms）。"
    override val doctorCheckNetworkDirectRouteDesc = "直连"
    override val doctorCheckNetworkFallbackRouteDesc = "回退直连"
    override fun doctorCheckNetworkOkWithRouteMsg(latencyMs: Long, mode: String, route: String) =
        "官方 Cloud Code 服务通信正常（${latencyMs}ms） · 模式: $mode · 路径: $route。"

    override fun doctorCheckNetworkFallbackMsg(latencyMs: Long, mode: String) =
        "官方 Cloud Code 服务已连通（${latencyMs}ms） · 模式: $mode · ⚠️ 代理不可用，已自动回退直连。"

    override val doctorCheckNetworkFallbackSugg =
        "上游网络代理节点无法建立连接，当前已回退直连；如需走代理请检查本地代理客户端是否已开启。"
    override val doctorCheckNetworkFailedTitle = "连接官方服务失败"
    override fun doctorCheckNetworkFailedMsg(error: String) = "无法连通 Google 官方服务：$error。"
    override fun doctorCheckNetworkFailedWithModeMsg(error: String, mode: String) =
        "无法连通 Google 官方服务：$error（模式: $mode）。"

    override val doctorCheckNetworkFailedSugg =
        "请检查网络与代理配置；如直连正常但 Studio 仍失败，请在「网络设置」中调整代理模式或允许直连回退。"
    override val doctorCheckProxyConfigIssueTitle = "上游系统代理未配置"
    override val doctorCheckProxyConfigNoSystemMsg =
        "当前上游代理模式为「仅系统代理」且禁止直连回退，但系统未检测到有效代理配置，所有公网请求将被阻断。"
    override val doctorCheckProxyConfigNoSystemSugg =
        "建议在「网络设置」中开启直连回退，或将连接方式切换为「智能选择」。"
    override val doctorCheckNoProvidersTitle = "未配置或未启用任何服务商"
    override val doctorCheckNoProvidersMsg = "当前没有已启用的模型服务商，自定义模型请求将无法转发。"
    override val doctorCheckNoProvidersSugg = "前往「模型管理」添加服务商。"
    override fun doctorCheckProviderNoModelsTitle(provider: String) = "服务商「$provider」未配置模型"
    override val doctorCheckProviderNoModelsMsg = "该服务商已启用，但尚未添加任何可用模型。"
    override val doctorCheckProviderNoModelsSugg = "请在模型管理中配置可用模型。"
    override val doctorCheckIdeMismatchTitle = "Antigravity IDE 代理配置不匹配（待更新）"
    override fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int) =
        "检测到 settings.json 中代理配置为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致，可能导致请求失败。"

    override val doctorCheckIdeMismatchSugg = "点击一键修复将更新为当前端口并自动重启生效，或重置为官方直连模式。"
    override val doctorCheckIdeRunningSuffix = "（IDE 正在运行）"
    override val doctorCheckIdeOkTitle = "Antigravity IDE 代理接入正常"
    override fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String) =
        "settings.json 已正确配置为 http://127.0.0.1:$port $runningSuffix。"

    override val doctorCheckIdeOfficialTitle = "Antigravity IDE 使用官方模式（未接入代理）"
    override val doctorCheckIdeOfficialMsg =
        "当前直连 Google 官方服务，可正常使用。如需在 IDE 中使用自定义模型，可启用代理接入。"
    override val doctorCheckAppMismatchTitle = "Antigravity App 代理环境变量不匹配（待更新）"
    override fun doctorCheckAppMismatchMsg(current: String, targetPort: Int) =
        "检测到环境变量 CLOUD_CODE_URL 当前为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致。"

    override val doctorCheckAppMismatchSugg = "点击一键修复将更新环境变量并重启 App 生效，或重置为官方模式。"
    override val doctorCheckAppRunningSuffix = "（App 正在运行）"
    override val doctorCheckAppOkTitle = "Antigravity App 代理接入正常"
    override fun doctorCheckAppOkMsg(port: Int, runningSuffix: String) =
        "环境变量 CLOUD_CODE_URL 已正确配置为 http://127.0.0.1:$port $runningSuffix。"

    override val doctorCheckAppOfficialTitle = "Antigravity App 使用官方模式（未接入代理）"
    override val doctorCheckAppOfficialMsg = "当前直连 Google 官方服务。如需在 App 中使用自定义模型，可启用代理接入。"
    override val doctorCheckCliMismatchTitle = "Antigravity CLI 代理配置不匹配（待更新）"
    override fun doctorCheckCliMismatchMsg(current: String, targetPort: Int) =
        "检测到 CLI 代理配置为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致。"

    override val doctorCheckCliMismatchSugg = "点击一键修复更新为当前端口，或重置为官方模式。"
    override val doctorCheckCliOkTitle = "Antigravity CLI 代理接入正常"
    override fun doctorCheckCliOkMsg(port: Int) = "已在 CLI 配置文件中配置 cloud_code_url 为 http://127.0.0.1:$port。"
    override val doctorCheckCliOfficialTitle = "Antigravity CLI 使用官方模式（未接入代理）"
    override val doctorCheckCliOfficialMsg = "CLI 当前处于官方直连模式。"
    override fun doctorCheckProviderInvalidModelsTitle(provider: String) = "服务商「$provider」存在失效模型"
    override fun doctorCheckProviderInvalidModelsMsg(models: String) = "服务商当前未提供以下模型：$models。"
    override val doctorCheckProviderInvalidModelsSugg = "建议清理失效模型，避免请求因模型不存在而失败。"
    override fun doctorCheckProviderOkTitle(provider: String) = "服务商「$provider」连通正常"
    override fun doctorCheckProviderOkMsg(count: Int) = "鉴权成功，已配置的 $count 个模型均可用。"
    override fun doctorCheckProviderUnverifiedTitle(provider: String) = "服务商「$provider」已连通但无法获取模型列表"
    override val doctorCheckProviderUnverifiedMsg = "已成功连接服务商，但该端点未返回可解析的模型列表。"
    override val doctorCheckProviderUnverifiedSugg = "请确认模型列表接口已正确配置，或手动核对模型 ID。"

    override val commonSave = "保存配置"
    override val commonCancel = "取消"
    override val commonConfirm = "确认"
    override val commonDelete = "删除"
    override val commonEdit = "编辑"
    override val commonClose = "关闭"
    override val commonSuccess = "操作成功"
    override val commonError = "操作失败"
    override val commonCopied = "已复制到剪贴板"
    override val commonGotIt = "知道了"
    override val commonRefresh = "刷新"
    override val commonRetry = "重试"
    override val commonSearch = "搜索..."
    override val commonClear = "清除"
    override val commonSelectAll = "全选"
    override val commonUnselectAll = "取消全选"
    override val commonNotSet = "未设置"
    override val commonUnknown = "未知"
    override val commonAndMore = "等"
    override val commonOptional = "选填"
    override val commonUnsaved = "未保存"
    override val trayShowMainWindow = "显示主窗口"
    override val trayQuitApplication = "退出应用"

    // Account Switch Dialog & Process
}

object DoctorStringsEn : DoctorStrings {

    override val doctorTitle = "Doctor Health Diagnostics"
    override val doctorSubtitle = "Full-stack diagnostics for network, configs, host and proxy"
    override val doctorRunAll = "Run All Checks"
    override val doctorScanning = "Diagnosing..."
    override val doctorPassed = "Passed"
    override val doctorFailed = "Failed"
    override val doctorWarning = "Warning"
    override val doctorDirect = "Direct"
    override val doctorFixSuggestions = "Fix Suggestions"
    override val doctorDialogTitle = "System Health & Diagnostic Suite"
    override val doctorDialogSubtitle = "Check local proxy, upstream connectivity and Antigravity host integration"
    override val doctorBannerGood = "All systems operational and ready"
    override val doctorBannerWarning = "Some configurations need attention"
    override val doctorBannerError = "System issues detected"
    override fun doctorBannerIssueCount(count: Int) = " • $count issue(s) pending"
    override fun doctorBannerStats(total: Int, passed: Int, issues: Int) =
        "Total $total checks • $passed healthy" + if (issues > 0) " • $issues pending" else ""

    override fun doctorCheckedAt(time: String) = "Checked at $time"
    override val doctorCategoryProxy = "Local Proxy Server"
    override val doctorCategoryNetwork = "Official Service Connectivity"
    override val doctorCategoryConfig = "Configuration Integrity"
    override val doctorCategoryProvider = "Model Providers"
    override val doctorCategoryHost = "Antigravity Host Environments"
    override val doctorScanningStatus = "Scanning system environment..."
    override val doctorRealtimeStatus = "Diagnostics generated in real-time"
    override val doctorScanningTitle = "Executing full-stack health diagnostics..."
    override val doctorScanningDesc = "Checking proxy ports, provider handshakes and host integration configs"
    override val doctorFixStartProxy = "Start Proxy"
    override val doctorFixGoConfigure = "Configure"
    override val doctorFixOneClickEnable = "Enable Proxy"
    override val doctorFixUpdateConfig = "Update Config"
    override val doctorFixResetOfficial = "Reset Official"
    override val doctorFixRestartIde = "Restart IDE"
    override val doctorFixRestartApp = "Restart App"
    override val doctorFixPruneModels = "Prune Models"
    override val doctorFixRetry = "Retry"
    override val doctorFixOpenNetworkSettings = "Network Settings"
    override val doctorSuggestionPrefix = "💡 Suggestion: "
    override val doctorAutoFixSuccess = "Auto-fix applied successfully"
    override val doctorAutoFixFailed = "Auto-fix failed; please check manually"

    override val doctorCheckProxyStoppedTitle = "Local proxy server is not running"
    override fun doctorCheckProxyStoppedMsg(port: Int) =
        "The proxy server is stopped and cannot intercept requests (configured port: $port)."

    override val doctorCheckProxyStoppedSugg = "Please start the local proxy server."
    override val doctorCheckProxyOkTitle = "Local proxy server is running"
    override fun doctorCheckProxyOkMsg(port: Int) = "Proxy is ready and listening on http://127.0.0.1:$port."
    override val doctorCheckProxyUnreachableTitle = "Local proxy endpoint unreachable"
    override fun doctorCheckProxyUnreachableMsg(port: Int) =
        "Cannot connect to 127.0.0.1:$port; check port conflicts or permissions."

    override val doctorCheckProxyUnreachableSugg = "Try restarting the proxy server."
    override val doctorCheckNetworkOkTitle = "Official service connectivity"
    override fun doctorCheckNetworkOkMsg(latencyMs: Long) = "Google Cloud Code service is reachable (${latencyMs}ms)."
    override val doctorCheckNetworkDirectRouteDesc = "Direct"
    override val doctorCheckNetworkFallbackRouteDesc = "Direct (fallback)"
    override fun doctorCheckNetworkOkWithRouteMsg(latencyMs: Long, mode: String, route: String) =
        "Google Cloud Code service reachable (${latencyMs}ms) · Mode: $mode · Route: $route."

    override fun doctorCheckNetworkFallbackMsg(latencyMs: Long, mode: String) =
        "Google Cloud Code service reachable (${latencyMs}ms) · Mode: $mode · ⚠️ Proxy unavailable, fell back to direct."

    override val doctorCheckNetworkFallbackSugg =
        "The configured proxy is unreachable and connection fell back to direct. Check your local proxy client if needed."
    override val doctorCheckNetworkFailedTitle = "Failed to connect to official service"
    override fun doctorCheckNetworkFailedMsg(error: String) = "Cannot reach Google official services: $error."
    override fun doctorCheckNetworkFailedWithModeMsg(error: String, mode: String) =
        "Cannot reach Google official services: $error (Mode: $mode)."

    override val doctorCheckNetworkFailedSugg =
        "Check network and proxy settings; adjust outbound proxy mode or enable direct fallback in Network Settings."
    override val doctorCheckProxyConfigIssueTitle = "Upstream system proxy not configured"
    override val doctorCheckProxyConfigNoSystemMsg =
        "Current proxy mode is 'System Proxy' without direct fallback, but no system proxy is detected. All requests will be blocked."
    override val doctorCheckProxyConfigNoSystemSugg =
        "Enable direct fallback in Network Settings or switch to Smart Select mode."
    override val doctorCheckNoProvidersTitle = "No model providers configured or enabled"
    override val doctorCheckNoProvidersMsg = "No active providers; all custom model requests will be blocked."
    override val doctorCheckNoProvidersSugg = "Go to Models screen to add a provider."
    override fun doctorCheckProviderNoModelsTitle(provider: String) = "Provider \"$provider\" has no models configured"
    override val doctorCheckProviderNoModelsMsg = "This provider is enabled but has no upstream models associated."
    override val doctorCheckProviderNoModelsSugg = "Configure upstream models in Models screen."
    override val doctorCheckIdeMismatchTitle = "Antigravity IDE proxy config mismatch (Needs update)"
    override fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int) =
        "Detected settings.json proxy is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckIdeMismatchSugg =
        "Click auto-fix to update to current port and restart IDE, or reset to official mode."
    override val doctorCheckIdeRunningSuffix = "(IDE is running)"
    override val doctorCheckIdeOkTitle = "Antigravity IDE proxy integration is active"
    override fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String) =
        "settings.json is properly configured to http://127.0.0.1:$port $runningSuffix."

    override val doctorCheckIdeOfficialTitle = "Antigravity IDE is in official mode (No proxy)"
    override val doctorCheckIdeOfficialMsg =
        "Directly connected to Google official service. Enable proxy integration to use custom models in IDE."
    override val doctorCheckAppMismatchTitle = "Antigravity App proxy environment mismatch (Needs update)"
    override fun doctorCheckAppMismatchMsg(current: String, targetPort: Int) =
        "Detected CLOUD_CODE_URL is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckAppMismatchSugg =
        "Click auto-fix to update environment variable and restart App, or reset to official mode."
    override val doctorCheckAppRunningSuffix = "(App is running)"
    override val doctorCheckAppOkTitle = "Antigravity App proxy integration is active"
    override fun doctorCheckAppOkMsg(port: Int, runningSuffix: String) =
        "CLOUD_CODE_URL is properly configured to http://127.0.0.1:$port $runningSuffix."

    override val doctorCheckAppOfficialTitle = "Antigravity App is in official mode (No proxy)"
    override val doctorCheckAppOfficialMsg =
        "Directly connected to Google official service. Enable proxy integration to use custom models in App."
    override val doctorCheckCliMismatchTitle = "Antigravity CLI proxy config mismatch (Needs update)"
    override fun doctorCheckCliMismatchMsg(current: String, targetPort: Int) =
        "Detected CLI proxy is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckCliMismatchSugg = "Click auto-fix to update to current port, or reset to official mode."
    override val doctorCheckCliOkTitle = "Antigravity CLI proxy integration is active"
    override fun doctorCheckCliOkMsg(port: Int) =
        "cloud_code_url in CLI config is properly configured to http://127.0.0.1:$port."

    override val doctorCheckCliOfficialTitle = "Antigravity CLI is in official mode (No proxy)"
    override val doctorCheckCliOfficialMsg = "CLI is currently in official direct mode."
    override fun doctorCheckProviderInvalidModelsTitle(provider: String) = "Provider \"$provider\" has invalid models"
    override fun doctorCheckProviderInvalidModelsMsg(models: String) =
        "Upstream does not provide the following models: $models."

    override val doctorCheckProviderInvalidModelsSugg = "Prune invalid models to avoid request errors."
    override fun doctorCheckProviderOkTitle(provider: String) = "Provider \"$provider\" connected successfully"
    override fun doctorCheckProviderOkMsg(count: Int) =
        "Authentication verified; all $count models are available in upstream catalog."

    override fun doctorCheckProviderUnverifiedTitle(provider: String) =
        "Provider \"$provider\" reachable but catalog unverified"

    override val doctorCheckProviderUnverifiedMsg =
        "Connected to upstream, but endpoint returned no parseable model catalog."
    override val doctorCheckProviderUnverifiedSugg =
        "Verify models endpoint configuration and check model IDs manually."

    override val commonSave = "Save"
    override val commonCancel = "Cancel"
    override val commonConfirm = "Confirm"
    override val commonDelete = "Delete"
    override val commonEdit = "Edit"
    override val commonClose = "Close"
    override val commonSuccess = "Success"
    override val commonError = "Error"
    override val commonCopied = "Copied to clipboard"
    override val commonGotIt = "Got it"
    override val commonRefresh = "Refresh"
    override val commonRetry = "Retry"
    override val commonSearch = "Search..."
    override val commonClear = "Clear"
    override val commonSelectAll = "Select All"
    override val commonUnselectAll = "Deselect All"
    override val commonNotSet = "Not Set"
    override val commonUnknown = "Unknown"
    override val commonAndMore = "etc."
    override val commonOptional = "Optional"
    override val commonUnsaved = "Unsaved"
    override val trayShowMainWindow = "Show Main Window"
    override val trayQuitApplication = "Quit"

    // Account Switch Dialog & Process
}
