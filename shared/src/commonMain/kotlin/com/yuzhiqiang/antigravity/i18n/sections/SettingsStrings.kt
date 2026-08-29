package com.yuzhiqiang.antigravity.i18n.sections

interface SettingsStrings {
    val settingsTitle: String
    val settingsSubtitle: String
    val settingsGeneral: String
    val settingsNetwork: String
    val settingsData: String
    val settingsAboutSection: String
    val settingsLanguage: String
    val settingsLanguageDescription: String
    val settingsTheme: String
    val settingsThemeDescription: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsThemePalette: String
    val settingsThemePaletteDescription: String
    // Theme Palette (赤橙黄绿青蓝紫 + 白)
    val paletteWhite: String
    val paletteRed: String
    val paletteOrange: String
    val paletteAmber: String
    val paletteGreen: String
    val paletteTeal: String
    val paletteBlue: String
    val palettePurple: String
    val paletteDawn: String
    val paletteDeepOcean: String
    val paletteEmerald: String
    val paletteRose: String
    val paletteSlate: String
    val paletteIndigo: String
    val paletteOcean: String
    val paletteViolet: String
    val settingsPort: String
    val settingsPortDescription: String
    val settingsPortInvalid: String
    fun settingsPortUpdated(port: Int): String
    fun settingsPortRestartFailed(error: String): String
    fun settingsPortUpdateFailed(error: String): String
    val settingsLocalProxyTitle: String
    val settingsOutboundProxyTitle: String
    val settingsOutboundProxyDescription: String
    val settingsOutboundMode: String
    val settingsOutboundAuto: String
    val settingsOutboundAutoDescription: String
    val settingsOutboundDirect: String
    val settingsOutboundDirectDescription: String
    val settingsOutboundSystem: String
    val settingsOutboundSystemDescription: String
    val settingsOutboundManual: String
    val settingsOutboundManualDescription: String
    val settingsOutboundRecommended: String
    val settingsOutboundProtocol: String
    val settingsOutboundHost: String
    val settingsOutboundPort: String
    val settingsOutboundFallback: String
    val settingsOutboundFallbackDescription: String
    val settingsOutboundDetection: String
    val settingsOutboundSystemDetected: String
    val settingsOutboundEnvironmentDetected: String
    val settingsOutboundEffectiveRoute: String
    val settingsOutboundNotConfigured: String
    val settingsOutboundDirectRoute: String
    val settingsOutboundTest: String
    val settingsOutboundTesting: String
    val settingsOutboundSave: String
    val settingsOutboundReset: String
    val settingsOutboundUnsaved: String
    val settingsOutboundManualHostRequired: String
    val settingsOutboundManualPortInvalid: String
    val settingsOutboundSystemUnavailable: String
    val settingsOutboundProxySaved: String
    fun settingsOutboundProxySaveFailed(error: String): String
    fun settingsOutboundTestSuccess(route: String, latencyMs: Long): String
    fun settingsOutboundTestFallback(latencyMs: Long): String
    fun settingsOutboundTestFailed(error: String): String
    val settingsOpenNetworkSettings: String
    val settingsOutboundDirectActiveDesc: String
    fun settingsOutboundAutoActiveWithProxyDesc(proxy: String): String
    val settingsOutboundAutoActiveNoProxyDesc: String
    fun settingsOutboundSystemActiveDesc(proxy: String, fallback: Boolean): String
    fun settingsOutboundSystemNoProxyDesc(fallback: Boolean): String
    fun settingsOutboundManualActiveDesc(endpoint: String, fallback: Boolean): String
    val settingsOutboundManualInvalidDesc: String
    val settingsHostPathsTitle: String
    val settingsHostPathsDesc: String
    val settingsDefaultSwitchTargetTitle: String
    val settingsDefaultSwitchTargetDesc: String
    val settingsDefaultSwitchTargetAll: String
    val settingsDefaultSwitchTargetIdeOnly: String
    val settingsDefaultSwitchTargetAppCliOnly: String
    val settingsDefaultSwitchTargetRemember: String
    fun settingsHostPathCustom(title: String): String
    fun settingsHostPathAuto(title: String): String
    val settingsStoragePath: String
    val settingsStorageDescription: String
    val settingsOpenDirectory: String
    val settingsDirectoryOpenError: String
    val settingsUnsupportedPlatform: String
    fun settingsOpenDirFailed(error: String): String
    val settingsAbout: String
    val settingsAboutDescription: String
    val settingsVersion: String
    val settingsRepo: String
    val settingsConfigDir: String
    val settingsOpenConfigDir: String
    val settingsDeveloper: String
    val settingsFeedback: String
    val settingsFeedbackDesc: String

    // Update & Version Checker
    val updateCheck: String
    val updateChecking: String
    val updateUpToDate: String
    val updateAvailableTitle: String
    fun updateAvailableSubtitle(version: String): String
    val updateChangelogTitle: String
    val updateCurrentVersionLabel: String
    val updateLatestVersionLabel: String
    val updateDownloadNow: String
    val updateLater: String
    val updateIgnoreThisVersion: String
    val updateIgnoredNotice: String
    fun updateCheckFailed(error: String): String
    val updateNoChangelog: String
    fun updateDownloadProgress(downloaded: String, total: String, percent: Int): String
    fun updateDownloadSpeed(speed: String): String
    val updateDownloading: String
    val updateDownloadCompleted: String
    val updateInstallNow: String
    val updateShowInFolder: String
    fun updateDownloadFailed(error: String): String
    val updateRetryDownload: String
    val updateOpenInBrowser: String
    val updateCancelDownload: String
    val settingsAutoCheckUpdate: String
    val settingsAutoCheckUpdateDesc: String
    val settingsCheckUpdateBtn: String
    val settingsCheckingUpdate: String
    val settingsLatestVersionBadge: String
    val settingsNewVersionBadge: String
    fun settingsLastChecked(time: String): String
    val settingsDeveloperMode: String
    val settingsDeveloperModeDesc: String
    val settingsDeveloperModeEnabled: String
    val settingsDeveloperModeDisabled: String
    val developerModeDialogTitle: String
    val developerModeUnlockPrompt: String
    val developerModeTurnOn: String
    val developerModeTurnOff: String
    val developerModeWrongPassword: String
    val developerModeKeepEnabled: String
    val developerModeCancel: String

    // Doctor Diagnostics
}

object SettingsStringsZh : SettingsStrings {
    override val settingsTitle = "应用偏好与配置"
    override val settingsSubtitle = "管理语言、外观、代理端口与数据存储"
    override val settingsGeneral = "常规偏好"
    override val settingsNetwork = "网络代理"
    override val settingsData = "数据存储"
    override val settingsAboutSection = "关于应用"
    override val settingsLanguage = "界面语言"
    override val settingsLanguageDescription = "切换应用显示语言"
    override val settingsTheme = "外观主题"
    override val settingsThemeDescription = "选择系统、浅色或深色外观"
    override val settingsThemeSystem = "跟随系统"
    override val settingsThemeLight = "浅色模式"
    override val settingsThemeDark = "深色模式"
    override val settingsThemePalette = "配色方案"
    override val settingsThemePaletteDescription = "选择应用核心主题色系 (Material Design 3)"
    override val paletteWhite = "白曜极简"
    override val paletteRed = "赤霞绯红"
    override val paletteOrange = "橙光落日"
    override val paletteAmber = "黄昏金珀"
    override val paletteGreen = "绿岚翡翠"
    override val paletteTeal = "青碧苍黛"
    override val paletteBlue = "蓝海清冽"
    override val palettePurple = "紫霞晨光"
    override val paletteDawn = "紫霞晨光"
    override val paletteDeepOcean = "蓝海清冽"
    override val paletteEmerald = "青岚翠绿"
    override val paletteRose = "赤霞绯红"
    override val paletteSlate = "远山苍灰"
    override val paletteIndigo = "极光靛蓝"
    override val paletteOcean = "深海青蓝"
    override val paletteViolet = "幻境紫罗兰"
    override val settingsPort = "Studio 本地监听端口"
    override val settingsPortDescription = "IDE、App 和 CLI 连接 Studio 使用的端口；修改后会重启本地代理服务"
    override val settingsPortInvalid = "端口必须在 1024 到 65535 之间"
    override fun settingsPortUpdated(port: Int) = "代理端口已更新为 $port"
    override fun settingsPortRestartFailed(error: String) = "代理端口更新后启动失败：$error"
    override fun settingsPortUpdateFailed(error: String) = "更新代理端口失败：$error"
    override val settingsLocalProxyTitle = "本地代理服务"
    override val settingsOutboundProxyTitle = "上游网络代理"
    override val settingsOutboundProxyDescription =
        "控制 Studio 访问官方服务和公网 Provider 时使用的网络路径；本地地址始终直连"
    override val settingsOutboundMode = "连接方式"
    override val settingsOutboundAuto = "智能选择"
    override val settingsOutboundAutoDescription = "系统或环境代理不可用时自动回退直连"
    override val settingsOutboundDirect = "始终直连"
    override val settingsOutboundDirectDescription = "忽略系统代理和代理环境变量"
    override val settingsOutboundSystem = "系统代理"
    override val settingsOutboundSystemDescription = "仅使用操作系统配置的网络代理"
    override val settingsOutboundManual = "手动代理"
    override val settingsOutboundManualDescription = "指定 HTTP 或 SOCKS5 代理节点"
    override val settingsOutboundRecommended = "推荐"
    override val settingsOutboundProtocol = "代理协议"
    override val settingsOutboundHost = "服务器地址"
    override val settingsOutboundPort = "端口"
    override val settingsOutboundFallback = "代理不可用时允许回退直连"
    override val settingsOutboundFallbackDescription = "关闭后代理连接失败将直接结束请求，不会绕过代理"
    override val settingsOutboundDetection = "当前网络检测"
    override val settingsOutboundSystemDetected = "系统代理"
    override val settingsOutboundEnvironmentDetected = "环境代理"
    override val settingsOutboundEffectiveRoute = "候选路径"
    override val settingsOutboundNotConfigured = "未配置"
    override val settingsOutboundDirectRoute = "直连"
    override val settingsOutboundTest = "测试连接"
    override val settingsOutboundTesting = "正在测试"
    override val settingsOutboundSave = "保存并应用"
    override val settingsOutboundReset = "恢复默认"
    override val settingsOutboundUnsaved = "有未保存的更改"
    override val settingsOutboundManualHostRequired = "请输入不带协议和路径的代理服务器地址"
    override val settingsOutboundManualPortInvalid = "代理端口必须在 1 到 65535 之间"
    override val settingsOutboundSystemUnavailable = "未检测到系统代理；关闭直连回退后请求将无法发送"
    override val settingsOutboundProxySaved = "上游网络代理设置已保存并立即生效"
    override fun settingsOutboundProxySaveFailed(error: String) = "保存上游网络代理失败：$error"
    override fun settingsOutboundTestSuccess(route: String, latencyMs: Long) = "连接成功 · $route · ${latencyMs}ms"
    override fun settingsOutboundTestFallback(latencyMs: Long) = "代理不可用，已通过直连连接成功 · ${latencyMs}ms"
    override fun settingsOutboundTestFailed(error: String) = "连接失败：$error"
    override val settingsOpenNetworkSettings = "打开网络设置"
    override val settingsOutboundDirectActiveDesc = "当前出站路径: 始终直连（已忽略操作系统网络代理）"
    override fun settingsOutboundAutoActiveWithProxyDesc(proxy: String) =
        "智能调度: 优先代理 ($proxy) → 失败自动回退直连"

    override val settingsOutboundAutoActiveNoProxyDesc = "智能调度: 未检测到系统代理 · 自动直连"
    override fun settingsOutboundSystemActiveDesc(proxy: String, fallback: Boolean) =
        "系统代理: $proxy" + if (fallback) " → 失败回退直连" else " (严格仅走代理)"

    override fun settingsOutboundSystemNoProxyDesc(fallback: Boolean) =
        if (fallback) "⚠️ 未检测到系统代理 · 已回退直连" else "⚠️ 未检测到系统代理，公网请求将被阻断"

    override fun settingsOutboundManualActiveDesc(endpoint: String, fallback: Boolean) =
        "手动代理: $endpoint" + if (fallback) " → 失败回退直连" else " (严格仅走代理)"

    override val settingsOutboundManualInvalidDesc = "未配置有效的手动代理服务器"
    override val settingsHostPathsTitle = "应用安装路径"
    override val settingsHostPathsDesc = "自定义 Antigravity IDE、App 与 CLI 的安装目录或可执行文件路径"
    override val settingsDefaultSwitchTargetTitle = "切号默认目标应用"
    override val settingsDefaultSwitchTargetDesc = "配置在账号列表切换账号时，默认勾选生效的目标应用"
    override val settingsDefaultSwitchTargetAll = "全部应用 (推荐)"
    override val settingsDefaultSwitchTargetIdeOnly = "仅 IDE"
    override val settingsDefaultSwitchTargetAppCliOnly = "仅 App & CLI"
    override val settingsDefaultSwitchTargetRemember = "记住上次选择"
    override fun settingsHostPathCustom(title: String) = "$title: 自定义"
    override fun settingsHostPathAuto(title: String) = "$title: 自动"
    override val settingsStoragePath = "配置文件目录"
    override val settingsStorageDescription = "查看或备份本地持久化的服务商配置与策略数据"
    override val settingsOpenDirectory = "打开目录"
    override val settingsDirectoryOpenError = "目录打开失败"
    override val settingsUnsupportedPlatform = "当前平台不支持直接打开文件夹"
    override fun settingsOpenDirFailed(error: String) = "打开配置目录失败：$error"
    override val settingsAbout = "关于 Antigravity Studio"
    override val settingsAboutDescription =
        "基于 Kotlin Multiplatform 与 Compose Desktop 构建的 Antigravity 智能代理与模型接入中枢。"
    override val settingsVersion =
        "Antigravity Studio v${com.yuzhiqiang.antigravity.update.model.AppVersion.CURRENT} · Kotlin Multiplatform & Compose Desktop"
    override val settingsRepo = "开源仓库"
    override val settingsConfigDir = "配置目录"
    override val settingsOpenConfigDir = "打开数据与模型配置文件"
    override val settingsDeveloper = "开发者"
    override val settingsFeedback = "反馈建议"
    override val settingsFeedbackDesc = "提交 Issue 或加入交流群"

    // Update & Version Checker
    override val updateCheck = "检查更新"
    override val updateChecking = "正在检查更新..."
    override val updateUpToDate = "当前已是最新版本"
    override val updateAvailableTitle = "发现新版本"
    override fun updateAvailableSubtitle(version: String) =
        "Antigravity Studio $version 已发布，建议立即更新以获得更佳体验。"

    override val updateChangelogTitle = "更新日志"
    override val updateCurrentVersionLabel = "当前版本"
    override val updateLatestVersionLabel = "最新版本"
    override val updateDownloadNow = "立即下载"
    override val updateLater = "稍后提醒"
    override val updateIgnoreThisVersion = "跳过此版本"
    override val updateIgnoredNotice = "已忽略此版本的后续启动提醒"
    override fun updateCheckFailed(error: String) = "检查更新失败：$error"
    override val updateNoChangelog = "暂无详细发布说明。"
    override fun updateDownloadProgress(downloaded: String, total: String, percent: Int) =
        "$downloaded / $total ($percent%)"

    override fun updateDownloadSpeed(speed: String) = "$speed/s"
    override val updateDownloading = "正在下载更新…"
    override val updateDownloadCompleted = "下载完成，正在打开安装器…"
    override val updateInstallNow = "立即安装"
    override val updateShowInFolder = "打开文件位置"
    override fun updateDownloadFailed(error: String) = "下载失败：$error"
    override val updateRetryDownload = "重试下载"
    override val updateOpenInBrowser = "在浏览器中下载"
    override val updateCancelDownload = "取消"
    override val settingsAutoCheckUpdate = "启动时自动检查更新"
    override val settingsAutoCheckUpdateDesc = "应用启动时在后台静默检查是否有新版本，并在有更新时提醒"
    override val settingsCheckUpdateBtn = "检查更新"
    override val settingsCheckingUpdate = "正在检测..."
    override val settingsLatestVersionBadge = "最新版本"
    override val settingsNewVersionBadge = "可更新"
    override fun settingsLastChecked(time: String) = "上次检查：$time"
    override val settingsDeveloperMode = "开发者调试模式"
    override val settingsDeveloperModeDesc = "显示官方模型原始 JSON、修改后 JSON 等协议调试入口"
    override val settingsDeveloperModeEnabled = "已开启开发者调试模式"
    override val settingsDeveloperModeDisabled = "已关闭开发者调试模式"
    override val developerModeDialogTitle = "开发者调试模式"
    override val developerModeUnlockPrompt = "请输入密码确认开启开发者调试模式："
    override val developerModeTurnOn = "确认开启"
    override val developerModeTurnOff = "关闭开发者模式"
    override val developerModeWrongPassword = "密码错误，请重新输入"
    override val developerModeKeepEnabled = "保持开启"
    override val developerModeCancel = "取消"

}

object SettingsStringsEn : SettingsStrings {
    override val settingsTitle = "Preferences & Settings"
    override val settingsSubtitle = "Manage language, appearance, proxy port and storage"
    override val settingsGeneral = "General"
    override val settingsNetwork = "Network Proxy"
    override val settingsData = "Data Storage"
    override val settingsAboutSection = "About App"
    override val settingsLanguage = "Language"
    override val settingsLanguageDescription = "Choose the application display language"
    override val settingsTheme = "Appearance Theme"
    override val settingsThemeDescription = "Use the system, light or dark appearance"
    override val settingsThemeSystem = "System Default"
    override val settingsThemeLight = "Light Theme"
    override val settingsThemeDark = "Dark Theme"
    override val settingsThemePalette = "Color Palette"
    override val settingsThemePaletteDescription = "Choose core theme color scheme (Material Design 3)"
    override val paletteWhite = "Pure White"
    override val paletteRed = "Crimson Red"
    override val paletteOrange = "Sunset Orange"
    override val paletteAmber = "Amber Gold"
    override val paletteGreen = "Emerald Green"
    override val paletteTeal = "Teal Cyan"
    override val paletteBlue = "Ocean Blue"
    override val palettePurple = "Dawn Purple"
    override val paletteDawn = "Dawn Purple"
    override val paletteDeepOcean = "Ocean Blue"
    override val paletteEmerald = "Emerald Green"
    override val paletteRose = "Crimson Red"
    override val paletteSlate = "Mountain Slate"
    override val paletteIndigo = "Aurora Indigo"
    override val paletteOcean = "Ocean Teal"
    override val paletteViolet = "Mystic Violet"
    override val settingsPort = "Studio Local Listening Port"
    override val settingsPortDescription =
        "Used by IDE, App and CLI to connect to Studio; saving restarts the local proxy service"
    override val settingsPortInvalid = "Port must be between 1024 and 65535"
    override fun settingsPortUpdated(port: Int) = "Proxy port updated to $port"
    override fun settingsPortRestartFailed(error: String) = "Failed to restart proxy on new port: $error"
    override fun settingsPortUpdateFailed(error: String) = "Failed to update proxy port: $error"
    override val settingsLocalProxyTitle = "Local Proxy Service"
    override val settingsOutboundProxyTitle = "Upstream Network Proxy"
    override val settingsOutboundProxyDescription =
        "Controls the network path Studio uses for official services and public providers; local addresses always connect directly"
    override val settingsOutboundMode = "Connection mode"
    override val settingsOutboundAuto = "Smart Select"
    override val settingsOutboundAutoDescription = "Use system or environment proxies, then fall back to direct"
    override val settingsOutboundDirect = "Always Direct"
    override val settingsOutboundDirectDescription = "Ignore system proxies and proxy environment variables"
    override val settingsOutboundSystem = "System Proxy"
    override val settingsOutboundSystemDescription = "Only use the proxy configured by the operating system"
    override val settingsOutboundManual = "Manual Proxy"
    override val settingsOutboundManualDescription = "Specify an HTTP or SOCKS5 proxy endpoint"
    override val settingsOutboundRecommended = "Recommended"
    override val settingsOutboundProtocol = "Proxy protocol"
    override val settingsOutboundHost = "Server address"
    override val settingsOutboundPort = "Port"
    override val settingsOutboundFallback = "Allow direct fallback when the proxy is unavailable"
    override val settingsOutboundFallbackDescription =
        "When disabled, a proxy connection failure ends the request without bypassing the proxy"
    override val settingsOutboundDetection = "Current network detection"
    override val settingsOutboundSystemDetected = "System proxy"
    override val settingsOutboundEnvironmentDetected = "Environment proxy"
    override val settingsOutboundEffectiveRoute = "Candidate routes"
    override val settingsOutboundNotConfigured = "Not configured"
    override val settingsOutboundDirectRoute = "Direct"
    override val settingsOutboundTest = "Test Connection"
    override val settingsOutboundTesting = "Testing"
    override val settingsOutboundSave = "Save & Apply"
    override val settingsOutboundReset = "Restore Defaults"
    override val settingsOutboundUnsaved = "Unsaved changes"
    override val settingsOutboundManualHostRequired = "Enter a proxy host without a scheme or path"
    override val settingsOutboundManualPortInvalid = "Proxy port must be between 1 and 65535"
    override val settingsOutboundSystemUnavailable =
        "No system proxy detected; requests cannot be sent with direct fallback disabled"
    override val settingsOutboundProxySaved = "Upstream network proxy settings saved and applied"
    override fun settingsOutboundProxySaveFailed(error: String) = "Failed to save upstream network proxy: $error"
    override fun settingsOutboundTestSuccess(route: String, latencyMs: Long) = "Connected · $route · ${latencyMs}ms"
    override fun settingsOutboundTestFallback(latencyMs: Long) =
        "Proxy unavailable; connected directly · ${latencyMs}ms"

    override fun settingsOutboundTestFailed(error: String) = "Connection failed: $error"
    override val settingsOpenNetworkSettings = "Open Network Settings"
    override val settingsOutboundDirectActiveDesc =
        "Current outbound route: Always direct (system & env proxies bypassed)"

    override fun settingsOutboundAutoActiveWithProxyDesc(proxy: String) =
        "Smart select: Preferred proxy ($proxy) → Direct fallback"

    override val settingsOutboundAutoActiveNoProxyDesc = "Smart select: No system proxy detected · Direct connection"
    override fun settingsOutboundSystemActiveDesc(proxy: String, fallback: Boolean) =
        "System proxy: $proxy" + if (fallback) " → Direct fallback" else " (Strict proxy only)"

    override fun settingsOutboundSystemNoProxyDesc(fallback: Boolean) =
        if (fallback) "⚠️ No system proxy detected · Fell back to direct" else "⚠️ No system proxy detected, requests will be blocked"

    override fun settingsOutboundManualActiveDesc(endpoint: String, fallback: Boolean) =
        "Manual proxy: $endpoint" + if (fallback) " → Direct fallback" else " (Strict proxy only)"

    override val settingsOutboundManualInvalidDesc = "No valid manual proxy configured"
    override val settingsHostPathsTitle = "Host Installation Paths"
    override val settingsHostPathsDesc = "Custom installation or executable paths for Antigravity IDE, App and CLI"
    override val settingsDefaultSwitchTargetTitle = "Default Switch Target"
    override val settingsDefaultSwitchTargetDesc = "Default target applications selected when switching accounts"
    override val settingsDefaultSwitchTargetAll = "All Apps (Recommended)"
    override val settingsDefaultSwitchTargetIdeOnly = "IDE Only"
    override val settingsDefaultSwitchTargetAppCliOnly = "App & CLI Only"
    override val settingsDefaultSwitchTargetRemember = "Remember Last Choice"
    override fun settingsHostPathCustom(title: String) = "$title: Custom"
    override fun settingsHostPathAuto(title: String) = "$title: Auto"
    override val settingsStoragePath = "Config File Location"
    override val settingsStorageDescription = "Inspect or back up persisted providers and compression policies"
    override val settingsOpenDirectory = "Open Directory"
    override val settingsDirectoryOpenError = "Unable to open directory"
    override val settingsUnsupportedPlatform = "Opening folder is not supported on this platform"
    override fun settingsOpenDirFailed(error: String) = "Failed to open config directory: $error"
    override val settingsAbout = "About Antigravity Studio"
    override val settingsAboutDescription =
        "A local model access tool built with Kotlin Multiplatform and Compose Desktop."
    override val settingsVersion =
        "Antigravity Studio v${com.yuzhiqiang.antigravity.update.model.AppVersion.CURRENT} · Kotlin Multiplatform & Compose Desktop"
    override val settingsRepo = "GitHub Repository"
    override val settingsConfigDir = "Config Directory"
    override val settingsOpenConfigDir = "Open data and model configuration files"
    override val settingsDeveloper = "Developer"
    override val settingsFeedback = "Feedback & Issues"
    override val settingsFeedbackDesc = "Submit issues or join community discussions"

    // Update & Version Checker
    override val updateCheck = "Check for Updates"
    override val updateChecking = "Checking for updates..."
    override val updateUpToDate = "You are up to date"
    override val updateAvailableTitle = "Update Available"
    override fun updateAvailableSubtitle(version: String) =
        "Antigravity Studio $version is now available. We recommend updating for the best experience."

    override val updateChangelogTitle = "Release Notes"
    override val updateCurrentVersionLabel = "Current Version"
    override val updateLatestVersionLabel = "Latest Version"
    override val updateDownloadNow = "Download Now"
    override val updateLater = "Remind Me Later"
    override val updateIgnoreThisVersion = "Skip This Version"
    override val updateIgnoredNotice = "This version will be skipped in future startup checks"
    override fun updateCheckFailed(error: String) = "Failed to check for updates: $error"
    override val updateNoChangelog = "No release notes provided."
    override fun updateDownloadProgress(downloaded: String, total: String, percent: Int) =
        "$downloaded / $total ($percent%)"

    override fun updateDownloadSpeed(speed: String) = "$speed/s"
    override val updateDownloading = "Downloading update…"
    override val updateDownloadCompleted = "Download complete. Opening installer…"
    override val updateInstallNow = "Install Now"
    override val updateShowInFolder = "Show in Folder"
    override fun updateDownloadFailed(error: String) = "Download failed: $error"
    override val updateRetryDownload = "Retry Download"
    override val updateOpenInBrowser = "Download in Browser"
    override val updateCancelDownload = "Cancel"
    override val settingsAutoCheckUpdate = "Check for updates on startup"
    override val settingsAutoCheckUpdateDesc =
        "Silently check for new versions on startup and notify when updates are available"
    override val settingsCheckUpdateBtn = "Check Updates"
    override val settingsCheckingUpdate = "Checking..."
    override val settingsLatestVersionBadge = "Latest"
    override val settingsNewVersionBadge = "Update"
    override fun settingsLastChecked(time: String) = "Last checked: $time"
    override val settingsDeveloperMode = "Developer Debug Mode"
    override val settingsDeveloperModeDesc = "Show raw JSON and modified JSON protocol inspection tools"
    override val settingsDeveloperModeEnabled = "Developer debug mode enabled"
    override val settingsDeveloperModeDisabled = "Developer debug mode disabled"
    override val developerModeDialogTitle = "Developer Debug Mode"
    override val developerModeUnlockPrompt = "Enter password to unlock developer debug mode:"
    override val developerModeTurnOn = "Enable"
    override val developerModeTurnOff = "Disable Developer Mode"
    override val developerModeWrongPassword = "Incorrect password, please try again"
    override val developerModeKeepEnabled = "Keep Enabled"
    override val developerModeCancel = "Cancel"

}
