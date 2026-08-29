package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuzhiqiang.antigravity.i18n.Strings

/**
 * 现代核心主题调色板定义 (Material Design 3 标准)
 * 1. DAWN (晨曦粉紫): 浅色基调，晨曦暖粉漫射大底 + 纯白磨砂卡片 + 优雅紫蓝 (#6366F1 / #4F46E5) 按钮与图标呼应
 * 2. DEEP_OCEAN (清冽海蓝): 浅色基调，清澈冰川冷蓝大底 + 纯白磨砂卡片 + 深海湛蓝 (#0284C7 / #0369A1) 按钮与图标呼应
 */
enum class ThemePalette(
    val id: String,
    val previewColor: Color,
    val labelProvider: (Strings) -> String
) {
    DAWN(
        id = "dawn",
        previewColor = Color(0xFFFAF4F6),
        labelProvider = { it.paletteDawn }
    ),
    DEEP_OCEAN(
        id = "deep_ocean",
        previewColor = Color(0xFFE0F2FE),
        labelProvider = { it.paletteDeepOcean }
    );

    companion object {
        fun fromId(id: String?): ThemePalette {
            if (id.isNullOrBlank()) return DAWN
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DAWN
        }
    }
}

/**
 * 调色板工厂，负责构建包含全部 29 个 MD3 语义角色的完整 ColorScheme
 */
object ThemePalettes {

    // MD3 标准通用 Error 角色
    private val LightError = Color(0xFFB91C1C)
    private val LightOnError = Color(0xFFFFFFFF)
    private val LightErrorContainer = Color(0xFFFEE2E2)
    private val LightOnErrorContainer = Color(0xFF7F1D1D)

    private val DarkError = Color(0xFFEF4444)
    private val DarkOnError = Color(0xFF450A0A)
    private val DarkErrorContainer = Color(0xFF7F1D1D)
    private val DarkOnErrorContainer = Color(0xFFFECACA)

    fun getColorScheme(palette: ThemePalette = ThemePalette.DAWN, isDark: Boolean): ColorScheme {
        return if (isDark) getDarkColorScheme(palette) else getLightColorScheme(palette)
    }

    private fun getLightColorScheme(palette: ThemePalette): ColorScheme {
        return when (palette) {
            ThemePalette.DAWN -> lightColorScheme(
                primary = Color(0xFF4F46E5),          // 优雅紫靛蓝 (Indigo-600 / 呼应晨曦粉紫大底)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFEEF2FF), // 极淡通透紫蓝高光底
                onPrimaryContainer = Color(0xFF3730A3), // 深紫蓝文本
                inversePrimary = Color(0xFFA5B4FC),
                secondary = Color(0xFF475569),        // Slate-600
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF1F5F9), // Slate-100
                onSecondaryContainer = Color(0xFF19191C),
                tertiary = Color(0xFFF43F5E),         // 珊瑚暖桃点睛色 (Rose-500)
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFFE4E6),
                onTertiaryContainer = Color(0xFF9F1239),
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
            ThemePalette.DEEP_OCEAN -> lightColorScheme(
                primary = Color(0xFF0284C7),          // 沉稳深海科技蓝 (Sky-600 / 呼应冰川冷蓝大底)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE0F2FE), // 极淡冰川湛蓝高光底
                onPrimaryContainer = Color(0xFF0369A1), // 沉稳深蓝文字
                inversePrimary = Color(0xFF38BDF8),
                secondary = Color(0xFF334155),        // Slate-700
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF0F6FA), // 浅蓝微灰底
                onSecondaryContainer = Color(0xFF0F172A),
                tertiary = Color(0xFF0D9488),         // 沉稳暗墨青 (Teal-600)
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFCCFBF1),
                onTertiaryContainer = Color(0xFF115E59),
                background = Color(0xFFF0F6FA),       // 极净冰川微蓝浅色底板
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFF0F6FA),
                onSurface = Color(0xFF0F172A),        // 深海炭黑正文 (16:1 对比度)
                surfaceVariant = Color(0xFFE6EFF7),   // 冰川微蓝子面板
                onSurfaceVariant = Color(0xFF475569), // 蓝灰说明字
                surfaceContainerLowest = Color(0xFFFFFFFF), // 纯白高光外壳
                surfaceContainerLow = Color(0xFFF0F6FA),
                surfaceContainer = Color(0xFFFFFFFF),      // 纯白半透明卡片
                surfaceContainerHigh = Color(0xFFE6EFF7),
                surfaceContainerHighest = Color(0xFFCBD5E1),
                surfaceDim = Color(0xFFE2E8F0),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF0F172A),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF94A3B8),
                outlineVariant = Color(0xFFD8E4F0),
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
        }
    }

    private fun getDarkColorScheme(palette: ThemePalette): ColorScheme {
        return when (palette) {
            ThemePalette.DAWN -> darkColorScheme(
                primary = Color(0xFFA5B4FC),          // 柔和紫蓝
                onPrimary = Color(0xFF1E1B4B),
                primaryContainer = Color(0xFF312E81),
                onPrimaryContainer = Color(0xFFEEF2FF),
                inversePrimary = Color(0xFF4F46E5),
                secondary = Color(0xFF94A3B8),
                onSecondary = Color(0xFF0F172A),
                secondaryContainer = Color(0xFF1E293B),
                onSecondaryContainer = Color(0xFFE2E8F0),
                tertiary = Color(0xFFFDA4AF),
                onTertiary = Color(0xFF4C0519),
                tertiaryContainer = Color(0xFF881337),
                onTertiaryContainer = Color(0xFFFFE4E6),
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
            ThemePalette.DEEP_OCEAN -> darkColorScheme(
                primary = Color(0xFF38BDF8),          // 灵动星空湛蓝
                onPrimary = Color(0xFF082F49),
                primaryContainer = Color(0xFF075985), // 深邃墨蓝底
                onPrimaryContainer = Color(0xFFE0F2FE),
                inversePrimary = Color(0xFF0284C7),
                secondary = Color(0xFF94A3B8),        // Slate-400
                onSecondary = Color(0xFF082F49),
                secondaryContainer = Color(0xFF1E293B),
                onSecondaryContainer = Color(0xFFE2E8F0),
                tertiary = Color(0xFF2DD4BF),         // 暗青极光
                onTertiary = Color(0xFF042F2E),
                tertiaryContainer = Color(0xFF115E59),
                onTertiaryContainer = Color(0xFFCCFBF1),
                background = Color(0xFF0B132B),       // 深邃冷夜大底
                onBackground = Color(0xFFF8FAFC),
                surface = Color(0xFF0B132B),
                onSurface = Color(0xFFF8FAFC),        // 柔和冰川白
                surfaceVariant = Color(0xFF1C2541),   // 深夜墨蓝
                onSurfaceVariant = Color(0xFF94A3B8), // 石板灰
                surfaceContainerLowest = Color(0xFF070B19),
                surfaceContainerLow = Color(0xFF0E172F),
                surfaceContainer = Color(0xFF141F3D),  // 墨蓝半透明卡片
                surfaceContainerHigh = Color(0xFF1C2B54),
                surfaceContainerHighest = Color(0xFF25376B),
                surfaceDim = Color(0xFF0B132B),
                surfaceBright = Color(0xFF1E2D5A),
                inverseSurface = Color(0xFFF8FAFC),
                inverseOnSurface = Color(0xFF0B132B),
                outline = Color(0xFF38BDF8).copy(alpha = 0.40f),
                outlineVariant = Color(0xFF1C2541),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
        }
    }
}
