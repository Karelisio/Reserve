package com.karelisio.reserve.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    /** Locale of the app language, read at each call so a language change applies at once. */
    private val loc: Locale get() = Lang.locale

    /** 1,729 € */
    fun price(value: Double): String = String.format(loc, "%.3f €", value)

    /** 1,73 € */
    fun price2(value: Double): String = String.format(loc, "%.2f €", value)

    fun euros(value: Double): String =
        NumberFormat.getCurrencyInstance(loc).apply { currency = Currency.getInstance("EUR") }.format(value)

    fun signedEuros(value: Double): String = (if (value >= 0) "+" else "−") + euros(abs(value))

    fun cents(valueEuros: Double): String {
        val c = valueEuros * 100
        return if (abs(c - c.roundToInt()) < 0.05) "${c.roundToInt()} c" else String.format(loc, "%.1f c", c)
    }

    fun distance(km: Double): String = when {
        km < 1 -> "${(km * 1000).roundToInt().coerceAtLeast(10) / 10 * 10} m"
        km < 10 -> String.format(loc, "%.1f km", km)
        else -> "${km.roundToInt()} km"
    }

    fun liters(value: Double): String = String.format(loc, "%.2f L", value)

    fun number(value: Double, decimals: Int = 1): String = String.format(loc, "%.${decimals}f", value)

    /** "il y a 2 h" / "2 h ago" */
    fun age(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - epochMs) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> t("à l'instant", "just now")
            minutes < 60 -> t("il y a $minutes min", "$minutes min ago")
            minutes < 48 * 60 -> t("il y a ${minutes / 60} h", "${minutes / 60} h ago")
            else -> t("il y a ${minutes / (24 * 60)} j", "${minutes / (24 * 60)} d ago")
        }
    }

    private val dateFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy", loc)
    private val dateTimeFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM, HH:mm", loc)
    private val monthFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMMM yyyy", loc)

    fun date(epochMs: Long): String = dateFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    fun dateTime(epochMs: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    fun month(epochMs: Long): String =
        monthFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase(loc) }

    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()
}
