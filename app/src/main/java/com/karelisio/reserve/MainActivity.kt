package com.karelisio.reserve

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.model.ThemeMode
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.ui.ReserveNavHost
import com.karelisio.reserve.ui.theme.ReserveTheme

class MainActivity : ComponentActivity() {

    private var openStationId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // The splash screen stays until the appearance settings are known (no theme flash).
        var settingsLoaded = false
        splash.setKeepOnScreenCondition { !settingsLoaded }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            settingsLoaded = true
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ReserveTheme(darkTheme = dark, dynamicColor = current.dynamicColor, pureBlack = current.pureBlack) {
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

        /**
         * Android 12+: the chosen theme also applies to system-drawn parts (splash screen,
         * window background, widgets' resources). Persisted by the system.
         */
        fun applyNightMode(context: android.content.Context, settings: Settings) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val manager = context.getSystemService(UiModeManager::class.java) ?: return
            manager.setApplicationNightMode(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }
}
