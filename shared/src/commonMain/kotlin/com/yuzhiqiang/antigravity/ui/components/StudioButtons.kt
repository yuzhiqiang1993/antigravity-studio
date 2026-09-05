package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/**
 * 桌面端统一基础按钮容器 (StudioButtonBase)：
 * 统筹 Hover、Pressed、Loading 状态收集、平滑色彩过渡、Ripple 涟漪与无障碍焦点交互。
 */
@Composable
private fun StudioButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 32.dp,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.small),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    backgroundColor: Color,
    borderColor: Color? = null,
    contentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }

    val animatedBg by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(durationMillis = 150),
        label = "StudioButtonBg"
    )

    val animatedBorder by animateColorAsState(
        targetValue = borderColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "StudioButtonBorder"
    )

    val borderModifier = if (borderColor != null) {
        Modifier.border(1.dp, animatedBorder, shape)
    } else {
        Modifier
    }

    val rippleIndication = ripple(
        bounded = true,
        color = contentColor
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(animatedBg)
            .then(borderModifier)
            .hoverable(interactionSource = interactionSource, enabled = enabled && !isLoading)
            .pointerHoverIcon(if (enabled && !isLoading) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = rippleIndication,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

/**
 * 桌面端高质感统一主按钮 (StudioButton)：
 * - 原生 MD3 语义视觉体验
 * - 鼠标悬停平滑变色与光标切换 Hand
 * - 保留原生 Material 涟漪反馈与焦点环
 * - 支持加载中旋转 Loading 指示器
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 32.dp,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.small),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val effectiveBg = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        isPressed -> containerColor.copy(alpha = 0.85f)
        isHovered -> if (isDark) containerColor.copy(alpha = 0.92f) else containerColor.copy(alpha = 0.90f)
        else -> containerColor
    }
    val effectiveContentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    StudioButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        height = height,
        shape = shape,
        contentPadding = contentPadding,
        backgroundColor = effectiveBg,
        contentColor = effectiveContentColor
    )
}

/**
 * 桌面端高质感统一次级色调按钮 (StudioTonalButton)：
 * - 适用于次要操作（如重启、配置、诊断等）
 * - 优雅的微底色与 Hover 微高亮反馈
 */
@Composable
fun StudioTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 32.dp,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.small),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val effectiveBg = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        isPressed -> if (containerColor.alpha < 0.5f) containerColor.copy(alpha = (containerColor.alpha * 2.2f).coerceAtMost(0.35f)) else containerColor.copy(alpha = 0.95f)
        isHovered -> if (containerColor.alpha < 0.5f) containerColor.copy(alpha = (containerColor.alpha * 1.6f).coerceAtMost(0.24f)) else if (isDark) MaterialTheme.colorScheme.surfaceVariant else containerColor.copy(alpha = 0.85f)
        else -> containerColor
    }

    val borderColor = if (isHovered && enabled) {
        if (containerColor.alpha < 0.5f) contentColor.copy(alpha = if (isDark) 0.55f else 0.45f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.6f else 0.8f)
    } else {
        if (containerColor.alpha < 0.5f) contentColor.copy(alpha = if (isDark) 0.25f else 0.20f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f)
    }

    val effectiveContentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    StudioButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        height = height,
        shape = shape,
        contentPadding = contentPadding,
        backgroundColor = effectiveBg,
        borderColor = borderColor,
        contentColor = effectiveContentColor
    )
}

/**
 * 桌面端高质感统一描边按钮 (StudioOutlinedButton)：
 * - 适用于停用代理、配置路径、次要取消等
 */
@Composable
fun StudioOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    height: Dp = 32.dp,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.small),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    customColor: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val baseColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        customColor != null -> customColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val effectiveBg = when {
        !enabled -> Color.Transparent
        isPressed -> baseColor.copy(alpha = 0.18f)
        isHovered -> baseColor.copy(alpha = if (isDark) 0.14f else 0.10f)
        else -> if (customColor != null) customColor.copy(alpha = 0.06f) else Color.Transparent
    }

    val effectiveBorder = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        isHovered -> baseColor.copy(alpha = if (isDark) 0.8f else 0.9f)
        else -> baseColor.copy(alpha = if (isDark) 0.4f else 0.5f)
    }

    val effectiveContentColor = if (enabled) baseColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    StudioButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        height = height,
        shape = shape,
        contentPadding = contentPadding,
        backgroundColor = effectiveBg,
        borderColor = effectiveBorder,
        contentColor = effectiveContentColor
    )
}
