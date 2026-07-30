package com.cashmemer.ui.history

import android.app.Application
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.SectionCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

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
}

@Composable
fun HistoryTab(viewModel: HistoryViewModel = viewModel()) {
    val receipts by viewModel.receipts.collectAsState()
    val query by viewModel.query.collectAsState()
    val selected by viewModel.selected.collectAsState()

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
                OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Text("  Backup JSON")
                }
                OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) {
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
                    IconButton(onClick = { }) {
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

        items(receipts, key = { it.id }) { receipt ->
            HistoryRow(
                receipt = receipt,
                checked = receipt.id in selected,
                onCheckedChange = { viewModel.toggleSelected(receipt.id) },
                onPin = { viewModel.togglePin(receipt) },
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
