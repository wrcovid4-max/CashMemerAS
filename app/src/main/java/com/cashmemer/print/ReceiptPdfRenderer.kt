package com.cashmemer.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.cashmemer.core.data.CurrencyNames
import com.cashmemer.core.data.ReceiptAnnotationCodec
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.AnnotationKind
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptAnnotation
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Which pages of the memo to render. Mirrors the Mass Print Option setting. */
enum class ReceiptPages { PAGE_1, PAGE_2, BOTH }

/** A string that was drawn on a page, and the box it landed in, in page points. */
data class ReceiptTextRun(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Everything the viewer needs to reason about a page without re-deriving it:
 * the page's true size and every string on it. Produced by the same layout pass
 * that draws, so a search hit lands exactly where the ink is.
 */
data class ReceiptPageLayout(
    /** 1 for the customer copy, 2 for the shop's record. */
    val page: Int,
    val width: Float,
    val height: Float,
    val runs: List<ReceiptTextRun>,
)

/**
 * Renders the cash memo to match the original app's output: a cream receipt
 * column with a navy title, everything bold, rules separating each block.
 *
 * Pages are a fixed width and a **variable height sized to their content** —
 * a receipt roll, not a sheet of A4. Each page is therefore laid out twice:
 * once with a null canvas purely to measure, then again for real once the
 * page has been created at the right height. Running the same code for both
 * passes is what keeps the measurement honest.
 *
 * Page 1 is the customer's copy — name, goods, money, note, where the sale
 * happened. Page 2 is the shop's record and adds full contact details, the
 * shopkeeper's own note and the issuing account.
 */
object ReceiptPdfRenderer {

    const val PAGE_W = 600f

    // Content column. Deliberately not centred on the page — this reproduces
    // the reference layout rather than "improving" it.
    private const val L = 37f
    private const val R = 517f
    private const val CENTRE = (L + R) / 2f
    private const val QTY_X = 360f

    private const val FRAME = 5f
    private const val ROW = 32.5f
    private const val ROW_TIGHT = 23f

    /** Half-width of a stamped tick or cross, in page points. */
    private const val MARK = 11f

    private val navy = Color.rgb(0x1E, 0x3A, 0x6E)
    private val cream = Color.rgb(0xF7, 0xF6, 0xF0)
    private val pageGrey = Color.rgb(0xD5, 0xD4, 0xCC)
    private val muted = Color.rgb(0x6B, 0x6B, 0x6B)
    private val markGreen = Color.rgb(0x1B, 0x7F, 0x37)
    private val markRed = Color.rgb(0xC0, 0x27, 0x27)

    private fun bold(size: Float, colour: Int = Color.BLACK) = Paint().apply {
        color = colour
        textSize = size
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val titlePaint = bold(33f, navy)
    private val storePaint = bold(16f, muted)
    private val bodyPaint = bold(16f)
    private val grandPaint = bold(20f)
    private val subLinePaint = Paint().apply {
        color = muted
        textSize = 13f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val footerSmall = Paint().apply {
        color = muted
        textSize = 12f
        isAntiAlias = true
    }
    private val footerPaint = bold(15.5f, muted)
    private val rulePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 1.4f
    }
    private val annotationTextPaint = bold(17f, markRed)
    private val tickPaint = Paint().apply {
        color = markGreen
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val crossPaint = Paint().apply {
        color = markRed
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    suspend fun render(
        context: Context,
        receipts: List<Receipt>,
        pages: ReceiptPages,
        outputFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            var pageNumber = 1

            receipts.forEach { receipt ->
                if (pages != ReceiptPages.PAGE_2) {
                    document.addPage(pageNumber++, receipt, secondPage = false)
                }
                // Page 2 always renders — it is the shop's record, so it has to
                // exist even when its optional blocks are empty.
                if (pages != ReceiptPages.PAGE_1) {
                    document.addPage(pageNumber++, receipt, secondPage = true)
                }
            }

            check(pageNumber > 1) { "Nothing to print" }

            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { document.writeTo(it) }
            document.close()
            outputFile
        }
    }

    /**
     * Runs the measure pass alone and reports what it found.
     *
     * The viewer needs page heights to place marks and text boxes to highlight
     * search hits; both come from here rather than from a second description of
     * the layout that could drift away from the real one.
     */
    suspend fun pageLayouts(
        receipt: Receipt,
        pages: ReceiptPages = ReceiptPages.BOTH,
    ): List<ReceiptPageLayout> = withContext(Dispatchers.Default) {
        buildList {
            if (pages != ReceiptPages.PAGE_2) add(measure(receipt, secondPage = false))
            if (pages != ReceiptPages.PAGE_1) add(measure(receipt, secondPage = true))
        }
    }

    private fun measure(receipt: Receipt, secondPage: Boolean): ReceiptPageLayout {
        val runs = mutableListOf<ReceiptTextRun>()
        val height = layout(Sheet(null, runs), receipt, secondPage, 0f)
        return ReceiptPageLayout(
            page = if (secondPage) 2 else 1,
            width = PAGE_W,
            height = height,
            runs = runs,
        )
    }

    /** Measures the page, creates it at that exact height, then draws it. */
    private fun PdfDocument.addPage(number: Int, receipt: Receipt, secondPage: Boolean) {
        val height = layout(Sheet(null, null), receipt, secondPage, 0f)
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), height.toInt(), number)
            .create()
        val page = startPage(info)
        layout(Sheet(page.canvas, null), receipt, secondPage, height)
        // Marks go on last so they sit over the memo, the way ink would.
        drawAnnotations(page.canvas, receipt, secondPage, height)
        finishPage(page)
    }

    /**
     * Single source of layout truth. With a null canvas nothing is drawn and
     * only the running baseline advances, giving the page height; that height
     * comes back in as [pageHeight] on the draw pass so the cream fill knows
     * how far down to reach.
     */
    private fun layout(
        sheet: Sheet,
        receipt: Receipt,
        secondPage: Boolean,
        pageHeight: Float,
    ): Float {
        var y = 55f
        val canvas = sheet.canvas

        canvas?.drawColor(pageGrey)
        canvas?.drawRect(
            FRAME,
            FRAME,
            PAGE_W - FRAME,
            pageHeight - FRAME,
            Paint().apply { color = cream },
        )

        val title = if (secondPage) "CASH MEMO (Page 2)" else "CASH MEMO"
        sheet.centred(title, y, titlePaint)
        y += 31f
        sheet.centred(receipt.placeName.ifBlank { "Cash Memer" }, y, storePaint)
        y += 39f

        y = sheet.rule(y, double = true)

        // ---- Meta grid ------------------------------------------------------
        sheet.write("Receipt No: #${receipt.id}", L, y)
        sheet.textRight("Date: ${Format.isoDate(receipt.createdAt)}", R, y)
        y += ROW

        sheet.write("Place/Store: ${receipt.placeName}", L, y)
        sheet.textRight("Time: ${Format.clockTime(receipt.createdAt)}", R, y)
        y += ROW

        sheet.write("Category: ${ReceiptCategory.from(receipt.category).label}", L, y)
        sheet.textRight("Method: ${PaymentType.from(receipt.paymentType).label}", R, y)
        y += ROW

        if (secondPage) {
            sheet.write("Customer Details:", L, y)
            y += ROW
            listOf(
                "Name" to receipt.customerName,
                "Phone" to receipt.customerPhone,
                "Email" to receipt.customerEmail,
            ).forEach { (label, value) ->
                if (value.isNotBlank()) {
                    sheet.write("  $label: $value", L, y)
                    y += ROW
                }
            }
        } else if (receipt.customerName.isNotBlank()) {
            sheet.write("Customer: ${receipt.customerName}", L, y)
            y += ROW
        }

        y = sheet.rule(y - 4f, double = true)

        // ---- Items ----------------------------------------------------------
        sheet.write("Item", L, y)
        sheet.textRight("Qty", QTY_X, y)
        sheet.textRight("Total", R, y)
        y = sheet.rule(y + 8f)

        val symbol = CurrencyNames.symbolOf(receipt.currencyCode)
        ReceiptItemCodec.decode(receipt.itemsJson).forEach { item ->
            sheet.write(item.productName.take(30), L, y)
            sheet.textRight(Format.amount(item.qty), QTY_X, y)
            sheet.textRight(
                Format.amountWithCurrency(item.lineTotal, receipt.currencyCode),
                R,
                y,
            )
            y += ROW_TIGHT
            sheet.write("@ $symbol${Format.amount(item.unitPrice)} each", L + 8f, y, subLinePaint)
            y += ROW
        }

        y = sheet.rule(y - 4f)

        // ---- Totals ---------------------------------------------------------
        sheet.money("Subtotal:", receipt.subtotal, symbol, y)
        y += ROW

        if (receipt.discount > 0) {
            sheet.money("Discount:", receipt.discount, symbol, y, sign = "- ")
            y += ROW
        }
        if (receipt.taxPercent > 0) {
            val tax = (receipt.subtotal - receipt.discount).coerceAtLeast(0.0) *
                receipt.taxPercent / 100.0
            sheet.money("Tax (${Format.amount(receipt.taxPercent)}%):", tax, symbol, y, "+ ")
            y += ROW
        }

        y = sheet.rule(y - 6f)

        sheet.write("GRAND TOTAL:", L, y, grandPaint)
        sheet.textRight("$symbol ${Format.amount(receipt.total)}", R, y)
        y = sheet.rule(y + 10f, double = true)

        sheet.money("Cash Given:", receipt.cashGiven, symbol, y)
        y += ROW
        sheet.money("Change Amount:", receipt.changeAmount, symbol, y)
        y = sheet.rule(y + 8f, double = true)

        // ---- Notes ----------------------------------------------------------
        // Page 1 carries the customer-facing note; page 2 carries the
        // shopkeeper's own, which must never reach the customer copy.
        val note = if (secondPage) receipt.notesPage2 else receipt.notesPage1
        val noteLabel = if (secondPage) "Note (Page 2):" else "Note:"
        if (note.isNotBlank()) {
            sheet.write(noteLabel, L, y)
            y += ROW - 3f
            y = sheet.wrapped(note, L + 12f, y)
            y = sheet.rule(y + 10f)
        }

        // ---- Location (page 1) ----------------------------------------------
        if (!secondPage && (receipt.locationAddress.isNotBlank() || receipt.hasCoordinates)) {
            sheet.write("Saved Location:", L, y)
            y += ROW - 3f
            if (receipt.locationAddress.isNotBlank()) {
                y = sheet.wrapped(receipt.locationAddress, L + 12f, y)
            }
            if (receipt.hasCoordinates) {
                sheet.write(
                    "  GPS: ${Format.coordinate(receipt.latitude)}, " +
                        Format.coordinate(receipt.longitude),
                    L,
                    y,
                )
                y += ROW_TIGHT
            }
            y = sheet.rule(y + 10f)
        }

        // ---- Issuer (page 2) -------------------------------------------------
        if (secondPage &&
            (receipt.issuerName.isNotBlank() || receipt.issuerEmail.isNotBlank())
        ) {
            sheet.write("Issuer Account:", L, y)
            y += ROW - 4f
            if (receipt.issuerName.isNotBlank()) {
                sheet.write("  Name: ${receipt.issuerName}", L, y)
                y += ROW_TIGHT
            }
            if (receipt.issuerEmail.isNotBlank()) {
                sheet.write("  Email: ${receipt.issuerEmail}", L, y)
                y += ROW_TIGHT
            }
            y = sheet.rule(y + 8f)
        }

        // ---- Signature -------------------------------------------------------
        receipt.signatureBase64?.let { encoded ->
            sheet.write("Authorized Signature:", L, y + 32f)
            if (canvas != null) {
                decodeSignature(encoded)?.let { bitmap ->
                    val scaled = Bitmap.createScaledBitmap(bitmap, 126, 62, true)
                    canvas.drawBitmap(scaled, R - 126f, y - 8f, null)
                    scaled.recycle()
                    bitmap.recycle()
                }
            }
            y = sheet.rule(y + 50f)
        }

        // ---- Footer ----------------------------------------------------------
        val qrSize = 94
        if (canvas != null) {
            canvas.drawRect(
                CENTRE - 56f,
                y - 8f,
                CENTRE + 56f,
                y + qrSize + 8f,
                Paint().apply { color = Color.WHITE },
            )
            QrRenderer.encode(QrRenderer.payloadFor(receipt), qrSize)?.let { qr ->
                canvas.drawBitmap(qr, CENTRE - qrSize / 2f, y, null)
                qr.recycle()
            }
        }
        y += qrSize + 30f

        sheet.centred("Scan QR Code for details", y, footerSmall)
        y += 24f
        sheet.centred("Thank you for shopping with us!", y, footerPaint)
        y += 40f

        return y
    }

    /**
     * Stamps the viewer's marks onto a page. Positions are stored as fractions,
     * so this is also what makes an exported PDF agree with what the shopkeeper
     * saw on screen.
     */
    private fun drawAnnotations(
        canvas: Canvas,
        receipt: Receipt,
        secondPage: Boolean,
        pageHeight: Float,
    ) {
        val page = if (secondPage) 2 else 1
        ReceiptAnnotationCodec.decode(receipt.annotationsJson)
            .filter { it.page == page }
            .forEach { mark -> drawMark(canvas, mark, PAGE_W, pageHeight) }
    }

    private fun drawMark(
        canvas: Canvas,
        mark: ReceiptAnnotation,
        width: Float,
        height: Float,
    ) {
        val x = mark.x * width
        val y = mark.y * height

        when (mark.kind) {
            AnnotationKind.CHECK -> {
                canvas.drawLine(x - MARK, y, x - MARK / 3f, y + MARK * 0.7f, tickPaint)
                canvas.drawLine(x - MARK / 3f, y + MARK * 0.7f, x + MARK, y - MARK, tickPaint)
            }
            AnnotationKind.CROSS -> {
                canvas.drawLine(x - MARK, y - MARK, x + MARK, y + MARK, crossPaint)
                canvas.drawLine(x + MARK, y - MARK, x - MARK, y + MARK, crossPaint)
            }
            AnnotationKind.TEXT ->
                if (mark.text.isNotBlank()) {
                    canvas.drawText(mark.text, x, y, annotationTextPaint)
                }
        }
    }

    /**
     * The canvas being drawn on, plus somewhere to record what was drawn.
     *
     * Both are nullable on purpose: a null canvas is the measure pass, and a
     * null run list means nobody asked where the text went. Keeping the
     * primitives on this one class is what stops the measure and draw passes
     * from ever disagreeing.
     */
    private class Sheet(
        val canvas: Canvas?,
        private val runs: MutableList<ReceiptTextRun>?,
    ) {

        fun write(text: String, x: Float, y: Float, paint: Paint = bodyPaint) {
            canvas?.drawText(text, x, y, paint)
            record(text, x, y, paint)
        }

        fun textRight(text: String, right: Float, y: Float, paint: Paint = bodyPaint) {
            write(text, right - paint.measureText(text), y, paint)
        }

        fun centred(text: String, y: Float, paint: Paint) {
            write(text, CENTRE - paint.measureText(text) / 2f, y, paint)
        }

        fun money(
            label: String,
            amount: Double,
            symbol: String,
            y: Float,
            sign: String = "",
        ) {
            write(label, L, y)
            textRight("$sign$symbol ${Format.amount(amount)}", R, y)
        }

        fun rule(y: Float, double: Boolean = false): Float {
            canvas?.drawLine(L, y, R, y, rulePaint)
            if (double) canvas?.drawLine(L, y + 4f, R, y + 4f, rulePaint)
            return y + (if (double) 4f else 0f) + 28f
        }

        fun wrapped(text: String, x: Float, startY: Float): Float {
            var y = startY
            var line = StringBuilder()
            val maxWidth = R - x

            text.split(Regex("\\s+")).forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (bodyPaint.measureText(candidate) > maxWidth) {
                    write(line.toString(), x, y)
                    y += ROW_TIGHT
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) {
                write(line.toString(), x, y)
                y += ROW_TIGHT
            }
            return y
        }

        private fun record(text: String, x: Float, y: Float, paint: Paint) {
            val sink = runs ?: return
            if (text.isBlank()) return
            val metrics = paint.fontMetrics
            sink += ReceiptTextRun(
                text = text,
                left = x,
                top = y + metrics.ascent,
                right = x + paint.measureText(text),
                bottom = y + metrics.descent,
            )
        }
    }

    private fun decodeSignature(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
