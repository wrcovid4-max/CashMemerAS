package com.cashmemer.core.data

import com.cashmemer.core.model.ReceiptItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Line items are stored as a JSON string on the receipt row. Using org.json keeps
 * the module free of a serialization plugin and makes the backup format readable.
 */
object ReceiptItemCodec {

    fun encode(items: List<ReceiptItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("productName", item.productName)
                    .put("qty", item.qty)
                    .put("unitPrice", item.unitPrice)
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<ReceiptItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ReceiptItem(
                    productName = o.optString("productName"),
                    qty = o.optDouble("qty", 1.0),
                    unitPrice = o.optDouble("unitPrice", 0.0),
                )
            }
        }.getOrDefault(emptyList())
    }
}
