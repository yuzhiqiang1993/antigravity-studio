package com.yuzhiqiang.antigravity.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlin.math.roundToLong

/**
 * Antigravity Studio 统一动效系统 (Studio Motion System)：
 * 1. 规范化动效规格（Shimmer 扫光、Spring 物理弹簧、Rolling Number 滚动数字、Crossfade 溶解）
 * 2. 跨模块、跨组件高内聚复用，避免在各卡片/页面中重复造轮子
 */
object StudioMotionDefaults {
    val crossfadeSpec = tween<Float>(
        durationMillis = AppTokens.Motion.durationMedium,
        easing = AppTokens.Motion.fastOutSlowIn
    )

    val gaugeSpringSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )

    val numericSpringSpec = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * 通用高级流光扫光 Brush：
 * 在父级或大盘中共享同一个 Brush，避免为每个骨架块创建独立的 Transition，极大降低 CPU/GPU 负载与 Recomposition。
 */
@Composable
fun rememberStudioShimmerBrush(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    durationMillis: Int = AppTokens.Motion.durationShimmer
): Brush {
    val transition = rememberInfiniteTransition(label = "studio_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "studio_shimmer_translate"
    )

    val baseColor = if (isDark) {
        Color(0xFF1E293B).copy(alpha = 0.65f)
    } else {
        Color(0xFFE2E8F0).copy(alpha = 0.8f)
    }

    val highlightColor = if (isDark) {
        Color(0xFF334155).copy(alpha = 0.95f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.95f)
    }

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 220f, translateAnim + 220f)
    )
}

/**
 * 通用流光扫光 Modifier（共享 Brush 版本，推荐大盘与复杂骨架屏使用）
 */
fun Modifier.studioShimmer(
    brush: Brush,
    shape: Shape = RoundedCornerShape(4.dp)
): Modifier = this.clip(shape).background(brush)

/**
 * 通用高级流光扫光 Modifier（独立单组件便捷版本）
 */
@Composable
fun Modifier.studioShimmer(
    shape: Shape = RoundedCornerShape(4.dp),
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    durationMillis: Int = AppTokens.Motion.durationShimmer
): Modifier {
    val brush = rememberStudioShimmerBrush(isDark = isDark, durationMillis = durationMillis)
    return this.studioShimmer(brush = brush, shape = shape)
}

/**
 * 匀速旋转动画 Modifier (适用于刷新按钮、加载指示器)
 */
@Composable
fun Modifier.studioRotating(
    isRotating: Boolean,
    durationMillis: Int = AppTokens.Motion.durationRotate
): Modifier {
    if (!isRotating) return this

    val transition = rememberInfiniteTransition(label = "studio_rotating_transition")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "studio_rotating_angle"
    )

    return this.rotate(angle)
}

/**
 * 呼吸微脉冲动效 Modifier (适用于活跃状态点、正在使用的徽标等)
 */
@Composable
fun Modifier.studioPulse(
    isPulsing: Boolean,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    durationMillis: Int = 1000
): Modifier {
    if (!isPulsing) return this

    val transition = rememberInfiniteTransition(label = "studio_pulse_transition")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "studio_pulse_scale"
    )

    return this.scale(scale)
}

/**
 * 智能记忆配额百分比动效：
 * - 首次挂载 / Tab 切换直接呈现真实数值（不产生从 100% 突降的烦人动画）
 * - 只有当数据在生命周期内真实发生变动 (Old Value != New Value) 时，以当前展示值为基准平滑触发物理过渡动效
 */
@Composable
fun rememberAnimatedQuotaPercentage(
    targetPercentage: Int,
    animationSpec: AnimationSpec<Float> = StudioMotionDefaults.numericSpringSpec
): State<Float> {
    val progress = remember { Animatable(1f) }
    val currentValue = remember { mutableStateOf(targetPercentage.toFloat()) }
    val lastTarget = remember { mutableStateOf(targetPercentage) }

    LaunchedEffect(targetPercentage) {
        if (lastTarget.value == targetPercentage) {
            return@LaunchedEffect
        }
        val from = currentValue.value
        lastTarget.value = targetPercentage
        val to = targetPercentage.toFloat()

        if (from == to) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = animationSpec
        ) {
            val p = this.value
            currentValue.value = from + (to - from) * p
        }
        currentValue.value = to
    }

    return currentValue
}

/**
 * 智能记忆配额进度比例动效 (0f ~ 1f)
 */
@Composable
fun rememberAnimatedQuotaProgress(
    targetPercentage: Int,
    animationSpec: AnimationSpec<Float> = StudioMotionDefaults.gaugeSpringSpec
): State<Float> {
    val targetFraction = (targetPercentage.coerceIn(0, 100) / 100f)
    val progress = remember { Animatable(1f) }
    val currentValue = remember { mutableStateOf(targetFraction) }
    val lastTarget = remember { mutableStateOf(targetFraction) }

    LaunchedEffect(targetFraction) {
        if (lastTarget.value == targetFraction) {
            return@LaunchedEffect
        }
        val from = currentValue.value
        lastTarget.value = targetFraction

        if (from == targetFraction) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = animationSpec
        ) {
            val p = this.value
            currentValue.value = from + (targetFraction - from) * p
        }
        currentValue.value = targetFraction
    }

    return currentValue
}

/**
 * 智能记忆长整型数字变动动效 (适用于 Tokens、调用次数等大整数)：
 * - 首次挂载直接呈现真实数值（不产生从 0 突增的延迟动画）
 * - 只有当数据在生命周期内真实发生变动 (Old Value != New Value) 时，以当前实际展示数值作为起点基准，
 *   平滑过渡到新数值，绝不重置为 0，且采用 Double 级高精度插值避免 Float 截断跳帧。
 */
@Composable
fun rememberAnimatedNumberLong(
    targetValue: Long,
    animationSpec: AnimationSpec<Float> = StudioMotionDefaults.numericSpringSpec
): State<Long> {
    val progress = remember { Animatable(1f) }
    val currentValue = remember { mutableStateOf(targetValue) }
    val lastTarget = remember { mutableStateOf(targetValue) }

    LaunchedEffect(targetValue) {
        if (lastTarget.value == targetValue) {
            return@LaunchedEffect
        }
        // 以当前正在展示的值为基准起点
        val from = currentValue.value
        lastTarget.value = targetValue

        if (from == targetValue) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = animationSpec
        ) {
            val p = this.value.toDouble()
            currentValue.value = (from + (targetValue - from) * p).roundToLong()
        }
        currentValue.value = targetValue
    }

    return currentValue
}

/**
 * 智能记忆浮点数变动动效 (适用于金额、比率等)：
 * 以当前展示值为基准平滑过渡，杜绝突变重置。
 */
@Composable
fun rememberAnimatedNumberDouble(
    targetValue: Double,
    animationSpec: AnimationSpec<Float> = StudioMotionDefaults.numericSpringSpec
): State<Double> {
    val progress = remember { Animatable(1f) }
    val currentValue = remember { mutableStateOf(targetValue) }
    val lastTarget = remember { mutableStateOf(targetValue) }

    LaunchedEffect(targetValue) {
        if (lastTarget.value == targetValue) {
            return@LaunchedEffect
        }
        val from = currentValue.value
        lastTarget.value = targetValue

        if (from == targetValue) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = animationSpec
        ) {
            val p = this.value.toDouble()
            currentValue.value = from + (targetValue - from) * p
        }
        currentValue.value = targetValue
    }

    return currentValue
}

/**
 * 高质感连续平滑滚动计数器：
 * 采用单一 Text 节点 + 内部物理弹簧长整数插值，避免 AnimatedContent 在高频插值帧导致的字符层叠与狂暴闪烁。
 */
@Composable
fun StudioAnimatedCounterText(
    value: Long,
    formatter: (Long) -> String = { it.toString() },
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    val animatedValue by rememberAnimatedNumberLong(value)
    Text(
        text = formatter(animatedValue),
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}

/**
 * Compose 最新 AnimatedContent 驱动的高质感字符翻牌滚动 Ticker：
 * 专用于离散文本变动（如价格、百分比等），产生轻量级垂直滑入滑出与淡入淡出动效。
 * 注意：禁止将高频每帧变动的连续插值数值传入此组件，以免触发多重重叠翻牌。
 */
@Composable
fun StudioTickerText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart
) {
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically { height -> height / 2 } + androidx.compose.animation.fadeIn(
                animationSpec = tween(160)
            )).togetherWith(
                androidx.compose.animation.slideOutVertically { height -> -height / 2 } + androidx.compose.animation.fadeOut(
                    animationSpec = tween(160)
                )
            )
        },
        contentAlignment = contentAlignment,
        label = "studio_ticker_text",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            textAlign = textAlign
        )
    }
}

/**
 * 统一的滚动数字呈现组件 (支持带单位后缀，如 "89%")
 */
@Composable
fun StudioAnimatedNumericText(
    value: Number,
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    val animatedValue by rememberAnimatedQuotaPercentage(targetPercentage = value.toInt())

    Text(
        text = "${animatedValue.toInt()}$suffix",
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}

/**
 * 统一容器平滑交叉淡入溶解过渡组件
 */
@Composable
fun <T> StudioCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "studio_crossfade",
    content: @Composable (T) -> Unit
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = tween(
            durationMillis = AppTokens.Motion.durationMedium,
            easing = AppTokens.Motion.fastOutSlowIn
        ),
        label = label,
        content = content
    )
}

/**
 * 统一环形物理弹簧进度圈组件 (Studio Circular Quota Ring Gauge)：
 * 采用智能记忆进度动画，首次进入稳定呈现，数据变化时平滑舒展
 */
@Composable
fun StudioCircularGauge(
    percentage: Int,
    barColor: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.45f else 0.65f),
    size: Dp = 22.dp,
    strokeWidth: Dp = 3.dp
) {
    val targetPct = percentage.coerceIn(0, 100)
    val animatedProgress by rememberAnimatedQuotaProgress(targetPercentage = targetPct)

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val arcSize = size.toPx() - strokeWidth.toPx()
        val topLeft = Offset(strokeWidth.toPx() / 2f, strokeWidth.toPx() / 2f)

        // 1. 底槽圆环
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcSize, arcSize),
            style = stroke
        )

        // 2. 动态进度圆环 (正上方 -90 度顺时针弹性展开)
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
