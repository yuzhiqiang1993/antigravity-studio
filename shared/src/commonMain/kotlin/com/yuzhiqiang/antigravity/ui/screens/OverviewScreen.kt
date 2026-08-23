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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val scrollState = rememberScrollState()
    val address = "127.0.0.1:$actualPort"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        // Topbar: 标题 + 副标题 + Material 3 规范一键体检按钮
        PageHeader(
            title = s.navOverview,
            subtitle = s.overviewSubtitle,
            action = {
                Button(
                    onClick = { viewModel.openDoctorDialog() },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.overviewDiagnostics,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        )

        // Hero Service Panel: 本地代理服务通栏卡片
        HeroProxyServiceCard(
            isRunning = isRunning,
            address = address,
            onStart = { viewModel.startProxy() },
            onStop = { viewModel.stopProxy() },
            onCopyAddress = {
                copyToClipboard("http://$address")
                viewModel.showNotice(s.overviewCopiedProxyAddress)
            }
        )

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
                        isIdeActive || isIdeRunning -> s.hostStatusRunning
                        isIdeInstalled -> s.hostStatusReady
                        else -> s.hostStatusNotInstalled
                    },
                    statusTone = when {
                        ideDetailedStatus.needsUpdate -> BadgeTone.WARNING
                        isIdeActive || isIdeRunning -> BadgeTone.SUCCESS
                        isIdeInstalled -> BadgeTone.INFO
                        else -> BadgeTone.NEUTRAL
                    },
                    desc = when {
                        ideDetailedStatus.needsUpdate -> s.hostIdePortMismatch(ideDetailedStatus.configuredEndpoint ?: s.commonUnknown)
                        isIdeRunning -> s.hostIdeRunningAndConfigured
                        isIdeInstalled -> s.hostIdeReady
                        else -> s.hostIdeNotDetected
                    },
                    isProxyActive = isIdeActive,
                    needsUpdate = ideDetailedStatus.needsUpdate,
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
                        isAppActive || isAppRunning -> s.hostStatusRunning
                        isAppInstalled -> s.hostStatusInstalled
                        else -> s.hostStatusNotInstalled
                    },
                    statusTone = when {
                        appDetailedStatus.needsUpdate -> BadgeTone.WARNING
                        isAppActive || isAppRunning -> BadgeTone.SUCCESS
                        isAppInstalled -> BadgeTone.INFO
                        else -> BadgeTone.NEUTRAL
                    },
                    desc = when {
                        appDetailedStatus.needsUpdate -> s.hostAppPortMismatch(appDetailedStatus.configuredEndpoint ?: s.commonUnknown)
                        isAppRunning -> s.hostAppRunning
                        isAppInstalled -> s.hostAppReady
                        else -> s.hostAppNotDetected
                    },
                    isProxyActive = isAppActive,
                    needsUpdate = appDetailedStatus.needsUpdate,
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
                        cliDetailedStatus.needsUpdate -> s.hostCliPortMismatch(cliDetailedStatus.configuredEndpoint ?: s.commonUnknown)
                        isCliInstalled -> s.hostCliInstalledDesc
                        else -> s.hostCliNotDetected
                    },
                    isProxyActive = isCliActive,
                    needsUpdate = cliDetailedStatus.needsUpdate,
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

private fun copyToClipboard(value: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}
