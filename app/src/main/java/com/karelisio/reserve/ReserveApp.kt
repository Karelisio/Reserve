package com.karelisio.reserve

import android.app.Application
import com.karelisio.reserve.notifications.Notifier
import com.karelisio.reserve.work.PriceRefreshWorker

class ReserveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        PriceRefreshWorker.schedule(this)
        Shortcuts.publish(this)
    }
}
