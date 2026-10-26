package com.karelisio.reserve.data.remote

import com.karelisio.reserve.data.model.LatLon
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

data class AddressSuggestion(val label: String, val context: String, val position: LatLon)

/** French national address database (Base Adresse Nationale), free and keyless. */
class AddressApi(private val client: OkHttpClient) {

    suspend fun search(query: String, near: LatLon?): List<AddressSuggestion> {
        if (query.trim().length < 3) return emptyList()
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                val url = endpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim())
                    .addQueryParameter("limit", "6")
                    .addQueryParameter("autocomplete", "1")
                    .apply {
                        if (near != null) {
                            addQueryParameter("lat", String.format(Locale.ROOT, "%.5f", near.lat))
                            addQueryParameter("lon", String.format(Locale.ROOT, "%.5f", near.lon))
                        }
                    }
                    .build()
                val root = client.getJson(Request.Builder().url(url).build()).obj() ?: return emptyList()
                return root["features"].arr().orEmpty().mapNotNull { feature ->
                    val obj = feature.obj() ?: return@mapNotNull null
                    val props = obj["properties"].obj() ?: return@mapNotNull null
                    val coords = obj["geometry"].obj()?.get("coordinates").arr() ?: return@mapNotNull null
                    val lon = coords.getOrNull(0)?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                    val lat = coords.getOrNull(1)?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                    AddressSuggestion(
                        label = props.str("label") ?: return@mapNotNull null,
                        context = props.str("context").orEmpty(),
                        position = LatLon(lat, lon),
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Address search unavailable")
    }

    companion object {
        private val ENDPOINTS = listOf(
            "https://api-adresse.data.gouv.fr/search/",
            // Same service, hosted by the Géoplateforme (IGN).
            "https://data.geopf.fr/geocodage/search",
        )
    }
}

data class Route(val points: List<LatLon>, val distanceKm: Double, val durationMin: Double)

/** Car routing with the public OSRM server (OpenStreetMap data). */
class OsrmApi(private val client: OkHttpClient) {

    suspend fun route(from: LatLon, to: LatLon): Route {
        val coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "https://router.project-osrm.org/route/v1/driving/$coords".toHttpUrl().newBuilder()
            .addQueryParameter("overview", "full")
            .addQueryParameter("geometries", "geojson")
            .build()
        val root = client.getJson(Request.Builder().url(url).build()).obj()
            ?: throw IllegalStateException("Empty route")
        if (root.str("code") != "Ok") throw IllegalStateException(root.str("message") ?: "Itinéraire introuvable")
        val route = root["routes"].arr()?.firstOrNull().obj() ?: throw IllegalStateException("Itinéraire introuvable")
        val points = route["geometry"].obj()?.get("coordinates").arr().orEmpty().mapNotNull { c ->
            val pair = c.arr() ?: return@mapNotNull null
            val lon = pair.getOrNull(0)?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = pair.getOrNull(1)?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            LatLon(lat, lon)
        }
        return Route(points, (route.dbl("distance") ?: 0.0) / 1000, (route.dbl("duration") ?: 0.0) / 60)
    }
}
