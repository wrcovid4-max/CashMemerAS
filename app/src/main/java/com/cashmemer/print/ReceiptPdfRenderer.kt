package com.cashmemer.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Which pages of the memo to render. Mirrors the Mass Print Option setting. */
enum class ReceiptPages { PAGE_1, PAGE_2, BOTH }

/**
 * Renders receipts to an A4 PDF using the platform PdfDocument — no external
 * PDF library, so nothing extra to keep up to date.
 */
object ReceiptPdfRenderer {

    // A4 at 72 dpi, the unit PdfDocument works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f

    private val brandGreen = Color.rgb(0x2E, 0x6B, 0x1F)

    private val titlePaint = Paint().apply {
        color = brandGreen
        textSize = 24f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val headingPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val bodyPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 11f
        isAntiAlias = true
    }
    private val totalPaint = Paint().apply {
        color = brandGreen
        textSize = 16f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val rulePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.8f
    }

    /**
     * Renders [receipts] into one PDF at [outputFile]. Each receipt contributes
     * one or two pages depending on [pages].
     */
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
                    document.newPage(pageNumber++) { canvas -> drawPageOne(canvas, receipt) }
                }
                if (pages != ReceiptPages.PAGE_1 && receipt.hasPageTwoContent()) {
                    document.newPage(pageNumber++) { canvas -> drawPageTwo(canvas, receipt) }
                }
            }

            // A caller asking for page 2 only on a receipt with no notes.
            check(pageNumber > 1) { "Nothing to print for the selected pages" }

            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { document.writeTo(it) }
            document.close()
            outputFile
        }
    }

    private fun Receipt.hasPageTwoContent(): Boolean =
        notesPage1.isNotBlank() || notesPage2.isNotBlank() || signatureBase64 != null

    private inline fun PdfDocument.newPage(number: Int, draw: (Canvas) -> Unit) {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        val page = startPage(info)
        draw(page.canvas)
        finishPage(page)
    }

    private fun drawPageOne(canvas: Canvas, receipt: Receipt) {
        var y = MARGIN + 18f

        canvas.drawText("CASH MEMO", MARGIN, y, titlePaint)
        y += 22f
        canvas.drawText(
            receipt.placeName.ifBlank { "Cash Memer" },
            MARGIN,
            y,
            headingPaint,
        )

        if (receipt.locationAddress.isNotBlank()) {
            y += 15f
            canvas.drawText(receipt.locationAddress, MARGIN, y, bodyPaint)
        }

        // Right-aligned meta block: receipt number and timestamp.
        val right = PAGE_WIDTH - MARGIN
        canvas.drawTextRight("Receipt #${receipt.id}", right, MARGIN + 18f, bodyPaint)
        canvas.drawTextRight(
            Format.timestamp(receipt.createdAt),
            right,
            MARGIN + 33f,
            bodyPaint,
        )

        y += 22f
        canvas.drawLine(MARGIN, y, right, y, rulePaint)
        y += 20f

        if (receipt.customerName.isNotBlank()) {
            canvas.drawText("Billed to", MARGIN, y, headingPaint)
            y += 15f
            canvas.drawText(receipt.customerName, MARGIN, y, bodyPaint)
            listOf(receipt.customerPhone, receipt.customerEmail)
                .filter { it.isNotBlank() }
                .forEach { line ->
                    y += 13f
                    canvas.drawText(line, MARGIN, y, bodyPaint)
                }
            y += 20f
        }

        // Items table
        canvas.drawText("Item", MARGIN, y, headingPaint)
        canvas.drawTextRight("Qty", MARGIN + 330f, y, headingPaint)
        canvas.drawTextRight("Price", MARGIN + 420f, y, headingPaint)
        canvas.drawTextRight("Amount", right, y, headingPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, right, y, rulePaint)
        y += 16f

        ReceiptItemCodec.decode(receipt.itemsJson).forEach { item ->
            canvas.drawText(item.productName.take(48), MARGIN, y, bodyPaint)
            canvas.drawTextRight(Format.amount(item.qty), MARGIN + 330f, y, bodyPaint)
            canvas.drawTextRight(Format.amount(item.unitPrice), MARGIN + 420f, y, bodyPaint)
            canvas.drawTextRight(Format.amount(item.lineTotal), right, y, bodyPaint)
            y += 16f

            // Spill onto nothing rather than off the page — long receipts get truncated.
            if (y > PAGE_HEIGHT - 200f) {
                canvas.drawText("… more items", MARGIN, y, bodyPaint)
                y += 16f
                return@forEach
            }
        }

        y += 6f
        canvas.drawLine(MARGIN + 300f, y, right, y, rulePaint)
        y += 18f

        canvas.drawTotal("Subtotal", receipt.subtotal, receipt.currencyCode, right, y)
        y += 16f
        if (receipt.discount > 0) {
            canvas.drawTotal("Discount", -receipt.discount, receipt.currencyCode, right, y)
            y += 16f
        }
        if (receipt.taxPercent > 0) {
            val tax = (receipt.subtotal - receipt.discount) * receipt.taxPercent / 100.0
            canvas.drawTotal(
                "Tax (${Format.amount(receipt.taxPercent)}%)",
                tax,
                receipt.currencyCode,
                right,
                y,
            )
            y += 16f
        }

        y += 6f
        canvas.drawTextRight("TOTAL", MARGIN + 420f, y, totalPaint)
        canvas.drawTextRight(
            Format.amountWithCurrency(receipt.total, receipt.currencyCode),
            right,
            y,
            totalPaint,
        )

        y += 26f
        canvas.drawText(
            "Paid by ${PaymentType.from(receipt.paymentType).label}",
            MARGIN,
            y,
            bodyPaint,
        )

        canvas.drawText(
            "Generated by Cash Memer",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            bodyPaint,
        )
    }

    private fun drawPageTwo(canvas: Canvas, receipt: Receipt) {
        var y = MARGIN + 18f
        val right = PAGE_WIDTH - MARGIN

        canvas.drawText("NOTES", MARGIN, y, titlePaint)
        y += 26f

        listOf(receipt.notesPage1, receipt.notesPage2)
            .filter { it.isNotBlank() }
            .forEach { note ->
                y = canvas.drawWrapped(note, MARGIN, y, right - MARGIN, bodyPaint)
                y += 14f
            }

        receipt.signatureBase64?.let { encoded ->
            y += 20f
            canvas.drawText("Authorised signature", MARGIN, y, headingPaint)
            y += 10f
            decodeSignature(encoded)?.let { bitmap ->
                val scaled = Bitmap.createScaledBitmap(bitmap, 220, 90, true)
                canvas.drawBitmap(scaled, MARGIN, y, null)
                y += 100f
                canvas.drawLine(MARGIN, y, MARGIN + 220f, y, rulePaint)
                scaled.recycle()
                bitmap.recycle()
            }
        }

        canvas.drawText(
            "Receipt #${receipt.id} · ${Format.timestamp(receipt.createdAt)}",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            bodyPaint,
        )
    }

    private fun decodeSignature(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun Canvas.drawTextRight(text: String, right: Float, y: Float, paint: Paint) {
        drawText(text, right - paint.measureText(text), y, paint)
    }

    private fun Canvas.drawTotal(
        label: String,
        amount: Double,
        currency: String,
        right: Float,
        y: Float,
    ) {
        drawTextRight(label, MARGIN + 420f, y, bodyPaint)
        drawTextRight(Format.amountWithCurrency(amount, currency), right, y, bodyPaint)
    }

    /** Naive word wrap — enough for free-text notes. Returns the new baseline. */
    private fun Canvas.drawWrapped(
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
    ): Float {
        var y = startY
        var line = StringBuilder()

        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth) {
                drawText(line.toString(), x, y, paint)
                y += 15f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            drawText(line.toString(), x, y, paint)
            y += 15f
        }
        return y
    }
}
