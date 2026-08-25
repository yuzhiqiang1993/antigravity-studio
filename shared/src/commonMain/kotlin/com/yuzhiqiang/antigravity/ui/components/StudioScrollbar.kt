package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Antigravity Studio 统一跨平台高质感滚动条样式配置。
 */
@Immutable
data class StudioScrollbarStyle(
    val normalThickness: Dp = 4.dp,
    val activeThickness: Dp = 6.dp,
    val minThumbLength: Dp = 32.dp,
    val shape: Shape = RoundedCornerShape(AppTokens.Radius.pill),
    val unhoverColor: Color,
    val hoverColor: Color,
    val trackColor: Color = Color.Transparent
)

/**
 * 获取符合当前主题的 Studio 滚动条样式。
 */
@Composable
fun rememberStudioScrollbarStyle(
    normalThickness: Dp = 4.dp,
    activeThickness: Dp = 6.dp,
    minThumbLength: Dp = 32.dp,
    shape: Shape = RoundedCornerShape(AppTokens.Radius.pill),
    unhoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
    hoverColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    trackColor: Color = Color.Transparent
): StudioScrollbarStyle {
    return StudioScrollbarStyle(
        normalThickness = normalThickness,
        activeThickness = activeThickness,
        minThumbLength = minThumbLength,
        shape = shape,
        unhoverColor = unhoverColor,
        hoverColor = hoverColor,
        trackColor = trackColor
    )
}

/**
 * 针对通用 [ScrollState] 的精致垂直滚动条组件。
 *
 * 特性：
 * 1. 100% 纯跨平台 Compose 实现，无平台 SDK 依赖与冲突风险；
 * 2. 悬停与拖拽时平滑自适应加粗与高亮变色；
 * 3. 支持鼠标拖拽滑块与点击轨道直接快速定位；
 * 4. 内容不足一屏时自动淡出隐藏。
 */
@Composable
fun StudioVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    style: StudioScrollbarStyle = rememberStudioScrollbarStyle()
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }

    val isActive = isHovered || isDragging
    val density = LocalDensity.current

    // 动态宽度与颜色平滑过渡动画
    val thumbWidth by animateDpAsState(
        targetValue = if (isActive) style.activeThickness else style.normalThickness,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "thumbWidth"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (isActive) style.hoverColor else style.unhoverColor,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "thumbColor"
    )

    val isVisible = scrollState.maxValue > 0

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .width(style.activeThickness + 4.dp)
                .hoverable(interactionSource)
                .background(style.trackColor)
        ) {
            val viewportHeightPx = constraints.maxHeight.toFloat()
            val maxValuePx = scrollState.maxValue.toFloat()
            val totalContentHeightPx = viewportHeightPx + maxValuePx

            val minThumbHeightPx = with(density) { style.minThumbLength.toPx() }
            val rawThumbHeightPx = if (totalContentHeightPx > 0f) {
                (viewportHeightPx / totalContentHeightPx) * viewportHeightPx
            } else {
                minThumbHeightPx
            }
            val thumbHeightPx = rawThumbHeightPx.coerceIn(minThumbHeightPx, viewportHeightPx)
            val trackLengthPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)

            val scrollRatio = (scrollState.value.toFloat() / maxValuePx).coerceIn(0f, 1f)
            val thumbOffsetPx = scrollRatio * trackLengthPx

            val draggableState = rememberDraggableState { delta ->
                if (trackLengthPx > 0f && maxValuePx > 0f) {
                    val scrollDelta = (delta / trackLengthPx) * maxValuePx
                    coroutineScope.launch {
                        scrollState.scrollBy(scrollDelta)
                    }
                }
            }

            // 点击轨道空白处跳跃
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(style.activeThickness + 4.dp)
                    .pointerInput(scrollState, trackLengthPx) {
                        detectTapGestures { tapOffset ->
                            val targetRatio = ((tapOffset.y - thumbHeightPx / 2f) / trackLengthPx).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                scrollState.animateScrollTo((targetRatio * maxValuePx).roundToInt())
                            }
                        }
                    }
            ) {
                // 滑块 Thumb
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                        .height(with(density) { thumbHeightPx.toDp() })
                        .width(thumbWidth)
                        .clip(style.shape)
                        .background(thumbColor)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { isDragging = true },
                            onDragStopped = { isDragging = false }
                        )
                )
            }
        }
    }
}

/**
 * 针对 [LazyListState] 的精致垂直滚动条组件。
 */
@Composable
fun StudioVerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    style: StudioScrollbarStyle = rememberStudioScrollbarStyle()
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }

    val isActive = isHovered || isDragging
    val density = LocalDensity.current

    val thumbWidth by animateDpAsState(
        targetValue = if (isActive) style.activeThickness else style.normalThickness,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lazyThumbWidth"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (isActive) style.hoverColor else style.unhoverColor,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lazyThumbColor"
    )

    val layoutInfo = lazyListState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    val isVisible = totalItems > 0 && visibleItems.size < totalItems

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .width(style.activeThickness + 4.dp)
                .hoverable(interactionSource)
                .background(style.trackColor)
        ) {
            val viewportHeightPx = constraints.maxHeight.toFloat()
            val minThumbHeightPx = with(density) { style.minThumbLength.toPx() }

            val estimatedTotalItems = totalItems.toFloat().coerceAtLeast(1f)
            val visibleCount = visibleItems.size.toFloat().coerceAtLeast(1f)
            val rawThumbHeightPx = (visibleCount / estimatedTotalItems) * viewportHeightPx
            val thumbHeightPx = rawThumbHeightPx.coerceIn(minThumbHeightPx, viewportHeightPx)
            val trackLengthPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)

            val firstVisibleIndex = lazyListState.firstVisibleItemIndex.toFloat()
            val maxFirstIndex = (totalItems - visibleItems.size).toFloat().coerceAtLeast(1f)
            val scrollRatio = (firstVisibleIndex / maxFirstIndex).coerceIn(0f, 1f)
            val thumbOffsetPx = scrollRatio * trackLengthPx

            val draggableState = rememberDraggableState { delta ->
                if (trackLengthPx > 0f) {
                    val scrollFactor = (viewportHeightPx * (totalItems.toFloat() / visibleCount.coerceAtLeast(1f))) / trackLengthPx
                    val scrollDelta = delta * scrollFactor * 0.5f
                    coroutineScope.launch {
                        lazyListState.scrollBy(scrollDelta)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(style.activeThickness + 4.dp)
                    .pointerInput(lazyListState, trackLengthPx) {
                        detectTapGestures { tapOffset ->
                            val targetRatio = ((tapOffset.y - thumbHeightPx / 2f) / trackLengthPx).coerceIn(0f, 1f)
                            val targetIndex = (targetRatio * (totalItems - 1)).roundToInt()
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(targetIndex)
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                        .height(with(density) { thumbHeightPx.toDp() })
                        .width(thumbWidth)
                        .clip(style.shape)
                        .background(thumbColor)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { isDragging = true },
                            onDragStopped = { isDragging = false }
                        )
                )
            }
        }
    }
}
