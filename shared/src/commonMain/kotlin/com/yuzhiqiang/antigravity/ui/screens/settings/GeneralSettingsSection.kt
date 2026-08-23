package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onUpdateAutoCheckUpdate: (Boolean) -> Unit,
    onConfigureHostPath: ((String, String) -> Unit)? = null,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
        ) {
            SettingsCardTitle(Icons.Outlined.Settings, s.settingsGeneral)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            SettingRow(
                icon = Icons.Outlined.Language,
                title = s.settingsLanguage,
                description = s.settingsLanguageDescription,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(AppTokens.Radius.pill)
                        )
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLanguage.values().forEach { lang ->
                        val selected = I18nManager.currentLanguage == lang
                        val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(bg)
                                .clickable { onUpdateLanguage(lang) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == AppLanguage.ZH_CN) "简体中文" else "English",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = text
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            SettingRow(
                icon = Icons.Outlined.Palette,
                title = s.settingsTheme,
                description = s.settingsThemeDescription,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(AppTokens.Radius.pill)
                        )
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple("system", s.settingsThemeSystem, Icons.Outlined.Computer),
                        Triple("light", s.settingsThemeLight, Icons.Outlined.Palette),
                        Triple("dark", s.settingsThemeDark, Icons.Outlined.Settings)
                    ).forEach { (mode, label, _) ->
                        val selected = config.themeMode == mode
                        val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(bg)
                                .clickable { onUpdateThemeMode(mode) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = text
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            SettingRow(
                icon = Icons.Outlined.Sync,
                title = s.settingsAutoCheckUpdate,
                description = s.settingsAutoCheckUpdateDesc,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Switch(
                    checked = config.autoCheckUpdate,
                    onCheckedChange = onUpdateAutoCheckUpdate,
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (onConfigureHostPath != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                SettingRow(
                    icon = Icons.Outlined.Computer,
                    title = s.settingsHostPathsTitle,
                    description = s.settingsHostPathsDesc,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple("ide", "IDE", config.customHostPaths["ide"]),
                            Triple("app", "App", config.customHostPaths["app"]),
                            Triple("cli", "CLI", config.customHostPaths["cli"])
                        ).forEach { (key, title, customPath) ->
                            val hasCustom = !customPath.isNullOrBlank()
                            val bg = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            val borderColor = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            val textColor = if (hasCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                    .background(bg)
                                    .border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(AppTokens.Radius.pill)
                                    )
                                    .clickable { onConfigureHostPath(key, "Antigravity $title") }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (hasCustom) s.settingsHostPathCustom(title) else s.settingsHostPathAuto(title),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (hasCustom) FontWeight.SemiBold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
