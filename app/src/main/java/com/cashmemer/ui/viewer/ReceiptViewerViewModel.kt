package com.cashmemer.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.ReceiptAnnotationCodec
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptAnnotation
import com.cashmemer.print.ReceiptOutput
import com.cashmemer.print.ReceiptPageLayout
import com.cashmemer.print.ReceiptPages
import com.cashmemer.print.ReceiptPdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One rendered page: the picture, its measured layout, and its text. */
data class ViewerPage(
    val bitmap: Bitmap,
    val layout: ReceiptPageLayout,
)

/** A place in the receipt where the search text was found. */
data class SearchHit(
    /** 1-based page. */
    val page: Int,
    /** All four as fractions of the page, so they survive any zoom. */
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class ViewerState(
    val loading: Boolean = true,
    val receipt: Receipt? = null,
    val pages: List<ViewerPage> = emptyList(),
    /** Index into [pages], not a page number. */
    val pageIndex: Int = 0,
    val annotations: List<ReceiptAnnotation> = emptyList(),
    val dirty: Boolean = false,
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val hitIndex: Int = 0,
    val message: String? = null,
) {
    val page: ViewerPage? get() = pages.getOrNull(pageIndex)
    val pageNumber: Int get() = page?.layout?.page ?: 1
    val currentHit: SearchHit? get() = hits.getOrNull(hitIndex)
}

/**
 * Backs the in-app memo viewer.
 *
 * The picture behind the marks is the real PDF, rendered by the same code that
 * prints — so what is on screen is what comes out of the printer. The marks
 * themselves are drawn live on top rather than baked into that picture, which
 * is why the background is deliberately rendered from a copy of the receipt
 * with its annotations stripped: otherwise every mark would appear twice.
 */
class ReceiptViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun load(receiptId: Long) {
        viewModelScope.launch {
            val receipt = repository.receipt(receiptId)
            if (receipt == null) {
                _state.update {
                    it.copy(loading = false, message = str(R.string.msg_receipt_missing, receiptId))
                }
                return@launch
            }

            val layouts = ReceiptPdfRenderer.pageLayouts(receipt, ReceiptPages.BOTH)
            val pages = renderPages(receipt, layouts)

            _state.update {
                it.copy(
                    loading = false,
                    receipt = receipt,
                    pages = pages,
                    annotations = ReceiptAnnotationCodec.decode(receipt.annotationsJson),
                )
            }
        }
    }

    /** Rasterises the memo. Marks are stripped so the overlay owns them alone. */
    private suspend fun renderPages(
        receipt: Receipt,
        layouts: List<ReceiptPageLayout>,
    ): List<ViewerPage> = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val clean = receipt.copy(annotationsJson = "[]")
        val file = File(app.cacheDir, "viewer/receipt-${receipt.id}.pdf")

        ReceiptPdfRenderer.render(app, listOf(clean), ReceiptPages.BOTH, file)
            .getOrElse { return@withContext emptyList() }

        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    (0 until renderer.pageCount).mapNotNull { index ->
                        val layout = layouts.getOrNull(index) ?: return@mapNotNull null
                        val page = renderer.openPage(index)
                        try {
                            val scale = RENDER_WIDTH_PX / page.width.toFloat()
                            val bitmap = Bitmap.createBitmap(
                                RENDER_WIDTH_PX,
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            // PdfRenderer composites onto whatever is there, so
                            // an unfilled bitmap comes out with black gaps.
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            ViewerPage(bitmap, layout)
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        }.getOrDefault(emptyList())
    }

    fun showPage(index: Int) = _state.update { it.copy(pageIndex = index) }

    fun addAnnotation(mark: ReceiptAnnotation) = _state.update {
        it.copy(annotations = it.annotations + mark, dirty = true)
    }

    /** Removes the most recent mark on the page being looked at. */
    fun undo() = _state.update { current ->
        val page = current.pageNumber
        val last = current.annotations.indexOfLast { it.page == page }
        if (last < 0) current
        else current.copy(
            annotations = current.annotations.filterIndexed { i, _ -> i != last },
            dirty = true,
        )
    }

    fun clearPage() = _state.update { current ->
        val page = current.pageNumber
        if (current.annotations.none { it.page == page }) current
        else current.copy(
            annotations = current.annotations.filterNot { it.page == page },
            dirty = true,
        )
    }

    /**
     * Writes the marks back and re-reads the receipt, so anything rendered from
     * here on — share, print, export — carries them.
     */
    fun save(onSaved: (Receipt) -> Unit = {}) {
        val current = _state.value
        val receipt = current.receipt ?: return
        viewModelScope.launch {
            repository.setAnnotations(receipt.id, current.annotations)
            val fresh = repository.receipt(receipt.id) ?: receipt
            _state.update {
                it.copy(receipt = fresh, dirty = false, message = str(R.string.marks_saved))
            }
            onSaved(fresh)
        }
    }

    /**
     * Renders the memo *with* its marks for sharing or printing. Saves first
     * when there are unsaved marks — exporting a memo without the tick that was
     * just put on it would be its own kind of wrong.
     */
    fun renderForOutput(forPrinting: Boolean, onReady: (File) -> Unit) {
        val current = _state.value
        val receipt = current.receipt ?: return
        viewModelScope.launch {
            if (current.dirty) repository.setAnnotations(receipt.id, current.annotations)
            val fresh = repository.receipt(receipt.id) ?: receipt
            _state.update { it.copy(receipt = fresh, dirty = false) }

            val app = getApplication<Application>()
            // Sharing always carries both pages; printing follows the Mass
            // Print setting, which is the whole point of that setting.
            val pages = if (forPrinting) {
                ReceiptOutput.pagesFor(SettingsStore(app).settings.first().massPrint)
            } else {
                ReceiptPages.BOTH
            }
            ReceiptPdfRenderer
                .render(app, listOf(fresh), pages, ReceiptOutput.outputFile(app, listOf(fresh)))
                .onSuccess(onReady)
                .onFailure { _state.update { s -> s.copy(message = str(R.string.msg_pdf_failed)) } }
        }
    }

    /**
     * Finds the text on the pages. Matching runs against the layout produced by
     * the renderer means a hit box sits exactly on the ink, rather than on a
     * guess at where that line probably went.
     */
    fun search(query: String) = _state.update { current ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@update current.copy(query = query, hits = emptyList(), hitIndex = 0)

        val hits = current.pages.flatMap { page ->
            val layout = page.layout
            layout.runs
                .filter { it.text.contains(trimmed, ignoreCase = true) }
                .map { run ->
                    SearchHit(
                        page = layout.page,
                        left = run.left / layout.width,
                        top = run.top / layout.height,
                        right = run.right / layout.width,
                        bottom = run.bottom / layout.height,
                    )
                }
        }

        current.copy(query = query, hits = hits, hitIndex = 0)
            .let { if (hits.isEmpty()) it else it.jumpToHit(0) }
    }

    fun nextHit() = _state.update { current ->
        if (current.hits.isEmpty()) current
        else current.jumpToHit((current.hitIndex + 1) % current.hits.size)
    }

    fun previousHit() = _state.update { current ->
        if (current.hits.isEmpty()) current
        else current.jumpToHit(
            (current.hitIndex - 1 + current.hits.size) % current.hits.size
        )
    }

    /** Selecting a hit also turns to the page it is on. */
    private fun ViewerState.jumpToHit(index: Int): ViewerState {
        val hit = hits.getOrNull(index) ?: return this
        val page = pages.indexOfFirst { it.layout.page == hit.page }
        return copy(hitIndex = index, pageIndex = if (page >= 0) page else pageIndex)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** Wide enough to stay sharp when zoomed in on a phone. */
        const val RENDER_WIDTH_PX = 1400
    }
}
