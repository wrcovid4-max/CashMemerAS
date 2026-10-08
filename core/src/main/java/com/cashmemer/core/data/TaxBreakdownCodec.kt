package com.cashmemer.core.data

import com.cashmemer.core.model.TaxLine
import org.json.JSONArray
import org.json.JSONObject

/** Stores a receipt's per-tax breakdown as a JSON array in one column. */
object TaxBreakdownCodec {
    fun encode(lines: List<TaxLine>): String =
        JSONArray().apply {
            lines.forEach { put(JSONObject().put("name", it.name).put("percent", it.percent)) }
        }.toString()

    fun decode(raw: String): List<TaxLine> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { TaxLine(it.optString("name"), it.optDouble("percent", 0.0)) }
        }
    }.getOrDefault(emptyList())
}
