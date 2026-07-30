package com.cashmemer.core.wear

import org.json.JSONArray
import org.json.JSONObject

/**
 * Contract shared by the phone and the watch. The phone writes a single
 * DataItem at [PATH_SUMMARY]; the watch renders whatever it last received, so
 * the companion still shows something useful when the phone is out of range.
 */
object WearSync {

    const val PATH_SUMMARY = "/cashmemer/summary"
    const val KEY_PAYLOAD = "payload"

    /** Message path the watch sends to ask the phone for a fresh push. */
    const val PATH_REQUEST_REFRESH = "/cashmemer/refresh"

    const val CAPABILITY_PHONE_APP = "cashmemer_phone_app"
}

/** One currency pair as shown on the watch's rates tile. */
data class WearRate(
    val code: String,
    val rate: Double,
    val flagEmoji: String = "",
)

/** Weather for the store's location, shown on the watch's weather tile. */
data class WearWeather(
    val temperatureC: Double,
    val condition: String,
    val place: String,
)

/**
 * Everything the watch needs in one payload: today's takings, sync state,
 * key rates and store weather.
 */
data class WearSummary(
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val currencyCode: String = "PKR",
    val lastSyncedAt: Long = 0L,
    val pendingCount: Int = 0,
    val rates: List<WearRate> = emptyList(),
    val weather: WearWeather? = null,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val synced: Boolean get() = pendingCount == 0 && lastSyncedAt > 0

    fun toJson(): String {
        val ratesArray = JSONArray()
        rates.forEach { r ->
            ratesArray.put(
                JSONObject()
                    .put("code", r.code)
                    .put("rate", r.rate)
                    .put("flag", r.flagEmoji)
            )
        }

        val root = JSONObject()
            .put("todayTotal", todayTotal)
            .put("todayCount", todayCount)
            .put("currencyCode", currencyCode)
            .put("lastSyncedAt", lastSyncedAt)
            .put("pendingCount", pendingCount)
            .put("rates", ratesArray)
            .put("generatedAt", generatedAt)

        weather?.let {
            root.put(
                "weather", JSONObject()
                    .put("temperatureC", it.temperatureC)
                    .put("condition", it.condition)
                    .put("place", it.place)
            )
        }
        return root.toString()
    }

    companion object {
        fun fromJson(json: String?): WearSummary {
            if (json.isNullOrBlank()) return WearSummary()
            return runCatching {
                val o = JSONObject(json)
                val rates = o.optJSONArray("rates")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val r = arr.getJSONObject(i)
                        WearRate(
                            code = r.optString("code"),
                            rate = r.optDouble("rate", 0.0),
                            flagEmoji = r.optString("flag"),
                        )
                    }
                }.orEmpty()

                val weather = o.optJSONObject("weather")?.let {
                    WearWeather(
                        temperatureC = it.optDouble("temperatureC", 0.0),
                        condition = it.optString("condition"),
                        place = it.optString("place"),
                    )
                }

                WearSummary(
                    todayTotal = o.optDouble("todayTotal", 0.0),
                    todayCount = o.optInt("todayCount", 0),
                    currencyCode = o.optString("currencyCode", "PKR"),
                    lastSyncedAt = o.optLong("lastSyncedAt", 0L),
                    pendingCount = o.optInt("pendingCount", 0),
                    rates = rates,
                    weather = weather,
                    generatedAt = o.optLong("generatedAt", 0L),
                )
            }.getOrDefault(WearSummary())
        }
    }
}
