package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/** 配置单个上游模型 reasoning 档位、custom mapping 与 Gemini 思考预算（Material Design 3 规范）。 */
@Composable
fun ReasoningConfigDialog(
    modelName: String,
    protocol: ProviderProtocol,
    outputTokenLimit: Long?,
    initialDraft: ReasoningConfigDraft,
    onDismiss: () -> Unit,
    onConfirm: (ReasoningConfigDraft) -> Unit
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    var enabled by remember { mutableStateOf(initialDraft.enabled) }
    var selectedLevels by remember { mutableStateOf(initialDraft.levels) }
    var customValue by remember { mutableStateOf(initialDraft.customValue.orEmpty()) }
    var thinkingBudget by remember { mutableStateOf(initialDraft.thinkingBudget?.toString().orEmpty()) }
    var minThinkingBudget by remember { mutableStateOf(initialDraft.minThinkingBudget?.toString().orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val selectableLevels = remember(protocol, outputTokenLimit, initialDraft) {
        val candidates = if (
            initialDraft.levels.isEmpty() &&
            initialDraft.mappings.isEmpty() &&
            (initialDraft.thinkingBudget != null || initialDraft.minThinkingBudget != null)
        ) {
            emptyList()
        } else {
            ReasoningMappingSupport.defaultLevels(protocol)
        }
        (candidates + initialDraft.levels).distinct().filter { level ->
            val configured = initialDraft.mappings[level]
            configured?.let { mapping ->
                ReasoningMappingSupport.isSupported(protocol, mapping, outputTokenLimit)
            } ?: ReasoningMappingSupport.defaultMapping(protocol, level)?.let { mapping ->
                ReasoningMappingSupport.isSupported(protocol, mapping, outputTokenLimit)
            } == true
        }
    }

    fun parseBudget(value: String, label: String): Int? {
        if (value.isBlank()) return null
        val parsed = value.toIntOrNull()
        if (parsed == null) {
            validationError = s.reasoningMustBeInteger(label)
            return null
        }
        return parsed
    }

    fun confirm() {
        validationError = null
        if (!enabled) {
            onConfirm(
                ReasoningConfigDraft(
                    enabled = false,
                    levels = emptySet(),
                    customValue = null,
                    thinkingBudget = null,
                    minThinkingBudget = null,
                    mappings = emptyMap(),
                    configuredSupported = false
                )
            )
            return
        }
        val budget = parseBudget(thinkingBudget, s.reasoningBudget)
        if (validationError != null) return
        val minBudget = parseBudget(minThinkingBudget, s.reasoningMinBudget)
        if (validationError != null) return
        if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
            if (budget != null && budget < -1) {
                validationError = s.reasoningGeminiBudgetValidation
                return
            }
            if (minBudget != null && minBudget <= 0) {
                validationError = s.reasoningMinBudgetMustPositive
                return
            }
            if (budget != null && budget > 0 && minBudget != null && minBudget > budget) {
                validationError = s.reasoningMinBudgetExceedsBudget
                return
            }
        }
        if (protocol != ProviderProtocol.GEMINI_GENERATE_CONTENT && (budget != null || minBudget != null)) {
            validationError = s.reasoningOnlyGeminiSupportsBudget
            return
        }
        val custom = customValue.trim().takeIf { it.isNotEmpty() }
        if (custom != null && ReasoningMappingSupport.customMapping(protocol, custom, outputTokenLimit) == null) {
            validationError = s.reasoningCustomValueInvalid
            return
        }
        val invalidLevel = selectedLevels.firstOrNull { level ->
            val mapping = ReasoningMappingSupport.resolveMapping(
                protocol = protocol,
                level = level,
                configured = initialDraft.mappings,
                outputTokenLimit = outputTokenLimit
            )
            mapping == null
        }
        if (invalidLevel != null) {
            validationError = s.reasoningLevelInvalid(invalidLevel.label)
            return
        }
        if (selectedLevels.isEmpty() && custom == null && budget == null && minBudget == null) {
            validationError = s.reasoningSelectAtLeastOne
            return
        }
        onConfirm(
            initialDraft.copy(
                enabled = true,
                levels = selectedLevels,
                customValue = custom,
                thinkingBudget = budget,
                minThinkingBudget = minBudget
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = s.reasoningDialogTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
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

                // 中间表单区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 总开关卡片
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { enabled = !enabled },
                        shape = RoundedCornerShape(10.dp),
                        color = if (enabled)
                            (if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFFEFF6FF))
                        else (if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF8FAFC)),
                        border = BorderStroke(
                            1.dp,
                            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = s.reasoningEnableTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = s.reasoningEnableSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StudioSwitch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    validationError = null
                                }
                            )
                        }
                    }

                    if (enabled) {
                        // 可用推理档位选择组（2 列精致网格）
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = s.reasoningAvailableLevels,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val chunkedLevels = remember(selectableLevels) { selectableLevels.chunked(2) }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunkedLevels.forEach { rowLevels ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowLevels.forEach { level ->
                                            val isSelected = level in selectedLevels
                                            ReasoningLevelOptionRow(
                                                label = level.label,
                                                isSelected = isSelected,
                                                isDark = isDark,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    selectedLevels = if (isSelected) {
                                                        selectedLevels - level
                                                    } else {
                                                        selectedLevels + level
                                                    }
                                                    validationError = null
                                                }
                                            )
                                        }
                                        if (rowLevels.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Custom reasoning 覆盖值
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = s.reasoningCustomValue,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = s.reasoningOptional,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StudioDialogInputField(
                                value = customValue,
                                onValueChange = {
                                    customValue = it
                                    validationError = null
                                },
                                placeholder = when (protocol) {
                                    ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                                    ProviderProtocol.OPENAI_RESPONSES -> s.reasoningExamplePlaceholder("high / xhigh")
                                    ProviderProtocol.ANTHROPIC_MESSAGES -> s.reasoningExamplePlaceholder("adaptive / 16384")
                                    ProviderProtocol.GEMINI_GENERATE_CONTENT -> s.reasoningExamplePlaceholder("high / 8192")
                                }
                            )
                            Text(
                                text = s.reasoningCustomValueDesc,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }

                        // Gemini 思考预算设置
                        if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = s.reasoningDefaultBudget,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    StudioDialogInputField(
                                        value = thinkingBudget,
                                        onValueChange = {
                                            thinkingBudget = it
                                            validationError = null
                                        },
                                        placeholder = s.reasoningDynamicBudgetPlaceholder
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = s.reasoningMinBudgetTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    StudioDialogInputField(
                                        value = minThinkingBudget,
                                        onValueChange = {
                                            minThinkingBudget = it
                                            validationError = null
                                        },
                                        placeholder = s.reasoningExamplePlaceholder("1024")
                                    )
                                }
                            }
                        }

                        // 校验错误提示
                        if (validationError != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        validationError!!,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(s.commonCancel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = ::confirm,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(s.commonConfirm, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

/** 档位选项条目组件 */
@Composable
private fun ReasoningLevelOptionRow(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val bg = when {
        isSelected -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFFDBEAFE)
        isHovered -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        else -> if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    }
    val borderCol = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isHovered -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    }
    val textCol = when {
        isSelected -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(if (isSelected) 1.2.dp else 1.dp, borderCol, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/** 桌面端轻量微型开关组件 */
@Composable
private fun StudioSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(AppTokens.Motion.durationShort)
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)),
        animationSpec = tween(AppTokens.Motion.durationShort)
    )

    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset - 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 弹窗内紧凑输入框组件 (36.dp) */
@Composable
private fun StudioDialogInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val borderCol = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isHovered -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    }
    val bgCol = when {
        isFocused -> MaterialTheme.colorScheme.surface
        isHovered -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        else -> if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .hoverable(interaction)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bgCol)
            .border(if (isFocused) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(8.dp)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
