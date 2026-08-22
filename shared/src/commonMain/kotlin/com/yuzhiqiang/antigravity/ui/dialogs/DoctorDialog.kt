package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckCategory
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckItem
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun DoctorDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val report by viewModel.doctorReport.collectAsState()
    val isRunning by viewModel.isDoctorRunning.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(720.dp)
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ==========================================
                // 1. Header 区域 (带底部细分割线)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "系统体检与全链路诊断",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = "检测本地代理服务、上游模型连通性与 Antigravity 宿主接入状态",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // ==========================================
                // 2. 中间内容滚动区
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    val currentReport = report
                    if (currentReport == null || isRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "正在进行全链路健康体检...",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // (1) 顶部状态横幅 (Dashboard Status Banner)
                            item {
                                val totalCount = currentReport.items.size
                                val passCount = currentReport.items.count {
                                    it.status == DoctorCheckStatus.PASSED || it.status == DoctorCheckStatus.INFO
                                }
                                val issueCount = totalCount - passCount

                                val (bannerBg, bannerBorder, iconBg, iconColor, titleText, titleColor) = when (currentReport.overallStatus) {
                                    DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> Tuple6(
                                        Color(0xFFF0FDF4),
                                        Color(0xFFBBF7D0),
                                        Color(0xFFDCFCE7),
                                        Color(0xFF16A34A),
                                        "全链路状态良好，各项配置已就绪",
                                        Color(0xFF0F172A)
                                    )

                                    DoctorCheckStatus.WARNING -> Tuple6(
                                        Color(0xFFFFFBEB),
                                        Color(0xFFFDE68A),
                                        Color(0xFFFEF3C7),
                                        Color(0xFFD97706),
                                        "部分配置待完善",
                                        Color(0xFF92400E)
                                    )

                                    DoctorCheckStatus.FAILED -> Tuple6(
                                        Color(0xFFFEF2F2),
                                        Color(0xFFFECACA),
                                        Color(0xFFFFE4E6),
                                        Color(0xFFDC2626),
                                        "检测到系统运行异常",
                                        Color(0xFF991B1B)
                                    )
                                }

                                val issueText = if (issueCount > 0) " • $issueCount 项待处理" else ""
                                val statsText = "共 $totalCount 项检测 • $passCount 项正常$issueText"

                                val sdf = remember { SimpleDateFormat("HH:mm:ss") }
                                val timeStr = sdf.format(Date(currentReport.timestamp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bannerBg)
                                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Banner 图标
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(iconBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (currentReport.overallStatus) {
                                                DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> Icons.Outlined.Security
                                                DoctorCheckStatus.WARNING -> Icons.Outlined.WarningAmber
                                                DoctorCheckStatus.FAILED -> Icons.Outlined.ErrorOutline
                                            },
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = titleText,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = titleColor
                                        )
                                        Text(
                                            text = statsText,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Text(
                                        text = "体检时间: $timeStr",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // (2) 5 个分类按原版顺序渲染
                            val categories = listOf(
                                DoctorCheckCategory.PROXY to "⚡ 本地代理",
                                DoctorCheckCategory.NETWORK to "🌐 官方服务连通性",
                                DoctorCheckCategory.CONFIG to "📦 配置完整性",
                                DoctorCheckCategory.PROVIDER to "🪐 模型提供商",
                                DoctorCheckCategory.HOST to "💻 ANTIGRAVITY 宿主"
                            )

                            for ((cat, title) in categories) {
                                val catItems = currentReport.items.filter { it.category == cat }
                                if (catItems.isNotEmpty()) {
                                    item {
                                        DoctorCategoryCard(
                                            title = title,
                                            items = catItems,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // ==========================================
                // 3. Footer 区域 (底栏背景与按钮)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "关闭",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRunning) Color(0xFF93C5FD) else Color(0xFF2563EB))
                            .clickable(enabled = !isRunning) { viewModel.runDoctor() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRunning) "检测中..." else "重新检测",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorCategoryCard(
    title: String,
    items: List<DoctorCheckItem>,
    viewModel: AppViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
    ) {
        // 卡片 Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.2.sp
            )
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        // 内部条目列表
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                DoctorItemRow(item = item, viewModel = viewModel)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorItemRow(
    item: DoctorCheckItem,
    viewModel: AppViewModel
) {
    var isActionInProgress by remember { mutableStateOf(false) }

    val dotColor = when (item.status) {
        DoctorCheckStatus.PASSED -> Color(0xFF10B981)
        DoctorCheckStatus.INFO -> Color(0xFFF59E0B)
        DoctorCheckStatus.WARNING -> Color(0xFFF59E0B)
        DoctorCheckStatus.FAILED -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 主体行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧状态圆点
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(Modifier.width(10.dp))

            // 中间文本列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val titleAnnotated = remember(item.title) {
                    buildAnnotatedString {
                        if (item.title.contains("（未接入代理）")) {
                            val parts = item.title.split("（未接入代理）")
                            append(parts[0])
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFB45309),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append("（未接入代理）")
                            }
                            if (parts.size > 1) append(parts[1])
                        } else if (item.title.contains("(未接入代理)")) {
                            val parts = item.title.split("(未接入代理)")
                            append(parts[0])
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFB45309),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append("(未接入代理)")
                            }
                            if (parts.size > 1) append(parts[1])
                        } else {
                            append(item.title)
                        }
                    }
                }

                Text(
                    text = titleAnnotated,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Text(
                    text = item.message,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // 右侧区域：操作按钮 vs 状态胶囊
            if (item.autoFixable && item.fixAction != null) {
                val actionLabel = when (item.fixAction) {
                    is DoctorFixAction.StartProxy -> "启动代理"
                    is DoctorFixAction.OpenAddProvider -> "去配置"
                    is DoctorFixAction.RepairIdeSettings, is DoctorFixAction.RepairAppEnvironment -> "一键接入"
                    is DoctorFixAction.RestartIdeHost -> "重启 IDE"
                    is DoctorFixAction.RestartAppHost -> "重启 App"
                    is DoctorFixAction.PruneInvalidModels -> "清理模型"
                    is DoctorFixAction.RetestNetwork -> "重试"
                }

                Box(
                    modifier = Modifier
                        .height(25.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isActionInProgress) Color(0xFF93C5FD) else Color(0xFF2563EB))
                        .clickable(enabled = !isActionInProgress) {
                            isActionInProgress = true
                            viewModel.runDoctorAutoFix(item.fixAction)
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = Color.White,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = actionLabel,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            } else {
                val (pillBg, pillBorder, pillText, label) = when (item.status) {
                    DoctorCheckStatus.PASSED -> Tuple4(Color(0xFFF0FDF4), Color(0xFFBBF7D0), Color(0xFF16A34A), "正常")
                    DoctorCheckStatus.INFO -> Tuple4(Color(0xFFFFFBEB), Color(0xFFFDE68A), Color(0xFFD97706), "直连")
                    DoctorCheckStatus.WARNING -> Tuple4(Color(0xFFFFFBEB), Color(0xFFFDE68A), Color(0xFFD97706), "警告")
                    DoctorCheckStatus.FAILED -> Tuple4(Color(0xFFFEF2F2), Color(0xFFFECACA), Color(0xFFDC2626), "异常")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillText
                    )
                }
            }
        }

        // 手动指引建议行
        if (!item.autoFixable && item.suggestion != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFEFF6FF))
                    .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 建议: ${item.suggestion}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF1D4ED8),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// 辅助多元组数据类
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Tuple6<A, B, C, D, E, F>(
    val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F
)
