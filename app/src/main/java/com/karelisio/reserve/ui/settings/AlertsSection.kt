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
import com.karelisio.reserve.notifications.Notifier
import com.karelisio.reserve.ui.components.rememberHaptics

@Composable
fun AlertsSection(
    settings: Settings,
    onChange: (enabled: Boolean, threshold: Double?, dropCents: Double) -> Unit,
    onWeeklyRecap: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onChange(granted, settings.alertThreshold, settings.alertDropCents)
    }
    SettingsSection("Alertes de prix") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Surveiller mes favoris", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Vérification toutes les heures, notification en cas de baisse.",
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
                        label = "Seuil (optionnel)",
                        suffix = "€/L",
                        value = settings.alertThreshold,
                        decimals = 3,
                        allowEmpty = true,
                        onValue = { onChange(true, it, settings.alertDropCents) },
                        onEmpty = { onChange(true, null, settings.alertDropCents) },
                        modifier = Modifier.weight(1f),
                    )
                    DecimalField(
                        label = "Baisse d'au moins",
                        suffix = "cts",
                        value = settings.alertDropCents,
                        decimals = 0,
                        onValue = { onChange(true, settings.alertThreshold, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Carburant suivi : ${settings.fuel.label}. Les alertes portent sur les stations favorites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Récap du dimanche", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Dépenses, économies de la semaine et meilleur jour pour faire le plein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.weeklyRecap, onCheckedChange = { haptics.toggle(it); onWeeklyRecap(it) })
        }
    }
}
