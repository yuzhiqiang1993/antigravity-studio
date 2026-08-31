package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Usage 模块专用设计系统视觉令牌。
 * 集中统一管理所有尺寸、间距、字号与特定视觉样式，避免散落硬编码魔法值。
 */
object UsageVisualTokens {
    // 布局与卡片基础尺寸
    val cardPadding = 14.dp
    val cardVerticalPadding = 12.dp
    val cardGap = 10.dp
    val sectionGap = 14.dp
    val innerRadius = 8.dp
    val progressHeight = 4.5.dp
    val progressRadius = 2.dp
    val detailGap = 8.dp
    val pillRadius = 16.dp

    // 图表与 X 轴尺寸令牌
    object Chart {
        val containerHeight = 220.dp
        val plotTop = 24.dp
        val plotBottomPadding = 12.dp
        val plotHorizontalPadding = 12.dp
        val axisHeight = 40.dp
        val axisItemWidth = 72.dp
        val axisTopPadding = 4.dp
        val axisSpacing = 2.dp
        val legendDotSize = 8.5.dp
        val legendSpacing = 16.dp
        val strokeWidth = 2.dp
        val dotRadius = 3.5.dp
        val dotHaloRadius = 7.dp
        val dotStrokeWidth = 1.5.dp
    }

    // 悬浮气泡 Tooltip 样式令牌
    object Tooltip {
        val width = 205.dp
        val cornerRadius = 8.dp
        val elevation = 8.dp
        val arrowWidth = 10.dp
        val arrowHeight = 5.dp
        val borderWidth = 1.dp
        val paddingHorizontal = 12.dp
        val paddingVertical = 8.dp
        val rowSpacing = 3.dp

        val backgroundColor = Color(0xFF18181B).copy(alpha = 0.96f)
        val borderColor = Color.White.copy(alpha = 0.18f)
        val tokenHighlightColor = Color(0xFF4ADE80)
        val costHighlightColor = Color(0xFFA78BFA)
        val sliceHighlightColor = Color(0xFF8B5CF6).copy(alpha = 0.10f)
    }

    // 字体字号令牌 (Typography Sp)
    object Typography {
        val cardTitle = 14.sp
        val overviewTitle = 13.5.sp
        val heroValue = 24.sp
        val modelTitle = 13.5.sp
        val sectionBadge = 12.sp
        val legendText = 12.sp
        val heroTitle = 12.sp
        val heroSupporting = 11.5.sp
        val axisTime = 11.sp
        val axisTokens = 11.5.sp
        val tooltipTitle = 12.5.sp
        val tooltipLabel = 11.sp
        val tooltipValue = 12.sp
        val modelMeta = 11.5.sp
        val badgeText = 10.5.sp
        val rankNumber = 11.sp
    }

    // 模型明细列表尺寸
    object ModelList {
        val rankBadgeSize = 22.dp
        val barHeight = 6.dp
        val itemSpacing = 12.dp
        val rowGap = 6.dp
        val headerGap = 8.dp
    }
}
