package com.karelisio.reserve.ui.charging

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.charging.ChargingStation
import com.karelisio.reserve.data.charging.Plug
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.ui.components.InfoBanner
import com.karelisio.reserve.ui.components.Tag
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.EmptyState
import com.karelisio.reserve.ui.map.MapMarker
import com.karelisio.reserve.ui.map.StationMap
import com.karelisio.reserve.ui.station.NavAppSheet
import com.karelisio.reserve.ui.station.Navigator
import com.karelisio.reserve.ui.theme.PriceColors

@Composable
fun ChargingScreen(vm: ChargingViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var navigateTo by remember { mutableStateOf<ChargingStation?>(null) }

    fun go(station: ChargingStation, app: NavApp) {
        haptics.confirm()
        if (!Navigator.navigate(context, station.position.lat, station.position.lon, station.name, app)) {
            Toast.makeText(context, "Aucune application de navigation trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Bornes de recharge") },
                subtitle = { Text("${state.stations.size} autour de vous · ${state.radiusKm} km") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = { haptics.confirm(); vm.refresh() }) { Icon(Icons.Rounded.Refresh, "Actualiser") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.filters.fastOnly,
                        onClick = { haptics.tick(); vm.setFastOnly(!state.filters.fastOnly) },
                        label = { Text("Rapide ≥ 50 kW") },
                        leadingIcon = { Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp)) },
                    )
                    Plug.entries.filter { it != Plug.DOMESTIC }.forEach { plug ->
                        FilterChip(
                            selected = plug in state.filters.plugs,
                            onClick = { haptics.tick(); vm.togglePlug(plug) },
                            label = { Text(plug.label) },
                        )
                    }
                    FilterChip(
                        selected = state.filters.freeOnly,
                        onClick = { haptics.tick(); vm.setFreeOnly(!state.filters.freeOnly) },
                        label = { Text("Gratuit") },
                    )
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    InfoBanner(
                        error, icon = Icons.Rounded.CloudOff, container = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
            if (!state.loading && state.result != null) {
                item(key = "map") {
                    val markers = rememberChargingMarkers(state.stations)
                    StationMap(
                        markers = markers,
                        userLocation = state.origin,
                        selectedId = selectedId?.let { id -> state.stations.indexOfFirst { it.id == id }.toLong() },
                        onMarkerClick = { index -> selectedId = state.stations.getOrNull(index.toInt())?.id },
                        onMapClick = { selectedId = null },
                        cameraKey = state.result,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
                if (state.stations.isEmpty()) {
                    item(key = "empty") { EmptyState("Aucune borne ne correspond à ces critères dans un rayon de ${state.radiusKm} km.") }
                }
                val ordered = state.stations.sortedByDescending { it.id == selectedId }
                items(ordered, key = { it.id }) { station ->
                    ChargingCard(
                        station = station,
                        distanceKm = state.origin?.let { Geo.distanceKm(it, station.position) },
                        selected = station.id == selectedId,
                        onGo = {
                            if (state.navApp == NavApp.ASK) navigateTo = station else go(station, state.navApp)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
                item(key = "source") {
                    Text(
                        state.result?.source?.mention.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    navigateTo?.let { target ->
        NavAppSheet(onPick = { app -> navigateTo = null; go(target, app) }, onDismiss = { navigateTo = null })
    }
}

/** Markers labelled with the power; the faster, the greener. Ids are list positions. */
@Composable
private fun rememberChargingMarkers(stations: List<ChargingStation>): List<MapMarker> {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val dark = com.karelisio.reserve.ui.theme.isDarkColors
    return remember(stations, primary, dark) {
        stations.mapIndexed { index, s ->
            val power = s.maxPowerKw
            val color = if (power == null) outline
            else PriceColors.scale(1f - (power / 150.0).toFloat().coerceIn(0f, 1f), primary, dark)
            MapMarker(
                id = index.toLong(),
                position = s.position,
                label = power?.let { "${Format.number(it, 0)} kW" } ?: "?",
                color = color.toArgb(),
                textColor = PriceColors.onColor(color).toArgb(),
                rank = -(power ?: 0.0).toInt(),
                showLabel = index < 60,
            )
        }
    }
}

@Composable
private fun ChargingCard(
    station: ChargingStation,
    distanceKm: Double?,
    selected: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(colors.tertiaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Bolt, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(18.dp))
                    Text(
                        station.maxPowerKw?.let { "${Format.number(it, 0)}" } ?: "?",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = colors.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(station.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOfNotNull(station.operator, station.address).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        distanceKm?.let { Format.distance(it) },
                        station.maxPowerKw?.let { "jusqu'à ${Format.number(it, 0)} kW" },
                        station.points?.let { "$it point${if (it > 1) "s" else ""}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    station.plugs.forEach { Tag(it.label, colors.secondaryContainer, colors.onSecondaryContainer) }
                    when (station.free) {
                        true -> Tag("Gratuit", colors.primaryContainer, colors.onPrimaryContainer)
                        false -> Tag("Payant", colors.surfaceContainerHighest, colors.onSurface)
                        null -> Unit
                    }
                    if (station.open24) Tag("24/24", colors.surfaceContainerHighest, colors.onSurface)
                }
                station.pricing?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalIconButton(onClick = onGo) { Icon(Icons.Rounded.Directions, contentDescription = "Y aller") }
        }
    }
}
