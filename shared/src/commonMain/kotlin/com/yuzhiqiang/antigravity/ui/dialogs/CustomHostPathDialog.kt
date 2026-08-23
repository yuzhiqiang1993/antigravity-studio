package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.io.File

/**
 * 自定义宿主安装路径配置对话框。
 * 支持用户在自动检测失败或使用非标准安装路径时，手动指定宿主应用根目录或可执行文件路径。
 */
@Composable
fun CustomHostPathDialog(
    hostKey: String,
    hostTitle: String,
    initialPath: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    var pathInput by remember(initialPath) { mutableStateOf(initialPath) }
    val trimmedPath = pathInput.trim()

    val pathStatus = remember(trimmedPath) {
        if (trimmedPath.isEmpty()) {
            PathStatus.EMPTY
        } else {
            val file = File(trimmedPath)
            if (file.exists()) {
                PathStatus.VALID
            } else {
                PathStatus.NOT_FOUND
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(480.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.padding(AppTokens.Spacing.card),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = s.hostPathDialogTitle(hostTitle),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = s.hostPathDialogDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text(s.hostPathInputLabel) },
                    placeholder = {
                        val isWin = System.getProperty("os.name", "").lowercase().contains("win")
                        val example = when (hostKey) {
                            "ide" -> if (isWin) "C:\\Users\\user\\AppData\\Local\\Programs\\Antigravity IDE" else "/Applications/Antigravity IDE.app"
                            "app" -> if (isWin) "C:\\Users\\user\\AppData\\Local\\Programs\\Antigravity" else "/Applications/Antigravity.app"
                            else -> if (isWin) "C:\\Users\\user\\AppData\\Local\\Programs\\Antigravity\\agy.exe" else "/usr/local/bin/agy"
                        }
                        Text(s.reasoningExamplePlaceholder(example))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppTokens.Radius.medium)
                )

                // 路径实时检测提示
                when (pathStatus) {
                    PathStatus.VALID -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF16A34A).copy(alpha = 0.1f))
                                .border(1.dp, Color(0xFF16A34A).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = s.hostPathStatusValid,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }

                    PathStatus.NOT_FOUND -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = s.hostPathStatusNotFound,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    PathStatus.EMPTY -> {
                        Text(
                            text = s.hostPathStatusEmpty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialPath.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onSave(null)
                            }
                        ) {
                            Text(
                                text = s.hostPathResetDefault,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = s.commonCancel,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Button(
                            onClick = {
                                onSave(trimmedPath.ifBlank { null })
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = s.commonSave,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class PathStatus {
    EMPTY,
    VALID,
    NOT_FOUND
}
