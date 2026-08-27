package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val isValid = !hostError && !portError && !systemUnavailable
    val hasChanges = draft != savedConfig

    fun updateDraft(updated: OutboundProxyConfig) {
        draft = updated
        onClearTestResult()
    }

    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            SettingsCardTitle(Icons.Outlined.Public, s.settingsOutboundProxyTitle)
            Text(
                text = s.settingsOutboundProxyDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            Text(
                text = s.settingsOutboundMode,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    ProxyModeOptionCard(
                        title = s.settingsOutboundAuto,
                        description = s.settingsOutboundAutoDescription,
                        selected = draft.mode == OutboundProxyMode.AUTO,
                        badge = s.settingsOutboundRecommended,
                        onClick = { updateDraft(draft.copy(mode = OutboundProxyMode.AUTO)) },
                        modifier = Modifier.weight(1f)
                    )
                    ProxyModeOptionCard(
                        title = s.settingsOutboundDirect,
                        description = s.settingsOutboundDirectDescription,
                        selected = draft.mode == OutboundProxyMode.DIRECT,
                        onClick = { updateDraft(draft.copy(mode = OutboundProxyMode.DIRECT)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    ProxyModeOptionCard(
                        title = s.settingsOutboundSystem,
                        description = s.settingsOutboundSystemDescription,
                        selected = draft.mode == OutboundProxyMode.SYSTEM,
                        onClick = { updateDraft(draft.copy(mode = OutboundProxyMode.SYSTEM)) },
                        modifier = Modifier.weight(1f)
                    )
                    ProxyModeOptionCard(
                        title = s.settingsOutboundManual,
                        description = s.settingsOutboundManualDescription,
                        selected = draft.mode == OutboundProxyMode.MANUAL,
                        onClick = { updateDraft(draft.copy(mode = OutboundProxyMode.MANUAL)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(
                visible = draft.mode == OutboundProxyMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ManualProxyForm(
                    config = draft,
                    hostError = hostError,
                    portError = portError,
                    onProtocolChange = { updateDraft(draft.copy(protocol = it)) },
                    onHostChange = { updateDraft(draft.copy(host = it)) },
                    onPortChange = { value ->
                        updateDraft(draft.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: 0))
                    },
                    s = s
                )
            }

            AnimatedVisibility(
                visible = draft.mode == OutboundProxyMode.SYSTEM || draft.mode == OutboundProxyMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingRow(
                    icon = Icons.Outlined.Public,
                    title = s.settingsOutboundFallback,
                    description = s.settingsOutboundFallbackDescription
                ) {
                    Switch(
                        checked = draft.fallbackToDirect,
                        onCheckedChange = { updateDraft(draft.copy(fallbackToDirect = it)) }
                    )
                }
            }

            if (systemUnavailable) {
                Text(
                    text = s.settingsOutboundSystemUnavailable,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ProxyDetectionPanel(inspection = inspection, s = s)
            OutboundProxyTestPanel(result = testResult, s = s)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    TextButton(
                        onClick = { updateDraft(OutboundProxyConfig()) },
                        shape = RoundedCornerShape(AppTokens.Radius.small)
                    ) {
                        Text(s.settingsOutboundReset)
                    }
                    if (hasChanges) {
                        Text(
                            text = s.settingsOutboundUnsaved,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                    OutlinedButton(
                        onClick = { onTest(draft) },
                        enabled = isValid && !isTesting,
                        shape = RoundedCornerShape(AppTokens.Radius.small)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (isTesting) s.settingsOutboundTesting else s.settingsOutboundTest)
                    }
                    Button(
                        onClick = { onSave(draft) },
                        enabled = isValid && hasChanges && !isTesting,
                        shape = RoundedCornerShape(AppTokens.Radius.small)
                    ) {
                        Text(s.settingsOutboundSave)
                    }
                }
            }
        }
    }
}
