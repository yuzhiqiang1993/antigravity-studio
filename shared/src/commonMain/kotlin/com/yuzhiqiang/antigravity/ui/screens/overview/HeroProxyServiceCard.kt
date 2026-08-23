package com.yuzhiqiang.antigravity.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
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

    StudioCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.section),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                Text(
                    text = s.overviewProxyCardTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                StatusBadge(
                    text = if (isRunning) s.overviewProxyRunning else s.overviewProxyStopped,
                    isActive = isRunning,
                    pulse = isRunning
                )

                Spacer(Modifier.width(AppTokens.Spacing.xs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    Text(
                        text = s.overviewProxyPort,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTokens.Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.small))
                            .clickable {
                                onCopyAddress()
                                isRecentlyCopied = true
                                scope.launch {
                                    delay(2000)
                                    isRecentlyCopied = false
                                }
                            }
                            .padding(horizontal = AppTokens.Spacing.content, vertical = AppTokens.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
                    ) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isRecentlyCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = s.overviewCopyAddress,
                            tint = if (isRecentlyCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                    }
                }
            }

            if (isRunning) {
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.Size.iconMedium)
                    )
                    Spacer(Modifier.width(AppTokens.Spacing.xs))
                    Text(
                        text = s.overviewStopProxy,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.Size.iconMedium)
                    )
                    Spacer(Modifier.width(AppTokens.Spacing.xs))
                    Text(
                        text = s.overviewStartProxy,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
