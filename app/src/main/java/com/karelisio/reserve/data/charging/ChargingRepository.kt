package com.karelisio.reserve.data.charging

import com.karelisio.reserve.diagnostics.Diagnostics
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.remote.arr
import com.karelisio.reserve.data.remote.dbl
import com.karelisio.reserve.data.remote.getJson
import com.karelisio.reserve.data.remote.obj
import com.karelisio.reserve.data.remote.str
import com.karelisio.reserve.domain.Geo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import kotlin.math.cos

enum class Plug(val label: String) { TYPE2("Type 2"), CCS("Combo CCS"), CHADEMO("CHAdeMO"), DOMESTIC("Domestique") }

enum class ChargingSource(val mention: String) {
    IRVE("Source : fichier consolidé IRVE, data.gouv.fr (Licence Ouverte 2.0)"),
    OSM("Source : © contributeurs OpenStreetMap (ODbL)"),
}

data class ChargingStation(
    val id: String,
    val name: String,
    val operator: String?,
    val address: String?,
    val position: LatLon,
    val points: Int?,
    val maxPowerKw: Double?,
    val plugs: Set<Plug>,
    val free: Boolean?,
    val pricing: String?,
    val open24: Boolean,
)

data class ChargingResult(val stations: List<ChargingStation>, val source: ChargingSource)

/**
 * Electric charging stations around a point: the national IRVE file (data.gouv.fr tabular
 * API) when it answers, otherwise OpenStreetMap.
 */
class ChargingRepository(private val client: OkHttpClient) {

    @Volatile
    private var irveResource: String? = null

    suspend fun around(center: LatLon, radiusKm: Double): ChargingResult {
        val irve = runCatching { irve(center, radiusKm) }
            .onFailure { Diagnostics.log("bornes", "IRVE indisponible, repli OpenStreetMap", it) }
            .getOrNull().orEmpty()
        val (stations, source) = if (irve.isNotEmpty()) irve to ChargingSource.IRVE else osm(center, radiusKm) to ChargingSource.OSM
        return ChargingResult(
            stations.filter { Geo.distanceKm(center, it.position) <= radiusKm }
                .sortedBy { Geo.distanceKm(center, it.position) },
            source,
        )
    }

    // --- IRVE (one row per charging point, grouped by station) ---

    private suspend fun irve(center: LatLon, radiusKm: Double): List<ChargingStation> {
        val resource = irveResource ?: findIrveResource()?.also { irveResource = it } ?: return emptyList()
        val (dLat, dLon) = degrees(center, radiusKm)
        val rows = mutableListOf<JsonObject>()
        var url: String? = "$TABULAR/$resource/data/".toHttpUrl().newBuilder()
            .addQueryParameter("consolidated_latitude__greater", fmt(center.lat - dLat))
            .addQueryParameter("consolidated_latitude__less", fmt(center.lat + dLat))
            .addQueryParameter("consolidated_longitude__greater", fmt(center.lon - dLon))
            .addQueryParameter("consolidated_longitude__less", fmt(center.lon + dLon))
            .addQueryParameter("page_size", "50")
            .build().toString()
        var pages = 0
        while (url != null && pages < 20) {
            val root = client.getJson(Request.Builder().url(url).build()).obj() ?: break
            root["data"].arr().orEmpty().mapNotNullTo(rows) { it.obj() }
            url = root["links"].obj()?.str("next")
            pages++
        }
        return rows.groupBy { it.str("id_station_itinerance") ?: it.str("id_station_local") ?: it.str("nom_station").orEmpty() }
            .mapNotNull { (id, points) -> irveStation(id, points) }
    }

    internal fun irveStation(id: String, points: List<JsonObject>): ChargingStation? {
        val first = points.first()
        val lat = first.dbl("consolidated_latitude") ?: return null
        val lon = first.dbl("consolidated_longitude") ?: return null
        fun flag(key: String) = points.any { it.bool(key) == true }
        val plugs = buildSet {
            if (flag("prise_type_2")) add(Plug.TYPE2)
            if (flag("prise_type_combo_ccs")) add(Plug.CCS)
            if (flag("prise_type_chademo")) add(Plug.CHADEMO)
            if (flag("prise_type_ef")) add(Plug.DOMESTIC)
        }
        val power = points.mapNotNull { it.dbl("puissance_nominale") }
            .map { if (it > 1000) it / 1000 else it } // some operators publish watts
            .maxOrNull()
        val address = listOfNotNull(
            first.str("adresse_station"),
            listOfNotNull(first.str("consolidated_code_postal"), first.str("consolidated_commune")).joinToString(" ").takeIf { it.isNotBlank() },
        ).joinToString(", ").takeIf { it.isNotBlank() }
        return ChargingStation(
            id = "irve:$id",
            name = first.str("nom_station") ?: first.str("nom_enseigne") ?: "Borne de recharge",
            operator = first.str("nom_operateur") ?: first.str("nom_enseigne"),
            address = address,
            position = LatLon(lat, lon),
            points = first.dbl("nbre_pdc")?.toInt() ?: points.size,
            maxPowerKw = power,
            plugs = plugs,
            free = first.bool("gratuit"),
            pricing = first.str("tarification"),
            open24 = first.str("horaires")?.contains("24/7") == true,
        )
    }

    /** The consolidated IRVE CSV changes id with each release: look it up in the dataset. */
    private suspend fun findIrveResource(): String? {
        val request = Request.Builder().url("https://www.data.gouv.fr/api/1/datasets/$IRVE_DATASET/").build()
        val resources = client.getJson(request).obj()?.get("resources").arr().orEmpty().mapNotNull { it.obj() }
        return resources.firstOrNull { r ->
            r.str("format")?.equals("csv", ignoreCase = true) == true &&
                (r.str("title")?.contains("consolid", ignoreCase = true) == true ||
                    r["schema"].obj()?.str("name")?.contains("irve", ignoreCase = true) == true)
        }?.str("id")
    }

    // --- OpenStreetMap ---

    private suspend fun osm(center: LatLon, radiusKm: Double): List<ChargingStation> {
        val (dLat, dLon) = degrees(center, radiusKm)
        val bbox = listOf(center.lat - dLat, center.lon - dLon, center.lat + dLat, center.lon + dLon).joinToString(",") { fmt(it) }
        val query = "[out:json][timeout:25];nwr[\"amenity\"=\"charging_station\"]($bbox);out center tags;"
        var lastError: Exception? = null
        for (endpoint in OVERPASS) {
            try {
                val request = Request.Builder().url(endpoint).post(FormBody.Builder().add("data", query).build()).build()
                val elements = client.getJson(request).obj()?.get("elements").arr().orEmpty()
                return elements.mapNotNull { element -> osmStation(element.obj() ?: return@mapNotNull null) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Overpass unavailable")
    }

    internal fun osmStation(element: JsonObject): ChargingStation? {
        val center = element["center"].obj()
        val lat = element.dbl("lat") ?: center?.dbl("lat") ?: return null
        val lon = element.dbl("lon") ?: center?.dbl("lon") ?: return null
        val tags = element["tags"].obj() ?: return null
        fun socket(name: String) = tags.str("socket:$name")?.let { it != "no" && it != "0" } == true
        val plugs = buildSet {
            if (socket("type2") || socket("type2_cable")) add(Plug.TYPE2)
            if (socket("type2_combo")) add(Plug.CCS)
            if (socket("chademo")) add(Plug.CHADEMO)
            if (socket("typee") || socket("schuko")) add(Plug.DOMESTIC)
        }
        val power = tags.entries.filter { it.key.startsWith("socket:") && it.key.endsWith(":output") }
            .mapNotNull { (it.value as? JsonPrimitive)?.content?.let(::parseKw) }
            .maxOrNull()
        val address = listOfNotNull(
            listOfNotNull(tags.str("addr:housenumber"), tags.str("addr:street")).joinToString(" ").takeIf { it.isNotBlank() },
            tags.str("addr:city"),
        ).joinToString(", ").takeIf { it.isNotBlank() }
        return ChargingStation(
            id = "osm:${element.str("type")}/${element.str("id")}",
            name = tags.str("name") ?: tags.str("brand") ?: tags.str("network") ?: "Borne de recharge",
            operator = tags.str("operator") ?: tags.str("network"),
            address = address,
            position = LatLon(lat, lon),
            points = tags.str("capacity")?.toIntOrNull(),
            maxPowerKw = power,
            plugs = plugs,
            free = tags.str("fee")?.let { it == "no" },
            pricing = tags.str("charge"),
            open24 = tags.str("opening_hours") == "24/7",
        )
    }

    private fun parseKw(value: String): Double? {
        val number = Regex("""([\d.,]+)""").find(value)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return when {
            value.contains("kW", ignoreCase = true) -> number
            value.contains("W") -> number / 1000
            else -> number
        }
    }

    private fun JsonObject.bool(key: String): Boolean? = when (str(key)?.lowercase()) {
        "true", "1", "oui", "yes" -> true
        "false", "0", "non", "no" -> false
        else -> null
    }

    private fun degrees(center: LatLon, radiusKm: Double): Pair<Double, Double> =
        radiusKm / 110.574 to radiusKm / (111.320 * cos(Math.toRadians(center.lat)))

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.5f", value)

    companion object {
        private const val TABULAR = "https://tabular-api.data.gouv.fr/api/resources"
        private const val IRVE_DATASET = "fichier-consolide-des-bornes-de-recharge-pour-vehicules-electriques"
        private val OVERPASS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
    }
}
