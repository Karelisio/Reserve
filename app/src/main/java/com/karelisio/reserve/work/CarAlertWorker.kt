package com.karelisio.reserve.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.reserve.R
import com.karelisio.reserve.container
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.notifications.Notifier

/** "Moins cher à 2,3 km : 1,72 €" when the phone connects to the car (at most once an hour). */
class CarAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.container
        if (!app.settings.tryClaimCarAlert()) return Result.success()
        val settings = app.settings.current()

        val center = runCatching { app.location.current(timeoutMs = 10_000) }.getOrNull()?.also { app.settings.setLastLocation(it) }
            ?: settings.lastLocation
            ?: return Result.success()
        val result = runCatching { app.stations.searchAround(center, settings.radiusKm) }.getOrNull()
            ?: app.stations.loadCache()
            ?: return Result.success()
        Surfaces.update(applicationContext)

        val best = Pricing.best(
            Pricing.rank(
                stations = result.stations,
                fuel = settings.fuel,
                origin = center,
                vehicle = Vehicle(settings.consumption, settings.fillLiters),
                cards = app.db.loyaltyCards().all(),
                filters = settings.filters,
            ).filter { it.distanceKm <= settings.radiusKm },
        ) ?: return Result.success()

        Notifier.notify(
            applicationContext,
            Notifier.CHANNEL_CAR,
            NOTIFICATION_ID,
            title = "Moins cher à ${Format.distance(best.distanceKm)} : ${Format.price2(best.effectivePrice)}",
            text = "${settings.fuel.label} chez ${best.station.displayName}, ${best.station.city.lowercase().replaceFirstChar { it.titlecase() }} · ${Format.age(best.price.updatedAt)}",
            stationId = best.station.id,
        )
        return Result.success()
    }

    /** Required for expedited work before Android 12 (runs as a short foreground service). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, Notifier.CHANNEL_BACKGROUND)
            .setSmallIcon(R.drawable.ic_stat_reserve)
            .setContentTitle("Recherche du carburant le moins cher…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return ForegroundInfo(FOREGROUND_ID, notification)
    }

    companion object {
        private const val NAME = "car-alert"
        private const val NOTIFICATION_ID = 4242
        private const val FOREGROUND_ID = 4243

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CarAlertWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
