package com.cashmemer.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cashmemer.core.wear.WearSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.summaryStore by preferencesDataStore(name = "wear_summary")

/**
 * Caches the last payload the phone pushed. The watch is often out of range, so
 * showing slightly stale numbers beats showing an empty screen.
 */
object WearSummaryStore {

    private val KEY_SUMMARY = stringPreferencesKey("summary_json")

    fun observe(context: Context): Flow<WearSummary> =
        context.summaryStore.data.map { WearSummary.fromJson(it[KEY_SUMMARY]) }

    suspend fun save(context: Context, json: String) {
        context.summaryStore.edit { it[KEY_SUMMARY] = json }
    }
}
