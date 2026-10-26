package com.karelisio.reserve

import android.content.Context
import com.karelisio.reserve.data.StationRepository
import com.karelisio.reserve.data.local.AppDatabase
import com.karelisio.reserve.data.prefs.SettingsRepository
import com.karelisio.reserve.data.remote.FuelApi
import com.karelisio.reserve.data.remote.Http
import com.karelisio.reserve.data.remote.OverpassApi
import com.karelisio.reserve.location.LocationProvider

/** Manual dependency container, created once by [ReserveApp]. */
class AppContainer(val context: Context) {
    val http = Http.client(context.cacheDir)
    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val location = LocationProvider(context)
    val stations = StationRepository(context, FuelApi(http), OverpassApi(http), db)
}

val Context.container: AppContainer
    get() = (applicationContext as ReserveApp).container
