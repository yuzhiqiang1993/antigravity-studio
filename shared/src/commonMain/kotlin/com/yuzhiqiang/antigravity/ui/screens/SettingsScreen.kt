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
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.ui.components.PageHeader
import com.yuzhiqiang.antigravity.ui.components.StudioSlidingTabLayout
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.components.tour.tourAnchor
import com.yuzhiqiang.antigravity.ui.screens.settings.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val tourManager = LocalSpotlightTourManager.current
    val config by viewModel.config.collectAsState()
    val loadError by viewModel.configLoadError.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val networkSettingsRequest by viewModel.networkSettingsRequest.collectAsState()
    val isTestingOutboundProxy by viewModel.isTestingOutboundProxy.collectAsState()
    val outboundProxyTestResult by viewModel.outboundProxyTestResult.collectAsState()

    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    var portInput by remember(config.proxyPort) { mutableStateOf(config.proxyPort.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var openDirectoryError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(networkSettingsRequest) {
        if (networkSettingsRequest > 0L) {
            selectedSection = SettingsSection.NETWORK
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(tourManager.currentStep) {
        if (tourManager.currentStep == TourStep.ABOUT_REOPEN_CARD) {
            selectedSection = SettingsSection.ABOUT
        }
    }

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
            tabHeight = 40.dp,
            modifier = Modifier.tourAnchor(TourStep.SETTINGS_PANEL, tourManager)
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
                onUpdateThemePalette = { viewModel.updateThemePalette(it) },
                onUpdateAutoCheckUpdate = { viewModel.updateAutoCheckUpdate(it) },
                onUpdateDefaultSwitchTarget = { viewModel.updateDefaultSwitchTarget(it) },
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
                outboundProxyTestResult = outboundProxyTestResult,
                isTestingOutboundProxy = isTestingOutboundProxy,
                onSaveOutboundProxy = viewModel::saveOutboundProxy,
                onTestOutboundProxy = viewModel::testOutboundProxy,
                onClearOutboundProxyTestResult = viewModel::clearOutboundProxyTestResult,
                onOpenDirectory = {
                    openDirectoryError = openConfigDirectory(viewModel)
                },
                onUpdateCustomPricing = { viewModel.updateCustomPricingPath(it) },
                onConfigureHostPath = { key, title -> viewModel.openHostPathDialog(key, title) },
                onOpenOnboarding = { viewModel.openOnboardingDialog() },
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
    onUpdateThemePalette: (String) -> Unit,
    onUpdateAutoCheckUpdate: (Boolean) -> Unit,
    onUpdateDefaultSwitchTarget: (String) -> Unit,
    onUpdateDeveloperMode: (Boolean) -> Unit,
    onToggleDeveloperMode: () -> Unit,
    updateState: com.yuzhiqiang.antigravity.update.model.UpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    outboundProxyTestResult: ConnectionTester.OutboundProxyTestResult?,
    isTestingOutboundProxy: Boolean,
    onSaveOutboundProxy: (OutboundProxyConfig) -> Unit,
    onTestOutboundProxy: (OutboundProxyConfig) -> Unit,
    onClearOutboundProxyTestResult: () -> Unit,
    onOpenDirectory: () -> Unit,
    onUpdateCustomPricing: (String?) -> Unit,
    onConfigureHostPath: ((String, String) -> Unit)? = null,
    onOpenOnboarding: () -> Unit = {},
    s: Strings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
        when (selectedSection) {
            SettingsSection.GENERAL -> GeneralSettingsSection(
                config = config,
                onUpdateLanguage = onUpdateLanguage,
                onUpdateThemeMode = onUpdateThemeMode,
                onUpdateThemePalette = onUpdateThemePalette,
                onUpdateAutoCheckUpdate = onUpdateAutoCheckUpdate,
                onUpdateDefaultSwitchTarget = onUpdateDefaultSwitchTarget,
                onConfigureHostPath = onConfigureHostPath,
                s = s
            )

            SettingsSection.NETWORK -> NetworkSettingsSection(
                portInput = portInput,
                portError = portError,
                outboundProxy = config.outboundProxy,
                isTestingOutboundProxy = isTestingOutboundProxy,
                outboundProxyTestResult = outboundProxyTestResult,
                onPortInputChange = onPortInputChange,
                onSavePort = onSavePort,
                onSaveOutboundProxy = onSaveOutboundProxy,
                onTestOutboundProxy = onTestOutboundProxy,
                onClearOutboundProxyTestResult = onClearOutboundProxyTestResult,
                s = s
            )

            SettingsSection.DATA -> DataSettingsSection(
                loadError = loadError,
                openDirectoryError = openDirectoryError,
                customPricingPath = config.customPricingPath,
                onUpdateCustomPricing = onUpdateCustomPricing,
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
                onOpenOnboarding = onOpenOnboarding,
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
        val opened = DesktopPlatformService.openDirectory(dir)
        if (opened) null else s.settingsUnsupportedPlatform
    } catch (e: Exception) {
        s.settingsOpenDirFailed(e.message ?: s.commonUnknown)
    }
}
