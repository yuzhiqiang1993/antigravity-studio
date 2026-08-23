package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuzhiqiang.antigravity.i18n.Strings

enum class SettingsSection {
    GENERAL,
    NETWORK,
    DATA,
    ABOUT
}

fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.GENERAL -> Icons.Outlined.Settings
    SettingsSection.NETWORK -> Icons.Outlined.Router
    SettingsSection.DATA -> Icons.Outlined.Folder
    SettingsSection.ABOUT -> Icons.Outlined.Info
}

fun SettingsSection.title(s: Strings): String = when (this) {
    SettingsSection.GENERAL -> s.settingsGeneral
    SettingsSection.NETWORK -> s.settingsNetwork
    SettingsSection.DATA -> s.settingsData
    SettingsSection.ABOUT -> s.settingsAbout
}
