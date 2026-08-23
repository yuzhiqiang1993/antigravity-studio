package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

private data class SidebarItem(
    val tab: NavTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun AppSidebar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    // 精准响应当前 MaterialTheme 主题（不管是系统暗色还是设置中手动切换的深色模式）
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val currentTab by viewModel.currentTab.collectAsState()
    val isCollapsed by viewModel.isSidebarCollapsed.collectAsState()

    val mainItems = listOf(
        SidebarItem(NavTab.OVERVIEW, s.navOverview, Icons.Outlined.Dashboard),
        SidebarItem(NavTab.MODELS, s.navModels, Icons.Outlined.Memory),
        SidebarItem(NavTab.ACTIVITY, s.navActivity, Icons.Outlined.Description)
    )

    val targetWidth = if (isCollapsed) 68.dp else 230.dp
    val sidebarWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )

    // Logo 尺寸连续平滑插值（从 88.dp 平滑过渡到 44.dp）
    val logoSize by animateDpAsState(
        targetValue = if (isCollapsed) 48.dp else 68.dp,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )

    // 渐变标题透明度与收拢动画
    val titleAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isCollapsed) 140 else 240,
            easing = AppTokens.Motion.standardEasing
        )
    )
    val titleHeight by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 24.dp,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )

    // 高对比度背景与分割线配色（实时响应深浅模式）
    val sidebarBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9) // Slate 900 / Slate 100
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1) // Slate 700 / Slate 300

    // 品牌文字现代科技渐变色（自适应深浅模式）
    val brandGradient = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF60A5FA), // Blue 400
                Color(0xFFA78BFA), // Violet 400
                Color(0xFF818CF8)  // Indigo 400
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF1D4ED8), // Blue 700 (深邃科技蓝)
                Color(0xFF6D28D9), // Violet 700 (现代科技紫)
                Color(0xFF4338CA)  // Indigo 700 (经典靛蓝)
            )
        )
    }

    Box(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .zIndex(10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(sidebarBg)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = Offset(size.width - strokeWidth / 2f, 0f),
                        end = Offset(size.width - strokeWidth / 2f, size.height),
                        strokeWidth = strokeWidth
                    )
                }
                .padding(
                    start = if (isCollapsed) 10.dp else 14.dp,
                    end = if (isCollapsed) 10.dp else 14.dp,
                    top = 22.dp,
                    bottom = 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header 品牌区（Logo 与标题具备连续平滑缩放与渐隐动效）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 纯透明底高清大图标（平滑缩放 88dp <-> 44dp）
                BrandMark(
                    size = logoSize,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isCollapsed) viewModel.toggleSidebar() }
                    )
                )

                // Antigravity Studio 科技渐变文字（平滑高度收拢与透明度渐显渐隐）
                if (titleHeight > 0.dp) {
                    Spacer(Modifier.height((titleHeight * 0.3f).coerceAtLeast(0.dp)))
                    Box(
                        modifier = Modifier
                            .height(titleHeight)
                            .graphicsLayer {
                                alpha = titleAlpha
                                scaleX = (0.85f + 0.15f * titleAlpha).coerceIn(0f, 1f)
                                scaleY = (0.85f + 0.15f * titleAlpha).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Antigravity Studio",
                            style = TextStyle(
                                brush = brandGradient,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            Spacer(Modifier.height(14.dp))

            // 主导航项列表
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                mainItems.forEach { item ->
                    SidebarNavigationItem(
                        item = item,
                        selected = currentTab == item.tab,
                        isCollapsed = isCollapsed,
                        isDark = isDark,
                        onClick = { viewModel.selectTab(item.tab) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 底部设置项区域
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            SidebarNavigationItem(
                item = SidebarItem(NavTab.SETTINGS, s.navSettings, Icons.Outlined.Settings),
                selected = currentTab == NavTab.SETTINGS,
                isCollapsed = isCollapsed,
                isDark = isDark,
                onClick = { viewModel.selectTab(NavTab.SETTINGS) }
            )
        }

        // 统一双态贴边折叠/展开滑块把手（位移与翻转动画极致平滑）
        SidebarUnifiedEdgeHandle(
            isCollapsed = isCollapsed,
            isDark = isDark,
            onClick = { viewModel.toggleSidebar() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(20f)
        )
    }
}

/**
 * 统一双态贴边折叠/展开把手（平滑位移与翻转动效）
 */
@Composable
private fun SidebarUnifiedEdgeHandle(
    isCollapsed: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // 平滑位移动画
    val handleOffsetX by animateDpAsState(
        targetValue = if (isCollapsed) 22.dp else 0.dp,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )
    val handleOffsetY by animateDpAsState(
        targetValue = if (isCollapsed) 30.dp else 42.dp,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )

    // 箭头平滑旋转动效（从向左 0° 平滑翻转到向右 180°）
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    val iconRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 180f else 0f,
        animationSpec = tween(AppTokens.Motion.durationMedium, easing = AppTokens.Motion.standardEasing)
    )

    val bg by animateColorAsState(
        targetValue = when {
            isHovered -> if (isDark) Color(0xFF312E81) else Color(0xFFEFF6FF)
            else -> if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        },
        animationSpec = tween(AppTokens.Motion.durationShort)
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            isHovered -> if (isDark) Color(0xFF818CF8) else Color(0xFF2563EB)
            else -> if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
        },
        animationSpec = tween(AppTokens.Motion.durationShort)
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            isHovered -> if (isDark) Color(0xFF818CF8) else Color(0xFF1D4ED8)
            else -> if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
        },
        animationSpec = tween(AppTokens.Motion.durationShort)
    )

    // 形状：展开时向左凸圆（右平），折叠时向右凸圆（左平）
    val shape = if (isCollapsed) {
        RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    Surface(
        modifier = modifier
            .offset(x = handleOffsetX, y = handleOffsetY)
            .size(width = 22.dp, height = 28.dp)
            .clip(shape)
            .border(1.2.dp, borderCol, shape)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = bg,
        shadowElevation = if (isHovered) 3.dp else 1.5.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = if (isCollapsed) s.sidebarExpand else s.sidebarCollapse,
                tint = iconTint,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }
    }
}

/**
 * 侧边栏导航条目组件（支持平滑收放与动效）
 */
@Composable
private fun SidebarNavigationItem(
    item: SidebarItem,
    selected: Boolean,
    isCollapsed: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val itemShape = RoundedCornerShape(8.dp)

    // 基础色与同色相全透明底色（彻底消除 Compose animateColorAsState 插值向纯黑 Color.Transparent 产生的黑色闪烁暗块）
    val baseSelectedBlue = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
    val baseHoverBlue = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val transparentColor = baseSelectedBlue.copy(alpha = 0f)

    val targetBg = when {
        selected -> if (isDark) baseSelectedBlue.copy(alpha = 0.65f) else baseSelectedBlue // Blue 100 饱满天蓝底
        isHovered -> if (isDark) baseHoverBlue.copy(alpha = 0.12f) else baseHoverBlue.copy(alpha = 0.07f) // 极浅轻盈微蓝光
        else -> transparentColor
    }
    val containerBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(AppTokens.Motion.durationShort)
    )

    // 选中态文字：高对比度深邃科技蓝；Hover态：清晰炭黑；未选中态：中炭灰
    val targetTextCol = when {
        selected -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8) // Blue 700 高对比度蓝
        isHovered -> if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A) // 高亮清晰
        else -> if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155) // Slate 700
    }
    val textCol by animateColorAsState(
        targetValue = targetTextCol,
        animationSpec = tween(AppTokens.Motion.durationShort)
    )

    // 选中态图标：高饱和科技蓝；Hover态：灵动主题蓝；未选中态：次级图标灰
    val targetIconTint = when {
        selected -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
        isHovered -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B) // Slate 500
    }
    val iconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(AppTokens.Motion.durationShort)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(itemShape)
            .background(containerBg)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isCollapsed) 10.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.spacedBy(10.dp)
    ) {
        // 展开状态下的选中胶囊指示条
        if (!isCollapsed) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 3.5.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB))
                )
            } else {
                Spacer(Modifier.width(3.5.dp))
            }
        }

        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn(animationSpec = tween(AppTokens.Motion.durationShort)) + expandHorizontally(),
            exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally()
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textCol,
                maxLines = 1
            )
        }
    }
}
