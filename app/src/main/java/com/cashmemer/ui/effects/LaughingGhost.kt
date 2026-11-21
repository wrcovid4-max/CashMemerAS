package com.cashmemer.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Halloween easter egg, a port of the iOS `LaughingGhostView`: a large orange ghost that bobs,
 * smiles and laughs on a loop. Takes no touches. Shown on wide screens (tablets), as on iPad.
 */
@Composable
fun LaughingGhost(modifier: Modifier = Modifier.size(280.dp)) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick = it }
    }
    Canvas(modifier = modifier) {
        if (tick == Long.MIN_VALUE) return@Canvas
        val t = System.currentTimeMillis() / 1000.0
        val s = min(size.width, size.height)
        val bob = (sin(t * 3) * s * 0.02).toFloat()
        val laugh = (0.5 + 0.5 * sin(t * 8)).toFloat()
        drawGhost(Offset(size.width / 2, size.height / 2 + bob), s, laugh)
    }
}

private val GHOST_ORANGE = Color(1f, 122f / 255f, 24f / 255f)
private val GHOST_INK = Color(26f / 255f, 15f / 255f, 46f / 255f)
private val GHOST_CHEEK = Color(1f, 0.41f, 0.71f).copy(alpha = 0.45f)

private fun DrawScope.drawGhost(center: Offset, s: Float, laugh: Float) {
    val r = s * 0.40f
    val domeCenter = Offset(center.x, center.y - s * 0.05f)
    val hemY = center.y + s * 0.36f

    // Body: a dome on top and a scalloped hem at the bottom.
    val body = Path().apply {
        moveTo(center.x - r, domeCenter.y)
        var deg = 180.0
        while (deg <= 360.0) {
            val rad = deg * PI / 180
            lineTo(domeCenter.x + r * cos(rad).toFloat(), domeCenter.y + r * sin(rad).toFloat())
            deg += 4.0
        }
        lineTo(center.x + r, hemY)
        val scallops = 4
        val width = 2 * r
        for (i in 0 until scallops) {
            val xStart = center.x + r - width * i / scallops
            val xEnd = center.x + r - width * (i + 1) / scallops
            quadraticBezierTo((xStart + xEnd) / 2, hemY + s * 0.1f, xEnd, hemY)
        }
        close()
    }
    drawPath(body, GHOST_ORANGE)

    // Rosy cheeks.
    val cheekY = center.y + s * 0.08f
    for (side in listOf(-1f, 1f)) {
        val cx = center.x + side * r * 0.6f
        drawOval(GHOST_CHEEK, topLeft = Offset(cx - s * 0.05f, cheekY - s * 0.03f), size = Size(s * 0.1f, s * 0.06f))
    }

    // Happy closed eyes: upward arcs.
    val eyeY = center.y - s * 0.08f
    for (side in listOf(-1f, 1f)) {
        val ex = center.x + side * r * 0.4f
        val eye = Path().apply {
            moveTo(ex - s * 0.07f, eyeY)
            quadraticBezierTo(ex, eyeY - s * 0.09f, ex + s * 0.07f, eyeY)
        }
        drawPath(eye, GHOST_INK, style = Stroke(width = s * 0.03f, cap = StrokeCap.Round))
    }

    // Laughing mouth: opens and closes.
    val mouthY = center.y + s * 0.12f
    val mouth = Path().apply {
        moveTo(center.x - s * 0.2f, mouthY)
        quadraticBezierTo(center.x, mouthY + s * 0.04f + laugh * s * 0.16f, center.x + s * 0.2f, mouthY)
        close()
    }
    drawPath(mouth, GHOST_INK)
}
