package com.karelisio.reserve.data.remote

import com.karelisio.reserve.data.model.LatLon
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/** A fuel station mapped in OpenStreetMap (amenity=fuel). */
data class OsmFuelStation(val position: LatLon, val brand: String?, val name: String?)

/** OpenStreetMap data (ODbL) through the Overpass API, used to name the official stations. */
class OverpassApi(private val client: OkHttpClient) {

    suspend fun fuelStations(south: Double, west: Double, north: Double, east: Double): List<OsmFuelStation> {
        val bbox = String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", south, west, north, east)
        val query = "[out:json][timeout:25];nwr[\"amenity\"=\"fuel\"]($bbox);out center tags;"
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                val root = client.getJson(request).obj() ?: return emptyList()
                return root["elements"].arr().orEmpty().mapNotNull { element ->
                    val obj = element.obj() ?: return@mapNotNull null
                    val center = obj["center"].obj()
                    val lat = obj.dbl("lat") ?: center?.dbl("lat") ?: return@mapNotNull null
                    val lon = obj.dbl("lon") ?: center?.dbl("lon") ?: return@mapNotNull null
                    val tags = obj["tags"].obj()
                    OsmFuelStation(
                        position = LatLon(lat, lon),
                        brand = tags?.str("brand") ?: tags?.str("operator")?.takeIf { it.length <= 24 },
                        name = tags?.str("name"),
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Overpass unavailable")
    }

    companion object {
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
    }
}
