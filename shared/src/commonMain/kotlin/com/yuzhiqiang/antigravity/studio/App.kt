package com.yuzhiqiang.antigravity.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.LocalStrings
import com.yuzhiqiang.antigravity.i18n.StringsEn
import com.yuzhiqiang.antigravity.i18n.StringsZh
import com.yuzhiqiang.antigravity.ui.components.AppSidebar
import com.yuzhiqiang.antigravity.ui.components.AppSnackbarHost
import com.yuzhiqiang.antigravity.ui.dialogs.ConfirmDialog
import com.yuzhiqiang.antigravity.ui.dialogs.DoctorDialog
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import com.yuzhiqiang.antigravity.ui.screens.ActivityScreen
import com.yuzhiqiang.antigravity.ui.screens.ModelsScreen
import com.yuzhiqiang.antigravity.ui.screens.OverviewScreen
import com.yuzhiqiang.antigravity.ui.screens.SettingsScreen
import com.yuzhiqiang.antigravity.ui.theme.AntigravityTheme

@Composable
fun App(
    viewModel: AppViewModel = remember { AppViewModel() }
) {
    val currentLang = I18nManager.currentLanguage
    val currentStrings = if (currentLang == AppLanguage.ZH_CN) StringsZh else StringsEn
    val config by viewModel.config.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val showDoctor by viewModel.showDoctorDialog.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val confirmState by viewModel.confirmDialog.collectAsState()

    CompositionLocalProvider(LocalStrings provides currentStrings) {
        AntigravityTheme(themeMode = config.themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
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
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (currentTab) {
                                NavTab.OVERVIEW -> OverviewScreen(viewModel = viewModel)
                                NavTab.MODELS -> ModelsScreen(viewModel = viewModel)
                                NavTab.ACTIVITY -> ActivityScreen(viewModel = viewModel)
                                NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }

                    // 全局 Toast 通知 — 对标 agy-byok 的 NoticeBar
                    AppSnackbarHost(
                        notice = notice,
                        onDismiss = { viewModel.dismissNotice() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Doctor 对话框
                if (showDoctor) {
                    DoctorDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.closeDoctorDialog() }
                    )
                }

                // 通用确认对话框 — 对标 agy-byok 的 ConfirmModal
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
            }
        }
    }
}
