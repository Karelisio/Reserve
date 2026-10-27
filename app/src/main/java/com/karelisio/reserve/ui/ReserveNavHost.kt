package com.karelisio.reserve.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.karelisio.reserve.Shortcuts
import com.karelisio.reserve.container
import com.karelisio.reserve.diagnostics.PendingCrashDialog
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.charging.ChargingScreen
import com.karelisio.reserve.ui.charging.ChargingViewModel
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.favorites.FavoritesScreen
import com.karelisio.reserve.ui.favorites.FavoritesViewModel
import com.karelisio.reserve.ui.fuellog.FillUpDraft
import com.karelisio.reserve.ui.fuellog.FillUpSheet
import com.karelisio.reserve.ui.fuellog.FuelLogScreen
import com.karelisio.reserve.ui.fuellog.FuelLogViewModel
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
@Serializable object ChargingRoute
@Serializable data class StationRoute(val id: Long)

/** [label] is read when displayed, so it follows the app language. */
private data class TopLevel(val route: Any, val label: () -> String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeRoute, { t("Autour", "Nearby") }, Icons.AutoMirrored.Rounded.FormatListBulleted),
    TopLevel(MapRoute, { t("Carte", "Map") }, Icons.Rounded.Map),
    TopLevel(TripRoute, { t("Trajet", "Trip") }, Icons.Rounded.Route),
    TopLevel(FavoritesRoute, { t("Favoris", "Favourites") }, Icons.Rounded.Favorite),
    TopLevel(FuelLogRoute, { t("Carnet", "Log") }, Icons.Rounded.LocalGasStation),
)

@Composable
fun ReserveNavHost(
    openStationId: Long?,
    onStationOpened: () -> Unit,
    openDestination: String? = null,
    onDestinationOpened: () -> Unit = {},
) {
    val nav = rememberNavController()
    val haptics = rememberHaptics()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isTopLevel = topLevels.any { top -> destination?.hasRoute(top.route::class) == true }
    val activity = LocalActivity.current as ComponentActivity
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

    // App shortcuts (long press on the icon).
    var addFillUp by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openDestination) {
        val route: Any = when (openDestination) {
            Shortcuts.AROUND -> HomeRoute
            Shortcuts.MAP -> MapRoute
            Shortcuts.TRIP -> TripRoute
            Shortcuts.FILL_UP -> FuelLogRoute.also { addFillUp = true }
            else -> return@LaunchedEffect
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
        onDestinationOpened()
    }

    // Every permission is asked at launch, then the app looks for an update.
    LaunchPermissions(
        onLocationResult = homeVm::onLocationPermissionResult,
        onDone = { activity.container.updates.checkOnLaunch() },
    )
    UpdateDialog()
    PendingCrashDialog()

    // Position followed while on screen; new prices at launch and once the user has moved.
    LifecycleResumeEffect(homeVm) {
        homeVm.onResume()
        onPauseOrDispose { homeVm.stopTracking() }
    }

    // After "Y aller", offer to log the fill-up when the user comes back.
    val logVm = appViewModel(owner = activity) { FuelLogViewModel(it) }
    val pending by logVm.pending.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val now = remember(lifecycleState, pending) { System.currentTimeMillis() }
    pending?.let { trip ->
        val age = now - trip.at
        when {
            age > 12 * 3600 * 1000L -> LaunchedEffect(trip) { logVm.dismissPending() }
            age >= 3 * 60 * 1000L && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) -> FillUpSheet(
                draft = FillUpDraft(trip.stationId, trip.label, trip.fuel, trip.price.takeIf { it > 0 }),
                lastOdometer = logVm.lastOdometer(),
                onSave = { draft, liters, price, odo, full -> logVm.save(draft, liters, price, odo, full) },
                // Asked once: closing the sheet counts as "no fill-up".
                onDismiss = { logVm.dismissPending() },
                onSkip = { logVm.dismissPending() },
            )
        }
    }

    // Bottom bar on phones, navigation rail on tablets and unfolded foldables.
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        layoutType = if (isTopLevel) adaptiveLayout else NavigationSuiteType.None,
        navigationSuiteItems = {
            topLevels.forEach { top ->
                item(
                    selected = destination?.hasRoute(top.route::class) == true,
                    onClick = {
                        haptics.tick()
                        nav.navigate(top.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(top.icon, null) },
                    label = { Text(top.label()) },
                )
            }
        },
    ) {
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
        ) {
            composable<HomeRoute> {
                ListDetail(openStation) { onOpen ->
                HomeScreen(
                    vm = homeVm,
                    onOpenStation = onOpen,
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onOpenCharging = { nav.navigate(ChargingRoute) },
                )
                }
            }
            composable<MapRoute> {
                MapScreen(vm = homeVm, onOpenStation = openStation, onOpenCharging = { nav.navigate(ChargingRoute) })
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
                FuelLogScreen(vm = logVm, startAdding = addFillUp, onStartAddingHandled = { addFillUp = false })
            }
            composable<FavoritesRoute> {
                val favoritesVm = appViewModel { FavoritesViewModel(it) }
                ListDetail(openStation) { onOpen ->
                FavoritesScreen(
                    vm = favoritesVm,
                    onOpenStation = onOpen,
                    onOpenAlerts = { nav.navigate(SettingsRoute) },
                )
                }
            }
            composable<StationRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                val id = entry.toRoute<StationRoute>().id
                StationScreen(vm = appViewModel(key = "station-$id") { StationViewModel(it, id) }, onBack = { nav.popBackStack() })
            }
            composable<ChargingRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                ChargingScreen(vm = appViewModel { ChargingViewModel(it) }, onBack = { nav.popBackStack() })
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

/**
 * On wide screens (840 dp and more), the station opens next to the list instead of on
 * top of it; on phones, [openStation] navigates as usual.
 */
@Composable
private fun ListDetail(openStation: (Long) -> Unit, list: @Composable (onOpen: (Long) -> Unit) -> Unit) {
    BoxWithConstraints {
        if (maxWidth < 840.dp) {
            list(openStation)
            return@BoxWithConstraints
        }
        var selected by rememberSaveable { mutableStateOf<Long?>(null) }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.45f)) { list { selected = it } }
            VerticalDivider()
            Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) {
                val id = selected
                if (id == null) {
                    Text(
                        t("Choisissez une station pour voir sa fiche", "Choose a station to see its details"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    StationScreen(vm = appViewModel(key = "station-$id") { StationViewModel(it, id) }, onBack = { selected = null })
                }
            }
        }
    }
}
