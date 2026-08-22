package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalLayoutApi::class)
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
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Topbar: 标题 + 副标题 + 紫蓝色一键体检按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "运行概览",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "管理上下文压缩配置、模型与 Antigravity 客户端的代理接入。",
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569)
                )
            }

            Button(
                onClick = { viewModel.openDoctorDialog() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text("一键体检", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Hero Service Panel: 本地代理服务通栏单行卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x18000000)),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "本地代理服务",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    // 绿色 Glow 圆点 + 运行中胶囊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(if (isRunning) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isRunning) "运行中" else "已停止",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRunning) Color(0xFF15803D) else Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // 代理服务地址代码框
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "代理服务地址",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = address,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF2563EB)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        copyToClipboard("http://$address")
                                        viewModel.showNotice("已复制代理地址")
                                    }
                                    .padding(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "复制",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // 停止代理按钮 (白底细灰框，单行展示)
                if (isRunning) {
                    OutlinedButton(
                        onClick = { viewModel.stopProxy() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF334155)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "停止代理",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.startProxy() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "启动代理",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 宿主环境卡片网格 (1:1 还原原版 2+1 网格)
        val hostCardItems = remember(
            isIdeActive, isIdeRunning, isIdeInstalled,
            isAppActive, isAppRunning, isAppInstalled,
            isCliActive, isCliInstalled
        ) {
            listOf(
                HostCardData(
                    title = "Antigravity IDE",
                    statusLabel = if (isIdeActive || isIdeRunning) "运行中" else if (isIdeInstalled) "已就绪" else "未安装",
                    isGreenStatus = isIdeActive || isIdeRunning,
                    desc = if (isIdeRunning) "Antigravity IDE 正在运行  (自定义)" else "Antigravity IDE 已安装",
                    isProxyActive = isIdeActive,
                    integrationDetail = if (isIdeActive) "代理配置正常" else "未接入代理模式",
                    onToggle = { viewModel.toggleIdeHost() },
                    onRestart = { viewModel.launchIde() },
                    onRefresh = { viewModel.refreshHostStatus() }
                ),
                HostCardData(
                    title = "Antigravity App",
                    statusLabel = if (isAppActive || isAppRunning) "运行中" else if (isAppInstalled) "已安装" else "未安装",
                    isGreenStatus = isAppActive || isAppRunning,
                    desc = if (isAppRunning) "Antigravity App 正在运行" else "Antigravity App 已安装",
                    isProxyActive = isAppActive,
                    integrationDetail = if (isAppActive) "代理配置正常" else "未接入代理模式",
                    onToggle = { viewModel.toggleAppHost() },
                    onRestart = { viewModel.launchApp() },
                    onRefresh = { viewModel.refreshHostStatus() }
                ),
                HostCardData(
                    title = "Antigravity CLI",
                    statusLabel = if (isCliInstalled) "已安装" else "未安装",
                    isGreenStatus = false,
                    desc = "Antigravity CLI 已安装",
                    isProxyActive = isCliActive,
                    integrationDetail = if (isCliActive) "CLI 已接入代理模式" else "CLI 当前处于官方直连模式",
                    onToggle = { viewModel.toggleCliHost() },
                    onRestart = null,
                    onRefresh = { viewModel.refreshHostStatus() }
                )
            )
        }

        // 宿主环境卡片网格 (1:1 还原 CSS Grid: repeat(auto-fit, minmax(300px, 1fr)))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val minCardWidth = 300.dp
            val gap = 20.dp
            // 计算当前宽度能够容纳的列数 (1, 2 或 3)
            val columns = ((availableWidth + gap) / (minCardWidth + gap)).toInt().coerceIn(1, 3)

            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                hostCardItems.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowItems.forEach { item ->
                            HostCardItem(
                                data = item,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 最后一行的空缺占位，确保卡片保持 1fr 相同宽度
                        val emptySlots = columns - rowItems.size
                        repeat(emptySlots) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class HostCardData(
    val title: String,
    val statusLabel: String,
    val isGreenStatus: Boolean,
    val desc: String,
    val isProxyActive: Boolean,
    val integrationDetail: String,
    val onToggle: () -> Unit,
    val onRestart: (() -> Unit)? = null,
    val onRefresh: () -> Unit
)

@Composable
private fun HostCardItem(
    data: HostCardData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x18000000)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 标题行：左侧标题 + 右侧药丸胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(if (data.isGreenStatus) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 3.5.dp)
                ) {
                    Text(
                        text = data.statusLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (data.isGreenStatus) Color(0xFF15803D) else Color(0xFF475569),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 标题下方的细分割线 (1:1 还原原版 panel-heading border-bottom)
            HorizontalDivider(color = Color(0x12000000), thickness = 1.dp)

            // 描述行
            Text(
                text = data.desc,
                fontSize = 13.sp,
                color = Color(0xFF475569),
                lineHeight = 18.sp,
                minLines = 1
            )

            // 内嵌代理模式子面板 (ide-integration-summary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "代理模式",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (data.isProxyActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = if (data.isProxyActive) "已启用" else "未启用",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (data.isProxyActive) Color(0xFF15803D) else Color(0xFF64748B),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Text(
                    text = data.integrationDetail,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(Modifier.height(4.dp))

            // 底部操作按钮组：左侧主操作与重启 + 右侧刷新按钮靠右对齐 (1:1 还原原版 host-actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = data.onToggle,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF334155)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (data.isProxyActive) "停用代理接入" else "启用代理模式",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (data.onRestart != null) {
                        OutlinedButton(
                            onClick = data.onRestart,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF334155)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "重启",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = data.onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF334155)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "刷新",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
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
