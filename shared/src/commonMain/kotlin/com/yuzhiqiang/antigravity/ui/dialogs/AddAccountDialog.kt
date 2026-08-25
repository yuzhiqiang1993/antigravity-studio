package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private enum class AddAccountMode {
    BROWSER_OAUTH,
    MANUAL_TOKEN
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
                .widthIn(min = 480.dp, max = 540.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)

        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                        onClick = onDismiss,
                        enabled = !isAuthorizing && !isSubmittingManual
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        text = s.accountsAddViaBrowser,
                        selected = selectedMode == AddAccountMode.BROWSER_OAUTH,
                        onClick = { selectedMode = AddAccountMode.BROWSER_OAUTH },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = s.accountsAddViaToken,
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
                            onStartAuth = {
                                viewModel.startGoogleOAuthFlow { success ->
                                    if (success) onDismiss()
                                }
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
    onStartAuth: () -> Unit
) {
    val s = strings()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = s.accountsAddDialogBrowserDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        if (isAuthorizing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = s.accountsWaitingBrowserAuth,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!authUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                    StringSelection(authUrl),
                                    null
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("复制授权链接", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = onStartAuth,
                modifier = Modifier.fillMaxWidth().height(44.dp),
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
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "在浏览器中打开 Google 授权",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
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
    val parsedEntries = remember(token) {
        com.yuzhiqiang.antigravity.services.auth.RefreshTokenParser.parse(token)
    }
    val count = parsedEntries.size

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "支持粘贴单个/多行 Refresh Token（每行一个）进行批量导入，或直接粘贴备份的账号 JSON 数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        StudioTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp),
            placeholder = "粘贴 1//0g... 字符串（支持多行批量粘贴或 JSON 数组/对象）",
            singleLine = false,
            maxLines = 8,
            minLines = 4
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(44.dp),
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
                Text("正在验证并导入账号...", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                val buttonText = when {
                    count > 1 -> "批量导入账号 ($count 个)"
                    count == 1 -> "确认导入账号 (1 个)"
                    else -> "确认导入账号"
                }
                Text(buttonText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
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
