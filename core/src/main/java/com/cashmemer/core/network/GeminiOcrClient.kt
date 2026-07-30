package com.cashmemer.core.network

import android.graphics.Bitmap
import android.util.Base64
import com.cashmemer.core.BuildConfig
import com.cashmemer.core.model.ReceiptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Fields Gemini pulls out of a photographed receipt. */
data class ParsedReceipt(
    val placeName: String = "",
    val locationAddress: String = "",
    val currencyCode: String = "",
    val category: String = "",
    val paymentType: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val total: Double = 0.0,
    val items: List<ReceiptItem> = emptyList(),
)

/**
 * Sends a receipt photo to Gemini and asks for structured JSON back.
 * Response schema is pinned so the model cannot wander into prose.
 */
object GeminiOcrClient {

    /** Swap this for a newer model id as they ship. */
    const val MODEL = "gemini-2.5-flash"

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    private val json = "application/json".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private const val PROMPT = """
You are parsing a retail receipt photograph for a point-of-sale app.
Read every visible line item. Return ONLY the requested JSON.
Use an empty string when a field is not visible. Never invent totals —
if a total is unreadable, sum the line items instead.
Currency must be a 3-letter ISO code. Payment type must be one of:
CASH, CARD, BANK_TRANSFER, MOBILE_WALLET, APPLE_PAY, GOOGLE_WALLET,
GOOGLE_PAY, KLARNA, PAY_PAK.
Category must be one of: SHOPPING, GROCERIES, FOOD, FUEL, UTILITIES,
SERVICES, MEDICAL, OTHER.
"""

    suspend fun parse(bitmap: Bitmap): Result<ParsedReceipt> = withContext(Dispatchers.IO) {
        runCatching {
            val key = BuildConfig.GEMINI_API_KEY
            require(key.isNotBlank()) {
                "GEMINI_API_KEY is missing from local.properties"
            }

            val body = JSONObject()
                .put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray()
                                .put(JSONObject().put("text", PROMPT.trimIndent()))
                                .put(
                                    JSONObject().put(
                                        "inline_data", JSONObject()
                                            .put("mime_type", "image/jpeg")
                                            .put("data", bitmap.toBase64Jpeg())
                                    )
                                )
                        )
                    )
                )
                .put(
                    "generationConfig", JSONObject()
                        .put("temperature", 0)
                        .put("response_mime_type", "application/json")
                        .put("response_schema", responseSchema())
                )
                .toString()

            val request = Request.Builder()
                .url(ENDPOINT.format(MODEL))
                .header("x-goog-api-key", key)
                .post(body.toRequestBody(json))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    "Gemini request failed: HTTP ${response.code} $raw"
                }
                parseResponse(raw)
            }
        }
    }

    private fun responseSchema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        fun num() = JSONObject().put("type", "NUMBER")

        val item = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("productName", str())
                    .put("qty", num())
                    .put("unitPrice", num())
            )
            .put("required", JSONArray().put("productName").put("qty").put("unitPrice"))

        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("placeName", str())
                    .put("locationAddress", str())
                    .put("currencyCode", str())
                    .put("category", str())
                    .put("paymentType", str())
                    .put("subtotal", num())
                    .put("discount", num())
                    .put("taxPercent", num())
                    .put("total", num())
                    .put("items", JSONObject().put("type", "ARRAY").put("items", item))
            )
    }

    private fun parseResponse(raw: String): ParsedReceipt {
        val text = JSONObject(raw)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val o = JSONObject(text)
        val items = o.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                ReceiptItem(
                    productName = item.optString("productName"),
                    qty = item.optDouble("qty", 1.0),
                    unitPrice = item.optDouble("unitPrice", 0.0),
                )
            }
        }.orEmpty()

        return ParsedReceipt(
            placeName = o.optString("placeName"),
            locationAddress = o.optString("locationAddress"),
            currencyCode = o.optString("currencyCode"),
            category = o.optString("category"),
            paymentType = o.optString("paymentType"),
            subtotal = o.optDouble("subtotal", 0.0),
            discount = o.optDouble("discount", 0.0),
            taxPercent = o.optDouble("taxPercent", 0.0),
            total = o.optDouble("total", 0.0),
            items = items,
        )
    }

    private fun Bitmap.toBase64Jpeg(quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
