package com.karelisio.reserve.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.local.DiscountUnit
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.domain.Brand
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.rememberHaptics

fun LoyaltyCardEntity.describe(): String = when (unit) {
    DiscountUnit.CENTS_PER_LITER -> "−${Format.number(discount, if (discount % 1.0 == 0.0) 0 else 1)} c/L"
    DiscountUnit.PERCENT -> "−${Format.number(discount, if (discount % 1.0 == 0.0) 0 else 1)} %"
}

@Composable
fun LoyaltySection(
    cards: List<LoyaltyCardEntity>,
    onAdd: (Brand, Double, DiscountUnit) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val haptics = rememberHaptics()
    var adding by rememberSaveable { mutableStateOf(false) }
    SettingsSection(t("Remises fidélité", "Loyalty discounts")) {
        Text(
            t(
                "Déclarez vos cartes : les prix affichés et le vrai coût tiennent compte de la remise dans les stations de l'enseigne.",
                "Add your cards: displayed prices and the true cost include the discount at that brand's stations.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        cards.forEach { card ->
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CardMembership, null) },
                trailingContent = {
                    IconButton(onClick = { haptics.toggle(false); onDelete(card.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = t("Supprimer la carte", "Delete card"))
                    }
                },
                supportingContent = { Text(card.describe()) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(Brand.fromKey(card.brandKey)?.label ?: card.brandKey) }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { haptics.tick(); adding = true }) {
            Icon(Icons.Rounded.Add, null)
            Text(t("  Ajouter une carte", "  Add a card"))
        }
    }

    if (adding) {
        AddCardDialog(
            onAdd = { brand, value, unit -> adding = false; haptics.confirm(); onAdd(brand, value, unit) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun AddCardDialog(onAdd: (Brand, Double, DiscountUnit) -> Unit, onDismiss: () -> Unit) {
    var brand by rememberSaveable { mutableStateOf(Brand.LECLERC) }
    var unit by rememberSaveable { mutableStateOf(DiscountUnit.CENTS_PER_LITER) }
    var value by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val parsed = Format.parseDecimal(value)?.takeIf {
        it > 0 && (unit == DiscountUnit.PERCENT && it <= 30 || unit == DiscountUnit.CENTS_PER_LITER && it <= 50)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CardMembership, null) },
        title = { Text(t("Carte de fidélité", "Loyalty card")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = brand.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(t("Enseigne", "Brand")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Brand.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { brand = option; expanded = false })
                        }
                    }
                }
                ConnectedChoice(
                    options = DiscountUnit.entries,
                    selected = unit,
                    label = { if (it == DiscountUnit.CENTS_PER_LITER) t("cts / litre", "c / litre") else "%" },
                    onSelect = { unit = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(t("Remise", "Discount")) },
                    suffix = { Text(if (unit == DiscountUnit.CENTS_PER_LITER) "c/L" else "%") },
                    singleLine = true,
                    isError = value.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onAdd(brand, it, unit) } }, enabled = parsed != null) { Text(t("Ajouter", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler", "Cancel")) } },
    )
}
