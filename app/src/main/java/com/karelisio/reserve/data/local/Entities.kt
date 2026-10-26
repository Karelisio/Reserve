package com.karelisio.reserve.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.karelisio.reserve.data.model.Fuel
import kotlinx.serialization.Serializable

/** Name and brand found in OpenStreetMap for an official station (null when no match). */
@Entity(tableName = "osm_match")
data class OsmMatchEntity(
    @PrimaryKey val stationId: Long,
    val brand: String?,
    val name: String?,
    val fetchedAt: Long,
)

@Serializable
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val stationId: Long,
    val label: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val addedAt: Long,
    /** Latest known station snapshot (JSON), so favorites work offline. */
    val snapshot: String?,
    /** Price that last triggered an alert, per fuel, to avoid duplicate notifications. */
    val lastAlertPrice: Double? = null,
)

@Serializable
@Entity(
    tableName = "price_history",
    indices = [Index(value = ["stationId", "fuel", "updatedAt"], unique = true)],
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Long,
    val fuel: Fuel,
    val price: Double,
    val updatedAt: Long,
)

/** Daily average price of a fuel in a ~20 km zone, recorded locally to compute the trend. */
@Serializable
@Entity(tableName = "zone_average", primaryKeys = ["day", "zone", "fuel"])
data class ZoneAverageEntity(
    val day: Long,
    val zone: String,
    val fuel: Fuel,
    val average: Double,
    val minimum: Double,
    val samples: Int,
)

@Serializable
@Entity(tableName = "fill_up")
data class FillUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val stationId: Long?,
    val stationLabel: String,
    val fuel: Fuel,
    val liters: Double,
    val pricePerLiter: Double,
    val odometerKm: Int?,
    val fullTank: Boolean,
    /** Average price around the station when filling up, to measure the savings. */
    val localAveragePrice: Double?,
    /** Vehicle of the fill-up (added in schema 2; null only for logs older than vehicles). */
    val vehicleId: Long? = null,
)

/** A car profile: its fuel, consumption and tank, used for the real cost and the range. */
@Serializable
@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fuel: Fuel,
    /** Liters per 100 km. */
    val consumption: Double,
    /** Liters usually put in the tank. */
    val fillLiters: Double,
    /** Tank capacity in liters, for the estimated range. */
    val tankCapacity: Double,
)

enum class DiscountUnit { CENTS_PER_LITER, PERCENT }

@Serializable
@Entity(tableName = "loyalty_card")
data class LoyaltyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Key of [com.karelisio.reserve.domain.Brands]. */
    val brandKey: String,
    val discount: Double,
    val unit: DiscountUnit,
)
