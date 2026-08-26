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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.overview.HeroProxyServiceCard
import com.yuzhiqiang.antigravity.ui.screens.overview.HostCardData
import com.yuzhiqiang.antigravity.ui.screens.overview.HostCardItem
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
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
    val upstreamSummary = remember(config) {
        val providerCount = config.providers.size
        val upstreamModelCount = config.upstreamModels.size
        if (providerCount > 0) {
            "$providerCount 个服务商 · $upstreamModelCount 个模型"
        } else {
            "官方默认直连通道"
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
                copyToClipboard("http://$address")
                viewModel.showNotice(s.overviewCopiedProxyAddress)
            },
            onDiagnostics = { viewModel.openDoctorDialog() }
        )

        // 宿主实际生效活跃账号与核心模型配额摘要 (同时适配双端不同账号或统一单账号)
        val accounts by viewModel.accounts.collectAsState()
        val cliActiveEmail by viewModel.cliActiveEmail.collectAsState()
        val ideActiveEmail by viewModel.ideActiveEmail.collectAsState()
        val activeAccount by viewModel.activeAccount.collectAsState()
        val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
        val quotas by viewModel.accountQuotas.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.syncHostAccounts()
        }

        val displayActiveAccounts = remember(accounts, cliActiveEmail, ideActiveEmail, activeAccount) {
            val result = mutableListOf<HostActiveAccountDisplay>()
            val seenEmails = mutableSetOf<String>()

            val hasCli = !cliActiveEmail.isNullOrBlank()
            val hasIde = !ideActiveEmail.isNullOrBlank()
            val isSameEmail = hasCli && hasIde && cliActiveEmail.equals(ideActiveEmail, ignoreCase = true)

            if (isSameEmail) {
                // IDE、App 与 CLI 均登录使用同一个账号 (展示单个全宽卡片，标注全端正在使用)
                val acc = accounts.firstOrNull { it.email.equals(cliActiveEmail, ignoreCase = true) }
                    ?: activeAccount
                if (acc != null && seenEmails.add(acc.email.lowercase())) {
                    result.add(HostActiveAccountDisplay(acc, "IDE & App/CLI 正在使用", isIde = true, isCli = true))
                }
            } else {
                // App / CLI 宿主账号
                if (hasCli) {
                    val acc = accounts.firstOrNull { it.email.equals(cliActiveEmail, ignoreCase = true) }
                    if (acc != null && seenEmails.add(acc.email.lowercase())) {
                        result.add(HostActiveAccountDisplay(acc, "App/CLI 正在使用", isIde = false, isCli = true))
                    }
                }
                // IDE 宿主账号
                if (hasIde) {
                    val acc = accounts.firstOrNull { it.email.equals(ideActiveEmail, ignoreCase = true) }
                    if (acc != null && seenEmails.add(acc.email.lowercase())) {
                        result.add(HostActiveAccountDisplay(acc, "IDE 正在使用", isIde = true, isCli = false))
                    }
                }
            }

            // 若未探测到任何宿主账号，则回退展示 Studio 当前激活账号
            if (result.isEmpty() && activeAccount != null) {
                result.add(HostActiveAccountDisplay(activeAccount!!, "激活账号", isIde = false, isCli = false))
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
                        else -> s.hostOfficialDirectDesc
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
                    actionLabel = null,
                    onAction = null,
                    onRefresh = { viewModel.refreshHostStatus() },
                    onForceReset = { viewModel.forceResetHost("cli") },
                    onConfigurePath = { viewModel.openHostPathDialog("cli", "Antigravity CLI") },
                    customPath = config.customHostPaths["cli"],
                    isLoading = "cli" in operatingHostKeys
                )
            )
        }

        // 宿主环境卡片 (固定平铺 3 列并排布局，彻底杜绝缩放过程中的跨行折叠与高度抖动)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.card)
        ) {
            hostCardItems.forEach { item ->
                key(item.title) {
                    HostCardItem(
                        data = item,
                        modifier = Modifier.weight(1f)
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
    val isCli: Boolean
)

@Composable
private fun ActiveAccountQuotaCard(
    item: HostActiveAccountDisplay,
    quotaSnapshot: com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot?,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
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

    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                    shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = StudioDesignTokens.TextSize.badge,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // === 头部第二行: 纯邮箱地址独立成行 ===
            Text(
                text = displayEmail,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            // === 配额展示区 ===
            if (quotaSnapshot != null) {
                com.yuzhiqiang.antigravity.ui.components.CompactDualQuotaBar(quotaSnapshot = quotaSnapshot)
            } else {
                Text(
                    text = "正在同步配额数据...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyToClipboard(value: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}
