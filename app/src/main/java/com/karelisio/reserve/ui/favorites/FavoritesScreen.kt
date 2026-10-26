package com.karelisio.reserve.ui.favorites

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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.FuelSelector
import com.karelisio.reserve.ui.components.InfoBanner
import com.karelisio.reserve.ui.components.Sparkline
import com.karelisio.reserve.ui.components.Tag
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.EmptyState

@Composable
fun FavoritesScreen(vm: FavoritesViewModel, onOpenStation: (Long) -> Unit, onOpenAlerts: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(t("Favoris", "Favourites")) },
                subtitle = {
                    Text(
                        t(
                            "${state.items.size} station${if (state.items.size > 1) "s" else ""} suivie${if (state.items.size > 1) "s" else ""}",
                            "Following ${state.items.size} station${if (state.items.size != 1) "s" else ""}",
                        ),
                    )
                },
                actions = {
                    IconButton(onClick = onOpenAlerts) { Icon(Icons.Rounded.NotificationsActive, contentDescription = t("Alertes de prix", "Price alerts")) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { haptics.confirm(); vm.refresh() },
            state = pullState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(pullState, state.loading, Modifier.align(Alignment.TopCenter))
            },
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "fuel") { FuelSelector(state.settings.fuel, vm::setFuel, Modifier.fillMaxWidth()) }
                if (state.offline) {
                    item(key = "offline") {
                        InfoBanner(
                            t("Hors ligne : derniers prix connus.", "Offline: last known prices."),
                            icon = Icons.Rounded.CloudOff,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (state.items.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            t(
                                "Aucun favori. Touchez le cœur d'une station pour suivre ses prix et recevoir des alertes.",
                                "No favourites yet. Tap a station's heart to follow its prices and get alerts.",
                            ),
                        )
                    }
                }
                items(state.items, key = { it.station.id }) { item ->
                    FavoriteCard(
                        item = item,
                        onClick = { onOpenStation(item.station.id) },
                        onRemove = { haptics.toggle(false); vm.remove(item.station.id) },
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoriteItem, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        item.price?.let { Format.price(it.price) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    item.price?.let {
                        Text(Format.age(it.updatedAt), style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = t("Retirer des favoris", "Remove from favourites")) }
            }
            if (item.history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    Sparkline(item.history, Modifier.weight(1f).height(40.dp))
                    item.lastChange?.let { change ->
                        Spacer(Modifier.width(12.dp))
                        val down = change < 0
                        Tag(
                            text = (if (down) "−" else "+") + Format.cents(kotlin.math.abs(change)),
                            container = if (down) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            content = if (down) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            icon = if (down) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                        )
                    }
                }
            }
            val price = item.price
            if (price != null && System.currentTimeMillis() - price.updatedAt > Pricing.STALE_AFTER_MS) {
                Text(
                    t("Prix de plus de 3 jours", "Price over 3 days old"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
