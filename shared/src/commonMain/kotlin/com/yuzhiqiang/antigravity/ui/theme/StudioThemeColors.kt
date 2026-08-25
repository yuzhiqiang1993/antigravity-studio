package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Antigravity Studio 原生设计系统的色彩规范 (StudioThemeColors)
 * 严格符合 Material Design 3 高对比度与 WCAG AA 标准
 */
object StudioThemeColors {
    // 1. 配额水位四级动态健康度色彩 (沉稳深色护眼色系，高对比度且绝不刺眼)
    // 浅色模式 (Light)
    val QuotaLevelFullLight = Color(0xFF047857)     // 沉稳森林深绿 (Emerald-700, >=80%)
    val QuotaLevelGoodLight = Color(0xFF0F766E)     // 沉稳暗青绿 (Teal-700, 50%~79%)
    val QuotaLevelWarningLight = Color(0xFFB45309)  // 沉稳暗琥珀 (Amber-800, 20%~49%)
    val QuotaLevelCriticalLight = Color(0xFF991B1B) // 沉稳深绯红 (Red-800, <20%)

    // 深色模式 (Dark) - 柔和高亮，在深灰/黑底上对比度优异且绝不刺眼
    val QuotaLevelFullDark = Color(0xFF34D399)     // 清晰翠绿 (Emerald-400, >=80%)
    val QuotaLevelGoodDark = Color(0xFF2DD4BF)     // 清晰青绿 (Teal-400, 50%~79%)
    val QuotaLevelWarningDark = Color(0xFFFBBF24)  // 清晰金琥珀 (Amber-400, 20%~49%)
    val QuotaLevelCriticalDark = Color(0xFFF87171) // 清晰绯红 (Red-400, <20%)

    val QuotaLevelFull = QuotaLevelFullLight
    val QuotaLevelGood = QuotaLevelGoodLight
    val QuotaLevelWarning = QuotaLevelWarningLight
    val QuotaLevelCritical = QuotaLevelCriticalLight

    val QuotaHealthy = QuotaLevelFullLight
    val QuotaWarning = QuotaLevelWarningLight
    val QuotaCritical = QuotaLevelCriticalLight

    // 2. 进度条底槽与内嵌面板
    val TrackLight = Color(0xFFE2E8F0) // 浅色底槽 (Slate-200)
    val TrackDark = Color(0xFF334155)  // 深色底槽 (Slate-700)

    val InnerCardLight = Color(0xFFF8FAFC) // 浅色内嵌面板微底 (Slate-50)
    val InnerCardDark = Color(0xFF1E293B)  // 内嵌面板深色背景 (Slate-800)

    val BorderCardLight = Color(0xFFE2E8F0) // 普通卡片外边框 (Slate-200)
    val BorderSubtleLight = Color(0xFFEEF2F6) // 内嵌面板极细微边框

    // 3. 激活高亮边框色彩 (沉稳精致细边框)
    val ActiveBorder = Color(0xFF0284C7) // 沉稳天蓝 (Sky-600)
    val ActiveBgLight = Color(0xFFFFFFFF) // 纯白底色

    // IDE 专属活跃沉稳细边框
    val CardIdeActiveBorder = Color(0xFF0284C7)    // Sky-600 (沉稳科技天蓝细边框)

    // App/CLI 专属活跃沉稳细边框
    val CardCliActiveBorder = Color(0xFF7E22CE)    // Purple-700 (沉稳极客深紫细边框)

    // 双端共同活跃沉稳细边框
    val CardDualActiveBorder = Color(0xFF047857)   // Emerald-700 (沉稳森林深绿细边框)

    // 4. 徽章胶囊 (高对比度深字浅底 / 兼容旧代码)
    val BadgeUltraBg = Color(0xFFF3E8FF)
    val BadgeUltraText = Color(0xFF6B21A8)
    val BadgeProBg = Color(0xFFDBEAFE)
    val BadgeProText = Color(0xFF1E40AF)
    val BadgeEnterpriseBg = Color(0xFFE0F2FE)
    val BadgeEnterpriseText = Color(0xFF0369A1)
    val BadgeActiveBg = Color(0xFFDCFCE7)
    val BadgeActiveText = Color(0xFF166534)
    val BadgeIdeBg = Color(0xFFE0F2FE)
    val BadgeIdeText = Color(0xFF0369A1)
    val BadgeCliBg = Color(0xFFF3E8FF)
    val BadgeCliText = Color(0xFF6B21A8)
    val BadgeFreeBg = Color(0xFFE2E8F0)
    val BadgeFreeText = Color(0xFF1E293B)

    // 5. 核心文字色彩 (推荐优先使用 MaterialTheme.colorScheme.onSurface / onSurfaceVariant)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextMuted = Color(0xFF64748B)
    val TextPlaceholder = Color(0xFF94A3B8)

    val ActionIconDefault = Color(0xFF475569)
    val ActionIconDelete = Color(0xFFDC2626)

    /**
     * 根据百分比与当前主题明暗模式计算健康的配额颜色
     */
    fun quotaColor(percentage: Int, isDark: Boolean = false): Color {
        return if (isDark) {
            when {
                percentage >= 80 -> QuotaLevelFullDark
                percentage >= 50 -> QuotaLevelGoodDark
                percentage >= 20 -> QuotaLevelWarningDark
                else             -> QuotaLevelCriticalDark
            }
        } else {
            when {
                percentage >= 80 -> QuotaLevelFullLight
                percentage >= 50 -> QuotaLevelGoodLight
                percentage >= 20 -> QuotaLevelWarningLight
                else             -> QuotaLevelCriticalLight
            }
        }
    }
}

/**
 * Antigravity Studio 原生设计规范令牌 (StudioDesignTokens)
 * 严格遵循 Material Design 3 偶数与 4dp/8dp 网格体系，严禁奇数与小数
 */
object StudioDesignTokens {
    object TextSize {
        val pageTitle = 24.sp      // MD3 Title Large
        val cardTitle = 14.sp      // MD3 Title Small (纯偶数)
        val body = 12.sp           // MD3 Body Medium (纯偶数)
        val label = 12.sp          // MD3 Label Medium (纯偶数)
        val badge = 10.sp          // MD3 Label Small (纯偶数)
        val caption = 10.sp        // MD3 Caption (纯偶数)
        val resetCountdown = 10.sp // MD3 Mono Micro Text (纯偶数)
    }

    object Sizes {
        val searchFieldWidth = 260.dp
        val searchFieldHeight = 40.dp
        val topButtonHeight = 36.dp
        val topIconButtonSize = 36.dp
        val topIconInnerSize = 18.dp
        val chipHeight = 36.dp
        val cardActionSize = 28.dp
        val cardActionIconSize = 16.dp   // 纯偶数 16dp
        val progressBarHeight = 4.dp     // 纯偶数 4dp
        val checkboxSize = 18.dp         // 纯偶数 18dp
        val avatarDotSize = 8.dp         // 纯偶数 8dp
    }

    object CornerRadius {
        val xs = 4.dp
        val sm = 6.dp
        val md = 8.dp
        val card = 12.dp
        val pill = 999.dp
    }

    object Padding {
        val cardInner = 16.dp            // 纯偶数 16dp
        val innerBlock = 12.dp           // 纯偶数 12dp
        val spaceBetweenRows = 10.dp     // 纯偶数 10dp
        val spaceBetweenColumns = 14.dp  // 纯偶数 14dp
        val topBarHorizontal = 16.dp     // 纯偶数 16dp
        val topBarVertical = 10.dp       // 纯偶数 10dp
    }
}
