package com.cashmemer.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.backup.BackupWriter
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.print.ReceiptOutput
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.receipts.ReceiptEditBus

@Composable
fun HistoryTab(viewModel: HistoryViewModel = viewModel()) {
    val receipts by viewModel.receipts.collectAsState()
    val query by viewModel.query.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val expanded by viewModel.expanded.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val range by viewModel.range.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

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
                    onClick = {
                        backupPicker.launch(
                            BackupWriter.fileNameFor(System.currentTimeMillis())
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Text("  Backup JSON")
                }
                OutlinedButton(
                    onClick = {
                        restorePicker.launch(arrayOf("application/json", "text/plain"))
                    },
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
            WeeklySummaryCard(
                summary = summary,
                onGenerateInsight = viewModel::generateInsight,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { pickingFrom = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text(
                        text = if (range.from == 0L) "  Start Date"
                        else "  ${Format.date(range.from)}",
                    )
                }
                OutlinedButton(
                    onClick = { pickingTo = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text(
                        text = if (range.to == 0L) "  End Date"
                        else "  ${Format.date(range.to)}",
                    )
                }
            }
        }

        if (range.from != 0L || range.to != 0L) {
            item {
                TextButton(onClick = viewModel::clearRange) { Text("Clear date filter") }
            }
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
                            viewModel.renderPdf(selected, forPrinting = true) { file ->
                                ReceiptOutput.print(context, file, "Cash Memer receipts")
                            }
                        }
                    ) { Icon(Icons.Filled.Print, contentDescription = "Print selected") }
                    IconButton(
                        onClick = {
                            viewModel.renderPdf(selected) { file ->
                                ReceiptOutput.share(context, file)
                            }
                        }
                    ) { Icon(Icons.Filled.Share, contentDescription = "Share selected") }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::consumeError) { Text("Dismiss") }
                }
            }
        }

        items(receipts, key = { it.id }) { receipt ->
            HistoryRow(
                receipt = receipt,
                checked = receipt.id in selected,
                expanded = expanded == receipt.id,
                onCheckedChange = { viewModel.toggleSelected(receipt.id) },
                onToggleExpanded = { viewModel.toggleExpanded(receipt.id) },
                onPin = { viewModel.togglePin(receipt) },
                onShare = {
                    viewModel.renderPdf(listOf(receipt.id)) { file ->
                        ReceiptOutput.share(context, file)
                    }
                },
                onPdf = {
                    viewModel.renderPdf(listOf(receipt.id)) { file ->
                        ReceiptOutput.share(context, file)
                    }
                },
                onPrint = {
                    viewModel.renderPdf(listOf(receipt.id), forPrinting = true) { file ->
                        ReceiptOutput.print(context, file, "Receipt ${receipt.id}")
                    }
                },
                onDuplicate = { viewModel.duplicate(receipt) },
                onEdit = { ReceiptEditBus.requestEdit(receipt.id) },
                onDelete = { viewModel.delete(receipt) },
            )
        }

        if (receipts.isEmpty()) {
            item {
                Text(
                    text = "No receipts match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickingFrom) {
        DateDialog(
            initial = range.from.takeIf { it != 0L },
            onDismiss = { pickingFrom = false },
            onPicked = {
                viewModel.setFrom(it)
                pickingFrom = false
            },
        )
    }
    if (pickingTo) {
        DateDialog(
            initial = range.to.takeIf { it != 0L },
            // Include the whole end day, not just its midnight instant.
            onDismiss = { pickingTo = false },
            onPicked = {
                viewModel.setTo(it?.plus(END_OF_DAY_MILLIS))
                pickingTo = false
            },
        )
    }
}

private const val END_OF_DAY_MILLIS = 24L * 60 * 60 * 1000 - 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long?) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(state.selectedDateMillis) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { onPicked(null) }) { Text("Clear") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun WeeklySummaryCard(
    summary: WeeklySummary,
    onGenerateInsight: () -> Unit,
) {
    var open by remember { mutableStateOf(true) }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text("Weekly AI Summary", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Business Insights",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { open = !open }) {
                Icon(
                    imageVector = if (open) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (open) "Collapse" else "Expand",
                )
            }
        }

        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Total Spend",
                        Format.amountWithCurrency(summary.totalSpend, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile("Txns", summary.transactions.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Avg Value",
                        Format.amountWithCurrency(summary.averageValue, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile("Top Customer", summary.topCustomer, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Total Tax",
                        Format.amountWithCurrency(summary.totalTax, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "Total Discount",
                        Format.amountWithCurrency(
                            summary.totalDiscount,
                            summary.currencyCode,
                        ),
                        Modifier.weight(1f),
                    )
                }

                summary.insight?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedButton(
                    onClick = onGenerateInsight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Text(if (summary.insight == null) "  Generate insight" else "  Regenerate")
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, accent = true) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryRow(
    receipt: Receipt,
    checked: Boolean,
    expanded: Boolean,
    onCheckedChange: () -> Unit,
    onToggleExpanded: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit,
    onPdf: () -> Unit,
    onPrint: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()

                ReceiptItemCodec.decode(receipt.itemsJson).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${item.productName} × ${Format.amount(item.qty)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Format.amountWithCurrency(item.lineTotal, receipt.currencyCode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (receipt.notesPage1.isNotBlank()) {
                    Row {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Notes (Page 1): ${receipt.notesPage1}",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                if (receipt.locationAddress.isNotBlank()) {
                    Row {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Saved Location: ${receipt.locationAddress}",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Payment: ${PaymentType.from(receipt.paymentType).label}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Change: ${
                            Format.amountWithCurrency(
                                receipt.changeAmount,
                                receipt.currencyCode,
                            )
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RowAction(Icons.Filled.Share, "Share", onShare)
                    RowAction(Icons.Filled.PictureAsPdf, "PDF", onPdf)
                    RowAction(Icons.Filled.Print, "Print", onPrint)
                    RowAction(Icons.Filled.ContentCopy, "Dupe", onDuplicate)
                    RowAction(Icons.Filled.Edit, "Edit", onEdit)
                    RowAction(
                        Icons.Filled.Delete,
                        "Delete",
                        onDelete,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
