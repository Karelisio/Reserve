package com.karelisio.reserve.data

import com.karelisio.reserve.data.local.AppDatabase
import com.karelisio.reserve.data.local.VehicleEntity
import com.karelisio.reserve.data.prefs.SettingsRepository

/**
 * Car profiles. The active vehicle's fuel, consumption and usual fill are copied into the
 * settings, so every screen keeps reading a single set of values.
 */
class VehicleRepository(private val db: AppDatabase, private val settings: SettingsRepository) {

    val vehicles = db.vehicles().observeAll()

    /** First launch with vehicles: the current settings become "Ma voiture", with its fill-ups. */
    suspend fun ensureDefault(): VehicleEntity {
        val all = db.vehicles().all()
        val current = settings.current()
        val active = all.firstOrNull { it.id == current.activeVehicleId } ?: all.firstOrNull()
        if (active != null) {
            if (current.activeVehicleId != active.id) activate(active)
            db.fillUps().assignOrphans(active.id)
            return active
        }
        val created = VehicleEntity(
            name = "Ma voiture",
            fuel = current.fuel,
            consumption = current.consumption,
            fillLiters = current.fillLiters,
            tankCapacity = DEFAULT_TANK,
        ).let { it.copy(id = db.vehicles().insert(it)) }
        settings.setActiveVehicle(created.id, created.fuel, created.consumption, created.fillLiters)
        db.fillUps().assignOrphans(created.id)
        return created
    }

    suspend fun active(): VehicleEntity? = settings.current().activeVehicleId?.let { db.vehicles().byId(it) }

    suspend fun activate(vehicle: VehicleEntity) =
        settings.setActiveVehicle(vehicle.id, vehicle.fuel, vehicle.consumption, vehicle.fillLiters)

    /** Saves a new or edited vehicle; editing the active one updates the app values too. */
    suspend fun save(vehicle: VehicleEntity): VehicleEntity {
        val saved = if (vehicle.id == 0L) vehicle.copy(id = db.vehicles().insert(vehicle)) else vehicle.also { db.vehicles().upsert(it) }
        if (settings.current().activeVehicleId == saved.id) activate(saved)
        return saved
    }

    /** The last vehicle cannot be deleted; its fill-ups move to the remaining first vehicle. */
    suspend fun delete(vehicle: VehicleEntity): Boolean {
        val others = db.vehicles().all().filter { it.id != vehicle.id }
        val fallback = others.firstOrNull() ?: return false
        db.fillUps().moveVehicle(vehicle.id, fallback.id)
        db.vehicles().delete(vehicle.id)
        if (settings.current().activeVehicleId == vehicle.id) activate(fallback)
        return true
    }

    companion object {
        const val DEFAULT_TANK = 50.0
    }
}
