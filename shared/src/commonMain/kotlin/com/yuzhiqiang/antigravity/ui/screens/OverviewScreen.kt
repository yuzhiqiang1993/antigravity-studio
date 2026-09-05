package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.components.StudioTooltip
import com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.components.tour.tourAnchor
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.overview.HeroProxyServiceCard
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import com.yuzhiqiang.antigravity.ui.screens.overview.HostCardData
import com.yuzhiqiang.antigravity.ui.screens.overview.HostCardItem
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun OverviewScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tourManager = LocalSpotlightTourManager.current
    val isRunning by viewModel.isProxyRunning.collectAsState()
    val actualPort by viewModel.actualProxyPort.collectAsState()
    val isIdeActive by viewModel.isIdeHostActive.collectAsState()
    val isIdeRunning by viewModel.isIdeRunning.collectAsState()
    val isIdeInstalled by viewModel.isIdeInstalled.collectAsState()
    val isCliActive by viewModel.isCliHostActive.collectAsState()
    val isCliInstalled by viewModel.isCliInstalled.collectAsState()
    val isAppActive by viewModel.isAppHostActive.collectAsState()
    val isAppRunning by viewModel.isAppRunning.collectAsState()
    val isAppInstalled by viewModel.isAppInstalled.collectAsState()
    val ideDetailedStatus by viewModel.ideDetailedStatus.collectAsState()
    val appDetailedStatus by viewModel.appDetailedStatus.collectAsState()
    val cliDetailedStatus by viewModel.cliDetailedStatus.collectAsState()
    val config by viewModel.config.collectAsState()
    val operatingHostKeys by viewModel.operatingHostKeys.collectAsState()
    val logs by viewModel.activityLogs.collectAsState()
    val scrollState = rememberScrollState()
    val address = "127.0.0.1:$actualPort"

    val failedCount = remember(logs) { logs.count { it.statusCode >= 400 } }
    val totalRequests = logs.size
    val successRateText = remember(logs, failedCount) {
        if (logs.isNotEmpty()) {
            val rate = ((logs.size - failedCount) * 100f / logs.size).toInt()
            "$rate%"
        } else "100%"
    }
    val avgLatencyText = remember(logs) {
        logs.takeIf { it.isNotEmpty() }
            ?.map { it.durationMs }
            ?.average()
            ?.toLong()
            ?.let { formatDuration(it) } ?: "--"
    }
    val upstreamSummary = remember(config, s) {
        val providerCount = config.providers.size
        val providerModelCount = config.providerModelBindings.size
        if (providerCount > 0) {
            s.overviewCustomUpstreamSummary(providerCount, providerModelCount)
        } else {
            s.overviewOfficialDirect
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        // 顶部紧凑单行 Header: 页面标题
        PageHeader(title = s.navOverview)

        // Hero Service Panel: 本地代理服务双层控制面板（网关状态 + 遥测监控指标 + 操作中枢）
        HeroProxyServiceCard(
            isRunning = isRunning,
            address = address,
            totalRequests = totalRequests,
            successRateText = successRateText,
            avgLatencyText = avgLatencyText,
            upstreamSummary = upstreamSummary,
            onStart = { viewModel.startProxy() },
            onStop = { viewModel.stopProxy() },
            onCopyAddress = {
                if (copyToClipboard("http://$address")) {
                    viewModel.showNotice(s.overviewCopiedProxyAddress)
                }
            },
            onDiagnostics = { viewModel.openDoctorDialog() },
            modifier = Modifier.tourAnchor(TourStep.OVERVIEW_HERO_PROXY, tourManager)
        )

        // 宿主实际生效活跃账号与核心模型配额摘要 (按 IDE 与 App & CLI 两大应用环境归集)
        val accounts by viewModel.accounts.collectAsState()
        val appCliActiveEmail by viewModel.appCliActiveEmail.collectAsState()
        val ideActiveEmail by viewModel.ideActiveEmail.collectAsState()
        val activeAccount by viewModel.activeAccount.collectAsState()
        val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
        val quotas by viewModel.accountQuotas.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.syncHostAccounts()
        }

        val displayActiveAccounts = remember(
            accounts,
            appCliActiveEmail,
            ideActiveEmail,
            activeAccount,
            s
        ) {
            val sourceGroups = linkedMapOf<String, MutableList<String>>()
            listOf(
                "IDE" to ideActiveEmail,
                "App & CLI" to appCliActiveEmail
            ).forEach { (source, email) ->
                email?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                    sourceGroups.getOrPut(value.lowercase()) { mutableListOf() }.add(source)
                }
            }

            val result = sourceGroups.mapNotNull { (email, sources) ->
                val account = accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
                    ?: return@mapNotNull null
                HostActiveAccountDisplay(
                    account = account,
                    sourceLabel = s.overviewSourceInUse(sources.joinToString(" & ")),
                    isIde = sources.contains("IDE"),
                    isApp = sources.contains("App & CLI"),
                    isCli = sources.contains("App & CLI")
                )
            }.toMutableList()

            // 若未检测到任何客户端账号，则回退展示 Studio 当前生效账号
            if (result.isEmpty()) {
                activeAccount?.let { account ->
                    result.add(
                        HostActiveAccountDisplay(
                            account,
                            s.overviewActiveAccountBadge,
                            isIde = false,
                            isApp = false,
                            isCli = false
                        )
                    )
                }
            }
            result
        }

        if (displayActiveAccounts.isNotEmpty()) {
            if (displayActiveAccounts.size == 1) {
                val item = displayActiveAccounts.first()
                val activeQuota = quotas[item.account.id]
                ActiveAccountQuotaCard(
                    item = item,
                    quotaSnapshot = activeQuota,
                    isPrivacyMode = isPrivacyMode,
                    onCopyEmail = {
                        viewModel.showNotice(s.accountsCopiedEmail, NoticeKind.SUCCESS)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.card)
                ) {
                    displayActiveAccounts.forEach { item ->
                        val activeQuota = quotas[item.account.id]
                        ActiveAccountQuotaCard(
                            item = item,
                            quotaSnapshot = activeQuota,
                            isPrivacyMode = isPrivacyMode,
                            onCopyEmail = {
                                viewModel.showNotice(s.accountsCopiedEmail, NoticeKind.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }


        // 宿主环境卡片网格
        val hostCardItems = remember(
            isIdeActive, isIdeRunning, isIdeInstalled, ideDetailedStatus,
            isAppActive, isAppRunning, isAppInstalled, appDetailedStatus,
            isCliActive, isCliInstalled, cliDetailedStatus,
            operatingHostKeys, config.customHostPaths, actualPort, s
        ) {
            listOf(
                HostCardData(
                    title = "Antigravity IDE",
                    statusLabel = when {
                        ideDetailedStatus.needsUpdate -> s.hostStatusNeedsUpdate
                        isIdeRunning -> s.hostStatusRunning
                        isIdeInstalled -> s.hostStatusInstalled
                        else -> s.hostStatusNotInstalled
                    },
                    statusTone = when {
                        ideDetailedStatus.needsUpdate -> BadgeTone.WARNING
                        isIdeRunning -> BadgeTone.SUCCESS
                        isIdeInstalled -> BadgeTone.INFO
                        else -> BadgeTone.NEUTRAL
                    },
                    desc = when {
                        isIdeRunning -> s.hostIdeRunning
                        isIdeInstalled -> s.hostIdeReady
                        else -> s.hostIdeNotDetected
                    },
                    isProxyActive = isIdeActive,
                    needsUpdate = ideDetailedStatus.needsUpdate,
                    version = ideDetailedStatus.version,
                    configuredEndpoint = ideDetailedStatus.configuredEndpoint,
                    targetEndpoint = ideDetailedStatus.targetEndpoint,
                    integrationDetail = when {
                        ideDetailedStatus.needsUpdate -> s.hostIdePendingUpdate(actualPort)
                        isIdeActive -> s.hostIdeActiveDesc
                        else -> s.hostOfficialDirectDesc
                    },
                    onToggle = { viewModel.toggleIdeHost() },
                    actionLabel = if (isIdeRunning) s.hostRestart else if (isIdeInstalled) s.hostLaunch else null,
                    onAction = if (isIdeInstalled) ({ viewModel.requestRestartOrLaunchIde(isIdeRunning) }) else null,
                    onRefresh = { viewModel.refreshHostStatus() },
                    onForceReset = { viewModel.forceResetHost("ide") },
                    onConfigurePath = { viewModel.openHostPathDialog("ide", "Antigravity IDE") },
                    customPath = config.customHostPaths["ide"],
                    isLoading = "ide" in operatingHostKeys
                ),
                HostCardData(
                    title = "Antigravity App",
                    statusLabel = when {
                        appDetailedStatus.needsUpdate -> s.hostStatusNeedsUpdate
                        isAppRunning -> s.hostStatusRunning
                        isAppInstalled -> s.hostStatusInstalled
                        else -> s.hostStatusNotInstalled
                    },
                    statusTone = when {
                        appDetailedStatus.needsUpdate -> BadgeTone.WARNING
                        isAppRunning -> BadgeTone.SUCCESS
                        isAppInstalled -> BadgeTone.INFO
                        else -> BadgeTone.NEUTRAL
                    },
                    desc = when {
                        isAppRunning -> s.hostAppRunning
                        isAppInstalled -> s.hostAppReady
                        else -> s.hostAppNotDetected
                    },
                    isProxyActive = isAppActive,
                    needsUpdate = appDetailedStatus.needsUpdate,
                    version = appDetailedStatus.version,
                    configuredEndpoint = appDetailedStatus.configuredEndpoint,
                    targetEndpoint = appDetailedStatus.targetEndpoint,
                    integrationDetail = when {
                        appDetailedStatus.needsUpdate -> s.hostAppPendingUpdate(actualPort)
                        isAppActive -> s.hostAppActiveDesc
                        else -> s.hostAppOfficialDirectDesc
                    },
                    onToggle = { viewModel.toggleAppHost() },
                    actionLabel = if (isAppRunning) s.hostRestart else if (isAppInstalled) s.hostLaunch else null,
                    onAction = if (isAppInstalled) ({ viewModel.requestRestartOrLaunchApp(isAppRunning) }) else null,
                    onRefresh = { viewModel.refreshHostStatus() },
                    onForceReset = { viewModel.forceResetHost("app") },
                    onConfigurePath = { viewModel.openHostPathDialog("app", "Antigravity App") },
                    customPath = config.customHostPaths["app"],
                    isLoading = "app" in operatingHostKeys
                ),
                HostCardData(
                    title = "Antigravity CLI",
                    statusLabel = when {
                        cliDetailedStatus.needsUpdate -> s.hostStatusNeedsUpdate
                        isCliActive -> s.hostStatusActive
                        isCliInstalled -> s.hostStatusInstalled
                        else -> s.hostStatusNotInstalled
                    },
                    statusTone = when {
                        cliDetailedStatus.needsUpdate -> BadgeTone.WARNING
                        isCliActive -> BadgeTone.SUCCESS
                        isCliInstalled -> BadgeTone.INFO
                        else -> BadgeTone.NEUTRAL
                    },
                    desc = when {
                        isCliInstalled -> s.hostCliInstalledDesc
                        else -> s.hostCliNotDetected
                    },
                    isProxyActive = isCliActive,
                    needsUpdate = cliDetailedStatus.needsUpdate,
                    version = cliDetailedStatus.version,
                    configuredEndpoint = cliDetailedStatus.configuredEndpoint,
                    targetEndpoint = cliDetailedStatus.targetEndpoint,
                    integrationDetail = when {
                        cliDetailedStatus.needsUpdate -> s.hostCliPendingUpdate(actualPort)
                        isCliActive -> s.hostCliActiveDesc
                        else -> s.hostCliOfficialDirectDesc
                    },
                    onToggle = { viewModel.toggleCliHost() },
                    actionLabel = s.hostCopyCliLaunchCommand,
                    onAction = { viewModel.copyCliLaunchCommand() },
                    onRefresh = { viewModel.refreshHostStatus() },
                    onForceReset = { viewModel.forceResetHost("cli") },
                    onConfigurePath = { viewModel.openHostPathDialog("cli", "Antigravity CLI") },
                    customPath = config.customHostPaths["cli"],
                    isLoading = "cli" in operatingHostKeys
                )
            )
        }

        // 宿主环境卡片 (固定平铺 3 列并排布局，使用 IntrinsicSize.Max 确保各卡片高度物理对齐，杜绝高度参差不齐)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .tourAnchor(TourStep.OVERVIEW_HOST_GRID, tourManager),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.card)
        ) {
            hostCardItems.forEach { item ->
                key(item.title) {
                    HostCardItem(
                        data = item,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        listOfNotNull(appDetailedStatus.externalEndpoint, cliDetailedStatus.externalEndpoint).distinct().forEach { endpoint ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.card),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.hostExternalEnvironmentNotice(endpoint),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { viewModel.migrateSharedHostEnvironment() }) {
                    Text(s.hostMigrateSharedEnvironment)
                }
            }
        }

        // 底部生态联动卡片：推荐搭配 Antigravity Cockpit 插件
        EcosystemCockpitBanner()
    }
}

@Composable
private fun EcosystemCockpitBanner(
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isInstalled = remember { com.yuzhiqiang.antigravity.host.ide.CockpitPluginDetector.isInstalled() }
    var isDismissed by remember { mutableStateOf(false) }
    if (isInstalled || isDismissed) return

    val brandColor = MaterialTheme.colorScheme.primary
    val cardBg = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = if (isDark) {
        brandColor.copy(alpha = 0.35f)
    } else {
        brandColor.copy(alpha = 0.25f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 左侧 Logo 容器
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = brandColor.copy(alpha = if (isDark) 0.25f else 0.15f),
                    border = BorderStroke(1.dp, brandColor.copy(alpha = 0.4f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 中间标题与特性亮点
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
                ) {
                    Text(
                        text = s.ecosystemCockpitTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = s.ecosystemCockpitSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        listOf(
                            s.ecosystemCockpitFeature1,
                            s.ecosystemCockpitFeature2,
                            s.ecosystemCockpitFeature3
                        ).forEach { feature ->
                            Surface(
                                shape = RoundedCornerShape(AppTokens.Radius.pill),
                                color = brandColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                border = BorderStroke(0.5.dp, brandColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = feature,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // 右侧操作区
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService.openBrowser(
                            "https://open-vsx.org/extension/yuzhiqiang/antigravity-ide-cockpit"
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = s.ecosystemCockpitOpenVsxBtn,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)
                    )
                }

                Button(
                    onClick = {
                        com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService.openBrowser(
                            "https://agycockpit.com/"
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Text(
                        text = s.ecosystemCockpitWebsiteBtn,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                IconButton(
                    onClick = { isDismissed = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = s.commonClose,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private data class HostActiveAccountDisplay(
    val account: AccountInfo,
    val sourceLabel: String,
    val isIde: Boolean,
    val isApp: Boolean,
    val isCli: Boolean
)

@Composable
private fun ActiveAccountQuotaCard(
    item: HostActiveAccountDisplay,
    quotaSnapshot: com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot?,
    isPrivacyMode: Boolean,
    onCopyEmail: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val acc = item.account
    val displayEmail = if (isPrivacyMode) acc.maskedEmail() else acc.email
    val badgeTone = if (item.isIde) BadgeTone.INFO else BadgeTone.SUCCESS

    val tier: AccountTier = when {
        quotaSnapshot?.tier == AccountTier.ULTRA ||
                quotaSnapshot?.tierName?.contains("ultra", ignoreCase = true) == true ||
                acc.profile.tier == AccountTier.ULTRA -> AccountTier.ULTRA

        quotaSnapshot?.tier == AccountTier.ENTERPRISE ||
                quotaSnapshot?.tierName?.contains("enterprise", ignoreCase = true) == true ||
                acc.profile.tier == AccountTier.ENTERPRISE -> AccountTier.ENTERPRISE

        quotaSnapshot?.tier == AccountTier.PRO ||
                quotaSnapshot?.isPro == true ||
                quotaSnapshot?.tierName?.contains("pro", ignoreCase = true) == true ||
                acc.profile.tier == AccountTier.PRO -> AccountTier.PRO

        else -> AccountTier.FREE
    }

    val badgeBg = when (tier) {
        AccountTier.ULTRA -> MaterialTheme.colorScheme.tertiaryContainer
        AccountTier.PRO -> MaterialTheme.colorScheme.primaryContainer
        AccountTier.ENTERPRISE -> MaterialTheme.colorScheme.secondaryContainer
        AccountTier.FREE -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeText = when (tier) {
        AccountTier.ULTRA -> MaterialTheme.colorScheme.onTertiaryContainer
        AccountTier.PRO -> MaterialTheme.colorScheme.onPrimaryContainer
        AccountTier.ENTERPRISE -> MaterialTheme.colorScheme.onSecondaryContainer
        AccountTier.FREE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val badgeLabel = when (tier) {
        AccountTier.ULTRA -> "Ultra"
        AccountTier.PRO -> "Pro"
        AccountTier.ENTERPRISE -> "Enterprise"
        AccountTier.FREE -> "Free"
    }

    val cardBg = StudioGlassTokens.cardBackgroundColor(isDark)
    val borderColor = StudioGlassTokens.cleanBorderColor(isDark)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = StudioGlassTokens.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === 头部第一行: 宿主正在使用状态徽标 + 右侧订阅等级徽章 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = item.sourceLabel,
                    tone = badgeTone,
                    pulse = true
                )

                Surface(
                    shape = RoundedCornerShape(AppTokens.Radius.pill),
                    color = badgeBg.copy(alpha = if (isDark) 0.55f else 0.70f),
                    border = BorderStroke(1.dp, badgeText.copy(alpha = 0.20f))
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // === 头部第二行: 纯邮箱地址独立成行 (支持点击复制) ===
            val emailInteractionSource = remember { MutableInteractionSource() }
            val isEmailHovered by emailInteractionSource.collectIsHoveredAsState()

            StudioTooltip(text = s.accountsEmailTooltip(acc.email)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.xs))
                        .background(
                            if (isEmailHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.10f else 0.06f)
                            else Color.Transparent
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = emailInteractionSource,
                            indication = null
                        ) {
                            if (copyToClipboard(acc.email)) {
                                onCopyEmail?.invoke(acc.email)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = displayEmail,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = if (isEmailHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            // === 配额展示区 ===
            if (quotaSnapshot != null) {
                com.yuzhiqiang.antigravity.ui.components.CompactDualQuotaBar(quotaSnapshot = quotaSnapshot)
            } else {
                val s = strings()
                Text(
                    text = s.overviewSyncingQuotas,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
