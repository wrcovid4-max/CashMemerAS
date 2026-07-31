package com.cashmemer.print

import android.content.Context
import android.content.Intent
import android.net.Uri
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
                    ReceiptOutput.print(context, file, "Receipt ${receipt.id}")
                    actions += "printing"
                }
                .onFailure { return "Auto-print failed: ${it.message}" }
        }

        if (settings.autoSend) {
            when {
                receipt.customerEmail.isNotBlank() -> {
                    if (sendEmail(context, receipt)) actions += "emailing"
                }
                receipt.customerPhone.isNotBlank() -> {
                    if (sendSms(context, receipt)) actions += "texting"
                }
                // Nothing to send to is not a failure worth shouting about.
                else -> Unit
            }
        }

        return actions.takeIf { it.isNotEmpty() }?.joinToString(" and ")
    }

    private fun summaryLine(receipt: Receipt): String =
        "Receipt #${receipt.id} from ${receipt.placeName.ifBlank { "Cash Memer" }} — " +
            "${Format.amountWithCurrency(receipt.total, receipt.currencyCode)} on " +
            Format.timestamp(receipt.createdAt)

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
            putExtra(Intent.EXTRA_SUBJECT, "Your receipt from ${receipt.placeName}")
            putExtra(Intent.EXTRA_TEXT, summaryLine(receipt))
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
            Intent.createChooser(intent, "Send receipt")
        )
    }

    private fun sendSms(context: Context, receipt: Receipt): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${receipt.customerPhone}")
            putExtra("sms_body", summaryLine(receipt))
        }
        return context.startActivitySafely(intent)
    }

    /** A phone with no mail or SMS app should not crash the sale. */
    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
}
