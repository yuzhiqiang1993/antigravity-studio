package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens

/**
 * Material Design 3 基础卡片组件（遵循 MD3 标准 1px 微轮廓规范）。
 */
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.medium),
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = StudioGlassTokens.borderWidth,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBg = containerColor ?: StudioGlassTokens.cardBackgroundColor(isDark)
    val effectiveBorderColor = borderColor ?: StudioGlassTokens.cleanBorderColor(isDark)

    Surface(
        modifier = modifier,
        shape = shape,
        color = effectiveBg,
        border = BorderStroke(borderWidth, effectiveBorderColor),
        shadowElevation = elevation,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/**
 * 高质感现代毛玻璃卡片 (StudioGlassCard)：
 * 直接委托给 StudioCard，保持向后兼容并消除重复代码。
 */
@Composable
fun StudioGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.medium),
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = StudioGlassTokens.borderWidth,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) = StudioCard(
    modifier = modifier,
    shape = shape,
    containerColor = backgroundColor,
    borderColor = borderColor,
    borderWidth = borderWidth,
    elevation = elevation,
    content = content
)

/**
 * 通用毛玻璃浮岛容器 Surface (StudioGlassSurface)
 */
@Composable
fun StudioGlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.medium),
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = StudioGlassTokens.borderWidth,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBg = backgroundColor ?: StudioGlassTokens.topBarBackgroundColor(isDark)
    val effectiveBorderColor = borderColor ?: StudioGlassTokens.cleanBorderColor(isDark)

    Surface(
        modifier = modifier,
        shape = shape,
        color = effectiveBg,
        border = BorderStroke(borderWidth, effectiveBorderColor),
        shadowElevation = elevation,
        tonalElevation = 0.dp,
        content = content
    )
}

/**
 * Material Design 3 标准弹窗容器 (StudioDialogSurface)：
 * - 容器色阶：浅色模式下使用极致纯白 surfaceContainerLowest，深色模式下使用深空暗色 surfaceContainer
 * - 圆角标准：MD3 Extra Large 24dp 圆角
 * - 边框标准：1px outlineVariant 微细轮廓，优雅沉稳
 * - 阴影与高度：MD3 Level 3 Elevation (6dp tonalElevation)
 */
@Composable
fun StudioDialogSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppTokens.Radius.dialog),
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBg = containerColor ?: if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val effectiveBorder = borderColor ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f)

    Surface(
        modifier = modifier,
        shape = shape,
        color = effectiveBg,
        border = BorderStroke(borderWidth, effectiveBorder),
        tonalElevation = 0.dp,
        shadowElevation = 24.dp
    ) {
        Column(content = content)
    }
}

/**
 * Material Design 3 统一弹窗脚手架 (StudioDialogScaffold)：
 * 统筹封装 Dialog 弹窗标准骨架：
 * - 标准外层 DialogProperties (非全屏桌面尺寸)
 * - 统一 Extra Large (24dp) 容器圆角与浮岛 Surface
 * - 优雅的顶栏 Header (图标 + 标题 + 副标题 + 关闭按钮)
 * - 统一的内容区 Content 插槽
 * - 规范的操作栏 Footer 插槽
 */
@Composable
fun StudioDialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = modifier,
            shape = RoundedCornerShape(AppTokens.Radius.dialog)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(AppTokens.Radius.small))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.20f),
                                        RoundedCornerShape(AppTokens.Radius.small)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )

                // 3. Actions (Footer)
                if (actions != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}

/**
 * 兼容旧代码的 SectionCard。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    StudioCard(
        modifier = modifier.padding(0.dp),
        content = {
            Column(
                modifier = Modifier.padding(AppTokens.Spacing.card),
                content = content
            )
        }
    )
}

/**
 * 优雅骨架屏加载卡片
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 72.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppTokens.Radius.medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(AppTokens.Radius.medium)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTokens.Spacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius.small))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius.xs))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius.xs))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                )
            }
        }
    }
}
