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
 * 深度融合现代浅色纯白毛玻璃双主题体系：
 * 1. 晨曦微光 (DAWN): 极净晨曦暖粉 (#FAF4F6) + 珊瑚暖桃 (#F43F5E) 柔光漫射 (Toolbox 经典温润感)
 * 2. 深海幽蓝 (DEEP_OCEAN 浅色): 极净冰川冷白 (#F0F6FA) + 深海湛蓝 (#0284C7) 与冰川天蓝 (#38BDF8) 清澈漫射
 * 3. 深色模式: 深邃深空曜黑 (#090D16 / #0B132B) + 星空极光
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

    // 判断当前浅色主题是否为深海幽蓝 (冰川蓝底色)
    val isDeepOceanLight = !isDark && surfaceColor == Color(0xFFF0F6FA)

    // 1. 视口全屏底色：根据主题选用晨曦暖桃或冰川湛蓝
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
    } else if (isDeepOceanLight) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFF0F6FA), // 极净冰川浅冷白
                Color(0xFFE6EFF7), // 柔和冰川浅蓝
                Color(0xFFE0F2FE), // 通透浅冰蓝漫射
                Color(0xFFF0F6FA)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFAF4F6), // 极净晨曦暖白
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

            // 2. 右上方主环境光斑 (晨曦暖桃 或 深海湛蓝)
            val primaryGlowColor = when {
                isDark -> primaryColor.copy(alpha = 0.14f)
                isDeepOceanLight -> Color(0x280284C7) // 深海湛蓝清澈漫射
                else -> Color(0x22F43F5E)            // 珊瑚暖桃微漫射 (Toolbox 经典光感)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor,
                        primaryGlowColor.copy(alpha = primaryGlowColor.alpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.08f),
                    radius = (w * 0.55f).coerceAtLeast(460f)
                ),
                center = Offset(w * 0.92f, h * 0.08f),
                radius = (w * 0.55f).coerceAtLeast(460f)
            )

            // 3. 顶部中部次级折射微光
            val topGlowColor = when {
                isDark -> Color(0x1F7C3AED)
                isDeepOceanLight -> Color(0x1F38BDF8) // 冰川天蓝高光
                else -> Color(0x180284C7)             // 沉稳深海蓝微光
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        topGlowColor,
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
