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
    override fun hostIdePendingUpdate(port: Int) = "代理端口已变更为 $port，待更新"
    override val hostIdeActiveDesc = "已接入代理模式"
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

    override fun hostAppPortMismatch(endpoint: String) = "代理端口不一致（$endpoint）"
    override val hostAppRunning = "Antigravity App 正在运行"
    override val hostAppRunningAndConfigured = "Antigravity App 正在运行"
    override val hostAppReady = "Antigravity App 已安装"
    override val hostAppNotDetected = "未检测到 Antigravity App 安装"
    override fun hostAppPendingUpdate(port: Int) = "代理端口已变更为 $port，待更新"
    override val hostAppActiveDesc = "已接入代理，从 Studio 点击启动即可生效"
    override val hostAppOfficialDirectDesc = "当前处于官方直连模式"

    override fun hostCliPortMismatch(endpoint: String) = "代理端口不一致（$endpoint）"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) 已安装"
    override val hostCliNotDetected = "未检测到 agy CLI 可执行文件"
    override fun hostCliPendingUpdate(port: Int) = "代理端口已变更为 $port，待更新"
    override val hostCliActiveDesc = "已接入代理，在终端运行 agy-studio 即可生效"
    override val hostCliOfficialDirectDesc = "当前处于官方直连模式"
    override val hostCopyCliLaunchCommand = "复制启动命令"
    override val hostCliLaunchCommandCopied = "启动命令已复制，在终端运行 agy-studio 即可"
    override val hostCliLaunchCommandRequiresProxy = "请先启动本地代理，再复制启动命令"
    override val hostCliLaunchCommandRequiresIntegration = "请先点击「接入代理」，再复制启动命令"

    override val hostIdeUpdateConfirmTitle = "更新 IDE 代理配置"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "本地代理端口已变更为 $port。更新配置并自动重启 IDE 生效，是否继续？"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "本地代理端口已变更为 $port。是否更新 IDE 代理配置？"

    override val hostIdeEnableConfirmTitle = "接入 IDE 代理"
    override val hostIdeEnableConfirmMessageRunning =
        "接入后 IDE 将使用 Studio 代理的模型。保存后将自动重启 IDE 生效，是否继续？"
    override val hostIdeEnableConfirmMessageStopped = "接入后 IDE 将使用 Studio 代理的模型，下次打开 IDE 时生效。是否继续？"
    override val hostIdeDisableConfirmTitle = "恢复 IDE 官方直连"
    override val hostIdeDisableConfirmMessageRunning =
        "将清除代理配置并自动重启 IDE，恢复官方直连。是否继续？"
    override val hostIdeDisableConfirmMessageStopped = "将清除代理配置，下次打开 IDE 时恢复官方直连。是否继续？"
    override val hostIdeUpdatedAndRestarted = "IDE 代理配置已更新并重启"
    override val hostIdeEnabledAndRestarted = "已接入代理模式并重启 IDE"
    override val hostIdeEnabledPendingStart = "已接入代理模式，下次打开 IDE 生效"
    override val hostIdeConfigUpdatedRestartFailed = "配置已更新，但自动重启 IDE 失败，请手动打开"
    override val hostIdeEnableFailed = "IDE 代理接入配置失败"
    override val hostIdeRestoredAndRestarted = "已恢复官方直连并重启 IDE"
    override val hostIdeRestored = "已恢复官方直连"
    override val hostIdeDisableFailed = "恢复官方直连失败"

    override val hostAppUpdateConfirmTitle = "更新 App 代理配置"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "代理端口已变更为 $port。更新后从 Studio 重新启动 App 即可生效，不会影响当前窗口。是否继续？"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        hostAppUpdateConfirmMessageRunning(endpoint, port)

    override val hostAppEnableConfirmTitle = "接入 App 代理"
    override val hostAppEnableConfirmMessageRunning =
        "开启后，从 Studio 点击「启动」即可接入代理，不会影响系统环境和已打开的窗口。是否继续？"
    override val hostAppEnableConfirmMessageStopped = hostAppEnableConfirmMessageRunning
    override val hostAppDisableConfirmTitle = "恢复 App 官方直连"
    override val hostAppDisableConfirmMessageRunning =
        "将恢复官方直连模式，后续从 Studio 启动不再使用代理。是否继续？"
    override val hostAppDisableConfirmMessageStopped = hostAppDisableConfirmMessageRunning
    override val hostAppUpdatedAndRestarted = "App 代理配置已更新，点击「启动」即可生效"
    override val hostAppEnabledAndRestarted = "App 代理已配置，点击「启动」即可生效"
    override val hostAppEnabledPendingStart = "App 代理已配置，点击「启动」即可生效"
    override val hostAppConfigUpdatedRestartFailed = "App 代理配置已更新，但启动失败"
    override val hostAppEnableFailed = "App 代理配置失败"
    override val hostAppRestoredAndRestarted = "App 已恢复官方直连"
    override val hostAppRestored = "App 已恢复官方直连"
    override val hostAppDisableFailed = "恢复 App 官方直连失败"
    override val hostAppNotInstalled = "未检测到 Antigravity App 安装"

    override val hostCliUpdateConfirmTitle = "更新 CLI 代理配置"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "代理端口已变更为 $port。更新后在终端重新运行 agy-studio 即可生效。是否继续？"

    override val hostCliEnableConfirmTitle = "接入 CLI 代理"
    override val hostCliEnableConfirmMessage =
        "开启后在终端运行 agy-studio 即可接入代理，原有 agy 命令依然保持官方直连。是否继续？"
    override val hostCliDisableConfirmTitle = "恢复 CLI 官方直连"
    override val hostCliDisableConfirmMessage =
        "将移除 agy-studio 代理配置，恢复官方直连。是否继续？"
    override val hostCliEnabledNotice = "CLI 代理已配置，点击复制命令在终端运行即可"
    override val hostCliDisabledNotice = "CLI 已恢复官方直连"
    override val hostCliEnableFailed = "CLI 代理配置失败"
    override val hostCliDisableFailed = "恢复 CLI 官方直连失败"
    override val hostCliNotInstalled = "未检测到 agy CLI 安装"

    override fun hostStartProxyFirstNotice(hostName: String) = "请先启动本地代理服务，再接入 $hostName"
    override fun hostForceResetConfirmTitle(hostName: String) = "强制重置 $hostName 为官方直连"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "将强制清除 $hostName 的代理配置并恢复官方直连。是否继续？"

    override fun hostForceResetSuccess(hostName: String) = "$hostName 已恢复官方直连"
    override val hostMigrateSharedEnvironment = "清理旧共享接入"
    override val hostMigrateSharedEnvironmentConfirmMessage = "此操作独立清理旧版 Studio 共享接入。仅当旧收据与当前环境值匹配时恢复旧环境，否则保留外部环境；不会改变 App / CLI 专属启动开关。已运行进程不会即时生效。是否继续？"
    override val hostMigrateSharedEnvironmentSuccess = "旧共享接入清理完成；外部环境与专属启动开关保持不变，已运行进程不受影响"
    override fun hostLaunchResetConfirmTitle(hostName: String) = "重置 $hostName 代理配置"
    override fun hostLaunchResetConfirmMessage(hostName: String) =
        "将清除 $hostName 的代理配置并恢复官方直连。是否继续？"
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
    override fun hostIdePendingUpdate(port: Int) = "Proxy port changed to $port (pending update)"
    override val hostIdeActiveDesc = "Proxy mode active"
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

    override fun hostAppPortMismatch(endpoint: String) = "Proxy port mismatch ($endpoint)"
    override val hostAppRunning = "Antigravity App is running"
    override val hostAppRunningAndConfigured = "Antigravity App is running"
    override val hostAppReady = "Antigravity App is installed"
    override val hostAppNotDetected = "Antigravity App installation not detected"
    override fun hostAppPendingUpdate(port: Int) = "Proxy port changed to $port (pending update)"
    override val hostAppActiveDesc = "Proxy configured; launch from Studio to connect"
    override val hostAppOfficialDirectDesc = "Currently using official direct mode"

    override fun hostCliPortMismatch(endpoint: String) = "Proxy port mismatch ($endpoint)"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) is installed"
    override val hostCliNotDetected = "agy CLI executable not detected"
    override fun hostCliPendingUpdate(port: Int) = "Proxy port changed to $port (pending update)"
    override val hostCliActiveDesc = "Proxy configured; run agy-studio in terminal to connect"
    override val hostCliOfficialDirectDesc = "Currently using official direct mode"
    override val hostCopyCliLaunchCommand = "Copy Launch Command"
    override val hostCliLaunchCommandCopied = "Launch command copied. Run agy-studio in terminal"
    override val hostCliLaunchCommandRequiresProxy = "Please start the local proxy before copying launch command"
    override val hostCliLaunchCommandRequiresIntegration = "Please click 'Enable Proxy' before copying launch command"

    override val hostIdeUpdateConfirmTitle = "Update IDE Proxy Config"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Proxy port changed to $port. Update configuration and restart IDE now?"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "Proxy port changed to $port. Update IDE configuration now?"

    override val hostIdeEnableConfirmTitle = "Enable IDE Proxy"
    override val hostIdeEnableConfirmMessageRunning =
        "IDE will use models configured in Studio and restart automatically to apply. Continue?"
    override val hostIdeEnableConfirmMessageStopped =
        "IDE will use models configured in Studio on next launch. Continue?"
    override val hostIdeDisableConfirmTitle = "Restore IDE Direct Mode"
    override val hostIdeDisableConfirmMessageRunning =
        "Proxy configuration will be cleared and IDE will restart to restore direct mode. Continue?"
    override val hostIdeDisableConfirmMessageStopped =
        "Proxy configuration will be cleared to restore direct mode on next launch. Continue?"
    override val hostIdeUpdatedAndRestarted = "IDE proxy config updated and restarted"
    override val hostIdeEnabledAndRestarted = "IDE proxy mode enabled and restarted"
    override val hostIdeEnabledPendingStart = "IDE proxy mode enabled; will apply on next launch"
    override val hostIdeConfigUpdatedRestartFailed = "Config updated, but failed to auto-restart IDE. Please restart manually"
    override val hostIdeEnableFailed = "Failed to configure Antigravity IDE proxy"
    override val hostIdeRestoredAndRestarted = "Restored to official direct mode and restarted"
    override val hostIdeRestored = "Restored to official direct mode"
    override val hostIdeDisableFailed = "Failed to restore official direct mode"

    override val hostAppUpdateConfirmTitle = "Update App Proxy Config"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Proxy port changed to $port. Re-launch from Studio to apply without affecting current windows. Continue?"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        hostAppUpdateConfirmMessageRunning(endpoint, port)

    override val hostAppEnableConfirmTitle = "Enable App Proxy"
    override val hostAppEnableConfirmMessageRunning =
        "Launching from Studio will connect to the proxy without affecting existing windows or system settings. Continue?"
    override val hostAppEnableConfirmMessageStopped = hostAppEnableConfirmMessageRunning
    override val hostAppDisableConfirmTitle = "Restore App Direct Mode"
    override val hostAppDisableConfirmMessageRunning =
        "Future launches from Studio will no longer use the proxy. Continue?"
    override val hostAppDisableConfirmMessageStopped = hostAppDisableConfirmMessageRunning
    override val hostAppUpdatedAndRestarted = "App proxy config updated; click Launch to apply"
    override val hostAppEnabledAndRestarted = "App proxy configured; click Launch to apply"
    override val hostAppEnabledPendingStart = "App proxy configured; click Launch to apply"
    override val hostAppConfigUpdatedRestartFailed = "App proxy config updated, but launch failed"
    override val hostAppEnableFailed = "Failed to configure App proxy"
    override val hostAppRestoredAndRestarted = "App restored to official direct mode"
    override val hostAppRestored = "App restored to official direct mode"
    override val hostAppDisableFailed = "Failed to restore App direct mode"
    override val hostAppNotInstalled = "Antigravity App not detected"

    override val hostCliUpdateConfirmTitle = "Update CLI Proxy Config"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "Proxy port changed to $port. Re-run agy-studio in your terminal to apply. Continue?"

    override val hostCliEnableConfirmTitle = "Enable CLI Proxy"
    override val hostCliEnableConfirmMessage =
        "Run agy-studio in your terminal to connect. Native agy commands remain untouched. Continue?"
    override val hostCliDisableConfirmTitle = "Restore CLI Direct Mode"
    override val hostCliDisableConfirmMessage =
        "Removes agy-studio proxy config and restores direct mode. Continue?"
    override val hostCliEnabledNotice = "CLI proxy configured; click copy command and run in terminal"
    override val hostCliDisabledNotice = "CLI restored to official direct mode"
    override val hostCliEnableFailed = "Failed to configure CLI proxy"
    override val hostCliDisableFailed = "Failed to restore CLI direct mode"
    override val hostCliNotInstalled = "agy CLI not detected"

    override fun hostStartProxyFirstNotice(hostName: String) =
        "Please start the local proxy server before integrating with $hostName"

    override fun hostForceResetConfirmTitle(hostName: String) = "Force Reset $hostName to Official Mode"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "This will clear proxy settings and restore clean official direct mode for $hostName. Continue?"

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
