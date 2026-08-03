package com.cashmemer.core.data

import android.content.Context
import com.cashmemer.core.model.CurrencyRate
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptAnnotation
import com.cashmemer.core.network.ExchangeRateApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single entry point for app data. Both the phone and the watch talk to this
 * rather than to Room directly, so the sync payload stays in one place.
 */
class CashMemerRepository private constructor(context: Context) {

    private val db = CashMemerDatabase.get(context)
    private val receipts = db.receiptDao()
    private val products = db.productDao()
    private val members = db.memberDao()
    private val rates = db.currencyRateDao()

    // ---- Receipts -----------------------------------------------------------

    fun observeReceipts(): Flow<List<Receipt>> = receipts.observeAll()

    fun searchReceipts(query: String, from: Long, to: Long): Flow<List<Receipt>> =
        receipts.search(query, from, to)

    suspend fun recentReceipts(limit: Int = 10): List<Receipt> = receipts.recent(limit)

    /** Snapshot reads used by the cloud sync layer. */
    suspend fun allReceiptsOnce(): List<Receipt> = receipts.allOnce()

    suspend fun allProductsOnce(): List<Product> = products.allOnce()

    suspend fun allMembersOnce(): List<Member> = members.allOnce()

    /** Replaces local data wholesale — used when restoring from the cloud. */
    suspend fun replaceAll(
        newReceipts: List<Receipt>,
        newProducts: List<Product>,
        newMembers: List<Member>,
    ) {
        receipts.clear()
        products.clear()
        members.clear()
        newReceipts.forEach { receipts.insert(it) }
        newProducts.forEach { products.upsert(it) }
        newMembers.forEach { members.upsert(it) }
    }

    suspend fun receipt(id: Long): Receipt? = receipts.byId(id)

    suspend fun saveReceipt(receipt: Receipt): Long =
        if (receipt.id == 0L) receipts.insert(receipt)
        else receipt.id.also { receipts.update(receipt) }

    suspend fun deleteReceipts(ids: List<Long>) = receipts.deleteByIds(ids)

    suspend fun setPinned(id: Long, pinned: Boolean) = receipts.setPinned(id, pinned)

    /** Persists the marks made on a memo in the viewer. */
    suspend fun setAnnotations(id: Long, annotations: List<ReceiptAnnotation>) =
        receipts.setAnnotations(id, ReceiptAnnotationCodec.encode(annotations))

    // ---- Products -----------------------------------------------------------

    fun observeProducts(): Flow<List<Product>> = products.observeAll()

    fun observePriceList(): Flow<List<Product>> = products.observePriceList()

    suspend fun productByBarcode(barcode: String): Product? = products.byBarcode(barcode)

    suspend fun saveProduct(product: Product): Long =
        products.upsert(product.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteProduct(product: Product) = products.delete(product)

    suspend fun setProductArchived(id: Long, archived: Boolean) =
        products.setArchived(id, archived)

    // ---- Members ------------------------------------------------------------

    fun observeMembers(): Flow<List<Member>> = members.observeAll()

    suspend fun saveMember(member: Member): Long = members.upsert(member)

    suspend fun deleteMember(member: Member) = members.delete(member)

    // ---- Exchange rates -----------------------------------------------------

    fun observeRates(): Flow<List<CurrencyRate>> = rates.observeAll()

    suspend fun ratesLastUpdated(): Long = rates.lastUpdated() ?: 0L

    suspend fun addCustomRate(code: String, name: String, rate: Double) =
        rates.upsert(CurrencyRate(code.uppercase(), name, rate, custom = true))

    suspend fun deleteRate(rate: CurrencyRate) = rates.delete(rate)

    /** Pulls USD-base rates and writes them through, keeping custom rows intact. */
    suspend fun refreshRates(): Result<Int> = withContext(Dispatchers.IO) {
        ExchangeRateApi.latestUsdRates().map { fetched ->
            // Hand-registered currencies win over the feed — never overwrite them.
            val customCodes = rates.customCodes().toSet()
            val rows = fetched
                .filterNot { it.key in customCodes }
                .map { (code, value) ->
                    CurrencyRate(
                        code = code,
                        displayName = CurrencyNames.of(code),
                        rate = value,
                        flagEmoji = CurrencyNames.flagOf(code),
                    )
                }
            // Toman is not in the feed — it is Rial divided by ten, and it is
            // what Iranian prices are actually quoted in.
            val toman = fetched[CurrencyNames.RIAL]
                ?.takeIf { CurrencyNames.TOMAN !in customCodes }
                ?.let { rial ->
                    CurrencyRate(
                        code = CurrencyNames.TOMAN,
                        displayName = CurrencyNames.of(CurrencyNames.TOMAN),
                        rate = rial / CurrencyNames.RIAL_PER_TOMAN,
                        flagEmoji = CurrencyNames.flagOf(CurrencyNames.TOMAN),
                    )
                }

            val all = rows + listOfNotNull(toman)
            rates.upsertAll(all)
            all.size
        }
    }

    // ---- Backup / restore ---------------------------------------------------

    /** Full offline backup payload — matches the JSON the web app exports. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val receiptArray = JSONArray()
        receipts.allOnce().forEach { r ->
            receiptArray.put(
                JSONObject()
                    .put("id", r.id)
                    .put("placeName", r.placeName)
                    .put("locationAddress", r.locationAddress)
                    .put("customerName", r.customerName)
                    .put("customerPhone", r.customerPhone)
                    .put("customerEmail", r.customerEmail)
                    .put("currencyCode", r.currencyCode)
                    .put("category", r.category)
                    .put("paymentType", r.paymentType)
                    .put("subtotal", r.subtotal)
                    .put("discount", r.discount)
                    .put("taxPercent", r.taxPercent)
                    .put("total", r.total)
                    .put("cashGiven", r.cashGiven)
                    .put("latitude", r.latitude ?: JSONObject.NULL)
                    .put("longitude", r.longitude ?: JSONObject.NULL)
                    .put("issuerName", r.issuerName)
                    .put("issuerEmail", r.issuerEmail)
                    .put("notesPage1", r.notesPage1)
                    .put("notesPage2", r.notesPage2)
                    .put("items", JSONArray(r.itemsJson))
                    .put("annotations", JSONArray(r.annotationsJson))
                    .put("pinned", r.pinned)
                    .put("createdAt", r.createdAt)
            )
        }
        root.put("receipts", receiptArray)

        val productArray = JSONArray()
        products.allOnce().forEach { p ->
            productArray.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("barcode", p.barcode)
                    .put("brand", p.brand)
                    .put("category", p.category)
                    .put("purchasePrice", p.purchasePrice)
                    .put("price", p.price)
                    .put("stock", p.stock)
                    .put("unit", p.unit)
                    .put("archived", p.archived)
                    .put("inPriceList", p.inPriceList)
            )
        }
        root.put("products", productArray)

        val memberArray = JSONArray()
        members.allOnce().forEach { m ->
            memberArray.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("phone", m.phone)
                    .put("email", m.email)
                    .put("address", m.address)
            )
        }
        root.put("members", memberArray)

        root.toString(2)
    }

    /** Replaces local data with a previously exported payload. */
    suspend fun importJson(json: String, replace: Boolean = true): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(json)
                if (replace) {
                    receipts.clear()
                    products.clear()
                    members.clear()
                }

                var count = 0
                root.optJSONArray("receipts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        receipts.insert(
                            Receipt(
                                placeName = o.optString("placeName"),
                                locationAddress = o.optString("locationAddress"),
                                customerName = o.optString("customerName"),
                                customerPhone = o.optString("customerPhone"),
                                customerEmail = o.optString("customerEmail"),
                                currencyCode = o.optString("currencyCode", "PKR"),
                                category = o.optString("category", "SHOPPING"),
                                paymentType = o.optString("paymentType", "CASH"),
                                subtotal = o.optDouble("subtotal", 0.0),
                                discount = o.optDouble("discount", 0.0),
                                taxPercent = o.optDouble("taxPercent", 0.0),
                                total = o.optDouble("total", 0.0),
                                cashGiven = o.optDouble("cashGiven", 0.0),
                                latitude = if (o.isNull("latitude")) null
                                else o.optDouble("latitude"),
                                longitude = if (o.isNull("longitude")) null
                                else o.optDouble("longitude"),
                                issuerName = o.optString("issuerName"),
                                issuerEmail = o.optString("issuerEmail"),
                                notesPage1 = o.optString("notesPage1"),
                                notesPage2 = o.optString("notesPage2"),
                                itemsJson = o.optJSONArray("items")?.toString() ?: "[]",
                                annotationsJson =
                                    o.optJSONArray("annotations")?.toString() ?: "[]",
                                pinned = o.optBoolean("pinned", false),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            )
                        )
                        count++
                    }
                }
                root.optJSONArray("products")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        products.upsert(
                            Product(
                                name = o.optString("name"),
                                barcode = o.optString("barcode"),
                                brand = o.optString("brand"),
                                category = o.optString("category"),
                                purchasePrice = o.optDouble("purchasePrice", 0.0),
                                price = o.optDouble("price", 0.0),
                                stock = o.optDouble("stock", 0.0),
                                unit = o.optString("unit", "piece"),
                                archived = o.optBoolean("archived", false),
                                inPriceList = o.optBoolean("inPriceList", false),
                            )
                        )
                        count++
                    }
                }
                root.optJSONArray("members")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        members.upsert(
                            Member(
                                name = o.optString("name"),
                                phone = o.optString("phone"),
                                email = o.optString("email"),
                                address = o.optString("address"),
                            )
                        )
                        count++
                    }
                }
                count
            }
        }

    companion object {
        @Volatile
        private var instance: CashMemerRepository? = null

        fun get(context: Context): CashMemerRepository =
            instance ?: synchronized(this) {
                instance ?: CashMemerRepository(context).also { instance = it }
            }
    }
}
