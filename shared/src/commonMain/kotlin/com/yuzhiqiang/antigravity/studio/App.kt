package com.yuzhiqiang.antigravity.studio

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.LocalStrings
import com.yuzhiqiang.antigravity.i18n.StringsEn
import com.yuzhiqiang.antigravity.i18n.StringsZh
import com.yuzhiqiang.antigravity.ui.components.AppSidebar
import com.yuzhiqiang.antigravity.ui.components.AppSnackbarHost
import com.yuzhiqiang.antigravity.ui.components.StudioAmbientBackground
import com.yuzhiqiang.antigravity.ui.dialogs.ConfirmDialog
import com.yuzhiqiang.antigravity.ui.dialogs.CustomHostPathDialog
import com.yuzhiqiang.antigravity.ui.dialogs.DoctorDialog
import com.yuzhiqiang.antigravity.ui.dialogs.UpdateDialog
import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import com.yuzhiqiang.antigravity.ui.screens.AccountsScreen
import com.yuzhiqiang.antigravity.ui.screens.ActivityScreen
import com.yuzhiqiang.antigravity.ui.screens.ModelsScreen
import com.yuzhiqiang.antigravity.ui.screens.OverviewScreen
import com.yuzhiqiang.antigravity.ui.screens.SettingsScreen

import com.yuzhiqiang.antigravity.ui.theme.AntigravityTheme
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Window as AwtWindow

@Composable
fun App(
    viewModel: AppViewModel = koinViewModel(),
    window: AwtWindow? = null
) {
    val currentLang = I18nManager.currentLanguage
    val currentStrings = if (currentLang == AppLanguage.ZH_CN) StringsZh else StringsEn
    val config by viewModel.config.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val showDoctor by viewModel.showDoctorDialog.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val confirmState by viewModel.confirmDialog.collectAsState()
    val hostPathDialogState by viewModel.hostPathDialogState.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val showOnboarding by viewModel.showOnboardingDialog.collectAsState()
    val activeRelease by viewModel.activeRelease.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    // 窗口焦点感知：恢复可见时立即刷新宿主账号与运行状态
    DisposableEffect(window) {
        val focusListener = object : java.awt.event.WindowFocusListener {
            override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                viewModel.onWindowFocusGained()
            }
            override fun windowLostFocus(e: java.awt.event.WindowEvent?) {}
        }
        window?.addWindowFocusListener(focusListener)
        onDispose {
            window?.removeWindowFocusListener(focusListener)
        }
    }


    val tourManager = remember {
        com.yuzhiqiang.antigravity.ui.components.tour.SpotlightTourManager().apply {
            onNavigateTab = { tab -> viewModel.selectTab(tab) }
        }
    }

    LaunchedEffect(showOnboarding) {
        if (showOnboarding && !tourManager.isActive) {
            tourManager.startTour()
        }
    }

    KoinContext {
    CompositionLocalProvider(
        LocalStrings provides currentStrings,
        com.yuzhiqiang.antigravity.ui.components.tour.LocalSpotlightTourManager provides tourManager
    ) {
        AntigravityTheme(
            themeMode = config.themeMode,
            themePalette = config.themePalette
        ) {
            val backgroundColor = MaterialTheme.colorScheme.background
            SideEffect {
                window?.let { w ->
                    val awtColor = java.awt.Color(backgroundColor.toArgb())
                    if (w.background != awtColor) {
                        w.background = awtColor
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = backgroundColor
            ) {
                StudioAmbientBackground(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            AppSidebar(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxHeight()
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.decelerateEasing)) +
                                                slideInVertically(animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.decelerateEasing)) { 14 })
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(AppTokens.Motion.durationShort, easing = AppTokens.Motion.accelerateEasing))
                                            )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) { tab ->
                                    when (tab) {
                                        NavTab.OVERVIEW -> OverviewScreen(viewModel = viewModel)
                                        NavTab.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                                        NavTab.MODELS -> ModelsScreen(viewModel = viewModel)
                                        NavTab.ACTIVITY -> ActivityScreen(viewModel = viewModel)
                                        NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }

                        // 全局 Toast 通知
                        AppSnackbarHost(
                            notice = notice,
                            onDismiss = { viewModel.dismissNotice() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                // Doctor 对话框
                if (showDoctor) {
                    DoctorDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeDoctorDialog() }
                    )
                }

                // 通用确认对话框
                confirmState?.let { state ->
                    ConfirmDialog(
                        title = state.title,
                        message = state.message,
                        confirmLabel = state.confirmLabel,
                        cancelLabel = state.cancelLabel,
                        isDestructive = state.isDestructive,
                        onConfirm = state.onConfirm,
                        onDismiss = { viewModel.dismissConfirmDialog() }
                    )
                }

                // 自定义宿主路径配置对话框
                hostPathDialogState?.let { state ->
                    CustomHostPathDialog(
                        hostKey = state.hostKey,
                        hostTitle = state.hostTitle,
                        initialPath = state.currentPath,
                        onSave = { path -> viewModel.saveCustomHostPath(state.hostKey, path) },
                        onDismiss = { viewModel.closeHostPathDialog() }
                    )
                }

                // 版本更新提示对话框
                if (showUpdateDialog && activeRelease != null) {
                    val rel = activeRelease!!
                    UpdateDialog(
                        release = rel,
                        currentVersion = AppVersion.CURRENT,
                        downloadState = downloadState,
                        onStartDownload = { viewModel.startDownloadUpdate(rel) },
                        onCancelDownload = { viewModel.cancelDownloadUpdate() },
                        onInstall = { file -> viewModel.installUpdate(file) },
                        onShowInFolder = { file -> viewModel.showDownloadedFileInFolder(file) },
                        onDismiss = { viewModel.dismissUpdateDialog() },
                        onIgnoreVersion = { version -> viewModel.ignoreUpdateVersion(version) }
                    )
                }

                // 全屏镂空挖孔聚光灯新手指引蒙层
                if (tourManager.isActive) {
                    com.yuzhiqiang.antigravity.ui.components.tour.SpotlightTourOverlay(
                        manager = tourManager,
                        onComplete = {
                            viewModel.completeOnboarding()
                            viewModel.showNotice(currentStrings.onboardingCompletedToast, com.yuzhiqiang.antigravity.ui.components.NoticeKind.SUCCESS)
                        }
                    )
                }
            }
        }
    }
    }
}
