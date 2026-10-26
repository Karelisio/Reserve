package com.karelisio.reserve.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.container
import com.karelisio.reserve.data.local.FavoriteEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.ThemeMode
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.theme.ReserveTheme
import kotlinx.coroutines.launch

/** Per-widget choice: cheapest around or one favorite, and the fuel (null = the app's fuel). */
data class WidgetConfig(val stationId: Long?, val fuel: Fuel?) {
    companion object {
        private val STATION = longPreferencesKey("station_id")
        private val FUEL = stringPreferencesKey("fuel")

        suspend fun read(context: Context, id: GlanceId): WidgetConfig {
            val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            return WidgetConfig(
                stationId = prefs[STATION],
                fuel = prefs[FUEL]?.let { runCatching { Fuel.valueOf(it) }.getOrNull() },
            )
        }

        suspend fun write(context: Context, id: GlanceId, config: WidgetConfig) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    if (config.stationId != null) this[STATION] = config.stationId else remove(STATION)
                    if (config.fuel != null) this[FUEL] = config.fuel.name else remove(FUEL)
                }
            }
        }
    }
}

/** Opened when the widget is placed (or reconfigured with a long press on it). */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val favorites by container.db.favorites().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            var initial by remember { mutableStateOf<WidgetConfig?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(glanceId) { initial = WidgetConfig.read(this@WidgetConfigActivity, glanceId) }
            val s = settings ?: return@setContent
            val config = initial ?: return@setContent
            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ReserveTheme(darkTheme = dark, dynamicColor = s.dynamicColor, pureBlack = s.pureBlack) {
                ConfigScreen(
                    initial = config,
                    appFuel = s.fuel,
                    favorites = favorites,
                    onSave = { chosen ->
                        scope.launch {
                            WidgetConfig.write(this@WidgetConfigActivity, glanceId, chosen)
                            ReserveWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    initial: WidgetConfig,
    appFuel: Fuel,
    favorites: List<FavoriteEntity>,
    onSave: (WidgetConfig) -> Unit,
) {
    var stationId by remember { mutableStateOf(initial.stationId) }
    // null = follow the fuel chosen in the app.
    var fuel by remember { mutableStateOf(initial.fuel) }
    Scaffold(
        topBar = { MediumFlexibleTopAppBar(title = { Text("Widget Reserve") }, subtitle = { Text("Que doit-il afficher ?") }) },
        bottomBar = {
            Button(
                onClick = { onSave(WidgetConfig(stationId, fuel)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 16.dp),
            ) { Text("Valider") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Carburant", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ConnectedChoice(
                    options = listOf<Fuel?>(null) + Fuel.selectableEntries,
                    selected = fuel,
                    label = { it?.let { f -> if (f == Fuel.E10) "E10" else f.label } ?: "Appli" },
                    onSelect = { fuel = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (fuel == null) "Suit le carburant choisi dans l'appli (${appFuel.label})." else "Toujours ${fuel!!.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Text("Station", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Choice(
                    selected = stationId == null,
                    title = "Le moins cher autour de moi",
                    caption = "Vrai coût, détour compris",
                    icon = { Icon(Icons.Rounded.NearMe, null) },
                    onClick = { stationId = null },
                )
            }
            items(favorites, key = { it.stationId }) { favorite ->
                Choice(
                    selected = stationId == favorite.stationId,
                    title = favorite.label,
                    caption = favorite.address,
                    icon = { Icon(Icons.Rounded.Favorite, null) },
                    onClick = { stationId = favorite.stationId },
                )
            }
            if (favorites.isEmpty()) {
                item {
                    Text(
                        "Ajoutez des favoris dans l'appli pour pouvoir épingler une station.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, caption: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = icon,
        supportingContent = { Text(caption, maxLines = 1) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(title, maxLines = 1) }
}
