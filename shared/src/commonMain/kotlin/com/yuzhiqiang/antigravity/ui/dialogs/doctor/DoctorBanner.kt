package com.yuzhiqiang.antigravity.ui.dialogs.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.text.SimpleDateFormat
import java.util.Date

private data class DoctorStatusStyle(
    val bannerBg: Color,
    val bannerBorder: Color,
    val iconColor: Color,
    val titleText: String,
    val titleColor: Color
)

@Composable
fun DoctorBanner(report: DoctorReport) {
    val totalCount = report.items.size
    val passCount = report.items.count {
        it.status == DoctorCheckStatus.PASSED || it.status == DoctorCheckStatus.INFO
    }
    val issueCount = totalCount - passCount

    val statusColors = AppStatusColors
    val style = when (report.overallStatus) {
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

    val issueText = if (issueCount > 0) " • " + issueCount + " 项待处理" else ""
    val statsText = "共 " + totalCount + " 项检测 • " + passCount + " 项正常" + issueText

    val sdf = remember { SimpleDateFormat("HH:mm:ss") }
    val timeStr = sdf.format(Date(report.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.Radius.medium))
            .background(style.bannerBg)
            .border(1.dp, style.bannerBorder, RoundedCornerShape(AppTokens.Radius.medium))
            .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (report.overallStatus) {
                DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> Icons.Outlined.Security
                DoctorCheckStatus.WARNING -> Icons.Outlined.WarningAmber
                DoctorCheckStatus.FAILED -> Icons.Outlined.ErrorOutline
            },
            contentDescription = null,
            tint = style.iconColor,
            modifier = Modifier.size(AppTokens.Size.iconLarge)
        )

        Spacer(Modifier.width(AppTokens.Spacing.md))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
        ) {
            Text(
                text = style.titleText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = style.titleColor
            )
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "体检时间: " + timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
