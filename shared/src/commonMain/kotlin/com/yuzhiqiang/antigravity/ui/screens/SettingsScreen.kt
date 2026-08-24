package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioSlidingTabLayout
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
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

    val sections = remember { SettingsSection.values() }
    val tabItems = remember(s) {
        sections.map { section ->
            StudioTabItem(
                key = section,
                title = section.title(s),
                icon = section.icon()
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.pageSection)
    ) {
        PageHeader(title = s.settingsTitle)

        // 现代滑动药丸 TabLayout
        StudioSlidingTabLayout(
            items = tabItems,
            selectedKey = selectedSection,
            onSelect = { selectedSection = it },
            tabHeight = 40.dp
        )

        // 通栏内容区（带 ViewPager 级方向感知水平滑动动画）
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }

                slideIntoContainer(
                    towards = direction,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(200)) togetherWith
                slideOutOfContainer(
                    towards = direction,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(animationSpec = tween(160))
            },
            modifier = Modifier.fillMaxWidth()
        ) { targetSection ->
            SettingsContent(
                selectedSection = targetSection,
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
                s = s
            )
        }
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
                isDeveloperMode = config.developerMode,
                onCheckUpdate = onCheckUpdate,
                onOpenUpdateDialog = onOpenUpdateDialog,
                onSetDeveloperMode = onUpdateDeveloperMode,
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
