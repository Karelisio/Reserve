package com.karelisio.reserve.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.karelisio.reserve.data.model.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Platform location (no Google Play Services needed). */
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun hasBackgroundPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Current position, or the freshest last known position, or null. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 12_000): LatLon? {
        if (!hasPermission() || manager == null) return null
        val providers = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        }
        val fresh = providers.firstNotNullOfOrNull { provider ->
            withTimeoutOrNull(timeoutMs / providers.size.coerceAtLeast(1)) { currentFrom(provider) }
        }
        return (fresh ?: lastKnown())?.let { LatLon(it.latitude, it.longitude) }
    }

    /** Position Android already knows (no wait), if it is less than [maxAgeMs] old. */
    fun recent(maxAgeMs: Long): LatLon? {
        if (!hasPermission()) return null
        val location = lastKnown() ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        return if (ageMs in 0..maxAgeMs) LatLon(location.latitude, location.longitude) else null
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? = runCatching {
        manager?.getProviders(true).orEmpty()
            .mapNotNull { manager?.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun currentFrom(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            @Suppress("DEPRECATION")
            LocationManagerCompat.getCurrentLocation(manager!!, provider, signal, executor) { location ->
                if (cont.isActive) cont.resume(location)
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
