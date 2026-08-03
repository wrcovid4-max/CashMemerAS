package com.cashmemer.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes a full JSON snapshot into the folder the user picked via the system
 * folder picker. Pointing that at a Drive- or OneDrive-synced folder is what
 * gets the data off the phone.
 */
object BackupWriter {

    private const val MIME_JSON = "application/json"

    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault())

    /** How many dated snapshots to keep before the oldest are pruned. */
    private const val KEEP_SNAPSHOTS = 30

    fun fileNameFor(millis: Long): String =
        "cashmemer-backup-${stamp.format(Instant.ofEpochMilli(millis))}.json"

    /**
     * Runs one backup. Returns the file name written, or a failure carrying a
     * message worth showing on the Settings screen.
     */
    suspend fun run(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val settingsStore = SettingsStore(context)
        val repository = CashMemerRepository.get(context)

        val result = runCatching {
            val folderUri = currentFolderUri(settingsStore)
                ?: error(context.getString(R.string.err_no_backup_folder))

            val folder = DocumentFile.fromTreeUri(context, folderUri)
            check(folder != null && folder.isDirectory && folder.canWrite()) {
                context.getString(R.string.err_backup_folder_unwritable)
            }

            val json = repository.exportJson()
            val now = System.currentTimeMillis()
            val name = fileNameFor(now)

            // Same-minute reruns would otherwise stack up duplicates.
            folder.findFile(name)?.delete()

            val target = folder.createFile(MIME_JSON, name)
                ?: error(context.getString(R.string.err_backup_create_failed, name))

            context.contentResolver.openOutputStream(target.uri)?.use { stream ->
                stream.write(json.toByteArray())
            } ?: error(context.getString(R.string.err_backup_write_failed, name))

            pruneOldSnapshots(folder)
            name
        }

        settingsStore.recordBackupResult(
            succeededAt = result.getOrNull()?.let { System.currentTimeMillis() },
            error = result.exceptionOrNull()?.message,
        )
        result
    }

    private suspend fun currentFolderUri(settingsStore: SettingsStore): Uri? =
        settingsStore.settings.first().backupFolderUri?.let(Uri::parse)

    /** Keeps the folder from growing without bound on a daily schedule. */
    private fun pruneOldSnapshots(folder: DocumentFile) {
        val snapshots = folder.listFiles()
            .filter { it.isFile && it.name?.startsWith("cashmemer-backup-") == true }
            .sortedByDescending { it.lastModified() }

        snapshots.drop(KEEP_SNAPSHOTS).forEach { it.delete() }
    }
}
