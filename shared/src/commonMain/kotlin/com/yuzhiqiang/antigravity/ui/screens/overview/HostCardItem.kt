package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens

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

/**
 * 宿主环境卡片组件 (HostCardItem)：
 * - 遵循 Studio 全套毛玻璃与高光微描边设计系统
 * - 紧凑清晰的信息架构：消除冗余废话，采用通透 Sub-Glass 状态条
 * - 对称规整的操作工具栏，3 列严格等高对齐
 */
@Composable
fun HostCardItem(
    data: HostCardData,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val warningColor = AppStatusColors.warning
    val successColor = AppStatusColors.success
    val primaryColor = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardElevation by animateDpAsState(
        targetValue = if (isHovered) StudioGlassTokens.cardElevationHovered else StudioGlassTokens.cardElevation,
        animationSpec = tween(150)
    )

    // 根据宿主类型确定专属图标与品牌色彩
    val (hostIcon, hostIconTint, hostIconBg) = remember(data.title, isDark) {
        when {
            data.title.contains("IDE", ignoreCase = true) -> Triple(
                Icons.Outlined.Code,
                Color(0xFF6366F1),
                Color(0xFF6366F1).copy(alpha = if (isDark) 0.18f else 0.10f)
            )
            data.title.contains("App", ignoreCase = true) -> Triple(
                Icons.Outlined.Laptop,
                Color(0xFF0D9488),
                Color(0xFF0D9488).copy(alpha = if (isDark) 0.18f else 0.10f)
            )
            else -> Triple(
                Icons.Outlined.Terminal,
                Color(0xFF64748B),
                Color(0xFF64748B).copy(alpha = if (isDark) 0.18f else 0.10f)
            )
        }
    }

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = StudioGlassTokens.cleanBorderColor(isDark, isHovered),
        animationSpec = androidx.compose.animation.core.tween(150)
    )

    val cardBg = StudioGlassTokens.cardBackgroundColor(isDark)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = StudioGlassTokens.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .hoverable(interactionSource),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header: 专属图标 + (标题 + 版本胶囊) + 状态徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = hostIconBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, hostIconTint.copy(alpha = 0.20f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = hostIcon,
                                contentDescription = null,
                                tint = hostIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
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

            // 2. 核心代理模式状态区 (与卡片本体完全融为一体，无内套小方盒)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val dotColor = when {
                                data.needsUpdate -> warningColor
                                data.isProxyActive -> successColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
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

            // 自定义路径小提示（若有）
            if (!data.customPath.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = data.customPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. 底部操作栏: 主操作按钮 + 重启/启动 + 右侧工具组
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
                                customColor = MaterialTheme.colorScheme.primary
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
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            size = 32.dp
                        )
                    }

                    ActionSquareIcon(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = s.commonRefresh,
                        onClick = if (!data.isLoading) data.onRefresh else null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        size = 32.dp
                    )
                }
            }
        }
    }
}
