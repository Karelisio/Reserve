package com.karelisio.reserve

import android.app.Application
import com.karelisio.reserve.notifications.Notifier
import com.karelisio.reserve.work.PriceRefreshWorker
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class ReserveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
        // Tiles are fetched with the app User-Agent, as required by the OSM tile policy.
        HttpRequestUtil.setOkHttpClient(container.http)
        Notifier.createChannels(this)
        PriceRefreshWorker.schedule(this)
    }
}
