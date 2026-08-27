package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuzhiqiang.antigravity.ui.components.StudioOutlinedButton
import com.yuzhiqiang.antigravity.ui.components.StudioTonalButton

@Composable
fun ModernToolButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    isTonal: Boolean = true
) {
    if (isDestructive) {
        StudioOutlinedButton(
            text = text,
            onClick = onClick,
            icon = icon,
            enabled = enabled,
            isDestructive = true
        )
    } else if (isTonal) {
        StudioTonalButton(
            text = text,
            onClick = onClick,
            icon = icon,
            enabled = enabled
        )
    } else {
        StudioOutlinedButton(
            text = text,
            onClick = onClick,
            icon = icon,
            enabled = enabled,
            isDestructive = false
        )
    }
}
