package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

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
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    val s = strings()
    val effectivePlaceholder = placeholder ?: s.commonSearch
    var isFocused by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val bg = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
    }
    val borderClr = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = StudioDesignTokens.TextSize.body
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(StudioDesignTokens.Sizes.searchFieldHeight)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StudioDesignTokens.Sizes.searchFieldHeight)
                    .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
                    .background(bg)
                    .border(
                        1.dp,
                        borderClr,
                        RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize)
                )
               Box(
                   modifier = Modifier.weight(1f),
                   contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = effectivePlaceholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = StudioDesignTokens.TextSize.body
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(
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
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val bg = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
    val borderClr = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
    }

    Column(modifier = modifier) {
        BasicTextField(
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
                fontSize = StudioDesignTokens.TextSize.body
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
                        .background(bg)
                        .border(
                            1.dp,
                            borderClr,
                            RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
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
                                    fontSize = StudioDesignTokens.TextSize.body
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
                    fontSize = StudioDesignTokens.TextSize.caption,
                    color = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(150)
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
            .height(StudioDesignTokens.Sizes.topButtonHeight)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
            .border(
                if (isExpanded) 1.5.dp else 1.dp,
                borderColor,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
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
                    fontSize = StudioDesignTokens.TextSize.body,
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
