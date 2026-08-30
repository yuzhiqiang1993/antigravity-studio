package com.yuzhiqiang.antigravity.ui.components.tour

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlin.math.roundToInt

/**
 * 全屏暗色遮罩 + 弹性挖孔动画 + 动态多区域镂空 + 自适应防溢出气泡卡片的新手引导蒙层组件
 */
@Composable
fun SpotlightTourOverlay(
    manager: SpotlightTourManager,
    onComplete: () -> Unit
) {
    if (!manager.isActive) return

    val s = strings()
    val density = LocalDensity.current
    val currentStep = manager.currentStep
    val targetRect = manager.anchors[currentStep]
    val associatedStep = currentStep.associatedSidebarStep
    val associatedRect = associatedStep?.let { manager.anchors[it] }

    // 挖孔平滑弹性动画 Spec（镜头对焦般灵动轻快）
    val cutoutSpringSpec = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
    )

    val defaultPadding = with(density) { 6.dp.toPx() }
    val animatedLeft by animateFloatAsState(
        targetValue = (targetRect?.left ?: 0f) - defaultPadding,
        animationSpec = cutoutSpringSpec
    )
    val animatedTop by animateFloatAsState(
        targetValue = (targetRect?.top ?: 0f) - defaultPadding,
        animationSpec = cutoutSpringSpec
    )
    val animatedRight by animateFloatAsState(
        targetValue = (targetRect?.right ?: 0f) + defaultPadding,
        animationSpec = cutoutSpringSpec
    )
    val animatedBottom by animateFloatAsState(
        targetValue = (targetRect?.bottom ?: 0f) + defaultPadding,
        animationSpec = cutoutSpringSpec
    )

    // 呼吸脉冲光晕动画
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // 点击全屏任意位置直接推进到下一步
                manager.nextStep(onComplete)
            }
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // 1. 全局暗色遮罩与多区域镂空挖孔 Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val screenPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }

            var combinedCutoutPath = Path()
            val hasTarget = targetRect != null && targetRect.width > 0 && targetRect.height > 0

            if (hasTarget) {
                // 主聚焦目标挖孔
                val mainCutout = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = animatedLeft,
                            top = animatedTop,
                            right = animatedRight,
                            bottom = animatedBottom,
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        )
                    )
                }
                combinedCutoutPath = mainCutout

                // 若处于阶段二且关联了左侧导航 Tab，则同时镂空左侧对应的导航 Tab
                if (associatedRect != null && associatedRect.width > 0 && associatedRect.height > 0) {
                    val sidePadding = with(density) { 4.dp.toPx() }
                    val sideCutout = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = associatedRect.left - sidePadding,
                                top = associatedRect.top - sidePadding,
                                right = associatedRect.right + sidePadding,
                                bottom = associatedRect.bottom + sidePadding,
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                        )
                    }
                    combinedCutoutPath = Path.combine(
                        operation = PathOperation.Union,
                        path1 = combinedCutoutPath,
                        path2 = sideCutout
                    )
                }

                // 统一全屏差集镂空
                val finalMaskPath = Path.combine(
                    operation = PathOperation.Difference,
                    path1 = screenPath,
                    path2 = combinedCutoutPath
                )
                drawPath(path = finalMaskPath, color = Color.Black.copy(alpha = 0.72f))

                // 绘制主目标高亮呼吸边框
                drawRoundRect(
                    color = primaryColor.copy(alpha = pulseAlpha),
                    topLeft = Offset(animatedLeft, animatedTop),
                    size = Size(animatedRight - animatedLeft, animatedBottom - animatedTop),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // 绘制关联侧边栏导航项的高亮呼吸边框
                if (associatedRect != null && associatedRect.width > 0 && associatedRect.height > 0) {
                    val sidePadding = with(density) { 4.dp.toPx() }
                    val sLeft = associatedRect.left - sidePadding
                    val sTop = associatedRect.top - sidePadding
                    val sRight = associatedRect.right + sidePadding
                    val sBottom = associatedRect.bottom + sidePadding

                    drawRoundRect(
                        color = primaryColor.copy(alpha = pulseAlpha * 0.85f),
                        topLeft = Offset(sLeft, sTop),
                        size = Size(sRight - sLeft, sBottom - sTop),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            } else {
                drawRect(color = Color.Black.copy(alpha = 0.72f))
            }
        }

        // 2. 指向性气泡卡片 (Coach Mark Card)
        if (targetRect != null) {
            val isSidePlacement = currentStep.isSidePlacement
            val bubbleWidthDp = 350.dp
            val bubbleEstimatedHeightDp = 220.dp
            val bubbleWidthPx = with(density) { bubbleWidthDp.toPx() }
            val bubbleEstimatedHeightPx = with(density) { bubbleEstimatedHeightDp.toPx() }
            val screenPaddingPx = with(density) { 16.dp.toPx() }

            val targetBubbleOffsetX: Float
            val targetBubbleOffsetY: Float
            val isTopPlacement: Boolean // 当下方放不下时向上翻转

            val targetArrowOffsetX: Float
            val targetArrowOffsetY: Float

            if (isSidePlacement) {
                isTopPlacement = false
                targetBubbleOffsetX = (animatedRight + with(density) { 18.dp.toPx() })
                    .coerceAtMost(screenWidthPx - bubbleWidthPx - screenPaddingPx)

                // 侧边放置时：气泡中心垂直对齐目标中心，并严格约束在视口内（彻底解决底部裁剪问题）
                val targetCenterY = animatedTop + (animatedBottom - animatedTop) / 2f
                val desiredY = targetCenterY - (bubbleEstimatedHeightPx / 2f)
                targetBubbleOffsetY = desiredY.coerceIn(
                    screenPaddingPx,
                    screenHeightPx - bubbleEstimatedHeightPx - screenPaddingPx
                )

                // 箭头动态跟随目标中心在气泡中的相对位置
                targetArrowOffsetX = with(density) { (-14).dp.toPx() }
                targetArrowOffsetY = (targetCenterY - targetBubbleOffsetY - with(density) { 8.dp.toPx() })
                    .coerceIn(with(density) { 20.dp.toPx() }, bubbleEstimatedHeightPx - with(density) { 36.dp.toPx() })
            } else {
                // 页面内放置：计算下方可用空间
                val spaceBelow = screenHeightPx - animatedBottom
                if (spaceBelow < bubbleEstimatedHeightPx + with(density) { 24.dp.toPx() }) {
                    // 下方空间不足，自动向上翻转（如宿主卡片网格）
                    isTopPlacement = true
                    targetBubbleOffsetY = (animatedTop - bubbleEstimatedHeightPx - with(density) { 16.dp.toPx() })
                        .coerceAtLeast(screenPaddingPx)
                } else {
                    isTopPlacement = false
                    targetBubbleOffsetY = (animatedBottom + with(density) { 16.dp.toPx() })
                }

                targetBubbleOffsetX = (animatedLeft + with(density) { 20.dp.toPx() })
                    .coerceIn(screenPaddingPx, screenWidthPx - bubbleWidthPx - screenPaddingPx)

                val targetCenterX = animatedLeft + (animatedRight - animatedLeft) / 2f
                targetArrowOffsetX = (targetCenterX - targetBubbleOffsetX - with(density) { 8.dp.toPx() })
                    .coerceIn(with(density) { 24.dp.toPx() }, bubbleWidthPx - with(density) { 36.dp.toPx() })
                targetArrowOffsetY = if (isTopPlacement) (bubbleEstimatedHeightPx - with(density) { 2.dp.toPx() }) else with(density) { (-14).dp.toPx() }
            }

            // 气泡平滑滑动动画（让步骤切换时卡片和箭头轻快滑入）
            val bubbleSpringSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            val animatedBubbleX by animateFloatAsState(targetBubbleOffsetX, bubbleSpringSpec)
            val animatedBubbleY by animateFloatAsState(targetBubbleOffsetY, bubbleSpringSpec)
            val animatedArrowX by animateFloatAsState(targetArrowOffsetX, bubbleSpringSpec)
            val animatedArrowY by animateFloatAsState(targetArrowOffsetY, bubbleSpringSpec)

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedBubbleX.roundToInt(), animatedBubbleY.roundToInt()) }
                    .width(bubbleWidthDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 阻止气泡内部点击冒泡穿透
                    }
            ) {
                // 气泡指示三角小箭头
                Canvas(
                    modifier = Modifier
                        .size(16.dp)
                        .offset { IntOffset(animatedArrowX.roundToInt(), animatedArrowY.roundToInt()) }
                ) {
                    val arrowPath = Path().apply {
                        if (isSidePlacement) {
                            // 朝左的三角箭头
                            moveTo(size.width, 0f)
                            lineTo(0f, size.height / 2f)
                            lineTo(size.width, size.height)
                            close()
                        } else if (isTopPlacement) {
                            // 朝下的三角箭头（当气泡翻转到上方时）
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        } else {
                            // 朝上的三角箭头（常规下方停靠）
                            moveTo(size.width / 2f, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                    }
                    drawPath(
                        path = arrowPath,
                        color = Color.White
                    )
                }

                // 气泡卡片主体（纯白高对比度，确保在暗色遮罩上极具可读性）
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppTokens.Spacing.card),
                        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                    ) {
                        // 步骤指示标与跳过按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 步骤徽章（如 1 / 10）
                            Surface(
                                shape = RoundedCornerShape(AppTokens.Radius.pill),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${currentStep.order} / ${TourStep.entries.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }

                            TextButton(
                                onClick = { manager.skipTour(onComplete) }
                            ) {
                                Text(
                                    text = s.onboardingSkip,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }

                        // 步骤内容平滑过渡动画（微向上淡入滑入）
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically(tween(220)) { 10 })
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            label = "tour_step_content"
                        ) { step ->
                            val (stepTitle, stepDesc) = when (step) {
                                TourStep.SIDEBAR_OVERVIEW -> s.tourStep1Title to s.tourStep1Desc
                                TourStep.SIDEBAR_ACCOUNTS -> s.tourStep2Title to s.tourStep2Desc
                                TourStep.SIDEBAR_MODELS -> s.tourStep3Title to s.tourStep3Desc
                                TourStep.SIDEBAR_ACTIVITY -> s.tourStep4Title to s.tourStep4Desc
                                TourStep.SIDEBAR_SETTINGS -> s.tourStep5Title to s.tourStep5Desc
                                TourStep.OVERVIEW_HERO_PROXY -> s.tourStep6Title to s.tourStep6Desc
                                TourStep.OVERVIEW_HOST_GRID -> s.tourStep7Title to s.tourStep7Desc
                                TourStep.ACCOUNTS_MANAGE -> s.tourStep8Title to s.tourStep8Desc
                                TourStep.MODELS_MANAGE -> s.tourStep9Title to s.tourStep9Desc
                                TourStep.ACTIVITY_PANEL -> s.tourStep10Title to s.tourStep10Desc
                                TourStep.SETTINGS_PANEL -> s.tourStep11Title to s.tourStep11Desc
                                TourStep.ABOUT_REOPEN_CARD -> s.tourStep12Title to s.tourStep12Desc
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)
                            ) {
                                // 标题
                                Text(
                                    text = stepTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )

                                // 详细说明文字
                                Text(
                                    text = stepDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4B5563),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(4.dp))

                        // 底部操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentStep.order > 1) {
                                TextButton(
                                    onClick = { manager.prevStep() }
                                ) {
                                    Text(
                                        text = s.onboardingPrev,
                                        color = Color(0xFF4B5563)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Button(
                                onClick = { manager.nextStep(onComplete) },
                                shape = RoundedCornerShape(AppTokens.Radius.medium),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (currentStep.order == TourStep.entries.size) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(s.onboardingFinish, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(s.onboardingNext, fontWeight = FontWeight.SemiBold)
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
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
