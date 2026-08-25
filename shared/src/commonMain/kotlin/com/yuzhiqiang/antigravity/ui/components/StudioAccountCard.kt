package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.core.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaGroup
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens
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
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    isRefreshing: Boolean = false,
    isPrivacyMode: Boolean,
    isIdeActive: Boolean = false,
    isCliActive: Boolean = account.isActive,
    onToggleSelect: () -> Unit,
    onSetActive: () -> Unit,
    onTogglePin: () -> Unit,
    onEditNote: () -> Unit,
    onRefresh: () -> Unit,
    onCopyToken: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isAnyActive = isIdeActive || isCliActive || account.isActive
    val isDark = isSystemInDarkTheme()
    var showMoreMenu by remember { mutableStateOf(false) }

    val targetCardBg = if (isAnyActive) {
        if (isDark) Color(0xFF1E293B) else StudioThemeColors.ActiveBgLight
    } else {
        if (isDark) MaterialTheme.colorScheme.surface else Color.White
    }

    val animatedCardBg by androidx.compose.animation.animateColorAsState(
        targetValue = targetCardBg,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

    val targetBorderColor = if (isAnyActive) {
        StudioThemeColors.ActiveBorder
    } else {
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else StudioThemeColors.BorderCardLight
    }

    val animatedBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

    val displayEmail = if (isPrivacyMode) account.maskedEmail() else account.email

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card)
            ),
        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
        color = animatedCardBg,
        shadowElevation = if (isAnyActive) 1.5.dp else 0.5.dp
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StudioDesignTokens.Padding.cardInner),
            verticalArrangement = Arrangement.spacedBy(StudioDesignTokens.Padding.spaceBetweenRows)
        ) {

            // 1. 顶部身份行: [MD3 Checkbox(仅批量模式可见)] [邮箱] [🏷 备注微胶囊(有则显示)] [置顶] [当前激活] [PRO/FREE]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode || isSelected) {
                        StudioTooltip(text = if (isSelected) "取消勾选" else "勾选用于批量操作") {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelect() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = StudioThemeColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    StudioTooltip(text = "账号邮箱: ${account.email}") {
                        Text(
                            text = displayEmail,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isAnyActive) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = StudioThemeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }



                    // 如果有自定义备注，紧随邮箱展示优雅微胶囊
                    if (!account.customNote.isNullOrBlank()) {
                        StudioTooltip(text = "账号备注: ${account.customNote}") {
                            Surface(
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else StudioThemeColors.InnerCardLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioThemeColors.BorderSubtleLight),
                                modifier = Modifier.clickable { onEditNote() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = StudioThemeColors.TextSecondary
                                    )
                                    Text(
                                        text = account.customNote!!,
                                        fontSize = StudioDesignTokens.TextSize.badge,
                                        fontWeight = FontWeight.Medium,
                                        color = StudioThemeColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 右侧状态徽章组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (account.isPinned) {
                        StudioTooltip(text = "此账号已置顶固定") {
                            Surface(
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                                color = StudioThemeColors.BadgeProBg
                            ) {
                                Text(
                                    text = "已置顶",
                                    fontSize = StudioDesignTokens.TextSize.badge,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioThemeColors.BadgeProText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (isIdeActive && isCliActive) {
                        Surface(
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                            color = StudioThemeColors.BadgeActiveBg
                        ) {
                            Text(
                                text = "IDE & App/CLI",
                                fontSize = StudioDesignTokens.TextSize.badge,
                                fontWeight = FontWeight.Bold,
                                color = StudioThemeColors.BadgeActiveText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isIdeActive) {
                        Surface(
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                            color = StudioThemeColors.BadgeIdeBg
                        ) {
                            Text(
                                text = "IDE",
                                fontSize = StudioDesignTokens.TextSize.badge,
                                fontWeight = FontWeight.Bold,
                                color = StudioThemeColors.BadgeIdeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isCliActive) {
                        Surface(
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                            color = StudioThemeColors.BadgeCliBg
                        ) {
                            Text(
                                text = "App/CLI",
                                fontSize = StudioDesignTokens.TextSize.badge,
                                fontWeight = FontWeight.Bold,
                                color = StudioThemeColors.BadgeCliText,
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

                    val badgeBg = when (tier) {
                        AccountTier.ULTRA -> StudioThemeColors.BadgeUltraBg
                        AccountTier.PRO -> StudioThemeColors.BadgeProBg
                        AccountTier.ENTERPRISE -> StudioThemeColors.BadgeEnterpriseBg
                        AccountTier.FREE -> StudioThemeColors.BadgeFreeBg
                    }

                    val badgeText = when (tier) {
                        AccountTier.ULTRA -> StudioThemeColors.BadgeUltraText
                        AccountTier.PRO -> StudioThemeColors.BadgeProText
                        AccountTier.ENTERPRISE -> StudioThemeColors.BadgeEnterpriseText
                        AccountTier.FREE -> StudioThemeColors.BadgeFreeText
                    }

                    val badgeLabel = when (tier) {
                        AccountTier.ULTRA -> "Ultra"
                        AccountTier.PRO -> "Pro"
                        AccountTier.ENTERPRISE -> "Enterprise"
                        AccountTier.FREE -> "Free"
                    }

                    Surface(
                        shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = StudioDesignTokens.TextSize.badge,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }



                }
            }

            // 2. 核心配额面板 (对齐插件官方经典：环形进度圈仪表盘，右侧绝对对齐)
            val displayGroups = quotaSnapshot?.normalizedDisplayGroups().orEmpty()
            RingQuotaMatrixBlock(groups = displayGroups, isDark = isDark)

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

                StudioTooltip(text = "配额最后同步时间: $timeStr") {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = StudioDesignTokens.TextSize.caption,
                            fontWeight = FontWeight.Medium
                        ),
                        color = StudioThemeColors.TextMuted
                    )
                }

                // 右侧极简操作组: [设为活跃 ⚡] + [刷新 🔄] + [更多操作 ⋮ 下拉菜单]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 快捷操作 1: 设为活跃
                    if (!isAnyActive) {
                        StudioTooltip(text = "设为 IDE 与 App/CLI 激活生效账号") {
                            FilledTonalIconButton(
                                onClick = onSetActive,

                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize),
                                shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.xs),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = StudioThemeColors.ActiveBgLight,
                                    contentColor = StudioThemeColors.ActiveBorder
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FlashOn,
                                    contentDescription = "设为活跃",
                                    modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize)
                                )
                            }
                        }
                    }

                    // 快捷操作 2: 刷新 (支持实时旋转 loading 动画)
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotateAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    StudioTooltip(text = if (isRefreshing) "正在刷新配额..." else "刷新此账号实时配额") {
                        IconButton(
                            onClick = { if (!isRefreshing) onRefresh() },
                            enabled = !isRefreshing,
                            modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier
                                    .size(StudioDesignTokens.Sizes.cardActionIconSize)
                                    .rotate(if (isRefreshing) rotateAngle else 0f),
                                tint = if (isRefreshing) MaterialTheme.colorScheme.primary else StudioThemeColors.ActionIconDefault
                            )
                        }
                    }


                    // 快捷操作 3: 更多操作下拉菜单 (收拢置顶、修改备注、复制、删除)
                    Box {
                        StudioTooltip(text = "更多账号操作") {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "更多操作",
                                    modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                    tint = StudioThemeColors.ActionIconDefault
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (account.isPinned) "取消置顶" else "置顶账号", fontSize = StudioDesignTokens.TextSize.body) },
                                onClick = {
                                    showMoreMenu = false
                                    onTogglePin()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                        tint = if (account.isPinned) StudioThemeColors.ActiveBorder else StudioThemeColors.TextSecondary
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(if (account.customNote.isNullOrBlank()) "添加备注" else "修改备注", fontSize = StudioDesignTokens.TextSize.body) },
                                onClick = {
                                    showMoreMenu = false
                                    onEditNote()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize + 2.dp),
                                        tint = StudioThemeColors.TextSecondary
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("复制 Token", fontSize = StudioDesignTokens.TextSize.body) },
                                onClick = {
                                    showMoreMenu = false
                                    onCopyToken()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                        tint = StudioThemeColors.TextSecondary
                                    )
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("删除账号", fontSize = StudioDesignTokens.TextSize.body, color = StudioThemeColors.ActionIconDelete) },
                                onClick = {
                                    showMoreMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                        tint = StudioThemeColors.ActionIconDelete
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 经典环形进度圈仪表盘面板（完全对齐插件经典呈现）：
 * - 左侧：窗口名称与自然语言倒计时文案
 * - 右侧：绝对垂直拉齐的百分比 + 环形进度圈 (QuotaRingGauge)
 */
@Composable
private fun RingQuotaMatrixBlock(
    groups: List<QuotaGroup>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val innerBg = if (isDark) StudioThemeColors.InnerCardDark else StudioThemeColors.InnerCardLight
    val borderClr = if (isDark) StudioThemeColors.TrackDark.copy(alpha = 0.5f) else StudioThemeColors.BorderSubtleLight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioDesignTokens.CornerRadius.md))
            .background(innerBg)
            .border(1.dp, borderClr, RoundedCornerShape(StudioDesignTokens.CornerRadius.md))
            .padding(horizontal = StudioDesignTokens.Padding.innerBlock, vertical = 10.dp)
    ) {
        if (groups.isEmpty()) {
            // 骨架屏仪表盘：保持高度与正常卡片 100% 严格一致，彻底消除卡片高低不平
            QuotaDashboardSkeleton(borderClr = borderClr)
        } else {
            val claudeGroup = groups.firstOrNull { it.family == "claude" } ?: groups.first()
            val geminiGroup = groups.firstOrNull { it.family == "gemini" } ?: groups.getOrNull(1) ?: groups.first()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 上部分: Claude 模型族
                RingFamilySection(group = claudeGroup)

                HorizontalDivider(color = borderClr.copy(alpha = 0.7f), thickness = 0.5.dp)

                // 下部分: Gemini 模型族
                RingFamilySection(group = geminiGroup)
            }
        }
    }
}

/**
 * 仪表盘骨架屏组件：
 * 结构与真实数据 1:1 镜像，保证卡片高度 100% 绝对一致，杜绝 Grid 凹凸不平
 */
@Composable
private fun QuotaDashboardSkeleton(borderClr: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Claude 模型骨架
        SkeletonFamilyBlock(title = "Claude 模型")

        HorizontalDivider(color = borderClr.copy(alpha = 0.7f), thickness = 0.5.dp)

        // 2. Gemini 模型骨架
        SkeletonFamilyBlock(title = "Gemini 模型")
    }
}

@Composable
private fun SkeletonFamilyBlock(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = StudioDesignTokens.TextSize.body
            ),
            color = StudioThemeColors.TextPrimary.copy(alpha = 0.6f)
        )

        SkeletonQuotaRow(title = "五小时额度")
        SkeletonQuotaRow(title = "周额度")
    }
}

@Composable
private fun SkeletonQuotaRow(title: String) {
    val trackBg = if (isSystemInDarkTheme()) StudioThemeColors.TrackDark else StudioThemeColors.TrackLight

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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.label,
                    fontWeight = FontWeight.SemiBold
                ),
                color = StudioThemeColors.TextPrimary.copy(alpha = 0.6f)
            )

            Text(
                text = "正在同步额度水位...",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.resetCountdown
                ),
                color = StudioThemeColors.TextMuted.copy(alpha = 0.6f),
                maxLines = 1
            )
        }

        // 右侧骨架：占位数值 + 占位空圆环
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
                color = StudioThemeColors.TextMuted.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp)
            )

            // 占位底槽圆环
            Canvas(modifier = Modifier.size(22.dp)) {
                drawArc(
                    color = trackBg,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(19.dp.toPx(), 19.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}


@Composable
private fun RingFamilySection(
    group: QuotaGroup
) {
    val fiveHour = group.buckets.firstOrNull { it.window == QuotaWindow.FIVE_HOUR }
        ?: group.buckets.firstOrNull()
    val weekly = group.buckets.firstOrNull { it.window == QuotaWindow.WEEKLY }
        ?: group.buckets.getOrNull(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 模型族标题
        Text(
            text = "${group.label} 模型",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = StudioDesignTokens.TextSize.body
            ),
            color = StudioThemeColors.TextPrimary
        )

        fiveHour?.let { item ->
            RingQuotaRow(groupName = group.label, title = "五小时额度", item = item)
        }

        weekly?.let { item ->
            RingQuotaRow(groupName = group.label, title = "周额度", item = item)
        }
    }
}

/**
 * 单条配额环形条目：
 * 左侧：窗口名称 + 精简高亮倒计时；右侧：百分比 + 环形进度圈 (绝对垂直对齐！)
 */
@Composable
private fun RingQuotaRow(
    groupName: String,
    title: String,
    item: ModelQuotaInfo
) {
    val barColor = StudioThemeColors.quotaColor(item.percentage)
    val isFull = item.percentage >= 100
    val countdown = item.formattedCountdown() ?: "即将重置"
    val formattedDate = item.resetTimeEpochSeconds?.let { epochSec ->
        val date = java.util.Date(epochSec * 1000L)
        java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(date)
    }

    // 直接在面板中呈现完整倒计时与精确重置时间点 (08/28 14:53)
    val descAnnotated = remember(item.percentage, countdown, formattedDate, isFull) {
        buildAnnotatedString {
            if (isFull) {
                withStyle(
                    SpanStyle(
                        color = StudioThemeColors.QuotaHealthy,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append("● 满额可用")
                }
            } else {
                withStyle(SpanStyle(color = StudioThemeColors.TextMuted)) {
                    append("将在 ")
                }
                withStyle(
                    SpanStyle(
                        color = StudioThemeColors.ActiveBorder,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(countdown)
                    if (formattedDate != null) {
                        append(" ($formattedDate)")
                    }
                }
                withStyle(SpanStyle(color = StudioThemeColors.TextMuted)) {
                    append(" 后重置")
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
        // 左侧：名称 + 高亮完整时间文案 (名称 + 倒计时 + 精确时间点直接呈现)
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
                color = StudioThemeColors.TextPrimary
            )

            Text(
                text = descAnnotated,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = StudioDesignTokens.TextSize.resetCountdown
                ),
                maxLines = 1
            )
        }

        // 右侧：绝对垂直拉齐的 [百分比] + [环形圈 Gauge]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${item.percentage}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = StudioDesignTokens.TextSize.cardTitle
                ),
                color = barColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp)
            )

            QuotaRingGauge(
                percentage = item.percentage,
                size = 22.dp,
                strokeWidth = 3.dp
            )
        }
    }

}


/**
 * 高质感 Canvas 环形进度圈组件 (Circular Quota Ring Gauge)：
 * - 动态平滑弧度过渡动画
 * - 翠绿/金橙/珊瑚红 三级精准配色
 * - 中性浅灰底槽
 */
@Composable
fun QuotaRingGauge(
    percentage: Int,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 3.dp
) {
    val barColor = StudioThemeColors.quotaColor(percentage)
    val isDark = isSystemInDarkTheme()
    val trackColor = if (isDark) StudioThemeColors.TrackDark else StudioThemeColors.TrackLight

    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400)
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val arcSize = size.toPx() - strokeWidth.toPx()
        val topLeft = Offset(strokeWidth.toPx() / 2f, strokeWidth.toPx() / 2f)

        // 1. 绘制底槽圆环
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcSize, arcSize),
            style = stroke
        )

        // 2. 绘制动态进度圆环 (从正上方 -90 度顺时针旋转)
        if (animatedProgress > 0f) {
            drawArc(
                color = barColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = stroke
            )
        }
    }
}
