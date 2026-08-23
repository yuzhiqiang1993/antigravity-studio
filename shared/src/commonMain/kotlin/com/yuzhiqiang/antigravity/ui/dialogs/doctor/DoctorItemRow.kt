package com.yuzhiqiang.antigravity.ui.dialogs.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckItem
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun DoctorItemRow(
    item: DoctorCheckItem,
    onRunFix: (DoctorFixAction) -> Unit
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
                        onRunFix(item.fixAction)
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
