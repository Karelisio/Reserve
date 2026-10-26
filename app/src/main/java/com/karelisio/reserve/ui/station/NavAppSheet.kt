package com.karelisio.reserve.ui.station

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.NavApp

@Composable
fun NavAppSheet(onPick: (NavApp) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Y aller avec…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            listOf(
                Triple(NavApp.WAZE, "Waze", Icons.Rounded.Navigation),
                Triple(NavApp.GOOGLE_MAPS, "Google Maps", Icons.Rounded.Map),
                Triple(NavApp.OTHER, "Autre application", Icons.Rounded.Public),
            ).forEach { (app, label, icon) ->
                val installed = Navigator.isInstalled(context, app)
                ListItem(
                    onClick = { onPick(app) },
                    leadingContent = { Icon(icon, null) },
                    supportingContent = if (!installed) {
                        { Text("Non installée : ouverture dans le navigateur") }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(label) }
            }
        }
    }
}
