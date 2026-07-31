package com.cashmemer.ui.history

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.backup.BackupWriter
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.print.ReceiptOutput
import com.cashmemer.print.ReceiptPdfRenderer
import com.cashmemer.ui.components.SectionCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)
    private val settingsStore = SettingsStore(application)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val receipts: StateFlow<List<Receipt>> = _query
        .flatMapLatest { q -> repository.searchReceipts(q, 0L, 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) = _query.update { value }

    fun toggleSelected(id: Long) = _selected.update { current ->
        if (id in current) current - id else current + id
    }

    fun selectAll(ids: List<Long>) = _selected.update { current ->
        if (current.size == ids.size) emptySet() else ids.toSet()
    }

    fun deleteSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteReceipts(ids)
            _selected.value = emptySet()
        }
    }

    fun togglePin(receipt: Receipt) {
        viewModelScope.launch { repository.setPinned(receipt.id, !receipt.pinned) }
    }

    /**
     * Renders the given receipts to a single PDF and hands the file back so the
     * caller can print or share it with an Activity context.
     */
    fun renderPdf(ids: Collection<Long>, onReady: (File) -> Unit) {
        if (ids.isEmpty()) {
            _error.value = "Select at least one receipt"
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            val chosen = receipts.value.filter { it.id in ids }
            val pages = ReceiptOutput.pagesFor(settingsStore.settings.first().massPrint)

            ReceiptPdfRenderer
                .render(app, chosen, pages, ReceiptOutput.outputFile(app, chosen))
                .onSuccess(onReady)
                .onFailure { _error.value = it.message ?: "Could not build the PDF" }
        }
    }

    /** Writes the JSON backup straight to a file the user names. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                val json = repository.exportJson()
                app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("Could not open that file for writing")
            }
                .onSuccess { _error.value = "Backup saved" }
                .onFailure { _error.value = it.message ?: "Backup failed" }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                app.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().decodeToString()
                } ?: error("Could not read that file")
            }
                .mapCatching { json -> repository.importJson(json).getOrThrow() }
                .onSuccess { _error.value = "Restored $it record(s)" }
                .onFailure { _error.value = it.message ?: "Restore failed" }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}

@Composable
fun HistoryTab(viewModel: HistoryViewModel = viewModel()) {
    val receipts by viewModel.receipts.collectAsState()
    val query by viewModel.query.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { backupPicker.launch(BackupWriter.fileNameFor(System.currentTimeMillis())) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Text("  Backup JSON")
                }
                OutlinedButton(
                    onClick = { restorePicker.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Text("  Restore JSON")
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search by title, location, customer…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected.size == receipts.size && receipts.isNotEmpty(),
                        onCheckedChange = { viewModel.selectAll(receipts.map { it.id }) },
                    )
                    Text("Select All", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            viewModel.renderPdf(selected) { file ->
                                ReceiptOutput.print(context, file, "Cash Memer receipts")
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "Print selected")
                    }
                    IconButton(
                        onClick = {
                            viewModel.renderPdf(selected) { file ->
                                ReceiptOutput.share(context, file)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share selected")
                    }
                    IconButton(onClick = viewModel::deleteSelected) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        error?.let { text ->
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        items(receipts, key = { it.id }) { receipt ->
            HistoryRow(
                receipt = receipt,
                checked = receipt.id in selected,
                onCheckedChange = { viewModel.toggleSelected(receipt.id) },
                onPin = { viewModel.togglePin(receipt) },
                onPrint = {
                    viewModel.renderPdf(listOf(receipt.id)) { file ->
                        ReceiptOutput.print(context, file, "Receipt ${receipt.id}")
                    }
                },
            )
        }

        if (receipts.isEmpty()) {
            item {
                Text(
                    text = "No receipts yet — generate one from the Receipts tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    receipt: Receipt,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onPin: () -> Unit,
    onPrint: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.placeName.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = receipt.customerName.ifBlank { "Walk-in customer" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Format.timestamp(receipt.createdAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = Format.amountWithCurrency(receipt.total, receipt.currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            IconButton(onClick = onPrint) {
                Icon(Icons.Filled.Print, contentDescription = "Print this receipt")
            }

            IconButton(onClick = onPin) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pin receipt",
                    tint = if (receipt.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
