package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Paid
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.io.File

@Composable
fun DataSettingsSection(
    loadError: String?,
    openDirectoryError: String?,
    customPricingPath: String?,
    onUpdateCustomPricing: (String?) -> Unit,
    onOpenDirectory: () -> Unit,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
        ) {
            SettingsCardTitle(Icons.Outlined.Folder, s.settingsData)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

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
                description = s.settingsStorageDescription,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDirectory,
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = s.settingsOpenDirectory,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

            // 自定义费率文件配置
            SettingRow(
                icon = Icons.Outlined.Paid,
                title = s.settingsCustomPricingTitle,
                description = s.settingsCustomPricingDesc,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!customPricingPath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onUpdateCustomPricing(null) },
                            shape = RoundedCornerShape(AppTokens.Radius.small),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(s.commonClear, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val picked = pickPricingJsonFile()
                            if (picked != null) {
                                onUpdateCustomPricing(picked)
                            }
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (customPricingPath.isNullOrBlank()) s.settingsCustomPricingSelectFile else File(customPricingPath).name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (openDirectoryError != null) {
                Text(
                    text = openDirectoryError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = AppTokens.Spacing.card)
                )
            }
        }
    }
}

private fun pickPricingJsonFile(): String? {
    return try {
        val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "选择 LiteLLM 定价 JSON 文件", java.awt.FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        fileDialog.isVisible = true
        val file = fileDialog.file
        val dir = fileDialog.directory
        if (file != null && dir != null) {
            File(dir, file).absolutePath
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
