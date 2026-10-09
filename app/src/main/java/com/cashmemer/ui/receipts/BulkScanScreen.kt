package com.cashmemer.ui.receipts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
)

/** Holds the bulk scan results, so they survive rotation while the screen is open. */
class BulkScanViewModel(application: Application) : AndroidViewModel(application) {
    private val _items = MutableStateFlow<List<BulkItem>>(emptyList())
    val items: StateFlow<List<BulkItem>> = _items.asStateFlow()

    /** Adds the picked photos and scans them one after another. */
    fun scan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val start = _items.value.size
        _items.update { current ->
            current + uris.mapIndexed { index, uri -> BulkItem(id = start + index, uri = uri) }
        }
        viewModelScope.launch {
            uris.forEachIndexed { offset, uri ->
                val id = start + offset
                setStatus(id, BulkStatus.Scanning)
                val bitmap = decodeScaled(uri)
                val result = bitmap?.let { GeminiOcrClient.parse(it).getOrNull() }
                _items.update { current ->
                    current.map { item ->
                        if (item.id == id) {
                            item.copy(
                                status = if (result != null) BulkStatus.Done else BulkStatus.Failed,
                                parsed = result,
                            )
                        } else {
                            item
                        }
                    }
                }
            }
        }
    }

    private fun setStatus(id: Int, status: BulkStatus) {
        _items.update { current ->
            current.map { if (it.id == id) it.copy(status = status) else it }
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

/** The bulk scan screen: one button to pick receipts, then a row per receipt with its progress. */
@Composable
fun BulkScanScreen(
    onBack: () -> Unit,
    viewModel: BulkScanViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsState()
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BULK_LIMIT),
    ) { uris -> viewModel.scan(uris) }
    val imagesOnly = ActivityResultContracts.PickVisualMedia.ImageOnly

    val finished = items.count { it.status == BulkStatus.Done || it.status == BulkStatus.Failed }

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
            onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(imagesOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bulk_pick))
        }

        if (items.isNotEmpty()) {
            Text(
                stringResource(R.string.bulk_progress, finished, items.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                BulkRow(number = item.id + 1, status = item.status)
            }
        }
    }
}

@Composable
private fun BulkRow(number: Int, status: BulkStatus) {
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
    }
}
