package com.karelisio.reserve.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddRoad
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.rememberHaptics
import kotlin.math.roundToInt

@Composable
fun FiltersSheet(
    radiusKm: Int,
    filters: Filters,
    onRadius: (Int) -> Unit,
    onFilters: (Filters) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            val sliderState = rememberSliderState(radiusKm.toFloat(), 4, 5f..30f)
            val radius = sliderState.value
            Row(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    t("Rayon de recherche", "Search radius"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text("${radius.roundToInt()} km", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                state = sliderState,
                onValueChange = {
                    if (it.roundToInt() != sliderState.value.roundToInt()) haptics.tick()
                    sliderState.value = it
                },
                onValueChangeFinished = { onRadius(sliderState.value.roundToInt()) },
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            )
            Text(
                t("Filtres", "Filters"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
            )
            FilterRow(Icons.Rounded.CreditCard, t("24/24 avec automate CB", "24/7 with card payment"), filters.open24) {
                onFilters(filters.copy(open24 = it))
            }
            FilterRow(Icons.Rounded.LocalCarWash, t("Lavage", "Car wash"), filters.wash) { onFilters(filters.copy(wash = it)) }
            FilterRow(Icons.Rounded.Air, t("Gonflage", "Tyre inflation"), filters.inflation) { onFilters(filters.copy(inflation = it)) }
            FilterRow(Icons.Rounded.Storefront, t("Boutique", "Shop"), filters.shop) { onFilters(filters.copy(shop = it)) }
            FilterRow(Icons.Rounded.AddRoad, t("Exclure l'autoroute", "Avoid motorways"), filters.excludeHighway) {
                onFilters(filters.copy(excludeHighway = it))
            }
        }
    }
}

@Composable
private fun FilterRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    ListItem(
        checked = checked,
        onCheckedChange = { haptics.toggle(it); onChange(it) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { Text(label) }
}
