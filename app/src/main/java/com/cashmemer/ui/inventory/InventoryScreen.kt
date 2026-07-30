package com.cashmemer.ui.inventory

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Product
import com.cashmemer.core.util.Format
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
                SectionTitle("Products")
                Button(onClick = { editing = Product() }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Add New")
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search products…") },
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
                            Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
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
                Text("$active active · $low low stock", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sell value: ${Format.amount(sellValue)}",
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
                Text(
                    "No products match.",
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
        Text(product.name, style = MaterialTheme.typography.titleMedium)
        if (product.barcode.isNotBlank()) {
            Text(
                "Barcode: ${product.barcode}",
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
                    Text(
                        "Category: ${product.category}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Stock: ${Format.amount(product.stock)} ${product.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (product.lowStock) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
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
                label = { Text(if (product.archived) "Archived" else "Active") },
            )
            Row {
                IconButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = "Archive")
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
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
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var unit by remember { mutableStateOf(product.unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product.id == 0L) "Add New Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Cost") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text("Stock") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
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
                            stock = stock.toDoubleOrNull() ?: 0.0,
                            unit = unit.ifBlank { "piece" },
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
