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
    /** PNG bytes of the captured signature, base64 encoded. */
    val signatureBase64: String? = null,
    val itemsJson: String = "[]",
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
