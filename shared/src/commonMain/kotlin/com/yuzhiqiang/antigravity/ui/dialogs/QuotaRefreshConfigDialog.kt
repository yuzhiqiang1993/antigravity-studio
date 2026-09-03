package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioSegmentedControl
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
import com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

private enum class IntervalUnit(val multiplierSeconds: Int) {
    SECONDS(1),
    MINUTES(60),
    HOURS(3600);

    fun label(s: Strings): String = when (this) {
        SECONDS -> s.quotaRefreshUnitSecond
        MINUTES -> s.quotaRefreshUnitMinute
        HOURS -> s.quotaRefreshUnitHour
    }
}

/**
 * 将秒数智能拆分为最佳显示数值与单位（完全对齐 Cockpit 插件 splitRefreshInterval）
 */
private fun splitIntervalSeconds(seconds: Int): Pair<Double, IntervalUnit> {
    val sec = seconds.coerceAtLeast(1)
    return when {
        sec % 3600 == 0 -> (sec / 3600.0) to IntervalUnit.HOURS
        sec % 60 == 0 -> (sec / 60.0) to IntervalUnit.MINUTES
        else -> sec.toDouble() to IntervalUnit.SECONDS
    }
}

private fun formatNumberTrimZero(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

/**
 * 精美分段选择器（用于快速切换秒/分/小时单位）
 */
@Composable
private fun SegmentedUnitPicker(
    units: List<IntervalUnit>,
    selectedUnit: IntervalUnit,
    onSelectUnit: (IntervalUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val items = remember(units, s) {
        units.map { unit ->
            StudioTabItem(unit, unit.label(s))
        }
    }
    StudioSegmentedControl(
        items = items,
        selectedKey = selectedUnit,
        onSelect = onSelectUnit,
        modifier = modifier,
        height = 28.dp
    )
}

/**
 * 预设 Chip 选项卡
 */
@Composable
private fun IntervalPresetChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
    }
    val borderClr = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.6f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, borderClr)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * 额度自动刷新频率配置弹窗 (1:1 复刻 Cockpit 原生交互 + Studio 沉稳精致设计)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotaRefreshConfigDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val config by viewModel.config.collectAsState()

    // 默认常量 (1:1 对齐 Cockpit 插件规范)
    val defaultActiveSeconds = 60
    val defaultBackgroundSeconds = 600

    val minActiveSeconds = 10
    val minBackgroundSeconds = 60
    val maxSeconds = 3600

    // 预设值列表 (单位: 秒)
    val activePresets = listOf(
        10 to "10s",
        30 to "30s",
        60 to s.quotaRefreshPresetRecommended("60s"),
        120 to "2m",
        300 to "5m",
        600 to "10m"
    )

    val backgroundPresets = listOf(
        60 to "1m",
        180 to "3m",
        300 to "5m",
        600 to s.quotaRefreshPresetRecommended("10m"),
        1800 to "30m",
        3600 to "1h"
    )

    // 当前活跃账号状态
    var activeSelectedPreset by remember(config) {
        val current = config.quotaActiveIntervalSeconds
        mutableStateOf(if (activePresets.any { it.first == current }) current else -1)
    }
    var activeCustomValue by remember(config) {
        val split = splitIntervalSeconds(config.quotaActiveIntervalSeconds)
        mutableStateOf(formatNumberTrimZero(split.first))
    }
    var activeCustomUnit by remember(config) {
        val split = splitIntervalSeconds(config.quotaActiveIntervalSeconds)
        mutableStateOf(split.second)
    }

    // 其他后台账号状态
    var bgSelectedPreset by remember(config) {
        val current = config.quotaBackgroundIntervalSeconds
        mutableStateOf(if (backgroundPresets.any { it.first == current }) current else -1)
    }
    var bgCustomValue by remember(config) {
        val split = splitIntervalSeconds(config.quotaBackgroundIntervalSeconds)
        mutableStateOf(formatNumberTrimZero(split.first))
    }
    var bgCustomUnit by remember(config) {
        val split = splitIntervalSeconds(config.quotaBackgroundIntervalSeconds)
        mutableStateOf(if (split.second == IntervalUnit.SECONDS) IntervalUnit.MINUTES else split.second)
    }

    // 校验活跃账号输入
    val activeValidation = remember(activeSelectedPreset, activeCustomValue, activeCustomUnit, s) {
        if (activeSelectedPreset != -1) {
            Triple(activeSelectedPreset, true, null)
        } else {
            val num = activeCustomValue.toDoubleOrNull()
            if (num == null || num <= 0.0) {
                Triple(0, false, s.quotaRefreshInputInvalid)
            } else {
                val totalSec = (num * activeCustomUnit.multiplierSeconds).toInt()
                when {
                    totalSec < minActiveSeconds -> Triple(0, false, s.quotaRefreshMinActiveSeconds(minActiveSeconds))
                    totalSec > maxSeconds -> Triple(0, false, s.quotaRefreshMaxHours(maxSeconds / 3600))
                    else -> Triple(totalSec, true, null)
                }
            }
        }
    }

    // 校验后台账号输入
    val bgValidation = remember(bgSelectedPreset, bgCustomValue, bgCustomUnit, s) {
        if (bgSelectedPreset != -1) {
            Triple(bgSelectedPreset, true, null)
        } else {
            val num = bgCustomValue.toDoubleOrNull()
            if (num == null || num <= 0.0) {
                Triple(0, false, s.quotaRefreshInputInvalid)
            } else {
                val totalSec = (num * bgCustomUnit.multiplierSeconds).toInt()
                when {
                    totalSec < minBackgroundSeconds -> Triple(0, false, s.quotaRefreshMinBackgroundMinutes(minBackgroundSeconds / 60))
                    totalSec > maxSeconds -> Triple(0, false, s.quotaRefreshMaxHours(maxSeconds / 3600))
                    else -> Triple(totalSec, true, null)
                }
            }
        }
    }

    val canSave = activeValidation.second && bgValidation.second

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogSurface(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 540.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = s.quotaRefreshTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                        Text(
                            text = s.quotaRefreshSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. 当前活跃账号刷新间隔
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = s.quotaRefreshActiveIntervalTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )

                    // 预设按钮网格 + 自定义
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activePresets.forEach { (sec, label) ->
                            IntervalPresetChip(
                                selected = activeSelectedPreset == sec,
                                label = label,
                                onClick = {
                                    activeSelectedPreset = sec
                                    val split = splitIntervalSeconds(sec)
                                    activeCustomValue = formatNumberTrimZero(split.first)
                                    activeCustomUnit = split.second
                                }
                            )
                        }

                        // 自定义…
                        IntervalPresetChip(
                            selected = activeSelectedPreset == -1,
                            label = s.quotaRefreshCustomOption,
                            onClick = { activeSelectedPreset = -1 }
                        )
                    }

                    // 自定义展开输入框 (复用 Studio 全局输入框规范 + 纯净单位选择器)
                    AnimatedVisibility(
                        visible = activeSelectedPreset == -1,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioTextField(
                                    value = activeCustomValue,
                                    onValueChange = { activeCustomValue = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    placeholder = s.quotaRefreshPlaceholderActive,
                                    singleLine = true,
                                    modifier = Modifier.width(110.dp),
                                    isError = !activeValidation.second
                                )

                                SegmentedUnitPicker(
                                    units = listOf(IntervalUnit.SECONDS, IntervalUnit.MINUTES, IntervalUnit.HOURS),
                                    selectedUnit = activeCustomUnit,
                                    onSelectUnit = { activeCustomUnit = it }
                                )
                            }

                            val activeError = activeValidation.third
                            if (!activeValidation.second && activeError != null) {
                                Text(
                                    text = activeError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = s.quotaRefreshActiveHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }

                // 3. 其他后台账号刷新间隔
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = s.quotaRefreshBackgroundIntervalTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )

                    // 预设按钮网格 + 自定义
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        backgroundPresets.forEach { (sec, label) ->
                            IntervalPresetChip(
                                selected = bgSelectedPreset == sec,
                                label = label,
                                onClick = {
                                    bgSelectedPreset = sec
                                    val split = splitIntervalSeconds(sec)
                                    bgCustomValue = formatNumberTrimZero(split.first)
                                    bgCustomUnit = split.second
                                }
                            )
                        }

                        // 自定义…
                        IntervalPresetChip(
                            selected = bgSelectedPreset == -1,
                            label = s.quotaRefreshCustomOption,
                            onClick = { bgSelectedPreset = -1 }
                        )
                    }

                    // 自定义展开输入框 (复用 Studio 全局输入框规范 + 纯净单位选择器)
                    AnimatedVisibility(
                        visible = bgSelectedPreset == -1,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioTextField(
                                    value = bgCustomValue,
                                    onValueChange = { bgCustomValue = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    placeholder = s.quotaRefreshPlaceholderBackground,
                                    singleLine = true,
                                    modifier = Modifier.width(110.dp),
                                    isError = !bgValidation.second
                                )

                                SegmentedUnitPicker(
                                    units = listOf(IntervalUnit.MINUTES, IntervalUnit.HOURS, IntervalUnit.SECONDS),
                                    selectedUnit = bgCustomUnit,
                                    onSelectUnit = { bgCustomUnit = it }
                                )
                            }

                            val bgError = bgValidation.third
                            if (!bgValidation.second && bgError != null) {
                                Text(
                                    text = bgError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                // 默认提示说明
                Text(
                    text = s.quotaRefreshDefaultSummary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                // 4. 底部操作栏: [恢复默认] (左) + [取消] [保存设置] (右)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            activeSelectedPreset = defaultActiveSeconds
                            val activeSplit = splitIntervalSeconds(defaultActiveSeconds)
                            activeCustomValue = formatNumberTrimZero(activeSplit.first)
                            activeCustomUnit = activeSplit.second

                            bgSelectedPreset = defaultBackgroundSeconds
                            val bgSplit = splitIntervalSeconds(defaultBackgroundSeconds)
                            bgCustomValue = formatNumberTrimZero(bgSplit.first)
                            bgCustomUnit = bgSplit.second
                        }
                    ) {
                        Text(
                            text = s.quotaRefreshResetDefault,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(s.commonCancel, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (canSave) {
                                    viewModel.updateQuotaRefreshConfig(
                                        enabled = true,
                                        activeIntervalSec = activeValidation.first,
                                        backgroundIntervalSec = bgValidation.first
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = canSave,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(s.commonSave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
