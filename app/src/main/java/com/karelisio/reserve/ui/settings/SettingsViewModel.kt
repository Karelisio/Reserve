package com.karelisio.reserve.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.prefs.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVehicle(consumption: Double, liters: Double) = viewModelScope.launch {
        app.settings.setVehicle(consumption, liters)
    }

    fun setNavApp(navApp: NavApp) = viewModelScope.launch { app.settings.setNavApp(navApp) }
}
