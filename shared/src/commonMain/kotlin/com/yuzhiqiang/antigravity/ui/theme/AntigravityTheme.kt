package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =========================================================================
// Material Design 3 调色板 (Light Theme - 高对比度现代科技质感)
// =========================================================================
private val LightPrimary = Color(0xFF4F46E5)              // Indigo 600
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFEEF2FF)     // Indigo 50
private val LightOnPrimaryContainer = Color(0xFF312E81)   // Indigo 900

private val LightSecondary = Color(0xFF0D9488)            // Teal 600
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFCCFBF1)   // Teal 50
private val LightOnSecondaryContainer = Color(0xFF134E4A) // Teal 900

private val LightTertiary = Color(0xFF7C3AED)             // Violet 600
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF3E8FF)    // Violet 50
private val LightOnTertiaryContainer = Color(0xFF4C1D95)  // Violet 900

private val LightBackground = Color(0xFFF8FAFC)           // Slate 50
private val LightOnBackground = Color(0xFF0F172A)         // Slate 900
private val LightSurface = Color(0xFFFFFFFF)              // Pure White
private val LightOnSurface = Color(0xFF0F172A)            // Slate 900 (深邃饱满)
private val LightSurfaceVariant = Color(0xFFF1F5F9)       // Slate 100
private val LightOnSurfaceVariant = Color(0xFF475569)     // Slate 600 (高对比度副文本)
private val LightOutline = Color(0xFFCBD5E1)              // Slate 300
private val LightOutlineVariant = Color(0xFFE2E8F0)       // Slate 200

private val LightError = Color(0xFFDC2626)                // Red 600
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFEE2E2)       // Red 50
private val LightOnErrorContainer = Color(0xFF7F1D1D)     // Red 900

// =========================================================================
// Material Design 3 调色板 (Dark Theme - 深邃透气暗夜质感)
// =========================================================================
private val DarkPrimary = Color(0xFF818CF8)               // Indigo 400
private val DarkOnPrimary = Color(0xFF1E1B4B)
private val DarkPrimaryContainer = Color(0xFF312E81)      // Indigo 900
private val DarkOnPrimaryContainer = Color(0xFFE0E7FF)    // Indigo 100

private val DarkSecondary = Color(0xFF2DD4BF)             // Teal 400
private val DarkOnSecondary = Color(0xFF042F2E)
private val DarkSecondaryContainer = Color(0xFF134E4A)
private val DarkOnSecondaryContainer = Color(0xFFCCFBF1)

private val DarkTertiary = Color(0xFFA78BFA)              // Violet 400
private val DarkOnTertiary = Color(0xFF2E1065)
private val DarkTertiaryContainer = Color(0xFF4C1D95)
private val DarkOnTertiaryContainer = Color(0xFFF3E8FF)

private val DarkBackground = Color(0xFF090D16)            // Deep Obsidian
private val DarkOnBackground = Color(0xFFF8FAFC)          // Slate 50
private val DarkSurface = Color(0xFF111827)               // Slate 900
private val DarkOnSurface = Color(0xFFF8FAFC)             // Slate 50
private val DarkSurfaceVariant = Color(0xFF1E293B)        // Slate 800
private val DarkOnSurfaceVariant = Color(0xFF94A3B8)      // Slate 400
private val DarkOutline = Color(0xFF334155)               // Slate 700
private val DarkOutlineVariant = Color(0xFF1E293B)        // Slate 800

private val DarkError = Color(0xFFF87171)
private val DarkOnError = Color(0xFF450A0A)
private val DarkErrorContainer = Color(0xFF7F1D1D)
private val DarkOnErrorContainer = Color(0xFFFECACA)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

// =========================================================================
// Material Design 3 官方标准字阶 (Typography Scale)
// =========================================================================
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppTokens.Radius.xs),
    small = RoundedCornerShape(AppTokens.Radius.small),
    medium = RoundedCornerShape(AppTokens.Radius.medium),
    large = RoundedCornerShape(AppTokens.Radius.large),
    extraLarge = RoundedCornerShape(AppTokens.Radius.dialog)
)

@Composable
fun AntigravityTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val statusColors = if (isDark) AppTokens.darkStatusColors else AppTokens.lightStatusColors

    CompositionLocalProvider(LocalAppStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
