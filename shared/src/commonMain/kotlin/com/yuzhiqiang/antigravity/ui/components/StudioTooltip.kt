package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的 Material Design 3 桌面端 Tooltip 提示浮层组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTooltip(
    text: String?,
    modifier: Modifier = Modifier,
    positioning: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    content: @Composable () -> Unit
) {
    if (text.isNullOrBlank()) {
        content()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = positioning
            ),
            tooltip = {
                PlainTooltip(
                    shape = RoundedCornerShape(6.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            },
            state = rememberTooltipState(),
            modifier = modifier
        ) {
            content()
        }
    }
}
