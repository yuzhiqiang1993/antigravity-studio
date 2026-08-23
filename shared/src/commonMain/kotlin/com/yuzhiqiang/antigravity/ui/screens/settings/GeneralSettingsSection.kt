package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun GeneralSettingsSection(
    config: AppConfig,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            SettingsCardTitle(Icons.Outlined.Settings, s.settingsGeneral)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                icon = Icons.Outlined.Language,
                title = s.settingsLanguage,
                description = s.settingsLanguageDescription
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(AppTokens.Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
                ) {
                    AppLanguage.values().forEach { lang ->
                        val selected = I18nManager.currentLanguage == lang
                        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val text = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(bg)
                                .clickable { onUpdateLanguage(lang) }
                                .padding(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.control)
                        ) {
                            Text(
                                text = if (lang == AppLanguage.ZH_CN) "简体中文" else "English",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = text
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                icon = Icons.Outlined.Palette,
                title = s.settingsTheme,
                description = s.settingsThemeDescription
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(AppTokens.Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
                ) {
                    listOf(
                        Triple("system", s.settingsThemeSystem, Icons.Outlined.Computer),
                        Triple("light", s.settingsThemeLight, Icons.Outlined.Palette),
                        Triple("dark", s.settingsThemeDark, Icons.Outlined.Settings)
                    ).forEach { (mode, label, _) ->
                        val selected = config.themeMode == mode
                        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val text = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(bg)
                                .clickable { onUpdateThemeMode(mode) }
                                .padding(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.control)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = text
                            )
                        }
                    }
                }
            }
        }
    }
}
