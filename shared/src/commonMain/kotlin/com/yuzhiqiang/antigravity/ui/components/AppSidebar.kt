package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab

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
    val currentTab by viewModel.currentTab.collectAsState()
    val isCollapsed by viewModel.isSidebarCollapsed.collectAsState()

    val mainItems = listOf(
        SidebarItem(NavTab.OVERVIEW, s.navOverview, Icons.Outlined.Dashboard),
        SidebarItem(NavTab.ACCOUNTS, s.navAccounts, Icons.Outlined.AccountCircle),
        SidebarItem(NavTab.MODELS, s.navModels, Icons.Outlined.Memory),
        SidebarItem(NavTab.ACTIVITY, s.navActivity, Icons.Outlined.Description)
    )


    val targetWidth = if (isCollapsed) 72.dp else 228.dp
    val sidebarWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )

    // Logo 尺寸平滑过渡（64dp <-> 44dp）
    val logoSize by animateDpAsState(
        targetValue = if (isCollapsed) 44.dp else 64.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )

    // 品牌文字透明度与高度收放动画
    val titleAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isCollapsed) 150 else 240,
            easing = FastOutSlowInEasing
        )
    )
    val titleHeight by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )

    val brandGradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary
        )
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .zIndex(10f)
    ) {
        PermanentDrawerSheet(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawLine(
                        color = outlineVariant.copy(alpha = if (isDark) 0.25f else 0.35f),
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f
                    )
                },
            drawerContainerColor = Color.Transparent,
            drawerShape = RoundedCornerShape(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isCollapsed) 8.dp else 12.dp,
                        end = if (isCollapsed) 8.dp else 12.dp,
                        top = 20.dp,
                        bottom = 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Logo 与品牌标题
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BrandMark(
                        size = logoSize,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (isCollapsed) viewModel.toggleSidebar() }
                        )
                    )

                    if (titleHeight > 0.dp) {
                        Spacer(Modifier.height((titleHeight * 0.35f).coerceAtLeast(0.dp)))
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))

                // 主导航条目列表 (M3 NavigationDrawerItem)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainItems.forEach { item ->
                        AppSidebarDrawerItem(
                            item = item,
                            selected = currentTab == item.tab,
                            isCollapsed = isCollapsed,
                            onClick = { viewModel.selectTab(item.tab) }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 底部设置条目
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(8.dp))

                AppSidebarDrawerItem(
                    item = SidebarItem(NavTab.SETTINGS, s.navSettings, Icons.Outlined.Settings),
                    selected = currentTab == NavTab.SETTINGS,
                    isCollapsed = isCollapsed,
                    onClick = { viewModel.selectTab(NavTab.SETTINGS) }
                )
            }
        }

        // 贴边折叠/展开浮动手柄
        SidebarUnifiedEdgeHandle(
            isCollapsed = isCollapsed,
            onClick = { viewModel.toggleSidebar() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(20f)
        )
    }
}

/**
 * 封装 M3 NavigationDrawerItem 并支持平滑的展开/折叠自适应过渡
 */
@Composable
private fun AppSidebarDrawerItem(
    item: SidebarItem,
    selected: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(10.dp)

    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(20.dp)
            )
        },
        label = {
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(),
                exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally()
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = itemShape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * 统一贴边折叠/展开把手（带旋转与位移动效）
 */
@Composable
private fun SidebarUnifiedEdgeHandle(
    isCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val s = strings()

    val handleOffsetX by animateDpAsState(
        targetValue = if (isCollapsed) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )
    val handleOffsetY by animateDpAsState(
        targetValue = if (isCollapsed) 26.dp else 36.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 180f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    )

    val bg by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(150)
    )
    val borderCol by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(150)
    )
    val iconTint by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(150)
    )

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
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, borderCol),
        shadowElevation = if (isHovered) 2.dp else 0.5.dp
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
                    .size(15.dp)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }
    }
}
