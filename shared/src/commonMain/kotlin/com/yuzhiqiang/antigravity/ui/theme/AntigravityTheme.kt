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

private val DarkBg = Color(0xFF0B0F19)
private val DarkSurface = Color(0xFF131B2E)
private val DarkSurfaceElevated = Color(0xFF172136)
private val DarkSurfaceBorder = Color(0x33475569)
private val DarkTextPrimary = Color(0xFFF8FAFC)
private val DarkTextSecondary = Color(0xFF94A3B8)
private val DarkAccent = Color(0xFF60A5FA)
private val DarkAccentHover = Color(0xFF93C5FD)
private val DarkAccentSoft = Color(0x333B82F6)

private val LightBg = Color(0xFFF7F9FC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceElevated = Color(0xFFF3F6FA)
private val LightSurfaceBorder = Color(0xFFE2E8F0)
private val LightTextPrimary = Color(0xFF172033)
private val LightTextSecondary = Color(0xFF64748B)
private val LightAccent = Color(0xFF2563EB)
private val LightAccentHover = Color(0xFF1D4ED8)
private val LightAccentSoft = Color(0xFFEFF6FF)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF10213F),
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkAccentHover,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSurfaceBorder,
    outlineVariant = Color(0x335E7188),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightAccentSoft,
    onPrimaryContainer = LightAccentHover,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSurfaceBorder,
    outlineVariant = Color(0xFFE8EDF4),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(AppTokens.Radius.small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(AppTokens.Radius.medium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(AppTokens.Radius.large)
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
