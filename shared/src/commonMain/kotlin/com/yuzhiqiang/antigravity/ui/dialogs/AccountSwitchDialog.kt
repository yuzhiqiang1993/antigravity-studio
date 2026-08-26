package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.icons.StudioIcons
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

/**
 * 账号切换与宿主重启确认对话框。
 * 遵循 Antigravity Studio 标准弹窗设计规范（标准卡片圆角、Header/Footer 分割线、交互式宿主卡片）。
 */
@Composable
fun AccountSwitchDialog(
    targetAccount: AccountInfo,
    isIdeInstalled: Boolean,
    isAppInstalled: Boolean,
    isIdeRunning: Boolean,
    isAppRunning: Boolean,
    isIdeActive: Boolean,
    isPrivacyMode: Boolean,
    isSwitching: Boolean,
    onConfirm: (applyToIde: Boolean, applyToAppCli: Boolean, restartIde: Boolean, restartApp: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    var applyToIde by remember(targetAccount.id) { mutableStateOf(isIdeInstalled && !isIdeActive) }
    // App & CLI 使用共享文件凭据，默认不隐式选中，避免用户只切 IDE 时误触发宿主重启。
    var applyToAppCli by remember(targetAccount.id) { mutableStateOf(false) }
    val hasRunningTarget = (applyToIde && isIdeRunning) || (applyToAppCli && isAppRunning)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 520.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = StudioIcons.SwitchAccount,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "切换生效账号",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "选择要应用该账号的目标宿主通道",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSwitching,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 2. 中间表单区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 目标账号信息卡片
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.45f
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = displayEmail,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    targetAccount.profile.name?.takeIf { it.isNotBlank() }?.let { name ->
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // 账号等级徽章
                            val tier = targetAccount.profile.tier
                            val tierBadgeBg = when (tier) {
                                AccountTier.ULTRA -> MaterialTheme.colorScheme.tertiaryContainer
                                AccountTier.PRO -> MaterialTheme.colorScheme.primaryContainer
                                AccountTier.ENTERPRISE -> MaterialTheme.colorScheme.secondaryContainer
                                AccountTier.FREE -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val tierBadgeText = when (tier) {
                                AccountTier.ULTRA -> MaterialTheme.colorScheme.onTertiaryContainer
                                AccountTier.PRO -> MaterialTheme.colorScheme.onPrimaryContainer
                                AccountTier.ENTERPRISE -> MaterialTheme.colorScheme.onSecondaryContainer
                                AccountTier.FREE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val tierLabel = when (tier) {
                                AccountTier.ULTRA -> "Ultra"
                                AccountTier.PRO -> "Pro"
                                AccountTier.ENTERPRISE -> "Enterprise"
                                AccountTier.FREE -> "Free"
                            }
                            Surface(
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                                color = tierBadgeBg
                            ) {
                                Text(
                                    text = tierLabel,
                                    fontSize = StudioDesignTokens.TextSize.badge,
                                    fontWeight = FontWeight.Bold,
                                    color = tierBadgeText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 选择目标生效宿主
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "选择目标生效宿主",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // IDE 选项卡片
                        HostOptionCard(
                            title = "Antigravity IDE",
                            statusText = when {
                                !isIdeInstalled -> "未安装 · 不参与本次切换"
                                isIdeRunning -> "运行中 · 选中后将安全退出并重启"
                                else -> "未运行 · 写入凭据后将在下次启动时生效"
                            },
                            statusColor = when {
                                !isIdeInstalled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                isIdeRunning -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            checked = applyToIde,
                            enabled = isIdeInstalled && !isSwitching,
                            onCheckedChange = { applyToIde = it },
                            isDark = isDark
                        )

                        // App & CLI 选项卡片
                        HostOptionCard(
                            title = "Antigravity App & CLI",
                            statusText = when {
                                !isAppInstalled -> "共享凭据仍会同步到 CLI；未安装 App，因此无法启动验证"
                                isAppRunning -> "App 运行中 · 选中后将安全退出并重启"
                                else -> "未运行 · 将先写入 App & CLI 共享凭据，再启动 App 验证"
                            },
                            statusColor = when {
                                !isAppInstalled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                isAppRunning -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            checked = applyToAppCli,
                            enabled = !isSwitching,
                            onCheckedChange = { applyToAppCli = it },
                            isDark = isDark
                        )
                    }


                    if (hasRunningTarget) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "请先保存宿主中的未保存内容。Studio 会请求宿主正常退出，不会强杀宿主进程；如果宿主未能安全退出，本次切换将取消。",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 3. 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSwitching,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(
                            text = s.commonCancel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = (applyToIde || applyToAppCli) && !isSwitching,
                        onClick = {
                            onConfirm(
                                applyToIde,
                                applyToAppCli,
                                applyToIde,
                                applyToAppCli
                            )
                        },
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSwitching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "确定并重启",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 宿主通道选择交互卡片
 */
@Composable
private fun HostOptionCard(
    title: String,
    statusText: String,
    statusColor: Color,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cardBg = when {
        !enabled -> if (isDark) Color(0xFF1E293B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.2f
        )

        checked -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFFEFF6FF)
        else -> if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF8FAFC)
    }
    val borderClr = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        checked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderClr),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                StudioCheckbox(
                    checked = checked,
                    onCheckedChange = { onCheckedChange(it) },
                    enabled = enabled
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = if (enabled) {
                            if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.9f
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
