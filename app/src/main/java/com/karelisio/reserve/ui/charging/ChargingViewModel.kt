package com.karelisio.reserve.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.charging.ChargingRepository
import com.karelisio.reserve.data.charging.ChargingResult
import com.karelisio.reserve.data.charging.ChargingStation
import com.karelisio.reserve.data.charging.Plug
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.NavApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChargingFilters(val plugs: Set<Plug> = emptySet(), val fastOnly: Boolean = false, val freeOnly: Boolean = false) {
    fun accepts(s: ChargingStation): Boolean =
        (plugs.isEmpty() || s.plugs.any { it in plugs }) &&
            (!fastOnly || (s.maxPowerKw ?: 0.0) >= FAST_KW) &&
            (!freeOnly || s.free == true)

    companion object {
        const val FAST_KW = 50.0
    }
}

data class ChargingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val result: ChargingResult? = null,
    val origin: LatLon? = null,
    val radiusKm: Int = 10,
    val filters: ChargingFilters = ChargingFilters(),
    val navApp: NavApp = NavApp.ASK,
) {
    val stations: List<ChargingStation> get() = result?.stations.orEmpty().filter(filters::accepts)
}

class ChargingViewModel(private val app: AppContainer) : ViewModel() {

    private val repository = ChargingRepository(app.http)
    private val data = MutableStateFlow(ChargingUiState())
    private val filters = MutableStateFlow(ChargingFilters())

    val state: StateFlow<ChargingUiState> = combine(data, filters, app.settings.settings.map { it.navApp }) { d, f, nav ->
        d.copy(filters = f, navApp = nav)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargingUiState())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        data.value = data.value.copy(loading = true, error = null)
        val settings = app.settings.current()
        val origin = app.location.current() ?: settings.lastLocation ?: app.stations.lastResult.value?.center
        if (origin == null) {
            data.value = data.value.copy(loading = false, error = "Position indisponible. Activez la localisation.")
            return@launch
        }
        data.value = runCatching { repository.around(origin, settings.radiusKm.toDouble()) }.fold(
            onSuccess = { ChargingUiState(loading = false, result = it, origin = origin, radiusKm = settings.radiusKm) },
            onFailure = { data.value.copy(loading = false, error = "Impossible de charger les bornes. Vérifiez la connexion.", origin = origin) },
        )
    }

    fun togglePlug(plug: Plug) {
        val current = filters.value.plugs
        filters.value = filters.value.copy(plugs = if (plug in current) current - plug else current + plug)
    }

    fun setFastOnly(value: Boolean) {
        filters.value = filters.value.copy(fastOnly = value)
    }

    fun setFreeOnly(value: Boolean) {
        filters.value = filters.value.copy(freeOnly = value)
    }
}
