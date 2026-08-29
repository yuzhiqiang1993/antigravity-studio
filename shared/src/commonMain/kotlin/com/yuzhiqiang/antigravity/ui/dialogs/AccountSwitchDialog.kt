package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.yuzhiqiang.antigravity.ui.components.StudioTooltip
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.DefaultSwitchTarget
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.icons.StudioIcons
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioBadgeColors
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

/**
 * 账号切换与宿主确认对话框。
 * 针对仅安装 IDE、仅安装 App、仅安装 CLI 或均未安装等各种宿主场景进行智能自适应展示与控制，
 * 并支持开箱默认全勾选、弹窗即时“记住选择”与设置页面持久化配置。
 */
@Composable
fun AccountSwitchDialog(
    targetAccount: AccountInfo,
    config: AppConfig,
    isIdeInstalled: Boolean,
    isAppInstalled: Boolean,
    isCliInstalled: Boolean = false,
    isIdeRunning: Boolean,
    isAppRunning: Boolean,
    isIdeActive: Boolean,
    isPrivacyMode: Boolean,
    isSwitching: Boolean,
    onConfirm: (applyToIde: Boolean, applyToAppCli: Boolean, restartIde: Boolean, restartApp: Boolean, rememberChoice: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val defaultTarget = DefaultSwitchTarget.fromValue(config.defaultSwitchTarget)

    // 智能初始勾选计算：
    // 1. ALL (默认): 全部可用目标应用勾选
    // 2. IDE_ONLY: 若安装了 IDE 则仅勾选 IDE；若未安装 IDE 智能回退至 App/CLI
    // 3. APP_CLI_ONLY: 仅勾选 App & CLI 共享应用
    // 4. REMEMBER_LAST: 沿用上次手动持久化的选择偏好
    val initialApplyToIde = remember(targetAccount.id, defaultTarget, isIdeInstalled, isIdeActive) {
        when (defaultTarget) {
            DefaultSwitchTarget.ALL -> isIdeInstalled
            DefaultSwitchTarget.IDE_ONLY -> isIdeInstalled
            DefaultSwitchTarget.APP_CLI_ONLY -> false
            DefaultSwitchTarget.REMEMBER_LAST -> (config.lastSwitchApplyToIde ?: true) && isIdeInstalled
        }
    }

    val initialApplyToAppCli =
        remember(targetAccount.id, defaultTarget, isIdeInstalled, isAppInstalled, isCliInstalled) {
            when (defaultTarget) {
                DefaultSwitchTarget.ALL -> true
                DefaultSwitchTarget.IDE_ONLY -> !isIdeInstalled
                DefaultSwitchTarget.APP_CLI_ONLY -> true
                DefaultSwitchTarget.REMEMBER_LAST -> config.lastSwitchApplyToAppCli ?: true
            }
        }

    var applyToIde by remember(targetAccount.id, initialApplyToIde) { mutableStateOf(initialApplyToIde) }
    var applyToAppCli by remember(targetAccount.id, initialApplyToAppCli) { mutableStateOf(initialApplyToAppCli) }
    var rememberChoice by remember(targetAccount.id) {
        mutableStateOf(defaultTarget == DefaultSwitchTarget.REMEMBER_LAST)
    }

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
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .widthIn(min = 500.dp, max = 560.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
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
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = s.accountsSwitchDialogTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSwitching,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 2. 中间内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 目标账号信息卡片（展示头像、邮箱与等级徽章）
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                val emailInteractionSource = remember { MutableInteractionSource() }
                                val isEmailHovered by emailInteractionSource.collectIsHoveredAsState()

                                StudioTooltip(text = s.accountsEmailTooltip(targetAccount.email)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.xs))
                                            .background(
                                                if (isEmailHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.10f else 0.06f)
                                                else Color.Transparent
                                            )
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable(
                                                interactionSource = emailInteractionSource,
                                                indication = null
                                            ) {
                                                copyToClipboard(targetAccount.email)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = displayEmail,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.5.sp
                                            ),
                                            color = if (isEmailHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // 账号等级徽章
                            val tier = targetAccount.profile.tier
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val tierBadgeStyle = StudioBadgeColors.tierBadge(tier, isDark)
                            val tierLabel = when (tier) {
                                AccountTier.ULTRA -> "Ultra"
                                AccountTier.PRO -> "Pro"
                                AccountTier.ENTERPRISE -> "Enterprise"
                                AccountTier.FREE -> "Free"
                            }
                            Surface(
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                                color = tierBadgeStyle.bg,
                                border = if (tierBadgeStyle.border != Color.Transparent) BorderStroke(1.dp, tierBadgeStyle.border) else null
                            ) {
                                Text(
                                    text = tierLabel,
                                    fontSize = StudioDesignTokens.TextSize.badge,
                                    fontWeight = FontWeight.Bold,
                                    color = tierBadgeStyle.text,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 选择目标应用
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = s.accountsSwitchSelectTargetTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 1. IDE 选项卡片
                        HostOptionCard(
                            title = s.accountsSwitchTargetIde,
                            statusText = when {
                                !isIdeInstalled -> s.accountsSwitchStatusIdeNotInstalled
                                isIdeRunning -> s.accountsSwitchStatusIdeRunning
                                else -> s.accountsSwitchStatusIdeStopped
                            },
                            statusColor = when {
                                !isIdeInstalled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                isIdeRunning -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            checked = applyToIde && isIdeInstalled,
                            enabled = isIdeInstalled && !isSwitching,
                            onCheckedChange = { applyToIde = it },
                            isDark = isDark
                        )

                        // 2. App & CLI / 共享凭据选项卡片（根据安装组合自适应标题与文案）
                        val sharedCardTitle = when {
                            isAppInstalled && isCliInstalled -> s.accountsSwitchTargetAppCli
                            isAppInstalled -> s.hostAppTitle
                            isCliInstalled -> s.accountsSwitchSharedTitleCli
                            else -> s.accountsSwitchSharedTitleSystem
                        }

                        val sharedCardStatusText = when {
                            isAppRunning -> s.accountsSwitchStatusAppRunning
                            isAppInstalled -> s.accountsSwitchStatusAppStopped
                            isCliInstalled -> s.accountsSwitchStatusCliOnly
                            else -> s.accountsSwitchStatusNone
                        }

                        val sharedCardStatusColor = when {
                            isAppRunning -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                            isAppInstalled || isCliInstalled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }

                        HostOptionCard(
                            title = sharedCardTitle,
                            statusText = sharedCardStatusText,
                            statusColor = sharedCardStatusColor,
                            checked = applyToAppCli,
                            enabled = !isSwitching,
                            onCheckedChange = { applyToAppCli = it },
                            isDark = isDark
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 3. 底部操作栏 (左侧提供“记住选择”，右侧提供取消与确定)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：记住选择目标应用复选框与提示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isSwitching) { rememberChoice = !rememberChoice }
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                    ) {
                        StudioCheckbox(
                            checked = rememberChoice,
                            onCheckedChange = { rememberChoice = it },
                            enabled = !isSwitching
                        )
                        Text(
                            text = s.accountsSwitchRememberChoice,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 右侧：取消与确定按钮 (不被挤压)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSwitching,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = s.commonCancel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val hasLaunchableTarget = (applyToIde && isIdeInstalled) || (applyToAppCli && isAppInstalled)
                        val confirmButtonText = when {
                            isSwitching -> s.accountsSwitching
                            hasRunningTarget -> s.accountsSwitchConfirmRestart
                            hasLaunchableTarget -> s.accountsSwitchConfirmLaunch
                            else -> s.accountsSwitchConfirm
                        }

                        val canConfirm = (applyToIde || applyToAppCli) && !isSwitching

                        Button(
                            enabled = canConfirm,
                            onClick = {
                                onConfirm(
                                    applyToIde,
                                    applyToAppCli,
                                    applyToIde && isIdeInstalled,
                                    applyToAppCli && isAppInstalled,
                                    rememberChoice
                                )
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            if (isSwitching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = confirmButtonText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 宿主应用选择交互卡片
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
        !enabled -> if (isDark) Color(0xFF1E293B).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.2f
        )

        checked -> if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(
            alpha = 0.06f
        )

        else -> if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.35f
        )
    }
    val borderClr = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        checked -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.55f else 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.45f)
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
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                StudioCheckbox(
                    checked = checked && enabled,
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
                            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
                        )
                    }
                }
            }
        }
    }
}
