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
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.karelisio.reserve.data.model.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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

    /**
     * Current position, or the freshest last known position, or null. Every provider is asked
     * at once and the first fix wins: the network one often answers in a second, and its
     * accuracy is plenty for a 5–30 km search.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 10_000): LatLon? {
        if (!hasPermission() || manager == null) return null
        val providers = enabledProviders()
        val fresh = if (providers.isEmpty()) null else withTimeoutOrNull(timeoutMs) {
            channelFlow {
                providers.forEach { provider -> launch { currentFrom(provider)?.let { send(it) } } }
            }.firstOrNull()
        }
        return (fresh ?: lastKnown())?.let { LatLon(it.latitude, it.longitude) }
    }

    /**
     * Position updates while collected (screen visible): at most every [intervalMs], after
     * [minDistanceM] of movement. Uses the fused provider when the phone has one.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 20_000, minDistanceM: Float = 250f): Flow<LatLon> = callbackFlow {
        val provider = enabledProviders().firstOrNull()
        if (!hasPermission() || manager == null || provider == null) {
            close()
            return@callbackFlow
        }
        val listener = LocationListenerCompat { trySend(LatLon(it.latitude, it.longitude)) }
        val request = LocationRequestCompat.Builder(intervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()
        runCatching { LocationManagerCompat.requestLocationUpdates(manager, provider, request, executor, listener) }
            .onFailure { close() }
        awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
    }

    private fun enabledProviders(): List<String> = buildList {
        val m = manager ?: return@buildList
        if (android.os.Build.VERSION.SDK_INT >= 31 && m.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        if (m.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
    }

    /** A position within a second when Android has a recent one, else a new fix. */
    suspend fun fast(): LatLon? = recent(RECENT_MS) ?: current()

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

    companion object {
        private const val RECENT_MS = 2 * 60 * 1000L
    }
}
