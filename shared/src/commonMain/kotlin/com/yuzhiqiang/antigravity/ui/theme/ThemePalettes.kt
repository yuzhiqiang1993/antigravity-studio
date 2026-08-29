package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuzhiqiang.antigravity.i18n.Strings

/**
 * 现代核心主题调色板全集 (赤橙黄绿青蓝紫 + 极简纯白)
 * 严格遵循 Material Design 3 规范、暗色调（Dark-Toned）克制原则与现代 Tinted Tonal Surface（色调微浸润卡片）体系
 */
enum class ThemePalette(
    val id: String,
    val previewColor: Color,
    val labelProvider: (Strings) -> String
) {
    WHITE(
        id = "white",
        previewColor = Color(0xFF0284C7),
        labelProvider = { it.paletteWhite }
    ),
    RED(
        id = "red",
        previewColor = Color(0xFFE11D48),
        labelProvider = { it.paletteRed }
    ),
    ORANGE(
        id = "orange",
        previewColor = Color(0xFFEA580C),
        labelProvider = { it.paletteOrange }
    ),
    AMBER(
        id = "amber",
        previewColor = Color(0xFFD97706),
        labelProvider = { it.paletteAmber }
    ),
    GREEN(
        id = "green",
        previewColor = Color(0xFF059669),
        labelProvider = { it.paletteGreen }
    ),
    TEAL(
        id = "teal",
        previewColor = Color(0xFF0D9488),
        labelProvider = { it.paletteTeal }
    ),
    BLUE(
        id = "blue",
        previewColor = Color(0xFF0284C7),
        labelProvider = { it.paletteBlue }
    ),
    PURPLE(
        id = "purple",
        previewColor = Color(0xFF6366F1),
        labelProvider = { it.palettePurple }
    );

    companion object {
        fun fromId(id: String?): ThemePalette {
            if (id.isNullOrBlank()) return WHITE
            return when (id.lowercase()) {
                "white" -> WHITE
                "red", "rose" -> RED
                "orange" -> ORANGE
                "amber", "yellow" -> AMBER
                "green", "emerald" -> GREEN
                "teal", "cyan" -> TEAL
                "blue", "deep_ocean", "ocean" -> BLUE
                "purple", "dawn", "violet", "indigo" -> PURPLE
                else -> entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WHITE
            }
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

    fun getColorScheme(palette: ThemePalette = ThemePalette.WHITE, isDark: Boolean): ColorScheme {
        return if (isDark) getDarkColorScheme(palette) else getLightColorScheme(palette)
    }

    private fun getLightColorScheme(palette: ThemePalette): ColorScheme {
        return when (palette) {
            ThemePalette.WHITE -> lightColorScheme(
                primary = Color(0xFF0284C7),          // 沉稳深海科技蓝 (纯白底板上的经典科技蓝点睛)
                onPrimary = Color(0xFFFFFFFF),        // 纯白文字
                primaryContainer = Color(0xFFE0F2FE), // 极淡通透浅冰蓝高光底
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
                background = Color(0xFFFFFFFF),       // 100% 极致纯白底板 (Pure Crisp White)
                onBackground = Color(0xFF19191C),
                surface = Color(0xFFFFFFFF),          // 100% 极致纯白
                onSurface = Color(0xFF19191C),        // 深邃炭黑文本 (16:1 锐利清晰度)
                surfaceVariant = Color(0xFFF1F5F9),   // Slate-100
                onSurfaceVariant = Color(0xFF64748B), // 中性石板灰
                surfaceContainerLowest = Color(0xFFFFFFFF), // 纯白高光外壳
                surfaceContainerLow = Color(0xFFF8FAFC),   // 顶栏微灰浮岛
                surfaceContainer = Color(0xFFF8FAFC),      // 极净微灰卡片浮岛 (与纯白底形成清晰轮廓反差)
                surfaceContainerHigh = Color(0xFFFFFFFF),  // 卡片内嵌纯白高光块
                surfaceContainerHighest = Color(0xFFE2E8F0),
                surfaceDim = Color(0xFFE2E8F0),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF19191C),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF94A3B8),
                outlineVariant = Color(0xFFE2E8F0),   // 1px 精致轮廓边框
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.RED -> lightColorScheme(
                primary = Color(0xFFBE123C),          // 沉稳暗绯红 (Rose-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE4E6), // 极淡初桃粉底
                onPrimaryContainer = Color(0xFF9F1239),
                inversePrimary = Color(0xFFFDA4AF),
                secondary = Color(0xFF4C0519),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFF1F2),
                onSecondaryContainer = Color(0xFF1C1917),
                tertiary = Color(0xFFE11D48),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFFE4E6),
                onTertiaryContainer = Color(0xFF881337),
                background = Color(0xFFFBF4F5),       // 初雪微白大底
                onBackground = Color(0xFF1C1917),
                surface = Color(0xFFFBF4F5),
                onSurface = Color(0xFF1C1917),        // 深炭黑正文
                surfaceVariant = Color(0xFFF7EBEF),
                onSurfaceVariant = Color(0xFF57534E),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFFBF4F5),
                surfaceContainer = Color(0xFFFAF2F4),      // 极淡初桃胭粉白卡片 (呼应赤红主题)
                surfaceContainerHigh = Color(0xFFF7EBEF),
                surfaceContainerHighest = Color(0xFFE5D5DA),
                surfaceDim = Color(0xFFE5D5DA),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF1C1917),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFFA8A29E),
                outlineVariant = Color(0xFFF2DDE2),   // 柔和初桃轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.ORANGE -> lightColorScheme(
                primary = Color(0xFFC2410C),          // 沉稳落日焦橙 (Orange-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFEDD5), // 暖橙微底
                onPrimaryContainer = Color(0xFF7C2D12),
                inversePrimary = Color(0xFFFDBA74),
                secondary = Color(0xFF431407),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFF7ED),
                onSecondaryContainer = Color(0xFF1C1917),
                tertiary = Color(0xFFEA580C),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFFEDD5),
                onTertiaryContainer = Color(0xFF9A3412),
                background = Color(0xFFFAF5F0),       // 暖杏微白大底
                onBackground = Color(0xFF1C1917),
                surface = Color(0xFFFAF5F0),
                onSurface = Color(0xFF1C1917),
                surfaceVariant = Color(0xFFF5ECE4),
                onSurfaceVariant = Color(0xFF57534E),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFFAF5F0),
                surfaceContainer = Color(0xFFFAF4EE),      // 极淡落日暖杏白卡片 (呼应焦橙主题)
                surfaceContainerHigh = Color(0xFFF5ECE4),
                surfaceContainerHighest = Color(0xFFE5D8CD),
                surfaceDim = Color(0xFFE5D8CD),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF1C1917),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFFA8A29E),
                outlineVariant = Color(0xFFF0E3D5),   // 柔和暖杏轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.AMBER -> lightColorScheme(
                primary = Color(0xFFB45309),          // 沉稳暮光金珀 (Amber-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFEF3C7), // 杏金微底
                onPrimaryContainer = Color(0xFF78350F),
                inversePrimary = Color(0xFFFCD34D),
                secondary = Color(0xFF451A03),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFFBEB),
                onSecondaryContainer = Color(0xFF1C1917),
                tertiary = Color(0xFFD97706),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFEF3C7),
                onTertiaryContainer = Color(0xFF92400E),
                background = Color(0xFFFAF6F0),       // 暖象牙白大底
                onBackground = Color(0xFF1C1917),
                surface = Color(0xFFFAF6F0),
                onSurface = Color(0xFF1C1917),
                surfaceVariant = Color(0xFFF5EEE3),
                onSurfaceVariant = Color(0xFF57534E),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFFAF6F0),
                surfaceContainer = Color(0xFFFAF5EE),      // 极淡暮光金珀白卡片 (呼应金珀主题)
                surfaceContainerHigh = Color(0xFFF5EEE3),
                surfaceContainerHighest = Color(0xFFE5DACB),
                surfaceDim = Color(0xFFE5DACB),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF1C1917),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFFA8A29E),
                outlineVariant = Color(0xFFF0E5D5),   // 柔和金珀轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.GREEN -> lightColorScheme(
                primary = Color(0xFF047857),          // 沉稳青岚翠绿 (Emerald-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD1FAE5), // 薄荷微底
                onPrimaryContainer = Color(0xFF065F46),
                inversePrimary = Color(0xFF6EE7B7),
                secondary = Color(0xFF064E3B),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFECFDF5),
                onSecondaryContainer = Color(0xFF064E3B),
                tertiary = Color(0xFF059669),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFD1FAE5),
                onTertiaryContainer = Color(0xFF064E3B),
                background = Color(0xFFF2F8F5),       // 浅玉冷白大底
                onBackground = Color(0xFF064E3B),
                surface = Color(0xFFF2F8F5),
                onSurface = Color(0xFF064E3B),
                surfaceVariant = Color(0xFFE4F0EB),
                onSurfaceVariant = Color(0xFF335C4D),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF2F8F5),
                surfaceContainer = Color(0xFFF2F9F5),      // 极淡初荷浅翠白卡片 (呼应翠绿主题)
                surfaceContainerHigh = Color(0xFFE4F0EB),
                surfaceContainerHighest = Color(0xFFCDDFD7),
                surfaceDim = Color(0xFFCDDFD7),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF064E3B),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF6EE7B7).copy(alpha = 0.5f),
                outlineVariant = Color(0xFFD6EAE0),   // 柔和薄荷轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.TEAL -> lightColorScheme(
                primary = Color(0xFF0F766E),          // 沉稳碧水苍青 (Teal-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFCCFBF1), // 苍青微底
                onPrimaryContainer = Color(0xFF115E59),
                inversePrimary = Color(0xFF5EEAD4),
                secondary = Color(0xFF134E4A),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF0FDFA),
                onSecondaryContainer = Color(0xFF134E4A),
                tertiary = Color(0xFF0D9488),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFCCFBF1),
                onTertiaryContainer = Color(0xFF134E4A),
                background = Color(0xFFF0F8F8),       // 苍青极净大底
                onBackground = Color(0xFF134E4A),
                surface = Color(0xFFF0F8F8),
                onSurface = Color(0xFF134E4A),
                surfaceVariant = Color(0xFFE1F0F0),
                onSurfaceVariant = Color(0xFF2D5755),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF0F8F8),
                surfaceContainer = Color(0xFFF0F8F7),      // 极淡青瓷苍碧白卡片 (呼应苍青主题)
                surfaceContainerHigh = Color(0xFFE1F0F0),
                surfaceContainerHighest = Color(0xFFC7DFDF),
                surfaceDim = Color(0xFFC7DFDF),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF134E4A),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF5EEAD4).copy(alpha = 0.5f),
                outlineVariant = Color(0xFFD3EBEA),   // 柔和苍青轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.BLUE -> lightColorScheme(
                primary = Color(0xFF0284C7),          // 沉稳清冽海蓝 (Sky-700)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE0F2FE), // 冰川湛蓝高光底
                onPrimaryContainer = Color(0xFF0369A1),
                inversePrimary = Color(0xFF38BDF8),
                secondary = Color(0xFF334155),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF0F6FA),
                onSecondaryContainer = Color(0xFF0F172A),
                tertiary = Color(0xFF0D9488),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFCCFBF1),
                onTertiaryContainer = Color(0xFF115E59),
                background = Color(0xFFF0F6FA),       // 冰川冷白大底
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFF0F6FA),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFE6EFF7),
                onSurfaceVariant = Color(0xFF475569),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF0F6FA),
                surfaceContainer = Color(0xFFF0F6FA),      // 极淡冰川湛蓝白卡片 (呼应海蓝主题)
                surfaceContainerHigh = Color(0xFFE6EFF7),
                surfaceContainerHighest = Color(0xFFCBD5E1),
                surfaceDim = Color(0xFFE2E8F0),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF0F172A),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF94A3B8),
                outlineVariant = Color(0xFFD4E5F2),   // 柔和冰川轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
            ThemePalette.PURPLE -> lightColorScheme(
                primary = Color(0xFF4F46E5),          // 优雅晨曦粉紫 (Indigo-600)
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFEEF2FF), // 紫蓝高光底
                onPrimaryContainer = Color(0xFF3730A3),
                inversePrimary = Color(0xFFA5B4FC),
                secondary = Color(0xFF475569),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF1F5F9),
                onSecondaryContainer = Color(0xFF19191C),
                tertiary = Color(0xFFF43F5E),         // 珊瑚暖桃点睛色
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFFE4E6),
                onTertiaryContainer = Color(0xFF9F1239),
                background = Color(0xFFFAF4F6),       // 晨曦暖白大底
                onBackground = Color(0xFF19191C),
                surface = Color(0xFFFAF4F6),
                onSurface = Color(0xFF19191C),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFFAF4F6),
                surfaceContainer = Color(0xFFF5F3FA),      // 极淡晨曦粉紫白卡片 (呼应粉紫主题)
                surfaceContainerHigh = Color(0xFFF1F5F9),
                surfaceContainerHighest = Color(0xFFE2E8F0),
                surfaceDim = Color(0xFFE2E8F0),
                surfaceBright = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFF19191C),
                inverseOnSurface = Color(0xFFF8FAFC),
                outline = Color(0xFF94A3B8),
                outlineVariant = Color(0xFFE2DEF2),   // 柔和粉紫轮廓
                error = LightError,
                onError = LightOnError,
                errorContainer = LightErrorContainer,
                onErrorContainer = LightOnErrorContainer
            )
        }
    }

    private fun getDarkColorScheme(palette: ThemePalette): ColorScheme {
        return when (palette) {
            ThemePalette.WHITE -> darkColorScheme(
                primary = Color(0xFF38BDF8),
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
                background = Color(0xFF090D16),
                onBackground = Color(0xFFF8FAFC),
                surface = Color(0xFF090D16),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF1E293B),
                onSurfaceVariant = Color(0xFF94A3B8),
                surfaceContainerLowest = Color(0xFF05080F),
                surfaceContainerLow = Color(0xFF0D131F),
                surfaceContainer = Color(0xFF111827),
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
            ThemePalette.RED -> darkColorScheme(
                primary = Color(0xFFFDA4AF),
                onPrimary = Color(0xFF4C0519),
                primaryContainer = Color(0xFF881337),
                onPrimaryContainer = Color(0xFFFFE4E6),
                background = Color(0xFF14080B),
                surface = Color(0xFF14080B),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF281116),
                onSurfaceVariant = Color(0xFFD4A5B0),
                surfaceContainer = Color(0xFF1A0D11),
                surfaceContainerHigh = Color(0xFF281116),
                outline = Color(0xFFFDA4AF).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF281116),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.ORANGE -> darkColorScheme(
                primary = Color(0xFFFDBA74),
                onPrimary = Color(0xFF431407),
                primaryContainer = Color(0xFF9A3412),
                onPrimaryContainer = Color(0xFFFFEDD5),
                background = Color(0xFF140B06),
                surface = Color(0xFF140B06),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF28160E),
                onSurfaceVariant = Color(0xFFD4B19F),
                surfaceContainer = Color(0xFF1B0F09),
                surfaceContainerHigh = Color(0xFF28160E),
                outline = Color(0xFFFDBA74).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF28160E),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.AMBER -> darkColorScheme(
                primary = Color(0xFFFCD34D),
                onPrimary = Color(0xFF451A03),
                primaryContainer = Color(0xFF92400E),
                onPrimaryContainer = Color(0xFFFEF3C7),
                background = Color(0xFF140F06),
                surface = Color(0xFF140F06),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF281D0E),
                onSurfaceVariant = Color(0xFFD4BF9F),
                surfaceContainer = Color(0xFF1C1409),
                surfaceContainerHigh = Color(0xFF281D0E),
                outline = Color(0xFFFCD34D).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF281D0E),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.GREEN -> darkColorScheme(
                primary = Color(0xFF6EE7B7),
                onPrimary = Color(0xFF022C22),
                primaryContainer = Color(0xFF065F46),
                onPrimaryContainer = Color(0xFFD1FAE5),
                background = Color(0xFF06140D),
                surface = Color(0xFF06140D),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF0F281B),
                onSurfaceVariant = Color(0xFF9FD4BC),
                surfaceContainer = Color(0xFF091C12),
                surfaceContainerHigh = Color(0xFF0F281B),
                outline = Color(0xFF6EE7B7).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF0F281B),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.TEAL -> darkColorScheme(
                primary = Color(0xFF5EEAD4),
                onPrimary = Color(0xFF042F2E),
                primaryContainer = Color(0xFF115E59),
                onPrimaryContainer = Color(0xFFCCFBF1),
                background = Color(0xFF061414),
                surface = Color(0xFF061414),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF0F2828),
                onSurfaceVariant = Color(0xFF9FD4D4),
                surfaceContainer = Color(0xFF091C1C),
                surfaceContainerHigh = Color(0xFF0F2828),
                outline = Color(0xFF5EEAD4).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF0F2828),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.BLUE -> darkColorScheme(
                primary = Color(0xFF38BDF8),
                onPrimary = Color(0xFF082F49),
                primaryContainer = Color(0xFF075985),
                onPrimaryContainer = Color(0xFFE0F2FE),
                background = Color(0xFF0B132B),
                surface = Color(0xFF0B132B),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF1C2541),
                onSurfaceVariant = Color(0xFF94A3B8),
                surfaceContainer = Color(0xFF141F3D),
                surfaceContainerHigh = Color(0xFF1C2B54),
                outline = Color(0xFF38BDF8).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF1C2541),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
            ThemePalette.PURPLE -> darkColorScheme(
                primary = Color(0xFFA5B4FC),
                onPrimary = Color(0xFF1E1B4B),
                primaryContainer = Color(0xFF312E81),
                onPrimaryContainer = Color(0xFFEEF2FF),
                background = Color(0xFF0D0A1A),
                surface = Color(0xFF0D0A1A),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF1C1733),
                onSurfaceVariant = Color(0xFFAAA3D4),
                surfaceContainer = Color(0xFF141026),
                surfaceContainerHigh = Color(0xFF1C1733),
                outline = Color(0xFFA5B4FC).copy(alpha = 0.4f),
                outlineVariant = Color(0xFF1C1733),
                error = DarkError,
                onError = DarkOnError,
                errorContainer = DarkErrorContainer,
                onErrorContainer = DarkOnErrorContainer
            )
        }
    }
}
