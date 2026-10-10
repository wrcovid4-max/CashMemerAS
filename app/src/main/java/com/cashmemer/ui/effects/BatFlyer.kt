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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A large bat that flies in from the left edge towards the status bar, swoops across to
 * the right and disappears, once every two minutes. Takes no touches.
 */
@Composable
fun BatFlyer() {
    val flight = remember { BatFlight() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                flight.update(System.nanoTime() / 1_000_000_000f)
                tick = now
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Reading tick makes this draw run on every frame.
        if (tick == Long.MIN_VALUE) return@Canvas
        flight.draw(this)
    }
}

private const val INTERVAL_SECONDS = 120f
private const val FLIGHT_SECONDS = 7f

private class BatFlight {
    private var now = 0f
    private var nextStart = -1f
    private var startedAt = -1f

    fun update(seconds: Float) {
        now = seconds
        if (nextStart < 0f) nextStart = seconds + INTERVAL_SECONDS
        if (startedAt < 0f && seconds >= nextStart) {
            startedAt = seconds
            nextStart = seconds + INTERVAL_SECONDS
        }
        if (startedAt >= 0f && seconds - startedAt >= FLIGHT_SECONDS) {
            startedAt = -1f
        }
    }

    fun draw(scope: DrawScope) {
        if (startedAt < 0f) return
        val progress = ((now - startedAt) / FLIGHT_SECONDS).coerceIn(0f, 1f)
        val size = scope.size
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val batSize = unit * 0.24f

        // Two curves: left edge up to the status bar, then down across to the right side.
        val p = pointAt(progress, w, h)
        val ahead = pointAt((progress + 0.01f).coerceAtMost(1f), w, h)
        val angle = atan2(ahead.y - p.y, ahead.x - p.x)
        val flap = sin(now * 14f) * 0.5f + 0.5f

        with(scope) {
            rotate(Math.toDegrees(angle.toDouble()).toFloat(), pivot = Offset(p.x, p.y)) {
                drawBat(Offset(p.x, p.y), batSize, flap)
            }
        }
    }

    private fun pointAt(t: Float, w: Float, h: Float): Offset {
        // First curve: from the left edge (above the middle) up to the top, near the status bar.
        val a = Offset(-w * 0.1f, h * 0.5f)
        val a1 = Offset(w * 0.05f, h * 0.15f)
        val a2 = Offset(w * 0.2f, 40f)
        val a3 = Offset(w * 0.5f, 50f)
        // Second curve: across the top, then down to the right side and off the edge.
        val b1 = Offset(w * 0.8f, 60f)
        val b2 = Offset(w * 0.95f, h * 0.3f)
        val b3 = Offset(w * 1.1f, h * 0.25f)
        return if (t < 0.5f) {
            cubic(a, a1, a2, a3, t * 2f)
        } else {
            cubic(a3, b1, b2, b3, (t - 0.5f) * 2f)
        }
    }

    private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
        val u = 1f - t
        val x = u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x
        val y = u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
        return Offset(x, y)
    }

    /** The bat drawn pointing right, centred on [c]; its wings flap with [flap] (0..1). */
    private fun DrawScope.drawBat(c: Offset, s: Float, flap: Float) {
        val ink = Color(0xFF1A0F2E)
        val wingLift = 0.35f + 0.65f * flap

        // Body and head
        drawOval(
            color = ink,
            topLeft = Offset(c.x - s * 0.12f, c.y - s * 0.16f),
            size = Size(s * 0.24f, s * 0.32f),
        )
        drawCircle(color = ink, radius = s * 0.09f, center = Offset(c.x + s * 0.1f, c.y - s * 0.12f))
        drawCircle(color = Color(0xFFFFD54F), radius = s * 0.025f, center = Offset(c.x + s * 0.13f, c.y - s * 0.14f))

        // Ears
        val ears = Path().apply {
            moveTo(c.x + s * 0.08f, c.y - s * 0.2f)
            lineTo(c.x + s * 0.11f, c.y - s * 0.32f)
            lineTo(c.x + s * 0.15f, c.y - s * 0.18f)
            close()
        }
        drawPath(ears, ink)

        // Wings, one each side, scalloped along the lower edge
        for (side in listOf(-1f, 1f)) {
            val wing = Path().apply {
                moveTo(c.x, c.y)
                quadraticTo(
                    c.x + side * s * 0.35f, c.y - s * 0.55f * wingLift,
                    c.x + side * s * 0.75f, c.y - s * 0.25f * wingLift,
                )
                quadraticTo(c.x + side * s * 0.6f, c.y + s * 0.02f, c.x + side * s * 0.55f, c.y + s * 0.06f)
                quadraticTo(c.x + side * s * 0.45f, c.y + s * 0.12f, c.x + side * s * 0.38f, c.y + s * 0.04f)
                quadraticTo(c.x + side * s * 0.25f, c.y + s * 0.16f, c.x + side * s * 0.12f, c.y + s * 0.06f)
                close()
            }
            drawPath(wing, ink)
        }
    }
}
