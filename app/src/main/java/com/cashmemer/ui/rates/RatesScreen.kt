package com.cashmemer.ui.rates

import com.cashmemer.ui.components.BoldGlyph

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.BuildConfig
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.CurrencyRate
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RatesUiState(
    val refreshing: Boolean = false,
    val lastUpdated: Long = 0L,
    val error: String? = null,
)

class RatesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    private val _ui = MutableStateFlow(RatesUiState())
    val ui: StateFlow<RatesUiState> = _ui.asStateFlow()

    val rates: StateFlow<List<CurrencyRate>> = repository.observeRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** False when EXCHANGE_RATE_API_KEY was never put in local.properties. */
    val keyConfigured: Boolean = BuildConfig.EXCHANGE_RATE_API_KEY.isNotBlank()

    init {
        viewModelScope.launch {
            val last = repository.ratesLastUpdated()
            _ui.update { it.copy(lastUpdated = last) }
            // Only hit the network on a cold table; the feed updates daily.
            if (last == 0L) refresh()
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        _ui.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            repository.refreshRates()
                .onSuccess {
                    _ui.update {
                        it.copy(refreshing = false, lastUpdated = System.currentTimeMillis())
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(refreshing = false, error = error.message ?: "Refresh failed")
                    }
                }
        }
    }

    fun addCustom(code: String, name: String, rate: Double) {
        if (code.isBlank()) return
        viewModelScope.launch { repository.addCustomRate(code, name, rate) }
    }
}

@Composable
fun RatesScreen(viewModel: RatesViewModel = viewModel()) {
    val rates by viewModel.rates.collectAsState()
    val ui by viewModel.ui.collectAsState()

    var query by remember { mutableStateOf("") }
    var customCode by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customRate by remember { mutableStateOf("") }

    val visible = rates.filter {
        query.isBlank() ||
            it.code.contains(query, ignoreCase = true) ||
            it.displayName.contains(query, ignoreCase = true)
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
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.exchange_rates))
                    Text(
                        text = if (ui.lastUpdated == 0L) stringResource(R.string.not_refreshed)
                        else stringResource(R.string.last_updated, Format.timestamp(ui.lastUpdated)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ui.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    IconButton(onClick = viewModel::refresh) {
                        BoldGlyph(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // A silent empty list was indistinguishable from "still loading", so
        // every failure state now says what is wrong and what to do about it.
        if (!viewModel.keyConfigured) {
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.no_rate_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.no_rate_key_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ui.error?.let { error ->
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.rates_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }

        if (rates.isEmpty() && ui.error == null && !ui.refreshing && viewModel.keyConfigured) {
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.no_rates_yet),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.load_rates)) }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(visible, key = { it.code }) { rate ->
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rate.flagEmoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rate.code, style = MaterialTheme.typography.titleMedium)
                        Text(
                            com.cashmemer.core.data.CurrencyNames.of(rate.code),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Format.rate(rate.rate),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.register_custom_currency))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customCode,
                        onValueChange = { customCode = it.uppercase().take(3) },
                        label = { Text(stringResource(R.string.code)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = customRate,
                        onValueChange = { customRate = it },
                        label = { Text(stringResource(R.string.rate_vs_usd)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text(stringResource(R.string.display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.addCustom(
                            customCode,
                            customName.ifBlank { customCode },
                            customRate.toDoubleOrNull() ?: 0.0,
                        )
                        customCode = ""
                        customName = ""
                        customRate = ""
                    },
                    enabled = customCode.length == 3,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.register_custom_currency)) }
            }
        }
    }
}
