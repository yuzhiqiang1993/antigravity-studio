package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeroProxyServiceCard(
    isRunning: Boolean,
    address: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    var isRecentlyCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isRunning && isHovered -> AppStatusColors.success.copy(alpha = 0.55f)
            isRunning -> AppStatusColors.success.copy(alpha = 0.35f)
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(150)
    )

    val containerBg by animateColorAsState(
        targetValue = if (isRunning) AppStatusColors.successContainer.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200)
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = if (isHovered) 2.dp else 0.dp, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerBg
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 网关图标徽标
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRunning) AppStatusColors.success.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRunning) Icons.Outlined.Router else Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = if (isRunning) AppStatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = s.overviewProxyCardTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        StatusBadge(
                            text = if (isRunning) s.overviewProxyRunning else s.overviewProxyStopped,
                            isActive = isRunning,
                            pulse = isRunning
                        )
                    }

                    Text(
                        text = if (isRunning) "服务正在监听本地回环请求，已准备好分发模型流量"
                        else "本地代理服务处于停止状态，宿主将直连官方服务",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 代理地址与端口复制胶囊
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onCopyAddress()
                            isRecentlyCopied = true
                            scope.launch {
                                delay(2000)
                                isRecentlyCopied = false
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isRecentlyCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = s.overviewCopyAddress,
                            tint = if (isRecentlyCopied) AppStatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.5.dp)
                        )
                    }
                }
            }

            // 控制开关按钮
            if (isRunning) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppStatusColors.warning.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppStatusColors.warning.copy(alpha = 0.08f),
                        contentColor = AppStatusColors.warning
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppStatusColors.warning
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.overviewStopProxy,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = AppStatusColors.warning
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.overviewStartProxy,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}
