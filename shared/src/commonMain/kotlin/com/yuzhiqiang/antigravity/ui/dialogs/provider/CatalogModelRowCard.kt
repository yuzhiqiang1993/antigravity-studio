package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.TokenLimitSource
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun CatalogModelRowCard(
    config: CatalogModelConfig,
    isChecked: Boolean,
    isSingleMode: Boolean,
    onToggleCheck: () -> Unit,
    onConfigureReasoning: () -> Unit,
    onTokenLimitChanged: (CatalogModelConfig) -> Unit,
    onToolsChanged: (CatalogModelConfig) -> Unit,
    onVisionChanged: (CatalogModelConfig) -> Unit,
    onTestModel: () -> Unit
) {
    val statusColors = AppStatusColors
    var expandedInputMenu by remember { mutableStateOf(false) }
    var expandedOutputMenu by remember { mutableStateOf(false) }
    var customDialogType by remember { mutableStateOf<String?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (customDialogType != null) {
        val isInput = customDialogType == "input"
        CustomTokenInputDialog(
            title = if (isInput) "自定义输入 Token 上限 · ${config.name}" else "自定义输出 Token 上限 · ${config.name}",
            initialValue = if (isInput) config.inputTokenLimit else config.outputTokenLimit,
            onConfirm = { newLimit ->
                if (isInput) {
                    onTokenLimitChanged(
                        config.copy(
                            inputTokenLimit = newLimit,
                            inputTokenLimitSource = if (newLimit != null) TokenLimitSource.CONFIGURED else TokenLimitSource.UNKNOWN
                        )
                    )
                } else {
                    onTokenLimitChanged(
                        config.copy(
                            outputTokenLimit = newLimit,
                            outputTokenLimitSource = if (newLimit != null) TokenLimitSource.CONFIGURED else TokenLimitSource.UNKNOWN
                        )
                    )
                }
            },
            onDismiss = { customDialogType = null }
        )
    }

    val brandStyle = remember(config.id, config.name) {
        getModelBrandStyle(config.id, config.name)
    }

    val cardBg by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface
    )

    val cardBorderColor by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.outlineVariant
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(enabled = !isSingleMode) { onToggleCheck() },
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isChecked) 1.5.dp else 1.dp,
            color = cardBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isSingleMode) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.2.dp,
                                    color = if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(3.5.dp)
                                )
                                .clickable { onToggleCheck() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(brandStyle.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = brandStyle.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = brandStyle.contentColor
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (config.isUnavailable) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColors.warning.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "目录未探活",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = statusColors.warning
                                    )
                                }
                            }
                        }

                        Text(
                            text = config.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (config.isTesting) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "测试中",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (config.testStatusText != null) {
                        val testSuccess = config.isTestSuccess
                        val pillBg = if (testSuccess) statusColors.successContainer else MaterialTheme.colorScheme.errorContainer
                        val pillText = if (testSuccess) statusColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
                        val dotColor = if (testSuccess) statusColors.success else MaterialTheme.colorScheme.error

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(pillBg)
                                .clickable(enabled = isChecked && !config.isUnavailable) { onTestModel() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    config.testStatusText ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    color = pillText
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTestModel,
                            enabled = isChecked && !config.isUnavailable,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChecked && !config.isUnavailable) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("测试", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        val inputLabel = formatTokenDisplay(config.inputTokenLimit)

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { expandedInputMenu = true }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "输入: $inputLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "▾",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedInputMenu,
                            onDismissRequest = { expandedInputMenu = false }
                        ) {
                            INPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        expandedInputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                inputTokenLimit = valLimit,
                                                inputTokenLimitSource = TokenLimitSource.CONFIGURED
                                            )
                                        )
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("自定义输入...", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                                    }
                                },
                                onClick = {
                                    expandedInputMenu = false
                                    customDialogType = "input"
                                }
                            )
                            if (config.inputTokenLimit != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            Text("清除 (设为未设置)", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
                                        }
                                    },
                                    onClick = {
                                        expandedInputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                inputTokenLimit = null,
                                                inputTokenLimitSource = TokenLimitSource.UNKNOWN
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        val outputLabel = formatTokenDisplay(config.outputTokenLimit)

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { expandedOutputMenu = true }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "输出: $outputLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "▾",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedOutputMenu,
                            onDismissRequest = { expandedOutputMenu = false }
                        ) {
                            OUTPUT_TOKEN_LIMIT_OPTIONS.forEach { (valLimit, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        expandedOutputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                outputTokenLimit = valLimit,
                                                outputTokenLimitSource = TokenLimitSource.CONFIGURED
                                            )
                                        )
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("自定义输入...", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                                    }
                                },
                                onClick = {
                                    expandedOutputMenu = false
                                    customDialogType = "output"
                                }
                            )
                            if (config.outputTokenLimit != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            Text("清除 (设为未设置)", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
                                        }
                                    },
                                    onClick = {
                                        expandedOutputMenu = false
                                        onTokenLimitChanged(
                                            config.copy(
                                                outputTokenLimit = null,
                                                outputTokenLimitSource = TokenLimitSource.UNKNOWN
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val visionActive = config.isVision
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (visionActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (visionActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onVisionChanged(config.copy(isVision = !config.isVision))
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (visionActive) "✓ 多模态" else "多模态",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (visionActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (visionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val toolsActive = config.isTools
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (toolsActive) statusColors.successContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (toolsActive) statusColors.success.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onToolsChanged(config.copy(isTools = !config.isTools))
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (toolsActive) "✓ 工具" else "工具",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (toolsActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (toolsActive) statusColors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val reasoningActive = config.isReasoning
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (reasoningActive) AppTokens.Feature.reasoning.container
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (reasoningActive) AppTokens.Feature.reasoning.border
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(onClick = onConfigureReasoning)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (reasoningActive) "✓ 推理 (已配置)" else "配置推理",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (reasoningActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (reasoningActive) AppTokens.Feature.reasoning.foreground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
