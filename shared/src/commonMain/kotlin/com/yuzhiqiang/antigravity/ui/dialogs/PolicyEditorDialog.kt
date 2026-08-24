package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

private enum class ByokPresetTab(val minCapacity: Long?) {
    DEFAULT(null),
    CONTEXT_256K(256_000L),
    CONTEXT_372K(372_000L),
    CONTEXT_500K(500_000L),
    CONTEXT_1M(1_000_000L),
    CUSTOM(null);

    fun label(s: Strings): String = when (this) {
        DEFAULT -> s.policyPresetDefault
        CONTEXT_256K -> "256K"
        CONTEXT_372K -> "372K"
        CONTEXT_500K -> "500K"
        CONTEXT_1M -> "1M"
        CUSTOM -> s.policyPresetCustom
    }
}

/**
 * 结构与视觉完美参考 BYOK，文案与逻辑全面采用最新三阶段生命周期标准的压缩策略配置弹窗。
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

    // 提取纯净的人类可读模型名称（剥离内部 UUID/provider 前缀）
    val cleanDisplayName = remember(modelDisplayName, modelId) {
        val raw = modelDisplayName?.takeIf { it.isNotBlank() } ?: modelId
        val cleaned = raw
            .replace(Regex("^p_[0-9a-zA-Z]+-"), "")
            .replace(Regex("^upstream-[0-9a-fA-F-]+-?"), "")
        if (cleaned.isNotBlank()) cleaned else raw
    }

    // 经典标准色板
    val byokAccent = Color(0xFF2563EB)
    val byokBorder = Color(0xFFE2E8F0)
    val byokSurfaceInner = Color(0xFFF8FAFC)
    val byokTextMain = Color(0xFF0F172A)
    val byokTextSecondary = Color(0xFF64748B)

    // 官方默认原生数值 (Gemini 默认为 140K 预备 / 256K 上限；Claude 默认为 50K 预备 / 160K 上限)
    val defaultNativeThreshold = when {
        modelId.contains("gemini", ignoreCase = true) -> 140_000L
        modelId.contains("claude", ignoreCase = true) || modelId.contains("sonnet", ignoreCase = true) -> 50_000L
        else -> 140_000L
    }
    val defaultNativeLimit = when {
        modelId.contains("gemini", ignoreCase = true) -> 256_000L
        modelId.contains("claude", ignoreCase = true) || modelId.contains("sonnet", ignoreCase = true) -> 160_000L
        else -> 256_000L
    }
    val defaultNativeReserve = 16_384L

    var selectedTab by remember {
        mutableStateOf(
            when {
                initialPolicy == null -> ByokPresetTab.DEFAULT
                initialPolicy.tokenThreshold == 102_400L && initialPolicy.maxTokenLimit == 153_600L -> ByokPresetTab.CONTEXT_256K
                initialPolicy.tokenThreshold == 148_800L && initialPolicy.maxTokenLimit == 223_200L -> ByokPresetTab.CONTEXT_372K
                initialPolicy.tokenThreshold == 200_000L && initialPolicy.maxTokenLimit == 300_000L -> ByokPresetTab.CONTEXT_500K
                initialPolicy.tokenThreshold == 419_430L && initialPolicy.maxTokenLimit == 629_145L -> ByokPresetTab.CONTEXT_1M
                else -> ByokPresetTab.CUSTOM
            }
        )
    }

    var maxLimitText by remember {
        mutableStateOf((initialPolicy?.maxTokenLimit ?: 153_600L).toString())
    }
    var thresholdText by remember {
        mutableStateOf((initialPolicy?.tokenThreshold ?: 102_400L).toString())
    }
    var reserveText by remember {
        mutableStateOf((initialPolicy?.maxOutputTokens ?: 30_720L).toString())
    }
    var useSameModel by remember {
        mutableStateOf(initialPolicy?.useLastPlannerModel == true && initialPolicy.strategy == "CHECKPOINT_STRATEGY_SAME_MODEL")
    }

    // 自定义模式下每个卡片的切换模式 (true = 按百分比/快捷预设, false = 精准 Token)
    var thresholdModeByPercent by remember { mutableStateOf(false) }
    var limitModeByPercent by remember { mutableStateOf(false) }
    var reserveModeByPreset by remember { mutableStateOf(false) }

    val isDefaultMode = selectedTab == ByokPresetTab.DEFAULT
    val isCustomMode = selectedTab == ByokPresetTab.CUSTOM

    val displayThreshold = if (isDefaultMode) defaultNativeThreshold else (thresholdText.toLongOrNull() ?: 0L)
    val displayLimit = if (isDefaultMode) defaultNativeLimit else (maxLimitText.toLongOrNull() ?: 0L)
    val displayReserve = if (isDefaultMode) defaultNativeReserve else (reserveText.toLongOrNull() ?: 0L)

    val safeLimit = if (contextWindow != null) (contextWindow - displayReserve).coerceAtLeast(1L) else null

    // 校验规则 (生命周期三阶段：0 < Threshold < Limit - Reserve 且 Limit <= Context - Reserve)
    val validationError = when {
        displayLimit <= 0L -> s.policyLimitMustPositive
        displayThreshold <= 0L -> s.policyThresholdMustPositive
        displayReserve <= 0L -> s.policyReserveMustPositive
        safeLimit != null && displayLimit > safeLimit -> s.policyLimitExceedsSafeLimit(
            formatTokenDisplay(displayLimit),
            formatTokenDisplay(safeLimit),
            formatTokenDisplay(contextWindow ?: 0L),
            formatTokenDisplay(displayReserve)
        )
        contextWindow != null && displayLimit > contextWindow -> s.policyLimitExceedsContext(formatTokenDisplay(displayLimit), formatTokenDisplay(contextWindow))
        displayThreshold >= displayLimit -> s.policyThresholdExceedsLimit(formatTokenDisplay(displayThreshold), formatTokenDisplay(displayLimit))
        displayThreshold + displayReserve > displayLimit -> s.policySumExceedsLimit(formatTokenDisplay(displayThreshold + displayReserve), formatTokenDisplay(displayLimit))
        else -> null
    }
    val isValid = isDefaultMode || validationError == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 680.dp, max = 800.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, byokBorder),
            shadowElevation = 16.dp
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
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = s.policyFormulaHint,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                )
                                Text(
                                    text = s.policyFormulaHintDesc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFF3B82F6)
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
                                ByokPresetTab.values().forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    val isExceeded = contextWindow != null && tab.minCapacity != null && tab.minCapacity > contextWindow
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
                                                selectedTab = tab
                                                when (tab) {
                                                    ByokPresetTab.DEFAULT -> {}
                                                    ByokPresetTab.CONTEXT_256K -> {
                                                        thresholdText = "102400"
                                                        maxLimitText = "153600"
                                                        reserveText = "30720"
                                                    }
                                                    ByokPresetTab.CONTEXT_372K -> {
                                                        thresholdText = "148800"
                                                        maxLimitText = "223200"
                                                        reserveText = "44640"
                                                    }
                                                    ByokPresetTab.CONTEXT_500K -> {
                                                        thresholdText = "200000"
                                                        maxLimitText = "300000"
                                                        reserveText = "60000"
                                                    }
                                                    ByokPresetTab.CONTEXT_1M -> {
                                                        thresholdText = "419430"
                                                        maxLimitText = "629145"
                                                        reserveText = "65535"
                                                    }
                                                    ByokPresetTab.CUSTOM -> {}
                                                }
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
                                            if (tab == ByokPresetTab.DEFAULT) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isSelected) Color.White.copy(alpha = 0.25f)
                                                            else Color(0xFF10B981).copy(alpha = 0.14f)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = s.policyRecommended,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.White else Color(0xFF047857)
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
                                isDefaultMode -> s.policyDefaultDesc
                                isCustomMode -> s.policyCustomDesc
                                else -> s.policyPresetDesc
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = byokTextSecondary
                            )
                        )
                    }

                    // 负责压缩的执行模型 (仅在非默认模式下展示)
                    if (!isDefaultMode) {
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
                                    val optSame = useSameModel
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (optSame) byokAccent else Color.Transparent)
                                            .clickable { useSameModel = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = s.policyFollowCurrent,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (optSame) FontWeight.Bold else FontWeight.Medium,
                                                color = if (optSame) Color.White else byokTextSecondary
                                            )
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!optSame) byokAccent else Color.Transparent)
                                            .clickable { useSameModel = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = s.policyOfficialDefault,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (!optSame) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!optSame) Color.White else byokTextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 三列核心参数卡片（全面采用新标准文案，等高严格对齐）
                    if (!isCustomMode) {
                        // 预设 / 默认模式：纯净等高 3 列卡片
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ByokMetricCard(
                                title = s.policyCheckpoint,
                                value = displayThreshold,
                                badgeText = formatTokenDisplay(displayThreshold),
                                modifier = Modifier.weight(1f)
                            )
                            ByokMetricCard(
                                title = s.policyContextLimit,
                                value = displayLimit,
                                badgeText = formatTokenDisplay(displayLimit),
                                modifier = Modifier.weight(1f)
                            )
                            ByokMetricCard(
                                title = s.policyOutputReserve,
                                value = displayReserve,
                                badgeText = formatTokenDisplay(displayReserve),
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
                            val thresholdPct = if (displayLimit > 0) (displayThreshold.toDouble() / displayLimit * 100).toInt() else 0
                            ByokCustomFieldCard(
                                title = s.policyCheckpoint,
                                badgeText = "${formatTokenDisplay(displayThreshold)} ($thresholdPct%)",
                                isPercentMode = thresholdModeByPercent,
                                onModeChange = { thresholdModeByPercent = it },
                                modeTabLabels = s.policyByPercentage to s.policyExactTokens,
                                rawInputValue = thresholdText,
                                onValueChange = { thresholdText = it },
                                percentContent = {
                                    ByokPercentageGrid(
                                        percentages = listOf(20, 30, 40, 50, 60, 70, 75, 80),
                                        baseCapacity = if (displayLimit > 0) displayLimit else (contextWindow ?: 372_000L),
                                        currentValue = displayThreshold,
                                        onSelect = { thresholdText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 卡片 2：会话上下文容量
                            ByokCustomFieldCard(
                                title = s.policyContextLimit,
                                badgeText = formatTokenDisplay(displayLimit),
                                isPercentMode = limitModeByPercent,
                                onModeChange = { limitModeByPercent = it },
                                modeTabLabels = s.policyByPercentage to s.policyExactTokens,
                                rawInputValue = maxLimitText,
                                onValueChange = { maxLimitText = it },
                                percentContent = {
                                    ByokPercentageGrid(
                                        percentages = listOf(40, 50, 60, 70, 80, 90, 95),
                                        baseCapacity = contextWindow ?: 372_000L,
                                        currentValue = displayLimit,
                                        onSelect = { maxLimitText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 卡片 3：输出预留
                            ByokCustomFieldCard(
                                title = s.policyOutputReserve,
                                badgeText = formatTokenDisplay(displayReserve),
                                isPercentMode = reserveModeByPreset,
                                onModeChange = { reserveModeByPreset = it },
                                modeTabLabels = s.policyQuickPreset to s.policyExactTokens,
                                rawInputValue = reserveText,
                                onValueChange = { reserveText = it },
                                percentContent = {
                                    ByokReserveGrid(
                                        reserves = listOf(16_384L to "16K", 32_768L to "32K", 44_640L to "44K", 65_535L to "65K"),
                                        currentValue = displayReserve,
                                        onSelect = { reserveText = it.toString() }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. 上下文容量分布卡片 (新版标准图例文案)
                    ByokCapacityBarCard(
                        threshold = displayThreshold,
                        limit = displayLimit,
                        capacity = contextWindow ?: (displayLimit * 2)
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
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = validationError,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = Color(0xFFDC2626)
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
                                if (isDefaultMode) {
                                    onSave(null)
                                } else {
                                    val basePolicy = initialPolicy ?: ModelCompressionPolicy()
                                    val policy = basePolicy.copy(
                                        tokenThreshold = displayThreshold,
                                        maxTokenLimit = displayLimit,
                                        maxOutputTokens = displayReserve,
                                        useLastPlannerModel = useSameModel,
                                        strategy = if (useSameModel) "CHECKPOINT_STRATEGY_SAME_MODEL" else "CHECKPOINT_STRATEGY_UNSPECIFIED"
                                    )
                                    onSave(policy)
                                }
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

/**
 * 预设/默认模式下的指标卡片 (.policy-metric)
 */
@Composable
private fun ByokMetricCard(
    title: String,
    value: Long,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCommaNumber(value),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 自定义模式下的参数配置卡片 (.policy-custom-field)
 */
@Composable
private fun ByokCustomFieldCard(
    title: String,
    badgeText: String,
    isPercentMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modeTabLabels: Pair<String, String>,
    rawInputValue: String,
    onValueChange: (String) -> Unit,
    percentContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header (.policy-field-header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    )
                }
            }

            // 2. 输入模式切换分段器 (.policy-input-mode-segmented)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(23.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isPercentMode) Color(0xFFEFF6FF) else Color.Transparent)
                            .clickable { onModeChange(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeTabLabels.first,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isPercentMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPercentMode) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(23.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isPercentMode) Color(0xFFEFF6FF) else Color.Transparent)
                            .clickable { onModeChange(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeTabLabels.second,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (!isPercentMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isPercentMode) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        )
                    }
                }
            }

            // 3. 内容区：等高容器 (height = 60.dp) 保证三张卡片底部绝对平齐！
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPercentMode) {
                    percentContent()
                } else {
                    ByokInputWrapper(
                        value = rawInputValue,
                        onValueChange = onValueChange
                    )
                }
            }
        }
    }
}

/**
 * 百分比选择网格 (4 列排布，共 7~8 个药丸)
 */
@Composable
private fun ByokPercentageGrid(
    percentages: List<Int>,
    baseCapacity: Long,
    currentValue: Long,
    onSelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 第一行 4 个
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            percentages.take(4).forEach { pct ->
                val calcVal = (baseCapacity * (pct / 100.0)).toLong()
                val isMatch = Math.abs(currentValue - calcVal) <= (baseCapacity * 0.025)
                ByokPercentagePill(
                    text = "$pct%",
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(calcVal) }
                )
            }
        }

        // 第二行剩余项
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            percentages.drop(4).forEach { pct ->
                val calcVal = (baseCapacity * (pct / 100.0)).toLong()
                val isMatch = Math.abs(currentValue - calcVal) <= (baseCapacity * 0.025)
                ByokPercentagePill(
                    text = "$pct%",
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(calcVal) }
                )
            }
            // 占位补齐
            if (percentages.drop(4).size < 4) {
                Spacer(modifier = Modifier.weight((4 - percentages.drop(4).size).toFloat()))
            }
        }
    }
}

/**
 * 预设备选网格 (2 列排布，共 4 个药丸)
 */
@Composable
private fun ByokReserveGrid(
    reserves: List<Pair<Long, String>>,
    currentValue: Long,
    onSelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reserves.take(2).forEach { (valTokens, label) ->
                val isMatch = currentValue == valTokens
                ByokPercentagePill(
                    text = label,
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(valTokens) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reserves.drop(2).forEach { (valTokens, label) ->
                val isMatch = currentValue == valTokens
                ByokPercentagePill(
                    text = label,
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(valTokens) }
                )
            }
        }
    }
}

/**
 * 精致百分比药丸按钮 (.policy-percentage-btn)
 */
@Composable
private fun ByokPercentagePill(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFEFF6FF) else Color.White
    val borderCol = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
    val textCol = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .height(27.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textCol
            ),
            maxLines = 1
        )
    }
}

/**
 * 单行输入框封装 (.policy-input-wrapper)
 */
@Composable
private fun ByokInputWrapper(
    value: String,
    onValueChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        ),
        cursorBrush = SolidColor(Color(0xFF2563EB)),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        if (isFocused) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    innerTextField()
                }
                Text(
                    text = "Tokens",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                )
            }
        }
    )
}

/**
 * 上下文容量分布卡片 (.policy-capacity-bar-wrapper)
 */
@Composable
private fun ByokCapacityBarCard(
    threshold: Long,
    limit: Long,
    capacity: Long
) {
    if (limit <= 0L || threshold <= 0L || capacity <= 0L) return

    val totalScale = if (capacity > limit) capacity else limit
    val thresholdPct = (threshold.toDouble() / totalScale).coerceIn(0.0, 1.0).toFloat()
    val limitPct = (limit.toDouble() / totalScale).coerceIn(0.0, 1.0).toFloat()
    val compressPct = (limitPct - thresholdPct).coerceAtLeast(0f)
    val reservePct = (1.0f - limitPct).coerceAtLeast(0f)

    val s = strings()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶栏 (.policy-capacity-bar-labels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.policyDistribution,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )

                // 图例 (新版精准语义：正常对话区 / 预备存档区 / 未用物理余量)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ByokLegendDot(color = Color(0xFF10B981), label = s.policyLegendNormal)
                    ByokLegendDot(color = Color(0xFFF59E0B), label = s.policyLegendArchive)
                    ByokLegendDot(color = Color(0xFF94A3B8), label = s.policyLegendUnused)
                }
            }

            // 进度条 (.policy-capacity-bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFFE2E8F0).copy(alpha = 0.6f))
            ) {
                Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    // 正常对话区
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(thresholdPct.coerceAtLeast(0.001f))
                            .background(Color(0xFF10B981))
                    )
                    // 预备存档区
                    if (compressPct > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(compressPct)
                                .background(Color(0xFFF59E0B))
                        )
                    }
                    // 未用物理余量
                    if (reservePct > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(reservePct)
                                .background(Color(0xFF94A3B8))
                        )
                    }
                }
            }

            // 刻度 (.policy-capacity-ticks)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                )
                Text(
                    text = formatTokenDisplay(threshold),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                )
                Text(
                    text = formatTokenDisplay(limit),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                )
                Text(
                    text = formatTokenDisplay(capacity),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                )
            }
        }
    }
}

@Composable
private fun ByokLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        )
    }
}

private fun formatCommaNumber(number: Long): String {
    if (number <= 0L) return "0"
    val str = number.toString()
    val sb = StringBuilder()
    for (i in str.indices) {
        if (i > 0 && (str.length - i) % 3 == 0) {
            sb.append(",")
        }
        sb.append(str[i])
    }
    return sb.toString()
}
