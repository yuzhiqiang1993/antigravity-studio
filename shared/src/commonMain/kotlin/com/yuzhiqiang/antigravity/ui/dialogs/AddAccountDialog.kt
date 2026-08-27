package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.services.auth.RefreshTokenParser
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

private enum class AddAccountMode {
    BROWSER_OAUTH,
    MANUAL_TOKEN
}

private fun copyToClipboard(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (_: Exception) {}
}

private fun readFromClipboard(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun pickJsonFile(s: com.yuzhiqiang.antigravity.i18n.Strings = com.yuzhiqiang.antigravity.i18n.currentStrings()): String? {
    return try {
        val fileDialog = FileDialog(null as Frame?, s.accountsAddSelectJsonFileTitle, FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        fileDialog.isVisible = true
        val file = fileDialog.file
        val dir = fileDialog.directory
        if (file != null && dir != null) {
            File(dir, file).readText(Charsets.UTF_8)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun AddAccountDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isAuthorizing by viewModel.isOAuthAuthorizing.collectAsState()
    val authUrl by viewModel.oauthAuthUrl.collectAsState()

    var selectedMode by remember { mutableStateOf(AddAccountMode.BROWSER_OAUTH) }
    var inputToken by remember { mutableStateOf("") }
    var isSubmittingManual by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isAuthorizing && !isSubmittingManual) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioCard(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 580.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = s.accountsAddDialogTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = s.accountsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isAuthorizing) {
                                viewModel.cancelOAuthFlow()
                            }
                            onDismiss()
                        },
                        enabled = !isSubmittingManual
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        text = s.accountsAddTabOAuth,
                        selected = selectedMode == AddAccountMode.BROWSER_OAUTH,
                        onClick = { selectedMode = AddAccountMode.BROWSER_OAUTH },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = s.accountsAddTabTokenImport,
                        selected = selectedMode == AddAccountMode.MANUAL_TOKEN,
                        onClick = { selectedMode = AddAccountMode.MANUAL_TOKEN },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tab Content
                when (selectedMode) {
                    AddAccountMode.BROWSER_OAUTH -> {
                        BrowserOAuthContent(
                            isAuthorizing = isAuthorizing,
                            authUrl = authUrl,
                            onStartAuth = { openBrowser ->
                                viewModel.startGoogleOAuthFlow(openBrowserDirectly = openBrowser) { success ->
                                    if (success) onDismiss()
                                }
                            },
                            onSubmitManualCallback = { callbackUrl ->
                                val success = viewModel.submitManualOAuthCallback(callbackUrl)
                                if (!success) {
                                    viewModel.showNotice(s.accountsAddInvalidAuthCode, NoticeKind.ERROR)
                                }
                            },
                            onCancel = {
                                viewModel.cancelOAuthFlow()
                            }
                        )
                    }

                    AddAccountMode.MANUAL_TOKEN -> {
                        ManualTokenContent(
                            token = inputToken,
                            onTokenChange = { inputToken = it },
                            isSubmitting = isSubmittingManual,
                            onSubmit = {
                                isSubmittingManual = true
                                viewModel.importAccountsBatch(inputToken) { successCount, _ ->
                                    isSubmittingManual = false
                                    if (successCount > 0) {
                                        onDismiss()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserOAuthContent(
    isAuthorizing: Boolean,
    authUrl: String?,
    onStartAuth: (openBrowser: Boolean) -> Unit,
    onSubmitManualCallback: (String) -> Unit,
    onCancel: () -> Unit
) {
    val s = strings()
    var manualCallbackInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = s.accountsAddDialogBrowserDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        // 1. 顶部主操作按钮行：[🌐 打开浏览器授权] + [📋 复制授权链接] (1:1 对齐 Cockpit 规范)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onStartAuth(true) },
                modifier = Modifier.weight(1.3f).height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isAuthorizing) s.accountsAddReopenBrowser else s.accountsAddOpenBrowser,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    fontSize = 13.sp
                )
            }

            OutlinedButton(
                onClick = {
                    if (!authUrl.isNullOrBlank()) {
                        copyToClipboard(authUrl)
                    } else {
                        onStartAuth(false)
                    }
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = s.accountsAddCopyAuthUrl,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. 等待授权返回微动画提示
        if (isAuthorizing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = s.accountsWaitingBrowserAuth,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.5.sp
                            )
                        )
                    }

                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = s.accountsAddCancelAuth,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. 手动回调 URL 容错录入区 (1:1 对齐 Cockpit 规范)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = s.accountsAddFallbackManualHint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioTextField(
                    value = manualCallbackInput,
                    onValueChange = { manualCallbackInput = it },
                    placeholder = s.accountsAddFallbackManualPlaceholder,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        if (manualCallbackInput.isNotBlank()) {
                            onSubmitManualCallback(manualCallbackInput)
                        }
                    },
                    enabled = manualCallbackInput.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text(s.accountsAddSubmit, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ManualTokenContent(
    token: String,
    onTokenChange: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    val s = strings()
    val parsedEntries = remember(token) {
        RefreshTokenParser.parse(token)
    }
    val count = parsedEntries.size

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = s.accountsAddTokenBatchDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        // 顶部操作工具条: [📁 导入 JSON 文件] [📋 从剪贴板粘贴] [清空]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val fileContent = pickJsonFile(s)
                    if (!fileContent.isNullOrBlank()) {
                        onTokenChange(fileContent)
                    }
                },
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(s.accountsAddImportJsonFile, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val clipText = readFromClipboard()
                    if (!clipText.isNullOrBlank()) {
                        onTokenChange(clipText)
                    }
                },
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(s.accountsAddPasteClipboard, fontSize = 12.sp)
            }

            Spacer(Modifier.weight(1f))

            if (token.isNotEmpty()) {
                TextButton(
                    onClick = { onTokenChange("") },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = s.commonClear,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 统一规范输入框
        StudioTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
            placeholder = s.accountsAddTokenPlaceholder,
            singleLine = false,
            maxLines = 8,
            minLines = 4
        )

        // 实时识别状态提示 (对齐 Cockpit 规范)
        AnimatedVisibility(
            visible = token.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (count > 0) {
                val emails = parsedEntries.mapNotNull { it.email }.filter { it.isNotBlank() }
                val previewText = when {
                    emails.isNotEmpty() -> " (${emails.take(2).joinToString(", ")}${if (count > 2) " ${s.commonAndMore}" else ""})"
                    else -> ""
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = s.accountsAddRecognizedCount(count, previewText),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = s.accountsAddUnrecognizedTokens,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }

        // 提交按钮
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            enabled = count > 0 && !isSubmitting,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(s.accountsAddImporting, style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = s.accountsAddConfirmImport(count),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp
            ),
            color = contentColor
        )
    }
}
