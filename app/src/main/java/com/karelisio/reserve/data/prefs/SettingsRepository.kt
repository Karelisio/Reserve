package com.karelisio.reserve.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val fuel: Fuel = Fuel.GAZOLE,
    val radiusKm: Int = 10,
    val sort: SortMode = SortMode.PRICE,
    val filters: Filters = Filters(),
    /** Consumption of the car, liters per 100 km. */
    val consumption: Double = 6.5,
    /** Liters usually put in the tank. */
    val fillLiters: Double = 40.0,
    val navApp: NavApp = NavApp.ASK,
    val alertsEnabled: Boolean = false,
    /** Notify when a favorite goes below this price (€/L), if set. */
    val alertThreshold: Double? = null,
    /** Notify when a favorite drops by at least this many cents. */
    val alertDropCents: Double = 5.0,
    val carAlertEnabled: Boolean = false,
    val carDeviceAddress: String? = null,
    val carDeviceName: String? = null,
    val lastLocation: LatLon? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Wallpaper colors (Android 12+); otherwise the green theme. */
    val dynamicColor: Boolean = true,
    /** Pure black background in dark mode (OLED screens). */
    val pureBlack: Boolean = false,
)

/** Fill-up suggested after "Y aller", until the user logs it or dismisses it. */
data class PendingFillUp(val stationId: Long, val label: String, val fuel: Fuel, val price: Double, val at: Long)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    private object Keys {
        val fuel = stringPreferencesKey("fuel")
        val radius = intPreferencesKey("radius_km")
        val sort = stringPreferencesKey("sort")
        val open24 = booleanPreferencesKey("filter_24")
        val wash = booleanPreferencesKey("filter_wash")
        val inflation = booleanPreferencesKey("filter_inflation")
        val shop = booleanPreferencesKey("filter_shop")
        val noHighway = booleanPreferencesKey("filter_no_highway")
        val consumption = doublePreferencesKey("consumption")
        val fillLiters = doublePreferencesKey("fill_liters")
        val navApp = stringPreferencesKey("nav_app")
        val alertsEnabled = booleanPreferencesKey("alerts_enabled")
        val alertThreshold = doublePreferencesKey("alert_threshold")
        val alertDrop = doublePreferencesKey("alert_drop_cents")
        val carAlert = booleanPreferencesKey("car_alert")
        val carAddress = stringPreferencesKey("car_address")
        val carName = stringPreferencesKey("car_name")
        val lastCarAlert = longPreferencesKey("last_car_alert")
        val lastLat = doublePreferencesKey("last_lat")
        val lastLon = doublePreferencesKey("last_lon")
        val pendingStation = longPreferencesKey("pending_station")
        val pendingLabel = stringPreferencesKey("pending_label")
        val pendingFuel = stringPreferencesKey("pending_fuel")
        val pendingPrice = doublePreferencesKey("pending_price")
        val pendingAt = longPreferencesKey("pending_at")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val askedBackground = booleanPreferencesKey("asked_background_location")
        val askedInstall = booleanPreferencesKey("asked_install_packages")
        val nationalAverages = stringPreferencesKey("national_averages")
        val nationalAt = longPreferencesKey("national_averages_at")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pureBlack = booleanPreferencesKey("pure_black")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            fuel = p[Keys.fuel]?.let { runCatching { Fuel.valueOf(it) }.getOrNull() } ?: Fuel.GAZOLE,
            radiusKm = p[Keys.radius] ?: 10,
            sort = p[Keys.sort]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.PRICE,
            filters = Filters(
                open24 = p[Keys.open24] ?: false,
                wash = p[Keys.wash] ?: false,
                inflation = p[Keys.inflation] ?: false,
                shop = p[Keys.shop] ?: false,
                excludeHighway = p[Keys.noHighway] ?: false,
            ),
            consumption = p[Keys.consumption] ?: 6.5,
            fillLiters = p[Keys.fillLiters] ?: 40.0,
            navApp = p[Keys.navApp]?.let { runCatching { NavApp.valueOf(it) }.getOrNull() } ?: NavApp.ASK,
            alertsEnabled = p[Keys.alertsEnabled] ?: false,
            alertThreshold = p[Keys.alertThreshold],
            alertDropCents = p[Keys.alertDrop] ?: 5.0,
            carAlertEnabled = p[Keys.carAlert] ?: false,
            carDeviceAddress = p[Keys.carAddress],
            carDeviceName = p[Keys.carName],
            lastLocation = p[Keys.lastLat]?.let { lat -> p[Keys.lastLon]?.let { lon -> LatLon(lat, lon) } },
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            pureBlack = p[Keys.pureBlack] ?: false,
        )
    }

    val pendingFillUp: Flow<PendingFillUp?> = store.data.map { p ->
        val id = p[Keys.pendingStation] ?: return@map null
        PendingFillUp(
            stationId = id,
            label = p[Keys.pendingLabel].orEmpty(),
            fuel = p[Keys.pendingFuel]?.let { runCatching { Fuel.valueOf(it) }.getOrNull() } ?: Fuel.GAZOLE,
            price = p[Keys.pendingPrice] ?: 0.0,
            at = p[Keys.pendingAt] ?: 0L,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setFuel(fuel: Fuel) = store.edit { it[Keys.fuel] = fuel.name }
    suspend fun setRadius(km: Int) = store.edit { it[Keys.radius] = km.coerceIn(5, 30) }
    suspend fun setSort(sort: SortMode) = store.edit { it[Keys.sort] = sort.name }
    suspend fun setFilters(f: Filters) = store.edit {
        it[Keys.open24] = f.open24
        it[Keys.wash] = f.wash
        it[Keys.inflation] = f.inflation
        it[Keys.shop] = f.shop
        it[Keys.noHighway] = f.excludeHighway
    }

    suspend fun setVehicle(consumption: Double, fillLiters: Double) = store.edit {
        it[Keys.consumption] = consumption.coerceIn(2.0, 30.0)
        it[Keys.fillLiters] = fillLiters.coerceIn(5.0, 150.0)
    }

    /** Cached national averages, as "GAZOLE=1.789;E10=1.85" with the time they were fetched. */
    suspend fun nationalAverages(): Pair<Map<Fuel, Double>, Long> {
        val p = store.data.first()
        val map = p[Keys.nationalAverages].orEmpty().split(';').mapNotNull { entry ->
            val (key, value) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val fuel = runCatching { Fuel.valueOf(key) }.getOrNull() ?: return@mapNotNull null
            value.toDoubleOrNull()?.let { fuel to it }
        }.toMap()
        return map to (p[Keys.nationalAt] ?: 0L)
    }

    suspend fun setNationalAverages(values: Map<Fuel, Double>, at: Long) = store.edit {
        it[Keys.nationalAverages] = values.entries.joinToString(";") { (fuel, value) -> "${fuel.name}=$value" }
        it[Keys.nationalAt] = at
    }

    suspend fun setAppearance(mode: ThemeMode, dynamicColor: Boolean, pureBlack: Boolean) = store.edit {
        it[Keys.themeMode] = mode.name
        it[Keys.dynamicColor] = dynamicColor
        it[Keys.pureBlack] = pureBlack
    }

    suspend fun setNavApp(app: NavApp) = store.edit { it[Keys.navApp] = app.name }

    suspend fun setAlerts(enabled: Boolean, threshold: Double?, dropCents: Double) = store.edit {
        it[Keys.alertsEnabled] = enabled
        if (threshold != null) it[Keys.alertThreshold] = threshold else it.remove(Keys.alertThreshold)
        it[Keys.alertDrop] = dropCents.coerceIn(1.0, 50.0)
    }

    suspend fun setCarAlert(enabled: Boolean, address: String?, name: String?) = store.edit {
        it[Keys.carAlert] = enabled
        if (address != null) it[Keys.carAddress] = address else it.remove(Keys.carAddress)
        if (name != null) it[Keys.carName] = name else it.remove(Keys.carName)
    }

    /** Returns true (and records the time) if no car alert was shown during the last hour. */
    suspend fun tryClaimCarAlert(now: Long = System.currentTimeMillis()): Boolean {
        var claimed = false
        store.edit {
            val last = it[Keys.lastCarAlert] ?: 0L
            if (now - last >= 60 * 60 * 1000L) {
                it[Keys.lastCarAlert] = now
                claimed = true
            }
        }
        return claimed
    }

    suspend fun setLastLocation(location: LatLon) = store.edit {
        it[Keys.lastLat] = location.lat
        it[Keys.lastLon] = location.lon
    }

    suspend fun lastUpdateCheck(): Long = store.data.first()[Keys.lastUpdateCheck] ?: 0L

    suspend fun setLastUpdateCheck(at: Long) = store.edit { it[Keys.lastUpdateCheck] = at }

    /** Special permissions (background location, app installs) are proposed once at launch. */
    suspend fun wasAsked(background: Boolean): Boolean =
        store.data.first()[if (background) Keys.askedBackground else Keys.askedInstall] ?: false

    suspend fun setAsked(background: Boolean) = store.edit { it[if (background) Keys.askedBackground else Keys.askedInstall] = true }

    suspend fun setPendingFillUp(pending: PendingFillUp?) = store.edit {
        if (pending == null) {
            listOf(Keys.pendingStation, Keys.pendingAt).forEach { key -> it.remove(key) }
            it.remove(Keys.pendingLabel); it.remove(Keys.pendingFuel); it.remove(Keys.pendingPrice)
        } else {
            it[Keys.pendingStation] = pending.stationId
            it[Keys.pendingLabel] = pending.label
            it[Keys.pendingFuel] = pending.fuel.name
            it[Keys.pendingPrice] = pending.price
            it[Keys.pendingAt] = pending.at
        }
    }
}
