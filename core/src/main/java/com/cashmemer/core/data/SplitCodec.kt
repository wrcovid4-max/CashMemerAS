package com.cashmemer.core.data

import org.json.JSONObject

/** Stores how a bill is split between two customers. An empty string means it is not split. */
object SplitCodec {
    fun encode(enabled: Boolean, firstAmount: Double?): String =
        if (!enabled) "" else JSONObject().put("first", firstAmount ?: JSONObject.NULL).toString()

    fun isSplit(json: String): Boolean = json.isNotBlank()

    /** What Customer 1 pays, or null when the bill is split equally. */
    fun firstAmount(json: String): Double? = runCatching {
        val o = JSONObject(json)
        if (o.isNull("first")) null else o.getDouble("first")
    }.getOrNull()
}
