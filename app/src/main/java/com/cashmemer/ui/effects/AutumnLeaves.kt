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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cards and info buttons mark themselves with this modifier. Autumn leaves can rest on
 * them. Marking is passive: it only records where the element sits.
 */
fun Modifier.leafPerch(): Modifier = this.onGloballyPositioned { coords ->
    LeafPerches.report(System.identityHashCode(coords), coords.boundsInRoot(), nowSeconds())
}

private fun nowSeconds(): Float = System.nanoTime() / 1_000_000_000f

/** Where leaves can rest: the recently reported bounds of marked elements. */
object LeafPerches {
    private val rects = HashMap<Int, Pair<Rect, Float>>()

    fun report(key: Int, rect: Rect, seconds: Float) {
        rects[key] = rect to seconds
    }

    fun active(seconds: Float): List<Rect> =
        rects.values.filter { seconds - it.second < 1f }.map { it.first }
}

/** Falling autumn leaves across the whole app. Non-interactive: touches pass through. */
@Composable
fun AutumnLeaves() {
    val sim = remember { LeafSimulation() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                sim.step(dt, nowSeconds())
                tick = now
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Reading tick makes this draw run on every frame.
        if (tick == Long.MIN_VALUE) return@Canvas
        sim.size = size
        sim.leaves.forEach { drawLeaf(it) }
    }
}

private enum class LeafState { FALLING, RESTING, PILED, LEAVING }

private class Leaf(
    var x: Float,
    var y: Float,
    val size: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    val spin: Float,
    val color: Color,
    val swayPhase: Float,
) {
    var state = LeafState.FALLING
    var restUntil = 0f
    var prevBottom = 0f
}

private val leafColours = listOf(
    Color(0xFFE07B39),
    Color(0xFFB33A1F),
    Color(0xFFE0A526),
    Color(0xFF8A4B1E),
    Color(0xFFC9592B),
)

private class LeafSimulation {
    var size: Size = Size.Zero
    val leaves = mutableListOf<Leaf>()
    private var time = 0f
    private var spawnTimer = 0f
    private val random = Random(2026)
    private val maxFalling = 20
    private val maxPiled = 28

    fun step(dt: Float, seconds: Float) {
        if (size == Size.Zero) return
        time += dt
        spawnTimer -= dt
        val falling = leaves.count { it.state == LeafState.FALLING || it.state == LeafState.LEAVING }
        if (spawnTimer <= 0f && falling < maxFalling) {
            spawn()
            spawnTimer = 0.4f + random.nextFloat() * 0.7f
        }
        val wind = 80f + 45f * sin(time * 0.6f) + 25f * sin(time * 1.7f)
        val perches = LeafPerches.active(seconds)
        val iterator = leaves.iterator()
        while (iterator.hasNext()) {
            val leaf = iterator.next()
            when (leaf.state) {
                LeafState.FALLING -> {
                    // The wind pushes leftwards, easing towards its current speed.
                    leaf.vx += (-wind - leaf.vx) * dt * 1.5f
                    leaf.x += leaf.vx * dt + sin(time * 2f + leaf.swayPhase) * 26f * dt
                    leaf.y += leaf.vy * dt
                    leaf.angle += leaf.spin * dt
                    val bottom = leaf.y + leaf.size * 0.6f
                    val landing = perches.firstOrNull { r ->
                        leaf.x in r.left..r.right &&
                            leaf.prevBottom <= r.top && bottom >= r.top
                    }
                    if (landing != null && random.nextFloat() < 0.35f) {
                        leaf.y = landing.top - leaf.size * 0.6f
                        leaf.state = LeafState.RESTING
                        leaf.restUntil = time + 3f + random.nextFloat() * 3f
                    } else if (bottom >= size.height - 18f) {
                        val pileCount = leaves.count { it.state == LeafState.PILED }
                        if (pileCount < maxPiled && random.nextFloat() < 0.4f) {
                            leaf.state = LeafState.PILED
                            leaf.x = random.nextFloat() * size.width
                            leaf.y = size.height - 10f
                        } else {
                            leaf.state = LeafState.LEAVING
                        }
                    }
                    leaf.prevBottom = bottom
                }
                LeafState.RESTING -> {
                    leaf.restUntil -= dt
                    if (leaf.restUntil <= 0f) {
                        leaf.state = LeafState.FALLING
                        leaf.vy = 60f + random.nextFloat() * 40f
                        leaf.prevBottom = leaf.y + leaf.size * 0.6f
                    }
                }
                LeafState.LEAVING -> {
                    leaf.x += leaf.vx * dt
                    leaf.y += leaf.vy * dt
                    leaf.angle += leaf.spin * dt
                }
                LeafState.PILED -> Unit
            }
            val gone = (leaf.state == LeafState.FALLING && leaf.x < -60f) ||
                (leaf.state == LeafState.LEAVING && (leaf.y > size.height + 40f || leaf.x < -60f))
            if (gone) iterator.remove()
        }
    }

    private fun spawn() {
        leaves.add(
            Leaf(
                x = size.width + random.nextFloat() * 120f,
                y = -random.nextFloat() * 90f - 10f,
                size = 18f + random.nextFloat() * 10f,
                vx = -90f,
                vy = 70f + random.nextFloat() * 50f,
                angle = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 120f,
                color = leafColours[random.nextInt(leafColours.size)],
                swayPhase = random.nextFloat() * 6.28f,
            ).also { it.prevBottom = it.y + it.size * 0.6f }
        )
    }
}

private fun DrawScope.drawLeaf(leaf: Leaf) {
    val s = leaf.size
    val shape = Path().apply {
        moveTo(0f, -s / 2f)
        quadraticTo(s / 2f, -s / 4f, 0f, s / 2f)
        quadraticTo(-s / 2f, -s / 4f, 0f, -s / 2f)
        close()
    }
    rotate(leaf.angle, Offset(leaf.x, leaf.y)) {
        translate(leaf.x, leaf.y) {
            drawPath(shape, leaf.color)
            drawLine(
                color = Color(0x66000000),
                start = Offset(0f, -s / 2f),
                end = Offset(0f, s / 2f),
                strokeWidth = 1.2f,
            )
        }
    }
}
