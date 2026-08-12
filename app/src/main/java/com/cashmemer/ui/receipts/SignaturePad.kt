package com.cashmemer.ui.receipts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Rect
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Freehand signature capture.
 *
 * The pad shows [signatureBase64] as its starting point rather than only what
 * has been drawn in this composition. That matters because the pad sits in a
 * scrolling list: scrolling it off screen, switching apps, or rotating all
 * throw away the in-memory strokes, and a pad that only knew about those went
 * blank even though the receipt still held a perfectly good signature.
 *
 * New strokes are drawn on top of that picture and flattened into it, so
 * signing, leaving, coming back and adding a flourish all works.
 */
@Composable
fun SignaturePad(
    signatureBase64: String?,
    onSignatureChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    strokeWidthPx: Float = 4f,
    locked: Boolean = false,
) {
    // Decoded once per distinct signature, not on every frame.
    val existing = remember(signatureBase64) { decodeSignature(signatureBase64) }
    val existingImage = remember(existing) { existing?.asImageBitmap() }

    val strokes = remember(signatureBase64) { mutableListOf<List<Offset>>() }
    var strokeCount by remember(signatureBase64) { mutableStateOf(0) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                // The pad lives inside a LazyColumn. Claim the gesture in the
                // Initial pass so the list's vertical scroll never steals the
                // stroke — without this, signing just scrolls the page.
                .pointerInput(locked) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Locked pad: leave the touch unconsumed so the list can
                        // still scroll, and never record a stroke — this is the
                        // "stop accidental marks" switch from Settings.
                        if (locked) return@awaitEachGesture
                        down.consume()
                        current = listOf(down.position)

                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }

                            if (change == null || !change.pressed) {
                                dragging = false
                            } else {
                                change.consume()
                                current = current + change.position
                            }
                        }

                        if (current.size > 1) {
                            strokes.add(current)
                            strokeCount = strokes.size
                        }
                        current = emptyList()

                        onSignatureChanged(
                            flatten(
                                base = existing,
                                strokes = strokes,
                                width = canvasWidth,
                                height = canvasHeight,
                                strokeWidth = strokeWidthPx,
                            )
                        )
                    }
                }
        ) {
            canvasWidth = size.width.toInt()
            canvasHeight = size.height.toInt()

            existingImage?.let { image ->
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }

            // strokeCount is read so that finishing a stroke redraws; the list
            // itself is a plain list and would not trigger recomposition.
            @Suppress("UNUSED_EXPRESSION") strokeCount

            (strokes + listOf(current)).forEach { points ->
                if (points.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

private fun decodeSignature(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * Paints [base] and then [strokes] into one PNG.
 *
 * Flattening on every finished stroke keeps a single source of truth: whatever
 * the receipt holds is exactly what was on the pad, whether it was drawn a
 * second ago or restored from a draft written yesterday.
 */
private fun flatten(
    base: Bitmap?,
    strokes: List<List<Offset>>,
    width: Int,
    height: Int,
    strokeWidth: Float,
): String? {
    if (width <= 0 || height <= 0) return null
    if (base == null && strokes.isEmpty()) return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    base?.let {
        canvas.drawBitmap(
            it,
            Rect(0, 0, it.width, it.height),
            Rect(0, 0, width, height),
            null,
        )
    }

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        isAntiAlias = true
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { points ->
        if (points.size < 2) return@forEach
        val path = AndroidPath().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }

    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    bitmap.recycle()
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
