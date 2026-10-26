package com.karelisio.reserve.diagnostics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.karelisio.reserve.domain.t

/** Report a problem: as a GitHub issue, or shared through any app. [crash] if it follows a crash. */
@Composable
fun ReportDialog(crash: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BugReport, null) },
        title = { Text(if (crash != null) t("Reserve s'est arrêtée", "Reserve stopped") else t("Signaler un problème", "Report a problem")) },
        text = {
            Text(
                (
                    if (crash != null) {
                        t(
                            "L'appli s'est fermée de façon inattendue lors de la dernière utilisation. ",
                            "The app closed unexpectedly the last time you used it. ",
                        )
                    } else {
                        ""
                    }
                ) +
                    t(
                        "Le rapport contient la version, le modèle du téléphone et les dernières erreurs, sans votre position. " +
                            "Vous pourrez le relire avant de l'envoyer.",
                        "The report includes the version, the phone model and the latest errors, but not your location. " +
                            "You can review it before sending it.",
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Diagnostics.githubIssueIntent(crash)) }
                onDismiss()
            }) { Text("GitHub") }
        },
        dismissButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Diagnostics.shareIntent(crash)) }
                onDismiss()
            }) { Text(t("Partager", "Share")) }
        },
    )
}

/** At launch: offers to report a crash of the previous session, once. */
@Composable
fun PendingCrashDialog() {
    var crash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crash = Diagnostics.pendingCrash() }
    crash?.let { text ->
        ReportDialog(crash = text, onDismiss = {
            Diagnostics.acknowledgeCrashes()
            crash = null
        })
    }
}
