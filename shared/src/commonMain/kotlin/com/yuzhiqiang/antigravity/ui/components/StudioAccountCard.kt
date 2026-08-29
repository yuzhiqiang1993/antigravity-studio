package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaGroup
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow
import com.yuzhiqiang.antigravity.ui.animation.*
import com.yuzhiqiang.antigravity.ui.icons.StudioIcons
import androidx.compose.foundation.BorderStroke
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.LocalAppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.StudioBadgeColors
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioThemeColors

/**
 * Antigravity Studio 现代化账号卡片组件（1:1 对齐插件经典环形进度仪表盘）：
 * - 环形进度圈 (Circular Quota Ring) 与百分比右侧绝对对齐，彻底消除线性进度条参差不齐的硬伤
 * - 左侧清晰呈现额度窗口标题与自然语言重置倒计时文案
 * - 备注自然融合在身份行（无备注不占行），更多操作收拢于下拉菜单 ⋮
 * - 严格遵循 MD3 偶数与 4dp/8dp 网格体系
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAccountCard(
    account: AccountInfo,
    quotaSnapshot: AccountQuotaSnapshot?,
    isRefreshing: Boolean = false,
    isPrivacyMode: Boolean,
    isIdeActive: Boolean = false,
    isAppCliActive: Boolean = false,
    isAppActive: Boolean = false,
    isCliActive: Boolean = false,
    isSwitching: Boolean = false,
    onSetActive: () -> Unit,
    onRefresh: () -> Unit,
    onCopyToken: () -> Unit,
    onDelete: () -> Unit,
    onCopyEmail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val effectiveAppCliActive = isAppCliActive || isAppActive || isCliActive
    val isDualActive = isIdeActive && effectiveAppCliActive
    val isAnyActive = isIdeActive || effectiveAppCliActive
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 1. 卡片整体背景 (遵循纯白毛玻璃半透明规范)
    val targetCardBg = if (isAnyActive) {
        if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = StudioGlassTokens.activeCardAlphaDark)
        else Color.White.copy(alpha = StudioGlassTokens.activeCardAlphaLight)
    } else {
        StudioGlassTokens.cardBackgroundColor(isDark)
    }

    val animatedCardBg by androidx.compose.animation.animateColorAsState(
        targetValue = targetCardBg,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardHovered by cardInteractionSource.collectIsHoveredAsState()

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = StudioGlassTokens.cleanBorderColor(isDark, isCardHovered),
        animationSpec = androidx.compose.animation.core.tween(150)
    )

    val displayEmail = if (isPrivacyMode) account.maskedEmail() else account.email

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = StudioGlassTokens.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card)
            )
            .hoverable(cardInteractionSource),
        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
        color = animatedCardBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StudioDesignTokens.Padding.cardInner),
            verticalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenRows)
        ) {

            // 1. 顶部身份行: [邮箱] [当前激活] [PRO/FREE]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emailInteractionSource = remember { MutableInteractionSource() }
                val isEmailHovered by emailInteractionSource.collectIsHoveredAsState()

                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioTooltip(text = s.accountsEmailTooltip(account.email)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.xs))
                                .background(
                                    if (isEmailHovered) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.10f else 0.06f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = emailInteractionSource,
                                    indication = null
                                ) {
                                    copyToClipboard(account.email)
                                    onCopyEmail?.invoke()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = displayEmail,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isAnyActive) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = if (isEmailHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 右侧状态徽章组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeHostLabel = when {
                        isDualActive -> "IDE & App & CLI"
                        isIdeActive -> "IDE"
                        effectiveAppCliActive -> "App & CLI"
                        else -> null
                    }
                    if (activeHostLabel != null) {
                        val hostBadgeStyle = StudioBadgeColors.hostBadge(
                            isDualActive = isDualActive,
                            isIdeActive = isIdeActive,
                            isDark = isDark
                        )
                        Surface(
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                            color = hostBadgeStyle.bg,
                            border = if (hostBadgeStyle.border != Color.Transparent) BorderStroke(1.dp, hostBadgeStyle.border) else null
                        ) {
                            Text(
                                text = activeHostLabel,
                                fontSize = StudioDesignTokens.TextSize.badge,
                                fontWeight = FontWeight.Bold,
                                color = hostBadgeStyle.text,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val tier: AccountTier = when {
                        quotaSnapshot?.tier == AccountTier.ULTRA ||
                                quotaSnapshot?.tierName?.contains("ultra", ignoreCase = true) == true ||
                                account.profile.tier == AccountTier.ULTRA -> AccountTier.ULTRA

                        quotaSnapshot?.tier == AccountTier.ENTERPRISE ||
                                quotaSnapshot?.tierName?.contains("enterprise", ignoreCase = true) == true ||
                                account.profile.tier == AccountTier.ENTERPRISE -> AccountTier.ENTERPRISE

                        quotaSnapshot?.tier == AccountTier.PRO ||
                                quotaSnapshot?.isPro == true ||
                                quotaSnapshot?.tierName?.contains("pro", ignoreCase = true) == true ||
                                account.profile.tier == AccountTier.PRO -> AccountTier.PRO

                        else -> AccountTier.FREE
                    }

                    val tierBadgeStyle = StudioBadgeColors.tierBadge(tier, isDark)
                    val badgeLabel = when (tier) {
                        AccountTier.ULTRA -> "Ultra"
                        AccountTier.PRO -> "Pro"
                        AccountTier.ENTERPRISE -> "Enterprise"
                        AccountTier.FREE -> "Free"
                    }

                    Surface(
                        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                        color = tierBadgeStyle.bg,
                        border = if (tierBadgeStyle.border != Color.Transparent) BorderStroke(1.dp, tierBadgeStyle.border) else null
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = StudioDesignTokens.TextSize.badge,
                            fontWeight = FontWeight.Bold,
                            color = tierBadgeStyle.text,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                }
            }

            // 2. 核心配额面板 (对齐插件官方经典：环形进度圈仪表盘，右侧绝对对齐)
            val displayGroups = quotaSnapshot?.normalizedDisplayGroups().orEmpty()
            RingQuotaMatrixBlock(groups = displayGroups, isDark = isDark, isRefreshing = isRefreshing)

            // 3. 底部元数据与极简操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastUpdated = quotaSnapshot?.fetchedAt ?: account.lastRefreshedAt
                val timeStr = if (lastUpdated > 0L) {
                    val date = java.util.Date(lastUpdated)
                    java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(date)
                } else {
                    "--"
                }

                StudioTooltip(text = s.accountsLastSyncTime(timeStr)) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = StudioDesignTokens.TextSize.caption,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 右侧平铺展开操作组: [切换账号 🔀] + [复制 Token 📋] + [刷新配额 🔄] + [删除账号 🗑️]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs - 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 单客户端激活时仍允许将账号同步到另一客户端。
                    if (!isDualActive) {
                        StudioTooltip(text = if (isAnyActive) s.accountsSyncToOtherHost else s.accountsSetAsActiveTooltip) {
                            IconButton(
                                onClick = onSetActive,
                                enabled = !isSwitching,
                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                            ) {
                                Icon(
                                    imageVector = StudioIcons.SwitchAccount,
                                    contentDescription = s.accountsSwitchDialogTitle,
                                    modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. 复制 Token (使用钥匙图标增强语义)
                    StudioTooltip(text = s.accountsCopyToken) {
                        IconButton(
                            onClick = onCopyToken,
                            modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = s.accountsCopyToken,
                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize - 1.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 4. 刷新 (复用 studioRotating 规范动效)
                    StudioTooltip(text = if (isRefreshing) s.accountsRefreshingTooltip else s.accountsRefreshThisTooltip) {
                        IconButton(
                            onClick = { if (!isRefreshing) onRefresh() },
                            enabled = !isRefreshing,
                            modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = s.commonRefresh,
                                modifier = Modifier
                                    .size(StudioDesignTokens.Sizes.cardActionIconSize)
                                    .studioRotating(isRefreshing),
                                tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 5. 删除账号
                    StudioTooltip(text = s.accountsDeleteThisTooltip) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = s.commonDelete,
                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 仪表盘核心矩阵容器：
 * - 采用无缝 StudioCrossfade 交叉淡入溶解过渡动画，消除数据加载完成时的生硬跳变
 * - 支持流光骨架屏与真实配额仪表盘之间的丝滑切换
 */
@Composable
private fun RingQuotaMatrixBlock(
    groups: List<QuotaGroup>,
    isDark: Boolean,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val innerBg = StudioGlassTokens.innerPanelBackgroundColor(isDark)
    val innerBorderColor = StudioGlassTokens.innerPanelBorderColor(isDark)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.md))
            .background(innerBg)
            .border(1.dp, innerBorderColor, RoundedCornerShape(StudioDesignTokens.CornerRadius.md))
            .padding(horizontal = StudioDesignTokens.Padding.innerBlock, vertical = 10.dp)
    ) {
        StudioCrossfade(
            targetState = groups.isNotEmpty(),
            label = "quota_matrix_crossfade"
        ) { hasData ->
            if (!hasData) {
                // 骨架屏仪表盘：保持高度与正常卡片 100% 严格一致，流光呼吸加载
                QuotaDashboardSkeleton(borderClr = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), isDark = isDark, isRefreshing = isRefreshing)
            } else {
                val geminiGroup = groups.firstOrNull { it.family == "gemini" } ?: groups.first()
                val claudeGroup = groups.firstOrNull { it.family == "claude" } ?: groups.getOrNull(1) ?: groups.first()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 上部分: Gemini 模型族
                    RingFamilySection(group = geminiGroup, isDark = isDark)

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )

                    // 下部分: Claude 模型族
                    RingFamilySection(group = claudeGroup, isDark = isDark)
                }
            }
        }
    }
}

/**
 * 仪表盘流光骨架屏组件：
 * 结构与真实数据 1:1 镜像，引入智能流光扫光 (studioShimmer)，保证高度 100% 绝对一致
 */
@Composable
private fun QuotaDashboardSkeleton(borderClr: Color, isDark: Boolean, isRefreshing: Boolean = false) {
    val s = strings()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Gemini 模型骨架
        SkeletonFamilyBlock(title = s.accountsModelFamily("Gemini"), isDark = isDark, isRefreshing = isRefreshing)

        HorizontalDivider(color = borderClr.copy(alpha = 0.7f), thickness = 0.5.dp)

        // 2. Claude 模型骨架
        SkeletonFamilyBlock(title = s.accountsModelFamily("Claude"), isDark = isDark, isRefreshing = isRefreshing)
    }
}

@Composable
private fun SkeletonFamilyBlock(title: String, isDark: Boolean, isRefreshing: Boolean = false) {
    val s = strings()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = StudioDesignTokens.TextSize.body
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (isRefreshing) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .studioShimmer(shape = CircleShape, isDark = isDark)
                )
            }
        }

        SkeletonQuotaRow(title = s.accountsQuotaFiveHour, isDark = isDark, isRefreshing = isRefreshing)
        SkeletonQuotaRow(title = s.accountsQuotaWeekly, isDark = isDark, isRefreshing = isRefreshing)
    }
}

@Composable
private fun SkeletonQuotaRow(title: String, isDark: Boolean, isRefreshing: Boolean = false) {
    val s = strings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧骨架
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.label,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (isRefreshing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(86.dp)
                            .studioShimmer(shape = RoundedCornerShape(3.dp), isDark = isDark)
                    )
                    Text(
                        text = s.accountsFetchingQuota,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = StudioDesignTokens.TextSize.resetCountdown
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = s.accountsNoQuotaData,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = StudioDesignTokens.TextSize.resetCountdown
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }

        // 右侧骨架：占位数值 + 流光扫光空圆环
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "--%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = StudioDesignTokens.TextSize.cardTitle
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp)
            )

            // 占位圆环（流光动画）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .studioShimmer(shape = CircleShape, isDark = isDark)
            )
        }
    }
}


@Composable
private fun RingFamilySection(
    group: QuotaGroup,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val s = strings()
    val fiveHour = group.buckets.firstOrNull { it.window == QuotaWindow.FIVE_HOUR }
        ?: group.buckets.firstOrNull()
    val weekly = group.buckets.firstOrNull { it.window == QuotaWindow.WEEKLY }
        ?: group.buckets.getOrNull(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 模型族标题
        Text(
            text = s.accountsModelFamily(group.label),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = StudioDesignTokens.TextSize.body
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        fiveHour?.let { item ->
            RingQuotaRow(groupName = group.label, title = s.accountsQuotaFiveHour, item = item, isDark = isDark)
        }

        weekly?.let { item ->
            RingQuotaRow(groupName = group.label, title = s.accountsQuotaWeekly, item = item, isDark = isDark)
        }
    }
}

/**
 * 单条配额环形条目：
 * - 左侧：名称 + 高亮倒计时
 * - 右侧：动态滚动递增数值 (Animated Numeric Counter) + 弹性 Spring 环形进度圈
 */
@Composable
private fun RingQuotaRow(
    groupName: String,
    title: String,
    item: ModelQuotaInfo,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val s = strings()
    val targetPct = item.percentage.coerceIn(0, 100)
    val barColor = StudioThemeColors.quotaColor(targetPct, isDark)
    val isFull = targetPct >= 100
    val countdown = item.formattedCountdown(s)
    val formattedDate = item.resetTimeEpochSeconds?.let { epochSec ->
        val date = java.util.Date(epochSec * 1000L)
        java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(date)
    }

    // 复用全局统一的智能记忆滚动数字计数动画 (首次挂载 snap 稳定呈现，数据真实变更才平滑动画)
    val animatedPctFloat by rememberAnimatedQuotaPercentage(targetPercentage = targetPct)

    // 精简沉稳倒计时与精确时间点 (2天 20小时后重置 · 08/28 14:53)，满额时使用独立的健康暗绿色
    val fullColor = StudioThemeColors.quotaColor(100, isDark)
    val descAnnotated = remember(targetPct, countdown, formattedDate, isFull, isDark, fullColor, s) {
        buildAnnotatedString {
            if (isFull) {
                withStyle(
                    SpanStyle(
                        color = fullColor,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(s.accountsQuotaFull)
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    if (countdown != null) {
                        append(countdown)
                        append(s.accountsQuotaResetInSuffix)
                    } else {
                        append(s.accountsQuotaResetSoon)
                    }
                }
                if (formattedDate != null) {
                    withStyle(
                        SpanStyle(
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    ) {
                        append(" ($formattedDate)")
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：名称 + 高亮完整时间文案
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.label,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = descAnnotated,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.resetCountdown
                ),
                maxLines = 1
            )
        }

        // 右侧：绝对垂直拉齐的 [平滑滚动百分比] + [弹性 Spring 环形圈 Gauge]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${animatedPctFloat.toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = StudioDesignTokens.TextSize.cardTitle
                ),
                color = barColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp)
            )

            StudioCircularGauge(
                percentage = targetPct,
                barColor = barColor,
                size = 22.dp,
                strokeWidth = 3.dp
            )
        }
    }
}
