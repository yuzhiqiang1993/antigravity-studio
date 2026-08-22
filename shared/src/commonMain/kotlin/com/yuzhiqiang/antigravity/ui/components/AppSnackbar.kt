package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 全局 Toast 通知宿主，放置在 App 顶层。
 * 对标 agy-byok 的 NoticeBar / Notice 组件。
 */
@Composable
fun AppSnackbarHost(
    notice: NoticeState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = notice != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            notice?.let { current ->
                LaunchedEffect(current.id) {
                    delay(4000)
                    onDismiss()
                }

                val (bgColor, iconTint, icon) = when (current.kind) {
                    NoticeKind.SUCCESS -> Triple(
                        Color(0xFF059669),
                        Color.White,
                        Icons.Outlined.CheckCircle
                    )
                    NoticeKind.ERROR -> Triple(
                        Color(0xFFDC2626),
                        Color.White,
                        Icons.Outlined.ErrorOutline
                    )
                    NoticeKind.INFO -> Triple(
                        MaterialTheme.colorScheme.primary,
                        Color.White,
                        Icons.Outlined.Info
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = current.message,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    current.action?.let { action ->
                        TextButton(onClick = action.onClick) {
                            Text(
                                text = action.label,
                                color = Color.White,
                                fontSize = 12.sp,
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
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
