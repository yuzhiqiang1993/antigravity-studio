package com.yuzhiqiang.antigravity.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Antigravity Studio 原生设计系统的色彩规范 (StudioThemeColors)
 * 严格符合 Material Design 3 高对比度与 WCAG AA 标准
 */
object StudioThemeColors {
    // 1. 配额水位色彩 (高饱和、高辨识度)
    val QuotaHealthy = Color(0xFF059669) // 翠绿 (>50%)
    val QuotaWarning = Color(0xFFD97706) // 暖金橙 (15%~50%)
    val QuotaCritical = Color(0xFFDC2626) // 警示红 (<15%)

    // 2. 进度条底槽与内嵌面板
    val TrackLight = Color(0xFFCBD5E1) // 浅色底槽 (Slate-300，清晰可见)
    val TrackDark = Color(0xFF475569)  // 深色底槽 (Slate-600)

    val InnerCardLight = Color(0xFFF8FAFC) // 内嵌面板极浅微灰背景 (柔和通透，不再压抑)
    val InnerCardDark = Color(0xFF1E293B)  // 内嵌面板深色背景 (Slate-800)

    val BorderCardLight = Color(0xFFE2E8F0) // 卡片外边框 (Slate-200，淡雅柔和)
    val BorderSubtleLight = Color(0xFFEEF2F6) // 内嵌细边框 (极淡微边框)

    // 3. 激活高亮 (淡雅精致微蓝)
    val ActiveBorder = Color(0xFF60A5FA) // 激活柔和天蓝 (Blue-400)
    val ActiveBgLight = Color(0xFFF6F9FE) // 激活极淡柔和浅蓝底 (淡雅纯净)


    // 4. 徽章胶囊 (高对比度深字浅底)
    val BadgeUltraBg = Color(0xFFF3E8FF) // Purple-100 (尊贵紫罗兰 Ultra 旗舰)
    val BadgeUltraText = Color(0xFF6B21A8) // Purple-800

    val BadgeProBg = Color(0xFFDBEAFE) // Blue-100 (电光蓝 Pro 专业版)
    val BadgeProText = Color(0xFF1E40AF) // Blue-800

    val BadgeEnterpriseBg = Color(0xFFE0F2FE) // Sky-100 (商务深青 Enterprise 企业版)
    val BadgeEnterpriseText = Color(0xFF0369A1) // Sky-800

    val BadgeActiveBg = Color(0xFFDCFCE7)
    val BadgeActiveText = Color(0xFF166534) // Green-800

    val BadgeIdeBg = Color(0xFFE0F2FE) // Sky-100 (IDE 独立活跃徽章)
    val BadgeIdeText = Color(0xFF0369A1) // Sky-800

    val BadgeCliBg = Color(0xFFF3E8FF) // Purple-100 (App/CLI 共享活跃徽章)
    val BadgeCliText = Color(0xFF6B21A8) // Purple-800

    val BadgeFreeBg = Color(0xFFE2E8F0)
    val BadgeFreeText = Color(0xFF1E293B) // Slate-800



    // 5. 核心文字色彩 (高对比度，彻底杜绝发灰看不清)
    val TextPrimary = Color(0xFF0F172A)   // Slate-900 (主标题、邮箱、强文字)
    val TextSecondary = Color(0xFF334155) // Slate-700 (二级标签、说明文字、图标)
    val TextMuted = Color(0xFF475569)     // Slate-600 (辅助文字、倒计时、时间戳)
    val TextPlaceholder = Color(0xFF64748B) // Slate-500 (搜索框占位符)

    val ActionIconDefault = Color(0xFF334155) // 操作图标默认色
    val ActionIconDelete = Color(0xFFDC2626)  // 删除图标警示色

    fun quotaColor(percentage: Int): Color {
        return when {
            percentage > 50 -> QuotaHealthy
            percentage >= 15 -> QuotaWarning
            else -> QuotaCritical
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
