package com.karelisio.reserve.ui.fuellog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.local.FillUpEntity
import com.karelisio.reserve.data.local.VehicleEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.prefs.PendingFillUp
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.FuelLog
import com.karelisio.reserve.domain.FuelLogStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FuelLogUiState(
    val fillUps: List<FillUpEntity> = emptyList(),
    val stats: FuelLogStats? = null,
    val settings: Settings = Settings(),
    val vehicles: List<VehicleEntity> = emptyList(),
    /** Vehicle shown in the log (the active one by default). */
    val vehicleId: Long? = null,
)

/** Draft of a fill-up being entered. */
data class FillUpDraft(
    val stationId: Long?,
    val stationLabel: String,
    val fuel: Fuel,
    val pricePerLiter: Double?,
)

class FuelLogViewModel(private val app: AppContainer) : ViewModel() {

    private val shownVehicle = MutableStateFlow<Long?>(null)

    val state: StateFlow<FuelLogUiState> = combine(
        app.db.fillUps().observeAll(),
        app.settings.settings,
        app.vehicles.vehicles,
        shownVehicle,
    ) { fillUps, settings, vehicles, shown ->
        val vehicleId = shown ?: settings.activeVehicleId ?: vehicles.firstOrNull()?.id
        val mine = if (vehicles.size <= 1) fillUps else fillUps.filter { it.vehicleId == vehicleId }
        FuelLogUiState(mine, FuelLog.stats(mine), settings, vehicles, vehicleId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelLogUiState())

    fun showVehicle(id: Long) {
        shownVehicle.value = id
    }

    val pending: StateFlow<PendingFillUp?> = app.settings.pendingFillUp
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(draft: FillUpDraft, liters: Double, price: Double, odometer: Int?, fullTank: Boolean) = viewModelScope.launch {
        app.db.fillUps().insert(
            FillUpEntity(
                date = System.currentTimeMillis(),
                stationId = draft.stationId,
                stationLabel = draft.stationLabel.ifBlank { "Station" },
                fuel = draft.fuel,
                liters = liters,
                pricePerLiter = price,
                odometerKm = odometer,
                fullTank = fullTank,
                localAveragePrice = localAverage(draft.fuel),
                vehicleId = app.settings.current().activeVehicleId,
            ),
        )
        app.settings.setPendingFillUp(null)
    }

    fun dismissPending() = viewModelScope.launch { app.settings.setPendingFillUp(null) }

    fun delete(id: Long) = viewModelScope.launch { app.db.fillUps().delete(id) }

    /** Average price of the fuel among the stations of the last search (fresh prices only). */
    private fun localAverage(fuel: Fuel): Double? {
        val limit = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        return app.stations.lastResult.value?.stations
            ?.mapNotNull { s -> s.priceOf(fuel)?.takeIf { it.updatedAt > limit }?.price }
            ?.takeIf { it.size >= 3 }
            ?.average()
    }

    fun lastOdometer(): Int? = state.value.fillUps.firstNotNullOfOrNull { it.odometerKm }
}
