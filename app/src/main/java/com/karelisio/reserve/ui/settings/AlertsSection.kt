package com.karelisio.reserve.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.notifications.Notifier
import com.karelisio.reserve.ui.components.rememberHaptics

@Composable
fun AlertsSection(
    settings: Settings,
    onChange: (enabled: Boolean, threshold: Double?, dropCents: Double) -> Unit,
    onWeeklyRecap: (Boolean) -> Unit = {},
    onRangeAlert: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onChange(granted, settings.alertThreshold, settings.alertDropCents)
    }
    SettingsSection(t("Alertes de prix", "Price alerts")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Surveiller mes favoris", "Watch my favourites"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    t("Vérification toutes les heures, notification en cas de baisse.", "Checked every hour, notified when prices drop."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.alertsEnabled,
                onCheckedChange = { enabled ->
                    haptics.toggle(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onChange(enabled, settings.alertThreshold, settings.alertDropCents)
                    }
                },
            )
        }
        AnimatedVisibility(settings.alertsEnabled) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DecimalField(
                        label = t("Seuil (optionnel)", "Threshold (optional)"),
                        suffix = "€/L",
                        value = settings.alertThreshold,
                        decimals = 3,
                        allowEmpty = true,
                        onValue = { onChange(true, it, settings.alertDropCents) },
                        onEmpty = { onChange(true, null, settings.alertDropCents) },
                        modifier = Modifier.weight(1f),
                    )
                    DecimalField(
                        label = t("Baisse d'au moins", "Drop of at least"),
                        suffix = t("cts", "c"),
                        value = settings.alertDropCents,
                        decimals = 0,
                        onValue = { onChange(true, settings.alertThreshold, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Carburant suivi : ${settings.fuel.label}. Les alertes portent sur les stations favorites.",
                        "Tracked fuel: ${settings.fuel.label}. Alerts cover your favourite stations.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Récap du dimanche", "Sunday recap"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    t("Dépenses, économies de la semaine et meilleur jour pour faire le plein.", "Spending, weekly savings and the best day to fill up."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.weeklyRecap, onCheckedChange = { haptics.toggle(it); onWeeklyRecap(it) })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Alerte réserve", "Low fuel alert"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    t("Quand l'autonomie estimée d'après le carnet passe sous 80 km.", "When the range estimated from your log drops below 80 km."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.rangeAlert, onCheckedChange = { haptics.toggle(it); onRangeAlert(it) })
        }
    }
}
