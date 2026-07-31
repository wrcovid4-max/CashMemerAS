package com.cashmemer.ui.receipts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.ReceiptCategory
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.draftDataStore by preferencesDataStore(name = "receipt_draft")

/**
 * Persists the half-typed receipt so a crash, a battery death or an accidental
 * back-out at the counter does not lose the sale in progress.
 *
 * Only the typed fields are kept — the signature bitmap is deliberately left
 * out, since re-signing is trivial and the base64 would bloat every write.
 */
object DraftStore {

    private val KEY_DRAFT = stringPreferencesKey("draft_json")

    suspend fun save(context: Context, state: ReceiptFormState) {
        val json = JSONObject()
            .put("editingId", state.editingId)
            .put("placeName", state.placeName)
            .put("locationAddress", state.locationAddress)
            .put("customerName", state.customerName)
            .put("customerPhone", state.customerPhone)
            .put("customerEmail", state.customerEmail)
            .put("currencyCode", state.currencyCode)
            .put("category", state.category.name)
            .put("paymentType", state.paymentType.name)
            .put("items", ReceiptItemCodec.encode(state.items))
            .put("discount", state.discount)
            .put("taxPercent", state.taxPercent)
            .put("cashGiven", state.cashGiven)
            .put("notesPage1", state.notesPage1)
            .put("notesPage2", state.notesPage2)
            .apply {
                state.latitude?.let { put("latitude", it) }
                state.longitude?.let { put("longitude", it) }
            }
            .toString()

        context.draftDataStore.edit { it[KEY_DRAFT] = json }
    }

    /** Returns the stored draft, or null when there is nothing worth restoring. */
    suspend fun load(context: Context): ReceiptFormState? {
        val json = context.draftDataStore.data.first()[KEY_DRAFT] ?: return null

        return runCatching {
            val o = JSONObject(json)
            val state = ReceiptFormState(
                editingId = o.optLong("editingId", 0L),
                placeName = o.optString("placeName"),
                locationAddress = o.optString("locationAddress"),
                customerName = o.optString("customerName"),
                customerPhone = o.optString("customerPhone"),
                customerEmail = o.optString("customerEmail"),
                currencyCode = o.optString("currencyCode", "PKR"),
                category = ReceiptCategory.from(o.optString("category")),
                paymentType = PaymentType.from(o.optString("paymentType")),
                items = ReceiptItemCodec.decode(o.optString("items")),
                discount = o.optDouble("discount", 0.0),
                taxPercent = o.optDouble("taxPercent", 0.0),
                cashGiven = o.optDouble("cashGiven", 0.0),
                latitude = if (o.has("latitude")) o.optDouble("latitude") else null,
                longitude = if (o.has("longitude")) o.optDouble("longitude") else null,
                notesPage1 = o.optString("notesPage1"),
                notesPage2 = o.optString("notesPage2"),
            )

            // An empty shell is worse than nothing — it would stomp the
            // defaults restored from settings.
            state.takeIf { it.placeName.isNotBlank() || it.items.isNotEmpty() }
        }.getOrNull()
    }

    suspend fun clear(context: Context) {
        context.draftDataStore.edit { it.remove(KEY_DRAFT) }
    }
}
