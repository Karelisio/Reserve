package com.karelisio.reserve.ui.station

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.karelisio.reserve.data.model.LatLon
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

    fun navigate(context: Context, station: Station, app: NavApp): Boolean =
        navigate(context, station.lat, station.lon, station.displayName, app)

    fun navigate(context: Context, latitude: Double, longitude: Double, label: String, app: NavApp): Boolean {
        val lat = String.format(Locale.ROOT, "%.6f", latitude)
        val lon = String.format(Locale.ROOT, "%.6f", longitude)
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
                Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})"),
            )
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Drive to [destination] stopping at the station: Google Maps takes the station as a
     * waypoint; Waze has no waypoints, so it navigates to the station first.
     * Returns false if no app could open the route.
     */
    fun navigateVia(context: Context, station: Station, destination: LatLon, app: NavApp): Boolean {
        if (app == NavApp.WAZE) return navigate(context, station, app)
        fun p(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", p(destination.lat, destination.lon))
            .appendQueryParameter("waypoints", p(station.lat, station.lon))
            .appendQueryParameter("travelmode", "driving")
            .appendQueryParameter("dir_action", "navigate")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(GOOGLE_MAPS.takeIf { isPackageInstalled(context, it) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
