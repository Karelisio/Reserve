package com.karelisio.reserve.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.local.PriceHistoryEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.Vehicle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StationUiState(
    val station: Station? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isFavorite: Boolean = false,
    val settings: Settings = Settings(),
    val ranked: RankedStation? = null,
    val history: List<PriceHistoryEntity> = emptyList(),
    val historyFuel: Fuel = Fuel.GAZOLE,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StationViewModel(private val app: AppContainer, private val stationId: Long) : ViewModel() {

    private val station = MutableStateFlow<Station?>(null)
    private val loading = MutableStateFlow(true)
    private val historyFuel = MutableStateFlow<Fuel?>(null)

    private val history = combine(app.settings.settings.map { it.fuel }.distinctUntilChanged(), historyFuel) { s, h -> h ?: s }
        .flatMapLatest { fuel ->
            app.db.priceHistory().observe(stationId, fuel, System.currentTimeMillis() - HISTORY_MS).map { fuel to it }
        }

    val state: StateFlow<StationUiState> = combine(
        station,
        loading,
        app.db.favorites().observeIds().map { stationId in it },
        app.settings.settings,
        combine(history, app.db.loyaltyCards().observeAll()) { h, c -> h to c },
    ) { station, loading, favorite, settings, (history, cards) ->
        val origin = app.stations.lastResult.value?.center ?: settings.lastLocation
        val ranked = if (station != null && origin != null) {
            Pricing.rank(
                listOf(station), settings.fuel, origin, Vehicle(settings.consumption, settings.fillLiters), cards,
            ).firstOrNull()
        } else null
        StationUiState(
            station = station,
            loading = loading,
            notFound = station == null && !loading,
            isFavorite = favorite,
            settings = settings,
            ranked = ranked,
            history = history.second,
            historyFuel = history.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationUiState())

    init {
        viewModelScope.launch {
            station.value = app.stations.cachedStation(stationId)
            loading.value = station.value == null
            // Refresh in the background: prices of this station may be newer than the list.
            app.stations.station(stationId, refresh = true)?.let { station.value = it }
            loading.value = false
        }
    }

    fun selectHistoryFuel(fuel: Fuel) {
        historyFuel.value = fuel
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = station.value ?: return@launch
        if (state.value.isFavorite) app.stations.removeFavorite(s.id) else app.stations.addFavorite(s)
    }

    companion object {
        private const val HISTORY_MS = 90L * 24 * 3600 * 1000
    }
}
