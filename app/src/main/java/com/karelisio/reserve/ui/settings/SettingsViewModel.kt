package com.karelisio.reserve.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.bluetooth.PairedDevice
import com.karelisio.reserve.data.local.DiscountUnit
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.domain.Brand
import com.karelisio.reserve.work.Surfaces
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.prefs.Settings
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

    fun setVehicle(consumption: Double, liters: Double) = viewModelScope.launch {
        app.settings.setVehicle(consumption, liters)
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

    fun setAlerts(enabled: Boolean, threshold: Double?, dropCents: Double) = viewModelScope.launch {
        app.settings.setAlerts(enabled, threshold, dropCents)
    }
}
