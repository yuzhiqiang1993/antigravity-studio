package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.DefaultSwitchTarget
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.ThemePalette

@Composable
fun GeneralSettingsSection(
    config: AppConfig,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    onUpdateThemePalette: (String) -> Unit = {},
    onUpdateAutoCheckUpdate: (Boolean) -> Unit,
    onUpdateDefaultSwitchTarget: (String) -> Unit = {},
    onConfigureHostPath: ((String, String) -> Unit)? = null,
    s: Strings
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 卡片 1：通用偏好
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                SettingsCardTitle(Icons.Outlined.Settings, s.settingsGeneral)
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                // 界面语言
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
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = text
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                // 主题模式
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
                            "system" to s.settingsThemeSystem,
                            "light" to s.settingsThemeLight,
                            "dark" to s.settingsThemeDark
                        ).forEach { (mode, label) ->
                            val selected = config.themeMode == mode
                            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                    .background(bg)
                                    .clickable { onUpdateThemeMode(mode) }
                                    .padding(horizontal = 10.dp),
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

                // 自动检查更新
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
            }
        }

        // 卡片 2：账号与应用设置
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
            ) {
                SettingsCardTitle(Icons.Outlined.People, s.settingsAccountAndAppCardTitle)
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                // 切号默认目标应用
                SettingRow(
                    icon = Icons.Outlined.People,
                    title = s.settingsDefaultSwitchTargetTitle,
                    description = s.settingsDefaultSwitchTargetDesc,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val currentTarget = config.defaultSwitchTarget.ifBlank { "all" }
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
                            DefaultSwitchTarget.ALL.value to s.settingsDefaultSwitchTargetAll,
                            DefaultSwitchTarget.IDE_ONLY.value to s.settingsDefaultSwitchTargetIdeOnly,
                            DefaultSwitchTarget.APP_CLI_ONLY.value to s.settingsDefaultSwitchTargetAppCliOnly,
                            DefaultSwitchTarget.REMEMBER_LAST.value to s.settingsDefaultSwitchTargetRemember
                        ).forEach { (targetValue, label) ->
                            val selected = currentTarget == targetValue
                            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                    .background(bg)
                                    .clickable { onUpdateDefaultSwitchTarget(targetValue) }
                                    .padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = text
                                )
                            }
                        }
                    }
                }

                if (onConfigureHostPath != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                    // 宿主路径配置
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
}
