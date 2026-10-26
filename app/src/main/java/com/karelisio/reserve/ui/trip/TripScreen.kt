package com.karelisio.reserve.ui.trip

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.FuelSelector
import com.karelisio.reserve.ui.components.InfoBanner
import com.karelisio.reserve.ui.components.SourceFooter
import com.karelisio.reserve.ui.components.StationCard
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.EmptyState
import com.karelisio.reserve.ui.map.StationMap
import com.karelisio.reserve.ui.map.rememberMarkers
import com.karelisio.reserve.ui.station.Navigator
import kotlin.math.roundToInt

@Composable
fun TripScreen(vm: TripViewModel, onOpenStation: (Long) -> Unit, favorites: Set<Long>, onToggleFavorite: (com.karelisio.reserve.data.model.Station) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(t("Sur mon trajet", "On my route")) },
                subtitle = { Text(t("Stations à moins de 2 km de la route", "Stations within 2 km of the route")) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text(t("Destination (adresse, ville…)", "Destination (address, town…)")) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { haptics.tick(); vm.clear() }) { Icon(Icons.Rounded.Close, contentDescription = t("Effacer", "Clear")) }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            state.suggestions.firstOrNull()?.let { focus.clearFocus(); vm.choose(it) }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = state.suggestions.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                            state.suggestions.forEachIndexed { index, suggestion ->
                                if (index > 0) HorizontalDivider()
                                ListItem(
                                    onClick = { haptics.tick(); focus.clearFocus(); vm.choose(suggestion) },
                                    leadingContent = { Icon(Icons.Rounded.Place, null) },
                                    supportingContent = { Text(suggestion.context) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                ) { Text(suggestion.label) }
                            }
                        }
                    }
                }
            }
            item(key = "fuel") { FuelSelector(state.settings.fuel, vm::setFuel, Modifier.fillMaxWidth()) }

            state.error?.let { error ->
                item(key = "error") {
                    InfoBanner(
                        error, icon = Icons.Rounded.Warning, container = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
            val route = state.route
            if (route == null && !state.loading && state.error == null) {
                item(key = "hint") {
                    EmptyState(
                        t(
                            "Saisissez votre destination : Reserve calcule l'itinéraire et classe les stations du trajet par vrai coût, détour compris.",
                            "Enter your destination: Reserve plans the route and ranks the stations along it by real cost, detour included.",
                        ),
                    )
                }
            }
            if (route != null && !state.loading) {
                item(key = "summary") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Route, null, Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.destination?.label.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${route.distanceKm.roundToInt()} km · ${formatDuration(route.durationMin)} · " +
                                        t(
                                            "${state.stations.size} station${if (state.stations.size > 1) "s" else ""}",
                                            "${state.stations.size} station${if (state.stations.size != 1) "s" else ""}",
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                item(key = "map") {
                    val markers = rememberMarkers(state.stations.map { it.ranked }, state.stations.priceRange(), maxLabels = 30)
                    StationMap(
                        markers = markers,
                        userLocation = state.origin,
                        selectedId = state.stations.firstOrNull()?.ranked?.station?.id,
                        onMarkerClick = onOpenStation,
                        onMapClick = {},
                        route = route.points,
                        cameraKey = route,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
                if (state.stations.isEmpty()) {
                    item(key = "none") {
                        EmptyState(
                            t(
                                "Aucune station ne vend ${state.settings.fuel.label} le long de ce trajet.",
                                "No station sells ${state.settings.fuel.label} along this route.",
                            ),
                        )
                    }
                }
                val range = state.stations.priceRange()
                val bestId = state.stations.firstOrNull()?.ranked?.station?.id
                items(state.stations, key = { it.ranked.station.id }) { item ->
                    Column(Modifier.padding(horizontal = 16.dp).animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                            Icon(Icons.Rounded.Flag, null, Modifier.height(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                t(
                                    "À ${item.alongKm.roundToInt()} km du départ · ${Format.distance(item.offRouteKm)} de la route",
                                    "${item.alongKm.roundToInt()} km from the start · ${Format.distance(item.offRouteKm)} off the route",
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            state.destination?.let { destination ->
                                TextButton(onClick = {
                                    haptics.confirm()
                                    val app = state.settings.navApp
                                    if (Navigator.navigateVia(context, item.ranked.station, destination.position, app)) {
                                        vm.onNavigate(item.ranked)
                                        if (app == NavApp.WAZE) {
                                            Toast.makeText(
                                                context,
                                                t(
                                                    "Waze ne gère pas les étapes : direction la station d'abord",
                                                    "Waze doesn't support stops: heading to the station first",
                                                ),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(context, t("Aucune application de navigation trouvée", "No navigation app found"), Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.AltRoute, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(t("Via cette station", "Via this station"))
                                }
                            }
                        }
                        StationCard(
                            item = item.ranked,
                            priceRange = range,
                            isFavorite = item.ranked.station.id in favorites,
                            sort = com.karelisio.reserve.data.model.SortMode.REAL_COST,
                            isBest = item.ranked.station.id == bestId,
                            onClick = { onOpenStation(item.ranked.station.id) },
                            onToggleFavorite = { onToggleFavorite(item.ranked.station) },
                            detourLabel = t("hors trajet", "off-route"),
                        )
                    }
                }
                item(key = "footer") {
                    SourceFooter()
                    Text(
                        t(
                            "Itinéraire : OSRM, données © contributeurs OpenStreetMap. Adresses : Base Adresse Nationale.",
                            "Routing: OSRM, data © OpenStreetMap contributors. Addresses: Base Adresse Nationale.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

private fun List<TripStation>.priceRange(): ClosedFloatingPointRange<Double>? =
    filterNot { it.ranked.outOfStock }.map { it.ranked.effectivePrice }.takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }

private fun formatDuration(minutes: Double): String {
    val total = minutes.roundToInt()
    return if (total < 60) "$total min" else "${total / 60} h ${(total % 60).toString().padStart(2, '0')}"
}
