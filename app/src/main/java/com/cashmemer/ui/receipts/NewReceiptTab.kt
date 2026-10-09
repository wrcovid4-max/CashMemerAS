package com.cashmemer.ui.receipts

import com.cashmemer.ui.components.AppleText
import com.cashmemer.ui.components.InfoIcon
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.PopupProperties
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
    onOpenBulkScan: () -> Unit = {},
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

    val editingBulk by BulkScanSession.editingId.collectAsState()
    val saveRequested by BulkScanSession.saveRequested.collectAsState()
    LaunchedEffect(editingBulk) {
        val id = editingBulk ?: return@LaunchedEffect
        val item = BulkScanSession.items.value.firstOrNull { it.id == id } ?: return@LaunchedEffect
        viewModel.loadBulkItem(item.edited ?: viewModel.stateFromParsed(item.parsed))
    }
    LaunchedEffect(saveRequested) {
        if (!saveRequested) return@LaunchedEffect
        val forms = BulkScanSession.items.value.map { it.edited ?: viewModel.stateFromParsed(it.parsed) }
        viewModel.saveBulk(forms)
        BulkScanSession.finishSave()
    }

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
            if (editingBulk != null) {
                Button(
                    onClick = {
                        editingBulk?.let { BulkScanSession.stash(it, viewModel.currentState) }
                        onOpenBulkScan()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bulk_back_to_bulk))
                }
            }
        }
        item {
            ScannerCard(
                scanning = state.scanning,
                onScanReceipt = { captureReceipt.launch(Unit) },
                onImportImage = {
                    pickImage.launch(PickVisualMediaRequest(imagesOnly))
                },
                onBulkScan = onOpenBulkScan,
                onBarcodeScan = { scanBarcode.launch(Unit) },
            )
        }

        item {
            SectionCard {
                // Heading first, then the draft stamp on its own line beneath.
                // Side by side, the two collided in Urdu — the right-to-left
                // heading and the stamp fought over the same corner. A line of
                // its own reads cleanly in both languages.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.receipt_details))
                    InfoIcon(
                        title = stringResource(R.string.receipt_details),
                        body = stringResource(R.string.info_receipt_form),
                    )
                }
                state.draftSavedAt?.let {
                    AppleText(
                        text = stringResource(R.string.draft_saved, Format.time(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                OutlinedTextField(
                    value = state.placeName,
                    onValueChange = viewModel::setPlaceName,
                    label = { AppleText(stringResource(R.string.place_store_name)) },
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
                    label = { AppleText(stringResource(R.string.location_address)) },
                    // Starts at the normal one-line height like the other fields
                    // and only grows when a long address is actually entered —
                    // the fixed two-line box read as oversized when empty.
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
                        {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
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
                    label = { AppleText(stringResource(R.string.customer_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerPhone,
                    onValueChange = viewModel::setCustomerPhone,
                    label = { AppleText(stringResource(R.string.customer_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerEmail,
                    onValueChange = viewModel::setCustomerEmail,
                    label = { AppleText(stringResource(R.string.customer_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard {
                AppleText(stringResource(R.string.select_currency_category), style = MaterialTheme.typography.titleMedium)
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

                PaymentTypePicker(
                    selected = state.paymentType,
                    onSelect = viewModel::setPaymentType,
                    modifier = Modifier.fillMaxWidth(),
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
                unitLabel = settings.priceUnit.unitLabel,
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
                onExtraFeesChange = viewModel::setExtraFees,
            )
        }

        item {
            SectionCard {
                // Locked: read-only, so every receipt prints the Settings default.
                // Editing it still happens in Settings; the lock icon says so.
                val noteLocked = settings.notePage1Locked
                OutlinedTextField(
                    value = state.notesPage1,
                    onValueChange = { if (!noteLocked) viewModel.setNotesPage1(it) },
                    readOnly = noteLocked,
                    label = { AppleText(stringResource(R.string.notes_page_1)) },
                    trailingIcon = if (noteLocked) {
                        { Icon(Icons.Filled.Lock, contentDescription = null) }
                    } else null,
                    supportingText = if (noteLocked) {
                        { AppleText(stringResource(R.string.page1_note_locked_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                val note2Locked = settings.notePage2Locked
                OutlinedTextField(
                    value = state.notesPage2,
                    onValueChange = { if (!note2Locked) viewModel.setNotesPage2(it) },
                    readOnly = note2Locked,
                    label = { AppleText(stringResource(R.string.notes_page_2)) },
                    trailingIcon = if (note2Locked) {
                        { Icon(Icons.Filled.Lock, contentDescription = null) }
                    } else null,
                    supportingText = if (note2Locked) {
                        { AppleText(stringResource(R.string.page1_note_locked_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SignatureCard(
                signatureBase64 = state.signatureBase64,
                saveAsDefault = state.saveSignatureAsDefault,
                locked = settings.signatureLocked,
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
                ) { AppleText(stringResource(R.string.action_clear)) }

                Button(
                    onClick = { viewModel.generate() },
                    enabled = state.canGenerate,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    AppleText(stringResource(R.string.action_generate))
                }
            }
        }

        state.message?.let { message ->
            item {
                AppleText(
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
        title = { AppleText(stringResource(R.string.product_not_found)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppleText(
                    "Barcode $barcode isn't in your inventory yet. " +
                        "Add it now and it will be recognised next time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { AppleText(stringResource(R.string.product_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { AppleText(stringResource(R.string.price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, price.toAmount() ?: 0.0) },
                enabled = name.isNotBlank(),
            ) { AppleText(stringResource(R.string.save_and_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { AppleText(stringResource(R.string.action_cancel)) }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            AppleText(
                text = stringResource(R.string.scanner_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            InfoIcon(
                title = stringResource(R.string.scanner_title),
                body = stringResource(R.string.info_scanner),
            )
        }
        AppleText(
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
                icon = Icons.Filled.PhotoLibrary,
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
                text = { AppleText(stringResource(R.string.none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { AppleText("${member.name} · ${member.phone}") },
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
        AppleText(
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
            value = "$selected (${com.cashmemer.core.data.CurrencyNames.symbolOf(selected)})",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { AppleText(stringResource(R.string.currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = {
                        AppleText("$code (${com.cashmemer.core.data.CurrencyNames.symbolOf(code)})")
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
            label = { AppleText(stringResource(R.string.category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReceiptCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { AppleText(category.localized()) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentTypePicker(
    selected: PaymentType,
    onSelect: (PaymentType) -> Unit,
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
            singleLine = true,
            label = { AppleText(stringResource(R.string.payment_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { AppleText(type.localized()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
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
    // The per-unit price behind whatever's showing in the Price (Total) field, so
    // Qty edits can recompute the total instead of leaving it stuck at the price
    // for the qty that was current when the product was picked (almost always 1,
    // since picking a product is usually the very first thing done on this row).
    // Null means the total field is under the user's own manual control (they
    // typed a custom total themselves, e.g. for a discounted sale) — Qty edits
    // leave a manual total alone rather than silently overwriting it.
    var unitPrice by remember { mutableStateOf<Double?>(null) }

    fun quantityOrDefault() = (qty.toAmount() ?: 1.0).takeIf { it > 0 } ?: 1.0

    SectionCard {
        SectionTitle(stringResource(R.string.add_purchased_items))

        ProductNameField(
            value = name,
            suggestions = productNames,
            onValueChange = { picked ->
                name = picked
                lookupPrice(picked)?.let { perUnit ->
                    unitPrice = perUnit
                    price = Format.amount(perUnit * quantityOrDefault())
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = qty,
                onValueChange = { newQty ->
                    qty = newQty
                    unitPrice?.let { perUnit ->
                        val quantity = (newQty.toAmount() ?: 0.0).takeIf { it > 0 }
                        price = Format.amount(perUnit * (quantity ?: 0.0))
                    }
                },
                label = { AppleText(stringResource(R.string.qty)) },
                trailingIcon = {
                    InfoIcon(
                        title = stringResource(R.string.qty),
                        body = stringResource(R.string.info_qty),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = price,
                onValueChange = {
                    price = it
                    unitPrice = null   // user is now driving the total manually
                },
                label = { AppleText(stringResource(R.string.price_total)) },
                trailingIcon = {
                    InfoIcon(
                        title = stringResource(R.string.price_total),
                        body = stringResource(R.string.info_price_total),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = {
                val quantity = quantityOrDefault()
                // unitPrice is already the true per-unit figure when it's set — use
                // it directly rather than round-tripping lineTotal/quantity through
                // the formatted string, which only the manual-total path still needs.
                val resolvedUnitPrice = unitPrice ?: ((price.toAmount() ?: 0.0) / quantity)
                onAdd(
                    ReceiptItem(
                        productName = name.trim(),
                        qty = quantity,
                        unitPrice = resolvedUnitPrice,
                    )
                )
                name = ""
                qty = "1"
                price = ""
                unitPrice = null
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { AppleText(stringResource(R.string.add_item)) }
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
    val focusManager = LocalFocusManager.current

    // A plain Box + non-focusable DropdownMenu instead of ExposedDropdownMenuBox:
    // the exposed box focuses its text field whenever the menu opens, which is
    // exactly what raised the keyboard on an arrow tap. Here the arrow drops
    // focus and opens a menu that never takes focus, so only tapping the field
    // text raises the keyboard.
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { AppleText(stringResource(R.string.product_name)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    expanded = !expanded
                }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ArrowDropUp
                        else Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            filtered.take(20).forEach { suggestion ->
                DropdownMenuItem(
                    text = { AppleText(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                        focusManager.clearFocus()
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
    unitLabel: String,
    onRemove: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppleText(item.productName, style = MaterialTheme.typography.titleMedium)
                // Always the cost of a single unit — never "2 Qty" — so the rate
                // reads the same however many were bought.
                AppleText(
                    text = stringResource(
                        R.string.item_unit_price,
                        unitLabel,
                        Format.amountWithCurrency(item.unitPrice, currencyCode),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppleText(
                text = Format.amountWithCurrency(item.lineTotal, currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            InfoIcon(
                title = item.productName,
                body = stringResource(R.string.info_line_item),
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
    onExtraFeesChange: (Double) -> Unit,
) {
    SectionCard {
        // Full width rather than side-by-side: the Rs / % switch needs room in
        // the discount field, and at half width the "Discount" label was
        // wrapping onto three lines.
        OutlinedTextField(
            value = if (state.discount == 0.0) "" else state.discount.toString(),
            onValueChange = { onDiscountChange(it.toAmount() ?: 0.0) },
            label = { AppleText(stringResource(R.string.discount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            // A small Rs / % switch inside the field flips how the number is
            // read — a flat amount, or a percentage of the subtotal.
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoIcon(
                        title = stringResource(R.string.discount),
                        body = stringResource(R.string.info_discount),
                    )
                    DiscountModeToggle(
                        isPercent = state.discountIsPercent,
                        currencyCode = state.currencyCode,
                        onChange = onDiscountModeChange,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (state.taxPercent == 0.0) "" else state.taxPercent.toString(),
            onValueChange = { onTaxChange(it.toAmount() ?: 0.0) },
            label = { AppleText(stringResource(R.string.tax_percent)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                InfoIcon(
                    title = stringResource(R.string.tax_percent),
                    body = stringResource(R.string.info_tax),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        TotalRow(
            stringResource(R.string.subtotal), state.subtotal, state.currencyCode,
            info = stringResource(R.string.info_subtotal),
        )
        TotalRow(
            stringResource(R.string.discount), -state.discountAmount, state.currencyCode,
            info = stringResource(R.string.info_discount),
        )
        if (state.showTaxBreakdown && state.taxLines.isNotEmpty()) {
            val base = (state.subtotal - state.discountAmount).coerceAtLeast(0.0)
            state.taxLines.forEach { line ->
                TotalRow(
                    "${line.name} (${Format.amount(line.percent)}%)",
                    base * line.percent / 100.0,
                    state.currencyCode,
                )
            }
        } else {
            TotalRow(
                stringResource(R.string.tax), state.taxAmount, state.currencyCode,
                info = stringResource(R.string.info_tax),
            )
        }
        if (state.extraFees > 0.0) {
            TotalRow(stringResource(R.string.extra_fees), state.extraFees, state.currencyCode)
        }
        TotalRow(
            stringResource(R.string.grand_total),
            state.total,
            state.currencyCode,
            emphasised = true,
            info = stringResource(R.string.info_grand_total),
        )

        OutlinedTextField(
            value = if (state.extraFees == 0.0) "" else state.extraFees.toString(),
            onValueChange = { onExtraFeesChange(it.toAmount() ?: 0.0) },
            label = { AppleText(stringResource(R.string.extra_fees)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (state.cashGiven == 0.0) "" else state.cashGiven.toString(),
            onValueChange = { onCashGivenChange(it.toAmount() ?: 0.0) },
            label = { AppleText(stringResource(R.string.cash_given)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                InfoIcon(
                    title = stringResource(R.string.cash_given),
                    body = stringResource(R.string.info_cash_given),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.cashGiven > 0) {
            TotalRow(
                stringResource(R.string.change_amount),
                state.changeAmount,
                state.currencyCode,
                emphasised = true,
                info = stringResource(R.string.info_change),
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
    info: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppleText(
                text = label,
                style = if (emphasised) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyLarge,
            )
            if (info != null) InfoIcon(title = label, body = info)
        }
        AppleText(
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
    locked: Boolean,
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
            if (locked) {
                AssistChip(
                    onClick = {},
                    label = { AppleText(stringResource(R.string.signature_locked)) },
                )
            } else if (signatureBase64 != null) {
                AssistChip(
                    onClick = {},
                    label = { AppleText(stringResource(R.string.captured)) },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }

        SignaturePad(
            signatureBase64 = signatureBase64,
            onSignatureChanged = onSignatureChanged,
            locked = locked,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveAsDefault, onCheckedChange = onSaveAsDefaultChange)
            AppleText(stringResource(R.string.save_default_signature))
        }

        OutlinedButton(
            onClick = { onSignatureChanged(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            AppleText(stringResource(R.string.clear_and_redraw))
        }
    }
}
