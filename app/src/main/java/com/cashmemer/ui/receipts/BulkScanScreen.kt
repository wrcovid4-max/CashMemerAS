package com.cashmemer.ui.receipts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
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
    }

    fun clear() {
        _saveRequested.value = false
        _editingId.value = null
        _items.value = emptyList()
    }
}

/** Scans the picked photos one after another, updating each item as it goes. */
class BulkScanViewModel(application: Application) : AndroidViewModel(application) {

    fun scan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val start = BulkScanSession.add(uris)
        viewModelScope.launch {
            uris.forEachIndexed { offset, uri ->
                val id = start + offset
                BulkScanSession.update(id) { it.copy(status = BulkStatus.Scanning) }
                val bitmap = decodeScaled(uri)
                val result = bitmap?.let { GeminiOcrClient.parse(it).getOrNull() }
                BulkScanSession.update(id) {
                    it.copy(
                        status = if (result != null) BulkStatus.Done else BulkStatus.Failed,
                        parsed = result,
                    )
                }
            }
        }
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
 * The bulk scan screen: one button to pick receipts, a row per receipt with its progress,
 * a pencil to open a receipt in the form, and Save / Clear once every photo is done.
 */
@Composable
fun BulkScanScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSaveAll: () -> Unit,
    viewModel: BulkScanViewModel = viewModel(),
) {
    val batch by BulkScanSession.items.collectAsState()
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BULK_LIMIT),
    ) { uris -> viewModel.scan(uris) }
    val imagesOnly = ActivityResultContracts.PickVisualMedia.ImageOnly

    val finished = batch.count { it.status == BulkStatus.Done || it.status == BulkStatus.Failed }
    val allDone = batch.isNotEmpty() && batch.none {
        it.status == BulkStatus.Waiting || it.status == BulkStatus.Scanning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        Button(
            onClick = { pick.launch(PickVisualMediaRequest(imagesOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bulk_pick))
        }

        if (batch.isNotEmpty()) {
            Text(
                stringResource(R.string.bulk_progress, finished, batch.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (status == BulkStatus.Scanning) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Text(
            stringResource(R.string.bulk_receipt_number, number),
            modifier = Modifier.weight(1f),
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
            style = MaterialTheme.typography.bodyMedium,
        )
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
