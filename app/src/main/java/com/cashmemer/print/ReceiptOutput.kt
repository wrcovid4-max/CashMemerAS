package com.cashmemer.print

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.cashmemer.core.data.MassPrintOption
import com.cashmemer.core.model.Receipt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Turns a rendered receipt PDF into a print job or a share sheet. */
object ReceiptOutput {

    private const val PDF_MIME = "application/pdf"

    fun pagesFor(option: MassPrintOption): ReceiptPages = when (option) {
        MassPrintOption.PAGE_1 -> ReceiptPages.PAGE_1
        MassPrintOption.PAGE_2 -> ReceiptPages.PAGE_2
        MassPrintOption.BOTH -> ReceiptPages.BOTH
    }

    fun outputFile(context: Context, receipts: List<Receipt>): File {
        val name = if (receipts.size == 1) "receipt-${receipts.first().id}.pdf"
        else "receipts-${receipts.size}-${System.currentTimeMillis()}.pdf"
        return File(File(context.cacheDir, "receipts"), name)
    }

    /** Hands the PDF to the system print dialog. */
    fun print(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, FilePrintAdapter(file, jobName), null)
    }

    /** Opens the share sheet so the memo can be sent by WhatsApp, email, etc. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share receipt").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Streams an already-rendered PDF to the print spooler. The document is
     * fully laid out before printing starts, so layout is a no-op here.
     */
    private class FilePrintAdapter(
        private val file: File,
        private val jobName: String,
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }

            val info = PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()

            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            runCatching {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure { error ->
                callback.onWriteFailed(error.message)
            }
        }
    }
}
