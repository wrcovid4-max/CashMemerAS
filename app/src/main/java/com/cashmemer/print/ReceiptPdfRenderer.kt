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
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Which pages of the memo to render. Mirrors the Mass Print Option setting. */
enum class ReceiptPages { PAGE_1, PAGE_2, BOTH }

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

    private const val PAGE_W = 600f

    // Content column. Deliberately not centred on the page — this reproduces
    // the reference layout rather than "improving" it.
    private const val L = 37f
    private const val R = 517f
    private const val CENTRE = (L + R) / 2f
    private const val QTY_X = 360f

    private const val FRAME = 5f
    private const val ROW = 32.5f
    private const val ROW_TIGHT = 23f

    private val navy = Color.rgb(0x1E, 0x3A, 0x6E)
    private val cream = Color.rgb(0xF7, 0xF6, 0xF0)
    private val pageGrey = Color.rgb(0xD5, 0xD4, 0xCC)
    private val muted = Color.rgb(0x6B, 0x6B, 0x6B)

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

    /** Measures the page, creates it at that exact height, then draws it. */
    private fun PdfDocument.addPage(number: Int, receipt: Receipt, secondPage: Boolean) {
        val height = layout(null, receipt, secondPage, 0f)
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), height.toInt(), number)
            .create()
        val page = startPage(info)
        layout(page.canvas, receipt, secondPage, height)
        finishPage(page)
    }

    /**
     * Single source of layout truth. With a null [canvas] nothing is drawn and
     * only the running baseline advances, giving the page height; that height
     * comes back in as [pageHeight] on the draw pass so the cream fill knows
     * how far down to reach.
     */
    private fun layout(
        canvas: Canvas?,
        receipt: Receipt,
        secondPage: Boolean,
        pageHeight: Float,
    ): Float {
        var y = 55f

        canvas?.drawColor(pageGrey)
        canvas?.drawRect(
            FRAME,
            FRAME,
            PAGE_W - FRAME,
            pageHeight - FRAME,
            Paint().apply { color = cream },
        )

        val title = if (secondPage) "CASH MEMO (Page 2)" else "CASH MEMO"
        canvas.centred(title, y, titlePaint)
        y += 31f
        canvas.centred(receipt.placeName.ifBlank { "Cash Memer" }, y, storePaint)
        y += 39f

        y = canvas.rule(y, double = true)

        // ---- Meta grid ------------------------------------------------------
        canvas.text("Receipt No: #${receipt.id}", L, y)
        canvas.textRight("Date: ${Format.isoDate(receipt.createdAt)}", R, y)
        y += ROW

        canvas.text("Place/Store: ${receipt.placeName}", L, y)
        canvas.textRight("Time: ${Format.clockTime(receipt.createdAt)}", R, y)
        y += ROW

        canvas.text("Category: ${ReceiptCategory.from(receipt.category).label}", L, y)
        canvas.textRight("Method: ${PaymentType.from(receipt.paymentType).label}", R, y)
        y += ROW

        if (secondPage) {
            canvas.text("Customer Details:", L, y)
            y += ROW
            listOf(
                "Name" to receipt.customerName,
                "Phone" to receipt.customerPhone,
                "Email" to receipt.customerEmail,
            ).forEach { (label, value) ->
                if (value.isNotBlank()) {
                    canvas.text("  $label: $value", L, y)
                    y += ROW
                }
            }
        } else if (receipt.customerName.isNotBlank()) {
            canvas.text("Customer: ${receipt.customerName}", L, y)
            y += ROW
        }

        y = canvas.rule(y - 4f, double = true)

        // ---- Items ----------------------------------------------------------
        canvas.text("Item", L, y)
        canvas.textRight("Qty", QTY_X, y)
        canvas.textRight("Total", R, y)
        y = canvas.rule(y + 8f)

        val symbol = CurrencyNames.symbolOf(receipt.currencyCode)
        ReceiptItemCodec.decode(receipt.itemsJson).forEach { item ->
            canvas.text(item.productName.take(30), L, y)
            canvas.textRight(Format.amount(item.qty), QTY_X, y)
            canvas.textRight(
                Format.amountWithCurrency(item.lineTotal, receipt.currencyCode),
                R,
                y,
            )
            y += ROW_TIGHT
            canvas.text("@ $symbol${Format.amount(item.unitPrice)} each", L + 8f, y, subLinePaint)
            y += ROW
        }

        y = canvas.rule(y - 4f)

        // ---- Totals ---------------------------------------------------------
        canvas.money("Subtotal:", receipt.subtotal, symbol, y)
        y += ROW

        if (receipt.discount > 0) {
            canvas.money("Discount:", receipt.discount, symbol, y, sign = "- ")
            y += ROW
        }
        if (receipt.taxPercent > 0) {
            val tax = (receipt.subtotal - receipt.discount).coerceAtLeast(0.0) *
                receipt.taxPercent / 100.0
            canvas.money("Tax (${Format.amount(receipt.taxPercent)}%):", tax, symbol, y, "+ ")
            y += ROW
        }

        y = canvas.rule(y - 6f)

        canvas.text("GRAND TOTAL:", L, y, grandPaint)
        canvas.textRight("$symbol ${Format.amount(receipt.total)}", R, y)
        y = canvas.rule(y + 10f, double = true)

        canvas.money("Cash Given:", receipt.cashGiven, symbol, y)
        y += ROW
        canvas.money("Change Amount:", receipt.changeAmount, symbol, y)
        y = canvas.rule(y + 8f, double = true)

        // ---- Notes ----------------------------------------------------------
        // Page 1 carries the customer-facing note; page 2 carries the
        // shopkeeper's own, which must never reach the customer copy.
        val note = if (secondPage) receipt.notesPage2 else receipt.notesPage1
        val noteLabel = if (secondPage) "Note (Page 2):" else "Note:"
        if (note.isNotBlank()) {
            canvas.text(noteLabel, L, y)
            y += ROW - 3f
            y = canvas.wrapped(note, L + 12f, y)
            y = canvas.rule(y + 10f)
        }

        // ---- Location (page 1) ----------------------------------------------
        if (!secondPage && (receipt.locationAddress.isNotBlank() || receipt.hasCoordinates)) {
            canvas.text("Saved Location:", L, y)
            y += ROW - 3f
            if (receipt.locationAddress.isNotBlank()) {
                y = canvas.wrapped(receipt.locationAddress, L + 12f, y)
            }
            if (receipt.hasCoordinates) {
                canvas.text(
                    "  GPS: ${Format.coordinate(receipt.latitude)}, " +
                        Format.coordinate(receipt.longitude),
                    L,
                    y,
                )
                y += ROW_TIGHT
            }
            y = canvas.rule(y + 10f)
        }

        // ---- Issuer (page 2) -------------------------------------------------
        if (secondPage &&
            (receipt.issuerName.isNotBlank() || receipt.issuerEmail.isNotBlank())
        ) {
            canvas.text("Issuer Account:", L, y)
            y += ROW - 4f
            if (receipt.issuerName.isNotBlank()) {
                canvas.text("  Name: ${receipt.issuerName}", L, y)
                y += ROW_TIGHT
            }
            if (receipt.issuerEmail.isNotBlank()) {
                canvas.text("  Email: ${receipt.issuerEmail}", L, y)
                y += ROW_TIGHT
            }
            y = canvas.rule(y + 8f)
        }

        // ---- Signature -------------------------------------------------------
        receipt.signatureBase64?.let { encoded ->
            canvas.text("Authorized Signature:", L, y + 32f)
            if (canvas != null) {
                decodeSignature(encoded)?.let { bitmap ->
                    val scaled = Bitmap.createScaledBitmap(bitmap, 126, 62, true)
                    canvas.drawBitmap(scaled, R - 126f, y - 8f, null)
                    scaled.recycle()
                    bitmap.recycle()
                }
            }
            y = canvas.rule(y + 50f)
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

        canvas.centred("Scan QR Code for details", y, footerSmall)
        y += 24f
        canvas.centred("Thank you for shopping with us!", y, footerPaint)
        y += 40f

        return y
    }

    // ---- Null-safe drawing primitives --------------------------------------
    // Every helper accepts a null canvas so the measure pass runs the exact
    // same code path as the draw pass.

    private fun Canvas?.text(
        text: String,
        x: Float,
        y: Float,
        paint: Paint = bodyPaint,
    ) {
        this?.drawText(text, x, y, paint)
    }

    private fun Canvas?.textRight(
        text: String,
        right: Float,
        y: Float,
        paint: Paint = bodyPaint,
    ) {
        this?.drawText(text, right - paint.measureText(text), y, paint)
    }

    private fun Canvas?.centred(text: String, y: Float, paint: Paint) {
        this?.drawText(text, CENTRE - paint.measureText(text) / 2f, y, paint)
    }

    private fun Canvas?.money(
        label: String,
        amount: Double,
        symbol: String,
        y: Float,
        sign: String = "",
    ) {
        text(label, L, y)
        textRight("$sign$symbol ${Format.amount(amount)}", R, y)
    }

    private fun Canvas?.rule(y: Float, double: Boolean = false): Float {
        this?.drawLine(L, y, R, y, rulePaint)
        if (double) this?.drawLine(L, y + 4f, R, y + 4f, rulePaint)
        return y + (if (double) 4f else 0f) + 28f
    }

    private fun Canvas?.wrapped(text: String, x: Float, startY: Float): Float {
        var y = startY
        var line = StringBuilder()
        val maxWidth = R - x

        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (bodyPaint.measureText(candidate) > maxWidth) {
                text(line.toString(), x, y)
                y += ROW_TIGHT
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            text(line.toString(), x, y)
            y += ROW_TIGHT
        }
        return y
    }

    private fun decodeSignature(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
