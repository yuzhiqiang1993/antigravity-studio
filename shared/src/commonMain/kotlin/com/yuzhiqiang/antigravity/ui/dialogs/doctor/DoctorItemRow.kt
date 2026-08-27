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
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckItem
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioTonalButton
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun DoctorItemRow(
    item: DoctorCheckItem,
    onRunFix: (DoctorFixAction) -> Unit
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
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
                val s = com.yuzhiqiang.antigravity.i18n.strings()
                val suffix = s.doctorCheckIdeRunningSuffix
                val titleAnnotated = remember(item.title, suffix) {
                    buildAnnotatedString {
                        if (suffix.isNotBlank() && item.title.contains(suffix)) {
                            val parts = item.title.split(suffix)
                            append(parts[0])
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append(suffix)
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
                    is DoctorFixAction.StartProxy -> s.doctorFixStartProxy
                    is DoctorFixAction.OpenAddProvider -> s.doctorFixGoConfigure
                    is DoctorFixAction.RepairIdeSettings, is DoctorFixAction.RepairAppEnvironment -> s.doctorFixOneClickEnable
                    is DoctorFixAction.UpdateIdeSettings, is DoctorFixAction.UpdateAppEnvironment, is DoctorFixAction.UpdateCliConfig -> s.doctorFixUpdateConfig
                    is DoctorFixAction.ResetIdeHostToOfficial, is DoctorFixAction.ResetAppHostToOfficial, is DoctorFixAction.ResetCliHostToOfficial -> s.doctorFixResetOfficial
                    is DoctorFixAction.RestartIdeHost -> s.doctorFixRestartIde
                    is DoctorFixAction.RestartAppHost -> s.doctorFixRestartApp
                    is DoctorFixAction.PruneInvalidModels -> s.doctorFixPruneModels
                    is DoctorFixAction.RetestNetwork -> s.doctorFixRetry
                    is DoctorFixAction.OpenNetworkSettings -> s.doctorFixOpenNetworkSettings
                }

                StudioTonalButton(
                    text = actionLabel,
                    onClick = {
                        isActionInProgress = true
                        onRunFix(item.fixAction)
                    },
                    enabled = !isActionInProgress,
                    isLoading = isActionInProgress
                )
            } else {
                val label = when (item.status) {
                    DoctorCheckStatus.PASSED -> s.doctorPassed
                    DoctorCheckStatus.INFO -> s.doctorDirect
                    DoctorCheckStatus.WARNING -> s.doctorWarning
                    DoctorCheckStatus.FAILED -> s.doctorFailed
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
                    text = s.doctorSuggestionPrefix + item.suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
