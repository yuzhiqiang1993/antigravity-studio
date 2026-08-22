package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.delay

enum class NoticeKind {
    SUCCESS, ERROR, INFO
}

data class NoticeAction(
    val label: String,
    val onClick: () -> Unit
)

data class NoticeState(
    val message: String,
    val kind: NoticeKind = NoticeKind.SUCCESS,
    val action: NoticeAction? = null,
    val id: Long = System.currentTimeMillis()
)

/**
 * Material Design 3 全局浮动 Snackbar 提示组件。
 */
@Composable
fun AppSnackbarHost(
    notice: NoticeState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTokens.Spacing.lg, vertical = AppTokens.Spacing.md),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = notice != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            notice?.let { current ->
                LaunchedEffect(current.id) {
                    delay(4000)
                    onDismiss()
                }

                val statusColors = AppStatusColors
                val (containerColor, contentColor, icon) = when (current.kind) {
                    NoticeKind.SUCCESS -> Triple(
                        statusColors.success,
                        statusColors.onSuccess,
                        Icons.Outlined.CheckCircle
                    )
                    NoticeKind.ERROR -> Triple(
                        statusColors.error,
                        statusColors.onError,
                        Icons.Outlined.ErrorOutline
                    )
                    NoticeKind.INFO -> Triple(
                        MaterialTheme.colorScheme.inverseSurface,
                        MaterialTheme.colorScheme.inverseOnSurface,
                        Icons.Outlined.Info
                    )
                }

                Surface(
                    modifier = Modifier
                        .shadow(AppTokens.Elevation.floating, RoundedCornerShape(AppTokens.Radius.medium)),
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    color = containerColor,
                    contentColor = contentColor
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.content),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(AppTokens.Size.iconLarge)
                        )
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        current.action?.let { action ->
                            TextButton(onClick = action.onClick) {
                                Text(
                                    text = action.label,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss",
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(AppTokens.Size.iconMedium)
                            )
                        }
                    }
                }
            }
        }
    }
}
