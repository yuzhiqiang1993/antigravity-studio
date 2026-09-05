package com.yuzhiqiang.antigravity.i18n.sections

interface CoreStrings {
    val appName: String
    val appSubtitle: String

    // Navigation & Sidebar
    val navOverview: String
    val navAccounts: String
    val navModels: String
    val navActivity: String
    val navSettings: String
    val navDoctor: String
    val sidebarCollapse: String
    val sidebarExpand: String

    // Accounts Screen
    val accountsTitle: String
    val accountsSubtitle: String
    val accountsAddAccount: String
    val accountsAddViaBrowser: String
    val accountsAddViaToken: String
    val accountsActiveInIde: String
    val accountsSetActive: String
    val accountsDelete: String
    val accountsRefreshToken: String
    val accountsCopyToken: String
    val accountsEmptyState: String
    val accountsEmptyDesc: String
    val accountsTokenExpiringSoon: String
    val accountsTokenExpired: String
    val accountsTokenHealthy: String
    val accountsExpiresIn: String
    val accountsAddDialogTitle: String
    val accountsAddDialogBrowserDesc: String
    val accountsAddDialogTokenDesc: String
    val accountsAddDialogTokenPlaceholder: String
    val accountsWaitingBrowserAuth: String
    val accountsAuthSuccess: String
    val accountsAuthFailed: String
    val accountsCopiedEmail: String
    fun accountsEmailTooltip(email: String): String


    // Overview & Proxy Card
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
    val overviewCopiedProxyAddress: String
    val hostUpdateFailed: String

    // Host Titles & Descriptions
    val hostIdeTitle: String
    val hostIdeDesc: String
    val hostAppTitle: String
    val hostAppDesc: String
    val hostCliTitle: String
    val hostCliDesc: String

    // Host Status Labels
    val hostStatusActive: String
    val hostStatusInactive: String
    val hostStatusNotInstalled: String
    val hostStatusReady: String
    val hostStatusInstalled: String
    val hostStatusRunning: String
    val hostStatusNeedsUpdate: String
    val hostStatusMismatch: String

    // Host Actions
    val hostEnable: String
    val hostDisable: String
    val hostRestartNotice: String
    val hostLaunch: String
    val hostRestart: String
    val hostUpdateAction: String
    val hostConfigurePath: String
    val hostForceReset: String
    fun hostCustomPath(path: String): String
    val hostProxyMode: String

    // Host Detail Status Text
    fun hostIdePortMismatch(endpoint: String): String
    val hostIdeRunning: String
    val hostIdeRunningAndConfigured: String
    val hostIdeReady: String
    val hostIdeNotDetected: String
    fun hostIdePendingUpdate(port: Int): String
    val hostIdeActiveDesc: String
    val hostOfficialDirectDesc: String

    // Ecosystem & Cockpit Plugin Promotion
    val hostIdeCockpitBannerTip: String
    val hostIdeCockpitInstallBtn: String
    val ecosystemCockpitTitle: String
    val ecosystemCockpitSubtitle: String
    val ecosystemCockpitFeature1: String
    val ecosystemCockpitFeature2: String
    val ecosystemCockpitFeature3: String
    val ecosystemCockpitGetPluginBtn: String
    val ecosystemCockpitOpenVsxBtn: String
    val ecosystemCockpitWebsiteBtn: String

    fun hostAppPortMismatch(endpoint: String): String
    val hostAppRunning: String
    val hostAppRunningAndConfigured: String
    val hostAppReady: String
    val hostAppNotDetected: String
    fun hostAppPendingUpdate(port: Int): String
    val hostAppActiveDesc: String
    val hostAppOfficialDirectDesc: String

    fun hostCliPortMismatch(endpoint: String): String
    val hostCliInstalledDesc: String
    val hostCliNotDetected: String
    fun hostCliPendingUpdate(port: Int): String
    val hostCliActiveDesc: String
    val hostCliOfficialDirectDesc: String
    val hostCopyCliLaunchCommand: String
    val hostCliLaunchCommandCopied: String
    val hostCliLaunchCommandRequiresProxy: String
    val hostCliLaunchCommandRequiresIntegration: String

    // Host Confirm Dialogs & Notices
    val hostIdeUpdateConfirmTitle: String
    fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int): String
    fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int): String
    val hostIdeEnableConfirmTitle: String
    val hostIdeEnableConfirmMessageRunning: String
    val hostIdeEnableConfirmMessageStopped: String
    val hostIdeDisableConfirmTitle: String
    val hostIdeDisableConfirmMessageRunning: String
    val hostIdeDisableConfirmMessageStopped: String
    val hostIdeUpdatedAndRestarted: String
    val hostIdeEnabledAndRestarted: String
    val hostIdeEnabledPendingStart: String
    val hostIdeConfigUpdatedRestartFailed: String
    val hostIdeEnableFailed: String
    val hostIdeRestoredAndRestarted: String
    val hostIdeRestored: String
    val hostIdeDisableFailed: String

    val hostAppUpdateConfirmTitle: String
    fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int): String
    fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int): String
    val hostAppEnableConfirmTitle: String
    val hostAppEnableConfirmMessageRunning: String
    val hostAppEnableConfirmMessageStopped: String
    val hostAppDisableConfirmTitle: String
    val hostAppDisableConfirmMessageRunning: String
    val hostAppDisableConfirmMessageStopped: String
    val hostAppUpdatedAndRestarted: String
    val hostAppEnabledAndRestarted: String
    val hostAppEnabledPendingStart: String
    val hostAppConfigUpdatedRestartFailed: String
    val hostAppEnableFailed: String
    val hostAppRestoredAndRestarted: String
    val hostAppRestored: String
    val hostAppDisableFailed: String
    val hostAppNotInstalled: String

    val hostCliUpdateConfirmTitle: String
    fun hostCliUpdateConfirmMessage(endpoint: String, port: Int): String
    val hostCliEnableConfirmTitle: String
    val hostCliEnableConfirmMessage: String
    val hostCliDisableConfirmTitle: String
    val hostCliDisableConfirmMessage: String
    val hostCliEnabledNotice: String
    val hostCliDisabledNotice: String
    val hostCliEnableFailed: String
    val hostCliDisableFailed: String
    val hostCliNotInstalled: String

    fun hostStartProxyFirstNotice(hostName: String): String
    fun hostForceResetConfirmTitle(hostName: String): String
    fun hostForceResetConfirmMessage(hostName: String): String
    fun hostForceResetSuccess(hostName: String): String
    fun hostLaunchResetConfirmTitle(hostName: String): String
    fun hostLaunchResetConfirmMessage(hostName: String): String
    fun hostExternalEnvironmentNotice(endpoint: String): String
    val hostMigrateSharedEnvironment: String
    val hostMigrateSharedEnvironmentConfirmMessage: String
    val hostMigrateSharedEnvironmentSuccess: String
    fun hostRestartConfirmTitle(hostName: String): String
    fun hostRestartConfirmMessage(hostName: String): String
    fun hostRestartSuccess(hostName: String): String
    fun hostRestartFailed(hostName: String): String
    fun hostLaunchSuccess(hostName: String): String
    fun hostLaunchFailed(hostName: String): String
    fun hostLaunchProxyNotRunning(hostName: String): String

    // Custom Host Path Dialog
    fun hostPathDialogTitle(hostTitle: String): String
    val hostPathDialogDesc: String
    val hostPathInputLabel: String
    val hostPathStatusValid: String
    val hostPathStatusNotFound: String
    val hostPathStatusEmpty: String
    val hostPathResetDefault: String
    val hostPathSavedCustom: String
    val hostPathResetNotice: String
    val hostPathBrowse: String
    val hostPathSuggestedTitle: String
    val hostPathSelectFile: String

    val onboardingTitle: String
    val onboardingSubtitle: String
    val onboardingSkip: String
    val onboardingPrev: String
    val onboardingNext: String
    val onboardingFinish: String
    val onboardingReopen: String
    val onboardingReopenDesc: String
    val onboardingCompletedToast: String

    val tourStep1Title: String
    val tourStep1Desc: String
    val tourStep2Title: String
    val tourStep2Desc: String
    val tourStep3Title: String
    val tourStep3Desc: String
    val tourStep4Title: String
    val tourStep4Desc: String
    val tourStep5Title: String
    val tourStep5Desc: String
    val tourStep6Title: String
    val tourStep6Desc: String
    val tourStep7Title: String
    val tourStep7Desc: String
    val tourStep8Title: String
    val tourStep8Desc: String
    val tourStep9Title: String
    val tourStep9Desc: String
    val tourStep10Title: String
    val tourStep10Desc: String
    val tourStep11Title: String
    val tourStep11Desc: String
    val tourStep12Title: String
    val tourStep12Desc: String
    val tourStep13Title: String
    val tourStep13Desc: String
    val tourStep14Title: String
    val tourStep14Desc: String
}

object CoreStringsZh : CoreStrings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "Antigravity 账号管理、本地代理与自定义模型工具"

    override val navOverview = "运行概览"
    override val navAccounts = "账号配额"
    override val navModels = "模型管理"
    override val navActivity = "调用日志"
    override val navSettings = "应用设置"
    override val navDoctor = "健康诊断"
    override val sidebarCollapse = "折叠侧边栏"
    override val sidebarExpand = "展开侧边栏"

    override val accountsTitle = "账号与配额管理"
    override val accountsSubtitle = "集中管理多个 Google 账号，实时监控配额余量并支持无缝一键切换"
    override val accountsAddAccount = "添加账号"
    override val accountsAddViaBrowser = "Google 浏览器登录"
    override val accountsAddViaToken = "手动输入 Token"
    override val accountsActiveInIde = "当前 IDE 生效账号"
    override val accountsSetActive = "设为当前账号"
    override val accountsDelete = "删除账号"
    override val accountsRefreshToken = "刷新凭据"
    override val accountsCopyToken = "复制凭据"
    override val accountsEmptyState = "暂无托管账号"
    override val accountsEmptyDesc = "点击右上角「添加账号」以登录 Google 账号或导入 Refresh Token"
    override val accountsTokenExpiringSoon = "凭据即将到期"
    override val accountsTokenExpired = "凭据已过期"
    override val accountsTokenHealthy = "有效"
    override val accountsExpiresIn = "后过期"
    override val accountsAddDialogTitle = "添加 Google 账号"
    override val accountsAddDialogBrowserDesc = "将在系统默认浏览器中打开 Google 授权页面，授权后自动返回并录入。"
    override val accountsAddDialogTokenDesc = "直接粘贴 Google OAuth Refresh Token 字符串，系统将自动换取并拉取用户资料。"
    override val accountsAddDialogTokenPlaceholder = "粘贴 Refresh Token (例如 1//0g...)"
    override val accountsWaitingBrowserAuth = "正在等待浏览器授权回调..."
    override val accountsAuthSuccess = "账号授权成功！"
    override val accountsAuthFailed = "账号授权失败"
    override val accountsCopiedEmail = "已复制账号邮箱"
    override fun accountsEmailTooltip(email: String) = "账号邮箱: $email (点击复制)"


    override val overviewProxyCardTitle = "本地代理服务"
    override val overviewProxyRunning = "运行中"
    override val overviewProxyStopped = "已停止"
    override val overviewProxyPort = "服务地址"
    override val overviewStartProxy = "启动代理"
    override val overviewStopProxy = "停止代理"
    override val overviewRestartProxy = "重启服务"
    override val overviewSubtitle = "统一管理本地代理、应用客户端接入与模型路由"
    override val overviewCopyAddress = "复制地址"
    override val overviewDiagnostics = "健康诊断"
    override val overviewProviderMetric = "自定义服务商"
    override val overviewModelMetric = "可用模型"
    override val overviewDisabledMetric = "隐藏官方模型"
    override val overviewHostSection = "应用客户端接入"
    override val overviewNotice = "切换接入模式后，请重启对应应用使配置生效。"
    override val overviewCopiedProxyAddress = "已复制代理地址"
    override val hostUpdateFailed = "应用接入配置失败，请检查配置文件权限"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "接管 IDE 模型请求，通过 settings.json 注入本地代理并扩展模型能力"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "接管桌面独立 App 模型请求，通过环境变量注入本地代理"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "接管终端 CLI 命令，管理 CLOUD_CODE_URL 环境变量"

    override val hostStatusActive = "已接入"
    override val hostStatusInactive = "官方直连"
    override val hostStatusNotInstalled = "未安装"
    override val hostStatusReady = "已就绪"
    override val hostStatusInstalled = "已安装"
    override val hostStatusRunning = "运行中"
    override val hostStatusNeedsUpdate = "配置待更新"
    override val hostStatusMismatch = "代理端口不匹配"

    override val hostEnable = "接入代理"
    override val hostDisable = "恢复官方直连"
    override val hostRestartNotice = "切换后请重启对应应用以使配置完全生效"
    override val hostLaunch = "打开"
    override val hostRestart = "重启"
    override val hostUpdateAction = "更新配置"
    override val hostConfigurePath = "配置路径"
    override val hostForceReset = "重置为官方模式"
    override fun hostCustomPath(path: String) = "自定义路径: $path"
    override val hostProxyMode = "代理模式"

    override fun hostIdePortMismatch(endpoint: String) = "检测到代理配置与当前端口不一致（$endpoint）"
    override val hostIdeRunning = "Antigravity IDE 正在运行"
    override val hostIdeRunningAndConfigured = "Antigravity IDE 正在运行并已配置"
    override val hostIdeReady = "Antigravity IDE 已安装"
    override val hostIdeNotDetected = "未检测到 Antigravity IDE 安装目录"
    override fun hostIdePendingUpdate(port: Int) = "代理配置待更新为 http://127.0.0.1:$port"
    override val hostIdeActiveDesc = "已通过 settings.json 接入代理"
    override val hostOfficialDirectDesc = "当前处于官方直连模式"

    // Ecosystem & Cockpit Plugin Promotion
    override val hostIdeCockpitBannerTip = "推荐搭配 Cockpit 插件：在 IDE 状态栏实时看配额与一键热切号"
    override val hostIdeCockpitInstallBtn = "安装插件 ↗"
    override val ecosystemCockpitTitle = "搭配 Antigravity Cockpit 插件，打造 IDE 沉浸式开发体验"
    override val ecosystemCockpitSubtitle = "Antigravity IDE 专属驾驶舱扩展：在编辑器状态栏实时监控配额与 Token 用量、支持一键无感热切号，并与 Studio 本地代理无缝握手协同。"
    override val ecosystemCockpitFeature1 = "状态栏实时配额"
    override val ecosystemCockpitFeature2 = "IDE 内快捷热切号"
    override val ecosystemCockpitFeature3 = "本地代理无缝协同"
    override val ecosystemCockpitGetPluginBtn = "获取 VS Code 插件 ↗"
    override val ecosystemCockpitOpenVsxBtn = "Open VSX 市场 ↗"
    override val ecosystemCockpitWebsiteBtn = "官方网站 ↗"

    override fun hostAppPortMismatch(endpoint: String) = "Studio 专属启动地址与当前端口不一致（$endpoint）"
    override val hostAppRunning = "Antigravity App 正在运行"
    override val hostAppRunningAndConfigured = "App 正在运行；已保存 Studio 专属启动配置"
    override val hostAppReady = "Antigravity App 已安装"
    override val hostAppNotDetected = "未检测到 Antigravity App 安装"
    override fun hostAppPendingUpdate(port: Int) = "Studio 后续启动地址待更新为 http://127.0.0.1:$port"
    override val hostAppActiveDesc = "Studio 专属启动已启用；仅从 Studio 后续启动 App 时接入代理"
    override val hostAppOfficialDirectDesc = "Studio 专属启动未启用；不改变已运行实例或外部环境"

    override fun hostCliPortMismatch(endpoint: String) = "CLI 专属启动地址与当前端口不一致（$endpoint）"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) 已安装"
    override val hostCliNotDetected = "未检测到 agy CLI 可执行文件"
    override fun hostCliPendingUpdate(port: Int) = "CLI 后续启动地址待更新为 http://127.0.0.1:$port"
    override val hostCliActiveDesc = "Studio 专属启动已启用；请复制命令启动 CLI，仅影响该次启动"
    override val hostCliOfficialDirectDesc = "CLI 专属启动未启用；不修改终端或外部环境"
    override val hostCopyCliLaunchCommand = "复制启动命令"
    override val hostCliLaunchCommandCopied = "CLI 启动命令已复制；请在终端执行，仅影响该次启动"
    override val hostCliLaunchCommandRequiresProxy = "请先启动本地代理，再复制 CLI 启动命令"
    override val hostCliLaunchCommandRequiresIntegration = "请先启用 CLI 的 Studio 专属启动配置"

    override val hostIdeUpdateConfirmTitle = "更新 Antigravity IDE 代理配置"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "检测到 IDE 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。更新后将自动重启 IDE 使配置生效。是否继续？"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "检测到 IDE 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。是否更新为当前代理端口？"

    override val hostIdeEnableConfirmTitle = "确认启用代理模式"
    override val hostIdeEnableConfirmMessageRunning =
        "启用代理模式后，Antigravity IDE 会接入配置的模型并自动重启使配置生效。是否继续？"
    override val hostIdeEnableConfirmMessageStopped = "启用代理模式将使 Antigravity IDE 在启动时连接本地代理。是否继续？"
    override val hostIdeDisableConfirmTitle = "确认停用代理接入"
    override val hostIdeDisableConfirmMessageRunning =
        "将停用 Antigravity IDE 的代理接入并重启恢复官方直连模式。是否继续？"
    override val hostIdeDisableConfirmMessageStopped = "将停用 Antigravity IDE 的代理接入，恢复官方直连模式。是否继续？"
    override val hostIdeUpdatedAndRestarted = "Antigravity IDE 代理配置已更新并完成重启"
    override val hostIdeEnabledAndRestarted = "Antigravity IDE 已启用代理模式并完成重启"
    override val hostIdeEnabledPendingStart = "Antigravity IDE 已启用代理模式，启动后生效"
    override val hostIdeConfigUpdatedRestartFailed = "Antigravity IDE 配置已更新，但自动重启失败"
    override val hostIdeEnableFailed = "Antigravity IDE 代理接入配置失败"
    override val hostIdeRestoredAndRestarted = "Antigravity IDE 已恢复官方直连并完成重启"
    override val hostIdeRestored = "Antigravity IDE 已恢复官方直连"
    override val hostIdeDisableFailed = "Antigravity IDE 停用代理接入失败"

    override val hostAppUpdateConfirmTitle = "更新 App 的 Studio 专属启动配置"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "App 专属启动地址（$endpoint）与代理端口（$port）不匹配。更新仅影响后续从 Studio 启动的 App，不会重启或改变当前实例。是否继续？"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        hostAppUpdateConfirmMessageRunning(endpoint, port)

    override val hostAppEnableConfirmTitle = "启用 App 的 Studio 专属启动"
    override val hostAppEnableConfirmMessageRunning =
        "启用后仅从 Studio 后续启动的 App 接入代理，不改变已运行实例。是否继续？"
    override val hostAppEnableConfirmMessageStopped = hostAppEnableConfirmMessageRunning
    override val hostAppDisableConfirmTitle = "停用 App 的 Studio 专属启动"
    override val hostAppDisableConfirmMessageRunning =
        "将停用 App 专属启动配置，后续从 Studio 启动不再注入代理，不改变已运行实例。是否继续？"
    override val hostAppDisableConfirmMessageStopped = hostAppDisableConfirmMessageRunning
    override val hostAppUpdatedAndRestarted = "App 专属启动配置已更新；仅影响后续从 Studio 启动"
    override val hostAppEnabledAndRestarted = "App 专属启动已启用；仅影响后续从 Studio 启动"
    override val hostAppEnabledPendingStart = "App 专属启动已启用；请从 Studio 启动 App"
    override val hostAppConfigUpdatedRestartFailed = "App 专属启动配置已更新，但启动失败"
    override val hostAppEnableFailed = "App 专属启动配置失败"
    override val hostAppRestoredAndRestarted = "App 专属启动已停用；已运行实例不受影响"
    override val hostAppRestored = "App 专属启动已停用；已运行实例不受影响"
    override val hostAppDisableFailed = "停用 App 专属启动失败"
    override val hostAppNotInstalled = "未检测到 Antigravity App 安装"

    override val hostCliUpdateConfirmTitle = "更新 CLI 的 Studio 专属启动配置"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "CLI 专属启动地址（$endpoint）与代理端口（$port）不匹配。更新后请重新复制启动命令，仅影响使用新命令的后续启动。是否继续？"

    override val hostCliEnableConfirmTitle = "启用 CLI 的 Studio 专属启动"
    override val hostCliEnableConfirmMessage =
        "启用后请复制启动命令在终端执行，仅该次启动接入代理，不修改终端配置。是否继续？"
    override val hostCliDisableConfirmTitle = "停用 CLI 的 Studio 专属启动"
    override val hostCliDisableConfirmMessage =
        "将停用 CLI 专属启动配置，不再生成代理启动命令；已运行进程和已复制的命令不受影响。是否继续？"
    override val hostCliEnabledNotice = "CLI 专属启动已启用；请复制启动命令在终端执行"
    override val hostCliDisabledNotice = "CLI 专属启动已停用；已运行进程和已复制命令不受影响"
    override val hostCliEnableFailed = "CLI 专属启动配置失败"
    override val hostCliDisableFailed = "停用 CLI 专属启动失败"
    override val hostCliNotInstalled = "未检测到 agy CLI 安装"

    override fun hostStartProxyFirstNotice(hostName: String) = "请先启动本地代理服务，再接入 $hostName"
    override fun hostForceResetConfirmTitle(hostName: String) = "强制重置 $hostName 为官方直连"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "此操作将清除 $hostName 的所有代理配置与环境变量，恢复为干净的官方直连模式。若应用正在运行将自动重启生效。是否确认重置？"

    override fun hostForceResetSuccess(hostName: String) = "$hostName 已强制重置为官方直连模式"
    override val hostMigrateSharedEnvironment = "清理旧共享接入"
    override val hostMigrateSharedEnvironmentConfirmMessage = "此操作独立清理旧版 Studio 共享接入。仅当旧收据与当前环境值匹配时恢复旧环境，否则保留外部环境；不会改变 App / CLI 专属启动开关。已运行进程不会即时生效。是否继续？"
    override val hostMigrateSharedEnvironmentSuccess = "旧共享接入清理完成；外部环境与专属启动开关保持不变，已运行进程不受影响"
    override fun hostLaunchResetConfirmTitle(hostName: String) = "重置 $hostName 的 Studio 专属启动"
    override fun hostLaunchResetConfirmMessage(hostName: String) =
        "仅清除 $hostName 的 Studio 专属启动设置，不修改共享或外部环境。已运行进程和已复制的 CLI 命令不受影响。是否继续？"
    override fun hostExternalEnvironmentNotice(endpoint: String) =
        "检测到共享环境中的 CLOUD_CODE_URL=$endpoint；非 Studio 专属启动的 App / CLI 可能仍受该外部环境控制。本地开关不会覆盖此设置。"
    override fun hostRestartConfirmTitle(hostName: String) = "确认重启 $hostName"
    override fun hostRestartConfirmMessage(hostName: String) =
        "确定要重启 $hostName 吗？重启将关闭当前运行中的实例并重新打开。是否继续？"

    override fun hostRestartSuccess(hostName: String) = "已重启 $hostName"
    override fun hostRestartFailed(hostName: String) = "重启 $hostName 失败"
    override fun hostLaunchSuccess(hostName: String) = "已打开 $hostName"
    override fun hostLaunchFailed(hostName: String) = "打开 $hostName 失败"
    override fun hostLaunchProxyNotRunning(hostName: String) = "当前 $hostName 已接入代理，请先启动本地代理服务"

    override fun hostPathDialogTitle(hostTitle: String) = "配置 $hostTitle 路径"
    override val hostPathDialogDesc =
        "未检测到默认安装时，可在此指定安装目录（如 .app 目录、安装文件夹）或主可执行文件的绝对路径。"
    override val hostPathInputLabel = "安装目录或可执行文件路径"
    override val hostPathStatusValid = "路径已检测到并存在于文件系统中"
    override val hostPathStatusNotFound = "该路径在当前文件系统中不存在，请确认路径无误"
    override val hostPathStatusEmpty = "留空保存将清除自定义配置，恢复为自动检测。"
    override val hostPathResetDefault = "重置为默认"
    override val hostPathSavedCustom = "已保存自定义路径并重新检测"
    override val hostPathResetNotice = "已重置为默认自动检测路径"
    override val hostPathBrowse = "选择路径 / 浏览..."
    override val hostPathSuggestedTitle = "推荐 / 发现的候选路径"
    override val hostPathSelectFile = "浏览"

    override val onboardingTitle = "快速上手指引"
    override val onboardingSubtitle = "带你快速熟悉各个页面的具体功能与使用技巧"
    override val onboardingSkip = "跳过"
    override val onboardingPrev = "上一步"
    override val onboardingNext = "下一步"
    override val onboardingFinish = "开始使用 Studio"
    override val onboardingReopen = "新手指引"
    override val onboardingReopenDesc = "重新打开向导，回顾各个页面的具体功能与操作技巧"
    override val onboardingCompletedToast = "新手指引已完成，祝你使用愉快！"

    override val tourStep1Title = "运行概览"
    override val tourStep1Desc = "这里是首页中枢，能一眼看到本地代理有没有正常跑起来，以及你的 IDE、App 等工具是否已经成功接管。"

    override val tourStep2Title = "账号与配额"
    override val tourStep2Desc = "在这里添加和管理你的多个账号，随时查看各个模型的剩余额度和恢复倒计时，还能随时换号使用。"

    override val tourStep3Title = "模型管理"
    override val tourStep3Desc = "如果你有自己的大模型 API，可以在这里接进来并直接加入到宿主模型列表中，还能根据需要自由调整上下文压缩策略。"

    override val tourStep4Title = "用量统计"
    override val tourStep4Desc = "全面统计各模型的 Token 消耗量、调用频次与费用预估，支持按天/按小时走势分析及多端来源过滤。"

    override val tourStep5Title = "调用日志"
    override val tourStep5Desc = "IDE 发出的每一笔代码补全和问答请求都会记录在这里，请求耗时、用了多少 Token、有没有报错一查便知。"

    override val tourStep6Title = "应用设置"
    override val tourStep6Desc = "在这里修改代理端口、配置公司网络代理、切换主题颜色和语言。如果忘了怎么用，随时能在这重看新手指引。"

    override val tourStep7Title = "本地代理服务"
    override val tourStep7Desc = "Studio 会在本地启动一个代理中转服务。你可以在这启动或停止服务、查看实时延迟，遇到网络问题点健康诊断就能一键体检。"

    override val tourStep8Title = "客户端一键接入"
    override val tourStep8Desc = "系统会自动扫描你电脑上装好的 IDE 和客户端。直接点「接入代理」，就会自动配置好网络，不用你手动去改端口。"

    override val tourStep9Title = "添加与管理账号"
    override val tourStep9Desc = "点「添加账号」可以用浏览器登录或直接粘贴 Token。上方还支持一键按剩余额度排序、隐藏邮箱防窥，以及把账号配置导出备份。"

    override val tourStep10Title = "接入第三方服务与压缩策略"
    override val tourStep10Desc = "点「添加提供商」就能接入你自备的模型服务，直接加入到宿主的模型列表中使用。还可以自由调整上下文压缩策略，灵活掌控上下文长度。"

    override val tourStep11Title = "费用预估与用量走势"
    override val tourStep11Desc = "顶部可快速切换时间范围与筛选模型/来源。下方看板清晰展示 Token 五维分布、每日消耗走势图以及热门模型消耗排行。"

    override val tourStep12Title = "实时请求审计流"
    override val tourStep12Desc = "这里会按时间列出所有请求。你可以按关键词搜索或按状态码过滤，点开就能查看具体的耗时与数据流，排查报错非常方便。"

    override val tourStep13Title = "端口、网络与主题设置"
    override val tourStep13Desc = "如果默认端口被占用了可以在这修改，公司需要挂上游代理也可以在这填。下面还准备了多种深浅色主题，挑一个你喜欢的风格。"

    override val tourStep14Title = "检查更新与重看指引"
    override val tourStep14Desc = "在这里可以检查最新版本，也能直接打开配置文件夹。如果你以后想重温功能用法，随时点击「新手指引」卡片就能重新打开！"
}

object CoreStringsEn : CoreStrings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "Account management, local proxy, and custom model tools for Antigravity"

    override val navOverview = "Overview"
    override val navAccounts = "Accounts"
    override val navModels = "Models"
    override val navActivity = "Activity"
    override val navSettings = "Settings"
    override val navDoctor = "Doctor"
    override val sidebarCollapse = "Collapse sidebar"
    override val sidebarExpand = "Expand sidebar"

    override val accountsTitle = "Accounts & Quota"
    override val accountsSubtitle = "Manage multiple accounts, monitor AI quotas, and switch seamlessly"
    override val accountsAddAccount = "Add Account"
    override val accountsAddViaBrowser = "Google Sign In"
    override val accountsAddViaToken = "Manual Token"
    override val accountsActiveInIde = "Active in IDE"
    override val accountsSetActive = "Set Active"
    override val accountsDelete = "Delete"
    override val accountsRefreshToken = "Refresh Token"
    override val accountsCopyToken = "Copy Token"
    override val accountsEmptyState = "No accounts configured"
    override val accountsEmptyDesc = "Click 'Add Account' above to sign in or import a Refresh Token"
    override val accountsTokenExpiringSoon = "Expiring soon"
    override val accountsTokenExpired = "Expired"
    override val accountsTokenHealthy = "Active"
    override val accountsExpiresIn = "expires in"
    override val accountsAddDialogTitle = "Add Google Account"
    override val accountsAddDialogBrowserDesc =
        "Opens Google authorization in your default browser and automatically captures the token."
    override val accountsAddDialogTokenDesc =
        "Paste a Google OAuth Refresh Token. Studio will fetch user profile and tokens automatically."
    override val accountsAddDialogTokenPlaceholder = "Paste Refresh Token (e.g. 1//0g...)"
    override val accountsWaitingBrowserAuth = "Waiting for browser authorization..."
    override val accountsAuthSuccess = "Account authorized successfully!"
    override val accountsAuthFailed = "Authorization failed"
    override val accountsCopiedEmail = "Account email copied to clipboard"
    override fun accountsEmailTooltip(email: String) = "Account email: $email (Click to copy)"


    override val overviewProxyCardTitle = "Local Proxy Server"
    override val overviewProxyRunning = "Running"
    override val overviewProxyStopped = "Stopped"
    override val overviewProxyPort = "Listening Address"
    override val overviewStartProxy = "Start Proxy"
    override val overviewStopProxy = "Stop Proxy"
    override val overviewRestartProxy = "Restart Server"
    override val overviewSubtitle = "Manage the local proxy, host integrations and model routes"
    override val overviewCopyAddress = "Copy address"
    override val overviewDiagnostics = "Run Diagnostics"
    override val overviewProviderMetric = "Custom providers"
    override val overviewModelMetric = "Available models"
    override val overviewDisabledMetric = "Hidden official"
    override val overviewHostSection = "Host integrations"
    override val overviewNotice = "Restart the host application after changing an integration."
    override val overviewCopiedProxyAddress = "Proxy address copied to clipboard"
    override val hostUpdateFailed = "Host integration failed; check settings file permissions"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "Intercept IDE requests via settings.json cloudCodeUrl"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "Intercept App requests via session environment variable"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "Intercept CLI commands via user CLOUD_CODE_URL"

    override val hostStatusActive = "Active"
    override val hostStatusInactive = "Official Direct"
    override val hostStatusNotInstalled = "Not Detected"
    override val hostStatusReady = "Ready"
    override val hostStatusInstalled = "Installed"
    override val hostStatusRunning = "Running"
    override val hostStatusNeedsUpdate = "Needs Update"
    override val hostStatusMismatch = "Port Mismatch"

    override val hostEnable = "Enable Proxy"
    override val hostDisable = "Restore Official"
    override val hostRestartNotice = "Please restart host application to take full effect"
    override val hostLaunch = "Launch"
    override val hostRestart = "Restart"
    override val hostUpdateAction = "Update Config"
    override val hostConfigurePath = "Configure Path"
    override val hostForceReset = "Reset to Official"
    override fun hostCustomPath(path: String) = "Custom path: $path"
    override val hostProxyMode = "Proxy Mode"

    override fun hostIdePortMismatch(endpoint: String) = "Proxy config differs from current port ($endpoint)"
    override val hostIdeRunning = "Antigravity IDE is running"
    override val hostIdeRunningAndConfigured = "Antigravity IDE is running and configured"
    override val hostIdeReady = "Antigravity IDE is installed"
    override val hostIdeNotDetected = "Antigravity IDE installation not detected"
    override fun hostIdePendingUpdate(port: Int) = "Proxy config pending update to http://127.0.0.1:$port"
    override val hostIdeActiveDesc = "settings.json proxy integration active"
    override val hostOfficialDirectDesc = "Currently using official direct mode"

    // Ecosystem & Cockpit Plugin Promotion
    override val hostIdeCockpitBannerTip = "Recommended with Cockpit: Status bar quota & 1-click account switch in IDE"
    override val hostIdeCockpitInstallBtn = "Install Plugin ↗"
    override val ecosystemCockpitTitle = "Pair with Antigravity Cockpit for an Immersive IDE Experience"
    override val ecosystemCockpitSubtitle = "Dedicated dashboard extension for Antigravity IDE: monitor quotas & tokens in status bar, switch accounts seamlessly, and sync with Studio proxy."
    override val ecosystemCockpitFeature1 = "Status Bar Quotas"
    override val ecosystemCockpitFeature2 = "1-Click Hot Switch"
    override val ecosystemCockpitFeature3 = "Studio Proxy Sync"
    override val ecosystemCockpitGetPluginBtn = "Get VS Code Extension ↗"
    override val ecosystemCockpitOpenVsxBtn = "Open VSX ↗"
    override val ecosystemCockpitWebsiteBtn = "Website ↗"

    override fun hostAppPortMismatch(endpoint: String) = "Studio launch endpoint differs from current port ($endpoint)"
    override val hostAppRunning = "Antigravity App is running"
    override val hostAppRunningAndConfigured = "App is running; Studio launch settings are saved"
    override val hostAppReady = "Antigravity App is installed"
    override val hostAppNotDetected = "Antigravity App installation not detected"
    override fun hostAppPendingUpdate(port: Int) = "Future Studio launches need endpoint http://127.0.0.1:$port"
    override val hostAppActiveDesc = "Studio launch enabled; only future App launches from Studio use the proxy"
    override val hostAppOfficialDirectDesc = "Studio launch disabled; running instances and external environment are unchanged"

    override fun hostCliPortMismatch(endpoint: String) = "CLI launch endpoint differs from current port ($endpoint)"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) is installed"
    override val hostCliNotDetected = "agy CLI executable not detected"
    override fun hostCliPendingUpdate(port: Int) = "Future CLI launches need endpoint http://127.0.0.1:$port"
    override val hostCliActiveDesc = "Studio launch enabled; copy the command to start CLI with a one-time proxy"
    override val hostCliOfficialDirectDesc = "CLI launch disabled; terminal and external environment are unchanged"
    override val hostCopyCliLaunchCommand = "Copy Launch Command"
    override val hostCliLaunchCommandCopied = "CLI launch command copied; run it in a terminal for a one-time launch"
    override val hostCliLaunchCommandRequiresProxy = "Start the local proxy before copying the CLI launch command"
    override val hostCliLaunchCommandRequiresIntegration = "Enable Studio launch settings for CLI first"

    override val hostIdeUpdateConfirmTitle = "Update Antigravity IDE Proxy Config"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Detected IDE proxy endpoint ($endpoint) differs from local proxy port ($port). Updating will restart IDE to apply changes. Continue?"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "Detected IDE proxy endpoint ($endpoint) differs from local proxy port ($port). Update to current proxy port?"

    override val hostIdeEnableConfirmTitle = "Enable Proxy Mode for IDE"
    override val hostIdeEnableConfirmMessageRunning =
        "Enabling proxy mode will inject configured models and restart Antigravity IDE to apply changes. Continue?"
    override val hostIdeEnableConfirmMessageStopped =
        "Enabling proxy mode will configure Antigravity IDE to connect to the local proxy when started. Continue?"
    override val hostIdeDisableConfirmTitle = "Disable Proxy Mode for IDE"
    override val hostIdeDisableConfirmMessageRunning =
        "Disabling proxy mode will restore official direct connection and restart Antigravity IDE. Continue?"
    override val hostIdeDisableConfirmMessageStopped =
        "Disabling proxy mode will restore official direct connection for Antigravity IDE. Continue?"
    override val hostIdeUpdatedAndRestarted = "Antigravity IDE proxy config updated and restarted"
    override val hostIdeEnabledAndRestarted = "Antigravity IDE proxy mode enabled and restarted"
    override val hostIdeEnabledPendingStart = "Antigravity IDE proxy mode enabled; will apply on launch"
    override val hostIdeConfigUpdatedRestartFailed = "Antigravity IDE config updated, but auto-restart failed"
    override val hostIdeEnableFailed = "Failed to configure Antigravity IDE proxy integration"
    override val hostIdeRestoredAndRestarted = "Antigravity IDE restored to official direct mode and restarted"
    override val hostIdeRestored = "Antigravity IDE restored to official direct mode"
    override val hostIdeDisableFailed = "Failed to disable proxy integration for Antigravity IDE"

    override val hostAppUpdateConfirmTitle = "Update App Studio Launch Settings"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "App launch endpoint ($endpoint) differs from proxy port ($port). Only future launches from Studio are affected; running instances stay unchanged. Continue?"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        hostAppUpdateConfirmMessageRunning(endpoint, port)

    override val hostAppEnableConfirmTitle = "Enable App Studio Launch"
    override val hostAppEnableConfirmMessageRunning =
        "Only future App launches from Studio will use the proxy; running instances stay unchanged. Other running processes are not immediately affected. Continue?"
    override val hostAppEnableConfirmMessageStopped = hostAppEnableConfirmMessageRunning
    override val hostAppDisableConfirmTitle = "Disable App Studio Launch"
    override val hostAppDisableConfirmMessageRunning =
        "Future App launches from Studio will no longer inject the proxy; running instances stay unchanged. Other running processes are not immediately affected. Continue?"
    override val hostAppDisableConfirmMessageStopped = hostAppDisableConfirmMessageRunning
    override val hostAppUpdatedAndRestarted = "App launch settings updated; only future launches from Studio are affected"
    override val hostAppEnabledAndRestarted = "App Studio launch enabled; only future launches from Studio are affected"
    override val hostAppEnabledPendingStart = "App Studio launch enabled; launch App from Studio"
    override val hostAppConfigUpdatedRestartFailed = "App launch settings updated, but launch failed"
    override val hostAppEnableFailed = "Failed to configure App Studio launch"
    override val hostAppRestoredAndRestarted = "App Studio launch disabled; running instances stay unchanged"
    override val hostAppRestored = "App Studio launch disabled; running instances stay unchanged"
    override val hostAppDisableFailed = "Failed to disable App Studio launch"
    override val hostAppNotInstalled = "Antigravity App not detected"

    override val hostCliUpdateConfirmTitle = "Update CLI Studio Launch Settings"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "CLI launch endpoint ($endpoint) differs from proxy port ($port). Copy a new launch command after updating; only subsequent launches using it are affected. Running processes stay unchanged. Continue?"

    override val hostCliEnableConfirmTitle = "Enable CLI Studio Launch"
    override val hostCliEnableConfirmMessage =
        "Copy and run the launch command in a terminal; only that launch uses the proxy, without changing terminal settings. Running processes stay unchanged. Continue?"
    override val hostCliDisableConfirmTitle = "Disable CLI Studio Launch"
    override val hostCliDisableConfirmMessage =
        "CLI proxy launch commands will no longer be generated; running processes and previously copied commands stay unchanged. Other running processes are not immediately affected. Continue?"
    override val hostCliEnabledNotice = "CLI Studio launch enabled; copy and run the launch command in a terminal"
    override val hostCliDisabledNotice = "CLI Studio launch disabled; running processes and copied commands stay unchanged"
    override val hostCliEnableFailed = "Failed to configure CLI Studio launch"
    override val hostCliDisableFailed = "Failed to disable CLI Studio launch"
    override val hostCliNotInstalled = "agy CLI not detected"

    override fun hostStartProxyFirstNotice(hostName: String) =
        "Please start the local proxy server before integrating with $hostName"

    override fun hostForceResetConfirmTitle(hostName: String) = "Force Reset $hostName to Official Mode"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "This will forcefully clear all proxy settings, environment variables and receipts for $hostName to restore clean official direct mode. The application will be restarted if running. Continue?"

    override fun hostForceResetSuccess(hostName: String) = "$hostName has been reset to official direct mode"
    override val hostMigrateSharedEnvironment = "Clean Up Legacy Shared Integration"
    override val hostMigrateSharedEnvironmentConfirmMessage = "This separately cleans up legacy Studio shared integration. The previous environment is restored only if the old receipt matches its current value; otherwise external settings are preserved. App / CLI Studio launch switches remain unchanged. Running processes are not immediately affected. Continue?"
    override val hostMigrateSharedEnvironmentSuccess = "Legacy shared integration cleanup complete; external settings, launch switches and running processes are preserved"
    override fun hostLaunchResetConfirmTitle(hostName: String) = "Reset $hostName Studio Launch"
    override fun hostLaunchResetConfirmMessage(hostName: String) =
        "Clear only Studio launch settings for $hostName, without changing shared or external environment settings. Running processes and copied CLI commands stay unchanged. Continue?"
    override fun hostExternalEnvironmentNotice(endpoint: String) =
        "Shared environment CLOUD_CODE_URL=$endpoint detected. App / CLI launched outside Studio's dedicated launch flow may still use it. Local switches do not overwrite this setting."
    override fun hostRestartConfirmTitle(hostName: String) = "Confirm Restart $hostName"
    override fun hostRestartConfirmMessage(hostName: String) =
        "Are you sure you want to restart $hostName? This will close running instances and launch a new process. Continue?"

    override fun hostRestartSuccess(hostName: String) = "Restarted $hostName"
    override fun hostRestartFailed(hostName: String) = "Failed to restart $hostName"
    override fun hostLaunchSuccess(hostName: String) = "Launched $hostName"
    override fun hostLaunchFailed(hostName: String) = "Failed to launch $hostName"
    override fun hostLaunchProxyNotRunning(hostName: String) =
        "$hostName is configured for proxy mode; please start the local proxy first"

    override fun hostPathDialogTitle(hostTitle: String) = "Configure $hostTitle Path"
    override val hostPathDialogDesc =
        "When auto-detection fails, enter the custom installation directory (e.g. .app bundle or install folder) or main executable path."
    override val hostPathInputLabel = "Installation Directory or Executable Path"
    override val hostPathStatusValid = "Path detected and exists on filesystem"
    override val hostPathStatusNotFound = "Path does not exist on filesystem; please check the path"
    override val hostPathStatusEmpty =
        "Leaving empty and saving will clear custom configuration and restore system auto-detection."
    override val hostPathResetDefault = "Reset to Default"
    override val hostPathSavedCustom = "Custom path configured; re-scanning host"
    override val hostPathResetNotice = "Reset to default auto-detection path"
    override val hostPathBrowse = "Browse..."
    override val hostPathSuggestedTitle = "Detected candidate paths"
    override val hostPathSelectFile = "Browse"

    override val onboardingTitle = "Quick Start Guide"
    override val onboardingSubtitle = "Get familiar with the key features and tips across each page"
    override val onboardingSkip = "Skip"
    override val onboardingPrev = "Previous"
    override val onboardingNext = "Next"
    override val onboardingFinish = "Get Started"
    override val onboardingReopen = "Quick Start Guide"
    override val onboardingReopenDesc = "Re-open the guide to review page features and workflows"
    override val onboardingCompletedToast = "Quick start guide completed. Enjoy using Studio!"

    override val tourStep1Title = "Overview"
    override val tourStep1Desc = "The main control hub. Quickly check if the local proxy is running and whether your IDE or App tools are hooked."

    override val tourStep2Title = "Accounts & Quota"
    override val tourStep2Desc = "Add and manage multiple accounts. Check remaining model quotas and recovery countdowns, and switch accounts anytime."

    override val tourStep3Title = "Model Management"
    override val tourStep3Desc = "Connect your own model APIs directly into the host model list, and freely adjust context compression strategies as needed."

    override val tourStep4Title = "Usage Analytics"
    override val tourStep4Desc = "Track token consumption, request volume, and estimated costs with hourly/daily trends and multi-source filtering."

    override val tourStep5Title = "Activity Logs"
    override val tourStep5Desc = "Every completion and chat request from your IDE is logged here. Check latency, token usage, and errors at a glance."

    override val tourStep6Title = "Settings"
    override val tourStep6Desc = "Change proxy ports, configure upstream corporate proxies, switch themes and languages, or replay this guide anytime."

    override val tourStep7Title = "Local Proxy Service"
    override val tourStep7Desc = "Studio runs a local proxy service. Start/stop the service, check latency, or run instant health diagnostics for network issues."

    override val tourStep8Title = "One-Click Client Hook"
    override val tourStep8Desc = "Automatically scans installed IDEs and clients on your machine. Click 'Connect Proxy' to hook networks without manual port edits."

    override val tourStep9Title = "Add & Manage Accounts"
    override val tourStep9Desc = "Click 'Add Account' to log in via browser or paste a Token. Sort by remaining quota, mask emails for privacy, and export backups."

    override val tourStep10Title = "Custom Providers & Strategies"
    override val tourStep10Desc = "Click 'Add Provider' to connect your custom model services directly into the host model list, and freely adjust compression strategies."

    override val tourStep11Title = "Cost Estimation & Usage Trends"
    override val tourStep11Desc = "Filter by time presets, models, and sources. The dashboard visualizes 5D token breakdowns, daily trend charts, and top model rankings."

    override val tourStep12Title = "Real-Time Audit Stream"
    override val tourStep12Desc = "Lists all incoming requests chronologically. Filter by keywords or status codes, and click to inspect latencies and stream data."

    override val tourStep13Title = "Ports, Network & Themes"
    override val tourStep13Desc = "Change the port if occupied, or set up upstream corporate proxies. Pick your favorite style from a variety of dark and light themes."

    override val tourStep14Title = "Updates & Reopening Guide"
    override val tourStep14Desc = "Check for new updates or open the config directory. Replay this walkthrough anytime by clicking the 'Quick Start Guide' card!"
}
