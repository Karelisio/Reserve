package com.karelisio.reserve

import android.content.Context
import com.karelisio.reserve.data.BackupManager
import com.karelisio.reserve.data.StationRepository
import com.karelisio.reserve.data.local.AppDatabase
import com.karelisio.reserve.data.prefs.SettingsRepository
import com.karelisio.reserve.data.remote.FuelApi
import com.karelisio.reserve.data.remote.Http
import com.karelisio.reserve.data.remote.OverpassApi
import com.karelisio.reserve.location.LocationProvider
import com.karelisio.reserve.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container, created once by [ReserveApp]. */
class AppContainer(val context: Context) {
    val http = Http.client(context.cacheDir)
    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val location = LocationProvider(context)
    val stations = StationRepository(context, settings, FuelApi(http), OverpassApi(http), db)

    /** Work that must outlive a screen (update download). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val updates = UpdateManager(context, http, settings, appScope)
    val backup = BackupManager(this)
}

val Context.container: AppContainer
    get() = (applicationContext as ReserveApp).container
