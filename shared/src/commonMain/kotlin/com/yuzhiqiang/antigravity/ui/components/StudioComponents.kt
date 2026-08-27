package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.ui.text.withStyle

import antigravity_studio.shared.generated.resources.Res
import antigravity_studio.shared.generated.resources.logo_transparent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import org.jetbrains.compose.resources.painterResource


/**
 * Material Design 3 现代极简单行顶栏 Header。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subtitle.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

/**
 * Material Design 3 基础卡片组件（带轻量 Outline 描边与圆角）。
 */
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
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
 * 区块标题（带视觉辅助线条或指示器）。
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingAction: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                content = trailingAction
            )
        }
    }
}

/**
 * 兼容旧代码的 SectionLabel。
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppTokens.Spacing.xs))
}

/**
 * 品牌 Icon 包装器 (纯透明底，无卡片白底，高清 88x88).
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp
) {
    Image(
        painter = painterResource(Res.drawable.logo_transparent),
        contentDescription = "Antigravity Studio",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

/**
 * 标准空状态提示视图。
 */
@Composable
fun EmptyStateView(
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppTokens.Radius.large))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(AppTokens.Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (description != null) {
            Spacer(Modifier.height(AppTokens.Spacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            Spacer(Modifier.height(AppTokens.Spacing.md))
            action()
        }
    }
}

/**
 * 桌面端标准统一搜索输入框 (StudioSearchField)：
 * - 统一高度 36dp，与顶栏按钮、FilterChip 严格等高
 * - 统一小圆角 StudioDesignTokens.CornerRadius.sm (6.dp)
 * - 统一背景色 InnerCardLight / Surface
 * - 统一边框 BorderCardLight，聚焦时高亮 Primary 色
 * - 文字垂直 100% 居中，彻底消除裁切或偏移
 * - 支持左侧 Search 图标、右侧一键清空 Close 图标
 */
@Composable
fun StudioSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val effectivePlaceholder = placeholder ?: s.commonSearch
    var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bg = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
    val borderClr = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
    }

    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.searchFieldHeight)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.searchFieldHeight)
                    .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm))
                    .background(bg)
                    .border(
                        1.dp,
                        borderClr,
                        RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.cardActionIconSize)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = effectivePlaceholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    androidx.compose.material3.IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClear,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    )
}

/**
 * 桌面端标准统一表单输入框 (StudioTextField)：
 * - 统一小圆角 StudioDesignTokens.CornerRadius.sm (6.dp)
 * - 统一边框与背景，与全 App 设计系统 100% 呼应
 * - 统一文字与占位符排版，垂直居中
 * - 支持单行/多行、Leading/Trailing 自定义内容
 */
@Composable
fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bg = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
    val borderClr = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
    }

    Column(modifier = modifier) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.5f
                ),
                fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm))
                        .background(bg)
                        .border(
                            1.dp,
                            borderClr,
                            RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm)
                        )
                        .padding(horizontal = 10.dp, vertical = if (singleLine) 8.dp else 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    leadingIcon?.invoke()
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = maxLines
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.invoke()
                }
            }
        )

        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.caption,
                    color = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}


/**
 * 现代高亮文本组件（用于搜索词精准高亮）
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlightColor: Color = MaterialTheme.colorScheme.primaryContainer,
    highlightTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = if (maxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = modifier
        )
        return
    }

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        while (currentIndex < text.length) {
            val startIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (startIndex == -1) {
                append(text.substring(currentIndex))
                break
            }
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }
            val endIndex = startIndex + query.length
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    background = highlightColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            )
            append(text.substring(startIndex, endIndex))
            pop()
            currentIndex = endIndex
        }
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier
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
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    val borderClr = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)

    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderClr, RoundedCornerShape(8.dp)),
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            content = content
        )
    }
}

/**
 * 桌面端高质感统一菜单条目组件 (StudioDropdownMenuItem)：
 * - 紧凑桌面端高度 32dp
 * - 内嵌 6dp 圆角 Hover 高亮背景（macOS 原生交互）
 * - 精致图标与文字对齐
 * - 支持危险操作警示色
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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val interactionSource =
        androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val itemBg = when {
        !enabled -> Color.Transparent
        isHovered -> if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.8f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.8f)
        }

        else -> Color.Transparent
    }

    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val animatedItemBg by animateColorAsState(
        targetValue = itemBg,
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
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.6f),
        thickness = 0.5.dp
    )
}

/**
 * 桌面端标准统一紧凑下拉选择框组件 (StudioSelectField)：
 * - 统一高度 36.dp，与全 App 输入框、按钮严格等高
 * - 统一圆角 CornerRadius.sm (6.dp)
 * - 支持旋转指示箭头动画、Hover 微灰底高亮、展开聚焦状态
 */
@Composable
fun StudioSelectField(
    label: String,
    isExpanded: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean = true,
    placeholder: String? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource =
        androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150)
    )
    val borderColor = when {
        isExpanded -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.8f else 1.0f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
    }
    val bgColor = when {
        isExpanded -> if (isDark) MaterialTheme.colorScheme.surface else Color.White
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.8f)
        else -> if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.Sizes.topButtonHeight)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm))
            .border(
                if (isExpanded) 1.5.dp else 1.dp,
                borderColor,
                RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm)
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.CornerRadius.sm),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label.ifBlank { placeholder ?: "" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens.TextSize.body,
                    color = if (label.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    ),
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}

/**
 * Antigravity Studio 全局统一定制复选框（精致现代桌面端尺寸，杜绝原生 M3 48dp 冗余边距与粗糙质感）
 */
@Composable
fun StudioCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val targetBg = when {
        !enabled -> if (isDark) Color(0xFF334155).copy(alpha = 0.35f) else Color(0xFFE2E8F0)
        checked -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 150),
        label = "StudioCheckboxBg"
    )

    val targetBorder = when {
        !enabled -> if (isDark) Color(0xFF475569).copy(alpha = 0.4f) else Color(0xFFCBD5E1)
        checked -> MaterialTheme.colorScheme.primary
        else -> if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = 0.9f
        )
    }
    val animatedBorder by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 150),
        label = "StudioCheckboxBorder"
    )

    val checkmarkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "StudioCheckboxCheckScale"
    )

    val checkmarkAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "StudioCheckboxCheckAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }

    val clickModifier = if (onCheckedChange != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onClick = { onCheckedChange(!checked) }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(18.dp)
            .then(clickModifier)
            .clip(RoundedCornerShape(4.5.dp))
            .background(animatedBg)
            .border(
                width = if (checked) 0.dp else 1.2.dp,
                color = animatedBorder,
                shape = RoundedCornerShape(4.5.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checkmarkAlpha > 0f) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else if (isDark) Color(0xFF94A3B8) else Color(
                    0xFF64748B
                ),
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer {
                        scaleX = checkmarkScale
                        scaleY = checkmarkScale
                        alpha = checkmarkAlpha
                    }
            )
        }
    }
}

/**
 * 桌面端高质感统一主按钮 (StudioButton)：
 * - 原生 macOS / MD3 精致视觉体验
 * - 鼠标悬停（Hover）平滑变色与光标切换 Hand
 * - 杜绝原生 M3 方形 StateLayer 瑕疵
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
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
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

    val animatedBg by animateColorAsState(
        targetValue = effectiveBg,
        animationSpec = tween(durationMillis = 150),
        label = "StudioButtonBg"
    )

    val effectiveContentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(animatedBg)
            .hoverable(interactionSource = interactionSource, enabled = enabled && !isLoading)
            .pointerHoverIcon(if (enabled && !isLoading) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = effectiveContentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveContentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = effectiveContentColor
            )
        }
    }
}

/**
 * 桌面端高质感统一次级色调按钮 (StudioTonalButton)：
 * - 适用于次要操作（如重启、配置、诊断等）
 * - 优雅的微底色与 Hover 微高亮反馈，彻底消除生硬方块
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
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
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
        isPressed -> containerColor.copy(alpha = 0.95f)
        isHovered -> if (isDark) MaterialTheme.colorScheme.surfaceVariant else containerColor.copy(alpha = 0.95f)
        else -> containerColor
    }

    val animatedBg by animateColorAsState(
        targetValue = effectiveBg,
        animationSpec = tween(durationMillis = 150),
        label = "StudioTonalButtonBg"
    )

    val borderColor = if (isHovered && enabled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.6f else 0.8f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f)
    }

    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(durationMillis = 150),
        label = "StudioTonalButtonBorder"
    )

    val effectiveContentColor = if (enabled) {
        if (isHovered) MaterialTheme.colorScheme.onSurface else contentColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(animatedBg)
            .border(1.dp, animatedBorder, shape)
            .hoverable(interactionSource = interactionSource, enabled = enabled && !isLoading)
            .pointerHoverIcon(if (enabled && !isLoading) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = effectiveContentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveContentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = effectiveContentColor
            )
        }
    }
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
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
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

    val animatedBg by animateColorAsState(
        targetValue = effectiveBg,
        animationSpec = tween(durationMillis = 150),
        label = "StudioOutlinedButtonBg"
    )

    val effectiveBorder = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        isHovered -> baseColor.copy(alpha = if (isDark) 0.8f else 0.9f)
        else -> baseColor.copy(alpha = if (isDark) 0.4f else 0.5f)
    }

    val animatedBorder by animateColorAsState(
        targetValue = effectiveBorder,
        animationSpec = tween(durationMillis = 150),
        label = "StudioOutlinedButtonBorder"
    )

    val effectiveContentColor = if (enabled) baseColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(animatedBg)
            .border(1.dp, animatedBorder, shape)
            .hoverable(interactionSource = interactionSource, enabled = enabled && !isLoading)
            .pointerHoverIcon(if (enabled && !isLoading) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = effectiveContentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveContentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = effectiveContentColor
            )
        }
    }
}

