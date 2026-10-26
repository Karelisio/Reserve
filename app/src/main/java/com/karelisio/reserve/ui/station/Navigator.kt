package com.karelisio.reserve.ui.station

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.Station
import java.util.Locale

/** Starts turn-by-turn navigation to a station in Waze, Google Maps or any map app. */
object Navigator {
    private const val WAZE = "com.waze"
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"

    fun isInstalled(context: Context, app: NavApp): Boolean = when (app) {
        NavApp.WAZE -> isPackageInstalled(context, WAZE)
        NavApp.GOOGLE_MAPS -> isPackageInstalled(context, GOOGLE_MAPS)
        else -> true
    }

    fun navigate(context: Context, station: Station, app: NavApp): Boolean {
        val lat = String.format(Locale.ROOT, "%.6f", station.lat)
        val lon = String.format(Locale.ROOT, "%.6f", station.lon)
        val intent = when (app) {
            NavApp.WAZE -> Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes"))
                .setPackage(WAZE.takeIf { isPackageInstalled(context, it) })
            NavApp.GOOGLE_MAPS -> if (isPackageInstalled(context, GOOGLE_MAPS)) {
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).setPackage(GOOGLE_MAPS)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=driving"))
            }
            NavApp.OTHER, NavApp.ASK -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(station.displayName)})"),
            )
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
