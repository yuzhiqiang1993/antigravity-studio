package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import antigravity_studio.shared.generated.resources.Res
import antigravity_studio.shared.generated.resources.logo_transparent
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import org.jetbrains.compose.resources.painterResource

/**
 * Material Design 3 现代标准页面顶栏 Header。
 * 标题使用 24sp 加粗主题主色，支持徽标 Badge、副标题 Subtitle 以及右侧操作 Action。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeContent: (@Composable () -> Unit)? = null,
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                if (badgeContent != null) {
                    badgeContent()
                } else if (!badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(AppTokens.Radius.pill),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!subtitle.isNullOrBlank()) {
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
 * 品牌 Icon 包装器 (纯透明底，支持根据当前主题 Primary 动态变色).
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    tint: Color? = MaterialTheme.colorScheme.primary
) {
    Image(
        painter = painterResource(Res.drawable.logo_transparent),
        contentDescription = "Antigravity Studio",
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) },
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
