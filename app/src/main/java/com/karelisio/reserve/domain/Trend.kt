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
    /** Cheapest day of the week in the zone, once a few weeks of history are known. */
    val bestDay: BestDay? = null,
) {
    val advice: String
        get() = when (direction) {
            null -> t(
                "Historique en cours de constitution ($days/${TrendAnalyzer.MIN_DAYS} jours). Reserve relève les prix de votre zone chaque heure.",
                "Building up history ($days/${TrendAnalyzer.MIN_DAYS} days). Reserve records the prices in your area every hour.",
            )
            TrendDirection.DOWN -> t(
                "Prix en baisse (−${Format.cents(abs(perWeek))} par semaine) : si votre réservoir le permet, attendez quelques jours.",
                "Prices falling (−${Format.cents(abs(perWeek))} per week): if your tank allows it, wait a few days.",
            )
            TrendDirection.UP -> t(
                "Prix en hausse (+${Format.cents(abs(perWeek))} par semaine) : faites le plein maintenant.",
                "Prices rising (+${Format.cents(abs(perWeek))} per week): fill up now.",
            )
            TrendDirection.STABLE -> t(
                "Prix stables : inutile d'attendre, visez simplement le moins cher.",
                "Prices stable: no need to wait, just go for the cheapest.",
            )
        }

    val label: String
        get() = when (direction) {
            null -> t("Pas encore assez de données", "Not enough data yet")
            TrendDirection.DOWN -> t("En baisse", "Falling")
            TrendDirection.UP -> t("En hausse", "Rising")
            TrendDirection.STABLE -> "Stable"
        }
}

/** [dayOfWeek] 1 = Monday; [gap] how much cheaper than the weekly average (€/L, positive). */
data class BestDay(val dayOfWeek: Int, val gap: Double) {
    val label: String
        get() = java.time.DayOfWeek.of(dayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, Lang.locale)
}

/** "Now or wait?": trend of the local average price over the last 7 days. */
object TrendAnalyzer {
    const val WINDOW_DAYS = 7L
    /** History used for the weekday pattern. */
    const val HISTORY_DAYS = 90L
    const val MIN_DAYS = 3
    /** Below one cent per week, prices are considered stable. */
    private const val STABLE_PER_WEEK = 0.01

    fun analyze(entries: List<ZoneAverageEntity>, zone: ZoneId = ZoneId.systemDefault()): Trend {
        val today = LocalDate.now(zone).toEpochDay()
        val window = entries.filter { it.day >= today - WINDOW_DAYS }.sortedBy { it.day }
        val points = window.map { LocalDate.ofEpochDay(it.day).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600_000L to it.average }
        val todayAverage = window.lastOrNull { it.day == today }?.average ?: window.lastOrNull()?.average
        val weekAverage = window.takeIf { it.isNotEmpty() }?.map { it.average }?.average()
        val bestDay = bestDay(entries)
        if (window.size < MIN_DAYS) {
            return Trend(null, window.size, 0.0, todayAverage, weekAverage, points, bestDay)
        }
        val slopePerDay = slope(window.map { it.day.toDouble() }, window.map { it.average })
        val perWeek = slopePerDay * 7
        val direction = when {
            perWeek <= -STABLE_PER_WEEK -> TrendDirection.DOWN
            perWeek >= STABLE_PER_WEEK -> TrendDirection.UP
            else -> TrendDirection.STABLE
        }
        return Trend(direction, window.size, perWeek, todayAverage, weekAverage, points, bestDay)
    }

    /**
     * Weekday pattern: each day is compared with the average of its own week (so a general
     * rise or fall does not matter), then deviations are averaged per weekday.
     */
    fun bestDay(entries: List<ZoneAverageEntity>): BestDay? {
        if (entries.size < 21) return null
        val byWeek = entries.groupBy { (it.day + 3).floorDiv(7) } // epoch day 0 is a Thursday
        val deviations = mutableMapOf<Int, MutableList<Double>>()
        byWeek.values.filter { it.size >= 4 }.forEach { week ->
            val mean = week.map { it.average }.average()
            week.forEach { entry ->
                val weekday = LocalDate.ofEpochDay(entry.day).dayOfWeek.value
                deviations.getOrPut(weekday) { mutableListOf() } += entry.average - mean
            }
        }
        if (deviations.size < 5 || deviations.values.any { it.size < 2 }) return null
        val (day, values) = deviations.minBy { it.value.average() }
        val gap = -values.average()
        // Below half a cent, there is no real cheaper day.
        return if (gap >= 0.005) BestDay(day, gap) else null
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
