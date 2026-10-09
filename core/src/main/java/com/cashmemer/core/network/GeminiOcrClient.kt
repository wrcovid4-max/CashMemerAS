package com.cashmemer.core.network

import com.cashmemer.core.model.TaxLine
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
    val taxes: List<TaxLine> = emptyList(),
    val extraFees: Double = 0.0,
    val total: Double = 0.0,
    val items: List<ReceiptItem> = emptyList(),
)

/**
 * Sends a receipt photo to Gemini and asks for structured JSON back.
 * Response schema is pinned so the model cannot wander into prose.
 */
object GeminiOcrClient {

    /** Swap this for a newer model id as they ship. */
    const val MODEL = "gemini-3.8-flash"

    /** Newest model first; the scanner falls back to the next one if the newest stays busy. */
    private val MODELS = listOf(MODEL, "gemini-3.6-flash")

    /** Busy or overloaded responses worth retrying. */
    private val RETRYABLE = setOf(429, 500, 503)

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
Currency must be a 3-letter ISO code. List every tax or service charge separately in taxes, with its name and percent, and set taxPercent to their combined total. Pakistani receipts often show several taxes. Look for all of these names and their abbreviations: GST, G.S.T, General Sales Tax, Sales Tax, S.Tax, Sindh Sales Tax, SST, Punjab Sales Tax, KPK Sales Tax, Balochistan Sales Tax, Further Tax, Further GST, Further Sales Tax, FED, Federal Excise Duty, Excise Duty, WHT, Withholding Tax, Income Tax, Advance Income Tax, Advance Tax, Service Charge, Service Tax, Stamp Duty, Octroi, Extra Tax, PRA Tax. Name each tax as it is printed. If a tax shows only an amount, work out its percentage from the subtotal. Put other non-tax charges such as delivery, packing or service fees in extraFees as a total amount. Payment type must be one of (choose from the payment evidence: a card brand, "VISA", "Mastercard" or "card ending" means CARD; EasyPaisa, JazzCash or a mobile wallet means MOBILE_WALLET; IBFT, bank or transfer means BANK_TRANSFER; Apple Pay means APPLE_PAY; Google Pay means GOOGLE_PAY; Klarna means KLARNA; PayPak means PAY_PAK; otherwise CASH):
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

            var lastError = "Gemini request failed"
            for (model in MODELS) {
                for (attempt in 1..3) {
                    val request = Request.Builder()
                        .url(ENDPOINT.format(model))
                        .header("x-goog-api-key", key)
                        .post(body.toRequestBody(json))
                        .build()
                    val (code, raw) = client.newCall(request).execute().use { r ->
                        r.code to r.body?.string().orEmpty()
                    }
                    if (code in 200..299) return@runCatching parseResponse(raw)
                    lastError = "Gemini request failed: HTTP $code $raw"
                    // Not a busy error (e.g. model unavailable): move on to the next model.
                    if (code !in RETRYABLE) break
                    Thread.sleep(attempt * 2000L)
                }
            }
            throw IllegalStateException(lastError)
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

        val taxItem = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("name", str())
                    .put("percent", num())
            )
            .put("required", JSONArray().put("name").put("percent"))

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
                    .put("taxes", JSONObject().put("type", "ARRAY").put("items", taxItem))
                    .put("extraFees", num())
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
            extraFees = o.optDouble("extraFees", 0.0),
            taxes = o.optJSONArray("taxes")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { t -> TaxLine(t.optString("name"), t.optDouble("percent", 0.0)) }
                }
            }.orEmpty(),
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
