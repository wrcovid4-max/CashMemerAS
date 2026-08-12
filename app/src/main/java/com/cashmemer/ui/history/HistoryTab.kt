package com.cashmemer.ui.history

import com.cashmemer.ui.components.BoldGlyph

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.backup.BackupWriter
import com.cashmemer.R
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.print.ReceiptOutput
import com.cashmemer.ui.localized
import com.cashmemer.ui.components.IconAction
import com.cashmemer.ui.components.SearchField
import com.cashmemer.ui.components.SecondaryButton
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.receipts.ReceiptEditBus
import com.cashmemer.ui.viewer.openReceiptViewer

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

    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var pinnedExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.search_receipts),
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
                    BoldGlyph(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text(
                        text = if (range.from == 0L) stringResource(R.string.start_date)
                        else Format.date(range.from),
                    )
                }
                OutlinedButton(
                    onClick = { pickingTo = true },
                    modifier = Modifier.weight(1f),
                ) {
                    BoldGlyph(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text(
                        text = if (range.to == 0L) stringResource(R.string.end_date)
                        else Format.date(range.to),
                    )
                }
            }
        }

        if (range.from != 0L || range.to != 0L) {
            item {
                TextButton(onClick = viewModel::clearRange) { Text(stringResource(R.string.clear_date_filter)) }
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
                    Text(
                        text = stringResource(R.string.action_select_all),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconAction(
                        icon = Icons.Filled.Print,
                        label = stringResource(R.string.action_print),
                        onClick = {
                            viewModel.renderPdf(selected, forPrinting = true) { file ->
                                ReceiptOutput.print(
                                    context,
                                    file,
                                    context.getString(R.string.app_name),
                                )
                            }
                        },
                    )
                    IconAction(
                        icon = Icons.Filled.Share,
                        label = stringResource(R.string.action_share),
                        onClick = {
                            viewModel.renderPdf(selected) { file ->
                                ReceiptOutput.share(context, file)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    IconAction(
                        icon = Icons.Filled.Delete,
                        label = stringResource(R.string.action_delete),
                        onClick = viewModel::deleteSelected,
                        modifier = Modifier.padding(start = 8.dp),
                        danger = true,
                    )
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
                    TextButton(onClick = viewModel::consumeError) { Text(stringResource(R.string.action_dismiss)) }
                }
            }
        }

        val pinned = receipts.filter { it.pinned }
        val others = receipts.filter { !it.pinned }

        // A tappable header lets the shopkeeper fold the pinned receipts away so
        // a long list of favourites doesn't bury the recent ones.
        if (pinned.isNotEmpty()) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pinnedExpanded = !pinnedExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoldGlyph(Icons.Filled.PushPin, contentDescription = null)
                        Text(
                            text = stringResource(R.string.pinned_count, pinned.size),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        BoldGlyph(
                            imageVector = if (pinnedExpanded) Icons.Filled.ExpandLess
                            else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        items(if (pinnedExpanded) receipts else others, key = { it.id }) { receipt ->
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
                onOpen = { context.openReceiptViewer(receipt.id) },
                onPrint = {
                    viewModel.renderPdf(listOf(receipt.id), forPrinting = true) { file ->
                        ReceiptOutput.print(
                            context,
                            file,
                            context.getString(R.string.print_job_receipt, receipt.id),
                        )
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
                    text = stringResource(R.string.no_receipts_match),
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
            TextButton(onClick = { onPicked(state.selectedDateMillis) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.action_clear)) }
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
    // Closed on arrival. Six tiles and a paragraph of AI text pushed the actual
    // receipts below the fold every time History was opened.
    var open by remember { mutableStateOf(false) }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoldGlyph(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.weekly_ai_summary),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (open) {
                        stringResource(R.string.business_insights)
                    } else {
                        // Closed, the card still earns its space by showing the
                        // one number most worth glancing at.
                        stringResource(
                            R.string.week_spend_summary,
                            Format.amountWithCurrency(summary.totalSpend, summary.currencyCode),
                            summary.transactions,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BoldGlyph(
                imageVector = if (open) Icons.Filled.ExpandLess
                else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (open) R.string.hide_details else R.string.show_details
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        stringResource(R.string.total_spend),
                        Format.amountWithCurrency(summary.totalSpend, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile(stringResource(R.string.transactions), summary.transactions.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        stringResource(R.string.avg_value),
                        Format.amountWithCurrency(summary.averageValue, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile(stringResource(R.string.top_customer), summary.topCustomer, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        stringResource(R.string.total_tax),
                        Format.amountWithCurrency(summary.totalTax, summary.currencyCode),
                        Modifier.weight(1f),
                    )
                    StatTile(
                        stringResource(R.string.total_discount),
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
                    BoldGlyph(Icons.Filled.AutoAwesome, contentDescription = null)
                    Text(stringResource(if (summary.insight == null) R.string.generate_insight else R.string.regenerate_insight))
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
    onOpen: () -> Unit,
    onPrint: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        // Two lines, not one. Sharing a single row with the amount, the pin and
        // the chevron left the shop name about eighty points of width, which is
        // why "Testing" came out as "Testin / g". The name now owns its line
        // and the money sits under it, where there is room for both.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })

            // Tapping the row opens the memo, the way it always did.
            Text(
                text = receipt.placeName.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp),
            )

            IconButton(onClick = onPin) {
                BoldGlyph(
                    Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.pin_receipt),
                    tint = if (receipt.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleExpanded) {
                BoldGlyph(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.hide_details else R.string.show_details
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.customerName.ifBlank {
                        stringResource(R.string.walk_in_customer)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Format.timestamp(receipt.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
            Text(
                text = Format.amountWithCurrency(receipt.total, receipt.currencyCode),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
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
                        BoldGlyph(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.notes_page_1) + ": " + receipt.notesPage1,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                if (receipt.locationAddress.isNotBlank()) {
                    Row {
                        BoldGlyph(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.saved_location) + ": " + receipt.locationAddress,
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
                        stringResource(R.string.payment_label) + ": " + PaymentType.from(receipt.paymentType).localized(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.change_label) + ": " + Format.amountWithCurrency(receipt.changeAmount, receipt.currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconAction(
                        Icons.Filled.Visibility,
                        stringResource(R.string.open_receipt),
                        onOpen,
                        Modifier.weight(1f),
                    )
                    IconAction(
                        Icons.Filled.Share,
                        stringResource(R.string.action_share),
                        onShare,
                        Modifier.weight(1f),
                    )
                    IconAction(
                        Icons.Filled.Print,
                        stringResource(R.string.action_print),
                        onPrint,
                        Modifier.weight(1f),
                    )
                    IconAction(
                        Icons.Filled.ContentCopy,
                        stringResource(R.string.action_duplicate),
                        onDuplicate,
                        Modifier.weight(1f),
                    )
                    IconAction(
                        Icons.Filled.Edit,
                        stringResource(R.string.action_edit),
                        onEdit,
                        Modifier.weight(1f),
                    )
                    IconAction(
                        Icons.Filled.Delete,
                        stringResource(R.string.action_delete),
                        onDelete,
                        Modifier.weight(1f),
                        danger = true,
                    )
                }
            }
        }
    }
}

