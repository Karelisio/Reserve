package com.karelisio.reserve.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.HomeScreen
import com.karelisio.reserve.ui.home.HomeViewModel
import com.karelisio.reserve.ui.map.MapScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object MapRoute

private data class TopLevel(val route: Any, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeRoute, "Autour", Icons.AutoMirrored.Rounded.FormatListBulleted),
    TopLevel(MapRoute, "Carte", Icons.Rounded.Map),
)

@Composable
fun ReserveNavHost(openStationId: Long?, onStationOpened: () -> Unit) {
    val nav = rememberNavController()
    val haptics = rememberHaptics()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isTopLevel = topLevels.any { top -> destination?.hasRoute(top.route::class) == true }
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    // Shared by the list and the map.
    val homeVm = appViewModel(owner = activity) { HomeViewModel(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel && topLevels.size > 1) {
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
                HomeScreen(vm = homeVm, onOpenStation = {}, onOpenSettings = {})
            }
            composable<MapRoute> {
                MapScreen(vm = homeVm, onOpenStation = {})
            }
        }
    }
}
