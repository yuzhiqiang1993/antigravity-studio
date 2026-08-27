package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Antigravity Studio 的跨平台视觉令牌（Material Design 3 标准）。
 * 页面和组件只消费语义令牌，避免在业务 UI 中散落随意颜色与尺寸常量。
 */
object AppTokens {

    object Motion {
        const val durationShort = 180
        const val durationMedium = 280
        const val durationLong = 400
        const val durationShimmer = 1200
        const val durationRotate = 800

        val standardEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val decelerateEasing = androidx.compose.animation.core.CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
        val accelerateEasing = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
        val fastOutSlowIn = androidx.compose.animation.core.FastOutSlowInEasing
    }


    object Spacing {
        val none = 0.dp
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val xxl = 48.dp

        // 语义化间距
        val pageHorizontal = 32.dp
        val pageVertical = 28.dp
        val pageSection = 24.dp
        val section = 16.dp
        val card = 20.dp
        val content = 12.dp
        val control = 8.dp
        val compact = 4.dp
    }

    object Size {
        val sidebarWidth = 224.dp
        val sidebarTopPadding = 48.dp
        val sidebarBottomPadding = 20.dp
        val navigationItemHeight = 44.dp
        val navigationItemIconSize = 20.dp

        val dialogWidth = 880.dp
        val dialogHeight = 650.dp
        val doctorDialogWidth = 720.dp
        val doctorDialogMaxHeight = 660.dp
        val singleModelDialogHeight = 540.dp
        val debugDialogWidth = 680.dp
        val debugDialogMinHeight = 360.dp
        val debugDialogMaxHeight = 640.dp

        val searchFieldWidth = 240.dp
        val modelSearchFieldWidth = 300.dp
        val presetGridMinWidth = 176.dp
        val presetGridHeight = 56.dp

        val controlHeight = 40.dp
        val fieldHeight = 56.dp
        val compactControlHeight = 34.dp

        val iconSmall = 14.dp
        val iconMedium = 18.dp
        val iconLarge = 24.dp
        val statusDot = 8.dp
        val debugCodePadding = 14.dp
        val brandMark = 52.dp
        val emptyStateHeight = 240.dp
    }

    object Radius {
        val none = 0.dp
        val xs = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val xl = 20.dp
        val dialog = 24.dp
        val pill = 999.dp
    }

    object Elevation {
        val level0 = 0.dp
        val level1 = 1.dp
        val level2 = 3.dp
        val level3 = 6.dp
        val level4 = 8.dp
        val level5 = 12.dp

        val card = 1.dp
        val floating = 4.dp
        val dialog = 16.dp
    }

    @Immutable
    data class StatusColors(
        val success: Color,
        val onSuccess: Color,
        val successContainer: Color,
        val onSuccessContainer: Color,
        val warning: Color,
        val onWarning: Color,
        val warningContainer: Color,
        val onWarningContainer: Color,
        val info: Color,
        val onInfo: Color,
        val infoContainer: Color,
        val onInfoContainer: Color,
        val error: Color,
        val onError: Color,
        val errorContainer: Color,
        val onErrorContainer: Color
    )

    val lightStatusColors = StatusColors(
        success = Color(0xFF16A34A),
        onSuccess = Color.White,
        successContainer = Color(0xFFDCFCE7),
        onSuccessContainer = Color(0xFF14532D),
        warning = Color(0xFFD97706),
        onWarning = Color.White,
        warningContainer = Color(0xFFFEF3C7),
        onWarningContainer = Color(0xFF78350F),
        info = Color(0xFF2563EB),
        onInfo = Color.White,
        infoContainer = Color(0xFFEFF6FF),
        onInfoContainer = Color(0xFF1E40AF),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D)
    )

    val darkStatusColors = StatusColors(
        success = Color(0xFF4ADE80),
        onSuccess = Color(0xFF052E16),
        successContainer = Color(0x3322C55E),
        onSuccessContainer = Color(0xFF86EFAC),
        warning = Color(0xFFFBBF24),
        onWarning = Color(0xFF451A03),
        warningContainer = Color(0x33F59E0B),
        onWarningContainer = Color(0xFFFDE68A),
        info = Color(0xFF60A5FA),
        onInfo = Color(0xFF172554),
        infoContainer = Color(0x333B82F6),
        onInfoContainer = Color(0xFFBFDBFE),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0x33EF4444),
        onErrorContainer = Color(0xFFFECACA)
    )

    @Immutable
    data class FeatureStyle(
        val foreground: Color,
        val container: Color,
        val border: Color
    )

    @Immutable
    data class FeatureColors(
        val vision: FeatureStyle,
        val tools: FeatureStyle,
        val reasoning: FeatureStyle,
        val info: FeatureStyle
    )

    val lightFeatureColors = FeatureColors(
        vision = FeatureStyle(
            foreground = Color(0xFF2563EB),
            container = Color(0xFFEFF6FF),
            border = Color(0xFFBFDBFE)
        ),
        tools = FeatureStyle(
            foreground = Color(0xFF0D9488),
            container = Color(0xFFF0FDFA),
            border = Color(0xFF99F6E4)
        ),
        reasoning = FeatureStyle(
            foreground = Color(0xFF7C3AED),
            container = Color(0xFFFAF5FF),
            border = Color(0xFFE9D5FF)
        ),
        info = FeatureStyle(
            foreground = Color(0xFF16A34A),
            container = Color(0xFFF0FDF4),
            border = Color(0xFFBBF7D0)
        )
    )

    val darkFeatureColors = FeatureColors(
        vision = FeatureStyle(
            foreground = Color(0xFF93C5FD),
            container = Color(0x333B82F6),
            border = Color(0x5960A5FA)
        ),
        tools = FeatureStyle(
            foreground = Color(0xFF5EEAD4),
            container = Color(0x3314B8A6),
            border = Color(0x592DD4BF)
        ),
        reasoning = FeatureStyle(
            foreground = Color(0xFFD8B4FE),
            container = Color(0x33A855F7),
            border = Color(0x59C084FC)
        ),
        info = FeatureStyle(
            foreground = Color(0xFF86EFAC),
            container = Color(0x3322C55E),
            border = Color(0x594ADE80)
        )
    )

    object Feature {
        val vision = lightFeatureColors.vision
        val tools = lightFeatureColors.tools
        val reasoning = lightFeatureColors.reasoning
        val info = lightFeatureColors.info
    }

    object Brand {
        @Immutable
        data class Colors(
            val start: Color,
            val end: Color,
            val accent: Color
        )

        val gemini = Colors(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF6366F1))
        val claude = Colors(Color(0xFFEA580C), Color(0xFFF97316), Color(0xFFEA580C))
        val openAi = Colors(Color(0xFF059669), Color(0xFF10B981), Color(0xFF059669))
        val deepSeek = Colors(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF0284C7))
        val qwen = Colors(Color(0xFF7C3AED), Color(0xFF9333EA), Color(0xFF7C3AED))
        val custom = Colors(Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF3B82F6))
    }
}

val LocalAppStatusColors = compositionLocalOf { AppTokens.lightStatusColors }

val AppStatusColors: AppTokens.StatusColors
    @androidx.compose.runtime.Composable
    get() = LocalAppStatusColors.current

val LocalAppFeatureColors = compositionLocalOf { AppTokens.lightFeatureColors }

val AppFeatureColors: AppTokens.FeatureColors
    @androidx.compose.runtime.Composable
    get() = LocalAppFeatureColors.current
