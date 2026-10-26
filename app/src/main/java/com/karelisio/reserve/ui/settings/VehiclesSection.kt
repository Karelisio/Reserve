package com.karelisio.reserve.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.VehicleRepository
import com.karelisio.reserve.data.local.VehicleEntity
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.rememberHaptics

@Composable
fun VehiclesSection(
    vehicles: List<VehicleEntity>,
    activeId: Long?,
    onActivate: (VehicleEntity) -> Unit,
    onSave: (VehicleEntity) -> Unit,
    onDelete: (VehicleEntity) -> Unit,
) {
    val haptics = rememberHaptics()
    var editing by remember { mutableStateOf<VehicleEntity?>(null) }
    SettingsSection("Véhicules") {
        Text(
            "Le véhicule actif fixe le carburant, la consommation (vrai coût) et le réservoir (autonomie estimée).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vehicles.forEach { vehicle ->
            ListItem(
                onClick = { haptics.tick(); onActivate(vehicle) },
                leadingContent = { RadioButton(selected = vehicle.id == activeId, onClick = null) },
                supportingContent = {
                    Text(
                        "${vehicle.fuel.label} · ${Format.number(vehicle.consumption)} L/100 · réservoir ${Format.number(vehicle.tankCapacity, 0)} L",
                    )
                },
                trailingContent = {
                    IconButton(onClick = { editing = vehicle }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifier ${vehicle.name}") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(vehicle.name) }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = {
            haptics.tick()
            editing = VehicleEntity(name = "", fuel = Fuel.GAZOLE, consumption = 6.5, fillLiters = 40.0, tankCapacity = VehicleRepository.DEFAULT_TANK)
        }) {
            Icon(Icons.Rounded.Add, null)
            Text("  Ajouter un véhicule")
        }
    }

    editing?.let { vehicle ->
        VehicleDialog(
            vehicle = vehicle,
            canDelete = vehicle.id != 0L && vehicles.size > 1,
            onSave = { haptics.confirm(); onSave(it); editing = null },
            onDelete = { onDelete(vehicle); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun VehicleDialog(
    vehicle: VehicleEntity,
    canDelete: Boolean,
    onSave: (VehicleEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(vehicle.name) }
    var fuel by remember { mutableStateOf(vehicle.fuel) }
    var consumption by remember { mutableStateOf(Format.number(vehicle.consumption)) }
    var fill by remember { mutableStateOf(Format.number(vehicle.fillLiters, 0)) }
    var tank by remember { mutableStateOf(Format.number(vehicle.tankCapacity, 0)) }
    val c = Format.parseDecimal(consumption)?.takeIf { it in 2.0..30.0 }
    val f = Format.parseDecimal(fill)?.takeIf { it in 5.0..150.0 }
    val t = Format.parseDecimal(tank)?.takeIf { it in 10.0..200.0 }
    val valid = name.isNotBlank() && c != null && f != null && t != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DirectionsCar, null) },
        title = { Text(if (vehicle.id == 0L) "Nouveau véhicule" else "Modifier le véhicule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ConnectedChoice(
                    options = Fuel.selectableEntries,
                    selected = fuel,
                    label = { if (it == Fuel.E10) "E10" else it.label },
                    onSelect = { fuel = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Conso", "L/100", consumption, { consumption = it }, c == null, Modifier.weight(1f))
                    NumberField("Plein", "L", fill, { fill = it }, f == null, Modifier.weight(1f))
                }
                NumberField("Réservoir", "L", tank, { tank = it }, t == null, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(vehicle.copy(name = name.trim(), fuel = fuel, consumption = c!!, fillLiters = f!!, tankCapacity = t!!))
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            if (canDelete) TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun NumberField(label: String, suffix: String, value: String, onValue: (String) -> Unit, error: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = error && value.isNotBlank(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
