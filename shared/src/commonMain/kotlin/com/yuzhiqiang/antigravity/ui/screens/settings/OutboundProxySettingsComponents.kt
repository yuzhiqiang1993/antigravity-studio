package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.network.NetworkProxyEndpoint
import com.yuzhiqiang.antigravity.network.OutboundProxyInspection
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
internal fun ProxyModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }

    Surface(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTokens.Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    badge?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(AppTokens.Radius.pill)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ManualProxyForm(
    config: OutboundProxyConfig,
    hostError: Boolean,
    portError: Boolean,
    onProtocolChange: (OutboundProxyProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    s: Strings
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s.settingsOutboundProtocol,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                SingleChoiceSegmentedButtonRow {
                    OutboundProxyProtocol.entries.forEachIndexed { index, protocol ->
                        SegmentedButton(
                            selected = config.protocol == protocol,
                            onClick = { onProtocolChange(protocol) },
                            shape = SegmentedButtonDefaults.itemShape(index, OutboundProxyProtocol.entries.size)
                        ) {
                            Text(if (protocol == OutboundProxyProtocol.HTTP) "HTTP" else "SOCKS5")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
            ) {
                OutlinedTextField(
                    value = config.host,
                    onValueChange = onHostChange,
                    label = { Text(s.settingsOutboundHost) },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    isError = hostError,
                    supportingText = if (hostError) {
                        { Text(s.settingsOutboundManualHostRequired) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.port.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = onPortChange,
                    label = { Text(s.settingsOutboundPort) },
                    placeholder = { Text("7890") },
                    singleLine = true,
                    isError = portError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (portError) {
                        { Text(s.settingsOutboundManualPortInvalid) }
                    } else {
                        null
                    },
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

@Composable
internal fun ProxyDetectionPanel(
    inspection: OutboundProxyInspection,
    s: Strings
) {
    val systemText = inspection.systemProxies.formatEndpoints(s.settingsOutboundNotConfigured)
    val environmentText = inspection.environmentProxy?.format() ?: s.settingsOutboundNotConfigured
    val effectiveParts = inspection.effectiveProxies.map(NetworkProxyEndpoint::format).toMutableList()
    if (inspection.directEnabled) effectiveParts += s.settingsOutboundDirectRoute
    val effectiveText =
        effectiveParts.distinct().ifEmpty { listOf(s.settingsOutboundNotConfigured) }.joinToString(" → ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            Text(
                text = s.settingsOutboundDetection,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            ProxyDetectionRow(s.settingsOutboundSystemDetected, systemText)
            ProxyDetectionRow(s.settingsOutboundEnvironmentDetected, environmentText)
            ProxyDetectionRow(s.settingsOutboundEffectiveRoute, effectiveText, highlight = true)
        }
    }
}

@Composable
private fun ProxyDetectionRow(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun OutboundProxyTestPanel(
    result: ConnectionTester.OutboundProxyTestResult?,
    s: Strings
) {
    if (result == null) return
    val statusColors = AppStatusColors
    val isFallback = result.success && result.fellBackToDirect
    val background = when {
        isFallback -> statusColors.warningContainer.copy(alpha = 0.35f)
        result.success -> statusColors.successContainer.copy(alpha = 0.35f)
        else -> statusColors.errorContainer.copy(alpha = 0.35f)
    }
    val foreground = when {
        isFallback -> statusColors.onWarningContainer
        result.success -> statusColors.onSuccessContainer
        else -> statusColors.onErrorContainer
    }
    val message = when {
        isFallback -> s.settingsOutboundTestFallback(result.latencyMs)
        result.success -> {
            val route = if (result.direct) s.settingsOutboundDirectRoute else result.endpoint?.format().orEmpty()
            s.settingsOutboundTestSuccess(route, result.latencyMs)
        }

        else -> s.settingsOutboundTestFailed(result.error ?: s.commonUnknown)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(AppTokens.Radius.medium))
            .padding(AppTokens.Spacing.md)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = foreground
        )
    }
}

private fun NetworkProxyEndpoint.format(): String {
    val label = if (protocol == OutboundProxyProtocol.HTTP) "HTTP" else "SOCKS5"
    return "$label $address"
}

private fun List<NetworkProxyEndpoint>.formatEndpoints(emptyLabel: String): String {
    return map(NetworkProxyEndpoint::format).distinct().ifEmpty { listOf(emptyLabel) }.joinToString("、")
}
