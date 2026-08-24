package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Immutable
data class HostCardData(
    val title: String,
    val statusLabel: String,
    val statusTone: BadgeTone,
    val desc: String,
    val isProxyActive: Boolean,
    val needsUpdate: Boolean = false,
    val configuredEndpoint: String? = null,
    val targetEndpoint: String? = null,
    val integrationDetail: String,
    val onToggle: () -> Unit,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onRefresh: () -> Unit,
    val onForceReset: (() -> Unit)? = null,
    val onConfigurePath: (() -> Unit)? = null,
    val customPath: String? = null,
    val isLoading: Boolean = false
)

@Composable
fun HostCardItem(
    data: HostCardData,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val warningColor = AppStatusColors.warning
    val successColor = AppStatusColors.success

    val borderColor by animateColorAsState(
        targetValue = when {
            data.needsUpdate -> warningColor.copy(alpha = 0.5f)
            data.isProxyActive -> successColor.copy(alpha = 0.45f)
            isHovered -> if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
            else -> if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
        },
        animationSpec = tween(AppTokens.Motion.durationMedium)
    )

    Surface(
        modifier = modifier
            .hoverable(interactionSource),
        shape = RoundedCornerShape(AppTokens.Radius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isHovered) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusBadge(
                    text = data.statusLabel,
                    tone = data.statusTone
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = data.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1
            )

            if (!data.customPath.isNullOrBlank()) {
                Text(
                    text = s.hostCustomPath(data.customPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            val containerBg = when {
                data.needsUpdate -> warningColor.copy(alpha = 0.08f)
                data.isProxyActive -> successColor.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val containerBorder = when {
                data.needsUpdate -> warningColor.copy(alpha = 0.35f)
                data.isProxyActive -> successColor.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTokens.Radius.medium))
                    .background(containerBg)
                    .border(1.dp, containerBorder, RoundedCornerShape(AppTokens.Radius.medium))
                    .padding(horizontal = AppTokens.Spacing.content, vertical = AppTokens.Spacing.content),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.hostProxyMode,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusBadge(
                        text = when {
                            data.needsUpdate -> s.hostStatusNeedsUpdate
                            data.isProxyActive -> s.hostStatusActive
                            else -> s.hostStatusInactive
                        },
                        tone = when {
                            data.needsUpdate -> BadgeTone.WARNING
                            data.isProxyActive -> BadgeTone.SUCCESS
                            else -> BadgeTone.NEUTRAL
                        },
                        showDot = false
                    )
                }
                Text(
                    text = data.integrationDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (data.needsUpdate) warningColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(AppTokens.Spacing.xxs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        data.needsUpdate -> {
                            Button(
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = warningColor,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (data.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = s.hostUpdateAction,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                        data.isProxyActive -> {
                            OutlinedButton(
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, successColor.copy(alpha = if (data.isLoading) 0.2f else 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = successColor.copy(alpha = 0.08f),
                                    contentColor = successColor
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (data.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = successColor
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = s.hostDisable,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = data.onToggle,
                                enabled = !data.isLoading,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (data.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = s.hostEnable,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }

                    if (data.onAction != null && data.actionLabel != null) {
                        OutlinedButton(
                            onClick = data.onAction,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = data.actionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    } else if (data.onConfigurePath != null && data.statusLabel == s.hostStatusNotInstalled) {
                        OutlinedButton(
                            onClick = data.onConfigurePath,
                            enabled = !data.isLoading,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = s.hostConfigurePath,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.onForceReset != null) {
                        HostSquareIconButton(
                            icon = Icons.Outlined.SettingsBackupRestore,
                            tooltip = s.hostForceReset,
                            onClick = data.onForceReset,
                            enabled = !data.isLoading
                        )
                    }

                    HostSquareIconButton(
                        icon = Icons.Outlined.Refresh,
                        tooltip = s.commonRefresh,
                        onClick = data.onRefresh,
                        enabled = !data.isLoading
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostSquareIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                )
            }
        },
        state = rememberTooltipState()
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                modifier = Modifier.size(15.dp),
                tint = tint
            )
        }
    }
}
