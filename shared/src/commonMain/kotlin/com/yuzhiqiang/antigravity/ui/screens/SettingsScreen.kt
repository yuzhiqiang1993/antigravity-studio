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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BrandMark
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.awt.Desktop
import java.io.File
import java.net.URI

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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
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
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            SettingsCardTitle(Icons.Outlined.Settings, s.settingsGeneral)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 语言设置
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
                                .clickable { viewModel.updateLanguage(lang) }
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

            // 主题模式
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
                                .clickable { viewModel.updateThemeMode(mode) }
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

@Composable
private fun NetworkSettings(
    portInput: String,
    portError: String?,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.card),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            SettingsCardTitle(Icons.Outlined.Router, s.settingsNetwork)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                icon = Icons.Outlined.Router,
                title = s.settingsPort,
                description = s.settingsPortDescription
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = onPortInputChange,
                        isError = portError != null,
                        singleLine = true,
                        modifier = Modifier.width(130.dp),
                        shape = RoundedCornerShape(AppTokens.Radius.medium)
                    )
                    Button(
                        onClick = onSavePort,
                        shape = RoundedCornerShape(AppTokens.Radius.medium)
                    ) {
                        Text(s.commonSave, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (portError != null) {
                Text(
                    text = portError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
                title = "配置文件目录",
                description = "查看或备份本地持久化的服务商配置与策略数据"
            ) {
                OutlinedButton(
                    onClick = onOpenDirectory,
                    shape = RoundedCornerShape(AppTokens.Radius.medium)
                ) {
                    Text("打开目录", style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun AboutSettings(
    viewModel: AppViewModel,
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

                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
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
                                text = "v2.0.0",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = s.appSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 快捷操作卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
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
                subtitle = "打开数据与模型配置文件",
                onClick = { openConfigDirectory(viewModel) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
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
                title = "反馈建议",
                subtitle = "提交 Issue 或加入交流群",
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

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppTokens.Size.iconLarge)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(AppTokens.Spacing.md))
        content()
    }
}

@Composable
private fun SettingsCardTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppTokens.Size.iconLarge)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.GENERAL -> Icons.Outlined.Settings
    SettingsSection.NETWORK -> Icons.Outlined.Router
    SettingsSection.DATA -> Icons.Outlined.Folder
    SettingsSection.ABOUT -> Icons.Outlined.Info
}

private fun SettingsSection.title(s: Strings): String = when (this) {
    SettingsSection.GENERAL -> s.settingsGeneral
    SettingsSection.NETWORK -> s.settingsNetwork
    SettingsSection.DATA -> s.settingsData
    SettingsSection.ABOUT -> s.settingsAbout
}

private fun openWebUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {}
}

private fun openConfigDirectory(viewModel: AppViewModel): String? {
    return try {
        val path = viewModel.configStore.currentConfig
        val dir = File(System.getProperty("user.home"), ".antigravity")
        if (!dir.exists()) dir.mkdirs()
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
            null
        } else {
            "当前平台不支持直接打开文件夹"
        }
    } catch (e: Exception) {
        "打开配置目录失败：${e.message}"
    }
}
