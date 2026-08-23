package com.yuzhiqiang.antigravity.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun NetworkSettingsSection(
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
