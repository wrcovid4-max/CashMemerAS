package com.cashmemer.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Halloween effect, a port of the iOS `SpiderDropView`. Every two minutes a thin brown wire drops
 * from the top centre and a black spider comes down it. Near the bottom the wire snaps; the spider
 * falls, hangs upside down for a few seconds, turns over, walks away and fades out. Takes no touches.
 */
@Composable
fun SpiderDrop() {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick = it }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Reading tick makes this draw run on every frame.
        if (tick == Long.MIN_VALUE) return@Canvas
        val t = (System.currentTimeMillis() / 1000.0) % CYCLE_SECONDS
        if (t < ACTIVE_SECONDS) drawScene(t)
    }
}

private const val CYCLE_SECONDS = 120.0
private const val ACTIVE_SECONDS = 16.0
private val WIRE_BROWN = Color(0.42f, 0.26f, 0.12f)

private fun smooth(x: Double): Double {
    val c = x.coerceIn(0.0, 1.0)
    return c * c * (3 - 2 * c)
}

private fun DrawScope.drawScene(t: Double) {
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val cx = w * 0.5
    val halfway = h * 0.5
    val breakY = h * 0.88
    val hangY = h * 0.96

    var x = cx
    var y = 0.0
    var angle = 0.0
    var alpha = 1.0
    var walk = 0.0
    var wireAlpha = 1.0
    var showSpider = true

    when {
        t < 0.6 -> showSpider = false
        t < 4.0 -> {
            y = halfway * smooth((t - 0.6) / 3.4)
            walk = t * 4
        }
        t < 6.0 -> {
            y = halfway + (breakY - halfway) * smooth((t - 4.0) / 2.0)
            walk = t * 4
        }
        t < 7.2 -> {
            // The wire snaps and the spider falls, turning upside down.
            val p = (t - 6.0) / 1.2
            y = breakY + (hangY - breakY) * p * p
            angle = PI * p
            wireAlpha = max(0.0, 1 - (t - 6.0))
        }
        t < 10.2 -> {
            // Hanging upside down, legs gently moving.
            y = hangY
            angle = PI
            walk = sin(t * 2) * 0.3
        }
        t < 13.5 -> {
            // Turns over, walks away towards the bottom and fades out.
            val p = (t - 10.2) / 3.3
            x = cx + w * 0.35 * p
            y = hangY + h * 0.06 * p
            angle = PI * (1 - min(1.0, (t - 10.2) / 0.6))
            walk = t * 10
            alpha = 1 - max(0.0, (p - 0.7) / 0.3)
            wireAlpha = 0.0
        }
        else -> {
            showSpider = false
            wireAlpha = 0.0
        }
    }

    // The thin brown wire from the top centre down to the spider.
    if (wireAlpha > 0 && t >= 0.6 && t < 7.2) {
        val end = if (t < 6.0) y else breakY
        drawLine(
            color = WIRE_BROWN.copy(alpha = wireAlpha.toFloat()),
            start = Offset(cx.toFloat(), 0f),
            end = Offset(cx.toFloat(), end.toFloat()),
            strokeWidth = 1.2f * density,
        )
    }

    if (showSpider) drawSpider(x.toFloat(), y.toFloat(), angle, walk, alpha.toFloat(), 1.5f * density)
}

private fun DrawScope.drawSpider(px: Float, py: Float, angle: Double, walk: Double, alpha: Float, scale: Float) {
    val ink = Color.Black.copy(alpha = alpha)
    withTransform({
        translate(left = px, top = py)
        rotate(degrees = Math.toDegrees(angle).toFloat(), pivot = Offset.Zero)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        // Eight legs, four per side, moving in alternating steps.
        val legs = Path()
        for (side in listOf(-1.0, 1.0)) {
            for (i in 0 until 4) {
                val phase = walk + i * 1.2 + (if (side > 0) PI else 0.0)
                val baseY = -6.0 + i * 3.5
                legs.moveTo((side * 3.5).toFloat(), baseY.toFloat())
                legs.lineTo((side * (11 + 2 * sin(phase))).toFloat(), (baseY - 5 + 2.5 * cos(phase)).toFloat())
                legs.lineTo((side * (20 + 3 * sin(phase + 0.6))).toFloat(), (baseY + 6 - cos(phase)).toFloat())
            }
        }
        drawPath(legs, ink, style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Body: abdomen and head.
        drawOval(ink, topLeft = Offset(-7f, -1f), size = Size(14f, 18f))
        drawOval(ink, topLeft = Offset(-5f, -10f), size = Size(10f, 9f))
    }
}
