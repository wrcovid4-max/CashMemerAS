package com.cashmemer.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Aggregates used by the Dashboard tab. */
data class DashboardStats(
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val monthTotal: Double = 0.0,
    val allTimeTotal: Double = 0.0,
    val receiptCount: Int = 0,
    val currencyCode: String = "PKR",
    val topStores: List<Pair<String, Double>> = emptyList(),
    val byCategory: List<Pair<String, Double>> = emptyList(),
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    val stats: StateFlow<DashboardStats> = repository.observeReceipts()
        .map { it.toStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    private fun List<Receipt>.toStats(): DashboardStats {
        if (isEmpty()) return DashboardStats()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todays = filter {
            Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today
        }
        val thisMonth = filter {
            val date = Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
            date.month == today.month && date.year == today.year
        }

        return DashboardStats(
            todayTotal = todays.sumOf { it.total },
            todayCount = todays.size,
            monthTotal = thisMonth.sumOf { it.total },
            allTimeTotal = sumOf { it.total },
            receiptCount = size,
            currencyCode = first().currencyCode,
            topStores = groupBy { it.placeName.ifBlank { "Untitled" } }
                .map { (name, list) -> name to list.sumOf { it.total } }
                .sortedByDescending { it.second }
                .take(5),
            byCategory = groupBy { it.category }
                .map { (category, list) -> category to list.sumOf { it.total } }
                .sortedByDescending { it.second },
        )
    }
}

@Composable
fun DashboardTab(viewModel: DashboardViewModel = viewModel()) {
    val stats by viewModel.stats.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Today",
                    value = Format.amountWithCurrency(stats.todayTotal, stats.currencyCode),
                    caption = "${stats.todayCount} receipt(s)",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "This month",
                    value = Format.amountWithCurrency(stats.monthTotal, stats.currencyCode),
                    caption = "${stats.receiptCount} total",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard {
                SectionTitle("All-time sales")
                Text(
                    text = Format.amountWithCurrency(stats.allTimeTotal, stats.currencyCode),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SectionCard {
                SectionTitle("Top stores")
                if (stats.topStores.isEmpty()) {
                    Text(
                        "No data yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val max = stats.topStores.maxOfOrNull { it.second } ?: 1.0
                stats.topStores.forEach { (name, total) ->
                    BarRow(
                        label = name,
                        value = Format.amountWithCurrency(total, stats.currencyCode),
                        fraction = (total / max).toFloat(),
                    )
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("By category")
                val max = stats.byCategory.maxOfOrNull { it.second } ?: 1.0
                stats.byCategory.forEach { (category, total) ->
                    BarRow(
                        label = category.lowercase().replaceFirstChar { it.uppercase() },
                        value = Format.amountWithCurrency(total, stats.currencyCode),
                        fraction = (total / max).toFloat(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier, accent = true) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}
