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

    /** Memo header format: 2026-08-01 and 22:28:03. */
    private val isoDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    private val clockFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Filename stamp: 20260801_22_32_29. */
    private val fileStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm_ss").withZone(ZoneId.systemDefault())

    fun amount(value: Double): String = money.format(value)

    fun amountWithCurrency(value: Double, code: String): String =
        "${CurrencyNames.symbolOf(code)} ${money.format(value)}"

    /** Rate rows show four decimals, matching the original rates screen. */
    fun rate(value: Double): String = String.format(Locale.US, "%.4f", value)

    fun timestamp(millis: Long): String = dateTime.format(Instant.ofEpochMilli(millis))

    fun date(millis: Long): String = dateOnly.format(Instant.ofEpochMilli(millis))

    fun time(millis: Long): String = timeOnly.format(Instant.ofEpochMilli(millis))

    fun isoDate(millis: Long): String = isoDateFormat.format(Instant.ofEpochMilli(millis))

    fun clockTime(millis: Long): String = clockFormat.format(Instant.ofEpochMilli(millis))

    fun fileStamp(millis: Long): String = fileStamp.format(Instant.ofEpochMilli(millis))

    /** Six decimals — roughly 10cm, plenty to find a shopfront. */
    fun coordinate(value: Double?): String =
        value?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
}
