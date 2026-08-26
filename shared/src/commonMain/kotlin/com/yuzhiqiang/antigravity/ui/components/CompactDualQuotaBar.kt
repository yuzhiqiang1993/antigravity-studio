package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaGroup
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow
import com.yuzhiqiang.antigravity.ui.animation.*

/**
 * 完全对齐 Cockpit 插件的紧凑双限额微进度条行：
 * Gemini  5小时 [▬] 65%   周 [▬] 61%
 * Claude  5小时 [▬] 100%  周 [▬] 100%
 */
@Composable
fun CompactDualQuotaBar(
    quotaSnapshot: AccountQuotaSnapshot?,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val groups = quotaSnapshot?.normalizedDisplayGroups().orEmpty()

    StudioCrossfade(
        targetState = groups.isNotEmpty(),
        label = "compact_quota_crossfade"
    ) { hasData ->
        if (!hasData) {
            // 骨架屏加载条
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactSkeletonRow(label = "Gemini", isDark = isDark)
                CompactSkeletonRow(label = "Claude", isDark = isDark)
            }
        } else {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.take(2).forEach { group ->
                    CompactGroupRow(group = group)
                }
            }
        }
    }
}

@Composable
private fun CompactSkeletonRow(label: String, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(60.dp)
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .studioShimmer(shape = RoundedCornerShape(3.dp), isDark = isDark)
        )

        Spacer(Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .studioShimmer(shape = RoundedCornerShape(3.dp), isDark = isDark)
        )
    }
}

@Composable
private fun CompactGroupRow(
    group: QuotaGroup
) {
    val fiveHour = group.buckets.firstOrNull { it.window == QuotaWindow.FIVE_HOUR }
        ?: group.buckets.firstOrNull()
    val weekly = group.buckets.firstOrNull { it.window == QuotaWindow.WEEKLY }
        ?: group.buckets.getOrNull(1)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Family Name (如 "Gemini" / "Claude")
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp)
        )

        Spacer(Modifier.width(8.dp))

        // 5小时限额
        if (fiveHour != null) {
            MiniBarCell(
                label = "5小时",
                percentage = fiveHour.percentage,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.width(16.dp))

        // 周额度
        if (weekly != null) {
            MiniBarCell(
                label = "周",
                percentage = weekly.percentage,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniBarCell(
    label: String,
    percentage: Int,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val targetPct = percentage.coerceIn(0, 100)

    val animatedProgress by rememberAnimatedQuotaProgress(targetPercentage = targetPct)
    val animatedPctFloat by rememberAnimatedQuotaPercentage(targetPercentage = targetPct)

    val barColor = com.yuzhiqiang.antigravity.ui.theme.StudioThemeColors.quotaColor(targetPct, isDark)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Progress Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }

        Text(
            text = "${animatedPctFloat.toInt()}%",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = barColor,
            modifier = Modifier.width(38.dp)
        )
    }
}


