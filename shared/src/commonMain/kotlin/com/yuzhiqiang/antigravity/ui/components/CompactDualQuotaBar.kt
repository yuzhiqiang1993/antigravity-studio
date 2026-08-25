package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaGroup
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow

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
    if (quotaSnapshot == null) return

    val groups = quotaSnapshot.normalizedDisplayGroups()
    if (groups.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.take(2).forEach { group ->
            CompactGroupRow(group = group)
        }
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
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )

        Spacer(Modifier.width(4.dp))

        // 5小时限额
        if (fiveHour != null) {
            MiniBarCell(
                label = "5小时",
                percentage = fiveHour.percentage,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.width(12.dp))

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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350)
    )

    val barColor = com.yuzhiqiang.antigravity.ui.theme.StudioThemeColors.quotaColor(percentage, isDark)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Progress Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.progressBarHeight)
                .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.pill))
                    .background(barColor)
            )
        }

        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body
            ),
            color = barColor,
            modifier = Modifier.width(38.dp)
        )
    }
}


