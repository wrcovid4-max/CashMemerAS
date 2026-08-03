package com.cashmemer.core.network

import com.cashmemer.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One-line business insight for the Weekly AI Summary card.
 *
 * The six figures on that card are computed locally and always shown; this only
 * adds a sentence of interpretation on top. If the key is missing or the call
 * fails the card simply carries no sentence — the numbers never depend on it.
 */
object GeminiInsights {

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    private val json = "application/json".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The fact labels stay English because they are model input, not UI. Only
     * [languageTag] decides what language the sentence comes back in, so the
     * card reads Urdu when the app is in Urdu.
     */
    suspend fun weeklyInsight(
        facts: Map<String, String>,
        languageTag: String = "en",
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = BuildConfig.GEMINI_API_KEY
                require(key.isNotBlank()) { "GEMINI_API_KEY is missing" }

                val prompt = buildString {
                    appendLine(
                        "You are advising a small shopkeeper. Given this week's " +
                            "figures, write ONE sentence of at most 25 words " +
                            "highlighting the single most useful observation. " +
                            "No greeting, no preamble, no markdown."
                    )
                    if (languageTag.startsWith("ur")) {
                        appendLine("Write that sentence in Urdu.")
                    }
                    facts.forEach { (label, value) -> appendLine("$label: $value") }
                }

                val body = JSONObject()
                    .put(
                        "contents", JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", prompt)),
                            )
                        )
                    )
                    .put(
                        "generationConfig", JSONObject()
                            .put("temperature", 0.2)
                            .put("maxOutputTokens", 80)
                    )
                    .toString()

                val request = Request.Builder()
                    .url(ENDPOINT.format(GeminiOcrClient.MODEL))
                    .header("x-goog-api-key", key)
                    .post(body.toRequestBody(json))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    check(response.isSuccessful) { "HTTP ${response.code}" }

                    JSONObject(raw)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                }
            }
        }
}
