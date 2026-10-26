package com.karelisio.reserve.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.karelisio.reserve.container
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.favorites.FavoritesScreen
import com.karelisio.reserve.ui.fuellog.FillUpDraft
import com.karelisio.reserve.ui.fuellog.FillUpSheet
import com.karelisio.reserve.ui.fuellog.FuelLogScreen
import com.karelisio.reserve.ui.fuellog.FuelLogViewModel
import com.karelisio.reserve.ui.favorites.FavoritesViewModel
import com.karelisio.reserve.ui.home.HomeScreen
import com.karelisio.reserve.ui.home.HomeViewModel
import com.karelisio.reserve.ui.map.MapScreen
import com.karelisio.reserve.ui.permissions.LaunchPermissions
import com.karelisio.reserve.ui.settings.SettingsScreen
import com.karelisio.reserve.ui.settings.SettingsViewModel
import com.karelisio.reserve.ui.station.StationScreen
import com.karelisio.reserve.ui.station.StationViewModel
import com.karelisio.reserve.ui.trip.TripScreen
import com.karelisio.reserve.ui.trip.TripViewModel
import com.karelisio.reserve.ui.update.UpdateDialog
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object MapRoute
@Serializable object FavoritesRoute
@Serializable object TripRoute
@Serializable object FuelLogRoute
@Serializable object SettingsRoute
@Serializable data class StationRoute(val id: Long)

private data class TopLevel(val route: Any, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeRoute, "Autour", Icons.AutoMirrored.Rounded.FormatListBulleted),
    TopLevel(MapRoute, "Carte", Icons.Rounded.Map),
    TopLevel(TripRoute, "Trajet", Icons.Rounded.Route),
    TopLevel(FavoritesRoute, "Favoris", Icons.Rounded.Favorite),
    TopLevel(FuelLogRoute, "Carnet", Icons.Rounded.LocalGasStation),
)

@Composable
fun ReserveNavHost(openStationId: Long?, onStationOpened: () -> Unit) {
    val nav = rememberNavController()
    val haptics = rememberHaptics()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isTopLevel = topLevels.any { top -> destination?.hasRoute(top.route::class) == true }
    val activity = LocalContext.current as ComponentActivity
    // Shared by the list and the map.
    val homeVm = appViewModel(owner = activity) { HomeViewModel(it) }

    // Deep link from the widget, the tile or a notification.
    LaunchedEffect(openStationId) {
        if (openStationId != null) {
            nav.navigate(StationRoute(openStationId)) { launchSingleTop = true }
            onStationOpened()
        }
    }

    val openStation: (Long) -> Unit = { id -> nav.navigate(StationRoute(id)) }

    // Every permission is asked at launch, then the app looks for an update.
    LaunchPermissions(
        onLocationResult = homeVm::onLocationPermissionResult,
        onDone = { activity.container.updates.checkOnLaunch() },
    )
    UpdateDialog()

    // After "Y aller", offer to log the fill-up when the user comes back.
    val logVm = appViewModel(owner = activity) { FuelLogViewModel(it) }
    val pending by logVm.pending.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var promptHiddenFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = remember(lifecycleState, pending) { System.currentTimeMillis() }
    pending?.let { trip ->
        val age = now - trip.at
        when {
            age > 12 * 3600 * 1000L -> LaunchedEffect(trip) { logVm.dismissPending() }
            age >= 3 * 60 * 1000L && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && promptHiddenFor != trip.at -> FillUpSheet(
                draft = FillUpDraft(trip.stationId, trip.label, trip.fuel, trip.price.takeIf { it > 0 }),
                lastOdometer = logVm.lastOdometer(),
                onSave = { draft, liters, price, odo, full -> logVm.save(draft, liters, price, odo, full) },
                onDismiss = { promptHiddenFor = trip.at },
                onSkip = { logVm.dismissPending() },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel) {
                ShortNavigationBar {
                    topLevels.forEach { top ->
                        val selected = destination?.hasRoute(top.route::class) == true
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = {
                                haptics.tick()
                                nav.navigate(top.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
        ) {
            composable<HomeRoute> {
                HomeScreen(vm = homeVm, onOpenStation = openStation, onOpenSettings = { nav.navigate(SettingsRoute) })
            }
            composable<MapRoute> {
                MapScreen(vm = homeVm, onOpenStation = openStation)
            }
            composable<TripRoute> {
                val home by homeVm.state.collectAsStateWithLifecycle()
                TripScreen(
                    vm = appViewModel { TripViewModel(it) },
                    onOpenStation = openStation,
                    favorites = home.favorites,
                    onToggleFavorite = { homeVm.toggleFavorite(it) },
                )
            }
            composable<FuelLogRoute> {
                FuelLogScreen(vm = logVm)
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    vm = appViewModel { FavoritesViewModel(it) },
                    onOpenStation = openStation,
                    onOpenAlerts = { nav.navigate(SettingsRoute) },
                )
            }
            composable<StationRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                val id = entry.toRoute<StationRoute>().id
                StationScreen(vm = appViewModel(key = "station-$id") { StationViewModel(it, id) }, onBack = { nav.popBackStack() })
            }
            composable<SettingsRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                SettingsScreen(vm = appViewModel { SettingsViewModel(it) }, onBack = { nav.popBackStack() })
            }
        }
    }
}
