package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun ModernSegmentedTab(
    icon: ImageVector,
    title: String,
    count: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tabShape = RoundedCornerShape(AppTokens.Radius.pill)
    val animSpec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = AppTokens.Motion.durationMedium,
        easing = AppTokens.Motion.standardEasing
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = animSpec
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = animSpec
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = animSpec
    )
    val badgeBgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = animSpec
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = animSpec
    )

    Surface(
        modifier = Modifier
            .clip(tabShape)
            .clickable(onClick = onClick),
        shape = tabShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppTokens.Spacing.content,
                vertical = AppTokens.Spacing.control
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppTokens.Size.iconMedium)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
            Surface(
                shape = RoundedCornerShape(AppTokens.Radius.pill),
                color = badgeBgColor,
                contentColor = badgeTextColor
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = AppTokens.Spacing.control,
                        vertical = AppTokens.Spacing.compact
                    )
                )
            }
        }
    }
}
