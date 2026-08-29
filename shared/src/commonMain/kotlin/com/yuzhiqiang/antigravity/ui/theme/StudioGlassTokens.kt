package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Antigravity Studio 毛玻璃与容器设计规范令牌 (StudioGlassTokens)。
 * 严格对标 JetBrains Toolbox 现代纯白毛玻璃与大圆角浮岛规范：
 * 1. 卡片统一使用纯白磨砂半透明玻璃 Color.White.copy(alpha = 0.82f)，通透有呼吸感
 * 2. 边框采用纯白高光微轮廓与沉稳科技蓝悬停光感，彻底消除生硬灰铁线框
 * 3. 卡片圆角统一为 18dp 大圆角，浮岛形态温润大气
 */
object StudioGlassTokens {

    // 1. 半透明度标准 (Alpha Scale - 纯净通透磨砂)
    const val surfaceAlphaLight = 0.82f
    const val surfaceAlphaDark = 0.70f

    const val topBarAlphaLight = 0.88f
    const val topBarAlphaDark = 0.76f

    const val innerPanelAlphaLight = 0.65f
    const val innerPanelAlphaDark = 0.45f

    const val activeCardAlphaLight = 0.92f
    const val activeCardAlphaDark = 0.82f

    // 2. 卡片与浮岛规范
    val borderWidth: Dp = 1.dp
    val activeBorderWidth: Dp = 1.5.dp
    val cardCornerRadius: Dp = 18.dp
    val cardElevation: Dp = 0.dp
    val cardElevationHovered: Dp = 0.dp
    val topBarElevation: Dp = 0.dp

    // 3. Toolbox 风格纯净微轮廓 (去粗黑边框，浅色使用高光纯白与极淡微线)
    @Composable
    fun cleanBorderColor(isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f, isHovered: Boolean = false): Color {
        return if (isDark) {
            if (isHovered) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        } else {
            if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
            else Color.White.copy(alpha = 0.85f)
        }
    }

    // 4. 次级内嵌面板细微轮廓
    @Composable
    fun innerPanelBorderColor(isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f): Color {
        return if (isDark) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        } else {
            Color.White.copy(alpha = 0.60f)
        }
    }

    // 5. 活跃状态极光渐变边框 (多色平滑过渡)
    fun activeAuroraBorderBrush(primaryColor: Color, secondaryColor: Color): Brush {
        return Brush.linearGradient(
            colors = listOf(
                primaryColor,
                secondaryColor,
                primaryColor.copy(alpha = 0.85f)
            )
        )
    }

    // 6. 快捷获取当前主题下的纯白通透毛玻璃底色
    @Composable
    fun cardBackgroundColor(isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f): Color {
        return if (isDark) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = surfaceAlphaDark)
        } else {
            Color.White.copy(alpha = surfaceAlphaLight)
        }
    }

    @Composable
    fun topBarBackgroundColor(isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f): Color {
        return if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = topBarAlphaDark)
        } else {
            Color.White.copy(alpha = topBarAlphaLight)
        }
    }

    @Composable
    fun innerPanelBackgroundColor(isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f): Color {
        return if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = innerPanelAlphaDark)
        } else {
            Color(0xFFF1F5F9).copy(alpha = innerPanelAlphaLight)
        }
    }
}
