package com.yuzhiqiang.antigravity.ui.dialogs.policy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.screens.usage.UsageNumberFormatter
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.statusColors

/**
 * 上下文容量分布卡片 (.policy-capacity-bar-wrapper)
 */
@Composable
fun PolicyCapacityBarCard(
    threshold: Long,
    limit: Long,
    capacity: Long
) {
    if (limit <= 0L || threshold <= 0L || capacity <= 0L) return

    val totalScale = if (capacity > limit) capacity else limit
    val thresholdPct = (threshold.toDouble() / totalScale).coerceIn(0.0, 1.0).toFloat()
    val limitPct = (limit.toDouble() / totalScale).coerceIn(0.0, 1.0).toFloat()
    val compressPct = (limitPct - thresholdPct).coerceAtLeast(0f)
    val reservePct = (1.0f - limitPct).coerceAtLeast(0f)

    val s = strings()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.small),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
        ) {
            // 顶栏 (.policy-capacity-bar-labels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.policyDistribution,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // 图例 (新版精准语义：正常对话区 / 预备存档区 / 未用物理余量)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                ) {
                    PolicyLegendDot(color = MaterialTheme.statusColors.success, label = s.policyLegendNormal)
                    PolicyLegendDot(color = MaterialTheme.statusColors.warning, label = s.policyLegendArchive)
                    PolicyLegendDot(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), label = s.policyLegendUnused)
                }
            }

            // 进度条 (.policy-capacity-bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    // 正常对话区
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(thresholdPct.coerceAtLeast(0.001f))
                            .background(MaterialTheme.statusColors.success)
                    )
                    // 预备存档区
                    if (compressPct > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(compressPct.coerceAtLeast(0.0001f))
                                .background(MaterialTheme.statusColors.warning)
                        )
                    }
                    // 未用物理余量
                    if (reservePct > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(reservePct.coerceAtLeast(0.0001f))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }

            // 刻度 (.policy-capacity-ticks)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                )
                Text(
                    text = formatTokenDisplay(threshold),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                )
                Text(
                    text = formatTokenDisplay(limit),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                )
                Text(
                    text = formatTokenDisplay(capacity),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                )
            }
        }
    }
}

@Composable
fun PolicyLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        )
    }
}

fun formatCommaNumber(number: Long): String = UsageNumberFormatter.formatCount(number)
