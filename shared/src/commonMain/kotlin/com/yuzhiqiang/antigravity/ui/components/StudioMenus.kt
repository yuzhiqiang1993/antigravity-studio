package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties

/**
 * 桌面端高质感统一弹出菜单组件 (StudioDropdownMenu)：
 * - 纯净底色 surfaceContainer
 * - 极细微边框 outlineVariant (1.dp)
 * - 优雅中圆角 8.dp + 柔和投影
 * - 紧凑桌面端间距
 */
@Composable
fun StudioDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    val borderClr = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderClr, RoundedCornerShape(8.dp)),
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            content = content
        )
    }
}

/**
 * 桌面端高质感统一弹出菜单项组件 (StudioDropdownMenuItem)：
 * - 统一高度 32~36.dp 紧凑桌面尺寸
 * - 悬停优雅淡灰底背景动画
 * - 支持自定义前置 Icon、后置快捷键/说明 Text、选中状态勾选指示、副标题
 */
@Composable
fun StudioDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    isSelected: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = isSystemInDarkTheme()

    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val targetItemBg = when {
        !enabled -> Color.Transparent
        isHovered -> if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.8f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.8f)
        }

        else -> Color.Transparent
    }
    val animatedItemBg by animateColorAsState(
        targetValue = targetItemBg,
        animationSpec = tween(durationMillis = 150),
        label = "StudioDropdownMenuItemBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle.isNullOrBlank()) 32.dp else 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(animatedItemBg)
            .hoverable(interactionSource = interactionSource)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.invoke()
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = textColor,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingIcon != null) {
                trailingIcon.invoke()
            } else if (isSelected) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 桌面端高质感统一弹出触发按钮 (StudioDropdownTrigger)：
 * 遵循 MD3 FilterChip / Outlined Dropdown 规范
 */
@Composable
fun StudioDropdownTrigger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = isSystemInDarkTheme()

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.5f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.25f else 0.35f)
    }

    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.6f else 0.8f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(32.dp)
            .hoverable(interactionSource = interactionSource)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                androidx.compose.material3.Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * 菜单分割线
 */
@Composable
fun StudioMenuDivider(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.6f),
        thickness = 0.5.dp
    )
}
