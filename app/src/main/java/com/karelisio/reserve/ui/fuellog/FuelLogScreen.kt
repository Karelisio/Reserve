package com.karelisio.reserve.ui.fuellog

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.container
import com.karelisio.reserve.data.local.FillUpEntity
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.FuelLogStats
import com.karelisio.reserve.domain.MonthSpend
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.ui.home.EmptyState
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FuelLogScreen(vm: FuelLogViewModel, startAdding: Boolean = false, onStartAddingHandled: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startAdding) {
        if (startAdding) {
            adding = true
            onStartAddingHandled()
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { context.container.backup.exportFillUpsCsv(it) }
            }.isSuccess
            Toast.makeText(context, if (ok) "Carnet exporté" else "Échec de l'export", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Carnet de pleins") },
                subtitle = { Text("Consommation, budget et économies") },
                actions = {
                    if (state.fillUps.isNotEmpty()) {
                        IconButton(onClick = { csvExporter.launch("reserve-carnet-${java.time.LocalDate.now()}.csv") }) {
                            Icon(Icons.Rounded.TableView, contentDescription = "Exporter en CSV")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { haptics.tick(); adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Ajouter un plein") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val stats = state.stats
            if (stats != null && state.fillUps.isNotEmpty()) {
                item(key = "stats") { StatsRow(stats) }
                item(key = "chart") { MonthlyChart(stats.months) }
            } else {
                item(key = "empty") {
                    EmptyState("Après « Y aller », Reserve vous propose de noter votre plein : litres, prix et kilométrage.")
                }
            }
            items(state.fillUps, key = { it.id }) { fillUp ->
                FillUpRow(fillUp, onDelete = { haptics.toggle(false); vm.delete(fillUp.id) }, modifier = Modifier.animateItem())
            }
        }
    }

    if (adding) {
        FillUpSheet(
            draft = FillUpDraft(null, "", state.settings.fuel, null),
            lastOdometer = vm.lastOdometer(),
            onSave = { draft, liters, price, odo, full -> vm.save(draft, liters, price, odo, full); adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun StatsRow(stats: FuelLogStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            "Conso réelle",
            stats.consumption?.let { "${Format.number(it)} L" } ?: "—",
            stats.distanceKm?.let { "sur $it km" } ?: "2 pleins complets requis",
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            Modifier.weight(1f),
        )
        StatCard(
            "Ce mois-ci",
            Format.euros(stats.thisMonth),
            "Total ${Format.euros(stats.totalSpent)}",
            MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
            Modifier.weight(1f),
        )
        StatCard(
            "Économies",
            Format.signedEuros(stats.savings),
            "vs prix moyen local",
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, caption: String, container: Color, content: Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun MonthlyChart(months: List<MonthSpend>) {
    val max = months.maxOf { it.total }.coerceAtLeast(1.0)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, label = "bars")
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.secondaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Budget carburant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Canvas(
                Modifier.fillMaxWidth().height(110.dp).semantics {
                    contentDescription = months.joinToString { "${it.month.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)} ${Format.euros(it.total)}" }
                },
            ) {
                val slot = size.width / months.size
                val barWidth = slot * 0.56f
                months.forEachIndexed { i, m ->
                    val h = (size.height * (m.total / max).toFloat() * progress).coerceAtLeast(if (m.total > 0) 4f else 2f)
                    drawRoundRect(
                        color = if (i == months.lastIndex) primary else muted,
                        topLeft = Offset(slot * i + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(12f, 12f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                months.forEach { m ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            m.month.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (m.total > 0) "${m.total.toInt()} €" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FillUpRow(fillUp: FillUpEntity, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalGasStation, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(fillUp.stationLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${Format.date(fillUp.date)} · ${fillUp.fuel.label}" + (fillUp.odometerKm?.let { " · $it km" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Format.liters(fillUp.liters)} à ${Format.price(fillUp.pricePerLiter)}" + if (!fillUp.fullTank) " · partiel" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.euros(fillUp.liters * fillUp.pricePerLiter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                fillUp.localAveragePrice?.let { avg ->
                    val saved = (avg - fillUp.pricePerLiter) * fillUp.liters
                    Text(
                        Format.signedEuros(saved),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (saved >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer") }
        }
    }
}
