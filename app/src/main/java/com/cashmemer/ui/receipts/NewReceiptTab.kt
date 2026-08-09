package com.cashmemer.ui.receipts

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.model.ReceiptItem
import androidx.compose.ui.res.stringResource
import com.cashmemer.R
import com.cashmemer.core.ui.theme.Dimens
import com.cashmemer.core.util.Format
import com.cashmemer.ui.localized
import com.cashmemer.location.LocationResolver
import com.cashmemer.location.PickLocationContract
import com.cashmemer.print.ReceiptDelivery
import com.cashmemer.scan.CaptureReceiptContract
import com.cashmemer.scan.ScanBarcodeContract
import com.cashmemer.ui.components.PrimaryButton
import com.cashmemer.ui.components.SecondaryButton
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle

/** Cap on how many photos one bulk scan will send to the parser. */
private const val MAX_BULK_SCAN = 10

/**
 * Parses a number the shopkeeper typed, tolerating the grouping commas a phone
 * keyboard or a paste can introduce. "1,000.00" was coming back null from a bare
 * toDoubleOrNull and defaulting to zero — which is how a Rs 1,000 item landed on
 * the receipt as Rs 0.00.
 */
private fun String.toAmount(): Double? =
    trim().replace(",", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()

@Composable
fun NewReceiptTab(
    settings: AppSettings,
    viewModel: ReceiptFormViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val members by viewModel.members.collectAsState()
    val products by viewModel.products.collectAsState()

    val captureReceipt = rememberLauncherForActivityResult(CaptureReceiptContract()) { uri ->
        uri?.let(viewModel::scanReceiptFrom)
    }
    val scanBarcode = rememberLauncherForActivityResult(ScanBarcodeContract()) { codes ->
        viewModel.addByBarcodes(codes)
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::scanReceiptFrom) }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_BULK_SCAN),
    ) { uris -> viewModel.scanReceipts(uris) }

    val imagesOnly = ActivityResultContracts.PickVisualMedia.ImageOnly

    val context = LocalContext.current

    // Auto-print / auto-send need an Activity window, so they run here rather
    // than in the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.generated.collect { receipt ->
            ReceiptDelivery.deliver(context, receipt, settings)?.let { done ->
                viewModel.reportDelivery(done)
            }
        }
    }

    // Scan confirmations must not be scrollable away — a toast always shows.
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    state.unknownBarcode?.let { barcode ->
        UnknownBarcodeDialog(
            barcode = barcode,
            onDismiss = viewModel::dismissUnknownBarcode,
            onSave = { name, price ->
                viewModel.createProductForBarcode(barcode, name, price)
            },
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation()
    }
    val pickOnMap = rememberLauncherForActivityResult(PickLocationContract()) { picked ->
        picked?.let {
            viewModel.setPickedLocation(it.address, it.latitude, it.longitude)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScannerCard(
                scanning = state.scanning,
                onScanReceipt = { captureReceipt.launch(Unit) },
                onImportImage = {
                    pickImage.launch(PickVisualMediaRequest(imagesOnly))
                },
                onBulkScan = {
                    pickImages.launch(PickVisualMediaRequest(imagesOnly))
                },
                onBarcodeScan = { scanBarcode.launch(Unit) },
            )
        }

        item {
            SectionCard {
                // Heading first, then the draft stamp on its own line beneath.
                // Side by side, the two collided in Urdu — the right-to-left
                // heading and the stamp fought over the same corner. A line of
                // its own reads cleanly in both languages.
                SectionTitle(stringResource(R.string.receipt_details))
                state.draftSavedAt?.let {
                    Text(
                        text = stringResource(R.string.draft_saved, Format.time(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                OutlinedTextField(
                    value = state.placeName,
                    onValueChange = viewModel::setPlaceName,
                    label = { Text(stringResource(R.string.place_store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // The two location buttons used to live inside this field's
                // trailing icon, which left barely forty points of visible text
                // — you could not read the address you were typing. They are
                // their own row now, and the field gets its full width.
                OutlinedTextField(
                    value = state.locationAddress,
                    onValueChange = viewModel::setLocationAddress,
                    label = { Text(stringResource(R.string.location_address)) },
                    minLines = 2,
                    trailingIcon = if (state.locatingAddress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.use_current_location),
                        icon = Icons.Filled.MyLocation,
                        enabled = !state.locatingAddress,
                        onClick = {
                            if (LocationResolver.hasPermission(context)) {
                                viewModel.useCurrentLocation()
                            } else {
                                locationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.pick_on_map),
                        icon = Icons.Filled.Map,
                        onClick = {
                            pickOnMap.launch(
                                state.latitude?.let { lat ->
                                    state.longitude?.let { lng -> lat to lng }
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                MemberPicker(
                    members = members,
                    selectedName = state.selectedMember?.name,
                    onSelect = viewModel::selectMember,
                )

                OutlinedTextField(
                    value = state.customerName,
                    onValueChange = viewModel::setCustomerName,
                    label = { Text(stringResource(R.string.customer_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerPhone,
                    onValueChange = viewModel::setCustomerPhone,
                    label = { Text(stringResource(R.string.customer_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerEmail,
                    onValueChange = viewModel::setCustomerEmail,
                    label = { Text(stringResource(R.string.customer_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard {
                Text(stringResource(R.string.select_currency_category), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CurrencyPicker(
                        selected = state.currencyCode,
                        onSelect = viewModel::setCurrency,
                        modifier = Modifier.weight(1f),
                    )
                    CategoryPicker(
                        selected = state.category,
                        onSelect = viewModel::setCategory,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(stringResource(R.string.payment_type), style = MaterialTheme.typography.titleMedium)
                PaymentTypeGrid(
                    selected = state.paymentType,
                    onSelect = viewModel::setPaymentType,
                )
            }
        }

        item {
            AddItemCard(
                productNames = products.map { it.name }.distinct(),
                onAdd = viewModel::addItem,
                lookupPrice = { name -> products.firstOrNull { it.name == name }?.price },
            )
        }

        itemsIndexed(state.items) { index, item ->
            LineItemRow(
                item = item,
                currencyCode = state.currencyCode,
                onRemove = { viewModel.removeItem(index) },
            )
        }

        item {
            TotalsCard(
                state = state,
                onDiscountChange = viewModel::setDiscount,
                onDiscountModeChange = viewModel::setDiscountIsPercent,
                onTaxChange = viewModel::setTaxPercent,
                onCashGivenChange = viewModel::setCashGiven,
            )
        }

        item {
            SectionCard {
                OutlinedTextField(
                    value = state.notesPage1,
                    onValueChange = viewModel::setNotesPage1,
                    label = { Text(stringResource(R.string.notes_page_1)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.notesPage2,
                    onValueChange = viewModel::setNotesPage2,
                    label = { Text(stringResource(R.string.notes_page_2)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SignatureCard(
                signatureBase64 = state.signatureBase64,
                saveAsDefault = state.saveSignatureAsDefault,
                onSaveAsDefaultChange = viewModel::setSaveSignatureAsDefault,
                onSignatureChanged = viewModel::setSignature,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::clear,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.action_clear)) }

                Button(
                    onClick = { viewModel.generate() },
                    enabled = state.canGenerate,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.action_generate))
                }
            }
        }

        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** Offers to save a scanned code that matched nothing in inventory. */
@Composable
private fun UnknownBarcodeDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
) {
    var name by remember(barcode) { mutableStateOf("") }
    var price by remember(barcode) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_not_found)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Barcode $barcode isn't in your inventory yet. " +
                        "Add it now and it will be recognised next time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, price.toAmount() ?: 0.0) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save_and_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ScannerCard(
    scanning: Boolean,
    onScanReceipt: () -> Unit,
    onImportImage: () -> Unit,
    onBulkScan: () -> Unit,
    onBarcodeScan: () -> Unit,
) {
    SectionCard(accent = true) {
        Text(
            text = stringResource(R.string.scanner_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.scanner_body),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapTight)) {
            SecondaryButton(
                text = stringResource(R.string.scan_receipt),
                icon = Icons.Filled.PhotoCamera,
                onClick = onScanReceipt,
                enabled = !scanning,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.import_image),
                icon = Icons.Filled.Image,
                onClick = onImportImage,
                enabled = !scanning,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapTight)) {
            PrimaryButton(
                text = stringResource(R.string.bulk_scan),
                icon = Icons.Filled.Image,
                onClick = onBulkScan,
                enabled = !scanning,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.barcode_scan),
                icon = Icons.Filled.QrCodeScanner,
                onClick = onBarcodeScan,
                enabled = !scanning,
                modifier = Modifier.weight(1f),
            )
        }

        if (scanning) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberPicker(
    members: List<com.cashmemer.core.model.Member>,
    selectedName: String?,
    onSelect: (com.cashmemer.core.model.Member?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName ?: "Select Member",
            onValueChange = {},
            readOnly = true,
            leadingIcon = { Icon(Icons.Filled.PersonSearch, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text("${member.name} · ${member.phone}") },
                    onClick = {
                        onSelect(member)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The small Rs / % switch on the Discount field. */
@Composable
private fun DiscountModeToggle(
    isPercent: Boolean,
    currencyCode: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DiscountModeChip(
            label = com.cashmemer.core.data.CurrencyNames.symbolOf(currencyCode),
            selected = !isPercent,
        ) { onChange(false) }
        DiscountModeChip(label = "%", selected = isPercent) { onChange(true) }
    }
}

@Composable
private fun DiscountModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 30.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The currencies offered in the receipt form's quick picker. */
private val COMMON_CURRENCIES = listOf(
    "PKR", "USD", "EUR", "GBP", "SAR", "AED", "INR",
    "CNY", "IRR", "IRT", "TRY", "RUB", "JPY", "CAD", "AUD",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Whatever is selected shows even if it is not in the common list.
    val options = remember(selected) {
        (listOf(selected) + COMMON_CURRENCIES).distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = "$selected  ${com.cashmemer.core.data.CurrencyNames.symbolOf(selected)}",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = {
                        Text("$code  (${com.cashmemer.core.data.CurrencyNames.symbolOf(code)})")
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    selected: ReceiptCategory,
    onSelect: (ReceiptCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.localized(),
            onValueChange = {},
            readOnly = true,
            // Without singleLine, a narrow half-width field wrapped "Shopping"
            // to "Shoppin / g". One line, and the field shows it whole.
            singleLine = true,
            label = { Text(stringResource(R.string.category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReceiptCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.localized()) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaymentTypeGrid(
    selected: PaymentType,
    onSelect: (PaymentType) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaymentType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.localized()) },
            )
        }
    }
}

@Composable
private fun AddItemCard(
    productNames: List<String>,
    onAdd: (ReceiptItem) -> Unit,
    lookupPrice: (String) -> Double?,
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }

    SectionCard {
        SectionTitle(stringResource(R.string.add_purchased_items))

        ProductNameField(
            value = name,
            suggestions = productNames,
            onValueChange = { picked ->
                name = picked
                lookupPrice(picked)?.let { price = Format.amount(it) }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text(stringResource(R.string.qty)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(stringResource(R.string.price_total)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = {
                val quantity = (qty.toAmount() ?: 1.0).takeIf { it > 0 } ?: 1.0
                // The field is labelled "Price (Total)", so split it back to a
                // unit price — the row and the receipt both store per-unit.
                val lineTotal = price.toAmount() ?: 0.0
                onAdd(
                    ReceiptItem(
                        productName = name.trim(),
                        qty = quantity,
                        unitPrice = lineTotal / quantity,
                    )
                )
                name = ""
                qty = "1"
                price = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.add_item)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductNameField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Blank, or a value that already matches a product (i.e. one was picked),
    // shows the whole list; a half-typed value narrows it. This is what lets the
    // arrow open the full list without typing first — the old code kept the menu
    // shut until a partial match existed.
    val filtered = remember(value, suggestions) {
        val q = value.trim()
        if (q.isEmpty() || suggestions.any { it.equals(q, ignoreCase = true) }) suggestions
        else suggestions.filter { it.contains(q, ignoreCase = true) }
    }
    val open = expanded && filtered.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.product_name)) },
            singleLine = true,
            // The framework's own trailing icon is wired into the anchor, so a
            // tap on it opens the menu reliably — the hand-rolled IconButton did
            // not, which is why the arrow "did nothing".
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { expanded = false },
        ) {
            filtered.take(20).forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LineItemRow(
    item: ReceiptItem,
    currencyCode: String,
    onRemove: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.productName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${Format.amount(item.qty)} × " +
                        Format.amountWithCurrency(item.unitPrice, currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Format.amountWithCurrency(item.lineTotal, currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove item",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(
    state: ReceiptFormState,
    onDiscountChange: (Double) -> Unit,
    onDiscountModeChange: (Boolean) -> Unit,
    onTaxChange: (Double) -> Unit,
    onCashGivenChange: (Double) -> Unit,
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = if (state.discount == 0.0) "" else state.discount.toString(),
                onValueChange = { onDiscountChange(it.toAmount() ?: 0.0) },
                label = { Text(stringResource(R.string.discount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // A small Rs / % switch inside the field flips how the number is
                // read — a flat amount, or a percentage of the subtotal.
                trailingIcon = {
                    DiscountModeToggle(
                        isPercent = state.discountIsPercent,
                        currencyCode = state.currencyCode,
                        onChange = onDiscountModeChange,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = if (state.taxPercent == 0.0) "" else state.taxPercent.toString(),
                onValueChange = { onTaxChange(it.toAmount() ?: 0.0) },
                label = { Text(stringResource(R.string.tax_percent)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        TotalRow(stringResource(R.string.subtotal), state.subtotal, state.currencyCode)
        TotalRow(stringResource(R.string.discount), -state.discountAmount, state.currencyCode)
        TotalRow(stringResource(R.string.tax), state.taxAmount, state.currencyCode)
        TotalRow(
            stringResource(R.string.grand_total),
            state.total,
            state.currencyCode,
            emphasised = true,
        )

        OutlinedTextField(
            value = if (state.cashGiven == 0.0) "" else state.cashGiven.toString(),
            onValueChange = { onCashGivenChange(it.toAmount() ?: 0.0) },
            label = { Text(stringResource(R.string.cash_given)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.cashGiven > 0) {
            TotalRow(
                stringResource(R.string.change_amount),
                state.changeAmount,
                state.currencyCode,
                emphasised = true,
            )
        }
    }
}

@Composable
private fun TotalRow(
    label: String,
    amount: Double,
    currencyCode: String,
    emphasised: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasised) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = Format.amountWithCurrency(amount, currencyCode),
            style = if (emphasised) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.bodyLarge,
            color = if (emphasised) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SignatureCard(
    signatureBase64: String?,
    saveAsDefault: Boolean,
    onSaveAsDefaultChange: (Boolean) -> Unit,
    onSignatureChanged: (String?) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(stringResource(R.string.digital_signature))
            if (signatureBase64 != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.captured)) },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }

        SignaturePad(
            signatureBase64 = signatureBase64,
            onSignatureChanged = onSignatureChanged,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveAsDefault, onCheckedChange = onSaveAsDefaultChange)
            Text(stringResource(R.string.save_default_signature))
        }

        OutlinedButton(
            onClick = { onSignatureChanged(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.clear_and_redraw))
        }
    }
}
