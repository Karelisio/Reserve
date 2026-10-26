package com.karelisio.reserve.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.karelisio.reserve.container
import com.karelisio.reserve.data.model.Fuel
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
) {
    val best: RankedStation? get() = Pricing.best(ranked)
}

object Surfaces {

    /** Cheapest stations around the last search, with the user's settings and cards. */
    suspend fun bestNearby(context: Context): BestNearby? = withContext(Dispatchers.Default) { computeBestNearby(context) }

    private suspend fun computeBestNearby(context: Context): BestNearby? {
        val app = context.container
        val result = app.stations.loadCache() ?: return null
        val settings = app.settings.current()
        val ranked = Pricing.rank(
            stations = result.stations,
            fuel = settings.fuel,
            origin = result.center,
            vehicle = Vehicle(settings.consumption, settings.fillLiters),
            cards = app.db.loyaltyCards().all(),
            filters = settings.filters,
        ).filter { it.distanceKm <= settings.radiusKm + 0.05 && !it.outOfStock }
        return BestNearby(settings.fuel, ranked.sortedBy { it.totalCost }, result.fetchedAt)
    }

    /** Refreshes every surface that displays prices outside of the app. */
    suspend fun update(context: Context) {
        runCatching { ReserveWidget().updateAll(context) }
        BestPriceTileService.requestUpdate(context)
    }
}
