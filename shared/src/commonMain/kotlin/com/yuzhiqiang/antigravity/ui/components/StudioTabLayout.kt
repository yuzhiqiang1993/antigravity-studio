package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch

data class StudioTabItem<T>(
    val key: T,
    val title: String,
    val icon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val badge: String? = null,
    val isWarningBadge: Boolean = false,
    val showDot: Boolean = false,
    val dotColor: Color? = null
)

/**
 * 现代高品质滑动胶囊 TabLayout 组件：
 * 1. 高对比度沉浸式外层底槽容器（Track）；
 * 2. 灵动丝滑的弹性悬浮药丸滑块（Floating Active Pill）；
 * 3. 悬停微交互与舒适字体排版；
 * 4. 支持徽标 Badge 与图标；
 * 5. 多项时自动支持平滑滚动并将选中项居中。
 */
@Composable
fun <T> StudioSlidingTabLayout(
    items: List<StudioTabItem<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 40.dp,
    scrollable: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    activePillColor: Color = MaterialTheme.colorScheme.surface,
    activeContentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 记录每个 Tab 的位置与尺寸 (单位: Dp)
    var tabPositions by remember { mutableStateOf(mapOf<Int, Pair<Dp, Dp>>()) }
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    val currentPosition = tabPositions[selectedIndex]
    val indicatorOffset by animateDpAsState(
        targetValue = currentPosition?.first ?: 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    val indicatorWidth by animateDpAsState(
        targetValue = currentPosition?.second ?: 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // 当选中的 Tab 改变时，自动将选中的 Tab 平滑滚动居中 (仅在启用滚动时生效)
    if (scrollable) {
        LaunchedEffect(selectedIndex, tabPositions) {
            currentPosition?.let { (xOffset, width) ->
                val targetScroll = with(density) {
                    val containerWidth = scrollState.viewportSize.dp
                    val centerOffset = (xOffset + width / 2) - (containerWidth / 2)
                    centerOffset.toPx().toInt().coerceAtLeast(0)
                }
                coroutineScope.launch {
                    scrollState.animateScrollTo(targetScroll, animationSpec = tween(300))
                }
            }
        }
    }

    val cornerRadius = when {
        tabHeight <= 30.dp -> 8.dp
        tabHeight <= 36.dp -> 9.dp
        else -> 10.dp
    }
    val pillCornerRadius = when {
        tabHeight <= 30.dp -> 6.dp
        tabHeight <= 36.dp -> 6.5.dp
        else -> 7.dp
    }
    val containerPadding = when {
        tabHeight <= 30.dp -> 2.5.dp
        tabHeight <= 36.dp -> 3.dp
        else -> 3.5.dp
    }
    val tabHorizontalPadding = when {
        tabHeight <= 30.dp -> 10.dp
        tabHeight <= 36.dp -> 11.dp
        else -> 14.dp
    }
    val iconSize = when {
        tabHeight <= 30.dp -> 13.dp
        tabHeight <= 36.dp -> 14.dp
        else -> 15.dp
    }
    val fontSize = when {
        tabHeight <= 30.dp -> 11.5.sp
        tabHeight <= 36.dp -> 12.5.sp
        else -> 13.sp
    }

    Surface(
        modifier = modifier.height(tabHeight),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        val boxModifier = if (scrollable) {
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(containerPadding)
        } else {
            Modifier
                .fillMaxHeight()
                .padding(containerPadding)
        }

        Box(
            modifier = boxModifier,
            contentAlignment = Alignment.CenterStart
        ) {
            // 滑动的 Active 药丸胶囊（高对比悬浮白卡片 + 阴影 + 细边框）
            if (indicatorWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(pillCornerRadius))
                        .clip(RoundedCornerShape(pillCornerRadius))
                        .background(activePillColor)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(pillCornerRadius)
                        )
                )
            }

            // Tab 选项列表
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = item.key == selectedKey
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    val textColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> activeContentColor
                            isHovered -> MaterialTheme.colorScheme.onSurface
                            else -> inactiveContentColor
                        },
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInParent()
                                with(density) {
                                    val xOffset = position.x.toDp()
                                    val width = coordinates.size.width.toDp()
                                    tabPositions = tabPositions + (index to (xOffset to width))
                                }
                            }
                            .clip(RoundedCornerShape(pillCornerRadius))
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onSelect(item.key)
                            }
                            .padding(horizontal = tabHorizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (item.icon != null) {
                                androidx.compose.material3.Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                    tint = textColor
                                )
                            }

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = fontSize,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = textColor,
                                maxLines = 1
                            )

                            if (item.showDot) {
                                val dotC = item.dotColor ?: Color(0xFFE53935)
                                Box(
                                    modifier = Modifier
                                        .size(6.5.dp)
                                        .clip(CircleShape)
                                        .background(dotC)
                                )
                            }

                            if (item.trailingIcon != null) {
                                androidx.compose.material3.Icon(
                                    imageVector = item.trailingIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                    tint = textColor
                                )
                            }

                            if (!item.badge.isNullOrBlank()) {
                                val badgeBg by animateColorAsState(
                                    targetValue = when {
                                        item.isWarningBadge -> AppStatusColors.warning.copy(alpha = 0.18f)
                                        isSelected -> activeContentColor.copy(alpha = 0.14f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                    },
                                    animationSpec = tween(150)
                                )
                                val badgeTextColor by animateColorAsState(
                                    targetValue = when {
                                        item.isWarningBadge -> AppStatusColors.warning
                                        isSelected -> activeContentColor
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(150)
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = badgeBg,
                                    modifier = Modifier.height(18.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            color = badgeTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * JetBrains Toolbox 风格经典下划线 TabLayout 组件 (StudioUnderlineTabLayout)：
 * 1. 极简通透无实心底槽，文字加粗 (SemiBold/Bold)；
 * 2. 底部动态弹性滑动科技蓝指示横线 (2.5dp 高度，两端圆角)；
 * 3. 悬停微高光反馈；
 * 4. 支持 Badge 徽标与图标。
 */
@Composable
fun <T> StudioUnderlineTabLayout(
    items: List<StudioTabItem<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 42.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var tabPositions by remember { mutableStateOf(mapOf<Int, Pair<Dp, Dp>>()) }
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    val currentPosition = tabPositions[selectedIndex]
    val indicatorOffset by animateDpAsState(
        targetValue = currentPosition?.first ?: 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    val indicatorWidth by animateDpAsState(
        targetValue = currentPosition?.second ?: 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    LaunchedEffect(selectedIndex, tabPositions) {
        currentPosition?.let { (xOffset, width) ->
            val targetScroll = with(density) {
                val containerWidth = scrollState.viewportSize.dp
                val centerOffset = (xOffset + width / 2) - (containerWidth / 2)
                centerOffset.toPx().toInt().coerceAtLeast(0)
            }
            coroutineScope.launch {
                scrollState.animateScrollTo(targetScroll, animationSpec = tween(300))
            }
        }
    }

    Box(
        modifier = modifier
            .height(tabHeight)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item.key == selectedKey
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                val textColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        isHovered -> MaterialTheme.colorScheme.onSurface
                        else -> inactiveColor
                    },
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val positionInParent = coordinates.positionInParent()
                            val width = coordinates.size.width
                            with(density) {
                                tabPositions = tabPositions + (index to (positionInParent.x.toDp() to width.toDp()))
                            }
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(item.key) }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) activeColor else textColor
                            )
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor,
                            maxLines = 1
                        )

                        if (item.showDot) {
                            val dotC = item.dotColor ?: Color(0xFFE53935)
                            Box(
                                modifier = Modifier
                                    .size(6.5.dp)
                                    .clip(CircleShape)
                                    .background(dotC)
                            )
                        }

                        if (!item.badge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(AppTokens.Radius.pill),
                                color = if (isSelected) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.height(18.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.badge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部科技蓝滑动指示横线 (Toolbox 经典样式)
        if (indicatorWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(activeColor)
            )
        }
    }
}

/**
 * 紧凑型胶囊滑动分段切换器 (StudioSegmentedControl)：
 * 与 StudioSlidingTabLayout 保持统一的滑动悬浮白卡片药丸与高对比度设计
 */
@Composable
fun <T> StudioSegmentedControl(
    items: List<StudioTabItem<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    activePillColor: Color = MaterialTheme.colorScheme.surface,
    activeContentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    StudioSlidingTabLayout(
        items = items,
        selectedKey = selectedKey,
        onSelect = onSelect,
        modifier = modifier,
        tabHeight = height,
        scrollable = false,
        containerColor = containerColor,
        activePillColor = activePillColor,
        activeContentColor = activeContentColor,
        inactiveContentColor = inactiveContentColor
    )
}
