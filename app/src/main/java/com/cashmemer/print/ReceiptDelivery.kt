package com.cashmemer.print

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format

/**
 * Runs the Auto-Print and Auto-Send settings after a receipt is generated.
 *
 * Both need an Activity context — the print spooler and the share chooser each
 * put up a window — so this is driven from the UI rather than the ViewModel.
 */
object ReceiptDelivery {

    /**
     * Honours whichever of the two toggles are on. Returns a short line
     * describing what happened, or null when neither applied.
     */
    suspend fun deliver(
        context: Context,
        receipt: Receipt,
        settings: AppSettings,
    ): String? {
        if (!settings.autoPrint && !settings.autoSend) return null

        val actions = mutableListOf<String>()

        if (settings.autoPrint) {
            val pages = ReceiptOutput.pagesFor(settings.massPrint)
            ReceiptPdfRenderer
                .render(context, listOf(receipt), pages, ReceiptOutput.outputFile(context, listOf(receipt)))
                .onSuccess { file ->
                    ReceiptOutput.print(
                        context,
                        file,
                        context.getString(R.string.print_job_receipt, receipt.id),
                    )
                    actions += context.getString(R.string.delivery_printing)
                }
                .onFailure {
                    return context.getString(
                        R.string.msg_auto_print_failed,
                        it.message.orEmpty(),
                    )
                }
        }

        if (settings.autoSend) {
            when {
                receipt.customerEmail.isNotBlank() -> {
                    if (sendEmail(context, receipt)) {
                        actions += context.getString(R.string.delivery_emailing)
                    }
                }
                receipt.customerPhone.isNotBlank() -> {
                    if (sendSms(context, receipt)) {
                        actions += context.getString(R.string.delivery_texting)
                    }
                }
                // Nothing to send to is not a failure worth shouting about.
                else -> Unit
            }
        }

        val joiner = " ${context.getString(R.string.delivery_and)} "
        return actions.takeIf { it.isNotEmpty() }?.joinToString(joiner)
    }

    private fun summaryLine(context: Context, receipt: Receipt): String =
        context.getString(
            R.string.receipt_summary_line,
            receipt.id,
            receipt.placeName.ifBlank { context.getString(R.string.app_name) },
            Format.amountWithCurrency(receipt.total, receipt.currencyCode),
            Format.timestamp(receipt.createdAt),
        )

    /**
     * Opens the mail client pre-filled with the receipt as an attachment.
     * Deliberately not silent — sending mail without the shopkeeper seeing it
     * would be worse than a tap.
     */
    private suspend fun sendEmail(context: Context, receipt: Receipt): Boolean {
        val pdf = ReceiptPdfRenderer
            .render(
                context,
                listOf(receipt),
                ReceiptPages.BOTH,
                ReceiptOutput.outputFile(context, listOf(receipt)),
            )
            .getOrNull()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(receipt.customerEmail))
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.email_subject_receipt, receipt.placeName),
            )
            putExtra(Intent.EXTRA_TEXT, summaryLine(context, receipt))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            pdf?.let { file ->
                putExtra(
                    Intent.EXTRA_STREAM,
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    ),
                )
            }
        }

        return context.startActivitySafely(
            Intent.createChooser(intent, context.getString(R.string.send_receipt))
        )
    }

    private fun sendSms(context: Context, receipt: Receipt): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${receipt.customerPhone}")
            putExtra("sms_body", summaryLine(context, receipt))
        }
        return context.startActivitySafely(intent)
    }

    /** A phone with no mail or SMS app should not crash the sale. */
    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
}
