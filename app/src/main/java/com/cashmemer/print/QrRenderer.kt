package com.cashmemer.print

import android.graphics.Bitmap
import android.graphics.Color
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Builds the QR block printed at the foot of every memo. */
object QrRenderer {

    /**
     * Payload kept short and human-readable: a long JSON blob forces a denser
     * QR that phone cameras struggle with on thermal paper.
     */
    fun payloadFor(receipt: Receipt): String = buildString {
        append("CASHMEMER|")
        append("no=${receipt.id}|")
        append("store=${receipt.placeName.take(32)}|")
        append("total=${Format.amount(receipt.total)} ${receipt.currencyCode}|")
        append("at=${Format.timestamp(receipt.createdAt)}")
        if (receipt.hasCoordinates) {
            append("|gps=${receipt.latitude},${receipt.longitude}")
        }
    }

    fun encode(content: String, size: Int = 240): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )

        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}
