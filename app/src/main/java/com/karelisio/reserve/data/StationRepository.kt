package com.karelisio.reserve.data

import com.karelisio.reserve.diagnostics.Diagnostics
import android.content.Context
import com.karelisio.reserve.data.local.AppDatabase
import com.karelisio.reserve.data.local.FavoriteEntity
import com.karelisio.reserve.data.local.OsmMatchEntity
import com.karelisio.reserve.data.local.PriceHistoryEntity
import com.karelisio.reserve.data.local.ZoneAverageEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SearchResult
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.data.prefs.PendingFillUp
import com.karelisio.reserve.data.prefs.SettingsRepository
import com.karelisio.reserve.data.remote.FuelApi
import com.karelisio.reserve.data.remote.Http
import com.karelisio.reserve.data.remote.OsmFuelStation
import com.karelisio.reserve.data.remote.OverpassApi
import com.karelisio.reserve.domain.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos

class StationRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val fuelApi: FuelApi,
    private val overpass: OverpassApi,
    private val db: AppDatabase,
) {
    private val cacheFile = File(context.filesDir, "last_result.json")
    private val cacheMutex = Mutex()
    private val _lastResult = MutableStateFlow<SearchResult?>(null)

    /** Last successful search around the user, restored from disk at startup (offline mode). */
    val lastResult: StateFlow<SearchResult?> = _lastResult.asStateFlow()

    suspend fun loadCache(): SearchResult? = cacheMutex.withLock {
        _lastResult.value ?: withContext(Dispatchers.IO) {
            runCatching { Http.json.decodeFromString<SearchResult>(cacheFile.readText()) }.getOrNull()
        }?.also { _lastResult.value = it }
    }

    /** Fetches stations around [center], names them through OSM and stores the result. */
    suspend fun searchAround(center: LatLon, radiusKm: Int): SearchResult {
        val stations = enrich(fuelApi.stationsAround(center, radiusKm.toDouble()))
        val result = SearchResult(center, radiusKm, System.currentTimeMillis(), stations)
        cacheMutex.withLock {
            _lastResult.value = result
            withContext(Dispatchers.IO) {
                runCatching { cacheFile.writeText(Http.json.encodeToString(SearchResult.serializer(), result)) }
            }
        }
        recordZoneAverages(center, stations)
        recordFavorites(stations)
        return result
    }

    suspend fun stationsByIds(ids: Collection<Long>): List<Station> {
        if (ids.isEmpty()) return emptyList()
        val stations = enrich(fuelApi.stationsByIds(ids))
        recordFavorites(stations)
        return stations
    }

    suspend fun stationsNearRoute(points: List<LatLon>, radiusKm: Double): List<Station> =
        enrich(fuelApi.stationsNearPoints(points, radiusKm))

    /** A station from the cache or the favorites, or fetched from the API. */
    suspend fun station(id: Long, refresh: Boolean = false): Station? {
        if (!refresh) cachedStation(id)?.let { return it }
        return runCatching { stationsByIds(listOf(id)).firstOrNull() }.getOrNull() ?: cachedStation(id)
    }

    suspend fun cachedStation(id: Long): Station? =
        (_lastResult.value ?: loadCache())?.stations?.firstOrNull { it.id == id }
            ?: db.favorites().byId(id)?.snapshot?.let { decodeStation(it) }

    fun decodeStation(json: String): Station? =
        runCatching { Http.json.decodeFromString<Station>(json) }.getOrNull()

    fun encodeStation(station: Station): String = Http.json.encodeToString(Station.serializer(), station)

    suspend fun addFavorite(station: Station) {
        db.favorites().upsert(
            FavoriteEntity(
                stationId = station.id,
                label = station.displayName,
                address = station.fullAddress,
                lat = station.lat,
                lon = station.lon,
                addedAt = System.currentTimeMillis(),
                snapshot = encodeStation(station),
            ),
        )
        recordHistory(listOf(station))
    }

    suspend fun removeFavorite(id: Long) = db.favorites().delete(id)

    /** National average price per fuel, refreshed twice a day at most. */
    suspend fun nationalAverages(): Map<Fuel, Double> {
        val (cached, at) = settings.nationalAverages()
        if (cached.isNotEmpty() && System.currentTimeMillis() - at < NATIONAL_TTL) return cached
        return runCatching { fuelApi.nationalAverages() }
            .onFailure { Diagnostics.log("api", "Moyenne nationale indisponible", it) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.also { settings.setNationalAverages(it, System.currentTimeMillis()) }
            ?: cached
    }

    /** After "Y aller": offer to log the fill-up when the user is back in the app. */
    suspend fun rememberTrip(station: Station, fuel: Fuel, price: Double?) {
        settings.setPendingFillUp(
            PendingFillUp(station.id, station.displayName, fuel, price ?: station.priceOf(fuel)?.price ?: 0.0, System.currentTimeMillis()),
        )
    }

    private suspend fun recordFavorites(stations: List<Station>) {
        val favorites = db.favorites().all().associateBy { it.stationId }
        val favoriteStations = stations.filter { it.id in favorites }
        favoriteStations.forEach { db.favorites().updateSnapshot(it.id, it.displayName, encodeStation(it)) }
        recordHistory(favoriteStations)
    }

    private suspend fun recordHistory(stations: List<Station>) {
        val entries = stations.flatMap { station ->
            station.prices.map { PriceHistoryEntity(stationId = station.id, fuel = it.fuel, price = it.price, updatedAt = it.updatedAt) }
        }
        if (entries.isNotEmpty()) db.priceHistory().insert(entries)
    }

    private suspend fun recordZoneAverages(center: LatLon, stations: List<Station>) {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val freshLimit = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        val zone = Geo.zoneKey(center)
        val entries = Fuel.entries.mapNotNull { fuel ->
            val prices = stations.mapNotNull { s -> s.priceOf(fuel)?.takeIf { it.updatedAt > freshLimit }?.price }
            if (prices.size < 2) return@mapNotNull null
            ZoneAverageEntity(today, zone, fuel, prices.average(), prices.min(), prices.size)
        }
        if (entries.isNotEmpty()) db.zoneAverages().upsert(entries)
        db.zoneAverages().prune(today - 90)
        db.priceHistory().prune(System.currentTimeMillis() - 365L * 24 * 3600 * 1000)
    }

    /** Adds brand and name from OpenStreetMap (cached locally, refreshed monthly). */
    private suspend fun enrich(stations: List<Station>): List<Station> {
        if (stations.isEmpty()) return stations
        val now = System.currentTimeMillis()
        val cached = db.osmMatches().byIds(stations.map { it.id }).associateBy { it.stationId }
        val missing = stations.filter { s ->
            val match = cached[s.id]
            match == null || now - match.fetchedAt > if (match.brand == null) NO_MATCH_TTL else MATCH_TTL
        }
        val fresh = if (missing.isEmpty()) emptyMap() else runCatching { matchWithOsm(missing, now) }
            .onFailure { Diagnostics.log("osm", "Noms des stations indisponibles (Overpass)", it) }
            .getOrDefault(emptyMap())
        return stations.map { s ->
            val match = fresh[s.id] ?: cached[s.id]
            if (match == null) s else s.copy(brand = match.brand ?: s.brand, name = match.name ?: s.name)
        }
    }

    private suspend fun matchWithOsm(stations: List<Station>, now: Long): Map<Long, OsmMatchEntity> {
        val osm = mutableListOf<OsmFuelStation>()
        // One bounding box per ~50 km cell keeps Overpass queries small.
        stations.groupBy { (it.lat / 0.45).toInt() to (it.lon / 0.65).toInt() }.values.take(8).forEach { group ->
            val margin = 0.003
            osm += overpass.fuelStations(
                south = group.minOf { it.lat } - margin,
                west = group.minOf { it.lon } - margin,
                north = group.maxOf { it.lat } + margin,
                east = group.maxOf { it.lon } + margin,
            )
        }
        val matches = stations.associate { s ->
            val nearest = osm.minByOrNull { approxDistanceM(s, it.position) }
                ?.takeIf { approxDistanceM(s, it.position) <= MATCH_DISTANCE_M }
            s.id to OsmMatchEntity(
                stationId = s.id,
                brand = nearest?.brand?.trim(),
                name = nearest?.name?.trim(),
                fetchedAt = now,
            )
        }
        db.osmMatches().upsert(matches.values.toList())
        return matches
    }

    private fun approxDistanceM(s: Station, p: LatLon): Double {
        val dx = (s.lon - p.lon) * cos(Math.toRadians(s.lat)) * 111_320
        val dy = (s.lat - p.lat) * 110_540
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val MATCH_DISTANCE_M = 220.0
        private const val MATCH_TTL = 30L * 24 * 3600 * 1000
        private const val NATIONAL_TTL = 12L * 3600 * 1000
        private const val NO_MATCH_TTL = 7L * 24 * 3600 * 1000
    }
}
