package com.karelisio.reserve.domain

import com.karelisio.reserve.data.local.FillUpEntity

/** Sunday summary of the week: fill-ups, money spent and saved, and what to expect. */
data class WeeklyRecap(
    val fillUps: Int,
    val spent: Double,
    val liters: Double,
    val saved: Double,
    val trend: Trend?,
) {
    val isEmpty: Boolean get() = fillUps == 0 && trend?.direction == null && trend?.bestDay == null

    fun text(): String = buildList {
        if (fillUps > 0) {
            add(
                t(
                    "$fillUps plein${if (fillUps > 1) "s" else ""} · ${Format.euros(spent)}",
                    "$fillUps fill-up${if (fillUps > 1) "s" else ""} · ${Format.euros(spent)}",
                ) + if (saved > 0.05) t(" · ${Format.euros(saved)} économisés", " · ${Format.euros(saved)} saved") else "",
            )
        } else {
            add(t("Aucun plein noté cette semaine", "No fill-up logged this week"))
        }
        trend?.bestDay?.let {
            add(t("Meilleur jour : le ${it.label} (−${Format.cents(it.gap)})", "Best day: ${it.label} (−${Format.cents(it.gap)})"))
        }
        when (trend?.direction) {
            TrendDirection.DOWN -> add(t("Prix en baisse : attendez si votre réservoir le permet", "Prices falling: wait if your tank allows it"))
            TrendDirection.UP -> add(t("Prix en hausse : faites le plein tôt dans la semaine", "Prices rising: fill up early in the week"))
            TrendDirection.STABLE -> add(t("Prix stables", "Prices stable"))
            null -> Unit
        }
    }.joinToString(". ") + "."

    companion object {
        private const val WEEK_MS = 7L * 24 * 3600 * 1000

        fun compute(fillUps: List<FillUpEntity>, trend: Trend?, now: Long = System.currentTimeMillis()): WeeklyRecap {
            val week = fillUps.filter { it.date > now - WEEK_MS && it.date <= now }
            return WeeklyRecap(
                fillUps = week.size,
                spent = week.sumOf { it.liters * it.pricePerLiter },
                liters = week.sumOf { it.liters },
                saved = week.sumOf { f -> f.localAveragePrice?.let { (it - f.pricePerLiter) * f.liters } ?: 0.0 },
                trend = trend,
            )
        }
    }
}
