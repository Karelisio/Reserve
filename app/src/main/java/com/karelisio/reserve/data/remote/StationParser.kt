package com.karelisio.reserve.data.remote

import com.karelisio.reserve.data.model.DayHours
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.FuelPrice
import com.karelisio.reserve.data.model.FuelShortage
import com.karelisio.reserve.data.model.Station
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Maps a record of the "prix-des-carburants-en-france-flux-instantane-v2" dataset. */
object StationParser {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val localFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
    )

    fun parse(record: JsonObject): Station? {
        val id = record.lng("id") ?: return null
        val (lat, lon) = coordinates(record) ?: return null

        val prices = Fuel.entries.mapNotNull { fuel ->
            val price = record.dbl("${fuel.apiKey}_prix")?.normalizePrice() ?: return@mapNotNull null
            val date = parseDate(record.str("${fuel.apiKey}_maj")) ?: return@mapNotNull null
            FuelPrice(fuel, price, date)
        }.ifEmpty { pricesFromDocument(record["prix"]) }

        val services = record["services_service"].asStringList().ifEmpty {
            record["services"].decodeEmbedded().obj()?.get("service").asStringList()
        }

        val hoursDoc = record["horaires"].decodeEmbedded().obj()
        val automate = record.str("horaires_automate_24_24")?.equals("Oui", ignoreCase = true)
            ?: (hoursDoc?.str("@automate-24-24") == "1")

        return Station(
            id = id,
            lat = lat,
            lon = lon,
            address = record.str("adresse").orEmpty().trim(),
            city = record.str("ville").orEmpty().trim(),
            postalCode = record.str("cp").orEmpty(),
            isHighway = record.str("pop") == "A",
            automate24 = automate,
            services = services,
            hours = parseHours(hoursDoc),
            prices = prices,
            shortages = parseShortages(record),
        )
    }

    private fun coordinates(record: JsonObject): Pair<Double, Double>? {
        record["geom"].obj()?.let { geom ->
            val lat = geom.dbl("lat")
            val lon = geom.dbl("lon")
            if (lat != null && lon != null) return lat to lon
        }
        // Legacy encoding: degrees multiplied by 100 000.
        val lat = record.dbl("latitude") ?: return null
        val lon = record.dbl("longitude") ?: return null
        return if (kotlin.math.abs(lat) > 90) lat / 100_000 to lon / 100_000 else lat to lon
    }

    private fun Double.normalizePrice(): Double? = when {
        this <= 0.0 -> null
        this > 100 -> this / 1000 // Older feeds publish thousandths of euro.
        else -> this
    }

    private fun pricesFromDocument(element: JsonElement?): List<FuelPrice> =
        element.decodeEmbedded().asObjectList().mapNotNull { item ->
            val fuel = item.str("@nom")?.let(Fuel::fromApiName) ?: return@mapNotNull null
            val price = item.dbl("@valeur")?.normalizePrice() ?: return@mapNotNull null
            val date = parseDate(item.str("@maj")) ?: return@mapNotNull null
            FuelPrice(fuel, price, date)
        }.distinctBy { it.fuel }

    private fun parseShortages(record: JsonObject): List<FuelShortage> {
        val fromDocument = record["rupture"].decodeEmbedded().asObjectList().mapNotNull { item ->
            val fuel = item.str("@nom")?.let(Fuel::fromApiName) ?: return@mapNotNull null
            // A shortage with an end date is over.
            if (item.str("@fin") != null) return@mapNotNull null
            FuelShortage(
                fuel = fuel,
                definitive = item.str("@type")?.startsWith("d", ignoreCase = true) == true,
                since = parseDate(item.str("@debut")),
            )
        }
        if (fromDocument.isNotEmpty()) return fromDocument.distinctBy { it.fuel }
        val temporary = record["carburants_rupture_temporaire"].asStringList()
            .mapNotNull(Fuel::fromApiName).map { FuelShortage(it, definitive = false) }
        val definitive = record["carburants_rupture_definitive"].asStringList()
            .mapNotNull(Fuel::fromApiName).map { FuelShortage(it, definitive = true) }
        return (temporary + definitive).distinctBy { it.fuel }
    }

    private fun parseHours(doc: JsonObject?): List<DayHours> {
        if (doc == null) return emptyList()
        return doc["jour"].asObjectList().mapNotNull { day ->
            val index = day.lng("@id")?.toInt()?.takeIf { it in 1..7 } ?: return@mapNotNull null
            val closed = day.str("@ferme") == "1"
            val ranges = day["horaire"].asObjectList().mapNotNull { range ->
                val open = range.str("@ouverture")?.replace('.', ':') ?: return@mapNotNull null
                val close = range.str("@fermeture")?.replace('.', ':') ?: return@mapNotNull null
                "$open–$close"
            }
            DayHours(index, closed, ranges)
        }.sortedBy { it.day }
    }

    fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        for (format in localFormats) {
            runCatching { return LocalDateTime.parse(value, format).atZone(paris).toInstant().toEpochMilli() }
        }
        return null
    }
}
