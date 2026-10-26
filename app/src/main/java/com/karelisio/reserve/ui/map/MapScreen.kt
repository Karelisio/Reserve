package com.karelisio.reserve.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.ActiveFilters
import com.karelisio.reserve.ui.components.FuelSelector
import com.karelisio.reserve.ui.components.PriceBadge
import com.karelisio.reserve.ui.components.priceColor
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.HomeViewModel
import com.karelisio.reserve.ui.station.NavAppSheet
import com.karelisio.reserve.ui.station.Navigator
import com.karelisio.reserve.ui.theme.PriceColors
import com.karelisio.reserve.ui.theme.isDarkColors

/** Builds map markers colored from cheapest (green) to most expensive (red). */
@Composable
fun rememberMarkers(stations: List<RankedStation>, range: ClosedFloatingPointRange<Double>?, maxLabels: Int = 60): List<MapMarker> {
    val outOfStock = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val dark = isDarkColors
    // Recomputed only when the stations or the theme change, not on every recomposition.
    return remember(stations, range, outOfStock, primary, dark) {
        stations.sortedBy { if (it.outOfStock) Double.MAX_VALUE else it.effectivePrice }.mapIndexed { rank, item ->
            val color = if (item.outOfStock) outOfStock else PriceColors.scale(fraction(item.effectivePrice, range), primary, dark)
            MapMarker(
                id = item.station.id,
                position = LatLon(item.station.lat, item.station.lon),
                label = String.format(com.karelisio.reserve.domain.Lang.locale, "%.3f", item.effectivePrice),
                color = color.toArgb(),
                textColor = PriceColors.onColor(color).toArgb(),
                rank = rank,
                showLabel = rank < maxLabels && !item.outOfStock,
            )
        }
    }
}

private fun fraction(price: Double, range: ClosedFloatingPointRange<Double>?): Float =
    if (range == null || range.endInclusive - range.start < 0.0005) 0f
    else ((price - range.start) / (range.endInclusive - range.start)).toFloat()

@Composable
fun MapScreen(vm: HomeViewModel, onOpenStation: (Long) -> Unit, onOpenCharging: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cameraKey by remember { mutableIntStateOf(0) }
    val markers = rememberMarkers(state.stations, state.priceRange)
    val selected = state.stations.firstOrNull { it.station.id == selectedId }
    val context = LocalContext.current
    var askNavApp by rememberSaveable { mutableStateOf(false) }
    if (askNavApp && selected != null) {
        NavAppSheet(
            onPick = { app ->
                askNavApp = false
                if (Navigator.navigate(context, selected.station, app)) vm.onNavigate(selected)
            },
            onDismiss = { askNavApp = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        StationMap(
            markers = markers,
            userLocation = state.origin,
            selectedId = selectedId,
            onMarkerClick = { haptics.tick(); selectedId = it },
            onMapClick = { selectedId = null },
            cameraKey = state.result?.fetchedAt?.let { it + cameraKey },
            contentPadding = PaddingValues(bottom = if (selected != null) 180.dp else 0.dp),
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
            FuelSelector(state.settings.fuel, vm::setFuel, Modifier.fillMaxWidth(), onCharging = onOpenCharging)
            ActiveFilters(state.settings.filters, vm::setFilters, Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        if (state.loading) {
            LoadingIndicator(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallFloatingActionButton(onClick = { haptics.tick(); cameraKey++ }) {
                Icon(Icons.Rounded.MyLocation, contentDescription = t("Recentrer", "Recentre map"))
            }
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                selected?.let { item ->
                    SelectedStationCard(
                        item = item,
                        range = state.priceRange,
                        onOpen = { onOpenStation(item.station.id) },
                        onGo = {
                            haptics.confirm()
                            if (state.settings.navApp == NavApp.ASK) askNavApp = true
                            else if (Navigator.navigate(context, item.station, state.settings.navApp)) vm.onNavigate(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedStationCard(
    item: RankedStation,
    range: ClosedFloatingPointRange<Double>?,
    onOpen: () -> Unit,
    onGo: () -> Unit,
) {
    ElevatedCard(onClick = onOpen, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceBadge(item.effectivePrice, priceColor(item.effectivePrice, range))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.station.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.station.fullAddress,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${Format.distance(item.distanceKm)} · ${Format.age(item.price.updatedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpen) {
                    Text(t("Voir la fiche", "View details"))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onGo) {
                    Icon(Icons.Rounded.Directions, null)
                    Spacer(Modifier.width(6.dp))
                    Text(t("Y aller", "Go there"))
                }
            }
        }
    }
}
