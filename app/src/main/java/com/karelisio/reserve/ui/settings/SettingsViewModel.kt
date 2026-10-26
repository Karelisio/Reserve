package com.karelisio.reserve.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.MainActivity
import com.karelisio.reserve.bluetooth.PairedDevice
import com.karelisio.reserve.data.local.DiscountUnit
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.data.local.VehicleEntity
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.ThemeMode
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.Brand
import com.karelisio.reserve.work.Surfaces
import com.karelisio.reserve.work.WeeklyRecapWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cards: StateFlow<List<LoyaltyCardEntity>> = app.db.loyaltyCards().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCard(brand: Brand, discount: Double, unit: DiscountUnit) = viewModelScope.launch {
        app.db.loyaltyCards().upsert(LoyaltyCardEntity(brandKey = brand.name, discount = discount, unit = unit))
        Surfaces.update(app.context)
    }

    fun deleteCard(id: Long) = viewModelScope.launch {
        app.db.loyaltyCards().delete(id)
        Surfaces.update(app.context)
    }

    val vehicles: StateFlow<List<VehicleEntity>> = app.vehicles.vehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activateVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        app.vehicles.activate(vehicle)
        Surfaces.update(app.context)
    }

    fun saveVehicle(vehicle: VehicleEntity) = viewModelScope.launch {
        app.vehicles.save(vehicle)
        Surfaces.update(app.context)
    }

    fun deleteVehicle(vehicle: VehicleEntity) = viewModelScope.launch { app.vehicles.delete(vehicle) }

    fun setAppearance(mode: ThemeMode, dynamicColor: Boolean, pureBlack: Boolean) = viewModelScope.launch {
        app.settings.setAppearance(mode, dynamicColor, pureBlack)
        MainActivity.applyNightMode(app.context, app.settings.current())
    }

    fun setNavApp(navApp: NavApp) = viewModelScope.launch { app.settings.setNavApp(navApp) }

    fun setCarAlert(enabled: Boolean, device: PairedDevice?) = viewModelScope.launch {
        val current = app.settings.current()
        app.settings.setCarAlert(
            enabled = enabled,
            address = device?.address ?: current.carDeviceAddress,
            name = device?.name ?: current.carDeviceName,
        )
    }

    fun setWeeklyRecap(enabled: Boolean) = viewModelScope.launch {
        app.settings.setWeeklyRecap(enabled)
        WeeklyRecapWorker.schedule(app.context, enabled)
    }

    fun setAlerts(enabled: Boolean, threshold: Double?, dropCents: Double) = viewModelScope.launch {
        app.settings.setAlerts(enabled, threshold, dropCents)
    }
}
