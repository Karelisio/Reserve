package com.karelisio.reserve.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.reserve.container
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.domain.TrendAnalyzer
import com.karelisio.reserve.domain.WeeklyRecap
import com.karelisio.reserve.notifications.Notifier
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Every Sunday evening: what the week cost, what was saved, and the best day to fill up. */
class WeeklyRecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val settings = app.settings.current()
        if (!settings.weeklyRecap) return Result.success()
        val center = app.stations.loadCache()?.center ?: settings.lastLocation
        val trend = center?.let {
            TrendAnalyzer.analyze(
                app.db.zoneAverages().get(Geo.zoneKey(it), settings.fuel, LocalDate.now().toEpochDay() - TrendAnalyzer.HISTORY_DAYS),
            )
        }
        val recap = WeeklyRecap.compute(app.db.fillUps().all(), trend)
        if (recap.isEmpty) return Result.success()
        Notifier.notify(
            applicationContext, Notifier.CHANNEL_RECAP, NOTIFICATION_ID,
            title = "Votre semaine carburant", text = recap.text(), stationId = null,
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-recap"
        private const val NOTIFICATION_ID = 7007

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(18, 0))
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            val request = PeriodicWorkRequestBuilder<WeeklyRecapWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
