package com.karelisio.reserve.data.remote

import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.Station
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Official real-time fuel prices (DGCCRF), Explore API v2.1 of data.economie.gouv.fr.
 * Free, no key, refreshed every 10 minutes. Licence Ouverte 2.0.
 */
class FuelApi(private val client: OkHttpClient) {

    /** Long routes need many requests: stay polite with the public API. */
    private val concurrency = Semaphore(4)

    suspend fun stationsAround(center: LatLon, radiusKm: Double): List<Station> =
        query(withinDistance(center, radiusKm))

    suspend fun stationsByIds(ids: Collection<Long>): List<Station> = coroutineScope {
        ids.distinct().chunked(40).map { chunk ->
            async { query(chunk.joinToString(" OR ") { "id=$it" }) }
        }.awaitAll().flatten()
    }

    /** Stations within [radiusKm] of any of [points] (used for the route corridor). */
    suspend fun stationsNearPoints(points: List<LatLon>, radiusKm: Double): List<Station> = coroutineScope {
        points.chunked(20).map { chunk ->
            async { query(chunk.joinToString(" OR ") { withinDistance(it, radiusKm) }) }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    private fun withinDistance(point: LatLon, radiusKm: Double) = String.format(
        Locale.ROOT, "within_distance(geom, geom'POINT(%.6f %.6f)', %dm)", point.lon, point.lat, (radiusKm * 1000).toInt(),
    )

    private suspend fun query(where: String): List<Station> = coroutineScope {
        val first = page(where, offset = 0)
        val total = minOf(first.total, MAX_RECORDS)
        val rest = (PAGE_SIZE until total step PAGE_SIZE).map { offset ->
            async { page(where, offset).stations }
        }.awaitAll().flatten()
        (first.stations + rest).distinctBy { it.id }
    }

    private class Page(val total: Int, val stations: List<Station>)

    /** Only the fields the app reads: about half the payload. Disabled if the API rejects it. */
    @Volatile
    private var selectFields = true

    private suspend fun page(where: String, offset: Int): Page = concurrency.withPermit {
        val root = try {
            fetch(where, offset, selectFields)
        } catch (e: HttpException) {
            if (e.code != 400 || !selectFields) throw e
            selectFields = false
            fetch(where, offset, select = false)
        }.obj() ?: return@withPermit Page(0, emptyList())
        val results = root["results"].arr().orEmpty().mapNotNull { (it as? JsonObject)?.let(StationParser::parse) }
        Page(root.lng("total_count")?.toInt() ?: results.size, results)
    }

    private suspend fun fetch(where: String, offset: Int, select: Boolean) = client.getJson(
        Request.Builder().url(
            BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("where", where)
                .addQueryParameter("order_by", "id")
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .addQueryParameter("offset", offset.toString())
                .apply { if (select) addQueryParameter("select", FIELDS) }
                .build(),
        ).build(),
    )

    companion object {
        private val FIELDS = (
            listOf(
                "id", "latitude", "longitude", "geom", "cp", "pop", "adresse", "ville", "horaires",
                "horaires_automate_24_24", "services_service", "carburants_rupture_temporaire", "carburants_rupture_definitive",
            ) + listOf("gazole", "sp95", "e85", "gplc", "e10", "sp98").flatMap { listOf("${it}_prix", "${it}_maj") }
            ).joinToString(",")

        const val BASE_URL =
            "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records"
        private const val PAGE_SIZE = 100
        private const val MAX_RECORDS = 1000
    }
}
