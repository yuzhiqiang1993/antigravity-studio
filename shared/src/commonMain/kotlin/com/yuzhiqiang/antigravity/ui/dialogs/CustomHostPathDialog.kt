package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/**
 * 自定义宿主安装路径配置对话框。
 * 参考 BYOK 交互模式：
 * 1. 自动发现并罗列候选路径供用户一键选择；
 * 2. 提供系统原生文件/目录选择器（Browse）按钮，避免手动敲击长路径；
 * 3. 支持自由输入与即时存在性校验。
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

    // 智能发现候选路径
    val candidatePaths = remember(hostKey) {
        discoverCandidatePaths(hostKey)
    }

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

    val openFilePicker = {
        openSystemHostPicker(hostKey, s.hostPathDialogTitle(hostTitle)) { selected ->
            pathInput = selected
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(540.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = s.hostPathDialogTitle(hostTitle),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Text(
                    text = s.hostPathDialogDesc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 智能候选列表（如有）
                if (candidatePaths.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = s.hostPathSuggestedTitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        candidatePaths.forEach { candidate ->
                            val isSelected = trimmedPath == candidate.path
                            val exists = candidate.exists
                            Surface(
                                onClick = { pathInput = candidate.path },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (exists) Color(0xFF16A34A).copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (exists) Icons.Outlined.CheckCircle else Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = if (exists) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.path,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (exists) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF16A34A).copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = s.hostPathStatusValid,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = Color(0xFF16A34A),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 手动输入路径 + 一体化内嵌浏览文件按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = s.hostPathInputLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (pathInput.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )

                            androidx.compose.foundation.text.BasicTextField(
                                value = pathInput,
                                onValueChange = { pathInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (pathInput.isEmpty()) {
                                            val isWin = System.getProperty("os.name", "").lowercase().contains("win")
                                            val example = when (hostKey) {
                                                "ide" -> if (isWin) "C:\\Program Files\\Antigravity IDE" else "/Applications/Antigravity IDE.app"
                                                "app" -> if (isWin) "C:\\Program Files\\Antigravity" else "/Applications/Antigravity.app"
                                                else -> if (isWin) "C:\\Users\\user\\.cargo\\bin\\agy.exe" else "/usr/local/bin/agy"
                                            }
                                            Text(
                                                text = s.reasoningExamplePlaceholder(example),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            if (pathInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { pathInput = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = s.commonClear,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 一体化内嵌浏览文件按钮
                            Surface(
                                onClick = openFilePicker,
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = s.hostPathSelectFile,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 路径实时检测状态提示
                when (pathStatus) {
                    PathStatus.VALID -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF16A34A).copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFF16A34A).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
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
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF16A34A)
                            )
                        }
                    }

                    PathStatus.NOT_FOUND -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
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
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    PathStatus.EMPTY -> {
                        Text(
                            text = s.hostPathStatusEmpty,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
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
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
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

private data class CandidatePath(
    val path: String,
    val exists: Boolean
)

private fun discoverCandidatePaths(hostKey: String): List<CandidatePath> {
    val osName = System.getProperty("os.name", "").lowercase()
    val isMac = osName.contains("mac")
    val isWin = osName.contains("win")
    val userHome = System.getProperty("user.home", "")

    val rawCandidates = mutableListOf<String>()

    when (hostKey) {
        "ide" -> {
            if (isMac) {
                rawCandidates.add("/Applications/Antigravity IDE.app")
                rawCandidates.add("$userHome/Applications/Antigravity IDE.app")
                rawCandidates.add("/Applications/Antigravity.app")
            } else if (isWin) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
                val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                rawCandidates.add("$localAppData\\Programs\\Antigravity IDE")
                rawCandidates.add("$programFiles\\Antigravity IDE")
            } else {
                rawCandidates.add("/opt/antigravity-ide")
                rawCandidates.add("/usr/share/antigravity-ide")
            }
        }
        "app" -> {
            if (isMac) {
                rawCandidates.add("/Applications/Antigravity.app")
                rawCandidates.add("$userHome/Applications/Antigravity.app")
                rawCandidates.add("/Applications/Antigravity App.app")
            } else if (isWin) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
                val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                rawCandidates.add("$localAppData\\Programs\\Antigravity")
                rawCandidates.add("$programFiles\\Antigravity")
            } else {
                rawCandidates.add("/opt/antigravity")
                rawCandidates.add("/usr/share/antigravity")
            }
        }
        "cli" -> {
            if (isMac || !isWin) {
                rawCandidates.add("/usr/local/bin/agy")
                rawCandidates.add("/opt/homebrew/bin/agy")
                rawCandidates.add("$userHome/.local/bin/agy")
                rawCandidates.add("$userHome/.cargo/bin/agy")
                rawCandidates.add("$userHome/bin/agy")
                rawCandidates.add("/usr/bin/agy")
            } else {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
                rawCandidates.add("$localAppData\\Programs\\Antigravity\\agy.exe")
                rawCandidates.add("$userHome\\.cargo\\bin\\agy.exe")
            }
        }
    }

    return rawCandidates.distinct().map { path ->
        CandidatePath(
            path = path,
            exists = File(path).exists()
        )
    }.sortedByDescending { it.exists }
}

private fun openSystemHostPicker(
    hostKey: String,
    dialogTitle: String,
    onSelected: (String) -> Unit
) {
    try {
        val osName = System.getProperty("os.name", "").lowercase()
        val isMac = osName.contains("mac")

        if (isMac) {
            System.setProperty("apple.awt.fileDialogForDirectories", if (hostKey == "cli") "false" else "true")
            val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD)
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                onSelected(File(dir, file).absolutePath)
            } else if (dir != null) {
                onSelected(File(dir).absolutePath)
            }
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        } else {
            val chooser = JFileChooser()
            chooser.dialogTitle = dialogTitle
            chooser.fileSelectionMode = if (hostKey == "cli") {
                JFileChooser.FILES_ONLY
            } else {
                JFileChooser.FILES_AND_DIRECTORIES
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                onSelected(chooser.selectedFile.absolutePath)
            }
        }
    } catch (_: Throwable) {
    }
}

private enum class PathStatus {
    EMPTY,
    VALID,
    NOT_FOUND
}
