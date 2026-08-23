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
    val s = com.yuzhiqiang.antigravity.i18n.strings()
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
            titleText = s.doctorBannerGood,
            titleColor = statusColors.onSuccessContainer
        )
        DoctorCheckStatus.WARNING -> DoctorStatusStyle(
            bannerBg = statusColors.warningContainer,
            bannerBorder = statusColors.warning.copy(alpha = 0.3f),
            iconColor = statusColors.warning,
            titleText = s.doctorBannerWarning,
            titleColor = statusColors.onWarningContainer
        )
        DoctorCheckStatus.FAILED -> DoctorStatusStyle(
            bannerBg = statusColors.errorContainer,
            bannerBorder = statusColors.error.copy(alpha = 0.3f),
            iconColor = statusColors.error,
            titleText = s.doctorBannerError,
            titleColor = statusColors.onErrorContainer
        )
    }

    val statsText = s.doctorBannerStats(totalCount, passCount, issueCount)

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
            text = s.doctorCheckedAt(timeStr),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
