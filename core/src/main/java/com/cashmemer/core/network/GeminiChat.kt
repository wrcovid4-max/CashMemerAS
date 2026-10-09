package com.cashmemer.core.network

import com.cashmemer.core.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chat-style calls to Gemini, with the same model fallback and retries as the scanner. */
object GeminiChat {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
    private val MODELS = listOf("gemini-3.8-flash", "gemini-3.6-flash")
    private val RETRYABLE = setOf(429, 500, 503)
    private val json = "application/json".toMediaType()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /** Sends one request body and returns the parsed reply. */
    suspend fun generate(body: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val key = BuildConfig.GEMINI_API_KEY
            require(key.isNotBlank()) { "GEMINI_API_KEY is missing from local.properties" }
            var lastError = "Gemini request failed"
            for (model in MODELS) {
                for (attempt in 1..3) {
                    val request = Request.Builder()
                        .url(ENDPOINT.format(model))
                        .header("x-goog-api-key", key)
                        .post(body.toString().toRequestBody(json))
                        .build()
                    val (code, raw) = client.newCall(request).execute().use { r ->
                        r.code to r.body?.string().orEmpty()
                    }
                    if (code in 200..299) return@runCatching JSONObject(raw)
                    lastError = "Gemini request failed: HTTP $code $raw"
                    if (code !in RETRYABLE) break
                    Thread.sleep(attempt * 2000L)
                }
            }
            throw IllegalStateException(lastError)
        }
    }
}
