package com.cashmemer.ui.receipts

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    val scanBarcode = rememberLauncherForActivityResult(ScanBarcodeContract()) { code ->
        code?.let(viewModel::addByBarcode)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.receipt_details))
                    state.draftSavedAt?.let {
                        Text(
                            text = stringResource(R.string.draft_saved, Format.time(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.placeName,
                    onValueChange = viewModel::setPlaceName,
                    label = { Text(stringResource(R.string.place_store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.locationAddress,
                    onValueChange = viewModel::setLocationAddress,
                    label = { Text(stringResource(R.string.location_address)) },
                    trailingIcon = {
                        if (state.locatingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Row {
                                IconButton(
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
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.MyLocation,
                                        contentDescription = "Use current location",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        pickOnMap.launch(
                                            state.latitude?.let { lat ->
                                                state.longitude?.let { lng -> lat to lng }
                                            }
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Map,
                                        contentDescription = "Pick on map",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    OutlinedTextField(
                        value = state.currencyCode,
                        onValueChange = { viewModel.setCurrency(it.uppercase()) },
                        label = { Text(stringResource(R.string.currency)) },
                        singleLine = true,
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
                captured = state.signatureBase64 != null,
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
                onClick = { onSave(name, price.toDoubleOrNull() ?: 0.0) },
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
                onAdd(
                    ReceiptItem(
                        productName = name.trim(),
                        qty = qty.toDoubleOrNull() ?: 1.0,
                        unitPrice = price.toDoubleOrNull() ?: 0.0,
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
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.take(8).forEach { suggestion ->
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
    onTaxChange: (Double) -> Unit,
    onCashGivenChange: (Double) -> Unit,
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = if (state.discount == 0.0) "" else state.discount.toString(),
                onValueChange = { onDiscountChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text(stringResource(R.string.discount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = if (state.taxPercent == 0.0) "" else state.taxPercent.toString(),
                onValueChange = { onTaxChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text(stringResource(R.string.tax_percent)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        TotalRow("Subtotal", state.subtotal, state.currencyCode)
        TotalRow("Discount", -state.discount, state.currencyCode)
        TotalRow("Tax", state.taxAmount, state.currencyCode)
        TotalRow("Grand Total", state.total, state.currencyCode, emphasised = true)

        OutlinedTextField(
            value = if (state.cashGiven == 0.0) "" else state.cashGiven.toString(),
            onValueChange = { onCashGivenChange(it.toDoubleOrNull() ?: 0.0) },
            label = { Text(stringResource(R.string.cash_given)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.cashGiven > 0) {
            TotalRow("Change Amount", state.changeAmount, state.currencyCode, emphasised = true)
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
    captured: Boolean,
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
            if (captured) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.captured)) },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }

        SignaturePad(onSignatureChanged = onSignatureChanged)

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
