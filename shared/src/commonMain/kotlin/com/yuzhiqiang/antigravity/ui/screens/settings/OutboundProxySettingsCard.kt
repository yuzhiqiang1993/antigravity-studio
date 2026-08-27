package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.network.PlatformNetworkConfig
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.delay

@Composable
internal fun OutboundProxySettingsCard(
    savedConfig: OutboundProxyConfig,
    isTesting: Boolean,
    testResult: ConnectionTester.OutboundProxyTestResult?,
    onSave: (OutboundProxyConfig) -> Unit,
    onTest: (OutboundProxyConfig) -> Unit,
    onClearTestResult: () -> Unit,
    s: Strings
) {
    var draft by remember(savedConfig) { mutableStateOf(savedConfig) }
    var inspectionRefreshTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000L)
            inspectionRefreshTick += 1L
        }
    }

    val inspection = remember(draft, testResult, inspectionRefreshTick) {
        PlatformNetworkConfig.inspectOutboundProxy(draft)
    }

    val hostError = draft.mode == OutboundProxyMode.MANUAL &&
            (draft.host.isBlank() || "://" in draft.host || "/" in draft.host)
    val portError = draft.mode == OutboundProxyMode.MANUAL && draft.port !in 1..65535
    val systemUnavailable = draft.mode == OutboundProxyMode.SYSTEM &&
            inspection.systemProxies.isEmpty() &&
            !draft.fallbackToDirect

    fun updateDraftAndSave(updated: OutboundProxyConfig) {
        draft = updated
        onClearTestResult()
        onSave(updated)
    }

    fun updateDraftOnly(updated: OutboundProxyConfig) {
        draft = updated
        onClearTestResult()
    }

    val modeDescription = when (draft.mode) {
        OutboundProxyMode.AUTO -> s.settingsOutboundAutoDescription
        OutboundProxyMode.DIRECT -> s.settingsOutboundDirectDescription
        OutboundProxyMode.SYSTEM -> s.settingsOutboundSystemDescription
        OutboundProxyMode.MANUAL -> s.settingsOutboundManualDescription
    }

    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
        ) {
            // 卡片标题与说明
            SettingsCardTitle(Icons.Outlined.Public, s.settingsOutboundProxyTitle)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            // 模式选择行 (胶囊分段选择器)
            SettingRow(
                icon = Icons.Outlined.Language,
                title = s.settingsOutboundMode,
                description = modeDescription,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                OutboundProxyModePillGroup(
                    selectedMode = draft.mode,
                    onSelectMode = { newMode ->
                        if (newMode == OutboundProxyMode.MANUAL) {
                            updateDraftOnly(draft.copy(mode = newMode))
                        } else {
                            val updated = draft.copy(mode = newMode)
                            updateDraftAndSave(updated)
                        }
                    },
                    s = s
                )
            }

            // 直连回退开关 (仅在系统代理或手动代理模式下显示)
            AnimatedVisibility(
                visible = draft.mode == OutboundProxyMode.SYSTEM || draft.mode == OutboundProxyMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.AltRoute,
                        title = s.settingsOutboundFallback,
                        description = s.settingsOutboundFallbackDescription,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Switch(
                            checked = draft.fallbackToDirect,
                            onCheckedChange = { updateDraftAndSave(draft.copy(fallbackToDirect = it)) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            // 手动代理配置输入行 (仅在手动代理模式下平滑展开)
            AnimatedVisibility(
                visible = draft.mode == OutboundProxyMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    CompactManualProxyForm(
                        config = draft,
                        hostError = hostError,
                        portError = portError,
                        hasChanges = draft != savedConfig,
                        onProtocolChange = { proto ->
                            val updated = draft.copy(protocol = proto)
                            if (!hostError && !portError && savedConfig.mode == OutboundProxyMode.MANUAL) {
                                updateDraftAndSave(updated)
                            } else {
                                updateDraftOnly(updated)
                            }
                        },
                        onHostChange = { updateDraftOnly(draft.copy(host = it)) },
                        onPortChange = { raw ->
                            val port = raw.filter(Char::isDigit).toIntOrNull() ?: 0
                            updateDraftOnly(draft.copy(port = port))
                        },
                        onApply = {
                            if (!hostError && !portError) {
                                updateDraftAndSave(draft)
                            }
                        },
                        s = s
                    )
                }
            }

            if (systemUnavailable) {
                Text(
                    text = s.settingsOutboundSystemUnavailable,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // 底部一体化状态与连通性测试条
            OutboundProxyStatusBar(
                config = draft,
                inspection = inspection,
                testResult = testResult,
                isTesting = isTesting,
                onTest = { onTest(draft) },
                s = s
            )
        }
    }
}
