package com.yuzhiqiang.antigravity.i18n

interface Strings {
    val appName: String
    val appSubtitle: String

    // Navigation
    val navOverview: String
    val navModels: String
    val navActivity: String
    val navSettings: String
    val navDoctor: String

    // Overview
    val overviewProxyCardTitle: String
    val overviewProxyRunning: String
    val overviewProxyStopped: String
    val overviewProxyPort: String
    val overviewStartProxy: String
    val overviewStopProxy: String
    val overviewRestartProxy: String
    val overviewSubtitle: String
    val overviewCopyAddress: String
    val overviewDiagnostics: String
    val overviewProviderMetric: String
    val overviewModelMetric: String
    val overviewDisabledMetric: String
    val overviewHostSection: String
    val overviewNotice: String
    val hostUpdateFailed: String

    val hostIdeTitle: String
    val hostIdeDesc: String
    val hostAppTitle: String
    val hostAppDesc: String
    val hostCliTitle: String
    val hostCliDesc: String

    val hostStatusActive: String
    val hostStatusInactive: String
    val hostStatusNotInstalled: String
    val hostEnable: String
    val hostDisable: String
    val hostRestartNotice: String
    val hostLaunch: String

    // Models
    val modelsOfficialTitle: String
    val modelsOfficialSubtitle: String
    val modelsCustomTitle: String
    val modelsAddProvider: String
    val modelsEditProvider: String
    val modelsDeleteProvider: String
    val modelsTestConnection: String
    val modelsFetchModels: String
    val modelsNoProviders: String
    val modelsCompressionPolicy: String
    val modelsReasoningConfig: String
    val modelsSubtitle: String
    val modelsOfficialTab: String
    val modelsCustomTab: String
    val modelsCollapse: String
    val modelsExpand: String
    val modelsContext: String
    val modelsVision: String
    val modelsTools: String
    val modelsReasoning: String
    val modelsNoModels: String
    val modelsTesting: String
    val modelsConnectionOk: String
    val modelsConnectionFailed: String
    val modelsRefreshOfficial: String
    val modelsFetchingOfficial: String

    // Activity
    val activityTitle: String
    val activityFilterAll: String
    val activityFilterFailed: String
    val activityClear: String
    val activityEmpty: String
    val activityPassthrough: String
    val activityRouted: String
    val activitySubtitle: String
    val activitySearchPlaceholder: String
    val activityRecent: String
    val activityTotal: String
    val activityFailedTotal: String
    val activityAverage: String
    val activityInMemory: String
    val activityHealthy: String
    val activityHasErrors: String
    val activityFallback: String
    val activityFallbackFailed: String

    // Settings
    val settingsTitle: String
    val settingsLanguage: String
    val settingsTheme: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsPort: String
    val settingsStoragePath: String
    val settingsAbout: String
    val settingsSubtitle: String
    val settingsGeneral: String
    val settingsNetwork: String
    val settingsData: String
    val settingsAboutSection: String
    val settingsLanguageDescription: String
    val settingsThemeDescription: String
    val settingsPortDescription: String
    val settingsStorageDescription: String
    val settingsOpenDirectory: String
    val settingsAboutDescription: String
    val settingsPortInvalid: String
    val settingsDirectoryOpenError: String
    val settingsVersion: String

    // Doctor
    val doctorTitle: String
    val doctorSubtitle: String
    val doctorRunAll: String
    val doctorNetworkCheck: String
    val doctorConfigCheck: String
    val doctorProxyCheck: String
    val doctorHostCheck: String
    val doctorPassed: String
    val doctorFailed: String
    val doctorWarning: String
    val doctorFixSuggestions: String

    // Connection Test
    val connectionTestTitle: String
    val connectionTestRunning: String
    val connectionTestSuccess: String
    val connectionTestFailed: String
    val connectionTestLatency: String

    // Host Confirm
    val hostEnableConfirmTitle: String
    val hostEnableConfirmMessage: String
    val hostDisableConfirmTitle: String
    val hostDisableConfirmMessage: String
    val hostCliNotInstalled: String
    val hostAppNotInstalled: String
    val hostAppNeedRestart: String

    // Common
    val commonSave: String
    val commonCancel: String
    val commonConfirm: String
    val commonDelete: String
    val commonEdit: String
    val commonClose: String
    val commonSuccess: String
    val commonError: String
    val commonCopied: String
}

object StringsZh : Strings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "Antigravity 全能桌面中枢与 BYOK 模型接入套件"

    override val navOverview = "运行概览"
    override val navModels = "模型管理"
    override val navActivity = "调用日志"
    override val navSettings = "应用设置"
    override val navDoctor = "全链路体检"

    override val overviewProxyCardTitle = "本地代理服务"
    override val overviewProxyRunning = "代理服务正在运行"
    override val overviewProxyStopped = "代理服务已停止"
    override val overviewProxyPort = "监听地址"
    override val overviewStartProxy = "启动代理"
    override val overviewStopProxy = "停止代理"
    override val overviewRestartProxy = "重启服务"
    override val overviewSubtitle = "统一管理本地代理、宿主接入与模型路由"
    override val overviewCopyAddress = "复制地址"
    override val overviewDiagnostics = "运行诊断"
    override val overviewProviderMetric = "自定义服务商"
    override val overviewModelMetric = "可用模型"
    override val overviewDisabledMetric = "隐藏官方模型"
    override val overviewHostSection = "宿主环境接入"
    override val overviewNotice = "切换宿主接入后，请重启对应应用使配置生效。"
    override val hostUpdateFailed = "宿主接入配置失败，请检查设置文件权限"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "接管 IDE 请求，通过 settings.json 注入本地代理并管理模型"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "接管桌面独立 App 请求，通过会话环境变量无损注入"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "接管终端 CLI 命令，管理用户级 CLOUD_CODE_URL 环境变量"

    override val hostStatusActive = "已接入代理"
    override val hostStatusInactive = "官方直连模式"
    override val hostStatusNotInstalled = "未检测到安装"
    override val hostEnable = "启用代理接入"
    override val hostDisable = "恢复官方默认"
    override val hostRestartNotice = "切换后请重启对应宿主应用以完全生效"
    override val hostLaunch = "打开应用"

    override val modelsOfficialTitle = "官方原生模型"
    override val modelsOfficialSubtitle = "管理 Antigravity 内置模型，可针对不需要的模型进行隐藏或禁用"
    override val modelsCustomTitle = "自定义 Provider 服务商"
    override val modelsAddProvider = "添加上游服务"
    override val modelsEditProvider = "编辑服务商"
    override val modelsDeleteProvider = "删除服务商"
    override val modelsTestConnection = "测试连通性"
    override val modelsFetchModels = "拉取模型列表"
    override val modelsNoProviders = "暂无配置的 Provider，点击右上角添加"
    override val modelsCompressionPolicy = "上下文压缩策略"
    override val modelsReasoningConfig = "思考推理档位"
    override val modelsSubtitle = "管理官方模型与自定义 BYOK 服务商"
    override val modelsOfficialTab = "官方原生"
    override val modelsCustomTab = "自定义服务"
    override val modelsCollapse = "收起"
    override val modelsExpand = "展开"
    override val modelsContext = "上下文"
    override val modelsVision = "视觉"
    override val modelsTools = "工具"
    override val modelsReasoning = "推理"
    override val modelsNoModels = "暂无模型"
    override val modelsTesting = "测试中…"
    override val modelsConnectionOk = "连通正常"
    override val modelsConnectionFailed = "连接失败"
    override val modelsRefreshOfficial = "刷新官方模型"
    override val modelsFetchingOfficial = "正在扫描探测语言服务并拉取官方模型…"

    override val activityTitle = "请求调用日志"
    override val activityFilterAll = "全部请求"
    override val activityFilterFailed = "仅看失败"
    override val activityClear = "清空日志"
    override val activityEmpty = "暂无请求日志"
    override val activityPassthrough = "官方透传"
    override val activityRouted = "自定义路由"
    override val activitySubtitle = "查看请求状态、路由来源与响应耗时"
    override val activitySearchPlaceholder = "搜索模型或 Provider"
    override val activityRecent = "最近日志"
    override val activityTotal = "总请求量"
    override val activityFailedTotal = "异常请求"
    override val activityAverage = "平均耗时"
    override val activityInMemory = "内存日志"
    override val activityHealthy = "运行正常"
    override val activityHasErrors = "存在错误"
    override val activityFallback = "备用路由"
    override val activityFallbackFailed = "备用路由失败"

    override val settingsTitle = "应用偏好与配置"
    override val settingsLanguage = "界面语言"
    override val settingsTheme = "外观主题"
    override val settingsThemeSystem = "跟随系统"
    override val settingsThemeLight = "浅色模式"
    override val settingsThemeDark = "深色模式"
    override val settingsPort = "本地代理默认端口"
    override val settingsStoragePath = "配置文件存储路径"
    override val settingsAbout = "关于 Antigravity Studio"
    override val settingsSubtitle = "管理语言、外观、代理端口与数据存储"
    override val settingsGeneral = "常规偏好"
    override val settingsNetwork = "网络代理"
    override val settingsData = "数据存储"
    override val settingsAboutSection = "关于应用"
    override val settingsLanguageDescription = "切换应用显示语言"
    override val settingsThemeDescription = "选择系统、浅色或深色外观"
    override val settingsPortDescription = "修改后会重启本地代理服务"
    override val settingsStorageDescription = "配置会以原子方式写入本地文件"
    override val settingsOpenDirectory = "打开目录"
    override val settingsAboutDescription = "Kotlin Multiplatform 与 Compose Desktop 驱动的本地模型接入工具。"
    override val settingsPortInvalid = "端口必须在 1024 到 65535 之间"
    override val settingsDirectoryOpenError = "目录打开失败"
    override val settingsVersion = "Antigravity Studio v1.0.0 · Kotlin Multiplatform & Compose Desktop"

    override val doctorTitle = "Doctor 全链路健康体检"
    override val doctorSubtitle = "一键诊断网络连通性、本地配置、宿主接管与代理服务健康度"
    override val doctorRunAll = "重新执行体检"
    override val doctorNetworkCheck = "上游网络连通性"
    override val doctorConfigCheck = "本地配置完整度"
    override val doctorProxyCheck = "本地代理监听状态"
    override val doctorHostCheck = "宿主环境接管一致性"
    override val doctorPassed = "正常"
    override val doctorFailed = "异常"
    override val doctorWarning = "警告"
    override val doctorFixSuggestions = "修复建议"

    override val connectionTestTitle = "连接测试"
    override val connectionTestRunning = "正在测试代理连接…"
    override val connectionTestSuccess = "代理连接正常"
    override val connectionTestFailed = "代理连接失败"
    override val connectionTestLatency = "延迟"

    override val hostEnableConfirmTitle = "启用代理接入"
    override val hostEnableConfirmMessage = "此操作将配置该宿主通过本地代理发送请求。确认继续？"
    override val hostDisableConfirmTitle = "恢复官方默认"
    override val hostDisableConfirmMessage = "此操作将恢复该宿主为官方直连模式，自定义模型将不可用。确认继续？"
    override val hostCliNotInstalled = "未检测到 agy CLI 安装，请先安装后重试"
    override val hostAppNotInstalled = "未检测到 Antigravity App 安装"
    override val hostAppNeedRestart = "配置已更新，需要重启 Antigravity App 以生效"

    override val commonSave = "保存配置"
    override val commonCancel = "取消"
    override val commonConfirm = "确认"
    override val commonDelete = "删除"
    override val commonEdit = "编辑"
    override val commonClose = "关闭"
    override val commonSuccess = "操作成功"
    override val commonError = "操作失败"
    override val commonCopied = "已复制到剪贴板"
}

object StringsEn : Strings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "The all-in-one desktop hub and BYOK model suite for Antigravity"

    override val navOverview = "Overview"
    override val navModels = "Models"
    override val navActivity = "Activity"
    override val navSettings = "Settings"
    override val navDoctor = "Doctor"

    override val overviewProxyCardTitle = "Local Proxy Server"
    override val overviewProxyRunning = "Proxy is running"
    override val overviewProxyStopped = "Proxy is stopped"
    override val overviewProxyPort = "Listening Address"
    override val overviewStartProxy = "Start Proxy"
    override val overviewStopProxy = "Stop Proxy"
    override val overviewRestartProxy = "Restart Server"
    override val overviewSubtitle = "Manage the local proxy, host integrations and model routes"
    override val overviewCopyAddress = "Copy address"
    override val overviewDiagnostics = "Run diagnostics"
    override val overviewProviderMetric = "Custom providers"
    override val overviewModelMetric = "Available models"
    override val overviewDisabledMetric = "Hidden official"
    override val overviewHostSection = "Host integrations"
    override val overviewNotice = "Restart the host application after changing an integration."
    override val hostUpdateFailed = "Host integration failed; check settings file permissions"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "Intercept IDE requests via settings.json cloudCodeUrl"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "Intercept App requests via session environment variable"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "Intercept CLI commands via user CLOUD_CODE_URL"

    override val hostStatusActive = "Proxy Active"
    override val hostStatusInactive = "Official Direct"
    override val hostStatusNotInstalled = "Not Detected"
    override val hostEnable = "Enable Proxy"
    override val hostDisable = "Restore Official"
    override val hostRestartNotice = "Please restart host application to take full effect"
    override val hostLaunch = "Launch Client"

    override val modelsOfficialTitle = "Official Models"
    override val modelsOfficialSubtitle = "Manage built-in Antigravity models, disable unwanted models"
    override val modelsCustomTitle = "Custom Model Providers"
    override val modelsAddProvider = "Add upstream service"
    override val modelsEditProvider = "Edit Provider"
    override val modelsDeleteProvider = "Delete Provider"
    override val modelsTestConnection = "Test Connection"
    override val modelsFetchModels = "Fetch Models"
    override val modelsNoProviders = "No providers configured. Click top right to add."
    override val modelsCompressionPolicy = "Compression Policy"
    override val modelsReasoningConfig = "Reasoning Config"
    override val modelsSubtitle = "Manage official models and custom BYOK providers"
    override val modelsOfficialTab = "Official"
    override val modelsCustomTab = "Custom services"
    override val modelsCollapse = "Collapse"
    override val modelsExpand = "Expand"
    override val modelsContext = "Context"
    override val modelsVision = "Vision"
    override val modelsTools = "Tools"
    override val modelsReasoning = "Reasoning"
    override val modelsNoModels = "No models"
    override val modelsTesting = "Testing…"
    override val modelsConnectionOk = "Connected"
    override val modelsConnectionFailed = "Connection failed"
    override val modelsRefreshOfficial = "Refresh Official Models"
    override val modelsFetchingOfficial = "Probing language server and fetching official models…"

    override val activityTitle = "Activity Logs"
    override val activityFilterAll = "All Requests"
    override val activityFilterFailed = "Failed Only"
    override val activityClear = "Clear Logs"
    override val activityEmpty = "No activity recorded"
    override val activityPassthrough = "Official Passthrough"
    override val activityRouted = "Custom Route"
    override val activitySubtitle = "Inspect request status, route source and response latency"
    override val activitySearchPlaceholder = "Search model or provider"
    override val activityRecent = "Recent logs"
    override val activityTotal = "Total requests"
    override val activityFailedTotal = "Failed requests"
    override val activityAverage = "Average latency"
    override val activityInMemory = "In-memory log"
    override val activityHealthy = "Healthy"
    override val activityHasErrors = "Errors found"
    override val activityFallback = "Fallback route"
    override val activityFallbackFailed = "Fallback failed"

    override val settingsTitle = "Preferences & Settings"
    override val settingsLanguage = "Language"
    override val settingsTheme = "Appearance Theme"
    override val settingsThemeSystem = "System Default"
    override val settingsThemeLight = "Light Theme"
    override val settingsThemeDark = "Dark Theme"
    override val settingsPort = "Proxy Default Port"
    override val settingsStoragePath = "Config File Location"
    override val settingsAbout = "About Antigravity Studio"
    override val settingsSubtitle = "Manage language, appearance, proxy port and storage"
    override val settingsGeneral = "General"
    override val settingsNetwork = "Network proxy"
    override val settingsData = "Data storage"
    override val settingsAboutSection = "About app"
    override val settingsLanguageDescription = "Choose the application display language"
    override val settingsThemeDescription = "Use the system, light or dark appearance"
    override val settingsPortDescription = "The local proxy restarts after saving"
    override val settingsStorageDescription = "Configuration is written atomically"
    override val settingsOpenDirectory = "Open directory"
    override val settingsAboutDescription = "A local model access tool built with Kotlin Multiplatform and Compose Desktop."
    override val settingsPortInvalid = "Port must be between 1024 and 65535"
    override val settingsDirectoryOpenError = "Unable to open directory"
    override val settingsVersion = "Antigravity Studio v1.0.0 · Kotlin Multiplatform & Compose Desktop"

    override val doctorTitle = "Doctor Health Diagnostics"
    override val doctorSubtitle = "Full-stack diagnostics for network, configs, host and proxy"
    override val doctorRunAll = "Run All Checks"
    override val doctorNetworkCheck = "Upstream Network"
    override val doctorConfigCheck = "Configuration Integrity"
    override val doctorProxyCheck = "Local Proxy Status"
    override val doctorHostCheck = "Host Environment State"
    override val doctorPassed = "Passed"
    override val doctorFailed = "Failed"
    override val doctorWarning = "Warning"
    override val doctorFixSuggestions = "Fix Suggestions"

    override val connectionTestTitle = "Connection Test"
    override val connectionTestRunning = "Testing proxy connection…"
    override val connectionTestSuccess = "Proxy connection OK"
    override val connectionTestFailed = "Proxy connection failed"
    override val connectionTestLatency = "Latency"

    override val hostEnableConfirmTitle = "Enable Proxy Integration"
    override val hostEnableConfirmMessage = "This will configure the host to route requests through the local proxy. Continue?"
    override val hostDisableConfirmTitle = "Restore Official Default"
    override val hostDisableConfirmMessage = "This will restore official direct connection; custom models will be unavailable. Continue?"
    override val hostCliNotInstalled = "agy CLI not detected; please install it first"
    override val hostAppNotInstalled = "Antigravity App not detected"
    override val hostAppNeedRestart = "Configuration updated; restart Antigravity App to apply"

    override val commonSave = "Save"
    override val commonCancel = "Cancel"
    override val commonConfirm = "Confirm"
    override val commonDelete = "Delete"
    override val commonEdit = "Edit"
    override val commonClose = "Close"
    override val commonSuccess = "Success"
    override val commonError = "Error"
    override val commonCopied = "Copied to clipboard"
}
