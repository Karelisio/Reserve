package com.karelisio.reserve.domain

import com.karelisio.reserve.data.local.FillUpEntity
import kotlin.math.max
import kotlin.math.min

/** Estimated fuel left, from the fuel log habits (no sensor: an estimate, shown as such). */
data class RangeEstimate(
    val remainingKm: Double,
    val remainingLiters: Double,
    /** 0..1 of the tank. */
    val level: Double,
    val kmPerDay: Double,
    /** The last fill-up filled the tank (more reliable estimate). */
    val fromFullTank: Boolean,
    val lastFillUpId: Long,
    val lastFillUpDate: Long,
) {
    val daysLeft: Double get() = if (kmPerDay > 0) remainingKm / kmPerDay else Double.MAX_VALUE
    val onReserve: Boolean get() = remainingKm < RESERVE_KM || level < RESERVE_LEVEL

    companion object {
        const val RESERVE_KM = 80.0
        const val RESERVE_LEVEL = 0.12
    }
}

object RangeEstimator {
    private const val DAY_MS = 24 * 3600 * 1000.0
    private const val MIN_FILL_UPS = 3
    private const val MIN_SPAN_DAYS = 14.0

    /**
     * [fillUps] of one vehicle. Fuel used per day = liters added after the first fill-up,
     * over the time span; consumption = measured one when known, else the declared one.
     */
    fun estimate(
        fillUps: List<FillUpEntity>,
        tankCapacity: Double,
        declaredConsumption: Double,
        now: Long = System.currentTimeMillis(),
    ): RangeEstimate? {
        val sorted = fillUps.sortedBy { it.date }
        if (sorted.size < MIN_FILL_UPS || tankCapacity <= 0) return null
        val spanDays = (sorted.last().date - sorted.first().date) / DAY_MS
        if (spanDays < MIN_SPAN_DAYS) return null
        val litersPerDay = sorted.drop(1).sumOf { it.liters } / spanDays
        val consumption = FuelLog.consumption(sorted).first ?: declaredConsumption
        if (litersPerDay <= 0 || consumption <= 0) return null

        val last = sorted.last()
        // After a partial fill, assume the tank already held a small reserve.
        val levelAfterFill = if (last.fullTank) tankCapacity else min(tankCapacity, last.liters + RangeEstimate.RESERVE_LEVEL * tankCapacity)
        val elapsedDays = max(0.0, (now - last.date) / DAY_MS)
        val liters = max(0.0, levelAfterFill - litersPerDay * elapsedDays)
        return RangeEstimate(
            remainingKm = liters / consumption * 100,
            remainingLiters = liters,
            level = liters / tankCapacity,
            kmPerDay = litersPerDay / consumption * 100,
            fromFullTank = last.fullTank,
            lastFillUpId = last.id,
            lastFillUpDate = last.date,
        )
    }
}
