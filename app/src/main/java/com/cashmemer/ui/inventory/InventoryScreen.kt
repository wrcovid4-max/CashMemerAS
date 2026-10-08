package com.cashmemer.ui.inventory

import com.cashmemer.ui.components.AppleText
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Product
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.DangerIconButton
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProductFilter { ALL, ACTIVE, ARCHIVED }

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    val products: StateFlow<List<Product>> = repository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(product: Product) {
        viewModelScope.launch { repository.saveProduct(product) }
    }

    fun duplicate(product: Product) {
        viewModelScope.launch {
            repository.saveProduct(
                product.copy(id = 0, name = "${product.name} (copy)", barcode = "")
            )
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch { repository.deleteProduct(product) }
    }

    fun setArchived(product: Product, archived: Boolean) {
        viewModelScope.launch { repository.setProductArchived(product.id, archived) }
    }
}

@Composable
fun InventoryScreen(viewModel: InventoryViewModel = viewModel()) {
    val products by viewModel.products.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProductFilter.ALL) }
    var editing by remember { mutableStateOf<Product?>(null) }

    val visible = products
        .filter { product ->
            when (filter) {
                ProductFilter.ALL -> true
                ProductFilter.ACTIVE -> !product.archived
                ProductFilter.ARCHIVED -> product.archived
            }
        }
        .filter { product ->
            query.isBlank() ||
                product.name.contains(query, ignoreCase = true) ||
                product.barcode.contains(query, ignoreCase = true) ||
                product.brand.contains(query, ignoreCase = true)
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(R.string.products))
                Button(onClick = { editing = Product() }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    AppleText(stringResource(R.string.add_new))
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { AppleText(stringResource(R.string.search_products)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            AppleText(
                                stringResource(
                                    when (option) {
                                        ProductFilter.ALL -> R.string.filter_all
                                        ProductFilter.ACTIVE -> R.string.filter_active
                                        ProductFilter.ARCHIVED -> R.string.filter_archived
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }

        item {
            val active = products.count { !it.archived }
            val low = products.count { it.lowStock }
            val sellValue = products.filter { !it.archived }.sumOf { it.sellValue }
            SectionCard(accent = true) {
                AppleText(stringResource(R.string.stock_summary, active, low), style = MaterialTheme.typography.titleMedium)
                AppleText(
                    stringResource(R.string.sell_value, Format.amount(sellValue)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        items(visible, key = { it.id }) { product ->
            ProductRow(
                product = product,
                onEdit = { editing = product },
                onDuplicate = { viewModel.duplicate(product) },
                onArchive = { viewModel.setArchived(product, !product.archived) },
                onDelete = { viewModel.delete(product) },
            )
        }

        if (visible.isEmpty()) {
            item {
                AppleText(
                    stringResource(R.string.no_products_match),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { product ->
        ProductEditorDialog(
            product = product,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }
}

@Composable
private fun ProductRow(
    product: Product,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        AppleText(product.name, style = MaterialTheme.typography.titleMedium)
        if (product.barcode.isNotBlank()) {
            AppleText(
                stringResource(R.string.barcode) + ": " + product.barcode,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                if (product.category.isNotBlank()) {
                    AppleText(
                        stringResource(R.string.category) + ": " + product.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppleText(
                    stringResource(R.string.stock) + ": " + Format.amount(product.stock) + " " + product.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (product.lowStock) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppleText(
                Format.amount(product.price),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {},
                label = { AppleText(stringResource(if (product.archived) R.string.archived else R.string.active)) },
            )
            Row {
                IconButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.archive))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_duplicate))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                DangerIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
) {
    var name by remember { mutableStateOf(product.name) }
    var barcode by remember { mutableStateOf(product.barcode) }
    var brand by remember { mutableStateOf(product.brand) }
    var category by remember { mutableStateOf(product.category) }
    var purchasePrice by remember { mutableStateOf(product.purchasePrice.toString()) }
    var price by remember { mutableStateOf(product.price.toString()) }
    var taxPercent by remember { mutableStateOf(product.taxPercent.toString()) }
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var unit by remember { mutableStateOf(product.unit) }

    val title = stringResource(if (product.id == 0L) R.string.add_new_product else R.string.edit_product)

    // A custom Dialog instead of AlertDialog: AlertDialog is a fixed-size window the
    // keyboard shoves around, which made fields disappear and the whole dialog jump.
    // Here the dialog lifts above the keyboard with imePadding, the field area scrolls
    // inside a bounded height, and Cancel/Save always stay visible.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    AppleText(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { AppleText(stringResource(R.string.name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = { barcode = it },
                            label = { AppleText(stringResource(R.string.barcode)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { AppleText(stringResource(R.string.brand)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { AppleText(stringResource(R.string.category)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = purchasePrice,
                                onValueChange = { purchasePrice = it },
                                label = { AppleText(stringResource(R.string.cost)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = price,
                                onValueChange = { price = it },
                                label = { AppleText(stringResource(R.string.price)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = stock,
                                onValueChange = { stock = it },
                                label = { AppleText(stringResource(R.string.stock)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { AppleText(stringResource(R.string.unit)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = taxPercent,
                            onValueChange = { taxPercent = it },
                            label = { AppleText(stringResource(R.string.tax_percent)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            AppleText(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = {
                                onSave(
                                    product.copy(
                                        name = name.trim(),
                                        barcode = barcode.trim(),
                                        brand = brand.trim(),
                                        category = category.trim(),
                                        purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
                                        price = price.toDoubleOrNull() ?: 0.0,
                                        taxPercent = taxPercent.toDoubleOrNull() ?: 0.0,
                                        stock = stock.toDoubleOrNull() ?: 0.0,
                                        unit = unit.ifBlank { "piece" },
                                    )
                                )
                            },
                            enabled = name.isNotBlank(),
                        ) {
                            AppleText(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }
}
