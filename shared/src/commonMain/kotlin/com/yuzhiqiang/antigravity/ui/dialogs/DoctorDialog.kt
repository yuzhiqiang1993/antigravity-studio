package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.graphics.Brush
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
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
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
                .width(680.dp)
                .heightIn(min = 360.dp, max = 660.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(AppTokens.Radius.medium))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HealthAndSafety,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppTokens.Size.iconLarge)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                            Text(
                                text = "系统体检与全链路诊断",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "检测本地代理服务、上游模型连通性与 Antigravity 宿主接入状态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Body 内容区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    val currentReport = report
                    if (currentReport == null || isRunning) {
                        DoctorScanningView()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(AppTokens.Spacing.card),
                            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                        ) {
                            // 顶部状态总览横幅
                            item {
                                val totalCount = currentReport.items.size
                                val passCount = currentReport.items.count {
                                    it.status == DoctorCheckStatus.PASSED || it.status == DoctorCheckStatus.INFO
                                }
                                val issueCount = totalCount - passCount

                                val statusColors = AppStatusColors
                                val (bannerBg, bannerBorder, iconColor, titleText, titleColor) = when (currentReport.overallStatus) {
                                    DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> DoctorStatusStyle(
                                        bannerBg = statusColors.successContainer.copy(alpha = 0.5f),
                                        bannerBorder = statusColors.success.copy(alpha = 0.35f),
                                        iconColor = statusColors.success,
                                        titleText = "全链路状态良好，各项配置已就绪",
                                        titleColor = statusColors.onSuccessContainer
                                    )

                                    DoctorCheckStatus.WARNING -> DoctorStatusStyle(
                                        bannerBg = statusColors.warningContainer.copy(alpha = 0.5f),
                                        bannerBorder = statusColors.warning.copy(alpha = 0.35f),
                                        iconColor = statusColors.warning,
                                        titleText = "部分配置待完善",
                                        titleColor = statusColors.onWarningContainer
                                    )

                                    DoctorCheckStatus.FAILED -> DoctorStatusStyle(
                                        bannerBg = statusColors.errorContainer.copy(alpha = 0.5f),
                                        bannerBorder = statusColors.error.copy(alpha = 0.35f),
                                        iconColor = statusColors.error,
                                        titleText = "检测到系统运行异常",
                                        titleColor = statusColors.onErrorContainer
                                    )
                                }

                                val issueText = if (issueCount > 0) " • " + issueCount + " 项待处理" else ""
                                val statsText = "共 " + totalCount + " 项检测 • " + passCount + " 项正常" + issueText

                                val sdf = remember { SimpleDateFormat("HH:mm:ss") }
                                val timeStr = sdf.format(Date(currentReport.timestamp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppTokens.Radius.medium))
                                        .background(bannerBg)
                                        .border(1.dp, bannerBorder, RoundedCornerShape(AppTokens.Radius.medium))
                                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (currentReport.overallStatus) {
                                            DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> Icons.Outlined.CheckCircle
                                            DoctorCheckStatus.WARNING -> Icons.Outlined.WarningAmber
                                            DoctorCheckStatus.FAILED -> Icons.Outlined.ErrorOutline
                                        },
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(Modifier.width(AppTokens.Spacing.md))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
                                    ) {
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = titleColor
                                        )
                                        Text(
                                            text = statsText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = "体检于 " + timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 5 个分类渲染
                            val categories = listOf(
                                DoctorCheckCategory.PROXY to "本地代理服务",
                                DoctorCheckCategory.NETWORK to "官方服务连通性",
                                DoctorCheckCategory.CONFIG to "配置完整性",
                                DoctorCheckCategory.PROVIDER to "模型服务商",
                                DoctorCheckCategory.HOST to "Antigravity 宿主环境"
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer 区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRunning) "正在扫描系统环境..." else "体检结果实时生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "关闭",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Button(
                            onClick = { viewModel.runDoctor() },
                            enabled = !isRunning,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppTokens.Size.iconSmall),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(AppTokens.Spacing.xs))
                                Text("诊断中...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppTokens.Size.iconSmall)
                                )
                                Spacer(Modifier.width(AppTokens.Spacing.xs))
                                Text("重新体检", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代精致的体检扫描动效与骨架屏组合视图
 */
@Composable
private fun DoctorScanningView() {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppTokens.Spacing.card),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
    ) {
        // 主扫描指示 Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius.medium))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(AppTokens.Radius.medium)
                )
                .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "正在执行全链路健康诊断...",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "逐项排查代理端口、模型服务商网络握手与宿主接管配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 骨架卡片
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius.medium))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(AppTokens.Radius.medium))
                        .padding(AppTokens.Spacing.content)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

private data class DoctorStatusStyle(
    val bannerBg: Color,
    val bannerBorder: Color,
    val iconColor: Color,
    val titleText: String,
    val titleColor: Color
)

@Composable
private fun DoctorCategoryCard(
    title: String,
    items: List<DoctorCheckItem>,
    viewModel: AppViewModel
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    DoctorItemRow(item = item, viewModel = viewModel)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card)
                        )
                    }
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
    val statusTone = when (item.status) {
        DoctorCheckStatus.PASSED -> BadgeTone.SUCCESS
        DoctorCheckStatus.INFO -> BadgeTone.INFO
        DoctorCheckStatus.WARNING -> BadgeTone.WARNING
        DoctorCheckStatus.FAILED -> BadgeTone.ERROR
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.content),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
            ) {
                val titleAnnotated = remember(item.title) {
                    buildAnnotatedString {
                        if (item.title.contains("（未接入代理）")) {
                            val parts = item.title.split("（未接入代理）")
                            append(parts[0])
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append("（未接入代理）")
                            }
                            if (parts.size > 1) append(parts[1])
                        } else if (item.title.contains("(未接入代理)")) {
                            val parts = item.title.split("(未接入代理)")
                            append(parts[0])
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(AppTokens.Spacing.md))

            if (item.autoFixable && item.fixAction != null) {
                val actionLabel = when (item.fixAction) {
                    is DoctorFixAction.StartProxy -> "启动代理"
                    is DoctorFixAction.OpenAddProvider -> "去配置"
                    is DoctorFixAction.RepairIdeSettings, is DoctorFixAction.RepairAppEnvironment -> "一键接入"
                    is DoctorFixAction.UpdateIdeSettings, is DoctorFixAction.UpdateAppEnvironment, is DoctorFixAction.UpdateCliConfig -> "更新配置"
                    is DoctorFixAction.ResetIdeHostToOfficial, is DoctorFixAction.ResetAppHostToOfficial, is DoctorFixAction.ResetCliHostToOfficial -> "重置官方"
                    is DoctorFixAction.RestartIdeHost -> "重启 IDE"
                    is DoctorFixAction.RestartAppHost -> "重启 App"
                    is DoctorFixAction.PruneInvalidModels -> "清理模型"
                    is DoctorFixAction.RetestNetwork -> "重试"
                }

                Button(
                    onClick = {
                        isActionInProgress = true
                        viewModel.runDoctorAutoFix(item.fixAction)
                    },
                    enabled = !isActionInProgress,
                    modifier = Modifier.height(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            } else {
                val label = when (item.status) {
                    DoctorCheckStatus.PASSED -> "正常"
                    DoctorCheckStatus.INFO -> "直连"
                    DoctorCheckStatus.WARNING -> "警告"
                    DoctorCheckStatus.FAILED -> "异常"
                }

                StatusBadge(
                    text = label,
                    tone = statusTone
                )
            }
        }

        // 手动建议指引
        if (!item.autoFixable && item.suggestion != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppTokens.Spacing.md)
                    .clip(RoundedCornerShape(AppTokens.Radius.small))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.small))
                    .padding(horizontal = AppTokens.Spacing.content, vertical = AppTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 建议: " + item.suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
