package com.cashmemer.wear

import android.content.Context
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.network.WeatherClient
import com.cashmemer.core.wear.WearRate
import com.cashmemer.core.wear.WearSummary
import com.cashmemer.core.wear.WearSync
import com.cashmemer.core.wear.WearWeather
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the watch payload from local data and puts it on the Data Layer.
 * Called after a receipt is generated and whenever the watch asks for a refresh.
 */
object PhoneWearSyncManager {

    /** Currencies worth a glance on a small screen — keep the list short. */
    private val WATCH_RATE_CODES = listOf("USD", "PKR", "AED", "SAR", "GBP", "EUR")

    suspend fun push(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val summary = buildSummary(context)

            val request = PutDataMapRequest.create(WearSync.PATH_SUMMARY).apply {
                dataMap.putString(WearSync.KEY_PAYLOAD, summary.toJson())
                // Forces a DATA_CHANGED event even when the numbers are unchanged.
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
        }
    }

    private suspend fun buildSummary(context: Context): WearSummary {
        val repository = CashMemerRepository.get(context)
        val receipts = repository.observeReceipts().first()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todays = receipts.filter {
            Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today
        }

        val rates = repository.observeRates().first()
            .filter { it.code in WATCH_RATE_CODES }
            .map { WearRate(code = it.code, rate = it.rate, flagEmoji = it.flagEmoji) }

        return WearSummary(
            todayTotal = todays.sumOf { it.total },
            todayCount = todays.size,
            currencyCode = receipts.firstOrNull()?.currencyCode ?: "PKR",
            lastSyncedAt = System.currentTimeMillis(),
            pendingCount = 0,
            rates = rates,
            weather = weatherForLatestStore(receipts),
        )
    }

    /**
     * Weather for wherever trading last happened. Falls back through the
     * receipt history so a sale without GPS does not blank the watch tile.
     */
    private suspend fun weatherForLatestStore(receipts: List<Receipt>): WearWeather? {
        val located = receipts.firstOrNull { it.hasCoordinates } ?: return null
        val latitude = located.latitude ?: return null
        val longitude = located.longitude ?: return null

        return WeatherClient
            .currentWeather(
                latitude = latitude,
                longitude = longitude,
                place = located.placeName.ifBlank { "Store" },
            )
            .getOrNull()
    }
}
