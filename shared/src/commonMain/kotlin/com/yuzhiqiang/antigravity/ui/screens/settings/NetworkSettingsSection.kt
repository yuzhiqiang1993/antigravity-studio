package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun NetworkSettingsSection(
    portInput: String,
    portError: String?,
    outboundProxy: OutboundProxyConfig,
    isTestingOutboundProxy: Boolean,
    outboundProxyTestResult: ConnectionTester.OutboundProxyTestResult?,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    onSaveOutboundProxy: (OutboundProxyConfig) -> Unit,
    onTestOutboundProxy: (OutboundProxyConfig) -> Unit,
    onClearOutboundProxyTestResult: () -> Unit,
    s: Strings
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
    ) {
        LocalProxySettingsCard(
            portInput = portInput,
            portError = portError,
            onPortInputChange = onPortInputChange,
            onSavePort = onSavePort,
            s = s
        )
        OutboundProxySettingsCard(
            savedConfig = outboundProxy,
            isTesting = isTestingOutboundProxy,
            testResult = outboundProxyTestResult,
            onSave = onSaveOutboundProxy,
            onTest = onTestOutboundProxy,
            onClearTestResult = onClearOutboundProxyTestResult,
            s = s
        )
    }
}

@Composable
private fun LocalProxySettingsCard(
    portInput: String,
    portError: String?,
    onPortInputChange: (String) -> Unit,
    onSavePort: () -> Unit,
    s: Strings
) {
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
        ) {
            SettingsCardTitle(Icons.Outlined.Router, s.settingsLocalProxyTitle)
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            SettingRow(
                icon = Icons.Outlined.Router,
                title = s.settingsPort,
                description = s.settingsPortDescription,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    val isError = portError != null
                    val borderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }

                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(AppTokens.Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(1.dp, borderColor, RoundedCornerShape(AppTokens.Radius.small))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = portInput,
                            onValueChange = onPortInputChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSavePort() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = onSavePort,
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = s.commonSave,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            portError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = AppTokens.Spacing.card)
                )
            }
        }
    }
}
