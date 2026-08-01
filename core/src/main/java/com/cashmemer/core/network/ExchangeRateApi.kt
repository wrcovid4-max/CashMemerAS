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

            // The v6 API returns its real reason in the body even on a 4xx, so
            // read that before falling back to the bare status code.
            val json = runCatching { JSONObject(body) }.getOrNull()
            val apiError = json?.optString("error-type").orEmpty()

            check(response.isSuccessful) {
                if (apiError.isNotBlank()) explain(apiError)
                else "Rates request failed: HTTP ${response.code}"
            }
            checkNotNull(json) { "Rates service returned an unreadable response" }
            check(json.optString("result") == "success") { explain(apiError) }

            val conversion = json.getJSONObject("conversion_rates")
            buildMap {
                conversion.keys().forEach { code ->
                    put(code, conversion.getDouble(code))
                }
            }
        }
    }

    /** Turns the API's terse codes into something a shopkeeper can act on. */
    private fun explain(errorType: String): String = when (errorType) {
        "invalid-key" -> "That exchange-rate key is not valid. Check " +
            "EXCHANGE_RATE_API_KEY in local.properties."
        "inactive-account" -> "Your exchangerate-api.com account is not active — " +
            "confirm your email address on their site."
        "quota-reached" -> "This month's free request quota is used up."
        "unsupported-code" -> "That currency code is not supported."
        "" -> "The rates service rejected the request."
        else -> "Rates service error: $errorType"
    }
}
