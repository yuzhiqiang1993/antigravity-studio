package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 全局环境氛围流光背景容器 (StudioAmbientBackground)。
 * 深度融合 JetBrains Toolbox 级现代纯白毛玻璃体系：
 * - 浅色模式下以极净晨曦柔光 (#F8F3F5) 为底衬，融合极淡珊瑚暖桃与深海微蓝漫射；
 * - 赋予大背景生命力与玉石温润感，为上层半透明纯白磨砂玻璃卡片 (Color.White 0.85f) 提供晶莹通透的折射光源；
 * - 深色模式下提供深邃深空曜黑 (#090D16) 与微光星云。
 */
@Composable
fun StudioAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // 1. 视口全屏底色：Toolbox 风格晨曦暖粉微漫射底衬
    val bgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                surfaceColor,
                containerLow,
                MaterialTheme.colorScheme.surfaceContainer,
                surfaceColor
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFAF4F6), // 极净晨曦暖白 (Toolbox 经典基底)
                Color(0xFFF7EFF2), // 柔和微桃灰
                Color(0xFFFBECEF), // 晨曦暖桃微漫射
                Color(0xFFEEF4FF), // 冰川微蓝折射源
                Color(0xFFFAF4F6)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 2. 右上方晨曦暖桃与深海蓝交织的柔和环境光斑 (Toolbox 标志性温润光感)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isDark) primaryColor.copy(alpha = 0.14f) else Color(0x22F43F5E), // 珊瑚暖桃微漫射
                        if (isDark) primaryColor.copy(alpha = 0.05f) else Color(0x0AE11D48),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.08f),
                    radius = (w * 0.55f).coerceAtLeast(460f)
                ),
                center = Offset(w * 0.92f, h * 0.08f),
                radius = (w * 0.55f).coerceAtLeast(460f)
            )

            // 3. 顶部中部深海科技蓝柔和折射微光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isDark) Color(0x1F7C3AED) else Color(0x180284C7), // 沉稳深海蓝微光
                        Color.Transparent
                    ),
                    center = Offset(w * 0.40f, h * 0.05f),
                    radius = (w * 0.42f).coerceAtLeast(360f)
                ),
                center = Offset(w * 0.40f, h * 0.05f),
                radius = (w * 0.42f).coerceAtLeast(360f)
            )

            // 4. 左下方微弱环境光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = if (isDark) 0.10f else 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.06f, h * 0.92f),
                    radius = (w * 0.38f).coerceAtLeast(320f)
                ),
                center = Offset(w * 0.06f, h * 0.92f),
                radius = (w * 0.38f).coerceAtLeast(320f)
            )
        }

        content()
    }
}
