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
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.content)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.Size.iconMedium)
                    )
                    Spacer(Modifier.width(AppTokens.Spacing.sm))
                    Text(
                        text = "一键体检",
                        style = MaterialTheme.typography.labelLarge
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
                viewModel.showNotice("已复制代理地址")
            }
        )

        // 宿主环境卡片网格
        val hostCardItems = remember(
            isIdeActive, isIdeRunning, isIdeInstalled,
            isAppActive, isAppRunning, isAppInstalled,
            isCliActive, isCliInstalled
        ) {
            listOf(
                HostCardData(
                    title = "Antigravity IDE",
                    statusLabel = if (isIdeActive || isIdeRunning) "运行中" else if (isIdeInstalled) "已就绪" else "未安装",
                    statusTone = if (isIdeActive || isIdeRunning) BadgeTone.SUCCESS else if (isIdeInstalled) BadgeTone.INFO else BadgeTone.NEUTRAL,
                    desc = if (isIdeRunning) "Antigravity IDE 正在运行并已配置" else if (isIdeInstalled) "Antigravity IDE 已安装就绪" else "未检测到 Antigravity IDE 安装目录",
                    isProxyActive = isIdeActive,
                    integrationDetail = if (isIdeActive) "settings.json 代理接入生效中" else "当前使用官方直连模式",
                    onToggle = { viewModel.toggleIdeHost() },
                    actionLabel = if (isIdeRunning) "重启" else if (isIdeInstalled) "打开" else null,
                    onAction = if (isIdeInstalled) ({ viewModel.requestRestartOrLaunchIde(isIdeRunning) }) else null,
                    onRefresh = { viewModel.refreshHostStatus() }
                ),
                HostCardData(
                    title = "Antigravity App",
                    statusLabel = if (isAppActive || isAppRunning) "运行中" else if (isAppInstalled) "已安装" else "未安装",
                    statusTone = if (isAppActive || isAppRunning) BadgeTone.SUCCESS else if (isAppInstalled) BadgeTone.INFO else BadgeTone.NEUTRAL,
                    desc = if (isAppRunning) "Antigravity App 正在运行" else if (isAppInstalled) "Antigravity App 已安装就绪" else "未检测到 Antigravity App 应用安装",
                    isProxyActive = isAppActive,
                    integrationDetail = if (isAppActive) "环境变量 CLOUD_CODE_URL 代理生效中" else "当前使用官方直连模式",
                    onToggle = { viewModel.toggleAppHost() },
                    actionLabel = if (isAppRunning) "重启" else if (isAppInstalled) "打开" else null,
                    onAction = if (isAppInstalled) ({ viewModel.requestRestartOrLaunchApp(isAppRunning) }) else null,
                    onRefresh = { viewModel.refreshHostStatus() }
                ),
                HostCardData(
                    title = "Antigravity CLI",
                    statusLabel = if (isCliInstalled) "已安装" else "未安装",
                    statusTone = if (isCliActive) BadgeTone.SUCCESS else if (isCliInstalled) BadgeTone.INFO else BadgeTone.NEUTRAL,
                    desc = if (isCliInstalled) "Antigravity CLI (agy) 已安装" else "未检测到 agy CLI 配置文件",
                    isProxyActive = isCliActive,
                    integrationDetail = if (isCliActive) "CLI 配置文件代理接入生效中" else "CLI 当前处于官方直连模式",
                    onToggle = { viewModel.toggleCliHost() },
                    actionLabel = null,
                    onAction = null,
                    onRefresh = { viewModel.refreshHostStatus() }
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
