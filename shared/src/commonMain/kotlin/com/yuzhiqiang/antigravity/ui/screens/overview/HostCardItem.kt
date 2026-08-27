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
import com.yuzhiqiang.antigravity.ui.components.StudioButton
import com.yuzhiqiang.antigravity.ui.components.StudioTonalButton
import com.yuzhiqiang.antigravity.ui.components.StudioOutlinedButton
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
    val version: String? = null,
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
        targetValue = if (isHovered) 2.dp else 0.dp,
        animationSpec = tween(150)
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            data.needsUpdate && isHovered -> warningColor.copy(alpha = 0.40f)
            data.needsUpdate -> warningColor.copy(alpha = 0.22f)
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(150)
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
            // Header: 平台专属图标 + (标题 + 版本号) + 状态徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = hostIconBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = hostIcon,
                                contentDescription = null,
                                tint = hostIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!data.version.isNullOrBlank()) {
                            Text(
                                text = if (data.version.startsWith("v", ignoreCase = true)) data.version else "v${data.version}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                data.needsUpdate -> warningColor.copy(alpha = 0.06f)
                data.isProxyActive -> successColor.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            val containerBorder = when {
                data.needsUpdate -> warningColor.copy(alpha = 0.22f)
                data.isProxyActive -> successColor.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
                                tint = if (data.needsUpdate) warningColor.copy(alpha = 0.9f)
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
                        color = if (data.needsUpdate) AppStatusColors.onWarningContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            StudioButton(
                                text = s.hostUpdateAction,
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                isLoading = data.isLoading,
                                icon = Icons.Outlined.Sync
                            )
                        }
                        data.isProxyActive -> {
                            StudioOutlinedButton(
                                text = s.hostDisable,
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                isLoading = data.isLoading,
                                customColor = successColor
                            )
                        }
                        else -> {
                            StudioButton(
                                text = s.hostEnable,
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                isLoading = data.isLoading
                            )
                        }
                    }

                    if (data.onAction != null && data.actionLabel != null) {
                        StudioTonalButton(
                            text = data.actionLabel,
                            onClick = data.onAction,
                            enabled = !data.isLoading,
                            isLoading = false
                        )
                    } else if (data.onConfigurePath != null && data.statusLabel == s.hostStatusNotInstalled) {
                        StudioOutlinedButton(
                            text = s.hostConfigurePath,
                            onClick = data.onConfigurePath,
                            enabled = !data.isLoading,
                            isLoading = false
                        )
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
