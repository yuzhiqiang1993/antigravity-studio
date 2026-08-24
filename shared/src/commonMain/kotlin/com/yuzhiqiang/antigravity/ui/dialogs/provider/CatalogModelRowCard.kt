package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val statusColors = AppStatusColors
    var expandedInputMenu by remember { mutableStateOf(false) }
    var expandedOutputMenu by remember { mutableStateOf(false) }
    var customDialogType by remember { mutableStateOf<String?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (customDialogType != null) {
        val isInput = customDialogType == "input"
        CustomTokenInputDialog(
            title = if (isInput) s.providerCustomInputTokenTitle(config.name) else s.providerCustomOutputTokenTitle(config.name),
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

    val cardBg by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
        else if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface
    )

    val cardBorderColor by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else if (isHovered) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .then(
                if (isSingleMode) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = isChecked,
                        role = Role.Checkbox,
                        onValueChange = { onToggleCheck() }
                    )
                }
            ),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            cardBorderColor
        ),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
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
                        val selectionShape = RoundedCornerShape(7.dp)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(selectionShape)
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isChecked) 1.dp else 1.5.dp,
                                    color = if (isChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                                    shape = selectionShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    config.vendor?.takeIf { it.isNotBlank() }?.let { vendor ->
                        val vendorShape = RoundedCornerShape(7.dp)
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(vendorShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    vendorShape
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = vendor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = config.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (config.isUnavailable) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColors.warning.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = s.providerUnprobedCatalog,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                .height(26.dp)
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
                                    s.providerTesting,
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
                                .height(26.dp)
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
                            modifier = Modifier.height(26.dp),
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
                            Text(s.providerTestBtn, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp))
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .clickable { expandedInputMenu = true }
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    s.providerInputTokenPrefix(inputLabel),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
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
                                        Text(s.providerCustomTokenOption, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
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
                                            Text(s.providerClearTokenOption, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .clickable { expandedOutputMenu = true }
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    s.providerOutputTokenPrefix(outputLabel),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
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
                                        Text(s.providerCustomTokenOption, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
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
                                            Text(s.providerClearTokenOption, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (visionActive) AppTokens.Feature.vision.container
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (visionActive) AppTokens.Feature.vision.border
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                onVisionChanged(config.copy(isVision = !config.isVision))
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.modelsVision,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (visionActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (visionActive) AppTokens.Feature.vision.foreground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }

                    val toolsActive = config.isTools
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (toolsActive) AppTokens.Feature.tools.container
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (toolsActive) AppTokens.Feature.tools.border
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                onToolsChanged(config.copy(isTools = !config.isTools))
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.modelsTools,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (toolsActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (toolsActive) AppTokens.Feature.tools.foreground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }

                    val reasoningActive = config.isReasoning
                    val reasoningText = remember(config.isReasoning, config.reasoningDraft) {
                        if (!reasoningActive) {
                            s.modelsReasoningConfig
                        } else {
                            val draft = config.reasoningDraft
                            val levels = draft?.levels.orEmpty()
                            val orderedLevels = listOf(
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.LOW,
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.MEDIUM,
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.HIGH,
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.X_HIGH,
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.MAX,
                                com.yuzhiqiang.antigravity.domain.model.ReasoningLevel.ADAPTIVE
                            ).filter { it in levels }
                            val levelsStr = when {
                                orderedLevels.isNotEmpty() -> orderedLevels.joinToString("·") { it.label }
                                !draft?.customValue.isNullOrBlank() -> draft?.customValue
                                else -> null
                            }
                            if (levelsStr != null) {
                                s.modelsReasoning + " · " + levelsStr
                            } else {
                                s.modelsReasoning
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (reasoningActive) AppTokens.Feature.reasoning.container
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (reasoningActive) AppTokens.Feature.reasoning.border
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(onClick = onConfigureReasoning)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = reasoningText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (reasoningActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (reasoningActive) AppTokens.Feature.reasoning.foreground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}
