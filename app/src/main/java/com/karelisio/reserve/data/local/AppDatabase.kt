package com.karelisio.reserve.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.karelisio.reserve.data.model.Fuel
import kotlinx.coroutines.flow.Flow

@Dao
interface OsmMatchDao {
    @Query("SELECT * FROM osm_match WHERE stationId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<OsmMatchEntity>

    @Upsert
    suspend fun upsert(matches: List<OsmMatchEntity>)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY addedAt")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite ORDER BY addedAt")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT stationId FROM favorite")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorite WHERE stationId = :id")
    suspend fun byId(id: Long): FavoriteEntity?

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE stationId = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE favorite SET snapshot = :snapshot, label = :label WHERE stationId = :id")
    suspend fun updateSnapshot(id: Long, label: String, snapshot: String)

    @Query("UPDATE favorite SET lastAlertPrice = :price WHERE stationId = :id")
    suspend fun updateLastAlert(id: Long, price: Double?)
}

@Dao
interface PriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entries: List<PriceHistoryEntity>)

    @Query("SELECT * FROM price_history WHERE stationId = :stationId AND fuel = :fuel AND updatedAt >= :since ORDER BY updatedAt")
    fun observe(stationId: Long, fuel: Fuel, since: Long): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE stationId IN (:ids) AND fuel = :fuel AND updatedAt >= :since ORDER BY updatedAt")
    fun observeMany(ids: List<Long>, fuel: Fuel, since: Long): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE stationId = :stationId AND fuel = :fuel ORDER BY updatedAt DESC LIMIT 2")
    suspend fun lastTwo(stationId: Long, fuel: Fuel): List<PriceHistoryEntity>

    @Query("SELECT * FROM price_history")
    suspend fun all(): List<PriceHistoryEntity>

    @Query("DELETE FROM price_history WHERE updatedAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface ZoneAverageDao {
    @Upsert
    suspend fun upsert(entries: List<ZoneAverageEntity>)

    @Query("SELECT * FROM zone_average WHERE zone = :zone AND fuel = :fuel AND day >= :fromDay ORDER BY day")
    fun observe(zone: String, fuel: Fuel, fromDay: Long): Flow<List<ZoneAverageEntity>>

    @Query("SELECT * FROM zone_average WHERE zone = :zone AND fuel = :fuel AND day >= :fromDay ORDER BY day")
    suspend fun get(zone: String, fuel: Fuel, fromDay: Long): List<ZoneAverageEntity>

    @Query("SELECT * FROM zone_average")
    suspend fun all(): List<ZoneAverageEntity>

    @Query("DELETE FROM zone_average WHERE day < :beforeDay")
    suspend fun prune(beforeDay: Long)
}

@Dao
interface FillUpDao {
    @Query("SELECT * FROM fill_up ORDER BY date DESC")
    fun observeAll(): Flow<List<FillUpEntity>>

    @Insert
    suspend fun insert(fillUp: FillUpEntity): Long

    @Query("SELECT * FROM fill_up ORDER BY date")
    suspend fun all(): List<FillUpEntity>

    @Query("DELETE FROM fill_up WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LoyaltyCardDao {
    @Query("SELECT * FROM loyalty_card ORDER BY id")
    fun observeAll(): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_card ORDER BY id")
    suspend fun all(): List<LoyaltyCardEntity>

    @Upsert
    suspend fun upsert(card: LoyaltyCardEntity)

    @Query("DELETE FROM loyalty_card WHERE id = :id")
    suspend fun delete(id: Long)
}

class Converters {
    @TypeConverter
    fun fuelToString(fuel: Fuel): String = fuel.name

    @TypeConverter
    fun stringToFuel(value: String): Fuel = Fuel.valueOf(value)

    @TypeConverter
    fun unitToString(unit: DiscountUnit): String = unit.name

    @TypeConverter
    fun stringToUnit(value: String): DiscountUnit = DiscountUnit.valueOf(value)
}

@Database(
    entities = [
        OsmMatchEntity::class,
        FavoriteEntity::class,
        PriceHistoryEntity::class,
        ZoneAverageEntity::class,
        FillUpEntity::class,
        LoyaltyCardEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun osmMatches(): OsmMatchDao
    abstract fun favorites(): FavoriteDao
    abstract fun priceHistory(): PriceHistoryDao
    abstract fun zoneAverages(): ZoneAverageDao
    abstract fun fillUps(): FillUpDao
    abstract fun loyaltyCards(): LoyaltyCardDao

    companion object {
        const val NAME = "reserve.db"

        /** Schema changes must keep the users' data: add a migration, never a destructive fallback. */
        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    }
}
