package com.karelisio.reserve.ui.fuellog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.rememberHaptics

/** Quick entry of a fill-up: liters, price and odometer. */
@Composable
fun FillUpSheet(
    draft: FillUpDraft,
    lastOdometer: Int?,
    onSave: (FillUpDraft, liters: Double, price: Double, odometer: Int?, fullTank: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var label by rememberSaveable { mutableStateOf(draft.stationLabel) }
    var fuel by rememberSaveable { mutableStateOf(draft.fuel) }
    var liters by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf(draft.pricePerLiter?.let { Format.number(it, 3) }.orEmpty()) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var fullTank by rememberSaveable { mutableStateOf(true) }
    val litersValue = Format.parseDecimal(liters)?.takeIf { it in 0.5..200.0 }
    val priceValue = Format.parseDecimal(price)?.takeIf { it in 0.3..5.0 }
    val odometerValue = odometer.filter { it.isDigit() }.toIntOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (draft.stationId != null) {
                    t("Plein chez ${draft.stationLabel}", "Fill-up at ${draft.stationLabel}")
                } else {
                    t("Nouveau plein", "New fill-up")
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (draft.stationId == null) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Station") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
            }
            ConnectedChoice(
                options = Fuel.selectableEntries,
                selected = fuel,
                label = { if (it == Fuel.E10) "E10" else it.label },
                onSelect = { fuel = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = liters, onValueChange = { liters = it },
                    label = { Text("Litres") }, suffix = { Text("L") }, singleLine = true,
                    isError = liters.isNotBlank() && litersValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text(t("Prix", "Price")) }, suffix = { Text("€/L") }, singleLine = true,
                    isError = price.isNotBlank() && priceValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = odometer, onValueChange = { odometer = it.filter { c -> c.isDigit() }.take(7) },
                label = { Text(t("Kilométrage (optionnel)", "Odometer (optional)")) }, suffix = { Text("km") }, singleLine = true,
                supportingText = lastOdometer?.let { { Text(t("Dernier relevé : $it km", "Last reading: $it km")) } },
                isError = odometerValue != null && lastOdometer != null && odometerValue < lastOdometer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Plein complet", "Full tank"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        t("Nécessaire pour calculer la consommation réelle", "Needed to work out real consumption"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = fullTank, onCheckedChange = { haptics.toggle(it); fullTank = it })
            }
            if (litersValue != null && priceValue != null) {
                Text(
                    t("Total : ${Format.euros(litersValue * priceValue)}", "Total: ${Format.euros(litersValue * priceValue)}"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSkip?.invoke() ?: onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(if (onSkip != null) t("Pas de plein", "No fill-up") else t("Annuler", "Cancel"))
                }
                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(draft.copy(stationLabel = label, fuel = fuel), litersValue!!, priceValue!!, odometerValue, fullTank)
                    },
                    enabled = litersValue != null && priceValue != null,
                    modifier = Modifier.weight(1f),
                ) { Text(t("Enregistrer", "Save")) }
            }
        }
    }
}
