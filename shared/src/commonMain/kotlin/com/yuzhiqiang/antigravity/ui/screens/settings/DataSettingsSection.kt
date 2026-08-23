package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun DataSettingsSection(
    loadError: String?,
    openDirectoryError: String?,
    onOpenDirectory: () -> Unit,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            SettingsCardTitle(Icons.Outlined.Folder, s.settingsData)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (loadError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius.medium))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(AppTokens.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = loadError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            SettingRow(
                icon = Icons.Outlined.Folder,
                title = s.settingsConfigDir,
                description = s.settingsStorageDescription
            ) {
                OutlinedButton(
                    onClick = onOpenDirectory,
                    shape = RoundedCornerShape(AppTokens.Radius.medium)
                ) {
                    Text(s.settingsOpenDirectory, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (openDirectoryError != null) {
                Text(
                    text = openDirectoryError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
