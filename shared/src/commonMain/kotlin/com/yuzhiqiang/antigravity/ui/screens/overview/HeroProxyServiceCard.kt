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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioButton
import com.yuzhiqiang.antigravity.ui.components.StudioTonalButton
import com.yuzhiqiang.antigravity.ui.components.StudioOutlinedButton
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeroProxyServiceCard(
    isRunning: Boolean,
    address: String,
    totalRequests: Int = 0,
    successRateText: String = "100%",
    avgLatencyText: String = "--",
    upstreamSummary: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit,
    onDiagnostics: (() -> Unit)? = null
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val displayUpstreamSummary = upstreamSummary ?: s.overviewOfficialDirect
    var isRecentlyCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(150)
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = if (isHovered) 2.dp else 0.dp, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Top Section: 核心身份 + 状态胶囊 + 操作控制组 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧: 图标 + 标题 + 运行状态 + 端点药丸
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 网关立体微渐变徽标
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRunning) AppStatusColors.success.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (isRunning) AppStatusColors.success.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRunning) Icons.Outlined.Router else Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = if (isRunning) AppStatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = s.overviewProxyCardTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            StatusBadge(
                                text = if (isRunning) s.overviewProxyRunning else s.overviewProxyStopped,
                                isActive = isRunning,
                                pulse = isRunning
                            )
                        }

                        // 地址与端口复制胶囊
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isRecentlyCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                    contentDescription = s.overviewCopyAddress,
                                    tint = if (isRecentlyCopied) AppStatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // 右侧: 操作按钮组 (健康诊断 + 启停控制)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onDiagnostics != null) {
                        StudioTonalButton(
                            text = s.overviewDiagnostics,
                            icon = Icons.Outlined.HealthAndSafety,
                            onClick = onDiagnostics,
                            height = 36.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 控制开关按钮
                    if (isRunning) {
                        StudioOutlinedButton(
                            text = s.overviewStopProxy,
                            icon = Icons.Outlined.Stop,
                            onClick = onStop,
                            height = 36.dp,
                            customColor = AppStatusColors.warning
                        )
                    } else {
                        StudioButton(
                            text = s.overviewStartProxy,
                            icon = Icons.Outlined.PlayArrow,
                            onClick = onStart,
                            height = 36.dp
                        )
                    }
                }
            }

            // === Divider 分割线 ===
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            // === Bottom Section: 4 个实时监控与拓扑指标小卡片 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroStatItem(
                    title = s.overviewTodayRequests,
                    value = s.overviewRequestsUnit(totalRequests.toLong()),
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f)
                )
                HeroStatItem(
                    title = s.overviewServiceUptime,
                    value = successRateText,
                    icon = Icons.Outlined.CheckCircleOutline,
                    valueColor = if (isRunning) AppStatusColors.success else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                HeroStatItem(
                    title = s.overviewAvgLatency,
                    value = avgLatencyText,
                    icon = Icons.Outlined.Timer,
                    modifier = Modifier.weight(1f)
                )
                HeroStatItem(
                    title = s.overviewRouteUpstreamStatus,
                    value = displayUpstreamSummary,
                    icon = Icons.Outlined.Hub,
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatItem(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
