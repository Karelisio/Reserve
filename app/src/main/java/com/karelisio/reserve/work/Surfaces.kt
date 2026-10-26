package com.karelisio.reserve.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.karelisio.reserve.container
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.tile.BestPriceTileService
import com.karelisio.reserve.widget.ReserveWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot shown outside the app: widget, quick settings tile, Android Auto. */
data class BestNearby(
    val fuel: Fuel,
    val ranked: List<RankedStation>,
    val fetchedAt: Long,
    /** A favorite chosen for this widget, shown even when out of stock. */
    val pinned: Boolean = false,
) {
    val best: RankedStation? get() = if (pinned) ranked.firstOrNull() else Pricing.best(ranked)
}

object Surfaces {

    /** Cheapest stations around the last search, with the user's settings and cards. */
    /** [fuel] overrides the fuel chosen in the app (configurable widget). */
    suspend fun bestNearby(context: Context, fuel: Fuel? = null): BestNearby? =
        withContext(Dispatchers.Default) { computeBestNearby(context, fuel) }

    /** One favorite station, for a widget pinned to it. */
    suspend fun favorite(context: Context, stationId: Long, fuel: Fuel? = null): BestNearby? = withContext(Dispatchers.Default) {
        val app = context.container
        val settings = app.settings.current()
        val station = app.stations.cachedStation(stationId) ?: return@withContext null
        val origin = app.stations.lastResult.value?.center ?: settings.lastLocation ?: LatLon(station.lat, station.lon)
        val chosen = fuel ?: settings.fuel
        val ranked = Pricing.rank(
            stations = listOf(station),
            fuel = chosen,
            origin = origin,
            vehicle = Vehicle(settings.consumption, settings.fillLiters),
            cards = app.db.loyaltyCards().all(),
        )
        BestNearby(chosen, ranked, ranked.firstOrNull()?.price?.updatedAt ?: 0L, pinned = true)
    }

    private suspend fun computeBestNearby(context: Context, fuelOverride: Fuel?): BestNearby? {
        val app = context.container
        val result = app.stations.loadCache() ?: return null
        val settings = app.settings.current()
        val fuel = fuelOverride ?: settings.fuel
        val ranked = Pricing.rank(
            stations = result.stations,
            fuel = fuel,
            origin = result.center,
            vehicle = Vehicle(settings.consumption, settings.fillLiters),
            cards = app.db.loyaltyCards().all(),
            filters = settings.filters,
        ).filter { it.distanceKm <= settings.radiusKm + 0.05 && !it.outOfStock }
        return BestNearby(fuel, ranked.sortedBy { it.totalCost }, result.fetchedAt)
    }

    /** Refreshes every surface that displays prices outside of the app. */
    suspend fun update(context: Context) {
        runCatching { ReserveWidget().updateAll(context) }
        BestPriceTileService.requestUpdate(context)
    }
}
