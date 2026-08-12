package com.cashmemer.core.data

import java.util.Currency
import java.util.Locale

/**
 * Display names and flags for currency codes. The rates feed returns codes only,
 * so names come from the JDK currency table and flags are derived from the
 * ISO-3166 country prefix of the code (PKR -> PK -> regional-indicator pair).
 */
object CurrencyNames {

    /**
     * Iranian Toman. Not an ISO currency — Iran's formal unit is the Rial, but
     * everyday prices are quoted in Toman, which is worth ten Rial. The rates
     * feed only publishes IRR, so IRT is derived from it.
     */
    const val TOMAN = "IRT"
    const val RIAL = "IRR"

    /** 1 Toman = 10 Rial. */
    const val RIAL_PER_TOMAN = 10.0

    private val overridesEn = mapOf(
        "IRT" to "Iranian Toman",
        "IRR" to "Iranian Rial",
        "PKR" to "Pakistani Rupee",
        "AED" to "United Arab Emirates Dirham",
        "AFN" to "Afghan Afghani",
        "ALL" to "Albanian Lek",
        "AMD" to "Armenian Dram",
        "USD" to "United States Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound Sterling",
        "SAR" to "Saudi Riyal",
        "INR" to "Indian Rupee",
        "CNY" to "Chinese Yuan",
        "XAF" to "Central African CFA Franc",
        "XOF" to "West African CFA Franc",
        "XCD" to "East Caribbean Dollar",
        "XDR" to "Special Drawing Rights",
    )

    /**
     * Urdu names for the invented/special codes only. Every real ISO currency
     * is named by Android's own ICU data in Urdu, so those need no table — this
     * covers the ones ICU can't know about.
     */
    private val overridesUr = mapOf(
        "IRT" to "ایرانی تومان",
        "IRR" to "ایرانی ریال",
    )

    /** Currencies with no single issuing country, so no flag makes sense. */
    private val flagless = setOf("XAF", "XOF", "XCD", "XDR", "XPF", "EUR")

    /**
     * A currency's name in the app's current language.
     *
     * In Urdu, the name comes from Android's ICU table — which knows every ISO
     * currency in Urdu — so the whole rates list translates without a
     * hand-written map. English keeps the tidy custom names above. The special
     * non-ISO codes (Toman) are covered explicitly in both.
     */
    fun of(code: String): String {
        val locale = Locale.getDefault()
        if (locale.language == "ur") {
            overridesUr[code]?.let { return it }
            return icuName(code, locale) ?: overridesEn[code] ?: code
        }
        return overridesEn[code]
            ?: icuName(code, Locale.ENGLISH)
            ?: code
    }

    private fun icuName(code: String, locale: Locale): String? =
        runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()

    fun flagOf(code: String): String {
        if (code == "EUR") return "🇪🇺"
        // IRT is invented, so its two-letter prefix would resolve to nothing.
        if (code == TOMAN) return "🇮🇷"
        if (code.length != 3 || code in flagless) return ""
        val country = code.substring(0, 2).uppercase()
        if (country.any { it !in 'A'..'Z' }) return ""
        val base = 0x1F1E6
        val first = base + (country[0] - 'A')
        val second = base + (country[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    /** Symbol used on the receipt, e.g. PKR -> Rs. */
    fun symbolOf(code: String): String = when (code) {
        "IRT" -> "تومان"
        "IRR" -> "﷼"
        "PKR" -> "Rs"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "INR" -> "₹"
        // Gulf currencies read in Arabic on the memo, per the shopkeeper.
        "AED" -> "درهم"
        "SAR" -> "ريال"
        else -> runCatching { Currency.getInstance(code).symbol }.getOrDefault(code)
    }
}
