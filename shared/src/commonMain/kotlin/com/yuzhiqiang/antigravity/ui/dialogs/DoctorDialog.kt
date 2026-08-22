package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckCategory
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckItem
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
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
                .fillMaxWidth(0.92f)
                .widthIn(max = AppTokens.Size.doctorDialogWidth)
                .fillMaxHeight(0.9f)
                .heightIn(max = AppTokens.Size.doctorDialogMaxHeight),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header 区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                        Text(
                            text = "系统体检与全链路诊断",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "检测本地代理服务、上游模型连通性与 Antigravity 宿主接入状态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                // 中间内容滚动区
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
                                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(AppTokens.Size.iconLarge)
                                )
                                Text(
                                    text = "正在进行全链路健康体检...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(AppTokens.Spacing.card),
                            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                        ) {
                            // 顶部状态横幅
                            item {
                                val totalCount = currentReport.items.size
                                val passCount = currentReport.items.count {
                                    it.status == DoctorCheckStatus.PASSED || it.status == DoctorCheckStatus.INFO
                                }
                                val issueCount = totalCount - passCount

                                val statusColors = AppStatusColors
                                val (bannerBg, bannerBorder, iconColor, titleText, titleColor) = when (currentReport.overallStatus) {
                                    DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> DoctorStatusStyle(
                                        bannerBg = statusColors.successContainer,
                                        bannerBorder = statusColors.success.copy(alpha = 0.3f),
                                        iconColor = statusColors.success,
                                        titleText = "全链路状态良好，各项配置已就绪",
                                        titleColor = statusColors.onSuccessContainer
                                    )

                                    DoctorCheckStatus.WARNING -> DoctorStatusStyle(
                                        bannerBg = statusColors.warningContainer,
                                        bannerBorder = statusColors.warning.copy(alpha = 0.3f),
                                        iconColor = statusColors.warning,
                                        titleText = "部分配置待完善",
                                        titleColor = statusColors.onWarningContainer
                                    )

                                    DoctorCheckStatus.FAILED -> DoctorStatusStyle(
                                        bannerBg = statusColors.errorContainer,
                                        bannerBorder = statusColors.error.copy(alpha = 0.3f),
                                        iconColor = statusColors.error,
                                        titleText = "检测到系统运行异常",
                                        titleColor = statusColors.onErrorContainer
                                    )
                                }

                                val issueText = if (issueCount > 0) " • $issueCount 项待处理" else ""
                                val statsText = "共 $totalCount 项检测 • $passCount 项正常$issueText"

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
                                            DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> Icons.Outlined.Security
                                            DoctorCheckStatus.WARNING -> Icons.Outlined.WarningAmber
                                            DoctorCheckStatus.FAILED -> Icons.Outlined.ErrorOutline
                                        },
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(AppTokens.Size.iconLarge)
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
                                        text = "体检时间: $timeStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 5 个分类渲染
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer 区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.content),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.width(AppTokens.Spacing.sm))

                    Button(
                        onClick = { viewModel.runDoctor() },
                        enabled = !isRunning,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.xs)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AppTokens.Size.iconSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(AppTokens.Spacing.xs))
                            Text("检测中...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.Size.iconSmall)
                            )
                            Spacer(Modifier.width(AppTokens.Spacing.xs))
                            Text("重新检测", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private data class DoctorStatusStyle(
    val bannerBg: androidx.compose.ui.graphics.Color,
    val bannerBorder: androidx.compose.ui.graphics.Color,
    val iconColor: androidx.compose.ui.graphics.Color,
    val titleText: String,
    val titleColor: androidx.compose.ui.graphics.Color
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    DoctorItemRow(item = item, viewModel = viewModel)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                            withStyle(
                                SpanStyle(
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
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
                    is DoctorFixAction.RestartIdeHost -> "重启 IDE"
                    is DoctorFixAction.RestartAppHost -> "重启 App"
                    is DoctorFixAction.PruneInvalidModels -> "清理模型"
                    is DoctorFixAction.RetestNetwork -> "重试"
                }

                FilledTonalButton(
                    onClick = {
                        isActionInProgress = true
                        viewModel.runDoctorAutoFix(item.fixAction)
                    },
                    enabled = !isActionInProgress,
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing.content, vertical = 2.dp)
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppTokens.Size.iconSmall),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
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
                    text = "💡 建议: ${item.suggestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
