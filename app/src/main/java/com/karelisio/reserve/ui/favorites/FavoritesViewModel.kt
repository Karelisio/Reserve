package com.karelisio.reserve.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.FuelPrice
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.data.prefs.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoriteItem(
    val station: Station,
    val price: FuelPrice?,
    val history: List<Pair<Long, Double>>,
    /** Last price change for the fuel (€), negative = cheaper. */
    val lastChange: Double?,
)

data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val settings: Settings = Settings(),
    val loading: Boolean = true,
    val offline: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(private val app: AppContainer) : ViewModel() {

    private val live = MutableStateFlow<Map<Long, Station>>(emptyMap())
    private val status = MutableStateFlow(true to false) // loading, offline

    private val favorites = app.db.favorites().observeAll()
    private val fuel = app.settings.settings.map { it.fuel }.distinctUntilChanged()

    private val history = combine(favorites.map { list -> list.map { it.stationId } }.distinctUntilChanged(), fuel) { ids, f -> ids to f }
        .flatMapLatest { (ids, f) ->
            if (ids.isEmpty()) flowOf(emptyList())
            else app.db.priceHistory().observeMany(ids, f, System.currentTimeMillis() - HISTORY_MS)
        }

    val state: StateFlow<FavoritesUiState> = combine(favorites, live, history, app.settings.settings, status) { favs, live, history, settings, (loading, offline) ->
        val byStation = history.groupBy { it.stationId }
        FavoritesUiState(
            items = favs.mapNotNull { fav ->
                val station = live[fav.stationId] ?: fav.snapshot?.let(app.stations::decodeStation) ?: return@mapNotNull null
                val points = byStation[fav.stationId].orEmpty().map { it.updatedAt to it.price }
                val changes = points.map { it.second }.zipWithNext { a, b -> b - a }.filter { kotlin.math.abs(it) > 0.0005 }
                FavoriteItem(station, station.priceOf(settings.fuel), points, changes.lastOrNull())
            },
            settings = settings,
            loading = loading,
            offline = offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        status.value = true to false
        val ids = app.db.favorites().all().map { it.stationId }
        val ok = runCatching { app.stations.stationsByIds(ids) }
            .onSuccess { stations -> live.value = stations.associateBy { it.id } }
            .isSuccess
        status.value = false to !ok
    }

    fun setFuel(fuel: Fuel) = viewModelScope.launch { app.settings.setFuel(fuel) }

    fun remove(id: Long) = viewModelScope.launch { app.stations.removeFavorite(id) }

    companion object {
        private const val HISTORY_MS = 30L * 24 * 3600 * 1000
    }
}
