package com.cashmemer.core.data

import com.cashmemer.core.model.AnnotationKind
import com.cashmemer.core.model.ReceiptAnnotation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Viewer marks are stored as a JSON string on the receipt row, exactly like the
 * line items — one row still holds a whole receipt, so backup and restore stay
 * a flat array and a marked-up memo survives a reinstall.
 */
object ReceiptAnnotationCodec {

    fun encode(annotations: List<ReceiptAnnotation>): String {
        val array = JSONArray()
        annotations.forEach { mark ->
            array.put(
                JSONObject()
                    .put("page", mark.page)
                    .put("x", mark.x.toDouble())
                    .put("y", mark.y.toDouble())
                    .put("kind", mark.kind.name)
                    .put("text", mark.text)
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<ReceiptAnnotation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ReceiptAnnotation(
                    page = o.optInt("page", 1),
                    x = o.optDouble("x", 0.0).toFloat(),
                    y = o.optDouble("y", 0.0).toFloat(),
                    kind = runCatching {
                        AnnotationKind.valueOf(o.optString("kind"))
                    }.getOrDefault(AnnotationKind.CHECK),
                    text = o.optString("text"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
