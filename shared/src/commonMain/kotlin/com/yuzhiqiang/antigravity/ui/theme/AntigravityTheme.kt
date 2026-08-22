package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =========================================================================
// Light Theme Palette (Material Design 3 精致现代浅色调色板)
// =========================================================================
private val LightPrimary = Color(0xFF4F46E5)              // 沉稳现代的 Indigo 主色
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFEEF2FF)     // 柔和 Indigo 背景
private val LightOnPrimaryContainer = Color(0xFF3730A3)

private val LightSecondary = Color(0xFF0D9488)            // 优雅 Teal 辅助色
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFCCFBF1)
private val LightOnSecondaryContainer = Color(0xFF115E59)

private val LightTertiary = Color(0xFF7C3AED)             // 灵动 Violet 第三色
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF3E8FF)
private val LightOnTertiaryContainer = Color(0xFF581C87)

private val LightBackground = Color(0xFFF8FAFC)           // Slate 50 柔和背景
private val LightOnBackground = Color(0xFF0F172A)         // Slate 900
private val LightSurface = Color(0xFFFFFFFF)              // 纯白卡片表面
private val LightOnSurface = Color(0xFF0F172A)            // Slate 900
private val LightSurfaceVariant = Color(0xFFF1F5F9)       // Slate 100 浅灰微垫底
private val LightOnSurfaceVariant = Color(0xFF64748B)     // Slate 500 副文本
private val LightOutline = Color(0xFFCBD5E1)              // Slate 300 边框
private val LightOutlineVariant = Color(0xFFE2E8F0)       // Slate 200 弱分割线

private val LightError = Color(0xFFDC2626)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFEE2E2)
private val LightOnErrorContainer = Color(0xFF7F1D1D)

// =========================================================================
// Dark Theme Palette (Material Design 3 深邃透气暗色调色板)
// =========================================================================
private val DarkPrimary = Color(0xFF818CF8)               // 柔和 Indigo 400
private val DarkOnPrimary = Color(0xFF1E1B4B)
private val DarkPrimaryContainer = Color(0xFF312E81)
private val DarkOnPrimaryContainer = Color(0xFFE0E7FF)

private val DarkSecondary = Color(0xFF2DD4BF)             // Teal 400
private val DarkOnSecondary = Color(0xFF042F2E)
private val DarkSecondaryContainer = Color(0xFF134E4A)
private val DarkOnSecondaryContainer = Color(0xFF99F6E4)

private val DarkTertiary = Color(0xFFA78BFA)              // Violet 400
private val DarkOnTertiary = Color(0xFF2E1065)
private val DarkTertiaryContainer = Color(0xFF4C1D95)
private val DarkOnTertiaryContainer = Color(0xFFEDE9FE)

private val DarkBackground = Color(0xFF090D16)            // 深度深空黑背景
private val DarkOnBackground = Color(0xFFF8FAFC)          // Slate 50
private val DarkSurface = Color(0xFF111827)               // Slate 900 卡片表面
private val DarkOnSurface = Color(0xFFF8FAFC)             // Slate 50
private val DarkSurfaceVariant = Color(0xFF1E293B)        // Slate 800 抬升表面
private val DarkOnSurfaceVariant = Color(0xFF94A3B8)      // Slate 400 副文本
private val DarkOutline = Color(0xFF334155)               // Slate 700 边框
private val DarkOutlineVariant = Color(0xFF1E293B)        // Slate 800 弱分割线

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

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
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
