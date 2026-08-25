package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuzhiqiang.antigravity.i18n.Strings

/**
 * Material Design 3 预设调色板定义
 */
enum class ThemePalette(
    val id: String,
    val previewColor: Color,
    val labelProvider: (Strings) -> String
) {
    INDIGO(
        id = "indigo",
        previewColor = Color(0xFF4F46E5),
        labelProvider = { it.paletteIndigo }
    ),
    OCEAN(
        id = "ocean",
        previewColor = Color(0xFF0284C7),
        labelProvider = { it.paletteOcean }
    ),
    EMERALD(
        id = "emerald",
        previewColor = Color(0xFF059669),
        labelProvider = { it.paletteEmerald }
    ),
    VIOLET(
        id = "violet",
        previewColor = Color(0xFF7C3AED),
        labelProvider = { it.paletteViolet }
    ),
    ROSE(
        id = "rose",
        previewColor = Color(0xFFE11D48),
        labelProvider = { it.paletteRose }
    ),
    AMBER(
        id = "amber",
        previewColor = Color(0xFFD97706),
        labelProvider = { it.paletteAmber }
    );

    companion object {
        fun fromId(id: String?): ThemePalette {
            if (id.isNullOrBlank()) return INDIGO
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: INDIGO
        }
    }
}

/**
 * 调色板工厂，负责构建包含全部 M3 语义角色的完整 ColorScheme
 */
object ThemePalettes {

    // 通用中性背景与表面基底 (Light)
    private val LightBackground = Color(0xFFF8FAFC)
    private val LightOnBackground = Color(0xFF0F172A)
    private val LightSurface = Color(0xFFFFFFFF)
    private val LightOnSurface = Color(0xFF0F172A)
    private val LightSurfaceVariant = Color(0xFFF1F5F9)
    private val LightOnSurfaceVariant = Color(0xFF475569)
    private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    private val LightSurfaceContainerLow = Color(0xFFF8FAFC)
    private val LightSurfaceContainer = Color(0xFFF1F5F9)
    private val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
    private val LightSurfaceContainerHighest = Color(0xFFCBD5E1)
    private val LightSurfaceDim = Color(0xFFE2E8F0)
    private val LightSurfaceBright = Color(0xFFFFFFFF)
    private val LightInverseSurface = Color(0xFF1E293B)
    private val LightInverseOnSurface = Color(0xFFF8FAFC)
    private val LightOutline = Color(0xFFCBD5E1)
    private val LightOutlineVariant = Color(0xFFE2E8F0)
    private val LightError = Color(0xFFDC2626)
    private val LightOnError = Color(0xFFFFFFFF)
    private val LightErrorContainer = Color(0xFFFEE2E2)
    private val LightOnErrorContainer = Color(0xFF7F1D1D)

    // 通用中性背景与表面基底 (Dark)
    private val DarkBackground = Color(0xFF090D16)
    private val DarkOnBackground = Color(0xFFF8FAFC)
    private val DarkSurface = Color(0xFF111827)
    private val DarkOnSurface = Color(0xFFF8FAFC)
    private val DarkSurfaceVariant = Color(0xFF1E293B)
    private val DarkOnSurfaceVariant = Color(0xFF94A3B8)
    private val DarkSurfaceContainerLowest = Color(0xFF0D131F)
    private val DarkSurfaceContainerLow = Color(0xFF111827)
    private val DarkSurfaceContainer = Color(0xFF1A2234)
    private val DarkSurfaceContainerHigh = Color(0xFF242E42)
    private val DarkSurfaceContainerHighest = Color(0xFF334155)
    private val DarkSurfaceDim = Color(0xFF0F172A)
    private val DarkSurfaceBright = Color(0xFF2A364F)
    private val DarkInverseSurface = Color(0xFFF8FAFC)
    private val DarkInverseOnSurface = Color(0xFF0F172A)
    private val DarkOutline = Color(0xFF334155)
    private val DarkOutlineVariant = Color(0xFF1E293B)
    private val DarkError = Color(0xFFF87171)
    private val DarkOnError = Color(0xFF450A0A)
    private val DarkErrorContainer = Color(0xFF7F1D1D)
    private val DarkOnErrorContainer = Color(0xFFFECACA)

    fun getColorScheme(palette: ThemePalette, isDark: Boolean): ColorScheme {
        return if (isDark) getDarkColorScheme(palette) else getLightColorScheme(palette)
    }

    private fun getLightColorScheme(palette: ThemePalette): ColorScheme {
        val (primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary) = when (palette) {
            ThemePalette.INDIGO -> Quintuple(Color(0xFF4F46E5), Color(0xFFFFFFFF), Color(0xFFEEF2FF), Color(0xFF312E81), Color(0xFF818CF8))
            ThemePalette.OCEAN -> Quintuple(Color(0xFF0284C7), Color(0xFFFFFFFF), Color(0xFFE0F2FE), Color(0xFF0369A1), Color(0xFF38BDF8))
            ThemePalette.EMERALD -> Quintuple(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5), Color(0xFF064E3B), Color(0xFF34D399))
            ThemePalette.VIOLET -> Quintuple(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFFF3E8FF), Color(0xFF4C1D95), Color(0xFFA78BFA))
            ThemePalette.ROSE -> Quintuple(Color(0xFFE11D48), Color(0xFFFFFFFF), Color(0xFFFFE4E6), Color(0xFF881337), Color(0xFFFB7185))
            ThemePalette.AMBER -> Quintuple(Color(0xFFD97706), Color(0xFFFFFFFF), Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFFFBBF24))
        }

        val (secondary, onSecondary, secondaryContainer, onSecondaryContainer) = when (palette) {
            ThemePalette.INDIGO -> Quadruple(Color(0xFF0D9488), Color(0xFFFFFFFF), Color(0xFFCCFBF1), Color(0xFF134E4A))
            ThemePalette.OCEAN -> Quadruple(Color(0xFF0D9488), Color(0xFFFFFFFF), Color(0xFFCCFBF1), Color(0xFF134E4A))
            ThemePalette.EMERALD -> Quadruple(Color(0xFF0D9488), Color(0xFFFFFFFF), Color(0xFFCCFBF1), Color(0xFF134E4A))
            ThemePalette.VIOLET -> Quadruple(Color(0xFFC026D3), Color(0xFFFFFFFF), Color(0xFFFAE8FF), Color(0xFF701A75))
            ThemePalette.ROSE -> Quadruple(Color(0xFFEA580C), Color(0xFFFFFFFF), Color(0xFFFFEDD5), Color(0xFF7C2D12))
            ThemePalette.AMBER -> Quadruple(Color(0xFFEA580C), Color(0xFFFFFFFF), Color(0xFFFFEDD5), Color(0xFF7C2D12))
        }

        val (tertiary, onTertiary, tertiaryContainer, onTertiaryContainer) = when (palette) {
            ThemePalette.INDIGO -> Quadruple(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFFF3E8FF), Color(0xFF4C1D95))
            ThemePalette.OCEAN -> Quadruple(Color(0xFF4F46E5), Color(0xFFFFFFFF), Color(0xFFEEF2FF), Color(0xFF312E81))
            ThemePalette.EMERALD -> Quadruple(Color(0xFF0284C7), Color(0xFFFFFFFF), Color(0xFFE0F2FE), Color(0xFF0369A1))
            ThemePalette.VIOLET -> Quadruple(Color(0xFF4F46E5), Color(0xFFFFFFFF), Color(0xFFEEF2FF), Color(0xFF312E81))
            ThemePalette.ROSE -> Quadruple(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFFF3E8FF), Color(0xFF4C1D95))
            ThemePalette.AMBER -> Quadruple(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5), Color(0xFF064E3B))
        }

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            surfaceDim = LightSurfaceDim,
            surfaceBright = LightSurfaceBright,
            inverseSurface = LightInverseSurface,
            inverseOnSurface = LightInverseOnSurface,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            error = LightError,
            onError = LightOnError,
            errorContainer = LightErrorContainer,
            onErrorContainer = LightOnErrorContainer
        )
    }

    private fun getDarkColorScheme(palette: ThemePalette): ColorScheme {
        val (primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary) = when (palette) {
            ThemePalette.INDIGO -> Quintuple(Color(0xFF818CF8), Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFFE0E7FF), Color(0xFF4F46E5))
            ThemePalette.OCEAN -> Quintuple(Color(0xFF38BDF8), Color(0xFF082F49), Color(0xFF0369A1), Color(0xFFE0F2FE), Color(0xFF0284C7))
            ThemePalette.EMERALD -> Quintuple(Color(0xFF34D399), Color(0xFF064E3B), Color(0xFF065F46), Color(0xFFD1FAE5), Color(0xFF059669))
            ThemePalette.VIOLET -> Quintuple(Color(0xFFA78BFA), Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFFF3E8FF), Color(0xFF7C3AED))
            ThemePalette.ROSE -> Quintuple(Color(0xFFFB7185), Color(0xFF4C0519), Color(0xFF881337), Color(0xFFFFE4E6), Color(0xFFE11D48))
            ThemePalette.AMBER -> Quintuple(Color(0xFFFBBF24), Color(0xFF451A03), Color(0xFF78350F), Color(0xFFFEF3C7), Color(0xFFD97706))
        }

        val (secondary, onSecondary, secondaryContainer, onSecondaryContainer) = when (palette) {
            ThemePalette.INDIGO -> Quadruple(Color(0xFF2DD4BF), Color(0xFF042F2E), Color(0xFF134E4A), Color(0xFFCCFBF1))
            ThemePalette.OCEAN -> Quadruple(Color(0xFF2DD4BF), Color(0xFF042F2E), Color(0xFF134E4A), Color(0xFFCCFBF1))
            ThemePalette.EMERALD -> Quadruple(Color(0xFF2DD4BF), Color(0xFF042F2E), Color(0xFF134E4A), Color(0xFFCCFBF1))
            ThemePalette.VIOLET -> Quadruple(Color(0xFFE879F9), Color(0xFF4A044E), Color(0xFF701A75), Color(0xFFFAE8FF))
            ThemePalette.ROSE -> Quadruple(Color(0xFFFB923C), Color(0xFF431407), Color(0xFF7C2D12), Color(0xFFFFEDD5))
            ThemePalette.AMBER -> Quadruple(Color(0xFFFB923C), Color(0xFF431407), Color(0xFF7C2D12), Color(0xFFFFEDD5))
        }

        val (tertiary, onTertiary, tertiaryContainer, onTertiaryContainer) = when (palette) {
            ThemePalette.INDIGO -> Quadruple(Color(0xFFA78BFA), Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFFF3E8FF))
            ThemePalette.OCEAN -> Quadruple(Color(0xFF818CF8), Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFFE0E7FF))
            ThemePalette.EMERALD -> Quadruple(Color(0xFF38BDF8), Color(0xFF082F49), Color(0xFF0369A1), Color(0xFFE0F2FE))
            ThemePalette.VIOLET -> Quadruple(Color(0xFF818CF8), Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFFE0E7FF))
            ThemePalette.ROSE -> Quadruple(Color(0xFFA78BFA), Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFFF3E8FF))
            ThemePalette.AMBER -> Quadruple(Color(0xFF34D399), Color(0xFF064E3B), Color(0xFF065F46), Color(0xFFD1FAE5))
        }

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            surfaceDim = DarkSurfaceDim,
            surfaceBright = DarkSurfaceBright,
            inverseSurface = DarkInverseSurface,
            inverseOnSurface = DarkInverseOnSurface,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            error = DarkError,
            onError = DarkOnError,
            errorContainer = DarkErrorContainer,
            onErrorContainer = DarkOnErrorContainer
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
