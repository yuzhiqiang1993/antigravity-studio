package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.statusColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyCapacityBarCard
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyCustomFieldCard
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyMetricCard
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyEditorFormState
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyPercentageGrid
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyPresetTab
import com.yuzhiqiang.antigravity.ui.dialogs.policy.PolicyReserveGrid
import com.yuzhiqiang.antigravity.ui.dialogs.policy.rememberPolicyEditorFormState
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay

/**
 * 结构与视觉参考 BYOK，文案与逻辑全面采用最新三阶段生命周期标准的压缩策略配置弹窗。
 */
@Composable
fun PolicyEditorDialog(
    modelId: String,
    modelDisplayName: String? = null,
    initialPolicy: ModelCompressionPolicy? = null,
    contextWindow: Long? = null,
    onDismiss: () -> Unit,
    onSave: (ModelCompressionPolicy?) -> Unit
) {
    val s = strings()
    val state = rememberPolicyEditorFormState(
        modelId = modelId,
        initialPolicy = initialPolicy
    )
    val cleanDisplayName = remember(modelDisplayName, modelId) {
        PolicyEditorFormState.cleanModelDisplayName(modelDisplayName, modelId)
    }

    // 经典标准色板（适配 M3 与深色模式）
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val byokAccent = MaterialTheme.colorScheme.primary
    val byokBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.8f)
    val byokSurfaceInner =
        if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest
    val byokTextMain = MaterialTheme.colorScheme.onSurface
    val byokTextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val validationError = state.calculateValidationError(s, contextWindow)
    val isValid = state.isDefaultMode || validationError == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .widthIn(min = 680.dp, max = 800.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Header 顶栏 (.policy-modal-header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = s.modelsCompressionPolicy,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = byokTextMain
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppTokens.Radius.xs))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.65f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cleanDisplayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = byokAccent
                                    )
                                )
                            }
                        }

                        if (contextWindow != null) {
                            Text(
                                text = s.policyModelContext(formatTokenDisplay(contextWindow)),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = byokTextSecondary
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = byokTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = byokBorder)

                // 2. Body 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 公式限制说明卡片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.25f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp).padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = s.policyFormulaHint,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = s.policyFormulaHintDesc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // 预设分段选择器
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = byokSurfaceInner,
                            border = BorderStroke(1.dp, byokBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PolicyPresetTab.values().forEach { tab ->
                                    val isSelected = state.selectedTab == tab
                                    val isExceeded =
                                        contextWindow != null && tab.minCapacity != null && tab.minCapacity > contextWindow
                                    val isEnabled = !isExceeded

                                    val bg = if (isSelected) byokAccent else Color.Transparent
                                    val txt = when {
                                        isSelected -> Color.White
                                        !isEnabled -> byokTextSecondary.copy(alpha = 0.4f)
                                        else -> byokTextSecondary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(bg)
                                            .clickable(enabled = isEnabled) {
                                                state.selectPreset(tab)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = tab.label(s),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = txt
                                                ),
                                                maxLines = 1
                                            )
                                            if (tab == PolicyPresetTab.DEFAULT) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                                            else MaterialTheme.statusColors.successContainer
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = s.policyRecommended,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.statusColors.onSuccessContainer
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 新版清晰副标说明
                        Text(
                            text = when {
                                state.isDefaultMode -> s.policyDefaultDesc
                                state.isCustomMode -> s.policyCustomDesc
                                else -> s.policyPresetDesc
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = byokTextSecondary
                            )
                        )
                    }

                    // 负责压缩的执行模型 (仅在非默认模式下展示)
                    if (!state.isDefaultMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = s.policyCompressorModel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = byokTextMain
                                )
                            )

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = byokSurfaceInner,
                                border = BorderStroke(1.dp, byokBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val optSame = state.useSameModel
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (optSame) byokAccent else Color.Transparent)
                                            .clickable { state.useSameModel = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = s.policyFollowCurrent,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (optSame) FontWeight.Bold else FontWeight.Medium,
                                                color = if (optSame) MaterialTheme.colorScheme.onPrimary else byokTextSecondary
                                            )
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!optSame) byokAccent else Color.Transparent)
                                            .clickable { state.useSameModel = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = s.policyOfficialDefault,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (!optSame) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!optSame) MaterialTheme.colorScheme.onPrimary else byokTextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 三列核心参数卡片（全面采用新标准文案，等高严格对齐）
                    if (!state.isCustomMode) {
                        // 预设 / 默认模式：纯净等高 3 列卡片
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PolicyMetricCard(
                                title = s.policyCheckpoint,
                                value = state.displayThreshold,
                                badgeText = formatTokenDisplay(state.displayThreshold),
                                modifier = Modifier.weight(1f)
                            )
                            PolicyMetricCard(
                                title = s.policyContextLimit,
                                value = state.displayLimit,
                                badgeText = formatTokenDisplay(state.displayLimit),
                                modifier = Modifier.weight(1f)
                            )
                            PolicyMetricCard(
                                title = s.policyOutputReserve,
                                value = state.displayReserve,
                                badgeText = formatTokenDisplay(state.displayReserve),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // 自定义模式：等高 3 列精细配置卡片
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 卡片 1：自动存档点
                            val thresholdPct = if (state.displayLimit > 0) {
                                (state.displayThreshold.toDouble() / state.displayLimit * 100).toInt()
                            } else 0

                            PolicyCustomFieldCard(
                                title = s.policyCheckpoint,
                                badgeText = formatTokenDisplay(state.displayThreshold) + " (" + thresholdPct + "%)",
                                isPercentMode = state.thresholdModeByPercent,
                                onModeChange = { state.thresholdModeByPercent = it },
                                modeTabLabels = s.policyByPercentage to s.policyExactTokens,
                                rawInputValue = state.thresholdText,
                                onValueChange = { state.thresholdText = it },
                                percentContent = {
                                    PolicyPercentageGrid(
                                        percentages = listOf(20, 30, 40, 50, 60, 70, 75, 80),
                                        baseCapacity = if (state.displayLimit > 0) state.displayLimit else (contextWindow
                                            ?: 372_000L),
                                        currentValue = state.displayThreshold,
                                        onSelect = { state.thresholdText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 卡片 2：会话上下文容量
                            PolicyCustomFieldCard(
                                title = s.policyContextLimit,
                                badgeText = formatTokenDisplay(state.displayLimit),
                                isPercentMode = state.limitModeByPercent,
                                onModeChange = { state.limitModeByPercent = it },
                                modeTabLabels = s.policyByPercentage to s.policyExactTokens,
                                rawInputValue = state.maxLimitText,
                                onValueChange = { state.maxLimitText = it },
                                percentContent = {
                                    PolicyPercentageGrid(
                                        percentages = listOf(40, 50, 60, 70, 80, 90, 95),
                                        baseCapacity = contextWindow ?: 372_000L,
                                        currentValue = state.displayLimit,
                                        onSelect = { state.maxLimitText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 卡片 3：输出预留
                            PolicyCustomFieldCard(
                                title = s.policyOutputReserve,
                                badgeText = formatTokenDisplay(state.displayReserve),
                                isPercentMode = state.reserveModeByPreset,
                                onModeChange = { state.reserveModeByPreset = it },
                                modeTabLabels = s.policyQuickPreset to s.policyExactTokens,
                                rawInputValue = state.reserveText,
                                onValueChange = { state.reserveText = it },
                                percentContent = {
                                    PolicyReserveGrid(
                                        reserves = listOf(
                                            16_384L to "16K",
                                            32_768L to "32K",
                                            44_640L to "44K",
                                            65_535L to "65K"
                                        ),
                                        currentValue = state.displayReserve,
                                        onSelect = { state.reserveText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. 上下文容量分布卡片
                    PolicyCapacityBarCard(
                        threshold = state.displayThreshold,
                        limit = state.displayLimit,
                        capacity = contextWindow ?: (state.displayLimit * 2)
                    )
                }

                HorizontalDivider(color = byokBorder)

                // 5. Footer 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(byokSurfaceInner)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧报错提示
                    Box(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        if (validationError != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = validationError,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.error
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 右侧操作按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, byokBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = s.commonCancel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = byokTextMain
                                )
                            )
                        }

                        Button(
                            onClick = {
                                onSave(state.buildPolicyToSave(initialPolicy))
                                onDismiss()
                            },
                            enabled = isValid,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = byokAccent),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = s.commonSave,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
