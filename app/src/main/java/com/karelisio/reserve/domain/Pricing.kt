package com.karelisio.reserve.domain

import com.karelisio.reserve.data.local.DiscountUnit
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.FuelPrice
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.Station

/** A station seen for a given fuel, with the cost of going there. */
data class RankedStation(
    val station: Station,
    val price: FuelPrice,
    /** Price per liter after loyalty discount. */
    val effectivePrice: Double,
    val discount: Double,
    /** Straight-line distance from the reference point (km). */
    val distanceKm: Double,
    /** Extra kilometers driven to reach the station (round trip or detour). */
    val detourKm: Double,
    /** Fuel bill plus the fuel burnt by the detour (€). */
    val totalCost: Double,
    /** Savings compared to the reference station (€), positive = cheaper. */
    val savings: Double,
    val outOfStock: Boolean,
    val stale: Boolean,
)

data class Vehicle(val consumption: Double, val fillLiters: Double)

object Pricing {
    const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

    fun discountFor(station: Station, price: Double, cards: List<LoyaltyCardEntity>): Double =
        cards.filter { Brand.fromKey(it.brandKey)?.matches(station.brand) == true }
            .maxOfOrNull { card ->
                when (card.unit) {
                    DiscountUnit.CENTS_PER_LITER -> card.discount / 100.0
                    DiscountUnit.PERCENT -> price * card.discount / 100.0
                }
            }?.coerceIn(0.0, price) ?: 0.0

    /**
     * Ranks stations around [origin]. The detour is the round trip to the station; savings
     * are measured against the nearest station selling the fuel (what you would do anyway).
     * [detourOverride] lets the route mode provide its own detour per station.
     */
    fun rank(
        stations: List<Station>,
        fuel: Fuel,
        origin: LatLon,
        vehicle: Vehicle,
        cards: List<LoyaltyCardEntity>,
        filters: Filters = Filters(),
        now: Long = System.currentTimeMillis(),
        detourOverride: ((Station) -> Double)? = null,
    ): List<RankedStation> {
        val base = stations.filter(filters::accepts).mapNotNull { station ->
            val price = station.priceOf(fuel) ?: return@mapNotNull null
            val distance = Geo.distanceKm(origin, LatLon(station.lat, station.lon))
            val discount = discountFor(station, price.price, cards)
            val effective = price.price - discount
            val detour = detourOverride?.invoke(station) ?: (2 * distance)
            val cost = effective * vehicle.fillLiters + detour * vehicle.consumption / 100.0 * effective
            RankedStation(
                station = station,
                price = price,
                effectivePrice = effective,
                discount = discount,
                distanceKm = distance,
                detourKm = detour,
                totalCost = cost,
                savings = 0.0,
                outOfStock = station.isOutOf(fuel),
                stale = now - price.updatedAt > STALE_AFTER_MS,
            )
        }
        val reference = base.filterNot { it.outOfStock }.minByOrNull { it.detourKm } ?: return base
        return base.map { it.copy(savings = reference.totalCost - it.totalCost) }
    }

    fun sort(list: List<RankedStation>, mode: SortMode): List<RankedStation> {
        val comparator = when (mode) {
            SortMode.PRICE -> compareBy<RankedStation>({ it.effectivePrice }, { it.distanceKm })
            SortMode.DISTANCE -> compareBy({ it.distanceKm })
            SortMode.REAL_COST -> compareBy({ it.totalCost }, { it.distanceKm })
        }
        // Stations out of stock always go last.
        return list.sortedWith(compareBy<RankedStation> { it.outOfStock }.then(comparator))
    }

    /** Best choice for the widget, tile, alerts: real cost, ignoring shortages. */
    fun best(list: List<RankedStation>): RankedStation? =
        list.filterNot { it.outOfStock }.minWithOrNull(compareBy({ it.totalCost }, { it.distanceKm }))
}
