package com.cashmemer.sync

import android.content.Context
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.model.ReceiptItem
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How many records a Firestore batch may carry — the hard limit is 500. */
private const val BATCH_LIMIT = 400

/**
 * Cloud backup and cross-device restore on Firestore.
 *
 * Layout is one document per record under the signed-in user:
 * `users/{uid}/receipts/{id}`, `.../products/{id}`, `.../members/{id}`.
 * Per-record documents rather than one blob keep each write small and stay
 * clear of the 1 MiB document ceiling once a shop builds up history.
 */
object FirebaseSync {

    class NotConfiguredException : Exception(
        "Firebase is not set up — add google-services.json to the app module"
    )

    class NotSignedInException : Exception("Sign in with Google first")

    /** False until a google-services.json is present and Firebase inits. */
    fun isConfigured(context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    fun signedInUid(context: Context): String? =
        if (!isConfigured(context)) null else FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Exchanges the Google ID token from Credential Manager for a Firebase
     * session, so Firestore security rules can key off the user.
     */
    suspend fun authenticate(context: Context, googleIdToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured(context)) throw NotConfiguredException()

                val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val result = FirebaseAuth.getInstance()
                    .signInWithCredential(credential)
                    .await()

                result.user?.uid ?: error("Firebase returned no user")
            }
        }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        if (isConfigured(context)) runCatching { FirebaseAuth.getInstance().signOut() }
        Unit
    }

    /** Uploads everything held locally. Returns how many records were written. */
    suspend fun push(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = requireUid(context)
            val repository = CashMemerRepository.get(context)
            val db = FirebaseFirestore.getInstance()
            val root = db.collection("users").document(uid)

            val receipts = repository.allReceiptsOnce()
            val products = repository.allProductsOnce()
            val members = repository.allMembersOnce()

            writeAll(db, root.collection("receipts"), receipts) { it.id to it.toMap() }
            writeAll(db, root.collection("products"), products) { it.id to it.toMap() }
            writeAll(db, root.collection("members"), members) { it.id to it.toMap() }

            root.set(mapOf("lastSyncAt" to System.currentTimeMillis())).await()

            receipts.size + products.size + members.size
        }
    }

    /**
     * Replaces local data with what is in the cloud. Destructive by design —
     * this is the "new phone, get my shop back" path.
     */
    suspend fun pull(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = requireUid(context)
            val repository = CashMemerRepository.get(context)
            val root = FirebaseFirestore.getInstance().collection("users").document(uid)

            val receipts = root.collection("receipts").get().await().toReceipts()
            val products = root.collection("products").get().await().toProducts()
            val members = root.collection("members").get().await().toMembers()

            check(receipts.isNotEmpty() || products.isNotEmpty() || members.isNotEmpty()) {
                "Nothing found in the cloud for this account"
            }

            repository.replaceAll(receipts, products, members)
            receipts.size + products.size + members.size
        }
    }

    /**
     * One-time import of receipts saved by the *old* Cash Memer app, which
     * stored them under `users/{uid}/cashMemos` in a different shape. Reads
     * them for whichever account is signed in, converts each to the current
     * format, and adds them to local history without touching what is already
     * there. Run it once per old account.
     */
    suspend fun importLegacy(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = requireUid(context)
            val repository = CashMemerRepository.get(context)
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("cashMemos").get().await()

            val receipts = snapshot.documents.mapNotNull { it.toLegacyReceipt() }
            check(receipts.isNotEmpty()) { "No old receipts found under this account" }

            repository.importReceipts(receipts)
            receipts.size
        }
    }

    /** Fire-and-forget upload of a single receipt, used right after Generate. */
    suspend fun pushReceipt(context: Context, receipt: Receipt): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = signedInUid(context) ?: return@runCatching
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("receipts").document(receipt.id.toString())
                    .set(receipt.toMap())
                    .await()
                Unit
            }
        }

    private fun requireUid(context: Context): String {
        if (!isConfigured(context)) throw NotConfiguredException()
        return FirebaseAuth.getInstance().currentUser?.uid ?: throw NotSignedInException()
    }

    private suspend fun <T> writeAll(
        db: FirebaseFirestore,
        collection: com.google.firebase.firestore.CollectionReference,
        items: List<T>,
        entry: (T) -> Pair<Long, Map<String, Any?>>,
    ) {
        items.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { item ->
                val (id, data) = entry(item)
                batch.set(collection.document(id.toString()), data)
            }
            batch.commit().await()
        }
    }

    // ---- Mapping ------------------------------------------------------------
    // Written by hand rather than via Firestore's reflection-based toObject so
    // R8 cannot strip the field names in a release build.

    private fun Receipt.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "placeName" to placeName,
        "locationAddress" to locationAddress,
        "memberId" to memberId,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "customerEmail" to customerEmail,
        "currencyCode" to currencyCode,
        "category" to category,
        "paymentType" to paymentType,
        "subtotal" to subtotal,
        "discount" to discount,
        "taxPercent" to taxPercent,
        "total" to total,
        "cashGiven" to cashGiven,
        "latitude" to latitude,
        "longitude" to longitude,
        "notesPage1" to notesPage1,
        "notesPage2" to notesPage2,
        "issuerName" to issuerName,
        "issuerEmail" to issuerEmail,
        "signatureBase64" to signatureBase64,
        "itemsJson" to itemsJson,
        "annotationsJson" to annotationsJson,
        "sourceImageUri" to sourceImageUri,
        "pinned" to pinned,
        "createdAt" to createdAt,
    )

    private fun Product.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "barcode" to barcode,
        "brand" to brand,
        "category" to category,
        "purchasePrice" to purchasePrice,
        "price" to price,
        "taxPercent" to taxPercent,
        "stock" to stock,
        "unit" to unit,
        "archived" to archived,
        "inPriceList" to inPriceList,
        "updatedAt" to updatedAt,
    )

    private fun Member.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "phone" to phone,
        "email" to email,
        "address" to address,
        "photoUri" to photoUri,
        "createdAt" to createdAt,
    )

    private fun QuerySnapshot.toReceipts(): List<Receipt> = documents.map { doc ->
        Receipt(
            id = doc.long("id"),
            placeName = doc.str("placeName"),
            locationAddress = doc.str("locationAddress"),
            memberId = doc.getLong("memberId"),
            customerName = doc.str("customerName"),
            customerPhone = doc.str("customerPhone"),
            customerEmail = doc.str("customerEmail"),
            currencyCode = doc.str("currencyCode").ifBlank { "PKR" },
            category = doc.str("category").ifBlank { "SHOPPING" },
            paymentType = doc.str("paymentType").ifBlank { "CASH" },
            subtotal = doc.dbl("subtotal"),
            discount = doc.dbl("discount"),
            taxPercent = doc.dbl("taxPercent"),
            total = doc.dbl("total"),
            cashGiven = doc.dbl("cashGiven"),
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            issuerName = doc.str("issuerName"),
            issuerEmail = doc.str("issuerEmail"),
            notesPage1 = doc.str("notesPage1"),
            notesPage2 = doc.str("notesPage2"),
            signatureBase64 = doc.getString("signatureBase64"),
            itemsJson = doc.str("itemsJson").ifBlank { "[]" },
            annotationsJson = doc.str("annotationsJson").ifBlank { "[]" },
            sourceImageUri = doc.getString("sourceImageUri"),
            pinned = doc.getBoolean("pinned") ?: false,
            createdAt = doc.long("createdAt"),
        )
    }

    private fun QuerySnapshot.toProducts(): List<Product> = documents.map { doc ->
        Product(
            id = doc.long("id"),
            name = doc.str("name"),
            barcode = doc.str("barcode"),
            brand = doc.str("brand"),
            category = doc.str("category"),
            purchasePrice = doc.dbl("purchasePrice"),
            price = doc.dbl("price"),
            taxPercent = doc.dbl("taxPercent"),
            stock = doc.dbl("stock"),
            unit = doc.str("unit").ifBlank { "piece" },
            archived = doc.getBoolean("archived") ?: false,
            inPriceList = doc.getBoolean("inPriceList") ?: false,
            updatedAt = doc.long("updatedAt"),
        )
    }

    private fun QuerySnapshot.toMembers(): List<Member> = documents.map { doc ->
        Member(
            id = doc.long("id"),
            name = doc.str("name"),
            phone = doc.str("phone"),
            email = doc.str("email"),
            address = doc.str("address"),
            photoUri = doc.getString("photoUri"),
            createdAt = doc.long("createdAt"),
        )
    }

    // ---- Legacy import -----------------------------------------------------
    // The old app used different field names (title/place/note) and stored line
    // items as {name, quantity, totalPrice}. Map each onto the current Receipt.

    private fun com.google.firebase.firestore.DocumentSnapshot.toLegacyReceipt(): Receipt? =
        runCatching {
            val discountValue = dbl("discountValue")
            val subtotal = dbl("subtotal")
            val discount =
                if (str("discountType").contains("percent", ignoreCase = true)) {
                    subtotal * discountValue / 100.0
                } else {
                    discountValue
                }

            Receipt(
                // Old "title" was the shop/business name shown as the header.
                placeName = str("title").ifBlank { str("place") },
                // Prefer the resolved GPS address; fall back to the free-text place.
                locationAddress = getString("locationAddress").orEmpty()
                    .ifBlank { str("place") },
                customerName = str("customerName"),
                customerPhone = str("customerPhone"),
                customerEmail = str("customerEmail"),
                currencyCode = str("currency").ifBlank { "PKR" },
                category = legacyCategory(str("category")),
                paymentType = legacyPaymentType(str("paymentType")),
                subtotal = subtotal,
                discount = discount,
                taxPercent = dbl("taxPercentage"),
                total = dbl("grandTotal"),
                cashGiven = dbl("cashGiven"),
                latitude = getDouble("latitude"),
                longitude = getDouble("longitude"),
                issuerName = str("accountName"),
                issuerEmail = str("accountEmail"),
                notesPage1 = str("note"),
                notesPage2 = str("notePage2"),
                signatureBase64 = getString("signatureBase64"),
                itemsJson = legacyItemsJson(get("items")),
                createdAt = getLong("timestamp") ?: System.currentTimeMillis(),
            )
        }.getOrNull()

    private fun legacyItemsJson(raw: Any?): String {
        val list = (raw as? List<*>).orEmpty().mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val qty = (m["quantity"] as? Number)?.toDouble() ?: 1.0
            val lineTotal = (m["totalPrice"] as? Number)?.toDouble() ?: 0.0
            ReceiptItem(
                productName = m["name"] as? String ?: "",
                qty = qty,
                // Old app stored the line total; the new one stores unit price.
                unitPrice = if (qty != 0.0) lineTotal / qty else lineTotal,
            )
        }
        return ReceiptItemCodec.encode(list)
    }

    /** Old category labels ("Bills", "Food") onto the current enum names. */
    private fun legacyCategory(raw: String): String = when (raw.trim().lowercase()) {
        "shopping" -> ReceiptCategory.SHOPPING
        "groceries", "grocery" -> ReceiptCategory.GROCERIES
        "food", "food & drink", "food and drink" -> ReceiptCategory.FOOD
        "fuel", "petrol" -> ReceiptCategory.FUEL
        "bills", "utilities", "utility" -> ReceiptCategory.UTILITIES
        "services", "service" -> ReceiptCategory.SERVICES
        "medical", "health" -> ReceiptCategory.MEDICAL
        else -> ReceiptCategory.OTHER
    }.name

    /** Old payment labels ("Cash") onto the current enum names. */
    private fun legacyPaymentType(raw: String): String =
        PaymentType.entries.firstOrNull {
            it.label.equals(raw.trim(), ignoreCase = true) ||
                it.name.equals(raw.trim(), ignoreCase = true)
        }?.name ?: PaymentType.CASH.name

    private fun com.google.firebase.firestore.DocumentSnapshot.str(field: String): String =
        getString(field).orEmpty()

    private fun com.google.firebase.firestore.DocumentSnapshot.dbl(field: String): Double =
        getDouble(field) ?: 0.0

    private fun com.google.firebase.firestore.DocumentSnapshot.long(field: String): Long =
        getLong(field) ?: 0L

    /**
     * Minimal Task-to-coroutine bridge so this file does not pull in
     * kotlinx-coroutines-play-services for one function.
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
