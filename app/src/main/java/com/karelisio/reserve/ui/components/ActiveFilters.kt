package com.karelisio.reserve.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.domain.t

/** Active filters as removable chips. */
@Composable
fun ActiveFilters(filters: Filters, onChange: (Filters) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val active = listOfNotNull(
        (t("24/24 CB", "24/7 card") to filters.copy(open24 = false)).takeIf { filters.open24 },
        (t("Lavage", "Car wash") to filters.copy(wash = false)).takeIf { filters.wash },
        (t("Gonflage", "Tyre inflation") to filters.copy(inflation = false)).takeIf { filters.inflation },
        (t("Boutique", "Shop") to filters.copy(shop = false)).takeIf { filters.shop },
        (t("Sans autoroute", "No motorways") to filters.copy(excludeHighway = false)).takeIf { filters.excludeHighway },
    )
    AnimatedVisibility(active.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            active.forEach { (label, without) ->
                InputChip(
                    selected = true,
                    onClick = { haptics.toggle(false); onChange(without) },
                    label = { Text(label) },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = t("Retirer le filtre", "Remove filter"), Modifier.size(InputChipDefaults.IconSize)) },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}
