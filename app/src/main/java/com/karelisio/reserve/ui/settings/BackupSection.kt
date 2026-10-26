package com.karelisio.reserve.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.container
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Export / import of all user data, the only backup without an account. */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val backup = context.container.backup
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { backup.export(it) }
            }.isSuccess
            Toast.makeText(context, if (ok) t("Sauvegarde enregistrée", "Backup saved") else t("Échec de la sauvegarde", "Backup failed"), Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                context.contentResolver.openInputStream(uri)!!.use { backup.import(it) }
            }.fold(
                onSuccess = {
                    t(
                        "Restauré : ${it.favorites} favoris, ${it.fillUps} pleins, ${it.cards} cartes",
                        "Restored: ${it.favorites} favourite${if (it.favorites == 1) "" else "s"}, " +
                            "${it.fillUps} fill-up${if (it.fillUps == 1) "" else "s"}, " +
                            "${it.cards} card${if (it.cards == 1) "" else "s"}",
                    )
                },
                onFailure = {
                    it.message?.takeIf { m -> m.startsWith("Ce fichier") || m.startsWith("This file") }
                        ?: t("Fichier de sauvegarde illisible", "Unreadable backup file")
                },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    SettingsSection(t("Sauvegarde", "Backup")) {
        Text(
            t(
                "Favoris, carnet de pleins, cartes fidélité, historique et réglages dans un fichier. " +
                    "La sauvegarde Android automatique est aussi activée.",
                "Favourites, fill-up log, loyalty cards, history and settings in one file. " +
                    "Android automatic backup is also enabled.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { haptics.tick(); exporter.launch(t("reserve-sauvegarde-${LocalDate.now()}.json", "reserve-backup-${LocalDate.now()}.json")) }) {
                Icon(Icons.Rounded.Upload, null)
                Text(t("  Exporter", "  Export"))
            }
            OutlinedButton(onClick = { haptics.tick(); importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Icon(Icons.Rounded.Download, null)
                Text(t("  Importer", "  Import"))
            }
        }
    }
}
