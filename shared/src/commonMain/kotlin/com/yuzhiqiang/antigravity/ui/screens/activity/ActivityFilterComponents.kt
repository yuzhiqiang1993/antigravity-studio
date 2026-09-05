package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

@Composable
internal fun ActivityFilterSectionHeader(
    title: String,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    s: Strings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = s.activityFilterAllOption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onSelectAll)
        )
    }
}

@Composable
internal fun ActivityFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = StudioDesignTokens.TextSize.badge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = StudioDesignTokens.TextSize.caption),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    }
                )
            }
        },
        modifier = modifier
            .height(28.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(AppTokens.Radius.pill),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}

@Composable
internal fun ActivityFilterOptionRow(
    label: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.sm, vertical = AppTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = StudioDesignTokens.TextSize.body,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
internal fun EndpointFilterOptionRow(
    path: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit,
    s: Strings
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val info = remember(path) { ActivityEndpointRegistry.resolve(path) }
    val isAiChat = info.category == ActivityEndpointCategory.AI_CHAT || info.category == ActivityEndpointCategory.CODE_ASSIST
    val displayName = remember(path, s) { s.activityEndpointDisplayName(path) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(StudioDesignTokens.CornerRadius.sm)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioCheckbox(checked = checked, onCheckedChange = { onClick() })
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = if (checked || isAiChat) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else if (isAiChat) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isAiChat) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        ) {
                            Text(
                                text = s.activityFilterCoreBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = cleanEndpointDisplayPath(path),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AppTokens.Spacing.xs))
        Surface(
            shape = RoundedCornerShape(AppTokens.Radius.pill),
            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

internal fun cleanEndpointDisplayPath(path: String): String {
    val trimmed = path.trim()
    val colonIdx = trimmed.lastIndexOf(':')
    if (colonIdx >= 0 && colonIdx < trimmed.length - 1) {
        return trimmed.substring(colonIdx + 1)
    }
    val cleaned = trimmed.removePrefix("/v1internal/").removePrefix("/v1/").removePrefix("/")
    return cleaned.ifBlank { trimmed.ifBlank { "/" } }
}

@Composable
internal fun ActivityFilterEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = AppTokens.Spacing.sm, vertical = AppTokens.Spacing.xs)
    )
}
