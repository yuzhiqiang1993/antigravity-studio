package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

        val card = 0.dp
        val floating = 2.dp
        val dialog = 6.dp
    }

    // =========================================================================
    // MD3 状态语义四元组 (Tone 40/80, 100/20, 90/30, 10/90)
    // =========================================================================

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
        success = Color(0xFF047857),        // 沉稳森林深绿
        onSuccess = Color.White,
        successContainer = Color(0xFFDCFCE7),
        onSuccessContainer = Color(0xFF064E3B),
        warning = Color(0xFFB45309),        // 沉稳暗琥珀
        onWarning = Color.White,
        warningContainer = Color(0xFFFEF3C7),
        onWarningContainer = Color(0xFF78350F),
        info = Color(0xFF0369A1),           // 沉稳深海蓝
        onInfo = Color.White,
        infoContainer = Color(0xFFE0F2FE),
        onInfoContainer = Color(0xFF075985),
        error = Color(0xFFB91C1C),          // 沉稳深绯红
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D)
    )

    val darkStatusColors = StatusColors(
        success = Color(0xFF10B981),        // 沉稳暗质感绿
        onSuccess = Color(0xFF022C22),
        successContainer = Color(0xFF064E3B),
        onSuccessContainer = Color(0xFFA7F3D0),
        warning = Color(0xFFF59E0B),        // 柔和暗金黄
        onWarning = Color(0xFF451A03),
        warningContainer = Color(0xFF78350F),
        onWarningContainer = Color(0xFFFDE68A),
        info = Color(0xFF38BDF8),           // 柔和天蓝
        onInfo = Color(0xFF082F49),
        infoContainer = Color(0xFF0C4A6E),
        onInfoContainer = Color(0xFFBAE6FD),
        error = Color(0xFFEF4444),          // 沉稳暗红宝石
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFECACA)
    )

    // =========================================================================
    // MD3 模型能力特性暗色调胶囊样式 (Dark-Toned Accent Feature Badges)
    // 避免过亮糖果色底，保持沉稳、克制、高级的微容器与暗调强调色
    // =========================================================================

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
            foreground = Color(0xFF0369A1),     // 沉稳深海蓝 (Sky-700)
            container = Color(0xFFF0F6FA),      // 极浅微蓝灰中性底
            border = Color(0xFFCBD5E1)         // 中性微边框
        ),
        tools = FeatureStyle(
            foreground = Color(0xFF0F766E),      // 沉稳暗青绿 (Teal-700)
            container = Color(0xFFF0F7F6),      // 极浅微青灰底
            border = Color(0xFFCBD5E1)
        ),
        reasoning = FeatureStyle(
            foreground = Color(0xFF6B21A8),     // 沉稳深琉璃紫 (Purple-700)
            container = Color(0xFFF7F3FA),      // 极浅微紫灰底
            border = Color(0xFFCBD5E1)
        ),
        info = FeatureStyle(
            foreground = Color(0xFF475569),     // 沉稳深炭灰 (Slate-600)
            container = Color(0xFFF1F5F9),      // 中性浅灰底
            border = Color(0xFFCBD5E1)
        )
    )

    val darkFeatureColors = FeatureColors(
        vision = FeatureStyle(
            foreground = Color(0xFF7DD3FC),     // 柔和冰川蓝 (Sky-300)
            container = Color(0xFF0F1E36),      // 深邃暗蓝底
            border = Color(0xFF1E3A8A)          // 暗蓝边框
        ),
        tools = FeatureStyle(
            foreground = Color(0xFF5EEAD4),     // 柔和暗薄荷青 (Teal-300)
            container = Color(0xFF042F2E),      // 深邃暗青底
            border = Color(0xFF134E4A)
        ),
        reasoning = FeatureStyle(
            foreground = Color(0xFFD8B4FE),     // 柔和暗琉璃紫 (Purple-300)
            container = Color(0xFF220D3D),      // 深邃暗紫底
            border = Color(0xFF4C1D95)
        ),
        info = FeatureStyle(
            foreground = Color(0xFFCBD5E1),     // 浅石板灰 (Slate-300)
            container = Color(0xFF1E293B),      // 深石板灰底
            border = Color(0xFF334155)
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

        val gemini = Colors(Color(0xFF4338CA), Color(0xFF6D28D9), Color(0xFF4338CA))
        val claude = Colors(Color(0xFFC2410C), Color(0xFFEA580C), Color(0xFFC2410C))
        val openAi = Colors(Color(0xFF047857), Color(0xFF059669), Color(0xFF047857))
        val deepSeek = Colors(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF0369A1))
        val qwen = Colors(Color(0xFF6B21A8), Color(0xFF7C3AED), Color(0xFF6B21A8))
        val custom = Colors(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF2563EB))
    }
}

val LocalAppStatusColors = compositionLocalOf { AppTokens.lightStatusColors }

val AppStatusColors: AppTokens.StatusColors
    @Composable
    get() = LocalAppStatusColors.current

val LocalAppFeatureColors = compositionLocalOf { AppTokens.lightFeatureColors }

val AppFeatureColors: AppTokens.FeatureColors
    @Composable
    get() = LocalAppFeatureColors.current

// MaterialTheme 扩展便捷入口
val MaterialTheme.statusColors: AppTokens.StatusColors
    @Composable
    get() = LocalAppStatusColors.current

val MaterialTheme.featureColors: AppTokens.FeatureColors
    @Composable
    get() = LocalAppFeatureColors.current

val MaterialTheme.quotaColors: QuotaLevelColors
    @Composable
    get() = LocalAppQuotaColors.current
