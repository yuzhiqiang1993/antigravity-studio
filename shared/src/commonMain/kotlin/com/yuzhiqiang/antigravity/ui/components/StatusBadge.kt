package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

enum class BadgeTone {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    NEUTRAL
}

@Composable
fun StatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    pulse: Boolean = false
) {
    val tone = if (isActive) BadgeTone.SUCCESS else BadgeTone.NEUTRAL
    StatusBadge(
        text = text,
        tone = tone,
        modifier = modifier,
        showDot = showDot,
        pulse = pulse && isActive
    )
}

@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    pulse: Boolean = false
) {
    val statusColors = AppStatusColors

    val (bgColor, textColor, dotColor) = when (tone) {
        BadgeTone.SUCCESS -> Triple(
            statusColors.successContainer,
            statusColors.onSuccessContainer,
            statusColors.success
        )
        BadgeTone.WARNING -> Triple(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            statusColors.warning
        )
        BadgeTone.ERROR -> Triple(
            statusColors.errorContainer,
            statusColors.onErrorContainer,
            statusColors.error
        )
        BadgeTone.INFO -> Triple(
            statusColors.infoContainer,
            statusColors.onInfoContainer,
            statusColors.info
        )
        BadgeTone.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by if (pulse) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppTokens.Radius.pill))
            .background(bgColor)
            .border(
                1.dp,
                dotColor.copy(alpha = 0.25f),
                RoundedCornerShape(AppTokens.Radius.pill)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .alpha(pulseAlpha)
                    .background(dotColor)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
