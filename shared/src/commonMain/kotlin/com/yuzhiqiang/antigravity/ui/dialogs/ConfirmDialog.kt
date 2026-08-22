package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/**
 * 通用确认对话框（遵循 Material Design 3 标准弹窗规范）。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    isDestructive: Boolean = false
) {
    val s = strings()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.padding(AppTokens.Spacing.card),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium)
                    ) {
                        Text(
                            text = cancelLabel ?: s.commonCancel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.width(AppTokens.Spacing.sm))
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        colors = if (isDestructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text(
                            text = confirmLabel ?: s.commonConfirm,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
