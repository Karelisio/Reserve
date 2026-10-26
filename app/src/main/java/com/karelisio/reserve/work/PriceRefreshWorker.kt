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
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.Format
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

        val center = runCatching { app.location.current(timeoutMs = 8_000) }.getOrNull()?.also { app.settings.setLastLocation(it) }
            ?: settings.lastLocation
        val aroundOk = center == null || runCatching { app.stations.searchAround(center, settings.radiusKm) }.isSuccess
        Surfaces.update(applicationContext)

        val alertsOk = !settings.alertsEnabled || runCatching { checkAlerts(settings.fuel, settings.alertThreshold, settings.alertDropCents) }.isSuccess
        return if (aroundOk && alertsOk) Result.success() else Result.retry()
    }

    private suspend fun checkAlerts(fuel: Fuel, threshold: Double?, dropCents: Double) {
        val app = applicationContext.container
        val favorites = app.db.favorites().all()
        if (favorites.isEmpty()) return
        val stations = app.stations.stationsByIds(favorites.map { it.stationId }).associateBy { it.id }
        for (favorite in favorites) {
            val station = stations[favorite.stationId] ?: continue
            if (station.isOutOf(fuel)) continue
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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
