package com.karelisio.reserve

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.karelisio.reserve.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Users keep their favorites and fuel log across updates: a database created with the
 * first released schema (app/schemas/…/1.json) must open with the current version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    @Test
    fun version1DataSurvivesUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        createFromSchema(context.getDatabasePath(name), version = 1)

        val db = AppDatabase.create(context, name)
        val favorites = db.favorites().all()
        val fillUps = db.fillUps().all()
        assertEquals(listOf(1000001L), favorites.map { it.stationId })
        assertEquals(42.0, fillUps.single().liters, 0.0)
        assertEquals(1, db.loyaltyCards().all().size)
        // Schema 2: vehicles, and fill-ups attached to the first one.
        assertEquals(null, fillUps.single().vehicleId)
        val vehicleId = db.vehicles().insert(
            com.karelisio.reserve.data.local.VehicleEntity(
                name = "Ma voiture", fuel = com.karelisio.reserve.data.model.Fuel.GAZOLE,
                consumption = 6.0, fillLiters = 40.0, tankCapacity = 50.0,
            ),
        )
        db.fillUps().assignOrphans(vehicleId)
        assertEquals(vehicleId, db.fillUps().all().single().vehicleId)
        db.close()
    }

    /** Builds the database exactly as Room created it for [version], with sample rows. */
    private fun createFromSchema(file: File, version: Int) {
        val schemaFile = File("schemas/com.karelisio.reserve.data.local.AppDatabase/$version.json")
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject["database"]!!.jsonObject
        file.parentFile?.mkdirs()
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            sql.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                sql.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.execSQL(
            "INSERT INTO favorite (stationId, label, address, lat, lon, addedAt, snapshot, lastAlertPrice) " +
                "VALUES (1000001, 'E.Leclerc', '1 rue', 48.8, 2.3, 1, NULL, NULL)",
        )
        sql.execSQL(
            "INSERT INTO fill_up (date, stationId, stationLabel, fuel, liters, pricePerLiter, odometerKm, fullTank, localAveragePrice) " +
                "VALUES (1700000000000, 1000001, 'E.Leclerc', 'GAZOLE', 42.0, 1.75, 12000, 1, 1.80)",
        )
        sql.execSQL("INSERT INTO loyalty_card (brandKey, discount, unit) VALUES ('LECLERC', 5.0, 'CENTS_PER_LITER')")
        sql.version = version
        sql.close()
    }
}
