package com.karelisio.reserve.domain

import com.karelisio.reserve.data.local.ZoneAverageEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

enum class TrendDirection { DOWN, STABLE, UP }

data class Trend(
    /** Null while there is not enough history. */
    val direction: TrendDirection?,
    val days: Int,
    /** Variation per week of the zone average (€/L). */
    val perWeek: Double,
    val today: Double?,
    val weekAverage: Double?,
    /** (epoch ms, average) points for the chart. */
    val points: List<Pair<Long, Double>>,
) {
    val advice: String
        get() = when (direction) {
            null -> "Historique en cours de constitution ($days/${TrendAnalyzer.MIN_DAYS} jours). Reserve relève les prix de votre zone chaque heure."
            TrendDirection.DOWN -> "Prix en baisse (−${Format.cents(abs(perWeek))} par semaine) : si votre réservoir le permet, attendez quelques jours."
            TrendDirection.UP -> "Prix en hausse (+${Format.cents(abs(perWeek))} par semaine) : faites le plein maintenant."
            TrendDirection.STABLE -> "Prix stables : inutile d'attendre, visez simplement le moins cher."
        }

    val label: String
        get() = when (direction) {
            null -> "Pas encore assez de données"
            TrendDirection.DOWN -> "En baisse"
            TrendDirection.UP -> "En hausse"
            TrendDirection.STABLE -> "Stable"
        }
}

/** "Now or wait?": trend of the local average price over the last 7 days. */
object TrendAnalyzer {
    const val WINDOW_DAYS = 7L
    const val MIN_DAYS = 3
    /** Below one cent per week, prices are considered stable. */
    private const val STABLE_PER_WEEK = 0.01

    fun analyze(entries: List<ZoneAverageEntity>, zone: ZoneId = ZoneId.systemDefault()): Trend {
        val today = LocalDate.now(zone).toEpochDay()
        val window = entries.filter { it.day >= today - WINDOW_DAYS }.sortedBy { it.day }
        val points = window.map { LocalDate.ofEpochDay(it.day).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600_000L to it.average }
        val todayAverage = window.lastOrNull { it.day == today }?.average ?: window.lastOrNull()?.average
        val weekAverage = window.takeIf { it.isNotEmpty() }?.map { it.average }?.average()
        if (window.size < MIN_DAYS) {
            return Trend(null, window.size, 0.0, todayAverage, weekAverage, points)
        }
        val slopePerDay = slope(window.map { it.day.toDouble() }, window.map { it.average })
        val perWeek = slopePerDay * 7
        val direction = when {
            perWeek <= -STABLE_PER_WEEK -> TrendDirection.DOWN
            perWeek >= STABLE_PER_WEEK -> TrendDirection.UP
            else -> TrendDirection.STABLE
        }
        return Trend(direction, window.size, perWeek, todayAverage, weekAverage, points)
    }

    /** Least squares slope. */
    fun slope(x: List<Double>, y: List<Double>): Double {
        val mx = x.average()
        val my = y.average()
        val num = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
        val den = x.indices.sumOf { (x[it] - mx) * (x[it] - mx) }
        return if (den == 0.0) 0.0 else num / den
    }
}
