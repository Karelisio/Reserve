package com.karelisio.reserve.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.reserve.BuildConfig
import com.karelisio.reserve.container
import com.karelisio.reserve.ui.components.rememberHaptics
import com.karelisio.reserve.update.AppUpdate
import com.karelisio.reserve.update.UpdateState
import java.util.Locale

/** Shows the in-app update flow: available → download → install. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val updates = context.container.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    // "Install unknown apps" must be allowed once for Reserve before installing.
    var pending by remember { mutableStateOf<AppUpdate?>(null) }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val update = pending ?: (updates.state.value as? UpdateState.Available)?.update
        if (update != null && updates.canInstallPackages()) updates.downloadAndInstall(update)
    }
    fun start(update: AppUpdate) {
        haptics.confirm()
        if (updates.canInstallPackages()) {
            updates.downloadAndInstall(update)
        } else {
            pending = update
            installPermission.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }
    }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updates::dismiss,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Mise à jour disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Version ${s.update.versionName} (actuelle : ${BuildConfig.VERSION_NAME})" +
                            if (s.update.sizeBytes > 0) " · ${megabytes(s.update.sizeBytes)}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.update.notes.isNotBlank()) {
                        Text(
                            s.update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    if (!updates.canInstallPackages()) {
                        Text(
                            "Android va vous demander d'autoriser Reserve à installer des applications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { start(s.update) }) { Text("Mettre à jour") } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text("Plus tard") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Téléchargement…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).toInt()} % · version ${s.update.versionName}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Installation…") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LoadingIndicator()
                    Text("Confirmez l'installation dans la fenêtre d'Android.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text("Masquer") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updates::dismiss,
            icon = { Icon(Icons.Rounded.ErrorOutline, null) },
            title = { Text("Mise à jour") },
            text = { Text(s.message) },
            confirmButton = {
                val update = s.update
                if (update != null) TextButton(onClick = { start(update) }) { Text("Réessayer") }
                else TextButton(onClick = updates::dismiss) { Text("OK") }
            },
            dismissButton = if (s.update != null) {
                { TextButton(onClick = updates::dismiss) { Text("Fermer") } }
            } else null,
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updates::dismiss,
            icon = { Icon(Icons.Rounded.CheckCircle, null) },
            title = { Text("Reserve est à jour") },
            text = { Text("Version ${BuildConfig.VERSION_NAME}.") },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text("OK") } },
        )
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.FRANCE, "%.1f Mo", bytes / 1_048_576.0)
