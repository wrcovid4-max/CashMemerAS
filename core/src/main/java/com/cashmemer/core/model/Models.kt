package com.cashmemer.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Payment methods shown as the chip grid on the new-receipt form. */
enum class PaymentType(val label: String) {
    CASH("Cash"),
    CARD("Card"),
    BANK_TRANSFER("Bank Transfer"),
    MOBILE_WALLET("Mobile Wallet"),
    APPLE_PAY("Apple Pay"),
    GOOGLE_WALLET("Google Wallet"),
    GOOGLE_PAY("Google Pay"),
    KLARNA("Klarna"),
    PAY_PAK("PayPak");

    companion object {
        fun from(raw: String?): PaymentType =
            entries.firstOrNull { it.name == raw } ?: CASH

        /**
         * Reads whatever the scanner found ("Apple Pay", "VISA ****1234", "EasyPaisa",
         * "IBFT", "Cash") and returns the matching type, or null when it is unclear.
         */
        fun fromScanned(raw: String?): PaymentType? {
            val text = raw?.uppercase()?.filter { it.isLetter() } ?: return null
            if (text.isEmpty()) return null
            entries.firstOrNull { it.name.replace("_", "") == text }?.let { return it }
            return when {
                "APPLE" in text -> APPLE_PAY
                "GOOGLEPAY" in text || "GPAY" in text -> GOOGLE_PAY
                "GOOGLEWALLET" in text -> GOOGLE_WALLET
                "KLARNA" in text -> KLARNA
                "PAYPAK" in text -> PAY_PAK
                listOf("BANK", "TRANSFER", "IBFT", "ACCOUNT").any { it in text } -> BANK_TRANSFER
                listOf("EASYPAISA", "JAZZCASH", "SADAPAY", "NAYAPAY", "WALLET", "MOBILE").any { it in text } ->
                    MOBILE_WALLET
                listOf("CARD", "VISA", "MASTERCARD", "MASTER", "AMEX", "DEBIT", "CREDIT", "MAESTRO", "UNIONPAY")
                    .any { it in text } -> CARD
                "CASH" in text -> CASH
                else -> null
            }
        }
    }
}

/** Receipt categories used for dashboard grouping. */
enum class ReceiptCategory(val label: String) {
    SHOPPING("Shopping"),
    GROCERIES("Groceries"),
    FOOD("Food & Drink"),
    FUEL("Fuel"),
    UTILITIES("Utilities"),
    SERVICES("Services"),
    MEDICAL("Medical"),
    OTHER("Other");

    companion object {
        fun from(raw: String?): ReceiptCategory =
            entries.firstOrNull { it.name == raw } ?: SHOPPING
    }
}

/**
 * A single generated receipt. [itemsJson] holds the line items so the whole
 * receipt stays one row — that keeps backup/restore to a flat JSON array.
 */
@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeName: String = "",
    val locationAddress: String = "",
    val memberId: Long? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val currencyCode: String = "PKR",
    val category: String = ReceiptCategory.SHOPPING.name,
    val paymentType: String = PaymentType.CASH.name,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val total: Double = 0.0,
    /** What the customer handed over, so the memo can show their change. */
    val cashGiven: Double = 0.0,
    val notesPage1: String = "",
    val notesPage2: String = "",
    /** Coordinates behind [locationAddress], printed on the memo. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Who issued the sale — taken from the Google account at generation time. */
    val issuerName: String = "",
    val issuerEmail: String = "",
    /** PNG bytes of the captured signature, base64 encoded. */
    val signatureBase64: String? = null,
    val itemsJson: String = "[]",
    /** JSON list of each tax on the receipt (name and percent). "[]" when shown combined. */
    val taxBreakdownJson: String = "[]",
    /** Marks the shopkeeper drew on the memo in the viewer. See [ReceiptAnnotation]. */
    val annotationsJson: String = "[]",
    /** Local file uri of the scanned source image, when the receipt came from OCR. */
    val sourceImageUri: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Never negative — an underpayment is not change owed. */
    val changeAmount: Double get() = (cashGiven - total).coerceAtLeast(0.0)

    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

/** One purchased line on a receipt. Serialised into [Receipt.itemsJson]. */
data class ReceiptItem(
    val productName: String = "",
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
) {
    val lineTotal: Double get() = qty * unitPrice
}

/** What a shopkeeper can stamp onto a memo in the viewer. */
enum class AnnotationKind { TEXT, CHECK, CROSS, PEN, HIGHLIGHT }

/**
 * One mark placed on a rendered memo page.
 *
 * [x] and [y] are stored as fractions of the page, not pixels, so a mark stays
 * where it was put whatever zoom the viewer happens to be at and whatever
 * height the page turned out to be.
 */
data class ReceiptAnnotation(
    /** 1 for the customer copy, 2 for the shop's record. */
    val page: Int = 1,
    val x: Float = 0f,
    val y: Float = 0f,
    val kind: AnnotationKind = AnnotationKind.CHECK,
    /** Only meaningful for [AnnotationKind.TEXT]. */
    val text: String = "",
    /** PEN and HIGHLIGHT only: the stroke as flattened page fractions [x0, y0, x1, y1, ...]. */
    val points: List<Float> = emptyList(),
    /** PEN and HIGHLIGHT only: ARGB colour of the stroke (highlights carry their own alpha). */
    val colour: Int = 0xFF000000.toInt(),
    /** PEN and HIGHLIGHT only: stroke width as a fraction of the page width. */
    val strokeWidth: Float = 0f,
)

/** Inventory product. Backs both the Inventory and Price List screens. */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val barcode: String = "",
    val brand: String = "",
    val category: String = "",
    /** What the shop paid — drives the margin figure on the dashboard. */
    val purchasePrice: Double = 0.0,
    /** What the customer pays. This is the price dropped into a receipt. */
    val price: Double = 0.0,
    /** Tax rate for this product, as a percentage (e.g. 17 for 17%). */
    val taxPercent: Double = 0.0,
    val stock: Double = 0.0,
    val unit: String = "piece",
    val archived: Boolean = false,
    /** Price-list entries are the quick-pick shortlist, kept out of stock totals. */
    val inPriceList: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val lowStock: Boolean get() = !archived && stock > 0 && stock <= LOW_STOCK_THRESHOLD
    val sellValue: Double get() = stock * price

    companion object {
        const val LOW_STOCK_THRESHOLD = 5.0
    }
}

/** Saved customer, selectable from the receipt form. */
@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One USD-base exchange rate row. */
@Entity(tableName = "currency_rates")
data class CurrencyRate(
    @PrimaryKey val code: String,
    val displayName: String,
    val rate: Double,
    val flagEmoji: String = "",
    val custom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
