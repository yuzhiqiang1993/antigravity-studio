package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.network.NetworkProxyEndpoint
import com.yuzhiqiang.antigravity.network.OutboundProxyInspection
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
internal fun OutboundProxyModePillGroup(
    selectedMode: OutboundProxyMode,
    onSelectMode: (OutboundProxyMode) -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        OutboundProxyMode.AUTO to (s.settingsOutboundAuto to s.settingsOutboundRecommended),
        OutboundProxyMode.DIRECT to (s.settingsOutboundDirect to null),
        OutboundProxyMode.SYSTEM to (s.settingsOutboundSystem to null),
        OutboundProxyMode.MANUAL to (s.settingsOutboundManual to null)
    )

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(AppTokens.Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(AppTokens.Radius.pill)
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { (mode, pair) ->
            val (label, badge) = pair
            val selected = selectedMode == mode
            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                    .background(bg)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = text
                )
                if (badge != null && !selected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTokens.Radius.pill))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactManualProxyForm(
    config: OutboundProxyConfig,
    hostError: Boolean,
    portError: Boolean,
    hasChanges: Boolean,
    onProtocolChange: (OutboundProxyProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onApply: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.Radius.medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(AppTokens.Radius.medium)
            )
            .padding(horizontal = AppTokens.Spacing.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
    ) {
        // 协议切换胶囊 (HTTP / SOCKS5)
        Row(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(AppTokens.Radius.pill)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                OutboundProxyProtocol.HTTP to "HTTP",
                OutboundProxyProtocol.SOCKS5 to "SOCKS5"
            ).forEach { (proto, label) ->
                val selected = config.protocol == proto
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(bg)
                        .clickable { onProtocolChange(proto) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }

        // 主机输入框
        val hostBorderColor = if (hostError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(AppTokens.Radius.small))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .border(1.dp, hostBorderColor, RoundedCornerShape(AppTokens.Radius.small))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = config.host,
                onValueChange = onHostChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!hostError && !portError) onApply() }),
                decorationBox = { innerTextField ->
                    if (config.host.isEmpty()) {
                        Text(
                            text = s.settingsOutboundHost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = ":",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 端口输入框
        val portBorderColor = if (portError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(AppTokens.Radius.small))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .border(1.dp, portBorderColor, RoundedCornerShape(AppTokens.Radius.small))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = if (config.port > 0) config.port.toString() else "",
                onValueChange = onPortChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!hostError && !portError) onApply() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (config.port <= 0) {
                        Text(
                            text = s.settingsOutboundPort,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 保存/应用按钮
        Button(
            onClick = onApply,
            enabled = !hostError && !portError && hasChanges,
            shape = RoundedCornerShape(AppTokens.Radius.small),
            modifier = Modifier.height(30.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = s.commonSave,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun OutboundProxyStatusBar(
    config: OutboundProxyConfig,
    inspection: OutboundProxyInspection,
    testResult: ConnectionTester.OutboundProxyTestResult?,
    isTesting: Boolean,
    onTest: () -> Unit,
    s: Strings,
    modifier: Modifier = Modifier
) {
    val statusColors = AppStatusColors
    val isFallback = testResult?.success == true && testResult.fellBackToDirect
    val isSuccess = testResult?.success == true && !isFallback
    val isFailed = testResult?.success == false

    val statusDotColor = when {
        isTesting -> MaterialTheme.colorScheme.primary
        isSuccess -> statusColors.success
        isFallback -> statusColors.warning
        isFailed -> statusColors.error
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    val statusContainerBg = when {
        isSuccess -> statusColors.successContainer.copy(alpha = 0.22f)
        isFallback -> statusColors.warningContainer.copy(alpha = 0.22f)
        isFailed -> statusColors.errorContainer.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    }

    val statusBorderColor = when {
        isSuccess -> statusColors.success.copy(alpha = 0.35f)
        isFallback -> statusColors.warning.copy(alpha = 0.35f)
        isFailed -> statusColors.error.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    val systemText = inspection.systemProxies.formatEndpoints(s.settingsOutboundNotConfigured)

    val defaultModeDescription = when (config.mode) {
        OutboundProxyMode.DIRECT -> s.settingsOutboundDirectActiveDesc
        OutboundProxyMode.AUTO -> {
            if (inspection.systemProxies.isNotEmpty()) {
                s.settingsOutboundAutoActiveWithProxyDesc(systemText)
            } else if (inspection.environmentProxy != null) {
                s.settingsOutboundAutoActiveWithProxyDesc(inspection.environmentProxy.format())
            } else {
                s.settingsOutboundAutoActiveNoProxyDesc
            }
        }
        OutboundProxyMode.SYSTEM -> {
            if (inspection.systemProxies.isNotEmpty()) {
                s.settingsOutboundSystemActiveDesc(systemText, config.fallbackToDirect)
            } else {
                s.settingsOutboundSystemNoProxyDesc(config.fallbackToDirect)
            }
        }
        OutboundProxyMode.MANUAL -> {
            if (config.host.isNotBlank() && config.port in 1..65535) {
                s.settingsOutboundManualActiveDesc("${config.protocol.name} ${config.host}:${config.port}", config.fallbackToDirect)
            } else {
                s.settingsOutboundManualInvalidDesc
            }
        }
    }

    val displayText = when {
        isTesting -> s.settingsOutboundTesting
        isFallback -> s.settingsOutboundTestFallback(testResult.latencyMs)
        isSuccess -> {
            val route = if (testResult.direct) s.settingsOutboundDirectRoute else testResult.endpoint?.format().orEmpty()
            s.settingsOutboundTestSuccess(route, testResult.latencyMs)
        }
        isFailed -> s.settingsOutboundTestFailed(testResult.error ?: s.commonUnknown)
        else -> defaultModeDescription
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.small),
        color = statusContainerBg,
        border = BorderStroke(1.dp, statusBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(statusDotColor)
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = when {
                        isSuccess -> statusColors.onSuccessContainer
                        isFallback -> statusColors.onWarningContainer
                        isFailed -> statusColors.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                    .clickable(enabled = !isTesting, onClick = onTest),
                shape = RoundedCornerShape(AppTokens.Radius.pill),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (isTesting) s.settingsOutboundTesting else s.settingsOutboundTest,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun NetworkProxyEndpoint.format(): String {
    val label = if (protocol == OutboundProxyProtocol.HTTP) "HTTP" else "SOCKS5"
    return "$label $address"
}

private fun List<NetworkProxyEndpoint>.formatEndpoints(emptyLabel: String): String {
    return map(NetworkProxyEndpoint::format).distinct().ifEmpty { listOf(emptyLabel) }.joinToString("、")
}
