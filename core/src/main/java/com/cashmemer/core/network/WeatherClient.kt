package com.cashmemer.core.network

import com.cashmemer.core.wear.WearWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Store weather for the watch face.
 *
 * Uses Open-Meteo, which needs no API key and no account — one less key for
 * the shopkeeper to manage, and nothing to leak.
 */
object WeatherClient {

    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun currentWeather(
        latitude: Double,
        longitude: Double,
        place: String,
    ): Result<WearWeather> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$ENDPOINT?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code"

            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                check(response.isSuccessful) { "Weather request failed: HTTP ${response.code}" }

                val current = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("current")

                WearWeather(
                    temperatureC = current.getDouble("temperature_2m"),
                    condition = describe(current.optInt("weather_code", -1)),
                    place = place,
                )
            }
        }
    }

    /** WMO weather codes, collapsed to labels that fit a watch screen. */
    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 85..86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Hailstorm"
        else -> "—"
    }
}
