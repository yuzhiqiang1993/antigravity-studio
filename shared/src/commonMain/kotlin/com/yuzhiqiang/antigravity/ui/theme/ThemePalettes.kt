package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuzhiqiang.antigravity.i18n.Strings

/**
 * 现代纯白毛玻璃 (Modern Glassmorphism White) 核心主题调色板定义
 * 对标 JetBrains Toolbox 级色彩工程与 MD3 HCT 规范：
 * - 纯净暖白环境底衬 + 纯白半透明磨砂卡片
 * - 沉稳科技深海蓝 (#0284C7) 核心点睛
 * - 极致深邃炭黑 (#19191C) 锐利高对比正文
 */
enum class ThemePalette(
    val id: String,
    val previewColor: Color,
    val labelProvider: (Strings) -> String
) {
    WHITE(
        id = "white",
        previewColor = Color(0xFFFFFFFF),
        labelProvider = { it.paletteWhite }
    );

    companion object {
        fun fromId(id: String?): ThemePalette = WHITE
    }
}

/**
 * 调色板工厂，负责构建包含全部 29 个 MD3 语义角色的完整 ColorScheme
 */
object ThemePalettes {

    // MD3 标准通用 Error 角色 (Tone 40 / Tone 80 对应)
    private val LightError = Color(0xFFB91C1C)
    private val LightOnError = Color(0xFFFFFFFF)
    private val LightErrorContainer = Color(0xFFFEE2E2)
    private val LightOnErrorContainer = Color(0xFF7F1D1D)

    private val DarkError = Color(0xFFEF4444)
    private val DarkOnError = Color(0xFF450A0A)
    private val DarkErrorContainer = Color(0xFF7F1D1D)
    private val DarkOnErrorContainer = Color(0xFFFECACA)

    fun getColorScheme(palette: ThemePalette = ThemePalette.WHITE, isDark: Boolean): ColorScheme {
        return if (isDark) getDarkColorScheme() else getLightColorScheme()
    }

    private fun getLightColorScheme(): ColorScheme {
        return lightColorScheme(
            primary = Color(0xFF0284C7),          // 沉稳深海科技蓝 (Toolbox 经典点睛蓝)
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE0F2FE), // 极淡通透冰蓝微底
            onPrimaryContainer = Color(0xFF0369A1), // 深蓝文字
            inversePrimary = Color(0xFF7DD3FC),
            secondary = Color(0xFF475569),        // Slate-600
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF1F5F9), // Slate-100
            onSecondaryContainer = Color(0xFF19191C),
            tertiary = Color(0xFF0D9488),         // 沉稳暗青绿
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFCCFBF1),
            onTertiaryContainer = Color(0xFF134E4A),
            background = Color(0xFFFAF8F9),       // 晨曦暖白底板
            onBackground = Color(0xFF19191C),
            surface = Color(0xFFFAF8F9),
            onSurface = Color(0xFF19191C),        // 深邃炭黑文本 (16:1 锐利清晰度)
            surfaceVariant = Color(0xFFF1F5F9),   // Slate-100
            onSurfaceVariant = Color(0xFF64748B), // 中性石板灰 (7:1 柔和护眼说明字)
            surfaceContainerLowest = Color(0xFFFFFFFF), // 纯白
            surfaceContainerLow = Color(0xFFFAF8F9),   // 侧边栏与顶栏
            surfaceContainer = Color(0xFFFFFFFF),      // 纯白高光卡片
            surfaceContainerHigh = Color(0xFFF1F5F9),  // 卡片内嵌微灰块
            surfaceContainerHighest = Color(0xFFE2E8F0),
            surfaceDim = Color(0xFFE2E8F0),
            surfaceBright = Color(0xFFFFFFFF),
            inverseSurface = Color(0xFF19191C),
            inverseOnSurface = Color(0xFFF8FAFC),
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFE2E8F0),
            error = LightError,
            onError = LightOnError,
            errorContainer = LightErrorContainer,
            onErrorContainer = LightOnErrorContainer
        )
    }

    private fun getDarkColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = Color(0xFF38BDF8),          // 柔和天蓝
            onPrimary = Color(0xFF082F49),
            primaryContainer = Color(0xFF0369A1),
            onPrimaryContainer = Color(0xFFE0F2FE),
            inversePrimary = Color(0xFF0284C7),
            secondary = Color(0xFF94A3B8),
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = Color(0xFF2DD4BF),
            onTertiary = Color(0xFF042F2E),
            tertiaryContainer = Color(0xFF134E4A),
            onTertiaryContainer = Color(0xFFCCFBF1),
            background = Color(0xFF090D16),       // 深空黑底
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF090D16),
            onSurface = Color(0xFFF8FAFC),        // 柔和白字
            surfaceVariant = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF94A3B8),
            surfaceContainerLowest = Color(0xFF05080F),
            surfaceContainerLow = Color(0xFF0D131F),
            surfaceContainer = Color(0xFF111827),  // 深色卡片
            surfaceContainerHigh = Color(0xFF1E293B),
            surfaceContainerHighest = Color(0xFF334155),
            surfaceDim = Color(0xFF090D16),
            surfaceBright = Color(0xFF1E293B),
            inverseSurface = Color(0xFFF8FAFC),
            inverseOnSurface = Color(0xFF0F172A),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF1E293B),
            error = DarkError,
            onError = DarkOnError,
            errorContainer = DarkErrorContainer,
            onErrorContainer = DarkOnErrorContainer
        )
    }
}
