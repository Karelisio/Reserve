package com.karelisio.reserve

import android.app.Application
import com.karelisio.reserve.diagnostics.Diagnostics
import com.karelisio.reserve.notifications.Notifier
import com.karelisio.reserve.work.PriceRefreshWorker
import com.karelisio.reserve.work.WeeklyRecapWorker
import kotlinx.coroutines.launch

class ReserveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        container = AppContainer(this)
        Notifier.createChannels(this)
        PriceRefreshWorker.schedule(this)
        WeeklyRecapWorker.schedule(this, enabled = true)
        Shortcuts.publish(this)
        container.appScope.launch { container.vehicles.ensureDefault() }
    }
}
