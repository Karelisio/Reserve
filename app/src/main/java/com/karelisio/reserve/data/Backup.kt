package com.karelisio.reserve.data

import androidx.room.withTransaction
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.local.FavoriteEntity
import com.karelisio.reserve.data.local.FillUpEntity
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.data.local.PriceHistoryEntity
import com.karelisio.reserve.data.local.VehicleEntity
import com.karelisio.reserve.data.local.ZoneAverageEntity
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.ThemeMode
import com.karelisio.reserve.data.remote.Http
import com.karelisio.reserve.domain.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class BackupSettings(
    val fuel: Fuel,
    val radiusKm: Int,
    val sort: SortMode,
    val filters: Filters,
    val consumption: Double,
    val fillLiters: Double,
    val navApp: NavApp,
    val alertsEnabled: Boolean,
    val alertThreshold: Double?,
    val alertDropCents: Double,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val pureBlack: Boolean,
)

/** Everything the user created, in one JSON file (no account, so this is the only copy). */
@Serializable
data class BackupFile(
    val format: String = "reserve-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val settings: BackupSettings,
    val favorites: List<FavoriteEntity>,
    val fillUps: List<FillUpEntity>,
    val loyaltyCards: List<LoyaltyCardEntity>,
    val priceHistory: List<PriceHistoryEntity>,
    val zoneAverages: List<ZoneAverageEntity>,
    val vehicles: List<VehicleEntity> = emptyList(),
)

data class ImportSummary(val favorites: Int, val fillUps: Int, val cards: Int)

class BackupManager(private val app: AppContainer) {

    suspend fun export(out: OutputStream) {
        val s = app.settings.current()
        val db = app.db
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(
                s.fuel, s.radiusKm, s.sort, s.filters, s.consumption, s.fillLiters, s.navApp,
                s.alertsEnabled, s.alertThreshold, s.alertDropCents, s.themeMode, s.dynamicColor, s.pureBlack,
            ),
            favorites = db.favorites().all(),
            fillUps = db.fillUps().all(),
            loyaltyCards = db.loyaltyCards().all(),
            priceHistory = db.priceHistory().all(),
            zoneAverages = db.zoneAverages().all(),
            vehicles = db.vehicles().all(),
        )
        withContext(Dispatchers.IO) {
            out.bufferedWriter().use { it.write(Http.json.encodeToString(BackupFile.serializer(), file)) }
        }
    }

    /** Merges a backup into the current data: nothing is deleted, duplicates are skipped. */
    suspend fun import(input: InputStream): ImportSummary {
        val text = withContext(Dispatchers.IO) { input.bufferedReader().use { it.readText() } }
        val file = Http.json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == "reserve-backup") { "Ce fichier n'est pas une sauvegarde Reserve." }
        val db = app.db
        var fillUps = 0
        var cards = 0
        db.withTransaction {
            file.favorites.forEach { db.favorites().upsert(it) }
            val existingFills = db.fillUps().all().map { Triple(it.date, it.liters, it.pricePerLiter) }.toSet()
            file.fillUps.filter { Triple(it.date, it.liters, it.pricePerLiter) !in existingFills }.forEach {
                db.fillUps().insert(it.copy(id = 0))
                fillUps++
            }
            val existingCards = db.loyaltyCards().all().map { Triple(it.brandKey, it.discount, it.unit) }.toSet()
            file.loyaltyCards.filter { Triple(it.brandKey, it.discount, it.unit) !in existingCards }.forEach {
                db.loyaltyCards().upsert(it.copy(id = 0))
                cards++
            }
            db.priceHistory().insert(file.priceHistory.map { it.copy(id = 0) })
            db.zoneAverages().upsert(file.zoneAverages)
            // Vehicles keep their ids so that imported fill-ups stay attached to them.
            val known = db.vehicles().all().map { it.id }.toSet()
            file.vehicles.filter { it.id !in known }.forEach { db.vehicles().upsert(it) }
        }
        with(file.settings) {
            app.settings.setFuel(fuel)
            app.settings.setRadius(radiusKm)
            app.settings.setSort(sort)
            app.settings.setFilters(filters)
            app.settings.setVehicle(consumption, fillLiters)
            app.settings.setNavApp(navApp)
            app.settings.setAlerts(alertsEnabled, alertThreshold, alertDropCents)
            app.settings.setAppearance(themeMode, dynamicColor, pureBlack)
        }
        return ImportSummary(file.favorites.size, fillUps, cards)
    }

    /** Fuel log for a spreadsheet (French format: semicolons, decimal commas). */
    suspend fun exportFillUpsCsv(out: OutputStream) {
        val fillUps = app.db.fillUps().all()
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        fun n(value: Double, decimals: Int) = Format.number(value, decimals)
        fun cell(text: String) = if (text.contains(';') || text.contains('"')) "\"${text.replace("\"", "\"\"")}\"" else text
        withContext(Dispatchers.IO) {
            out.bufferedWriter().use { w ->
                w.write("\uFEFF") // BOM: accents shown correctly by spreadsheets
                w.write("Date;Station;Carburant;Litres;Prix au litre;Total;Kilométrage;Plein complet;Prix moyen local;Économie\n")
                fillUps.forEach { f ->
                    val date = dateFormat.format(Instant.ofEpochMilli(f.date).atZone(ZoneId.systemDefault()))
                    val saving = f.localAveragePrice?.let { (it - f.pricePerLiter) * f.liters }
                    w.write(
                        listOf(
                            date, cell(f.stationLabel), f.fuel.label, n(f.liters, 2), n(f.pricePerLiter, 3),
                            n(f.liters * f.pricePerLiter, 2), f.odometerKm?.toString().orEmpty(),
                            if (f.fullTank) "oui" else "non", f.localAveragePrice?.let { n(it, 3) }.orEmpty(),
                            saving?.let { n(it, 2) }.orEmpty(),
                        ).joinToString(";") + "\n",
                    )
                }
            }
        }
    }
}
