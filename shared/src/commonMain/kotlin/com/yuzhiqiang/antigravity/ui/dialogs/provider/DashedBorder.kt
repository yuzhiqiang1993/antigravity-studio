package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 4.dp,
    gapLength: Dp = 3.5.dp
) = drawWithContent {
    drawContent()
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f
        )
    )
    val halfWidth = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(halfWidth, halfWidth),
        size = Size(size.width - width.toPx(), size.height - width.toPx()),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = stroke
    )
}

