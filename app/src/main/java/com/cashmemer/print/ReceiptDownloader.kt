package com.cashmemer.print

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.model.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a PDF of a receipt to the phone as soon as it is generated (Auto-download PDF in Settings),
 * for Generate and for every receipt saved from a bulk scan.
 *
 * Where it goes: the folder picked in Settings (a storage-access tree, so any folder or drive works),
 * or by default Downloads/CashMemer. Neither needs a storage permission.
 */
object ReceiptDownloader {

    private const val DEFAULT_FOLDER = "CashMemer"

    /** Human-readable destination, for the confirmation message and Settings. */
    fun locationLabel(context: Context, settings: AppSettings): String {
        settings.downloadFolderUri?.let { uri ->
            return Uri.decode(uri.substringAfterLast(':')).ifBlank { uri }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_FOLDER"
        } else {
            // Before Android 10 a public folder would need a storage permission; the app's own
            // Download folder doesn't.
            "Android/data/${context.packageName}/files/${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_FOLDER"
        }
    }

    /** Renders [receipt] and writes it to the chosen location. Succeeds with that location's label. */
    suspend fun save(context: Context, receipt: Receipt, settings: AppSettings): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = ReceiptPdfRenderer
                    .render(
                        context,
                        listOf(receipt),
                        ReceiptPages.BOTH,
                        ReceiptOutput.outputFile(context, listOf(receipt)),
                    )
                    .getOrThrow()

                val folder = settings.downloadFolderUri?.let(Uri::parse)
                if (folder != null) writeToTree(context, folder, source) else writeToDownloads(context, source)
                locationLabel(context, settings)
            }
        }

    private fun writeToTree(context: Context, tree: Uri, source: File) {
        val dir = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canWrite() }
            ?: error("The download folder is not available")
        // createFile adds the extension and renames a clash ("… (1)"), so nothing is overwritten.
        val target = dir.createFile("application/pdf", source.nameWithoutExtension)
            ?: error("Could not create the file")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: error("Could not open the file")
    }

    private fun writeToDownloads(context: Context, source: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the file")
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: error("Could not open the file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DEFAULT_FOLDER)
                .apply { mkdirs() }
            source.copyTo(File(dir, source.name), overwrite = true)
        }
    }
}
