package com.karelisio.reserve.data.model

import com.karelisio.reserve.domain.t
import kotlinx.serialization.Serializable

/** Fuels published by the official feed. [selectable] fuels can be chosen in the search. */
@Serializable
enum class Fuel(
    val apiKey: String,
    val apiNames: Set<String>,
    val selectable: Boolean = true,
) {
    GAZOLE("gazole", setOf("gazole")),
    E10("e10", setOf("e10", "sp95-e10")),
    SP98("sp98", setOf("sp98")),
    E85("e85", setOf("e85")),
    GPLC("gplc", setOf("gplc", "gpl")),
    SP95("sp95", setOf("sp95"), selectable = false);

    val label: String
        get() = when (this) {
            GAZOLE -> t("Gazole", "Diesel")
            E10 -> "SP95-E10"
            SP98 -> "SP98"
            E85 -> "E85"
            GPLC -> t("GPLc", "LPG")
            SP95 -> "SP95"
        }

    companion object {
        val selectableEntries = entries.filter { it.selectable }

        fun fromApiName(name: String): Fuel? {
            val key = name.trim().lowercase()
            return entries.firstOrNull { key in it.apiNames }
        }
    }
}

@Serializable
data class FuelPrice(
    val fuel: Fuel,
    /** Price in euros per liter. */
    val price: Double,
    /** Last update of the price, epoch milliseconds. */
    val updatedAt: Long,
)

@Serializable
data class FuelShortage(
    val fuel: Fuel,
    val definitive: Boolean,
    val since: Long? = null,
)

@Serializable
data class DayHours(
    /** 1 = Monday … 7 = Sunday. */
    val day: Int,
    val closed: Boolean,
    /** Opening ranges formatted as "06:00–20:00". */
    val ranges: List<String>,
)

@Serializable
data class Station(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val address: String,
    val city: String,
    val postalCode: String,
    /** "A" in the official feed: the station is on a highway (autoroute). */
    val isHighway: Boolean,
    /** Fuel can be bought 24/24 with a card machine. */
    val automate24: Boolean,
    val services: List<String>,
    val hours: List<DayHours>,
    val prices: List<FuelPrice>,
    val shortages: List<FuelShortage>,
    val brand: String? = null,
    val name: String? = null,
) {
    fun priceOf(fuel: Fuel): FuelPrice? = prices.firstOrNull { it.fuel == fuel }

    fun isOutOf(fuel: Fuel): Boolean = shortages.any { it.fuel == fuel && !it.definitive }

    val displayName: String
        get() = when {
            brand != null && name != null && !name.contains(brand, ignoreCase = true) -> "$brand · ${name.trim()}"
            brand != null -> brand
            name != null -> name
            else -> "Station ${city.lowercase().replaceFirstChar { it.titlecase() }}"
        }

    val fullAddress: String
        get() = listOf(address, "$postalCode ${city}".trim()).filter { it.isNotBlank() }.joinToString(", ")

    fun hasService(keyword: String): Boolean = services.any { it.contains(keyword, ignoreCase = true) }
}

@Serializable
data class LatLon(val lat: Double, val lon: Double)

/** Last successful search, persisted so the app works offline. */
@Serializable
data class SearchResult(
    val center: LatLon,
    val radiusKm: Int,
    val fetchedAt: Long,
    val stations: List<Station>,
)

@Serializable
enum class SortMode {
    PRICE,
    DISTANCE,
    REAL_COST;

    val label: String
        get() = when (this) {
            PRICE -> t("Prix", "Price")
            DISTANCE -> "Distance"
            REAL_COST -> t("Vrai coût", "Real cost")
        }
}

@Serializable
enum class NavApp { ASK, WAZE, GOOGLE_MAPS, OTHER }

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    val label: String
        get() = when (this) {
            SYSTEM -> t("Système", "System")
            LIGHT -> t("Clair", "Light")
            DARK -> t("Sombre", "Dark")
        }
}

@Serializable
data class Filters(
    val open24: Boolean = false,
    val wash: Boolean = false,
    val inflation: Boolean = false,
    val shop: Boolean = false,
    val excludeHighway: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(open24, wash, inflation, shop, excludeHighway).count { it }

    fun accepts(station: Station): Boolean {
        if (open24 && !(station.automate24 || station.hasService("Automate CB 24/24"))) return false
        if (wash && !station.hasService("Lavage")) return false
        if (inflation && !station.hasService("gonflage")) return false
        if (shop && !station.hasService("Boutique")) return false
        if (excludeHighway && station.isHighway) return false
        return true
    }
}
