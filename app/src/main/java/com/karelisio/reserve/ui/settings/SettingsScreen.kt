package com.karelisio.reserve.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.BuildConfig
import com.karelisio.reserve.container
import com.karelisio.reserve.data.model.NavApp
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.diagnostics.ReportDialog
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.SOURCE_MENTION

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceSection(s, vm::setAppearance)
            VehiclesSection(vehicles, s.activeVehicleId, vm::activateVehicle, vm::saveVehicle, vm::deleteVehicle)
            LoyaltySection(cards, vm::addCard, vm::deleteCard)
            AlertsSection(s, vm::setAlerts, vm::setWeeklyRecap, vm::setRangeAlert)
            CarAlertSection(s, vm::setCarAlert)
            SettingsSection("Navigation") {
                Text("Application pour « Y aller »", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ConnectedChoice(
                    options = listOf(NavApp.ASK, NavApp.WAZE, NavApp.GOOGLE_MAPS),
                    selected = s.navApp.takeIf { it != NavApp.OTHER } ?: NavApp.ASK,
                    label = {
                        when (it) {
                            NavApp.ASK -> "Demander"
                            NavApp.WAZE -> "Waze"
                            NavApp.GOOGLE_MAPS -> "Maps"
                            NavApp.OTHER -> "Autre"
                        }
                    },
                    onSelect = vm::setNavApp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PermissionsSection()
            BackupSection()
            SettingsSection("À propos") {
                val context = LocalContext.current
                Text("Reserve ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyLarge)
                if (context.container.updates.enabled) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { context.container.updates.checkNow() }) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Text("  Rechercher une mise à jour")
                    }
                }
                var reporting by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { reporting = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.BugReport, null)
                    Text("  Signaler un problème")
                }
                if (reporting) ReportDialog(crash = null, onDismiss = { reporting = false })
                Spacer(Modifier.height(12.dp))
                Text(SOURCE_MENTION, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Noms et enseignes des stations : © contributeurs OpenStreetMap, ODbL. " +
                        "Fond de carte : tuiles OpenStreetMap via MapLibre. " +
                        "Aucun compte, aucun serveur : vos données restent sur votre téléphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Text field for a decimal number, saved as soon as it is valid. */
@Composable
fun DecimalField(
    label: String,
    suffix: String,
    value: Double?,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    allowEmpty: Boolean = false,
    onEmpty: () -> Unit = {},
) {
    var text by remember(value == null) { mutableStateOf(value?.let { Format.number(it, decimals) }.orEmpty()) }
    val parsed = Format.parseDecimal(text)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val number = Format.parseDecimal(it)
            if (number != null && number > 0) onValue(number) else if (it.isBlank() && allowEmpty) onEmpty()
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = text.isNotBlank() && (parsed == null || parsed <= 0),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}
