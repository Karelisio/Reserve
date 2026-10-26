package com.karelisio.reserve.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.bluetooth.CarBluetooth
import com.karelisio.reserve.bluetooth.PairedDevice
import com.karelisio.reserve.container
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.rememberHaptics

@Composable
fun CarAlertSection(settings: Settings, onChange: (enabled: Boolean, device: PairedDevice?) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var picking by rememberSaveable { mutableStateOf(false) }
    var backgroundGranted by remember { mutableStateOf(context.container.location.hasBackgroundPermission()) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (CarBluetooth.hasPermission(context)) picking = true
        else if (result.isNotEmpty()) onChange(false, null)
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        backgroundGranted = it
    }

    fun requestAndPick() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty() || CarBluetooth.hasPermission(context) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) picking = true
        else permissions.launch(needed.toTypedArray())
    }

    SettingsSection(t("Alerte en voiture", "In-car alert")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Prévenir en montant en voiture", "Notify me when I get in the car"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    t(
                        "À la connexion Bluetooth de la voiture : « Moins cher à X km : 1,72 € » (une fois par heure au plus).",
                        "When the car's Bluetooth connects: “Cheaper X km away: €1.72” (at most once an hour).",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.carAlertEnabled,
                onCheckedChange = { enabled ->
                    haptics.toggle(enabled)
                    if (!enabled) onChange(false, null)
                    else requestAndPick()
                },
            )
        }
        AnimatedVisibility(settings.carAlertEnabled) {
            Column {
                Spacer(Modifier.height(12.dp))
                ListItem(
                    onClick = { requestAndPick() },
                    leadingContent = { Icon(Icons.Rounded.DirectionsCar, null) },
                    supportingContent = { Text(t("Appareil Bluetooth de la voiture", "Car Bluetooth device")) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) { Text(settings.carDeviceName ?: t("Choisir ma voiture", "Choose my car")) }
                if (!backgroundGranted && Build.VERSION.SDK_INT >= 29) {
                    Text(
                        t(
                            "Pour utiliser la position exacte au démarrage, autorisez la localisation « Toujours ». " +
                                "Sinon, la dernière position connue est utilisée.",
                            "To use your exact position when you set off, allow location “All the time”. " +
                                "Otherwise, the last known position is used.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                        Text(t("Autoriser en arrière-plan", "Allow in background"))
                    }
                }
            }
        }
    }

    if (picking) {
        val devices = remember { CarBluetooth.pairedDevices(context) }
        AlertDialog(
            onDismissRequest = { picking = false },
            icon = { Icon(Icons.Rounded.Bluetooth, null) },
            title = { Text(t("Ma voiture", "My car")) },
            text = {
                if (devices.isEmpty()) {
                    Text(t("Aucun appareil Bluetooth associé. Associez d'abord le téléphone à la voiture.", "No paired Bluetooth devices. Pair your phone with the car first."))
                } else {
                    LazyColumn {
                        items(devices, key = { it.address }) { device ->
                            ListItem(
                                onClick = {
                                    haptics.confirm()
                                    picking = false
                                    onChange(true, device)
                                },
                                supportingContent = { Text(device.address) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            ) { Text(device.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(t("Fermer", "Close")) } },
        )
    }
}
