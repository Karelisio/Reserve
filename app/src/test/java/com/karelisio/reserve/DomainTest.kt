package com.karelisio.reserve

import com.karelisio.reserve.data.local.DiscountUnit
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.FuelPrice
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.domain.Brand
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private val now = 1_700_000_000_000L

    private fun station(id: Long, lat: Double, lon: Double, price: Double, brand: String? = null) = Station(
        id = id, lat = lat, lon = lon, address = "", city = "Test", postalCode = "", isHighway = false,
        automate24 = false, services = emptyList(), hours = emptyList(),
        prices = listOf(FuelPrice(Fuel.GAZOLE, price, now)), shortages = emptyList(), brand = brand,
    )

    @Test
    fun haversineParisLyon() {
        val d = Geo.distanceKm(LatLon(48.8566, 2.3522), LatLon(45.7640, 4.8357))
        assertEquals(392.0, d, 3.0)
    }

    @Test
    fun distanceToPolyline() {
        val line = listOf(LatLon(48.0, 2.0), LatLon(48.0, 3.0))
        // 0.01° of latitude ≈ 1.11 km north of the segment.
        assertEquals(1.11, Geo.distanceToPolylineKm(LatLon(48.01, 2.5), line), 0.02)
    }

    @Test
    fun resampleKeepsEnds() {
        val line = listOf(LatLon(48.0, 2.0), LatLon(48.0, 3.0))
        val points = Geo.resample(line, 10.0)
        assertEquals(line.first(), points.first())
        assertEquals(line.last(), points.last())
        assertTrue(points.size in 8..10)
    }

    @Test
    fun realCostPrefersNearStationWhenFarIsBarelyCheaper() {
        val origin = LatLon(48.0, 2.0)
        val near = station(1, 48.001, 2.0, 1.800)
        // ~22 km away, 1 cent cheaper: the detour costs more than the savings.
        val far = station(2, 48.2, 2.0, 1.790)
        val ranked = Pricing.rank(listOf(near, far), Fuel.GAZOLE, origin, Vehicle(6.5, 40.0), emptyList(), now = now)
        val byCost = Pricing.sort(ranked, SortMode.REAL_COST)
        assertEquals(1L, byCost.first().station.id)
        assertEquals(2L, Pricing.sort(ranked, SortMode.PRICE).first().station.id)
        assertTrue(byCost.last().savings < 0)
    }

    @Test
    fun loyaltyDiscountApplies() {
        val leclerc = station(1, 48.0, 2.0, 1.80, brand = "E.Leclerc")
        val total = station(2, 48.0, 2.0, 1.80, brand = "TotalEnergies")
        val cards = listOf(LoyaltyCardEntity(brandKey = "LECLERC", discount = 5.0, unit = DiscountUnit.CENTS_PER_LITER))
        assertEquals(0.05, Pricing.discountFor(leclerc, 1.80, cards), 1e-9)
        assertEquals(0.0, Pricing.discountFor(total, 1.80, cards), 1e-9)
    }

    @Test
    fun brandMatching() {
        assertTrue(Brand.TOTAL.matches("TotalEnergies Access"))
        assertTrue(Brand.SYSTEME_U.matches("Super U"))
        assertTrue(Brand.SYSTEME_U.matches("U"))
        assertTrue(Brand.INTERMARCHE.matches("Intermarché"))
        assertFalse(Brand.ESSO.matches("Carrefour"))
        assertFalse(Brand.BP.matches("Esso"))
    }
}

class FuelLogTest {
    private fun fill(day: Long, liters: Double, odo: Int?, full: Boolean = true, price: Double = 1.8, avg: Double? = null) =
        com.karelisio.reserve.data.local.FillUpEntity(
            date = 1_700_000_000_000L + day * 86_400_000L, stationId = null, stationLabel = "S",
            fuel = Fuel.GAZOLE, liters = liters, pricePerLiter = price, odometerKm = odo, fullTank = full, localAveragePrice = avg,
        )

    @Test
    fun consumptionBetweenFullTanks() {
        val log = listOf(fill(0, 40.0, 10_000), fill(7, 20.0, 10_300, full = false), fill(14, 25.0, 10_700))
        val (consumption, km) = com.karelisio.reserve.domain.FuelLog.consumption(log)
        // 45 L burnt over 700 km.
        assertEquals(6.43, consumption!!, 0.01)
        assertEquals(700, km)
    }

    @Test
    fun savingsVersusLocalAverage() {
        val stats = com.karelisio.reserve.domain.FuelLog.stats(listOf(fill(0, 40.0, null, price = 1.75, avg = 1.80)))
        assertEquals(2.0, stats.savings, 1e-9)
    }
}
