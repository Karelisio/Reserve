package com.karelisio.reserve

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.karelisio.reserve.ui.ReserveNavHost
import com.karelisio.reserve.ui.theme.ReserveTheme

class MainActivity : ComponentActivity() {

    private var openStationId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            ReserveTheme {
                ReserveNavHost(openStationId = openStationId, onStationOpened = { openStationId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_STATION_ID, -1L) ?: -1L
        if (id > 0) openStationId = id
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"

        fun stationIntent(context: android.content.Context, stationId: Long?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (stationId != null) putExtra(EXTRA_STATION_ID, stationId) }
    }
}
