package com.karelisio.reserve.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karelisio.reserve.MainActivity
import com.karelisio.reserve.R

object Notifier {
    const val CHANNEL_ALERTS = "price_alerts"
    const val CHANNEL_CAR = "car_alerts"
    const val CHANNEL_BACKGROUND = "background"
    const val CHANNEL_RECAP = "weekly_recap"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ALERTS, "Alertes de prix", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Baisse de prix ou seuil atteint dans une station favorite"
                },
                NotificationChannel(CHANNEL_CAR, "En voiture", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Station la moins chère quand le téléphone se connecte à la voiture"
                },
                NotificationChannel(CHANNEL_BACKGROUND, "Mises à jour en arrière-plan", NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CHANNEL_RECAP, "Récap hebdomadaire", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Le dimanche : dépenses, économies et meilleur jour pour faire le plein"
                },
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun stationPendingIntent(context: Context, stationId: Long?): PendingIntent {
        val intent = MainActivity.stationIntent(context, stationId)
            .setData(Uri.parse("reserve://station/${stationId ?: 0}"))
        return PendingIntent.getActivity(
            context, (stationId ?: 0L).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notify(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        stationId: Long?,
    ) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reserve)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(stationPendingIntent(context, stationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }
}
