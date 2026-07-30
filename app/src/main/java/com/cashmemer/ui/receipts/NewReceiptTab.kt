package com.cashmemer.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.model.ReceiptItem
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle

@Composable
fun NewReceiptTab(
    settings: AppSettings,
    viewModel: ReceiptFormViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val members by viewModel.members.collectAsState()
    val products by viewModel.products.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScannerCard(scanning = state.scanning) }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Receipt Details")
                    state.draftSavedAt?.let {
                        Text(
                            text = "Draft saved: ${Format.time(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.placeName,
                    onValueChange = viewModel::setPlaceName,
                    label = { Text("Place / Store Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.locationAddress,
                    onValueChange = viewModel::setLocationAddress,
                    label = { Text("Location Address (GPS)") },
                    trailingIcon = {
                        Icon(Icons.Filled.Place, contentDescription = "Use current location")
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
                    label = { Text("Customer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerPhone,
                    onValueChange = viewModel::setCustomerPhone,
                    label = { Text("Customer Phone (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.customerEmail,
                    onValueChange = viewModel::setCustomerEmail,
                    label = { Text("Customer Email (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard {
                Text("Select Currency / Category", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.currencyCode,
                        onValueChange = { viewModel.setCurrency(it.uppercase()) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    CategoryPicker(
                        selected = state.category,
                        onSelect = viewModel::setCategory,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Payment Type", style = MaterialTheme.typography.titleMedium)
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
            )
        }

        item {
            SectionCard {
                OutlinedTextField(
                    value = state.notesPage1,
                    onValueChange = viewModel::setNotesPage1,
                    label = { Text("Notes (Page 1)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.notesPage2,
                    onValueChange = viewModel::setNotesPage2,
                    label = { Text("Notes (Page 2)") },
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
                ) { Text("Clear") }

                Button(
                    onClick = { viewModel.generate() },
                    enabled = state.canGenerate,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("  Generate")
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

@Composable
private fun ScannerCard(scanning: Boolean) {
    SectionCard(accent = true) {
        Text(
            text = "OCR Receipt Scanner (AI Gemini)",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Take a picture of any paper receipt or pick one from your gallery, " +
                "and Gemini AI will parse and auto-populate all forms!",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  Scan Receipt")
            }
            OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text("  Import Image")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text("  Bulk Scan")
            }
            Button(onClick = { }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Barcode Scan")
            }
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
                text = { Text("None") },
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
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReceiptCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
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
                label = { Text(type.label) },
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
        SectionTitle("Add Purchased Items")

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
                label = { Text("Qty") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Price (Total)") },
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
        ) { Text("Add Item") }
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
            label = { Text("Product Name") },
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
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = if (state.discount == 0.0) "" else state.discount.toString(),
                onValueChange = { onDiscountChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Discount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = if (state.taxPercent == 0.0) "" else state.taxPercent.toString(),
                onValueChange = { onTaxChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Tax %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        TotalRow("Subtotal", state.subtotal, state.currencyCode)
        TotalRow("Discount", -state.discount, state.currencyCode)
        TotalRow("Tax", state.taxAmount, state.currencyCode)
        TotalRow("Total", state.total, state.currencyCode, emphasised = true)
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
            SectionTitle("Digital Signature")
            if (captured) {
                AssistChip(
                    onClick = {},
                    label = { Text("Captured") },
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }

        SignaturePad(onSignatureChanged = onSignatureChanged)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveAsDefault, onCheckedChange = onSaveAsDefaultChange)
            Text("Save as Default Signature")
        }

        OutlinedButton(
            onClick = { onSignatureChanged(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text("  Clear & Redraw")
        }
    }
}
