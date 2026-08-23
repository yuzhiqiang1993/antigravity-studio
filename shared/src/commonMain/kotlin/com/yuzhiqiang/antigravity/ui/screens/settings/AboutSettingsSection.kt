package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.BrandMark
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.update.model.UpdateState
import java.awt.Desktop
import java.net.URI

@Composable
fun AboutSettingsSection(
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenConfigDirectory: () -> Unit,
    s: Strings
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTokens.Spacing.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.lg)
            ) {
                BrandMark(size = 56.dp)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        Text(
                            text = s.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = AppTokens.Spacing.sm, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${AppVersion.CURRENT}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Update Status Badges
                        when (updateState) {
                            is UpdateState.Available -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .clickable(onClick = onOpenUpdateDialog)
                                        .padding(horizontal = AppTokens.Spacing.sm, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${s.settingsNewVersionBadge} v${updateState.release.cleanVersion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            is UpdateState.UpToDate -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = AppTokens.Spacing.sm, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = s.settingsLatestVersionBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                    Text(
                        text = s.appSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Check for updates action button
                val isChecking = updateState is UpdateState.Checking
                Button(
                    onClick = {
                        if (updateState is UpdateState.Available) {
                            onOpenUpdateDialog()
                        } else if (!isChecking) {
                            onCheckUpdate()
                        }
                    },
                    enabled = !isChecking,
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.sm)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppTokens.Size.iconSmall),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(AppTokens.Spacing.xs))
                        Text(
                            text = s.settingsCheckingUpdate,
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else if (updateState is UpdateState.Available) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(AppTokens.Spacing.xs))
                        Text(
                            text = s.updateAvailableTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(AppTokens.Spacing.xs))
                        Text(
                            text = s.settingsCheckUpdateBtn,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            AboutActionCard(
                icon = Icons.Outlined.Code,
                title = s.settingsRepo,
                subtitle = "${AppVersion.GITHUB_OWNER}/${AppVersion.GITHUB_REPO}",
                onClick = { openWebUrl(AppVersion.GITHUB_REPO_URL) },
                modifier = Modifier.weight(1f)
            )
            AboutActionCard(
                icon = Icons.Outlined.Folder,
                title = s.settingsConfigDir,
                subtitle = s.settingsOpenConfigDir,
                onClick = onOpenConfigDirectory,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            AboutActionCard(
                icon = Icons.Outlined.Person,
                title = s.settingsDeveloper,
                subtitle = "@${AppVersion.GITHUB_OWNER}",
                onClick = { openWebUrl("https://github.com/${AppVersion.GITHUB_OWNER}") },
                modifier = Modifier.weight(1f)
            )
            AboutActionCard(
                icon = Icons.Outlined.Feedback,
                title = s.settingsFeedback,
                subtitle = s.settingsFeedbackDesc,
                onClick = { openWebUrl("${AppVersion.GITHUB_REPO_URL}/issues") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AboutActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(AppTokens.Radius.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTokens.Spacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppTokens.Size.iconLarge)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun openWebUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {}
}
