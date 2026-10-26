package com.karelisio.reserve.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.reserve.container
import com.karelisio.reserve.data.local.FavoriteEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.notifications.Notifier
import java.util.concurrent.TimeUnit

/**
 * Runs every hour: refreshes the prices around the last known position (widget, tile,
 * offline cache) and checks the price alerts of the favorites.
 */
class PriceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val settings = app.settings.current()

        // Battery: no GPS fix in the background, only the position other apps already obtained.
        val center = runCatching { app.location.lastKnown() }.getOrNull()
            ?.let { LatLon(it.latitude, it.longitude) }
            ?.also { app.settings.setLastLocation(it) }
            ?: settings.lastLocation
        val cached = app.stations.loadCache()
        val cacheIsFresh = cached != null && center != null &&
            System.currentTimeMillis() - cached.fetchedAt < FRESH_MS &&
            Geo.distanceKm(cached.center, center) < 1.0 && cached.radiusKm >= settings.radiusKm
        val aroundOk = center == null || cacheIsFresh ||
            runCatching { app.stations.searchAround(center, settings.radiusKm) }.isSuccess
        if (!cacheIsFresh) Surfaces.update(applicationContext)

        val alertsOk = !settings.alertsEnabled || runCatching { checkAlerts(settings.fuel, settings.alertThreshold, settings.alertDropCents) }.isSuccess
        return if (aroundOk && alertsOk) Result.success() else Result.retry()
    }

    private suspend fun checkAlerts(fuel: Fuel, threshold: Double?, dropCents: Double) {
        val app = applicationContext.container
        val favorites = app.db.favorites().all()
        if (favorites.isEmpty()) return
        val stations = app.stations.stationsByIds(favorites.map { it.stationId }).associateBy { it.id }
        val outNotified = app.settings.shortageNotified().toMutableSet()
        try {
            checkFavorites(favorites, stations, outNotified, fuel, threshold, dropCents)
        } finally {
            app.settings.setShortageNotified(outNotified)
        }
    }

    private suspend fun checkFavorites(
        favorites: List<FavoriteEntity>,
        stations: Map<Long, Station>,
        outNotified: MutableSet<String>,
        fuel: Fuel,
        threshold: Double?,
        dropCents: Double,
    ) {
        val app = applicationContext.container
        for (favorite in favorites) {
            val station = stations[favorite.stationId] ?: continue
            val shortageKey = "${station.id}:${fuel.name}"
            if (station.isOutOf(fuel)) {
                // Shortage: notify once, until the fuel is back.
                if (outNotified.add(shortageKey)) {
                    Notifier.notify(
                        applicationContext, Notifier.CHANNEL_ALERTS, shortageKey.hashCode(),
                        title = "Rupture chez ${station.displayName}",
                        text = "${fuel.label} indisponible pour le moment. Touchez pour voir les autres carburants et stations.",
                        stationId = station.id,
                    )
                }
                continue
            }
            if (outNotified.remove(shortageKey)) {
                Notifier.notify(
                    applicationContext, Notifier.CHANNEL_ALERTS, shortageKey.hashCode(),
                    title = "${fuel.label} de retour chez ${station.displayName}",
                    text = station.priceOf(fuel)?.let { "Disponible à ${Format.price(it.price)}." } ?: "De nouveau disponible.",
                    stationId = station.id,
                )
            }
            val price = station.priceOf(fuel)?.price ?: continue
            val lastAlert = favorite.lastAlertPrice
            val history = app.db.priceHistory().lastTwo(station.id, fuel)
            // Only a recent change counts as a drop (not one seen before alerts were enabled).
            val recent = history.firstOrNull()?.let { System.currentTimeMillis() - it.updatedAt < 24 * 3600 * 1000L } == true
            val drop = if (history.size == 2 && recent) history[1].price - history[0].price else 0.0

            val newLowUnderThreshold = threshold != null && price <= threshold &&
                (lastAlert == null || price < lastAlert - 0.0005)
            val bigDrop = drop * 100 >= dropCents - 0.001 && (lastAlert == null || kotlin.math.abs(price - lastAlert) > 0.0005)

            when {
                newLowUnderThreshold || bigDrop -> {
                    val text = buildString {
                        append("${fuel.label} à ${Format.price(price)}")
                        if (drop > 0) append(" (−${Format.cents(drop)})")
                        if (newLowUnderThreshold) append(", sous votre seuil de ${Format.price(threshold)}")
                    }
                    Notifier.notify(
                        applicationContext, Notifier.CHANNEL_ALERTS, station.id.hashCode(),
                        title = "Baisse chez ${station.displayName}", text = text, stationId = station.id,
                    )
                    app.db.favorites().updateLastAlert(station.id, price)
                }
                // Back above the threshold: the next crossing will notify again.
                threshold != null && price > threshold && lastAlert != null && drop <= 0 ->
                    app.db.favorites().updateLastAlert(station.id, null)
            }
        }
    }

    companion object {
        private const val NAME = "price-refresh"
        /** The official feed is refreshed every 10 minutes. */
        private const val FRESH_MS = 20 * 60 * 1000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
