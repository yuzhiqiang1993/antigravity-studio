package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier

/**
 * 独立的配额进度条暗色调色彩体系 (Independent Dark-Toned Quota Palette)
 * 严格按照高质感、沉稳不刺眼的暗色调设计：
 * - 额度健康 (>= 50%) -> 沉稳森林绿 / 暗翡翠绿 (Dark Forest Green)
 * - 额度紧张 (20% ~ 49%) -> 沉稳暗琥珀 / 焦糖金 (Dark Amber / Muted Gold)
 * - 额度即将用尽 (< 20%) -> 沉稳深绯红 / 暗酒红 (Dark Crimson / Muted Red)
 */
@Immutable
data class QuotaLevelStyle(
    val main: Color,           // 进度条/圆环主色 (沉稳暗色调)
    val onMain: Color,         // 主色上的文字
    val container: Color,      // 徽章与底槽容器色
    val onContainer: Color     // 容器文本与图标
)

/**
 * 完整配额健康度色彩集
 */
@Immutable
data class QuotaLevelColors(
    val full: QuotaLevelStyle,     // 健康 (>=50%)
    val good: QuotaLevelStyle,     // 良好 (>=50% 兼容别名)
    val warning: QuotaLevelStyle,  // 紧张提示 (20%~49%)
    val critical: QuotaLevelStyle  // 告警 (<20%)
)

val LocalAppQuotaColors = compositionLocalOf { StudioThemeColors.lightQuotaColors }

/**
 * 暗色系强调色徽章样式 (Dark-Toned Accent Badge Style)
 */
@Immutable
data class BadgeStyle(
    val bg: Color,
    val text: Color,
    val border: Color = Color.Transparent
)

/**
 * 统一徽章色彩工厂：提供沉稳、高级、富有质感的暗色系强调色
 */
object StudioBadgeColors {
    /**
     * 账号等级徽章 (Pro, Ultra, Enterprise, Free)
     */
    fun tierBadge(tier: AccountTier, isDark: Boolean): BadgeStyle {
        return if (isDark) {
            when (tier) {
                AccountTier.ULTRA -> BadgeStyle(
                    bg = Color(0xFF3B0764),
                    text = Color(0xFFE9D5FF),
                    border = Color(0xFF7E22CE)
                )
                AccountTier.PRO -> BadgeStyle(
                    bg = Color(0xFF172554),
                    text = Color(0xFFBFDBFE),
                    border = Color(0xFF2563EB)
                )
                AccountTier.ENTERPRISE -> BadgeStyle(
                    bg = Color(0xFF082F49),
                    text = Color(0xFFBAE6FD),
                    border = Color(0xFF0284C7)
                )
                AccountTier.FREE -> BadgeStyle(
                    bg = Color(0xFF1E293B),
                    text = Color(0xFF94A3B8),
                    border = Color(0xFF334155)
                )
            }
        } else {
            // 浅色模式下采用高质感暗色调背景 + 纯白高对比度文字
            when (tier) {
                AccountTier.ULTRA -> BadgeStyle(
                    bg = Color(0xFF6B21A8),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF581C87)
                )
                AccountTier.PRO -> BadgeStyle(
                    bg = Color(0xFF1E40AF),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF1E3A8A)
                )
                AccountTier.ENTERPRISE -> BadgeStyle(
                    bg = Color(0xFF0369A1),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF075985)
                )
                AccountTier.FREE -> BadgeStyle(
                    bg = Color(0xFF475569),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF334155)
                )
            }
        }
    }

    /**
     * 宿主环境激活徽章 (IDE, App & CLI, CLI)
     */
    fun hostBadge(isDualActive: Boolean, isIdeActive: Boolean, isDark: Boolean): BadgeStyle {
        return if (isDark) {
            when {
                isDualActive -> BadgeStyle(
                    bg = Color(0xFF064E3B),
                    text = Color(0xFFA7F3D0),
                    border = Color(0xFF059669)
                )
                isIdeActive -> BadgeStyle(
                    bg = Color(0xFF082F49),
                    text = Color(0xFFBAE6FD),
                    border = Color(0xFF0284C7)
                )
                else -> BadgeStyle(
                    bg = Color(0xFF3B0764),
                    text = Color(0xFFE9D5FF),
                    border = Color(0xFF7E22CE)
                )
            }
        } else {
            // 浅色模式下采用沉稳暗色调背景 + 纯白高对比度文字
            when {
                isDualActive -> BadgeStyle(
                    bg = Color(0xFF047857),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF065F46)
                )
                isIdeActive -> BadgeStyle(
                    bg = Color(0xFF0284C7),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF0369A1)
                )
                else -> BadgeStyle(
                    bg = Color(0xFF7C3AED),
                    text = Color(0xFFFFFFFF),
                    border = Color(0xFF6D28D9)
                )
            }
        }
    }
}

/**
 * Antigravity Studio 原生设计系统的色彩规范 (StudioThemeColors)
 * 进度条与徽章拥有独立的暗色调色彩体系，不随主题主色干扰
 */
object StudioThemeColors {

    // =========================================================================
    // 1. 独立配额健康度暗色调色彩体系 (沉稳高级、绝对不刺眼、高对比度)
    // =========================================================================

    val lightQuotaColors = run {
        val healthGreen = QuotaLevelStyle(
            main = Color(0xFF047857),        // Emerald-700 (沉稳深森林绿，>=50%)
            onMain = Color(0xFFFFFFFF),
            container = Color(0xFFDCFCE7),   // Emerald-100
            onContainer = Color(0xFF064E3B)  // Emerald-900
        )
        QuotaLevelColors(
            full = healthGreen,
            good = healthGreen,
            warning = QuotaLevelStyle(
                main = Color(0xFFB45309),        // Amber-700 (沉稳暗琥珀，20%~49%)
                onMain = Color(0xFFFFFFFF),
                container = Color(0xFFFEF3C7),   // Amber-100
                onContainer = Color(0xFF78350F)  // Amber-900
            ),
            critical = QuotaLevelStyle(
                main = Color(0xFFB91C1C),        // Red-700 (沉稳深绯红，<20%)
                onMain = Color(0xFFFFFFFF),
                container = Color(0xFFFEE2E2),   // Red-100
                onContainer = Color(0xFF7F1D1D)  // Red-900
            )
        )
    }

    val darkQuotaColors = run {
        val healthGreen = QuotaLevelStyle(
            main = Color(0xFF10B981),        // Emerald-500 (沉稳质感暗绿，>=50%)
            onMain = Color(0xFF022C22),
            container = Color(0xFF064E3B),   // Emerald-900
            onContainer = Color(0xFFA7F3D0)
        )
        QuotaLevelColors(
            full = healthGreen,
            good = healthGreen,
            warning = QuotaLevelStyle(
                main = Color(0xFFF59E0B),        // Amber-500 (沉稳暗金黄，20%~49%)
                onMain = Color(0xFF451A03),
                container = Color(0xFF78350F),   // Amber-900
                onContainer = Color(0xFFFDE68A)
            ),
            critical = QuotaLevelStyle(
                main = Color(0xFFEF4444),        // Red-500 (沉稳暗绯红，<20%)
                onMain = Color(0xFF450A0A),
                container = Color(0xFF7F1D1D),   // Red-900
                onContainer = Color(0xFFFECACA)
            )
        )
    }

    // 兼容旧代码的便捷别名
    val QuotaLevelFullLight = lightQuotaColors.full.main
    val QuotaLevelGoodLight = lightQuotaColors.good.main
    val QuotaLevelWarningLight = lightQuotaColors.warning.main
    val QuotaLevelCriticalLight = lightQuotaColors.critical.main

    val QuotaLevelFullDark = darkQuotaColors.full.main
    val QuotaLevelGoodDark = darkQuotaColors.good.main
    val QuotaLevelWarningDark = darkQuotaColors.warning.main
    val QuotaLevelCriticalDark = darkQuotaColors.critical.main

    val QuotaLevelFull = QuotaLevelFullLight
    val QuotaLevelGood = QuotaLevelGoodLight
    val QuotaLevelWarning = QuotaLevelWarningLight
    val QuotaLevelCritical = QuotaLevelCriticalLight

    val QuotaHealthy = QuotaLevelFullLight
    val QuotaWarning = QuotaLevelWarningLight
    val QuotaCritical = QuotaLevelCriticalLight

    // =========================================================================
    // 2. 底槽、面板与边框色
    // =========================================================================
    val TrackLight = Color(0xFFE2E8F0)
    val TrackDark = Color(0xFF334155)

    val InnerCardLight = Color(0xFFF8FAFC)
    val InnerCardDark = Color(0xFF1E293B)

    val BorderCardLight = Color(0xFFE2E8F0)
    val BorderSubtleLight = Color(0xFFEEF2F6)

    val ActiveBorder = Color(0xFF0284C7)
    val ActiveBgLight = Color(0xFFFFFFFF)

    val CardIdeActiveBorder = Color(0xFF1976D2)
    val CardCliActiveBorder = Color(0xFF5E35B1)
    val CardDualActiveBorder = Color(0xFF00796B)

    // 徽章旧兼容色
    val BadgeUltraBg = Color(0xFF6B21A8)
    val BadgeUltraText = Color(0xFFFFFFFF)
    val BadgeProBg = Color(0xFF1E40AF)
    val BadgeProText = Color(0xFFFFFFFF)
    val BadgeEnterpriseBg = Color(0xFF0369A1)
    val BadgeEnterpriseText = Color(0xFFFFFFFF)
    val BadgeActiveBg = Color(0xFF047857)
    val BadgeActiveText = Color(0xFFFFFFFF)
    val BadgeIdeBg = Color(0xFF0284C7)
    val BadgeIdeText = Color(0xFFFFFFFF)
    val BadgeCliBg = Color(0xFF7C3AED)
    val BadgeCliText = Color(0xFFFFFFFF)
    val BadgeFreeBg = Color(0xFF475569)
    val BadgeFreeText = Color(0xFFFFFFFF)

    // 文字色彩
    val TextPrimary = Color(0xFF1A1B21)
    val TextSecondary = Color(0xFF46464F)
    val TextMuted = Color(0xFF777680)
    val TextPlaceholder = Color(0xFF90909A)

    val ActionIconDefault = Color(0xFF46464F)
    val ActionIconDelete = Color(0xFFBA1A1A)

    /**
     * 根据百分比与明暗模式计算健康的配额颜色 (独立暗色调体系)
     */
    fun quotaColor(percentage: Int, isDark: Boolean = false): Color {
        val scheme = if (isDark) darkQuotaColors else lightQuotaColors
        return when {
            percentage >= 50 -> scheme.full.main
            percentage >= 20 -> scheme.warning.main
            else             -> scheme.critical.main
        }
    }

    /**
     * 获取指定配额水位对应的完整 MD3 四元样式
     */
    fun quotaStyle(percentage: Int, isDark: Boolean = false): QuotaLevelStyle {
        val scheme = if (isDark) darkQuotaColors else lightQuotaColors
        return when {
            percentage >= 50 -> scheme.full
            percentage >= 20 -> scheme.warning
            else             -> scheme.critical
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
