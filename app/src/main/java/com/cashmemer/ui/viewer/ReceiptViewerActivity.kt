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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What a tap on the page does. */
private enum class ViewerTool { PAN, TEXT, CHECK, CROSS }

/** The stamp colours, shared by the toolbar and the marks on the page. */
private val MarkGreen = Color(0xFF1B7F37)
private val MarkRed = Color(0xFFC02727)

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
    var pendingText by remember { mutableStateOf<PendingText?>(null) }

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
                    state.pages.isEmpty() -> Text(
                        text = stringResource(R.string.viewer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        val pagerState = rememberPagerState(
                            initialPage = state.pageIndex,
                            pageCount = { state.pages.size },
                        )

                        // Swiping the pager and tapping a page chip are the two
                        // ways to change page; each keeps the other in step, and
                        // a search hit that lives on the other page turns to it.
                        LaunchedEffect(pagerState.currentPage) {
                            viewModel.showPage(pagerState.currentPage)
                        }
                        LaunchedEffect(state.pageIndex) {
                            if (state.pageIndex != pagerState.currentPage) {
                                pagerState.animateScrollToPage(state.pageIndex)
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            // Only the Pan tool owns horizontal drags; while a
                            // marking tool is active the pager stays put so a
                            // stamp near the edge is not read as a page turn.
                            userScrollEnabled = tool == ViewerTool.PAN,
                            modifier = Modifier.fillMaxSize(),
                        ) { index ->
                            val viewerPage = state.pages[index]
                            PageCanvas(
                                state = state,
                                page = viewerPage,
                                tool = tool,
                                onPlace = { fraction ->
                                    val pageNumber = viewerPage.layout.page
                                    when (tool) {
                                        ViewerTool.PAN -> Unit
                                        ViewerTool.TEXT -> pendingText =
                                            PendingText(pageNumber, fraction)
                                        ViewerTool.CHECK -> viewModel.addAnnotation(
                                            ReceiptAnnotation(
                                                page = pageNumber,
                                                x = fraction.x,
                                                y = fraction.y,
                                                kind = AnnotationKind.CHECK,
                                            )
                                        )
                                        ViewerTool.CROSS -> viewModel.addAnnotation(
                                            ReceiptAnnotation(
                                                page = pageNumber,
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
                }
            }

            ViewerBottomBar(
                tool = tool,
                onToolChange = { tool = it },
                onUndo = viewModel::undo,
                onClear = viewModel::clearPage,
                pageCount = state.pages.size,
                pageIndex = state.pageIndex,
                onSelectPage = viewModel::showPage,
            )
        }
    }

    pendingText?.let { pending ->
        TextMarkDialog(
            onDismiss = { pendingText = null },
            onConfirm = { typed ->
                viewModel.addAnnotation(
                    ReceiptAnnotation(
                        page = pending.page,
                        x = pending.at.x,
                        y = pending.at.y,
                        kind = AnnotationKind.TEXT,
                        text = typed,
                    )
                )
                pendingText = null
            },
        )
    }
}

/** A tap waiting for its text, and the page it landed on. */
private data class PendingText(val page: Int, val at: Offset)

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
    page: ViewerPage,
    tool: ViewerTool,
    onPlace: (Offset) -> Unit,
) {
    val pageNumber = page.layout.page
    val image = remember(page) { page.bitmap.asImageBitmap() }

    // Keyed on this page, not the selected index, so each page in the pager
    // keeps its own zoom and scroll position.
    var container by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(page) { mutableStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }

    val textColour = MaterialTheme.colorScheme.error
    val highlight = MaterialTheme.colorScheme.tertiary
    val scope = rememberCoroutineScope()

    // A receipt page is much taller than the screen, so vertical movement is
    // the whole interaction. Dragging with no fling meant swiping a dozen times
    // to reach the total; this carries the page on after the finger leaves and
    // eases to a stop against the edges.
    val flingSpec = rememberSplineBasedDecay<Float>()

    fun limits(): ClosedFloatingPointRange<Float> {
        val frame = frameFor(container, image.width, image.height, zoom, Offset.Zero)
        val slack = (frame.height - container.height).coerceAtLeast(0f)
        return -slack..0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(page) {
                // One gesture loop, because the crux is what gets *consumed*.
                // The old code used detectTransformGestures, which swallows
                // single-finger horizontal drags — so the pager underneath never
                // saw a swipe and the pages would not turn. Here a horizontal
                // drag at rest zoom is deliberately left unconsumed so it bubbles
                // up to the pager, while vertical drags (scroll) and pinches
                // (zoom) are consumed as before.
                val tracker = VelocityTracker()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    tracker.resetTracking()

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        val pan = event.calculatePan()
                        val zoomChange = event.calculateZoom()
                        val range = limits()

                        if (pressed.size >= 2) {
                            // Pinch: zoom, and pan within the zoomed page.
                            zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = (offset.y + pan.y)
                                    .coerceIn(range.start, range.endInclusive),
                            )
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            if (zoom > 1f) {
                                // Zoomed in: the finger pans the page in both axes.
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = (offset.y + pan.y)
                                        .coerceIn(range.start, range.endInclusive),
                                )
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                            } else {
                                // At rest zoom, only vertical is ours; a mostly
                                // horizontal drag is left for the pager to turn
                                // the page with.
                                val newY = (offset.y + pan.y)
                                    .coerceIn(range.start, range.endInclusive)
                                if (kotlin.math.abs(pan.y) > kotlin.math.abs(pan.x) &&
                                    newY != offset.y
                                ) {
                                    offset = offset.copy(y = newY)
                                    change.consume()
                                    tracker.addPosition(
                                        change.uptimeMillis,
                                        change.position,
                                    )
                                }
                            }
                        }
                    } while (true)

                    // Carry a vertical flick on after the finger lifts.
                    val velocity = tracker.calculateVelocity().y
                    val range = limits()
                    if (range.start != 0f && kotlin.math.abs(velocity) > 1f) {
                        scope.launch {
                            Animatable(offset.y).animateDecay(velocity, flingSpec) {
                                offset = offset.copy(
                                    y = value.coerceIn(range.start, range.endInclusive),
                                )
                            }
                        }
                    }
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
                if (hit.page != pageNumber) return@forEachIndexed
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
                .filter { it.page == pageNumber }
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
                color = MarkGreen,
                start = Offset(x - size, y),
                end = Offset(x - size / 3f, y + size * 0.7f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = MarkGreen,
                start = Offset(x - size / 3f, y + size * 0.7f),
                end = Offset(x + size, y - size),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        AnnotationKind.CROSS -> {
            drawLine(
                color = MarkRed,
                start = Offset(x - size, y - size),
                end = Offset(x + size, y + size),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = MarkRed,
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

/**
 * The whole bottom control surface: the marking tools, undo/clear, and page
 * navigation, on one raised sheet.
 *
 * Rebuilt to read like a real editor's toolbar — the four tools sit in a single
 * segmented track that slides a pill under the active one, the destructive Clear
 * is set apart on the right, and the pages are their own segmented control. The
 * point is that the controls look deliberate rather than like loose buttons
 * dropped on a black strip.
 */
@Composable
private fun ViewerBottomBar(
    tool: ViewerTool,
    onToolChange: (ViewerTool) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    pageCount: Int,
    pageIndex: Int,
    onSelectPage: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The four tools as one segmented track.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        ToolSegment(
                            Icons.Filled.OpenWith, R.string.tool_pan,
                            tool == ViewerTool.PAN,
                        ) { onToolChange(ViewerTool.PAN) }
                        ToolSegment(
                            Icons.Filled.TextFields, R.string.tool_text,
                            tool == ViewerTool.TEXT,
                        ) { onToolChange(ViewerTool.TEXT) }
                        ToolSegment(
                            Icons.Filled.Check, R.string.tool_tick,
                            tool == ViewerTool.CHECK,
                            selectedColour = MarkGreen,
                        ) { onToolChange(ViewerTool.CHECK) }
                        ToolSegment(
                            Icons.Filled.Close, R.string.tool_cross,
                            tool == ViewerTool.CROSS,
                            selectedColour = MarkRed,
                        ) { onToolChange(ViewerTool.CROSS) }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                RoundIconButton(
                    icon = Icons.Filled.Undo,
                    label = stringResource(R.string.action_undo),
                    onClick = onUndo,
                )
                Spacer(modifier = Modifier.width(8.dp))
                RoundIconButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.action_clear),
                    onClick = onClear,
                    danger = true,
                )
            }

            if (pageCount > 1) {
                PageSelector(
                    pageCount = pageCount,
                    pageIndex = pageIndex,
                    onSelect = onSelectPage,
                )
            }
        }
    }
}

/**
 * One cell in the tool track. Icon only — the active tool raises a coloured
 * pill, which is the whole signal, so the labels underneath were just noise.
 * The name still rides along as the icon's content description for screen
 * readers and long-press tooltips.
 */
@Composable
private fun ToolSegment(
    icon: ImageVector,
    labelRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedColour: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) selectedColour else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A soft round icon button — Undo, and the red Clear. */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (danger) MarkRed.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (danger) MarkRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Page 1 / Page 2 as a segmented control with arrows on either side. */
@Composable
private fun PageSelector(pageCount: Int, pageIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { onSelect(pageIndex - 1) },
            enabled = pageIndex > 0,
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.previous_match),
            )
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                repeat(pageCount) { index ->
                    val selected = index == pageIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(index) }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.page_number, index + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { onSelect(pageIndex + 1) },
            enabled = pageIndex < pageCount - 1,
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.next_match),
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
