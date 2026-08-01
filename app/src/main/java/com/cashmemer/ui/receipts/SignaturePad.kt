package com.cashmemer.ui.receipts

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * Freehand signature capture. Strokes are kept as point lists so the same data
 * can be rasterised to a PNG for storage without re-reading the canvas.
 */
@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    strokeWidthPx: Float = 4f,
    onSignatureChanged: (String?) -> Unit,
) {
    val strokes = remember { mutableListOf<List<Offset>>().toMutableStateList() }
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
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

                        if (current.size > 1) strokes.add(current)
                        current = emptyList()
                        onSignatureChanged(
                            encodeSignature(strokes, canvasWidth, canvasHeight, strokeWidthPx)
                        )
                    }
                }
        ) {
            canvasWidth = size.width.toInt()
            canvasHeight = size.height.toInt()

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

/** Rasterises captured strokes into a base64 PNG for the receipt row. */
private fun encodeSignature(
    strokes: SnapshotStateList<List<Offset>>,
    width: Int,
    height: Int,
    strokeWidth: Float,
): String? {
    if (strokes.isEmpty() || width <= 0 || height <= 0) return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

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
