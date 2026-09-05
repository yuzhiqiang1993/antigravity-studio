package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioButton
import com.yuzhiqiang.antigravity.ui.components.StudioDialogScaffold
import com.yuzhiqiang.antigravity.ui.components.StudioOutlinedButton
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/**
 * 通用确认对话框（接入 StudioDialogScaffold 标准弹窗脚手架）。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    isDestructive: Boolean = false
) {
    val s = strings()
    StudioDialogScaffold(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier.width(420.dp),
        actions = {
            StudioOutlinedButton(
                text = cancelLabel ?: s.commonCancel,
                onClick = onDismiss
            )
            Spacer(Modifier.width(AppTokens.Spacing.sm))
            StudioButton(
                text = confirmLabel ?: s.commonConfirm,
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
