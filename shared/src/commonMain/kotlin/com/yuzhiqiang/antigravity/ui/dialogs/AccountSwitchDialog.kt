package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.ui.icons.StudioIcons
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

/**
 * 账号切换与宿主重启确认对话框。
 * 允许用户精准指定切号目标通道（IDE vs App/CLI），杜绝多宿主账号相互覆盖。
 */
@Composable
fun AccountSwitchDialog(
    targetAccount: AccountInfo,
    isIdeInstalled: Boolean,
    isAppInstalled: Boolean,
    isIdeRunning: Boolean,
    isAppRunning: Boolean,
    isIdeActive: Boolean,
    isAppCliActive: Boolean,
    isPrivacyMode: Boolean,
    isSwitching: Boolean,
    onConfirm: (applyToIde: Boolean, applyToAppCli: Boolean, restartIde: Boolean, restartApp: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var applyToIde by remember(targetAccount.id) { mutableStateOf(isIdeInstalled && !isIdeActive) }
    var applyToAppCli by remember(targetAccount.id) { mutableStateOf(!isAppCliActive) }
    val isMacOs = remember { System.getProperty("os.name", "").lowercase().contains("mac") }
    val hasRunningTarget = (applyToIde && isIdeRunning) || (applyToAppCli && isAppRunning)
    val displayEmail = if (isPrivacyMode) {
        targetAccount.maskedEmail()
    } else {
        targetAccount.email
    }

    Dialog(
        onDismissRequest = {
            if (!isSwitching) {
                onDismiss()
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = AppTokens.Elevation.level3,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.width(480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTokens.Spacing.content),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 标题行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = StudioIcons.SwitchAccount,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "切换生效账号",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "选择要应用该账号的目标宿主通道",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. 目标账号卡片
                Surface(
                    shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )

                        Column {
                            Text(
                                text = displayEmail,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            targetAccount.profile.name?.takeIf { it.isNotBlank() }?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. 宿主同步与重启选项
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "选择目标生效宿主：",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // IDE 选项
                    Surface(
                        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                        color = if (applyToIde) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (applyToIde) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = applyToIde,
                                    onCheckedChange = { checked -> applyToIde = checked },
                                    enabled = isIdeInstalled && !isSwitching
                                )
                                Column {
                                    Text(
                                        text = "Antigravity IDE",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (applyToIde) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (!isIdeInstalled) {
                                            "⚪ 未安装 · 不参与本次切换"
                                        } else if (isIdeRunning) {
                                            "🟢 运行中 · 选中后将安全退出并重启"
                                        } else {
                                            "⚪ 未运行 · 写入凭据后将在下次启动时生效"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // App & CLI 选项
                    Surface(
                        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                        color = if (applyToAppCli) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (applyToAppCli) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = applyToAppCli,
                                    onCheckedChange = { checked -> applyToAppCli = checked },
                                    enabled = !isSwitching
                                )
                                Column {
                                    Text(
                                        text = "Antigravity App & CLI",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (applyToAppCli) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (!isAppInstalled) {
                                            "⚪ App 未安装 · 仍可只切换 CLI 凭据"
                                        } else if (isAppRunning) {
                                            "🟢 App 运行中 · 选中后将安全退出并重启"
                                        } else {
                                            "⚪ 未运行 · 写入 Keychain 与 ~/.gemini 凭据"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isMacOs && isAppInstalled && applyToAppCli) {
                    Text(
                        text = "切换 Antigravity App 时，macOS 可能请求访问登录钥匙串；拒绝授权将取消本次 App 切换。后台账号探测不会访问钥匙串。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasRunningTarget) {
                    Surface(
                        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "请先保存宿主中的未保存内容。Studio 会请求宿主正常退出，不会强杀宿主进程；如果宿主未能安全退出，本次切换将取消。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // 4. 底部确认按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSwitching
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = (applyToIde || applyToAppCli) && !isSwitching,
                        onClick = {
                            onConfirm(
                                applyToIde,
                                applyToAppCli,
                                applyToIde && isIdeRunning,
                                applyToAppCli && isAppRunning
                            )
                        }
                    ) {
                        if (isSwitching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (hasRunningTarget) "确认切换并安全重启" else "确认切换生效账号")
                    }
                }
            }
        }
    }
}
