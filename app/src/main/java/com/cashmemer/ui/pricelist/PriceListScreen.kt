package com.cashmemer.ui.pricelist

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

class PriceListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    val entries: StateFlow<List<Product>> = repository.observePriceList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(product: Product) {
        viewModelScope.launch { repository.saveProduct(product.copy(inPriceList = true)) }
    }

    fun remove(product: Product) {
        viewModelScope.launch {
            // Inventory items stay in stock; only the quick-pick flag is cleared.
            if (product.barcode.isBlank() && product.stock == 0.0) {
                repository.deleteProduct(product)
            } else {
                repository.saveProduct(product.copy(inPriceList = false))
            }
        }
    }
}

/**
 * The manual price list — a short list of go-to products and prices that can be
 * dropped into any receipt. Separate from full inventory on purpose.
 */
@Composable
fun PriceListScreen(viewModel: PriceListViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsState()
    var editing by remember { mutableStateOf<Product?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(stringResource(R.string.price_list_title)) }

        item {
            Button(
                onClick = { editing = Product(inPriceList = true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_new_product))
            }
        }

        items(entries, key = { it.id }) { product ->
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Price: ${Format.amount(product.price)} ${product.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = product }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DangerIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.remove),
                        onClick = { viewModel.remove(product) },
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.price_list_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { product ->
        var name by remember(product.id) { mutableStateOf(product.name) }
        var price by remember(product.id) { mutableStateOf(product.price.toString()) }
        var unit by remember(product.id) { mutableStateOf(product.unit) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (product.id == 0L) R.string.add_new_product else R.string.edit_product)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.unit)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.save(
                            product.copy(
                                name = name.trim(),
                                price = price.toDoubleOrNull() ?: 0.0,
                                unit = unit.ifBlank { "piece" },
                            )
                        )
                        editing = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
