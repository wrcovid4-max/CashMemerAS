package com.cashmemer.ui.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.model.AnnotationKind
import com.cashmemer.core.model.ReceiptAnnotation
import com.cashmemer.core.ui.theme.CashMemerTheme
import com.cashmemer.print.ReceiptOutput
import kotlin.math.roundToInt

/** What a tap on the page does. */
private enum class ViewerTool { PAN, TEXT, CHECK, CROSS }

/**
 * The in-app memo viewer: the real receipt, zoomable, searchable, and markable
 * with text, ticks and crosses — the screen the original app opened when a row
 * in History was tapped.
 */
class ReceiptViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receiptId = intent.getLongExtra(EXTRA_RECEIPT_ID, 0L)

        setContent {
            CashMemerTheme {
                ReceiptViewerScreen(receiptId = receiptId, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_RECEIPT_ID = "receipt_id"
    }
}

@Composable
private fun ReceiptViewerScreen(
    receiptId: Long,
    onClose: () -> Unit,
    viewModel: ReceiptViewerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var tool by remember { mutableStateOf(ViewerTool.PAN) }
    var searching by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(receiptId) { viewModel.load(receiptId) }

    state.message?.let { text ->
        LaunchedEffect(text) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            ViewerTopBar(
                title = stringResource(R.string.viewer_title, receiptId),
                searching = searching,
                onToggleSearch = {
                    searching = !searching
                    if (!searching) viewModel.search("")
                },
                onBack = onClose,
                onShare = {
                    viewModel.renderForOutput(forPrinting = false) { file ->
                        ReceiptOutput.share(context, file)
                    }
                },
                onPrint = {
                    viewModel.renderForOutput(forPrinting = true) { file ->
                        ReceiptOutput.print(
                            context,
                            file,
                            context.getString(R.string.print_job_receipt, receiptId),
                        )
                    }
                },
                onSave = { viewModel.save() },
                saveEnabled = state.dirty,
            )

            if (searching) {
                SearchBar(
                    query = state.query,
                    hitCount = state.hits.size,
                    hitIndex = state.hitIndex,
                    onQueryChange = viewModel::search,
                    onPrevious = viewModel::previousHit,
                    onNext = viewModel::nextHit,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.page == null -> Text(
                        text = stringResource(R.string.viewer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> PageCanvas(
                        state = state,
                        tool = tool,
                        onPlace = { fraction ->
                            when (tool) {
                                ViewerTool.PAN -> Unit
                                ViewerTool.TEXT -> pendingText = fraction
                                ViewerTool.CHECK -> viewModel.addAnnotation(
                                    ReceiptAnnotation(
                                        page = state.pageNumber,
                                        x = fraction.x,
                                        y = fraction.y,
                                        kind = AnnotationKind.CHECK,
                                    )
                                )
                                ViewerTool.CROSS -> viewModel.addAnnotation(
                                    ReceiptAnnotation(
                                        page = state.pageNumber,
                                        x = fraction.x,
                                        y = fraction.y,
                                        kind = AnnotationKind.CROSS,
                                    )
                                )
                            }
                        },
                    )
                }
            }

            ToolBar(
                tool = tool,
                onToolChange = { tool = it },
                onUndo = viewModel::undo,
                onClear = viewModel::clearPage,
            )

            PageBar(
                pageCount = state.pages.size,
                pageIndex = state.pageIndex,
                onSelect = viewModel::showPage,
            )
        }
    }

    pendingText?.let { at ->
        TextMarkDialog(
            onDismiss = { pendingText = null },
            onConfirm = { typed ->
                viewModel.addAnnotation(
                    ReceiptAnnotation(
                        page = state.pageNumber,
                        x = at.x,
                        y = at.y,
                        kind = AnnotationKind.TEXT,
                        text = typed,
                    )
                )
                pendingText = null
            },
        )
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    searching: Boolean,
    saveEnabled: Boolean,
    onToggleSearch: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
        IconButton(onClick = onToggleSearch) {
            Icon(
                if (searching) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = stringResource(R.string.search_in_receipt),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        IconButton(onClick = onSave, enabled = saveEnabled) {
            Icon(
                Icons.Filled.Save,
                contentDescription = stringResource(R.string.action_save),
                tint = if (saveEnabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                },
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        IconButton(onClick = onPrint) {
            Icon(
                Icons.Filled.Print,
                contentDescription = stringResource(R.string.action_print),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    hitCount: Int,
    hitIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_in_receipt)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                query.isBlank() -> ""
                hitCount == 0 -> stringResource(R.string.no_matches)
                else -> stringResource(R.string.match_position, hitIndex + 1, hitCount)
            },
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        IconButton(onClick = onPrevious, enabled = hitCount > 0) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.previous_match),
            )
        }
        IconButton(onClick = onNext, enabled = hitCount > 0) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.next_match),
            )
        }
    }
}

/**
 * Draws the page and everything on top of it.
 *
 * The transform is kept as plain numbers — a fit-to-width base scale, a user
 * zoom and an offset — rather than a graphics layer, because every tap has to
 * be turned back into a position on the page. Doing the maths in one direction
 * only is what keeps a stamped tick landing under the finger.
 */
@Composable
private fun PageCanvas(
    state: ViewerState,
    tool: ViewerTool,
    onPlace: (Offset) -> Unit,
) {
    val page = state.page ?: return
    val image = remember(page) { page.bitmap.asImageBitmap() }

    var container by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(state.pageIndex) { mutableStateOf(1f) }
    var offset by remember(state.pageIndex) { mutableStateOf(Offset.Zero) }

    val textColour = MaterialTheme.colorScheme.error
    val highlight = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                    offset += pan
                }
            }
            .pointerInput(tool, container, zoom, offset) {
                detectTapGestures { tap ->
                    if (tool == ViewerTool.PAN) return@detectTapGestures
                    val frame = frameFor(container, image.width, image.height, zoom, offset)
                    if (frame.width <= 0f || frame.height <= 0f) return@detectTapGestures
                    val fx = (tap.x - frame.left) / frame.width
                    val fy = (tap.y - frame.top) / frame.height
                    if (fx in 0f..1f && fy in 0f..1f) onPlace(Offset(fx, fy))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = frameFor(container, image.width, image.height, zoom, offset)
            if (frame.width <= 0f || frame.height <= 0f) return@Canvas

            drawImage(
                image = image,
                dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
            )

            // Search hits sit under the marks — they are a hint, not content.
            // Indexed over the whole list, not the visible page, so "3 of 7"
            // and the darker box always mean the same hit.
            state.hits.forEachIndexed { index, hit ->
                if (hit.page != state.pageNumber) return@forEachIndexed
                drawRect(
                    color = highlight.copy(
                        alpha = if (index == state.hitIndex) 0.45f else 0.20f,
                    ),
                    topLeft = Offset(
                        frame.left + hit.left * frame.width,
                        frame.top + hit.top * frame.height,
                    ),
                    size = Size(
                        (hit.right - hit.left) * frame.width,
                        (hit.bottom - hit.top) * frame.height,
                    ),
                )
            }

            state.annotations
                .filter { it.page == state.pageNumber }
                .forEach { mark ->
                    drawMark(
                        mark = mark,
                        origin = Offset(frame.left, frame.top),
                        width = frame.width,
                        height = frame.height,
                        textColour = textColour,
                    )
                }
        }
    }
}

/** Where the page picture sits inside the viewport, in pixels. */
private data class PageFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun frameFor(
    container: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
    offset: Offset,
): PageFrame {
    if (container.width == 0 || imageWidth == 0) return PageFrame(0f, 0f, 0f, 0f)

    val scale = container.width.toFloat() / imageWidth * zoom
    val width = imageWidth * scale
    val height = imageHeight * scale

    // Centre whatever is smaller than the viewport; otherwise let the drag move
    // it, but never far enough to lose the page off an edge.
    val left = if (width <= container.width) {
        (container.width - width) / 2f
    } else {
        offset.x.coerceIn(container.width - width, 0f)
    }
    val top = if (height <= container.height) {
        (container.height - height) / 2f
    } else {
        offset.y.coerceIn(container.height - height, 0f)
    }

    return PageFrame(left, top, width, height)
}

/** Same shapes the PDF renderer stamps, so screen and paper agree. */
private fun DrawScope.drawMark(
    mark: ReceiptAnnotation,
    origin: Offset,
    width: Float,
    height: Float,
    textColour: Color,
) {
    val x = origin.x + mark.x * width
    val y = origin.y + mark.y * height
    // The PDF stamps at 11pt on a 600pt page; matching that ratio keeps a mark
    // the same size relative to the memo however far it is zoomed in.
    val size = width * (11f / 600f)
    val strokeWidth = size * 0.36f

    when (mark.kind) {
        AnnotationKind.CHECK -> {
            drawLine(
                color = Color(0xFF1B7F37),
                start = Offset(x - size, y),
                end = Offset(x - size / 3f, y + size * 0.7f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFF1B7F37),
                start = Offset(x - size / 3f, y + size * 0.7f),
                end = Offset(x + size, y - size),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        AnnotationKind.CROSS -> {
            drawLine(
                color = Color(0xFFC02727),
                start = Offset(x - size, y - size),
                end = Offset(x + size, y + size),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFC02727),
                start = Offset(x + size, y - size),
                end = Offset(x - size, y + size),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        AnnotationKind.TEXT -> if (mark.text.isNotBlank()) {
            drawContext.canvas.nativeCanvas.drawText(
                mark.text,
                x,
                y,
                android.graphics.Paint().apply {
                    color = textColour.toArgb()
                    textSize = width * (17f / 600f)
                    isAntiAlias = true
                    isFakeBoldText = true
                },
            )
        }
    }
}

@Composable
private fun ToolBar(
    tool: ViewerTool,
    onToolChange: (ViewerTool) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(Icons.Filled.OpenWith, R.string.tool_pan, tool == ViewerTool.PAN) {
            onToolChange(ViewerTool.PAN)
        }
        ToolButton(Icons.Filled.TextFields, R.string.tool_text, tool == ViewerTool.TEXT) {
            onToolChange(ViewerTool.TEXT)
        }
        ToolButton(Icons.Filled.Check, R.string.tool_tick, tool == ViewerTool.CHECK) {
            onToolChange(ViewerTool.CHECK)
        }
        ToolButton(Icons.Filled.Close, R.string.tool_cross, tool == ViewerTool.CROSS) {
            onToolChange(ViewerTool.CROSS)
        }

        Box(modifier = Modifier.weight(1f))

        IconButton(onClick = onUndo) {
            Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
        }
        TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PageBar(pageCount: Int, pageIndex: Int, onSelect: (Int) -> Unit) {
    if (pageCount <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { index ->
            FilterChip(
                selected = index == pageIndex,
                onClick = { onSelect(index) },
                label = { Text(stringResource(R.string.page_number, index + 1)) },
            )
        }
    }
}

@Composable
private fun TextMarkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_text_mark)) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.mark_text)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typed.trim()) },
                enabled = typed.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Opens the viewer on one receipt. */
class ViewReceiptContract : ActivityResultContract<Long, Unit>() {

    override fun createIntent(context: Context, input: Long): Intent =
        Intent(context, ReceiptViewerActivity::class.java)
            .putExtra(ReceiptViewerActivity.EXTRA_RECEIPT_ID, input)

    override fun parseResult(resultCode: Int, intent: Intent?) = Unit
}

/** Opens the viewer without needing a result launcher. */
fun Context.openReceiptViewer(receiptId: Long) {
    startActivity(
        Intent(this, ReceiptViewerActivity::class.java)
            .putExtra(ReceiptViewerActivity.EXTRA_RECEIPT_ID, receiptId)
    )
}
