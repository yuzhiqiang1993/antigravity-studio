package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.SectionCard
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import java.awt.Desktop

private enum class SettingsSection {
    GENERAL,
    NETWORK,
    DATA,
    ABOUT
}

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val config by viewModel.config.collectAsState()
    val loadError by viewModel.configLoadError.collectAsState()
    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    var portInput by remember(config.proxyPort) { mutableStateOf(config.proxyPort.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var openDirectoryError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PageHeader(
            title = s.settingsTitle,
            subtitle = s.settingsSubtitle
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 780.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSectionSelector(
                        selectedSection = selectedSection,
                        s = s,
                        onSelect = { selectedSection = it }
                    )
                    SettingsContent(
                        selectedSection = selectedSection,
                        viewModel = viewModel,
                        config = config,
                        loadError = loadError,
                        portInput = portInput,
                        portError = portError,
                        openDirectoryError = openDirectoryError,
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
                        s = s
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    SettingsSidebar(
                        selectedSection = selectedSection,
                        s = s,
                        onSelect = { selectedSection = it }
                    )
                    Spacer(Modifier.width(18.dp))
                    SettingsContent(
                        selectedSection = selectedSection,
                        viewModel = viewModel,
                        config = config,
                        loadError = loadError,
                        portInput = portInput,
                        portError = portError,
                        openDirectoryError = openDirectoryError,
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
    Column(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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

@Composable
private fun SettingsSectionSelector(
    selectedSection: SettingsSection,
    s: Strings,
    onSelect: (SettingsSection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSection.values().toList().chunked(2).forEach { rowSections ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSections.forEach { section ->
                    SettingsNavItem(
                        section = section,
                        selected = selectedSection == section,
                        s = s,
                        onClick = { onSelect(section) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSections.size == 1) Spacer(Modifier.weight(1f))
            }
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
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(section.icon(), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text = section.title(s),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsContent(
    selectedSection: SettingsSection,
    viewModel: AppViewModel,
    config: com.yuzhiqiang.antigravity.domain.model.AppConfig,
    loadError: String?,
    portInput: String,
    portError: String?,
    openDirectoryError: String?,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    onOpenDirectory: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (selectedSection) {
            SettingsSection.GENERAL -> GeneralSettings(viewModel, s)
            SettingsSection.NETWORK -> NetworkSettings(
                portInput = portInput,
                portError = portError,
                onPortInputChange = onPortInputChange,
                onSavePort = onSavePort,
                s = s
            )
            SettingsSection.DATA -> DataSettings(
                viewModel = viewModel,
                loadError = loadError,
                openDirectoryError = openDirectoryError,
                onOpenDirectory = onOpenDirectory,
                s = s
            )
            SettingsSection.ABOUT -> AboutSettings(viewModel, s)
        }
    }
}

@Composable
private fun GeneralSettings(
    viewModel: AppViewModel,
    s: Strings
) {
    val config by viewModel.config.collectAsState()
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardTitle(Icons.Outlined.Settings, s.settingsGeneral)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // 语言
        SettingRow(
            icon = Icons.Outlined.Language,
            title = s.settingsLanguage,
            description = s.settingsLanguageDescription
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AppLanguage.values().forEach { lang ->
                    val selected = I18nManager.currentLanguage == lang
                    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val text = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(bg)
                            .clickable { viewModel.updateLanguage(lang) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.ZH_CN) "简体中文" else "English",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = text
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        // 主题模式（三段胶囊）
        SettingRow(
            icon = Icons.Outlined.Palette,
            title = s.settingsTheme,
            description = s.settingsThemeDescription
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(
                    Triple("system", s.settingsThemeSystem, Icons.Outlined.Computer),
                    Triple("light", s.settingsThemeLight, Icons.Outlined.Palette),
                    Triple("dark", s.settingsThemeDark, Icons.Outlined.Settings)
                ).forEach { (mode, label, _) ->
                    val selected = config.themeMode == mode
                    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val text = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(bg)
                            .clickable { viewModel.updateThemeMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = text
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSettings(
    viewModel: AppViewModel,
    s: Strings
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero Card
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = s.appName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v1.0.0",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = s.appSubtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Compose Desktop", "Kotlin Multiplatform", "BYOK Proxy", "Material 3").forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4 Quick Action Cards (开源仓库、配置目录、开发者、意见反馈)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AboutActionCard(
                icon = Icons.Outlined.Code,
                title = "开源仓库",
                subtitle = "yuzhiqiang1993/antigravity-studio",
                onClick = { openWebUrl("https://github.com/yuzhiqiang1993/antigravity-studio") },
                modifier = Modifier.weight(1f)
            )
            AboutActionCard(
                icon = Icons.Outlined.Folder,
                title = "配置目录",
                subtitle = "打开配置文件所在目录",
                onClick = { openConfigDirectory(viewModel) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AboutActionCard(
                icon = Icons.Outlined.Person,
                title = "开发者",
                subtitle = "喻志强 (@yuzhiqiang1993)",
                onClick = { openWebUrl("https://github.com/yuzhiqiang1993") },
                modifier = Modifier.weight(1f)
            )
            AboutActionCard(
                icon = Icons.Outlined.Feedback,
                title = "意见反馈",
                subtitle = "提交 Issue 与 Feature Request",
                onClick = { openWebUrl("https://github.com/yuzhiqiang1993/antigravity-studio/issues") },
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
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun openWebUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(java.net.URI(url))
        }
    }
}

@Composable
private fun NetworkSettings(
    portInput: String,
    portError: String?,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    s: Strings
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardTitle(Icons.Outlined.Router, s.settingsNetwork)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingRow(
            icon = Icons.Outlined.Settings,
            title = s.settingsPort,
            description = s.settingsPortDescription
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortInputChange,
                    modifier = Modifier.width(130.dp),
                    singleLine = true,
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it, fontSize = 10.sp) } }
                )
                Button(onClick = onSavePort, shape = RoundedCornerShape(8.dp)) {
                    Text(s.commonSave, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DataSettings(
    viewModel: AppViewModel,
    loadError: String?,
    openDirectoryError: String?,
    onOpenDirectory: () -> Unit,
    s: Strings
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SettingsCardTitle(Icons.Outlined.Folder, s.settingsData)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingRow(
            icon = Icons.Outlined.Folder,
            title = s.settingsStoragePath,
            description = s.settingsStorageDescription
        ) {
            OutlinedButton(onClick = onOpenDirectory, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(s.settingsOpenDirectory, fontSize = 11.sp)
            }
        }
        Text(
            text = viewModel.configStore.configFile.absolutePath,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        if (loadError != null) {
            DiagnosticMessage(
                title = s.commonError,
                message = loadError,
                isError = true
            )
        }
        if (openDirectoryError != null) {
            DiagnosticMessage(
                title = s.settingsDirectoryOpenError,
                message = openDirectoryError,
                isError = true
            )
        }
    }
}

@Composable
private fun SettingsCardTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        val compact = maxWidth < 560.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingLabel(icon, title, description)
                content()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingLabel(
                    icon = icon,
                    title = title,
                    description = description,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingLabel(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticMessage(
    title: String,
    message: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Text(message, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun SettingsSection.title(s: Strings): String = when (this) {
    SettingsSection.GENERAL -> s.settingsGeneral
    SettingsSection.NETWORK -> s.settingsNetwork
    SettingsSection.DATA -> s.settingsData
    SettingsSection.ABOUT -> s.settingsAboutSection
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.GENERAL -> Icons.Outlined.Settings
    SettingsSection.NETWORK -> Icons.Outlined.Router
    SettingsSection.DATA -> Icons.Outlined.Folder
    SettingsSection.ABOUT -> Icons.Outlined.Info
}

private fun openConfigDirectory(viewModel: AppViewModel): String? {
    return try {
        val parent = viewModel.configStore.configFile.parentFile
            ?: throw IllegalStateException("config directory is unavailable")
        if (!parent.exists()) parent.mkdirs()

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(parent)
        } else {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("explorer.exe", parent.absolutePath).start()
                os.contains("mac") -> ProcessBuilder("/usr/bin/open", parent.absolutePath).start()
                else -> ProcessBuilder("xdg-open", parent.absolutePath).start()
            }
        }
        null
    } catch (error: Exception) {
        error.message ?: "Unable to open directory"
    }
}
