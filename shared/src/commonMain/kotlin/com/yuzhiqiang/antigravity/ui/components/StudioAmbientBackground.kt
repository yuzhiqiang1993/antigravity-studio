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
 * 自适应 8 大主题色彩体系（赤橙黄绿青蓝紫 + 极简纯白）：
 * - 浅色模式下以极净浅色底板为基底，由主色 (Primary) 与辅助色 (Tertiary) 动态生成 8%~12% 专属极淡优雅漫射；
 * - 为上层纯白半透明磨砂毛玻璃卡片 (Color.White 0.82f) 提供通透晶莹的散射折射光源；
 * - 深色模式下提供深邃冷夜星空与柔和星云极光漫射。
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
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    // 判断是否为 100% 纯白主题
    val isPureWhite = !isDark && surfaceColor == Color.White

    // 1. 视口全屏底色：根据当前主题色动态合成流光大底板 (纯白主题下为绝对纯白)
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
    } else if (isPureWhite) {
        Brush.linearGradient(
            colors = listOf(Color.White, Color.White),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                surfaceColor,
                surfaceColor,
                primaryContainer.copy(alpha = 0.30f),
                secondaryContainer.copy(alpha = 0.35f),
                surfaceColor
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
        if (!isPureWhite || isDark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 2. 右上方主环境光斑 (当前主题 Primary 强调色柔光漫射)
                val primaryGlow = primaryColor.copy(alpha = if (isDark) 0.14f else 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow,
                            primaryGlow.copy(alpha = primaryGlow.alpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.92f, h * 0.08f),
                        radius = (w * 0.55f).coerceAtLeast(460f)
                    ),
                    center = Offset(w * 0.92f, h * 0.08f),
                    radius = (w * 0.55f).coerceAtLeast(460f)
                )

                // 3. 顶部中部次级折射微光 (Tertiary 色相微光)
                val topGlow = tertiaryColor.copy(alpha = if (isDark) 0.10f else 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            topGlow,
                            Color.Transparent
                        ),
                        center = Offset(w * 0.40f, h * 0.05f),
                        radius = (w * 0.42f).coerceAtLeast(360f)
                    ),
                    center = Offset(w * 0.40f, h * 0.05f),
                    radius = (w * 0.42f).coerceAtLeast(360f)
                )

                // 4. 左下方微弱环境光
                val bottomGlow = secondaryColor.copy(alpha = if (isDark) 0.08f else 0.04f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bottomGlow,
                            Color.Transparent
                        ),
                        center = Offset(w * 0.06f, h * 0.92f),
                        radius = (w * 0.38f).coerceAtLeast(320f)
                    ),
                    center = Offset(w * 0.06f, h * 0.92f),
                    radius = (w * 0.38f).coerceAtLeast(320f)
                )
            }
        }

        content()
    }
}
