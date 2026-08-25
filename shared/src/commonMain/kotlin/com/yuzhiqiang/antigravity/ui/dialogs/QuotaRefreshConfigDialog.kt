package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

private enum class IntervalUnit(val label: String, val multiplierSeconds: Int) {
    SECONDS("秒", 1),
    MINUTES("分钟", 60),
    HOURS("小时", 3600)
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

@Composable
private fun IntervalPresetChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    }
    val borderClr = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.7f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
            .background(bg)
            .border(1.dp, borderClr, RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun SegmentedUnitPicker(
    units: List<IntervalUnit>,
    selectedUnit: IntervalUnit,
    onSelectUnit: (IntervalUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val borderClr = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
    val bg = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
            .background(bg)
            .border(1.dp, borderClr, RoundedCornerShape(StudioDesignTokens.CornerRadius.sm))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        units.forEach { unit ->
            val isSelected = selectedUnit == unit
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.xs))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onSelectUnit(unit) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unit.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotaRefreshConfigDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
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
        60 to "60s (推荐)",
        120 to "2m",
        300 to "5m",
        600 to "10m"
    )

    val backgroundPresets = listOf(
        60 to "1m",
        180 to "3m",
        300 to "5m",
        600 to "10m (推荐)",
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
    val activeValidation = remember(activeSelectedPreset, activeCustomValue, activeCustomUnit) {
        if (activeSelectedPreset != -1) {
            Triple(activeSelectedPreset, true, null)
        } else {
            val num = activeCustomValue.toDoubleOrNull()
            if (num == null || num <= 0.0) {
                Triple(0, false, "请输入有效的刷新时间")
            } else {
                val totalSec = (num * activeCustomUnit.multiplierSeconds).toInt()
                when {
                    totalSec < minActiveSeconds -> Triple(0, false, "最短刷新时间为 10 秒")
                    totalSec > maxSeconds -> Triple(0, false, "最长刷新时间为 1 小时")
                    else -> Triple(totalSec, true, null)
                }
            }
        }
    }

    // 校验后台账号输入
    val bgValidation = remember(bgSelectedPreset, bgCustomValue, bgCustomUnit) {
        if (bgSelectedPreset != -1) {
            Triple(bgSelectedPreset, true, null)
        } else {
            val num = bgCustomValue.toDoubleOrNull()
            if (num == null || num <= 0.0) {
                Triple(0, false, "请输入有效的刷新时间")
            } else {
                val totalSec = (num * bgCustomUnit.multiplierSeconds).toInt()
                when {
                    totalSec < minBackgroundSeconds -> Triple(0, false, "最短刷新时间为 1 分钟")
                    totalSec > maxSeconds -> Triple(0, false, "最长刷新时间为 1 小时")
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
        StudioCard(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 580.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                                text = "设置额度自动刷新频率",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                        Text(
                            text = "配置多账号配额后台自动同步频率（每个卡片左下角展示最后更新时间）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. 当前账号轮询周期
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "当前账号轮询周期",
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
                            label = "自定义…",
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
                                    placeholder = "例如 45",
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

                            if (!activeValidation.second && activeValidation.third != null) {
                                Text(
                                    text = activeValidation.third!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "提示：当前账号的刷新间隔会同时影响配额水位更新和自动切号触发时机，建议不要设置过长。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }

                // 3. 其他账号轮询周期
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "其他账号轮询周期",
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
                                    bgCustomUnit = if (split.second == IntervalUnit.SECONDS) IntervalUnit.MINUTES else split.second
                                }
                            )
                        }

                        // 自定义…
                        IntervalPresetChip(
                            selected = bgSelectedPreset == -1,
                            label = "自定义…",
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
                                    placeholder = "例如 15",
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

                            if (!bgValidation.second && bgValidation.third != null) {
                                Text(
                                    text = bgValidation.third!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                // 默认提示说明
                Text(
                    text = "默认：当前账号 1 分钟，其他账号 10 分钟",
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
                            text = "恢复默认",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消", fontSize = 13.sp)
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
                            Text("保存设置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
