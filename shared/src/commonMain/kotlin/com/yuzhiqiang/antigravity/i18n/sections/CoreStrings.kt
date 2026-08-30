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

    fun hostAppPortMismatch(endpoint: String): String
    val hostAppRunning: String
    val hostAppRunningAndConfigured: String
    val hostAppReady: String
    val hostAppNotDetected: String
    fun hostAppPendingUpdate(port: Int): String
    val hostAppActiveDesc: String

    fun hostCliPortMismatch(endpoint: String): String
    val hostCliInstalledDesc: String
    val hostCliNotDetected: String
    fun hostCliPendingUpdate(port: Int): String
    val hostCliActiveDesc: String
    val hostCliOfficialDirectDesc: String

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

    override fun hostAppPortMismatch(endpoint: String) = "检测到环境变量与当前端口不一致（$endpoint）"
    override val hostAppRunning = "Antigravity App 正在运行"
    override val hostAppRunningAndConfigured = "Antigravity App 正在运行并已配置"
    override val hostAppReady = "Antigravity App 已安装"
    override val hostAppNotDetected = "未检测到 Antigravity App 安装"
    override fun hostAppPendingUpdate(port: Int) = "环境变量待更新为 http://127.0.0.1:$port"
    override val hostAppActiveDesc = "已通过环境变量配置代理"

    override fun hostCliPortMismatch(endpoint: String) = "检测到 CLI 代理配置与当前端口不一致（$endpoint）"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) 已安装"
    override val hostCliNotDetected = "未检测到 agy CLI 配置文件"
    override fun hostCliPendingUpdate(port: Int) = "CLI 配置待更新为 http://127.0.0.1:$port"
    override val hostCliActiveDesc = "CLI 配置文件代理接入生效中"
    override val hostCliOfficialDirectDesc = "CLI 当前处于官方直连模式"

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

    override val hostAppUpdateConfirmTitle = "更新 Antigravity App 代理配置"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "检测到 App 当前代理环境变量（$endpoint）与本地代理端口（$port）不匹配。更新后将自动重启 App 使配置生效。是否继续？"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "检测到 App 当前代理环境变量（$endpoint）与本地代理端口（$port）不匹配。是否更新为当前代理端口？"

    override val hostAppEnableConfirmTitle = "确认启用代理模式"
    override val hostAppEnableConfirmMessageRunning =
        "启用代理模式后，Antigravity App 会接入配置的模型并自动重启使配置生效。是否继续？"
    override val hostAppEnableConfirmMessageStopped = "启用代理模式将使 Antigravity App 在启动时连接本地代理。是否继续？"
    override val hostAppDisableConfirmTitle = "确认停用代理接入"
    override val hostAppDisableConfirmMessageRunning =
        "将停用 Antigravity App 的代理接入并重启恢复官方直连模式。是否继续？"
    override val hostAppDisableConfirmMessageStopped = "将停用 Antigravity App 的代理接入，恢复官方直连模式。是否继续？"
    override val hostAppUpdatedAndRestarted = "Antigravity App 代理配置已更新并完成重启"
    override val hostAppEnabledAndRestarted = "Antigravity App 已启用代理模式并完成重启"
    override val hostAppEnabledPendingStart = "Antigravity App 已启用代理模式，启动后生效"
    override val hostAppConfigUpdatedRestartFailed = "Antigravity App 配置已更新，但自动重启失败"
    override val hostAppEnableFailed = "Antigravity App 代理接入配置失败"
    override val hostAppRestoredAndRestarted = "Antigravity App 已恢复官方直连并完成重启"
    override val hostAppRestored = "Antigravity App 已恢复官方直连"
    override val hostAppDisableFailed = "Antigravity App 停用代理接入失败"
    override val hostAppNotInstalled = "未检测到 Antigravity App 安装"

    override val hostCliUpdateConfirmTitle = "更新 Antigravity CLI 代理配置"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "检测到 CLI 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。更新后请完全退出并重新打开终端应用生效。是否继续？"

    override val hostCliEnableConfirmTitle = "确认启用代理模式"
    override val hostCliEnableConfirmMessage =
        "启用代理模式后会在用户环境中配置 CLOUD_CODE_URL；完全退出并重新打开终端应用后生效。是否继续？"
    override val hostCliDisableConfirmTitle = "确认停用代理接入"
    override val hostCliDisableConfirmMessage =
        "将停用 CLI 的代理接入并恢复官方直连模式；完全退出并重新打开终端应用后生效。是否继续？"
    override val hostCliEnabledNotice = "CLI 已启用代理模式；请完全退出并重新打开终端应用"
    override val hostCliDisabledNotice = "CLI 代理接入已停用；请完全退出并重新打开终端应用"
    override val hostCliEnableFailed = "CLI 代理接入配置失败"
    override val hostCliDisableFailed = "CLI 停用代理接入失败"
    override val hostCliNotInstalled = "未检测到 agy CLI 安装"

    override fun hostStartProxyFirstNotice(hostName: String) = "请先启动本地代理服务，再接入 $hostName"
    override fun hostForceResetConfirmTitle(hostName: String) = "强制重置 $hostName 为官方直连"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "此操作将清除 $hostName 的所有代理配置与环境变量，恢复为干净的官方直连模式。若应用正在运行将自动重启生效。是否确认重置？"

    override fun hostForceResetSuccess(hostName: String) = "$hostName 已强制重置为官方直连模式"
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

    override val tourStep4Title = "调用日志"
    override val tourStep4Desc = "IDE 发出的每一笔代码补全和问答请求都会记录在这里，请求耗时、用了多少 Token、有没有报错一查便知。"

    override val tourStep5Title = "应用设置"
    override val tourStep5Desc = "在这里修改代理端口、配置公司网络代理、切换主题颜色和语言。如果忘了怎么用，随时能在这重看新手指引。"

    override val tourStep6Title = "本地代理服务"
    override val tourStep6Desc = "Studio 会在本地启动一个代理中转服务。你可以在这启动或停止服务、查看实时延迟，遇到网络问题点健康诊断就能一键体检。"

    override val tourStep7Title = "客户端一键接入"
    override val tourStep7Desc = "系统会自动扫描你电脑上装好的 IDE 和客户端。直接点「接入代理」，就会自动配置好网络，不用你手动去改端口。"

    override val tourStep8Title = "添加与管理账号"
    override val tourStep8Desc = "点「添加账号」可以用浏览器登录或直接粘贴 Token。上方还支持一键按剩余额度排序、隐藏邮箱防窥，以及把账号配置导出备份。"

    override val tourStep9Title = "接入第三方服务与压缩策略"
    override val tourStep9Desc = "点「添加提供商」就能接入你自备的模型服务，直接加入到宿主的模型列表中使用。还可以自由调整上下文压缩策略，灵活掌控上下文长度。"

    override val tourStep10Title = "实时请求审计流"
    override val tourStep10Desc = "这里会按时间列出所有请求。你可以按关键词搜索或按状态码过滤，点开就能查看具体的耗时与数据流，排查报错非常方便。"

    override val tourStep11Title = "端口、网络与主题设置"
    override val tourStep11Desc = "如果默认端口被占用了可以在这修改，公司需要挂上游代理也可以在这填。下面还准备了多种深浅色主题，挑一个你喜欢的风格。"

    override val tourStep12Title = "检查更新与重看指引"
    override val tourStep12Desc = "在这里可以检查最新版本，也能直接打开配置文件夹。如果你以后想重温功能用法，随时点击「新手指引」卡片就能重新打开！"
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

    override fun hostAppPortMismatch(endpoint: String) = "Environment variable differs from current port ($endpoint)"
    override val hostAppRunning = "Antigravity App is running"
    override val hostAppRunningAndConfigured = "Antigravity App is running and configured"
    override val hostAppReady = "Antigravity App is installed"
    override val hostAppNotDetected = "Antigravity App installation not detected"
    override fun hostAppPendingUpdate(port: Int) = "Environment variable pending update to http://127.0.0.1:$port"
    override val hostAppActiveDesc = "CLOUD_CODE_URL environment proxy active"

    override fun hostCliPortMismatch(endpoint: String) = "CLI proxy config differs from current port ($endpoint)"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) is installed"
    override val hostCliNotDetected = "agy CLI config file not detected"
    override fun hostCliPendingUpdate(port: Int) = "CLI config pending update to http://127.0.0.1:$port"
    override val hostCliActiveDesc = "CLI config proxy integration active"
    override val hostCliOfficialDirectDesc = "CLI currently in official direct mode"

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

    override val hostAppUpdateConfirmTitle = "Update Antigravity App Proxy Config"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Detected App proxy environment ($endpoint) differs from local proxy port ($port). Updating will restart App to apply changes. Continue?"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "Detected App proxy environment ($endpoint) differs from local proxy port ($port). Update to current proxy port?"

    override val hostAppEnableConfirmTitle = "Enable Proxy Mode for App"
    override val hostAppEnableConfirmMessageRunning =
        "Enabling proxy mode will inject configured models and restart Antigravity App to apply changes. Continue?"
    override val hostAppEnableConfirmMessageStopped =
        "Enabling proxy mode will configure Antigravity App to connect to the local proxy when started. Continue?"
    override val hostAppDisableConfirmTitle = "Disable Proxy Mode for App"
    override val hostAppDisableConfirmMessageRunning =
        "Disabling proxy mode will restore official direct connection and restart Antigravity App. Continue?"
    override val hostAppDisableConfirmMessageStopped =
        "Disabling proxy mode will restore official direct connection for Antigravity App. Continue?"
    override val hostAppUpdatedAndRestarted = "Antigravity App proxy config updated and restarted"
    override val hostAppEnabledAndRestarted = "Antigravity App proxy mode enabled and restarted"
    override val hostAppEnabledPendingStart = "Antigravity App proxy mode enabled; will apply on launch"
    override val hostAppConfigUpdatedRestartFailed = "Antigravity App config updated, but auto-restart failed"
    override val hostAppEnableFailed = "Failed to configure Antigravity App proxy integration"
    override val hostAppRestoredAndRestarted = "Antigravity App restored to official direct mode and restarted"
    override val hostAppRestored = "Antigravity App restored to official direct mode"
    override val hostAppDisableFailed = "Failed to disable proxy integration for Antigravity App"
    override val hostAppNotInstalled = "Antigravity App not detected"

    override val hostCliUpdateConfirmTitle = "Update Antigravity CLI Proxy Config"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "Detected CLI proxy config ($endpoint) differs from local proxy port ($port). Please restart your terminal application after updating. Continue?"

    override val hostCliEnableConfirmTitle = "Enable Proxy Mode for CLI"
    override val hostCliEnableConfirmMessage =
        "Enabling proxy mode will configure CLOUD_CODE_URL in your user environment; restart your terminal to apply. Continue?"
    override val hostCliDisableConfirmTitle = "Disable Proxy Mode for CLI"
    override val hostCliDisableConfirmMessage =
        "Disabling proxy mode will restore official direct connection; restart your terminal to apply. Continue?"
    override val hostCliEnabledNotice = "CLI proxy mode enabled; please restart your terminal application"
    override val hostCliDisabledNotice = "CLI proxy mode disabled; please restart your terminal application"
    override val hostCliEnableFailed = "Failed to configure CLI proxy integration"
    override val hostCliDisableFailed = "Failed to disable CLI proxy integration"
    override val hostCliNotInstalled = "agy CLI not detected"

    override fun hostStartProxyFirstNotice(hostName: String) =
        "Please start the local proxy server before integrating with $hostName"

    override fun hostForceResetConfirmTitle(hostName: String) = "Force Reset $hostName to Official Mode"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "This will forcefully clear all proxy settings, environment variables and receipts for $hostName to restore clean official direct mode. The application will be restarted if running. Continue?"

    override fun hostForceResetSuccess(hostName: String) = "$hostName has been reset to official direct mode"
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

    override val tourStep4Title = "Activity Logs"
    override val tourStep4Desc = "Every completion and chat request from your IDE is logged here. Check latency, token usage, and errors at a glance."

    override val tourStep5Title = "Settings"
    override val tourStep5Desc = "Change proxy ports, configure upstream corporate proxies, switch themes and languages, or replay this guide anytime."

    override val tourStep6Title = "Local Proxy Service"
    override val tourStep6Desc = "Studio runs a local proxy service. Start/stop the service, check latency, or run instant health diagnostics for network issues."

    override val tourStep7Title = "One-Click Client Hook"
    override val tourStep7Desc = "Automatically scans installed IDEs and clients on your machine. Click 'Connect Proxy' to hook networks without manual port edits."

    override val tourStep8Title = "Add & Manage Accounts"
    override val tourStep8Desc = "Click 'Add Account' to log in via browser or paste a Token. Sort by remaining quota, mask emails for privacy, and export backups."

    override val tourStep9Title = "Custom Providers & Strategies"
    override val tourStep9Desc = "Click 'Add Provider' to connect your custom model services directly into the host model list, and freely adjust compression strategies."

    override val tourStep10Title = "Real-Time Audit Stream"
    override val tourStep10Desc = "Lists all incoming requests chronologically. Filter by keywords or status codes, and click to inspect latencies and stream data."

    override val tourStep11Title = "Ports, Network & Themes"
    override val tourStep11Desc = "Change the port if occupied, or set up upstream corporate proxies. Pick your favorite style from a variety of dark and light themes."

    override val tourStep12Title = "Updates & Reopening Guide"
    override val tourStep12Desc = "Check for new updates or open the config directory. Replay this walkthrough anytime by clicking the 'Quick Start Guide' card!"
}
