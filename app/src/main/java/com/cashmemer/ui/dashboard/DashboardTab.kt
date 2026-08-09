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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.data.CashMemerDatabase
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Everything the Dashboard shows.
 *
 * Deliberately one flat object rather than several: the tiles are read at a
 * glance and side by side, so they should all come from the same instant.
 */
data class DashboardStats(
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val monthTotal: Double = 0.0,
    val allTimeTotal: Double = 0.0,
    val receiptCount: Int = 0,
    val currencyCode: String = "PKR",

    val averageReceipt: Double = 0.0,
    val highestReceipt: Double = 0.0,
    val lowestReceipt: Double = 0.0,
    /** Receipts that came in through the camera rather than being typed. */
    val scannedCount: Int = 0,
    val uniqueStores: Int = 0,
    val mostVisitedStore: String? = null,
    val mostVisitedCount: Int = 0,
    val topProduct: String? = null,
    val topProductQty: Double = 0.0,

    val productCount: Int = 0,
    val lowStockCount: Int = 0,
    val averageProductCost: Double = 0.0,
    val inventoryValue: Double = 0.0,
    val memberCount: Int = 0,

    val storageBytes: Long = 0L,
    val lastBackupAt: Long = 0L,
    val accountEmail: String? = null,

    val topStores: List<Pair<String, Double>> = emptyList(),
    val byCategory: List<Pair<String, Double>> = emptyList(),
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)
    private val settingsStore = SettingsStore(application)

    /**
     * Recomputed when the data changes rather than on every frame — walking the
     * database files is cheap, but not free.
     */
    private val storage = MutableStateFlow(0L)

    val stats: StateFlow<DashboardStats> = combine(
        repository.observeReceipts(),
        repository.observeProducts(),
        repository.observeMembers(),
        settingsStore.settings,
        storage,
    ) { receipts, products, members, settings, storageBytes ->
        buildStats(receipts, products, members, settings, storageBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    init {
        viewModelScope.launch { storage.value = measureStorage() }
    }

    private fun buildStats(
        receipts: List<Receipt>,
        products: List<Product>,
        members: List<Member>,
        settings: AppSettings,
        storageBytes: Long,
    ): DashboardStats {
        val live = products.filterNot { it.archived }

        val base = DashboardStats(
            // The app's stable base currency, not the latest receipt's — a
            // single foreign-currency sale should not re-symbol the dashboard.
            currencyCode = settings.defaultCurrency,
            productCount = live.size,
            lowStockCount = live.count { it.lowStock },
            averageProductCost = live
                .filter { it.purchasePrice > 0 }
                .takeIf { it.isNotEmpty() }
                ?.let { costed -> costed.sumOf { it.purchasePrice } / costed.size }
                ?: 0.0,
            inventoryValue = live.sumOf { it.sellValue },
            memberCount = members.size,
            storageBytes = storageBytes,
            lastBackupAt = settings.lastBackupAt,
            accountEmail = settings.accountEmail,
        )

        if (receipts.isEmpty()) return base

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todays = receipts.filter {
            Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today
        }
        val thisMonth = receipts.filter {
            val date = Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
            date.month == today.month && date.year == today.year
        }

        val byStore = receipts.groupBy { it.placeName.ifBlank { UNTITLED } }
        val mostVisited = byStore.maxByOrNull { (_, list) -> list.size }

        // Quantities across every line on every receipt — this is "what do I
        // actually sell", which is a different question from "what earns most".
        val soldQuantities = receipts
            .flatMap { ReceiptItemCodec.decode(it.itemsJson) }
            .groupBy { it.productName.trim() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, lines) -> lines.sumOf { it.qty } }
        val topProduct = soldQuantities.maxByOrNull { it.value }

        val total = receipts.sumOf { it.total }

        return base.copy(
            todayTotal = todays.sumOf { it.total },
            todayCount = todays.size,
            monthTotal = thisMonth.sumOf { it.total },
            allTimeTotal = total,
            receiptCount = receipts.size,
            averageReceipt = total / receipts.size,
            highestReceipt = receipts.maxOf { it.total },
            lowestReceipt = receipts.minOf { it.total },
            scannedCount = receipts.count { !it.sourceImageUri.isNullOrBlank() },
            uniqueStores = byStore.size,
            mostVisitedStore = mostVisited?.key,
            mostVisitedCount = mostVisited?.value?.size ?: 0,
            topProduct = topProduct?.key,
            topProductQty = topProduct?.value ?: 0.0,
            topStores = byStore
                .map { (name, list) -> name to list.sumOf { it.total } }
                .sortedByDescending { it.second }
                .take(5),
            byCategory = receipts.groupBy { it.category }
                .map { (category, list) -> category to list.sumOf { it.total } }
                .sortedByDescending { it.second },
        )
    }

    /** Database plus the rendered PDFs and scans sitting in the cache. */
    private suspend fun measureStorage(): Long = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val db = app.getDatabasePath(CashMemerDatabase.NAME)
        // Room keeps its write-ahead log beside the database; counting only the
        // main file can understate the total by megabytes.
        val dbFiles = listOf(db, File("${db.path}-wal"), File("${db.path}-shm"))
        dbFiles.filter { it.exists() }.sumOf { it.length() } + app.cacheDir.sizeOnDisk()
    }

    private fun File.sizeOnDisk(): Long = when {
        !exists() -> 0L
        isFile -> length()
        else -> listFiles()?.sumOf { it.sizeOnDisk() } ?: 0L
    }

    private companion object {
        const val UNTITLED = "—"
    }
}

@Composable
fun DashboardTab(viewModel: DashboardViewModel = viewModel()) {
    val stats by viewModel.stats.collectAsState()
    val currency = stats.currencyCode
    val noValue = stringResource(R.string.no_value)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.today),
                        value = Format.amountWithCurrency(stats.todayTotal, currency),
                        caption = stringResource(R.string.receipts_count, stats.todayCount),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.this_month),
                        value = Format.amountWithCurrency(stats.monthTotal, currency),
                        caption = stringResource(R.string.total_count, stats.receiptCount),
                        modifier = it,
                    )
                },
            )
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.all_time_sales))
                Text(
                    text = Format.amountWithCurrency(stats.allTimeTotal, currency),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.receipt_figures)) }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.avg_receipt),
                        value = Format.amountWithCurrency(stats.averageReceipt, currency),
                        caption = stringResource(R.string.total_count, stats.receiptCount),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.receipts_scanned),
                        value = stats.scannedCount.toString(),
                        caption = stringResource(R.string.from_camera),
                        modifier = it,
                    )
                },
            )
        }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.highest_receipt),
                        value = Format.amountWithCurrency(stats.highestReceipt, currency),
                        caption = stringResource(R.string.all_time),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.lowest_receipt),
                        value = Format.amountWithCurrency(stats.lowestReceipt, currency),
                        caption = stringResource(R.string.all_time),
                        modifier = it,
                    )
                },
            )
        }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.most_visited_store),
                        value = stats.mostVisitedStore ?: noValue,
                        caption = stringResource(R.string.visits_count, stats.mostVisitedCount),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.unique_stores),
                        value = stats.uniqueStores.toString(),
                        caption = stringResource(R.string.stores_recorded),
                        modifier = it,
                    )
                },
            )
        }

        item { SectionTitle(stringResource(R.string.stock_figures)) }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.top_product),
                        value = stats.topProduct ?: noValue,
                        caption = stringResource(
                            R.string.sold_qty,
                            Format.amount(stats.topProductQty),
                        ),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.total_products),
                        value = stats.productCount.toString(),
                        caption = stringResource(R.string.low_stock_count, stats.lowStockCount),
                        modifier = it,
                    )
                },
            )
        }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.avg_product_cost),
                        value = Format.amountWithCurrency(stats.averageProductCost, currency),
                        caption = stringResource(R.string.purchase_price_label),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.inventory_value),
                        value = Format.amountWithCurrency(stats.inventoryValue, currency),
                        caption = stringResource(R.string.at_selling_price),
                        modifier = it,
                    )
                },
            )
        }

        item { SectionTitle(stringResource(R.string.app_figures)) }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.nav_members),
                        value = stats.memberCount.toString(),
                        caption = stringResource(R.string.saved_customers),
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.storage_used),
                        value = formatBytes(stats.storageBytes),
                        caption = stringResource(R.string.data_and_cache),
                        modifier = it,
                    )
                },
            )
        }

        item {
            TilePair(
                left = {
                    StatTile(
                        label = stringResource(R.string.last_backup_tile),
                        value = if (stats.lastBackupAt == 0L) {
                            stringResource(R.string.never)
                        } else {
                            Format.date(stats.lastBackupAt)
                        },
                        caption = if (stats.lastBackupAt == 0L) {
                            stringResource(R.string.no_backup_yet)
                        } else {
                            Format.time(stats.lastBackupAt)
                        },
                        modifier = it,
                    )
                },
                right = {
                    StatTile(
                        label = stringResource(R.string.cloud_sync),
                        value = stringResource(
                            if (stats.accountEmail != null) R.string.sync_on
                            else R.string.sync_off
                        ),
                        caption = stats.accountEmail ?: stringResource(R.string.not_signed_in),
                        modifier = it,
                    )
                },
            )
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.top_stores))
                if (stats.topStores.isEmpty()) {
                    Text(
                        stringResource(R.string.no_data_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val max = stats.topStores.maxOfOrNull { it.second } ?: 1.0
                stats.topStores.forEach { (name, total) ->
                    BarRow(
                        label = name,
                        value = Format.amountWithCurrency(total, currency),
                        fraction = (total / max).toFloat(),
                    )
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.by_category))
                if (stats.byCategory.isEmpty()) {
                    Text(
                        stringResource(R.string.no_data_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val max = stats.byCategory.maxOfOrNull { it.second } ?: 1.0
                stats.byCategory.forEach { (category, total) ->
                    BarRow(
                        label = category.lowercase().replaceFirstChar { it.uppercase() },
                        value = Format.amountWithCurrency(total, currency),
                        fraction = (total / max).toFloat(),
                    )
                }
            }
        }
    }
}

/** Two equal tiles on a row. Keeps every pair the same width and height. */
@Composable
private fun TilePair(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            // A shop name or a product name can be long; a tile should get
            // shorter text, not a wider column than the tile beside it.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
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

/** KB/MB/GB, not raw bytes — nobody reads a till in bytes. */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
