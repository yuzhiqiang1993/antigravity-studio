package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val button = @Composable {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(7.dp))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }

    if (!tooltip.isNullOrBlank()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    shape = RoundedCornerShape(6.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        text = tooltip,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
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
