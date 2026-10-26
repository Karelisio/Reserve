package com.karelisio.reserve.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EuroSymbol
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.ui.components.ActiveFilters
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.FuelSelector
import com.karelisio.reserve.ui.components.InfoBanner
import com.karelisio.reserve.ui.components.SourceFooter
import com.karelisio.reserve.ui.components.StationCard
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.station.NavAppSheet
import com.karelisio.reserve.ui.station.Navigator

val LocationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenStation: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var navigateTo by remember { mutableStateOf<RankedStation?>(null) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onLocationPermissionResult(it.values.any { granted -> granted })
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Reserve") },
                subtitle = {
                    val updated = state.result?.let { " · ${Format.age(it.fetchedAt)}" }.orEmpty()
                    Text("${state.settings.fuel.label} · ${state.settings.radiusKm} km$updated")
                },
                actions = {
                    IconButton(onClick = { haptics.confirm(); vm.refresh(userInitiated = true) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Réglages") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { haptics.confirm(); vm.refresh(userInitiated = true) },
            state = pullState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val best = state.best
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "fuel") {
                    FuelSelector(state.settings.fuel, vm::setFuel, Modifier.fillMaxWidth())
                }
                item(key = "controls") {
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConnectedChoice(
                            options = SortMode.entries,
                            selected = state.settings.sort,
                            label = { it.label },
                            onSelect = vm::setSort,
                            icon = {
                                when (it) {
                                    SortMode.PRICE -> Icons.Rounded.EuroSymbol
                                    SortMode.DISTANCE -> Icons.Rounded.NearMe
                                    SortMode.REAL_COST -> Icons.Rounded.Savings
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        BadgedBox(badge = {
                            val count = state.settings.filters.activeCount
                            if (count > 0) Badge { Text("$count") }
                        }) {
                            FilledTonalIconButton(onClick = { haptics.tick(); showFilters = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = "Rayon et filtres")
                            }
                        }
                    }
                }
                item(key = "filters") {
                    ActiveFilters(state.settings.filters, vm::setFilters, Modifier.fillMaxWidth())
                }
                state.error?.let { error ->
                    item(key = "error") {
                        InfoBanner(
                            text = error,
                            icon = Icons.Rounded.CloudOff,
                            container = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                            action = { TextButton(onClick = { vm.refresh(true) }) { Text("Réessayer") } },
                        )
                    }
                }
                if (best != null) {
                    item(key = "best") {
                        BestChoiceCard(
                            item = best,
                            onOpen = { onOpenStation(best.station.id) },
                            onGo = {
                                haptics.confirm()
                                if (state.settings.navApp == NavApp.ASK) navigateTo = best
                                else if (Navigator.navigate(context, best.station, state.settings.navApp)) vm.onNavigate(best)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        )
                    }
                }
                state.trend?.let { trend ->
                    item(key = "trend") {
                        TrendCard(trend, state.settings.fuel, Modifier.padding(horizontal = 16.dp).animateItem(), national = state.national[state.settings.fuel])
                    }
                }
                if (state.needsLocationPermission) {
                    item(key = "permission") {
                        InfoBanner(
                            text = "La localisation permet de trouver les stations autour de vous.",
                            icon = Icons.Rounded.LocationOff,
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                            action = { TextButton(onClick = { permissionLauncher.launch(LocationPermissions) }) { Text("Autoriser") } },
                        )
                    }
                }
                if (state.stations.isEmpty() && !state.loading && state.result != null) {
                    item(key = "empty") {
                        EmptyState(
                            text = "Aucune station ne propose ${state.settings.fuel.label} dans un rayon de ${state.settings.radiusKm} km" +
                                if (state.settings.filters.activeCount > 0) " avec ces filtres." else ".",
                            action = if (state.settings.radiusKm < 30) {
                                { Button(onClick = { vm.setRadius((state.settings.radiusKm + 10).coerceAtMost(30)) }) { Text("Élargir le rayon") } }
                            } else null,
                        )
                    }
                }
                items(state.stations, key = { it.station.id }) { item ->
                    StationCard(
                        item = item,
                        priceRange = state.priceRange,
                        isFavorite = item.station.id in state.favorites,
                        sort = state.settings.sort,
                        isBest = item.station.id == best?.station?.id,
                        onClick = { onOpenStation(item.station.id) },
                        onToggleFavorite = { vm.toggleFavorite(item.station) },
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
                item(key = "footer") { SourceFooter() }
            }
        }
    }

    navigateTo?.let { target ->
        NavAppSheet(
            onPick = { app ->
                navigateTo = null
                if (Navigator.navigate(context, target.station, app)) vm.onNavigate(target)
            },
            onDismiss = { navigateTo = null },
        )
    }

    if (showFilters) {
        FiltersSheet(
            radiusKm = state.settings.radiusKm,
            filters = state.settings.filters,
            onRadius = vm::setRadius,
            onFilters = vm::setFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
fun EmptyState(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.LocalGasStation, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp).height(48.dp).width(48.dp),
        )
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
