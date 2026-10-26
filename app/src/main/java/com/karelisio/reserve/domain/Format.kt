package com.karelisio.reserve.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    private val fr = Locale.FRANCE

    /** 1,729 € */
    fun price(value: Double): String = String.format(fr, "%.3f €", value)

    /** 1,73 € */
    fun price2(value: Double): String = String.format(fr, "%.2f €", value)

    fun euros(value: Double): String = NumberFormat.getCurrencyInstance(fr).format(value)

    fun signedEuros(value: Double): String = (if (value >= 0) "+" else "−") + euros(abs(value))

    fun cents(valueEuros: Double): String {
        val c = valueEuros * 100
        return if (abs(c - c.roundToInt()) < 0.05) "${c.roundToInt()} c" else String.format(fr, "%.1f c", c)
    }

    fun distance(km: Double): String = when {
        km < 1 -> "${(km * 1000).roundToInt().coerceAtLeast(10) / 10 * 10} m"
        km < 10 -> String.format(fr, "%.1f km", km)
        else -> "${km.roundToInt()} km"
    }

    fun liters(value: Double): String = String.format(fr, "%.2f L", value)

    fun number(value: Double, decimals: Int = 1): String = String.format(fr, "%.${decimals}f", value)

    /** "il y a 2 h" */
    fun age(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - epochMs) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 48 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", fr)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", fr)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", fr)

    fun date(epochMs: Long): String = dateFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    fun dateTime(epochMs: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    fun month(epochMs: Long): String =
        monthFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase(fr) }

    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()
}
