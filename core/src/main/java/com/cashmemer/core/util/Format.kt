package com.cashmemer.core.util

import com.cashmemer.core.data.CurrencyNames
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {

    private val money: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private val dateOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

    private val timeOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

    fun amount(value: Double): String = money.format(value)

    fun amountWithCurrency(value: Double, code: String): String =
        "${CurrencyNames.symbolOf(code)} ${money.format(value)}"

    /** Rate rows show four decimals, matching the original rates screen. */
    fun rate(value: Double): String = String.format(Locale.US, "%.4f", value)

    fun timestamp(millis: Long): String = dateTime.format(Instant.ofEpochMilli(millis))

    fun date(millis: Long): String = dateOnly.format(Instant.ofEpochMilli(millis))

    fun time(millis: Long): String = timeOnly.format(Instant.ofEpochMilli(millis))
}
