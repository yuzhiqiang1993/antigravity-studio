package com.yuzhiqiang.antigravity.ui.dialogs.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckStatus
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import java.text.SimpleDateFormat
import java.util.Date

private data class BannerStyle(
    val icon: ImageVector,
    val iconColor: Color,
    val iconBg: Color,
    val titleText: String,
    val titleColor: Color,
    val borderColor: Color
)

/**
 * 诊断状态总览横幅 (DoctorBanner)：
 * - 纯净微灰通透底衬，彻底消除膏药大色块
 * - 精致暗色调状态指示图标与 Monospace 时间戳
 */
@Composable
fun DoctorBanner(report: DoctorReport) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val totalCount = report.items.size
    val passCount = report.items.count {
        it.status == DoctorCheckStatus.PASSED || it.status == DoctorCheckStatus.INFO
    }
    val issueCount = totalCount - passCount

    val statusColors = AppStatusColors
    val style = when (report.overallStatus) {
        DoctorCheckStatus.PASSED, DoctorCheckStatus.INFO -> BannerStyle(
            icon = Icons.Outlined.CheckCircle,
            iconColor = statusColors.success,
            iconBg = statusColors.success.copy(alpha = if (isDark) 0.18f else 0.10f),
            titleText = s.doctorBannerGood,
            titleColor = MaterialTheme.colorScheme.onSurface,
            borderColor = statusColors.success.copy(alpha = if (isDark) 0.35f else 0.20f)
        )
        DoctorCheckStatus.WARNING -> BannerStyle(
            icon = Icons.Outlined.WarningAmber,
            iconColor = statusColors.warning,
            iconBg = statusColors.warning.copy(alpha = if (isDark) 0.18f else 0.10f),
            titleText = s.doctorBannerWarning,
            titleColor = MaterialTheme.colorScheme.onSurface,
            borderColor = statusColors.warning.copy(alpha = if (isDark) 0.35f else 0.20f)
        )
        DoctorCheckStatus.FAILED -> BannerStyle(
            icon = Icons.Outlined.ErrorOutline,
            iconColor = statusColors.error,
            iconBg = statusColors.error.copy(alpha = if (isDark) 0.18f else 0.10f),
            titleText = s.doctorBannerError,
            titleColor = MaterialTheme.colorScheme.onSurface,
            borderColor = statusColors.error.copy(alpha = if (isDark) 0.35f else 0.20f)
        )
    }

    val statsText = s.doctorBannerStats(totalCount, passCount, issueCount)
    val sdf = remember { SimpleDateFormat("HH:mm:ss") }
    val timeStr = sdf.format(Date(report.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f))
            .border(
                width = 1.dp,
                color = style.borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态圆形微底图标
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(style.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.iconColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = style.titleText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = style.titleColor
                )

                Text(
                    text = s.doctorCheckedAt(timeStr),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
