package com.cashmemer.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Halloween decorations for every screen: spider webs in the top corners and pumpkins in
 * the bottom corners. Drawn as a fixed layer that takes no touches.
 */
@Composable
fun HalloweenDecor() {
    val web = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val unit = minOf(size.width, size.height)
        val webRadius = unit * 0.22f
        // Each corner's web fans out into the screen: start angle and a quarter-turn sweep.
        drawWeb(0f, 0f, 0f, webRadius, web)
        drawWeb(size.width, 0f, 90f, webRadius, web)
        drawWeb(0f, size.height, 270f, webRadius, web)
        drawWeb(size.width, size.height, 180f, webRadius, web)

        val pumpkinSize = unit * 0.15f
        val inset = unit * 0.1f
        drawPumpkin(inset, size.height - inset, pumpkinSize)
        drawPumpkin(size.width - inset, size.height - inset, pumpkinSize)
    }
}

private fun DrawScope.drawWeb(cx: Float, cy: Float, startAngle: Float, radius: Float, colour: Color) {
    val spokes = 8
    for (i in 0..spokes) {
        val angle = Math.toRadians((startAngle + i * 90.0 / spokes))
        val end = Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
        drawLine(color = colour, start = Offset(cx, cy), end = end, strokeWidth = 1.2f)
    }
    val rings = 6
    for (ring in 1..rings) {
        val r = radius * ring / rings
        drawArc(
            color = colour,
            startAngle = startAngle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 1.1f),
        )
    }
}

private fun DrawScope.drawPumpkin(cx: Float, cy: Float, s: Float) {
    val k = s / 108f
    fun x(v: Float) = cx - 54f * k + v * k
    fun y(v: Float) = cy - 54f * k + v * k

    val body = Path().apply {
        moveTo(x(54f), y(40f))
        cubicTo(x(74f), y(40f), x(86f), y(54f), x(86f), y(68f))
        cubicTo(x(86f), y(86f), x(72f), y(96f), x(54f), y(96f))
        cubicTo(x(36f), y(96f), x(22f), y(86f), x(22f), y(68f))
        cubicTo(x(22f), y(54f), x(34f), y(40f), x(54f), y(40f))
        close()
    }
    drawPath(body, Color(0xE6FF7A18))

    val stem = Path().apply {
        moveTo(x(50f), y(36f))
        lineTo(x(58f), y(36f))
        lineTo(x(57f), y(26f))
        quadraticTo(x(54f), y(22f), x(51f), y(26f))
        close()
    }
    drawPath(stem, Color(0xE63B6E22))

    val face = Color(0xE61A0F2E)
    val leftEye = Path().apply {
        moveTo(x(36f), y(56f)); lineTo(x(50f), y(56f)); lineTo(x(43f), y(68f)); close()
    }
    val rightEye = Path().apply {
        moveTo(x(58f), y(56f)); lineTo(x(72f), y(56f)); lineTo(x(65f), y(68f)); close()
    }
    val mouth = Path().apply {
        moveTo(x(34f), y(76f)); lineTo(x(74f), y(76f)); lineTo(x(68f), y(84f)); lineTo(x(62f), y(76f))
        lineTo(x(54f), y(85f)); lineTo(x(46f), y(76f)); lineTo(x(40f), y(84f)); close()
    }
    drawPath(leftEye, face)
    drawPath(rightEye, face)
    drawPath(mouth, face)
}
