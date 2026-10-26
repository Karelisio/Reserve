package com.karelisio.reserve.ui.station

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Lang
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.PriceBadge
import com.karelisio.reserve.ui.components.SourceFooter
import com.karelisio.reserve.ui.components.Sparkline
import com.karelisio.reserve.ui.components.Tag
import com.karelisio.reserve.ui.components.rememberHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

@Composable
fun StationScreen(vm: StationViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var askNavApp by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun go(app: NavApp) {
        val station = state.station ?: return
        haptics.confirm()
        if (Navigator.navigate(context, station, app)) vm.onNavigate()
        else Toast.makeText(context, t("Aucune application de navigation trouvée", "No navigation app found"), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(state.station?.displayName ?: "Station", maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                subtitle = { state.station?.let { Text(it.fullAddress, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = t("Retour", "Back")) }
                },
                actions = {
                    state.station?.let { station ->
                        IconButton(onClick = {
                            haptics.tick()
                            context.startActivity(shareIntent(station, state.settings.fuel))
                        }) { Icon(Icons.Rounded.Share, contentDescription = t("Partager", "Share")) }
                    }
                    IconToggleButton(
                        checked = state.isFavorite,
                        onCheckedChange = { haptics.toggle(it); vm.toggleFavorite() },
                        enabled = state.station != null,
                    ) {
                        AnimatedContent(state.isFavorite, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "fav") { fav ->
                            Icon(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (fav) t("Retirer des favoris", "Remove from favourites") else t("Ajouter aux favoris", "Add to favourites"),
                                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.station != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        when (state.settings.navApp) {
                            NavApp.ASK -> { haptics.tick(); askNavApp = true }
                            else -> go(state.settings.navApp)
                        }
                    },
                    icon = { Icon(Icons.Rounded.Directions, null) },
                    text = { Text(t("Y aller", "Go there")) },
                )
            }
        },
    ) { padding ->
        val station = state.station
        when {
            station == null && state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            station == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(t("Station introuvable", "Station not found"), style = MaterialTheme.typography.bodyLarge)
            }
            else -> StationContent(
                station = station,
                state = state,
                onHistoryFuel = vm::selectHistoryFuel,
                padding = padding,
            )
        }
    }

    if (askNavApp) {
        NavAppSheet(
            onPick = { app -> askNavApp = false; go(app) },
            onDismiss = { askNavApp = false },
        )
    }
}

@Composable
private fun StationContent(
    station: Station,
    state: StationUiState,
    onHistoryFuel: (Fuel) -> Unit,
    padding: PaddingValues,
) {
    Column(
        Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.ranked?.let { ranked -> SummaryCard(ranked, state) }
        Section(t("Prix", "Prices")) { PricesTable(station, state.settings.fuel) }
        Section(t("Historique", "History")) {
            Column(Modifier.animateContentSize()) {
                val fuels = station.prices.map { it.fuel }.sortedBy { it.ordinal }
                if (fuels.size > 1) {
                    ConnectedChoice(
                        options = fuels.take(4),
                        selected = state.historyFuel.takeIf { it in fuels } ?: fuels.first(),
                        label = { it.label },
                        onSelect = onHistoryFuel,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                val points = state.history.map { it.updatedAt to it.price }
                when {
                    !state.isFavorite && points.size < 2 -> Text(
                        t(
                            "Ajoutez cette station aux favoris pour suivre l'évolution de ses prix.",
                            "Add this station to your favourites to track its prices over time.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    points.isEmpty() -> Text(t("Pas encore d'historique pour ce carburant.", "No history yet for this fuel."), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        Sparkline(points, Modifier.fillMaxWidth().height(96.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                "Min ${Format.price(points.minOf { it.second })}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text("Max ${Format.price(points.maxOf { it.second })}", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            t(
                                "${points.size} relevé${if (points.size > 1) "s" else ""} depuis le ${Format.date(points.first().first)}",
                                "${points.size} reading${if (points.size > 1) "s" else ""} since ${Format.date(points.first().first)}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Section(t("Horaires", "Opening hours")) { OpeningHours(station) }
        if (station.services.isNotEmpty()) {
            Section("Services") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    station.services.sorted().forEach { service ->
                        Tag(service, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        if (station.isHighway) {
            Text(t("Station sur autoroute", "Motorway station"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SourceFooter()
    }
}

@Composable
private fun SummaryCard(ranked: com.karelisio.reserve.domain.RankedStation, state: StationUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            PriceBadge(ranked.effectivePrice, MaterialTheme.colorScheme.primary, highlighted = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${state.settings.fuel.label} · ${Format.distance(ranked.distanceKm)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    t(
                        "Plein de ${Format.number(state.settings.fillLiters, 0)} L : ${Format.euros(ranked.totalCost)}",
                        "${Format.number(state.settings.fillLiters, 0)} L fill-up: ${Format.euros(ranked.totalCost)}",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    t(
                        "Détour aller-retour inclus (${Format.distance(ranked.detourKm)})",
                        "Round-trip detour included (${Format.distance(ranked.detourKm)})",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                state.national[ranked.price.fuel]?.let { avg ->
                    val diff = ranked.effectivePrice - avg
                    Text(
                        if (diff <= 0) {
                            t(
                                "${Format.cents(-diff)} sous la moyenne nationale (${Format.price(avg)})",
                                "${Format.cents(-diff)} below the national average (${Format.price(avg)})",
                            )
                        } else {
                            t(
                                "${Format.cents(diff)} au-dessus de la moyenne nationale (${Format.price(avg)})",
                                "${Format.cents(diff)} above the national average (${Format.price(avg)})",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (ranked.discount > 0) {
                    Text(
                        t(
                            "Remise fidélité −${Format.cents(ranked.discount)}/L incluse",
                            "Loyalty discount −${Format.cents(ranked.discount)}/L included",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PricesTable(station: Station, selected: Fuel) {
    val now = System.currentTimeMillis()
    val rows = Fuel.entries.mapNotNull { fuel ->
        val price = station.priceOf(fuel)
        val out = station.isOutOf(fuel)
        if (price == null && !out) null else Triple(fuel, price, out)
    }
    if (rows.isEmpty()) {
        Text(t("Aucun prix publié.", "No prices published."), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        rows.forEachIndexed { index, (fuel, price, out) ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fuel.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (fuel == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (price != null) {
                        Text(
                            t("Mis à jour ${Format.age(price.updatedAt)}", "Updated ${Format.age(price.updatedAt)}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    out -> Tag(t("Rupture", "Out of stock"), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, icon = Icons.Rounded.Block)
                    price != null && now - price.updatedAt > Pricing.STALE_AFTER_MS -> Tag(
                        t("Ancien", "Outdated"), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                        icon = Icons.Rounded.HourglassBottom,
                    )
                    else -> Unit
                }
                if (price != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Format.price(price.price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fuel == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Day name in the app language, capitalised ("Lundi", "Monday"); [day] is 1 (Monday) to 7. */
private fun dayName(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Lang.locale).replaceFirstChar { it.titlecase(Lang.locale) }

/** "Ouvert", "Fermé" or null when unknown, from the published opening hours. */
fun openNow(station: Station, date: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()): Boolean? {
    val today = station.hours.firstOrNull { it.day == date.dayOfWeek.value } ?: return null
    if (today.closed) return false
    if (today.ranges.isEmpty()) return null
    return today.ranges.any { range ->
        val parts = range.split('–')
        val open = parts.getOrNull(0)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@any false
        val close = parts.getOrNull(1)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@any false
        if (close <= open) time >= open || time < close else time >= open && time < close
    }
}

@Composable
private fun OpeningHours(station: Station) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (station.automate24) {
                Tag(t("Automate CB 24/24", "24/7 card pump"), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, icon = Icons.Rounded.AccessTime)
            }
            when (openNow(station)) {
                true -> Tag(t("Ouvert", "Open"), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                false -> Tag(t("Fermé", "Closed"), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                null -> Unit
            }
        }
        if (station.hours.isEmpty()) {
            Text(
                if (station.automate24) {
                    t("Horaires de la boutique non publiés.", "Shop opening hours not published.")
                } else {
                    t("Horaires non publiés.", "Opening hours not published.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val today = LocalDate.now().dayOfWeek.value
        station.hours.forEach { day ->
            val isToday = day.day == today
            Row {
                Text(
                    dayName(day.day),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        day.closed -> t("Fermé", "Closed")
                        day.ranges.isEmpty() -> "—"
                        else -> day.ranges.joinToString("  ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Plain text: prices, address and a map link (OpenStreetMap, no account needed). */
private fun shareIntent(station: Station, fuel: Fuel): android.content.Intent {
    val price = station.priceOf(fuel)
    val text = buildString {
        append(station.displayName)
        price?.let {
            append(
                t(
                    " : ${fuel.label} à ${Format.price(it.price)} (${Format.age(it.updatedAt)})",
                    ": ${fuel.label} at ${Format.price(it.price)} (${Format.age(it.updatedAt)})",
                ),
            )
        }
        val others = station.prices.filter { it.fuel != fuel }.joinToString(", ") { "${it.fuel.label} ${Format.price(it.price)}" }
        if (others.isNotEmpty()) append("\n$others")
        append("\n${station.fullAddress}")
        append(String.format(java.util.Locale.ROOT, "\nhttps://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=17/%.5f/%.5f", station.lat, station.lon, station.lat, station.lon))
        append("\n— via Reserve")
    }
    return android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text),
        t("Partager la station", "Share station"),
    )
}
