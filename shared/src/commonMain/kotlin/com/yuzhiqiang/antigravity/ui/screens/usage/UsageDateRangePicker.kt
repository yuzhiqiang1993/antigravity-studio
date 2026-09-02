package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class ActiveDateField {
    START,
    END
}

/**
 * 复合型日期与时间范围选择面板弹窗
 */
@Composable
fun UsageDateRangePickerDialog(
    initialTimeRange: UsageTimeRange,
    initialCustomRange: CustomDateRange?,
    onSelectPresetRange: (UsageTimeRange) -> Unit,
    onConfirmCustomRange: (CustomDateRange) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    val now = remember { LocalDateTime.now() }
    val today = remember(now) { now.toLocalDate() }

    // 初始化选中的快捷预设（若为 CUSTOM 则为 null）
    var selectedPreset by remember {
        mutableStateOf<UsageTimeRange?>(
            if (initialTimeRange == UsageTimeRange.CUSTOM) null else initialTimeRange
        )
    }

    // 初始化开始日期与时间
    var startDate by remember {
        mutableStateOf(
            parseDateOrFallback(initialCustomRange?.startDate, fallback = today)
        )
    }
    var startTimeStr by remember {
        mutableStateOf(
            initialCustomRange?.startTime?.ifBlank { "00:00" } ?: "00:00"
        )
    }

    // 初始化结束日期与时间
    var endDate by remember {
        mutableStateOf(
            parseDateOrFallback(initialCustomRange?.endDate, fallback = today)
        )
    }
    var endTimeStr by remember {
        mutableStateOf(
            initialCustomRange?.endTime?.ifBlank {
                formatTime(now.toLocalTime())
            } ?: formatTime(now.toLocalTime())
        )
    }

    // 结束时间跟随当前时刻
    var followNow by remember {
        mutableStateOf(initialCustomRange?.followNow ?: (initialTimeRange == UsageTimeRange.CALENDAR_TODAY))
    }

    // 当前处于激活编辑状态的字段（点击右侧日历时更新对应字段）
    var activeField by remember { mutableStateOf(ActiveDateField.START) }

    // 右侧日历当前展示的年月
    var displayYearMonth by remember {
        mutableStateOf(YearMonth.from(startDate))
    }

    // 处理预设快捷项切换
    fun applyPreset(preset: UsageTimeRange) {
        selectedPreset = preset
        val currentNow = LocalDateTime.now()
        val currToday = currentNow.toLocalDate()
        val currTimeStr = formatTime(currentNow.toLocalTime())

        when (preset) {
            UsageTimeRange.CALENDAR_TODAY -> {
                startDate = currToday
                startTimeStr = "00:00"
                endDate = currToday
                endTimeStr = currTimeStr
                followNow = true
                displayYearMonth = YearMonth.from(currToday)
            }
            UsageTimeRange.ROLLING_24H -> {
                val start = currentNow.minusDays(1)
                startDate = start.toLocalDate()
                startTimeStr = formatTime(start.toLocalTime())
                endDate = currToday
                endTimeStr = currTimeStr
                followNow = true
                displayYearMonth = YearMonth.from(startDate)
            }
            UsageTimeRange.ROLLING_7D -> {
                val start = currentNow.minusDays(7)
                startDate = start.toLocalDate()
                startTimeStr = formatTime(start.toLocalTime())
                endDate = currToday
                endTimeStr = currTimeStr
                followNow = true
                displayYearMonth = YearMonth.from(startDate)
            }
            UsageTimeRange.ROLLING_14D -> {
                val start = currentNow.minusDays(14)
                startDate = start.toLocalDate()
                startTimeStr = formatTime(start.toLocalTime())
                endDate = currToday
                endTimeStr = currTimeStr
                followNow = true
                displayYearMonth = YearMonth.from(startDate)
            }
            UsageTimeRange.ROLLING_30D -> {
                val start = currentNow.minusDays(30)
                startDate = start.toLocalDate()
                startTimeStr = formatTime(start.toLocalTime())
                endDate = currToday
                endTimeStr = currTimeStr
                followNow = true
                displayYearMonth = YearMonth.from(startDate)
            }
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.yuzhiqiang.antigravity.ui.components.StudioDialogSurface(
            modifier = Modifier
                .width(620.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(AppTokens.Radius.dialog)
        ) {
            Column(
                modifier = Modifier.padding(AppTokens.Spacing.card),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 顶部快捷预设选项胶囊栏 [当天] [1d] [7d] [14d] [30d]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PresetCapsule(
                        label = s.usagePresetToday,
                        selected = selectedPreset == UsageTimeRange.CALENDAR_TODAY,
                        onClick = { applyPreset(UsageTimeRange.CALENDAR_TODAY) }
                    )
                    PresetCapsule(
                        label = s.usagePreset1Day,
                        selected = selectedPreset == UsageTimeRange.ROLLING_24H,
                        onClick = { applyPreset(UsageTimeRange.ROLLING_24H) }
                    )
                    PresetCapsule(
                        label = s.usagePreset7Days,
                        selected = selectedPreset == UsageTimeRange.ROLLING_7D,
                        onClick = { applyPreset(UsageTimeRange.ROLLING_7D) }
                    )
                    PresetCapsule(
                        label = s.usagePreset14Days,
                        selected = selectedPreset == UsageTimeRange.ROLLING_14D,
                        onClick = { applyPreset(UsageTimeRange.ROLLING_14D) }
                    )
                    PresetCapsule(
                        label = s.usagePreset30Days,
                        selected = selectedPreset == UsageTimeRange.ROLLING_30D,
                        onClick = { applyPreset(UsageTimeRange.ROLLING_30D) }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                // 2. 主体左右分栏布局
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 左侧：时间配置与操作面板 (宽 240dp)
                    Column(
                        modifier = Modifier.width(240.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = s.usageSupportDateAndTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // 开始时间卡片
                        DateTimeInputCard(
                            label = s.usageStartTimeLabel,
                            dateStr = formatDateWithSlash(startDate),
                            timeStr = startTimeStr,
                            isActive = activeField == ActiveDateField.START,
                            onClick = {
                                activeField = ActiveDateField.START
                                displayYearMonth = YearMonth.from(startDate)
                            },
                            onTimeChange = { newTime ->
                                startTimeStr = newTime
                                selectedPreset = null
                            }
                        )

                        // 结束时间卡片
                        DateTimeInputCard(
                            label = s.usageEndTimeLabel,
                            dateStr = if (followNow) formatDateWithSlash(LocalDate.now()) else formatDateWithSlash(endDate),
                            timeStr = if (followNow) formatTime(LocalTime.now()) else endTimeStr,
                            isActive = activeField == ActiveDateField.END && !followNow,
                            enabled = !followNow,
                            onClick = {
                                if (!followNow) {
                                    activeField = ActiveDateField.END
                                    displayYearMonth = YearMonth.from(endDate)
                                }
                            },
                            onTimeChange = { newTime ->
                                endTimeStr = newTime
                                selectedPreset = null
                            }
                        )

                        // 结束时间跟随当前时刻 Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    followNow = !followNow
                                    if (followNow) {
                                        activeField = ActiveDateField.START
                                    }
                                    selectedPreset = null
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Checkbox(
                                checked = followNow,
                                onCheckedChange = { checked ->
                                    followNow = checked
                                    if (checked) {
                                        activeField = ActiveDateField.START
                                    }
                                    selectedPreset = null
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.usageFollowNowLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = false))

                        // 底部操作按钮 [取消] [确定]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = s.commonCancel,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Button(
                                onClick = {
                                    if (selectedPreset != null) {
                                        onSelectPresetRange(selectedPreset!!)
                                    } else {
                                        val custom = CustomDateRange(
                                            startDate = startDate.toString(),
                                            endDate = if (followNow) "" else endDate.toString(),
                                            startTime = startTimeStr,
                                            endTime = if (followNow) "" else endTimeStr,
                                            followNow = followNow
                                        )
                                        onConfirmCustomRange(custom)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.2f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = s.commonConfirm,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    // 右侧：交互式月度日历网格 (宽 300dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 日历头部：<  2026年9月  >
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { displayYearMonth = displayYearMonth.minusMonths(1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "${displayYearMonth.year}年${displayYearMonth.monthValue}月",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { displayYearMonth = displayYearMonth.plusMonths(1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 星期表头
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf("日", "一", "二", "三", "四", "五", "六").forEach { weekday ->
                                Text(
                                    text = weekday,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 日期网格 6 行 7 列
                        val calendarDays = remember(displayYearMonth) {
                            buildMonthGridDays(displayYearMonth)
                        }

                        val primaryColor = MaterialTheme.colorScheme.primary
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val variantColor = MaterialTheme.colorScheme.onSurfaceVariant

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            calendarDays.chunked(7).forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    week.forEach { dateItem ->
                                        val isCurrentMonth = dateItem.month == displayYearMonth.month
                                        val isStart = dateItem == startDate
                                        val isEnd = dateItem == endDate && !followNow
                                        val inRange = !followNow && dateItem.isAfter(startDate) && dateItem.isBefore(endDate)

                                        val bgColor: Color
                                        val textColor: Color
                                        when {
                                            isStart || isEnd -> {
                                                bgColor = primaryColor
                                                textColor = Color.White
                                            }
                                            inRange -> {
                                                bgColor = primaryColor.copy(alpha = 0.12f)
                                                textColor = primaryColor
                                            }
                                            !isCurrentMonth -> {
                                                bgColor = Color.Transparent
                                                textColor = variantColor.copy(alpha = 0.3f)
                                            }
                                            else -> {
                                                bgColor = Color.Transparent
                                                textColor = onSurfaceColor
                                            }
                                        }

                                        val cellInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        val isCellHovered by cellInteraction.collectIsHoveredAsState()

                                        val effectiveCellBg = if ((isStart || isEnd || inRange)) {
                                            bgColor
                                        } else if (isCellHovered) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        } else {
                                            bgColor
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(effectiveCellBg)
                                                .hoverable(cellInteraction)
                                                .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
                                                .clickable(
                                                    interactionSource = cellInteraction,
                                                    indication = null
                                                ) {
                                                    selectedPreset = null
                                                    if (activeField == ActiveDateField.START) {
                                                        startDate = dateItem
                                                        if (endDate.isBefore(dateItem)) {
                                                            endDate = dateItem
                                                        }
                                                        if (!followNow) {
                                                            activeField = ActiveDateField.END
                                                        }
                                                    } else {
                                                        if (dateItem.isBefore(startDate)) {
                                                            startDate = dateItem
                                                        } else {
                                                            endDate = dateItem
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dateItem.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部快捷胶囊按钮（符合 MD3 FilterChip 规范）
 */
@Composable
private fun PresetCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.25f else 0.4f)
    }
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = if (!selected) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
        ) else null,
        modifier = Modifier
            .height(30.dp)
            .hoverable(interactionSource)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

/**
 * 开始/结束时间输入卡片（遵循 MD3 Outlined Card 规范）
 */
@Composable
private fun DateTimeInputCard(
    label: String,
    dateStr: String,
    timeStr: String,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onTimeChange: (String) -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
    }
    val borderWidth = if (isActive) 1.5.dp else 1.dp
    val bgColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.25f else 0.35f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.18f else 0.25f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .pointerHoverIcon(if (enabled) androidx.compose.ui.input.pointer.PointerIcon.Hand else androidx.compose.ui.input.pointer.PointerIcon.Default)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.75f else 0.4f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * 构建某月的 42 格（6x7）日历日期矩阵
 */
private fun buildMonthGridDays(yearMonth: YearMonth): List<LocalDate> {
    val firstDayOfMonth = yearMonth.atDay(1)
    val dayOfWeekVal = firstDayOfMonth.dayOfWeek.value % 7 // 周日为 0
    val startCalendarDate = firstDayOfMonth.minusDays(dayOfWeekVal.toLong())

    val days = mutableListOf<LocalDate>()
    for (i in 0 until 42) {
        days.add(startCalendarDate.plusDays(i.toLong()))
    }
    return days
}

private fun formatDateWithSlash(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

private fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun parseDateOrFallback(dateStr: String?, fallback: LocalDate): LocalDate {
    if (dateStr.isNullOrBlank()) return fallback
    return try {
        val normalized = dateStr.trim().replace('/', '-')
        LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) {
        fallback
    }
}
