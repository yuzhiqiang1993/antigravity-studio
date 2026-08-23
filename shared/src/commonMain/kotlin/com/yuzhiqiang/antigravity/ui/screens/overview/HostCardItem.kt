package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Immutable
data class HostCardData(
    val title: String,
    val statusLabel: String,
    val statusTone: BadgeTone,
    val desc: String,
    val isProxyActive: Boolean,
    val integrationDetail: String,
    val onToggle: () -> Unit,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onRefresh: () -> Unit,
    val onConfigurePath: (() -> Unit)? = null,
    val customPath: String? = null,
    val isLoading: Boolean = false
)

@Composable
fun HostCardItem(
    data: HostCardData,
    modifier: Modifier = Modifier
) {
    StudioCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(Modifier.width(AppTokens.Spacing.sm))

                StatusBadge(
                    text = data.statusLabel,
                    tone = data.statusTone
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = data.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1
            )

            if (!data.customPath.isNullOrBlank()) {
                Text(
                    text = "自定义路径: ${data.customPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTokens.Radius.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.medium))
                    .padding(horizontal = AppTokens.Spacing.content, vertical = AppTokens.Spacing.content),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "代理模式",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusBadge(
                        text = if (data.isProxyActive) "已接入" else "未接入",
                        tone = if (data.isProxyActive) BadgeTone.SUCCESS else BadgeTone.NEUTRAL,
                        showDot = false
                    )
                }
                Text(
                    text = data.integrationDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(AppTokens.Spacing.xxs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.isProxyActive) {
                        val successColor = Color(0xFF16A34A)
                        OutlinedButton(
                            onClick = data.onToggle,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, successColor.copy(alpha = if (data.isLoading) 0.2f else 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = successColor.copy(alpha = 0.08f),
                                contentColor = successColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            if (data.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = successColor
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = "恢复官方直连",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    } else {
                        Button(
                            onClick = data.onToggle,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            if (data.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = "接入代理",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    if (data.onAction != null && data.actionLabel != null) {
                        OutlinedButton(
                            onClick = data.onAction,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = data.actionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    } else if (data.onConfigurePath != null && data.statusLabel == "未安装") {
                        OutlinedButton(
                            onClick = data.onConfigurePath,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "配置路径",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.onConfigurePath != null) {
                        OutlinedButton(
                            onClick = data.onConfigurePath,
                            enabled = !data.isLoading,
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "配置路径",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = data.onRefresh,
                        enabled = !data.isLoading,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
