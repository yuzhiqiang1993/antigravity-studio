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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * - 统一高度 32.dp 紧凑桌面尺寸
 * - 悬停优雅淡灰底背景动画
 * - 支持自定义前置 Icon 和后置快捷键/说明 Text
 */
@Composable
fun StudioDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = isSystemInDarkTheme()

    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
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
            .height(32.dp)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1
            )
        }

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
