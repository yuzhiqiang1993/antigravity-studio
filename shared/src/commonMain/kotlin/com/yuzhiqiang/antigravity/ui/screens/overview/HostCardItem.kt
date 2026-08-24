package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.screens.models.ActionSquareIcon
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Immutable
data class HostCardData(
    val title: String,
    val statusLabel: String,
    val statusTone: BadgeTone,
    val desc: String,
    val isProxyActive: Boolean,
    val needsUpdate: Boolean = false,
    val configuredEndpoint: String? = null,
    val targetEndpoint: String? = null,
    val integrationDetail: String,
    val onToggle: () -> Unit,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onRefresh: () -> Unit,
    val onForceReset: (() -> Unit)? = null,
    val onConfigurePath: (() -> Unit)? = null,
    val customPath: String? = null,
    val isLoading: Boolean = false
)

@Composable
fun HostCardItem(
    data: HostCardData,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val warningColor = AppStatusColors.warning
    val successColor = AppStatusColors.success
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardElevation by animateDpAsState(
        targetValue = if (isHovered) 2.5.dp else 0.dp,
        animationSpec = tween(150)
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            data.needsUpdate && isHovered -> warningColor.copy(alpha = 0.75f)
            data.needsUpdate -> warningColor.copy(alpha = 0.55f)
            data.isProxyActive && isHovered -> successColor.copy(alpha = 0.65f)
            data.isProxyActive -> successColor.copy(alpha = 0.45f)
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(AppTokens.Motion.durationMedium)
    )

    // 根据宿主类型确定专属图标与主题色
    val (hostIcon, hostIconTint, hostIconBg) = remember(data.title) {
        when {
            data.title.contains("IDE", ignoreCase = true) -> Triple(
                Icons.Outlined.Code,
                Color(0xFF6366F1),
                Color(0xFF6366F1).copy(alpha = 0.12f)
            )
            data.title.contains("App", ignoreCase = true) -> Triple(
                Icons.Outlined.Laptop,
                Color(0xFF0D9488),
                Color(0xFF0D9488).copy(alpha = 0.12f)
            )
            else -> Triple(
                Icons.Outlined.Terminal,
                Color(0xFF475569),
                Color(0xFF475569).copy(alpha = 0.12f)
            )
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = cardElevation, shape = RoundedCornerShape(14.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: 平台专属图标 + 标题 + 状态徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = hostIconBg,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = hostIcon,
                                contentDescription = null,
                                tint = hostIconTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusBadge(
                    text = data.statusLabel,
                    tone = data.statusTone
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // 宿主当前简述
            Text(
                text = data.desc,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!data.customPath.isNullOrBlank()) {
                Text(
                    text = s.hostCustomPath(data.customPath),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 核心代理模式指示卡片
            val containerBg = when {
                data.needsUpdate -> warningColor.copy(alpha = 0.08f)
                data.isProxyActive -> successColor.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
            val containerBorder = when {
                data.needsUpdate -> warningColor.copy(alpha = 0.4f)
                data.isProxyActive -> successColor.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = containerBg,
                border = BorderStroke(1.dp, containerBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (data.isProxyActive) Icons.Outlined.CheckCircle
                                else if (data.needsUpdate) Icons.Outlined.WarningAmber
                                else Icons.Outlined.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (data.needsUpdate) warningColor
                                else if (data.isProxyActive) successColor
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = s.hostProxyMode,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        StatusBadge(
                            text = when {
                                data.needsUpdate -> s.hostStatusNeedsUpdate
                                data.isProxyActive -> s.hostStatusActive
                                else -> s.hostStatusInactive
                            },
                            tone = when {
                                data.needsUpdate -> BadgeTone.WARNING
                                data.isProxyActive -> BadgeTone.SUCCESS
                                else -> BadgeTone.NEUTRAL
                            },
                            showDot = false
                        )
                    }
                    Text(
                        text = data.integrationDetail,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (data.needsUpdate) warningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Action Bar: 主操作按钮 + 次级操作 + 右侧工具组
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        data.needsUpdate -> {
                            Button(
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = warningColor,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (data.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = s.hostUpdateAction,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                        data.isProxyActive -> {
                            OutlinedButton(
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, successColor.copy(alpha = if (data.isLoading) 0.2f else 0.5f)),
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
                                    text = s.hostDisable,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                        else -> {
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
                                    text = s.hostEnable,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    if (data.onAction != null && data.actionLabel != null) {
                        FilledTonalButton(
                            onClick = data.onAction,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = data.actionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else if (data.onConfigurePath != null && data.statusLabel == s.hostStatusNotInstalled) {
                        OutlinedButton(
                            onClick = data.onConfigurePath,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = s.hostConfigurePath,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.onForceReset != null) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.SettingsBackupRestore,
                            contentDescription = s.hostForceReset,
                            onClick = if (!data.isLoading) data.onForceReset else null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            size = 32.dp
                        )
                    }

                    ActionSquareIcon(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = s.commonRefresh,
                        onClick = if (!data.isLoading) data.onRefresh else null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        size = 32.dp
                    )
                }
            }
        }
    }
}
