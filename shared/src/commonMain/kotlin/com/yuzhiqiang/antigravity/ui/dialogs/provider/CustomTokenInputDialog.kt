package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomTokenInputDialog(
    title: String,
    initialValue: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    var inputText by remember { mutableStateOf(initialValue?.let(::formatTokenDisplay).orEmpty()) }
    val parsedTokens = remember(inputText) { parseCustomTokenInput(inputText) }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.width(420.dp).wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(s.providerCustomTokenPlaceholder) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = s.providerCustomTokenHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (parsedTokens != null && parsedTokens > 0) {
                            Text(
                                text = s.providerCustomTokenParsed(parsedTokens.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(s.commonCancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(parsedTokens)
                            onDismiss()
                        },
                        enabled = inputText.isBlank() || (parsedTokens != null && parsedTokens > 0),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(s.commonConfirm)
                    }
                }
            }
        }
    }
}
