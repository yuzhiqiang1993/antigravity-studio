package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.border
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSquareIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tooltip: String? = contentDescription,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    size: Dp = 28.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(7.dp)

    val animatedBg by animateColorAsState(
        targetValue = if (isHovered && onClick != null) containerColor.copy(alpha = (containerColor.alpha + 0.18f).coerceAtMost(1f)) else containerColor,
        animationSpec = tween(150),
        label = "ActionSquareBg"
    )

    val animatedBorder by animateColorAsState(
        targetValue = if (isHovered && onClick != null) borderColor.copy(alpha = (borderColor.alpha + 0.35f).coerceAtMost(1f)) else borderColor,
        animationSpec = tween(150),
        label = "ActionSquareBorder"
    )

    val animatedTint by animateColorAsState(
        targetValue = if (isHovered && onClick != null) tint else tint.copy(alpha = 0.85f),
        animationSpec = tween(150),
        label = "ActionSquareTint"
    )

    val button = @Composable {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(animatedBg)
                .border(1.dp, animatedBorder, shape)
                .hoverable(interactionSource = interactionSource, enabled = onClick != null)
                .pointerHoverIcon(if (onClick != null) PointerIcon.Hand else PointerIcon.Default)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = onClick != null,
                    onClick = { onClick?.invoke() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = animatedTint,
                modifier = Modifier.size(15.dp)
            )
        }
    }

    if (!tooltip.isNullOrBlank()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above
            ),
            tooltip = {
                PlainTooltip(
                    shape = RoundedCornerShape(6.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        text = tooltip,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            },
            state = rememberTooltipState()
        ) {
            button()
        }
    } else {
        button()
    }
}
