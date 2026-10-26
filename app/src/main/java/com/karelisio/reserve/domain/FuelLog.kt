package com.karelisio.reserve.domain

import com.karelisio.reserve.data.local.FillUpEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthSpend(val month: YearMonth, val total: Double, val liters: Double)

data class FuelLogStats(
    /** Real consumption measured between full tanks (L/100 km), null if not enough data. */
    val consumption: Double?,
    val distanceKm: Int?,
    val thisMonth: Double,
    val months: List<MonthSpend>,
    /** Savings versus the local average price on the day of each fill-up (€). */
    val savings: Double,
    val totalSpent: Double,
)

object FuelLog {

    fun stats(fillUps: List<FillUpEntity>, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): FuelLogStats {
        val sorted = fillUps.sortedBy { it.date }
        val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val byMonth = sorted.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }
        val months = (5 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            val items = byMonth[month].orEmpty()
            MonthSpend(month, items.sumOf { it.liters * it.pricePerLiter }, items.sumOf { it.liters })
        }
        val (consumption, distance) = consumption(sorted)
        return FuelLogStats(
            consumption = consumption,
            distanceKm = distance,
            thisMonth = months.last().total,
            months = months,
            savings = sorted.sumOf { f -> f.localAveragePrice?.let { (it - f.pricePerLiter) * f.liters } ?: 0.0 },
            totalSpent = sorted.sumOf { it.liters * it.pricePerLiter },
        )
    }

    /**
     * Full-tank method: the fuel added after a full tank, up to the next full tank, was burnt
     * over the kilometers driven between the two.
     */
    fun consumption(sorted: List<FillUpEntity>): Pair<Double?, Int?> {
        var liters = 0.0
        var km = 0
        var lastFull: FillUpEntity? = null
        var pending = 0.0
        for (f in sorted) {
            val odo = f.odometerKm
            if (odo == null) {
                pending += f.liters
                continue
            }
            pending += f.liters
            if (f.fullTank) {
                val previous = lastFull
                if (previous?.odometerKm != null && odo > previous.odometerKm) {
                    liters += pending
                    km += odo - previous.odometerKm
                }
                lastFull = f
                pending = 0.0
            }
        }
        return if (km >= 50 && liters > 0) (liters / km * 100) to km else null to null
    }
}
