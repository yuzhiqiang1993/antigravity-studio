package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot

/**
 * 完全对齐 Cockpit 插件顶部的多账号健康点列展示：
 * ●●●●●●●●●●●●●●●● 16 个账号
 */
@Composable
fun AccountHealthDotList(
    accounts: List<AccountInfo>,
    quotas: Map<String, AccountQuotaSnapshot>,
    modifier: Modifier = Modifier
) {
    if (accounts.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            accounts.forEach { account ->
                val snapshot = quotas[account.id]
                val dotColor = when {
                    account.status == AccountStatus.ERROR || account.tokens.isExpired() -> Color(0xFFFF5252)
                    snapshot != null && snapshot.lowestQuotaPct() <= 15 -> Color(0xFFFF5252)
                    snapshot != null && snapshot.lowestQuotaPct() <= 40 -> Color(0xFFFFB74D)
                    else -> Color(0xFF00E676)
                }

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }

        Text(
            text = "${accounts.size} 个账号",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
