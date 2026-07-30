package com.cashmemer.core.network

import com.cashmemer.core.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin client for the ExchangeRate-API v6 "latest" endpoint, USD base. */
object ExchangeRateApi {

    private const val BASE = "https://v6.exchangerate-api.com/v6"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun latestUsdRates(): Result<Map<String, Double>> = runCatching {
        val key = BuildConfig.EXCHANGE_RATE_API_KEY
        require(key.isNotBlank()) {
            "EXCHANGE_RATE_API_KEY is missing from local.properties"
        }

        val request = Request.Builder()
            .url("$BASE/$key/latest/USD")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Rates request failed: HTTP ${response.code}" }

            val json = JSONObject(body)
            check(json.optString("result") == "success") {
                json.optString("error-type", "unknown error")
            }

            val conversion = json.getJSONObject("conversion_rates")
            buildMap {
                conversion.keys().forEach { code ->
                    put(code, conversion.getDouble(code))
                }
            }
        }
    }
}
