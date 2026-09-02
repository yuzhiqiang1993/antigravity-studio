package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.usage.DailyUsageBucket
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioGlassCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 插件 up dashboard 的 GitHub 风格年度活跃度网格。
 * 桌面端保留完整 53 周宽度，窄窗口只横向滚动，不压缩到无法辨认的尺寸。
 */
@Composable
fun ActivityHeatmapCard(
    dailyBuckets: List<DailyUsageBucket>,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val colors = usageTokenColors()
    val today = LocalDate.now()
    val availableYears = remember(dailyBuckets) {
        dailyBuckets.mapNotNull { runCatching { LocalDate.parse(it.date).year }.getOrNull() }.toSet()
    }
    val year = if (today.year in availableYears) today.year else availableYears.maxOrNull() ?: today.year
    val cutoffDate = if (year == today.year) today else LocalDate.of(year, 12, 31)
    val firstDay = LocalDate.of(year, 1, 1)
    val gridStart = firstDay.minusDays((firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val weeks = remember(gridStart) {
        (0 until 53).map { week ->
            (0 until 7).map { day -> gridStart.plusDays((week * 7L) + day.toLong()) }
        }
    }
    val bucketsByDate = remember(dailyBuckets) { dailyBuckets.associateBy { it.date } }
    val maxTokens = remember(dailyBuckets, year) {
        maxOf(
            1L,
            dailyBuckets
                .filter { runCatching { LocalDate.parse(it.date).year == year }.getOrDefault(false) }
                .maxOfOrNull { it.totalTokens } ?: 1L
        )
    }

    val cellGap = 3.dp
    val cellSize = 11.5.dp

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UsageVisualTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.cardGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.usageActivityHeatmapTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = UsageVisualTokens.Typography.cardTitle,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = year.toString(),
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(AppTokens.Radius.xs)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = UsageVisualTokens.Typography.sectionBadge),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (dailyBuckets.isEmpty()) {
                Text(
                    text = s.usageTrendChartEmpty,
                    modifier = Modifier.padding(vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 月份标题行
                    MonthLabels(weeks = weeks, cellSize = cellSize, cellGap = cellGap)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 左侧星期标签
                        WeekdayLabels(cellSize = cellSize, cellGap = cellGap)

                        // 53 周矩阵网格
                        Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                            weeks.forEach { week ->
                                Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                                    week.forEach { date ->
                                        val bucket = bucketsByDate[date.toString()]
                                        ActivityCell(
                                            date = date,
                                            bucket = bucket,
                                            displayYear = year,
                                            cutoffDate = cutoffDate,
                                            maxTokens = maxTokens,
                                            cellSize = cellSize,
                                            color = MaterialTheme.colorScheme.primary,
                                            strings = s
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 底部右侧图例 (少 🟩 🟩 🟩 🟩 多)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "少",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.badgeText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(0.04f, 0.25f, 0.50f, 0.75f, 1.0f).forEach { alpha ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (alpha <= 0.05f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "多",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = UsageVisualTokens.Typography.badgeText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthLabels(
    weeks: List<List<LocalDate>>,
    cellSize: androidx.compose.ui.unit.Dp,
    cellGap: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(22.dp))
        weeks.forEachIndexed { weekIndex, week ->
            val firstOfMonth = week.firstOrNull { it.dayOfMonth in 1..7 && it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfMonth == 1 }
            val showMonth = firstOfMonth != null && (weekIndex == 0 || weekIndex % 4 == 0)
            Box(
                modifier = Modifier.width(cellSize + cellGap),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showMonth) {
                    Text(
                        text = monthLabel(firstOfMonth!!.monthValue),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.axisTime,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayLabels(
    cellSize: androidx.compose.ui.unit.Dp,
    cellGap: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier.width(18.dp),
        verticalArrangement = Arrangement.spacedBy(cellGap)
    ) {
        repeat(7) { index ->
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(cellSize),
                contentAlignment = Alignment.CenterStart
            ) {
                if (index == 0 || index == 2 || index == 4) {
                    Text(
                        text = if (I18nManager.currentLanguage == AppLanguage.ZH_CN) {
                            listOf("一", "二", "三", "四", "五", "六", "日")[index]
                        } else {
                            listOf("M", "T", "W", "T", "F", "S", "S")[index]
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = UsageVisualTokens.Typography.badgeText,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCell(
    date: LocalDate,
    bucket: DailyUsageBucket?,
    displayYear: Int,
    cutoffDate: LocalDate,
    maxTokens: Long,
    cellSize: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    strings: Strings
) {
    val visible = date.year == displayYear && !date.isAfter(cutoffDate)
    val tokens = bucket?.totalTokens ?: 0L
    val intensity = if (visible && tokens > 0L) {
        (tokens.toDouble() / maxTokens.toDouble()).toFloat().coerceIn(0.15f, 1f)
    } else 0f

    val cellColor = when {
        !visible -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        tokens > 0L -> color.copy(alpha = 0.20f + intensity * 0.80f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    }

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(2.5.dp))
            .background(cellColor)
            .semantics {
                if (visible) {
                    contentDescription = strings.usageActivityHeatmapTip(
                        UsageNumberFormatter.formatShortDate(date.toString()),
                        UsageNumberFormatter.formatTokens(tokens),
                        bucket?.calls ?: 0L
                    )
                }
            }
    )
}

private fun monthLabel(month: Int): String = if (I18nManager.currentLanguage == AppLanguage.ZH_CN) {
    "${month}月"
} else {
    arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        .getOrNull(month - 1) ?: month.toString()
}
