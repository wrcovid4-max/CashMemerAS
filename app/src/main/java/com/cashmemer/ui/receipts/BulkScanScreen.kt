package com.cashmemer.ui.receipts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.network.GeminiOcrClient
import com.cashmemer.core.network.ParsedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Most photos one bulk scan takes. */
private const val BULK_LIMIT = 10

/** Long edge, in pixels, that photos are shrunk to before upload. */
private const val BULK_MAX_EDGE = 1600

enum class BulkStatus { Waiting, Scanning, Done, Failed }

data class BulkItem(
    val id: Int,
    val uri: Uri,
    val status: BulkStatus = BulkStatus.Waiting,
    val parsed: ParsedReceipt? = null,
    /** The form as last edited by hand; it takes priority over the scan result. */
    val edited: ReceiptFormState? = null,
)

/**
 * Shared by the bulk screen and the receipt form, which live on different screens.
 * Holds the batch, which receipt is open for editing, and whether a save was asked for.
 */
object BulkScanSession {
    private val _items = MutableStateFlow<List<BulkItem>>(emptyList())
    val items: StateFlow<List<BulkItem>> = _items.asStateFlow()

    private val _editingId = MutableStateFlow<Int?>(null)
    val editingId: StateFlow<Int?> = _editingId.asStateFlow()

    private val _saveRequested = MutableStateFlow(false)
    private var doneCount = 0
    private var doneMillis = 0L

    /** Records how long one receipt took, for the time-left estimate. */
    fun recordDuration(millis: Long) {
        doneCount++
        doneMillis += millis
    }

    /** Estimated time left, from the average of the receipts already scanned. */
    fun etaMillis(remaining: Int): Long? =
        if (doneCount == 0 || remaining <= 0) null else (doneMillis / doneCount) * remaining

    private fun resetTiming() {
        doneCount = 0
        doneMillis = 0L
    }
    val saveRequested: StateFlow<Boolean> = _saveRequested.asStateFlow()

    /** Adds the photos as waiting items and returns the id of the first one. */
    fun add(uris: List<Uri>): Int {
        val start = _items.value.size
        _items.update { current ->
            current + uris.mapIndexed { index, uri -> BulkItem(id = start + index, uri = uri) }
        }
        return start
    }

    fun update(id: Int, transform: (BulkItem) -> BulkItem) {
        _items.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    fun edit(id: Int) {
        _editingId.value = id
    }

    /** Keeps the form as it was left, and clears the editing marker. */
    fun stash(id: Int, form: ReceiptFormState) {
        update(id) { it.copy(edited = form) }
        _editingId.value = null
    }

    fun requestSave() {
        _saveRequested.value = true
    }

    /** Called once the form has saved the batch: the list is finished with. */
    fun finishSave() {
        _saveRequested.value = false
        _editingId.value = null
        _items.value = emptyList()
        resetTiming()
    }

    fun clear() {
        _saveRequested.value = false
        _editingId.value = null
        _items.value = emptyList()
        resetTiming()
    }
}

/** The green used for the bulk scan progress bar. */
private val ScanGreen = Color(0xFF2E7D32)

/** Shows scan progress as an ongoing notification in the notification shade. */
object BulkNotifier {
    private const val CHANNEL = "bulk_scan"
    private const val NOTIFICATION_ID = 4101

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.bulk_notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun progress(context: Context, done: Int, total: Int, etaText: String) {
        ensureChannel(context)
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.bulk_notif_title))
            .setContentText(context.getString(R.string.bulk_progress, done, total) + " · " + etaText)
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun finished(context: Context, done: Int, total: Int) {
        ensureChannel(context)
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.bulk_notif_done))
            .setContentText(context.getString(R.string.bulk_progress, done, total))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Formats a time-left estimate as a short phrase. */
@Composable
private fun etaText(millis: Long?): String = when {
    millis == null -> stringResource(R.string.bulk_eta_calculating)
    millis >= 60_000 -> stringResource(R.string.bulk_eta_minutes, (millis / 60_000).toInt() + 1)
    else -> stringResource(R.string.bulk_eta_seconds, maxOf(1, (millis / 1000).toInt()))
}

/** Scans the picked photos one after another, updating each item and the notification. */
class BulkScanViewModel(application: Application) : AndroidViewModel(application) {

    fun scan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val start = BulkScanSession.add(uris)
        val total = BulkScanSession.items.value.size
        viewModelScope.launch {
            uris.forEachIndexed { offset, uri ->
                val id = start + offset
                val started = System.currentTimeMillis()
                BulkScanSession.update(id) { it.copy(status = BulkStatus.Scanning) }
                val bitmap = decodeScaled(uri)
                val result = bitmap?.let { GeminiOcrClient.parse(it).getOrNull() }
                BulkScanSession.update(id) {
                    it.copy(
                        status = if (result != null) BulkStatus.Done else BulkStatus.Failed,
                        parsed = result,
                    )
                }
                BulkScanSession.recordDuration(System.currentTimeMillis() - started)
                val done = BulkScanSession.items.value.count {
                    it.status == BulkStatus.Done || it.status == BulkStatus.Failed
                }
                val eta = BulkScanSession.etaMillis(total - done)
                BulkNotifier.progress(getApplication<Application>(), done, total, etaTextPlain(getApplication<Application>(), eta))
            }
            val done = BulkScanSession.items.value.count {
                it.status == BulkStatus.Done || it.status == BulkStatus.Failed
            }
            BulkNotifier.finished(getApplication<Application>(), done, total)
        }
    }

    private fun etaTextPlain(context: Context, millis: Long?): String = when {
        millis == null -> context.getString(R.string.bulk_eta_calculating)
        millis >= 60_000 -> context.getString(R.string.bulk_eta_minutes, (millis / 60_000).toInt() + 1)
        else -> context.getString(R.string.bulk_eta_seconds, maxOf(1, (millis / 1000).toInt()))
    }

    private suspend fun decodeScaled(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > BULK_MAX_EDGE) {
                    val scale = BULK_MAX_EDGE.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt(),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }
}

/**
 * The bulk scan screen: a pick button, one progress card with a green bar and time left,
 * a card per receipt with a pencil, and Save / Clear once every photo is done.
 */
@Composable
fun BulkScanScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSaveAll: () -> Unit,
    viewModel: BulkScanViewModel = viewModel(),
) {
    val batch by BulkScanSession.items.collectAsState()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BULK_LIMIT),
    ) { uris ->
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.scan(uris)
    }
    val imagesOnly = ActivityResultContracts.PickVisualMedia.ImageOnly

    val finished = batch.count { it.status == BulkStatus.Done || it.status == BulkStatus.Failed }
    val remaining = batch.size - finished
    val fraction = if (batch.isEmpty()) 0f else finished.toFloat() / batch.size
    val allDone = batch.isNotEmpty() && remaining == 0
    val eta = BulkScanSession.etaMillis(remaining)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.bulk_back),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.bulk_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        // Pick card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.bulk_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { pick.launch(PickVisualMediaRequest(imagesOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bulk_pick))
                }
            }
        }

        // Progress card: green bar, count and time left
        if (batch.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.bulk_progress, finished, batch.size),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (allDone) stringResource(R.string.bulk_all_done) else etaText(eta),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ScanGreen,
                        trackColor = ScanGreen.copy(alpha = 0.2f),
                    )
                }
            }
        }

        // Receipts
        if (batch.isNotEmpty()) {
            Text(
                stringResource(R.string.bulk_section_receipts),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(batch, key = { it.id }) { item ->
                    val canEdit = item.status == BulkStatus.Done || item.status == BulkStatus.Failed
                    BulkRow(
                        number = item.id + 1,
                        status = item.status,
                        onEdit = if (canEdit) {
                            {
                                BulkScanSession.edit(item.id)
                                onEdit()
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // Save / Clear once everything is scanned
        if (allDone) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { BulkScanSession.clear() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.bulk_clear))
                }
                Button(
                    onClick = {
                        BulkScanSession.requestSave()
                        onSaveAll()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.bulk_save))
                }
            }
        }
    }
}

@Composable
private fun BulkRow(number: Int, status: BulkStatus, onEdit: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == BulkStatus.Scanning) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.bulk_receipt_number, number),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(
                        when (status) {
                            BulkStatus.Waiting -> R.string.bulk_waiting
                            BulkStatus.Scanning -> R.string.bulk_scanning
                            BulkStatus.Done -> R.string.bulk_done
                            BulkStatus.Failed -> R.string.bulk_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == BulkStatus.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.bulk_edit),
                    )
                }
            }
        }
    }
}
