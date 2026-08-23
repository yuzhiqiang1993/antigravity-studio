package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.screens.settings.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.awt.Desktop
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val config by viewModel.config.collectAsState()
    val loadError by viewModel.configLoadError.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    var portInput by remember(config.proxyPort) { mutableStateOf(config.proxyPort.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var openDirectoryError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        PageHeader(
            title = s.settingsTitle,
            subtitle = s.settingsSubtitle
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 780.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
                    SettingsSectionSelector(
                        selectedSection = selectedSection,
                        s = s,
                        onSelect = { selectedSection = it }
                    )
                    SettingsContent(
                        selectedSection = selectedSection,
                        config = config,
                        loadError = loadError,
                        portInput = portInput,
                        portError = portError,
                        openDirectoryError = openDirectoryError,
                        onUpdateLanguage = viewModel::updateLanguage,
                        onUpdateThemeMode = viewModel::updateThemeMode,
                        onUpdateAutoCheckUpdate = viewModel::updateAutoCheckUpdate,
                        onUpdateDeveloperMode = viewModel::updateDeveloperMode,
                        onToggleDeveloperMode = viewModel::toggleDeveloperMode,
                        updateState = updateState,
                        onCheckUpdate = { viewModel.checkForUpdates(isManual = true) },
                        onOpenUpdateDialog = { viewModel.openUpdateDialog() },
                        onPortInputChange = {
                            portInput = it
                            portError = null
                        },
                        onSavePort = {
                            val port = portInput.toIntOrNull()
                            if (port == null || port !in 1024..65535) {
                                portError = s.settingsPortInvalid
                            } else {
                                portError = null
                                viewModel.updateProxyPort(port)
                            }
                        },
                        onOpenDirectory = {
                            openDirectoryError = openConfigDirectory(viewModel)
                        },
                        onConfigureHostPath = { key, title -> viewModel.openHostPathDialog(key, title) },
                        s = s
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.lg)
                ) {
                    SettingsSidebar(
                        selectedSection = selectedSection,
                        s = s,
                        onSelect = { selectedSection = it }
                    )
                    SettingsContent(
                        selectedSection = selectedSection,
                        config = config,
                        loadError = loadError,
                        portInput = portInput,
                        portError = portError,
                        openDirectoryError = openDirectoryError,
                        onUpdateLanguage = { viewModel.updateLanguage(it) },
                        onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                        onUpdateAutoCheckUpdate = { viewModel.updateAutoCheckUpdate(it) },
                        onUpdateDeveloperMode = { viewModel.updateDeveloperMode(it) },
                        onToggleDeveloperMode = { viewModel.toggleDeveloperMode() },
                        updateState = updateState,
                        onCheckUpdate = { viewModel.checkForUpdates(isManual = true) },
                        onOpenUpdateDialog = { viewModel.openUpdateDialog() },
                        onPortInputChange = {
                            portInput = it
                            portError = null
                        },
                        onSavePort = {
                            val port = portInput.toIntOrNull()
                            if (port == null || port !in 1024..65535) {
                                portError = s.settingsPortInvalid
                            } else {
                                portError = null
                                viewModel.updateProxyPort(port)
                            }
                        },
                        onOpenDirectory = {
                            openDirectoryError = openConfigDirectory(viewModel)
                        },
                        onConfigureHostPath = { key, title -> viewModel.openHostPathDialog(key, title) },
                        s = s,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    selectedSection: SettingsSection,
    s: Strings,
    onSelect: (SettingsSection) -> Unit
) {
    StudioCard(
        modifier = Modifier.width(AppTokens.Size.sidebarWidth)
    ) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
        ) {
            SettingsSection.values().forEach { section ->
                SettingsNavItem(
                    section = section,
                    selected = selectedSection == section,
                    s = s,
                    onClick = { onSelect(section) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionSelector(
    selectedSection: SettingsSection,
    s: Strings,
    onSelect: (SettingsSection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
    ) {
        SettingsSection.values().forEach { section ->
            FilterChip(
                selected = selectedSection == section,
                onClick = { onSelect(section) },
                label = { Text(section.title(s), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = section.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.Size.iconSmall)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsNavItem(
    section: SettingsSection,
    selected: Boolean,
    s: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.Radius.small))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.content, vertical = AppTokens.Spacing.content),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
    ) {
        Icon(
            imageVector = section.icon(),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(AppTokens.Size.iconMedium)
        )
        Text(
            text = section.title(s),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsContent(
    selectedSection: SettingsSection,
    config: AppConfig,
    loadError: String?,
    portInput: String,
    portError: String?,
    openDirectoryError: String?,
    onUpdateLanguage: (com.yuzhiqiang.antigravity.i18n.AppLanguage) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    onUpdateAutoCheckUpdate: (Boolean) -> Unit,
    onUpdateDeveloperMode: (Boolean) -> Unit,
    onToggleDeveloperMode: () -> Unit,
    updateState: com.yuzhiqiang.antigravity.update.model.UpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    onOpenDirectory: () -> Unit,
    onConfigureHostPath: ((String, String) -> Unit)? = null,
    s: Strings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
        when (selectedSection) {
            SettingsSection.GENERAL -> GeneralSettingsSection(
                config = config,
                onUpdateLanguage = onUpdateLanguage,
                onUpdateThemeMode = onUpdateThemeMode,
                onUpdateAutoCheckUpdate = onUpdateAutoCheckUpdate,
                onUpdateDeveloperMode = onUpdateDeveloperMode,
                onConfigureHostPath = onConfigureHostPath,
                s = s
            )
            SettingsSection.NETWORK -> NetworkSettingsSection(
                portInput = portInput,
                portError = portError,
                onPortInputChange = onPortInputChange,
                onSavePort = onSavePort,
                s = s
            )
            SettingsSection.DATA -> DataSettingsSection(
                loadError = loadError,
                openDirectoryError = openDirectoryError,
                onOpenDirectory = onOpenDirectory,
                s = s
            )
            SettingsSection.ABOUT -> AboutSettingsSection(
                updateState = updateState,
                onCheckUpdate = onCheckUpdate,
                onOpenUpdateDialog = onOpenUpdateDialog,
                onToggleDeveloperMode = onToggleDeveloperMode,
                onOpenConfigDirectory = onOpenDirectory,
                s = s
            )
        }
    }
}

private fun openConfigDirectory(viewModel: AppViewModel): String? {
    val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
    return try {
        val dir = viewModel.configStore.configFile.parentFile ?: File(System.getProperty("user.home"))
        if (!dir.exists()) dir.mkdirs()
        var opened = false
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(dir)
                opened = true
            } catch (_: Exception) {
            }
        }
        if (!opened) {
            val osName = System.getProperty("os.name", "").lowercase()
            when {
                osName.contains("win") -> {
                    ProcessBuilder("explorer.exe", dir.absolutePath).start()
                    opened = true
                }
                osName.contains("mac") -> {
                    ProcessBuilder("/usr/bin/open", dir.absolutePath).start()
                    opened = true
                }
                else -> {
                    ProcessBuilder("xdg-open", dir.absolutePath).start()
                    opened = true
                }
            }
        }
        if (opened) null else s.settingsUnsupportedPlatform
    } catch (e: Exception) {
        s.settingsOpenDirFailed(e.message ?: s.commonUnknown)
    }
}
