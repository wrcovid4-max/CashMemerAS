package com.cashmemer.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.network.GeminiInsights
import com.cashmemer.core.util.Format
import com.cashmemer.print.ReceiptOutput
import com.cashmemer.print.ReceiptPdfRenderer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** The six figures on the Weekly AI Summary card, plus an optional sentence. */
data class WeeklySummary(
    val totalSpend: Double = 0.0,
    val transactions: Int = 0,
    val averageValue: Double = 0.0,
    val topCustomer: String = "—",
    val totalTax: Double = 0.0,
    val totalDiscount: Double = 0.0,
    val currencyCode: String = "PKR",
    val insight: String? = null,
)

/** Inclusive date bounds, in epoch millis. Zero means unbounded. */
data class DateRange(val from: Long = 0L, val to: Long = 0L)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)
    private val settingsStore = SettingsStore(application)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _range = MutableStateFlow(DateRange())
    val range: StateFlow<DateRange> = _range.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _expanded = MutableStateFlow<Long?>(null)
    val expanded: StateFlow<Long?> = _expanded.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val receipts: StateFlow<List<Receipt>> = combine(_query, _range) { q, r -> q to r }
        .flatMapLatest { (q, r) -> repository.searchReceipts(q, r.from, r.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _summary = MutableStateFlow(WeeklySummary())
    val summary: StateFlow<WeeklySummary> = _summary.asStateFlow()

    init {
        // The card always reflects the last seven days, independent of filters.
        viewModelScope.launch {
            repository.observeReceipts()
                .map { it.lastSevenDays() }
                .collect { week -> _summary.value = week.toSummary() }
        }
    }

    private fun List<Receipt>.lastSevenDays(): List<Receipt> {
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MILLIS
        return filter { it.createdAt >= cutoff }
    }

    private fun List<Receipt>.toSummary(): WeeklySummary {
        if (isEmpty()) return WeeklySummary()

        val spend = sumOf { it.total }
        return WeeklySummary(
            totalSpend = spend,
            transactions = size,
            averageValue = spend / size,
            topCustomer = groupBy { it.customerName.ifBlank { "Walk-in" } }
                .maxByOrNull { (_, list) -> list.sumOf { it.total } }
                ?.key
                ?: "—",
            totalTax = sumOf { r ->
                (r.subtotal - r.discount).coerceAtLeast(0.0) * r.taxPercent / 100.0
            },
            totalDiscount = sumOf { it.discount },
            currencyCode = first().currencyCode,
        )
    }

    /** Asks Gemini for one sentence over the figures already on screen. */
    fun generateInsight() {
        val current = _summary.value
        if (current.transactions == 0) {
            _error.value = "No receipts in the last seven days"
            return
        }

        viewModelScope.launch {
            GeminiInsights.weeklyInsight(
                mapOf(
                    "Total spend" to Format.amountWithCurrency(
                        current.totalSpend,
                        current.currencyCode,
                    ),
                    "Transactions" to current.transactions.toString(),
                    "Average value" to Format.amountWithCurrency(
                        current.averageValue,
                        current.currencyCode,
                    ),
                    "Top customer" to current.topCustomer,
                    "Total tax" to Format.amount(current.totalTax),
                    "Total discount" to Format.amount(current.totalDiscount),
                )
            )
                .onSuccess { text -> _summary.update { it.copy(insight = text) } }
                .onFailure { _error.value = it.message ?: "Could not generate insight" }
        }
    }

    fun setQuery(value: String) = _query.update { value }

    fun setFrom(millis: Long?) = _range.update { it.copy(from = millis ?: 0L) }

    fun setTo(millis: Long?) = _range.update { it.copy(to = millis ?: 0L) }

    fun clearRange() = _range.update { DateRange() }

    fun toggleExpanded(id: Long) = _expanded.update { if (it == id) null else id }

    fun toggleSelected(id: Long) = _selected.update { current ->
        if (id in current) current - id else current + id
    }

    fun selectAll(ids: List<Long>) = _selected.update { current ->
        if (current.size == ids.size) emptySet() else ids.toSet()
    }

    fun deleteSelected() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteReceipts(ids)
            _selected.value = emptySet()
        }
    }

    fun delete(receipt: Receipt) {
        viewModelScope.launch { repository.deleteReceipts(listOf(receipt.id)) }
    }

    /** Copies a receipt as a fresh one — the usual "same order again" case. */
    fun duplicate(receipt: Receipt) {
        viewModelScope.launch {
            repository.saveReceipt(
                receipt.copy(
                    id = 0,
                    pinned = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
            _error.value = "Duplicated receipt #${receipt.id}"
        }
    }

    fun togglePin(receipt: Receipt) {
        viewModelScope.launch { repository.setPinned(receipt.id, !receipt.pinned) }
    }

    /** Renders to PDF and hands the file back for printing or sharing. */
    fun renderPdf(ids: Collection<Long>, onReady: (File) -> Unit) {
        if (ids.isEmpty()) {
            _error.value = "Select at least one receipt"
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            val chosen = receipts.value.filter { it.id in ids }
            val pages = ReceiptOutput.pagesFor(settingsStore.settings.first().massPrint)

            ReceiptPdfRenderer
                .render(app, chosen, pages, ReceiptOutput.outputFile(app, chosen))
                .onSuccess(onReady)
                .onFailure { _error.value = it.message ?: "Could not build the PDF" }
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                val json = repository.exportJson()
                app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("Could not open that file for writing")
            }
                .onSuccess { _error.value = "Backup saved" }
                .onFailure { _error.value = it.message ?: "Backup failed" }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                app.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().decodeToString()
                } ?: error("Could not read that file")
            }
                .mapCatching { json -> repository.importJson(json).getOrThrow() }
                .onSuccess { _error.value = "Restored $it record(s)" }
                .onFailure { _error.value = it.message ?: "Restore failed" }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
