package com.karelisio.reserve.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.karelisio.reserve.container
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.Tag
import com.karelisio.reserve.ui.permissions.AppPermissions

/** Status of every permission, with a shortcut to grant the missing ones. */
@Composable
fun PermissionsSection() {
    val context = LocalContext.current
    // Re-read the statuses when coming back from the system settings.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val key = refresh + if (lifecycleState == Lifecycle.State.RESUMED) 1 else 0

    val granted = remember(key) {
        mapOf(
            "location" to AppPermissions.hasLocation(context),
            "background" to AppPermissions.hasBackgroundLocation(context),
            "notifications" to (Build.VERSION.SDK_INT < 33 || AppPermissions.granted(context, Manifest.permission.POST_NOTIFICATIONS)),
            "bluetooth" to (Build.VERSION.SDK_INT < 31 || AppPermissions.granted(context, Manifest.permission.BLUETOOTH_CONNECT)),
            "install" to AppPermissions.canInstallUpdates(context),
        )
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    SettingsSection(t("Autorisations", "Permissions")) {
        PermissionRow(
            Icons.Rounded.LocationOn, t("Localisation", "Location"), t("Stations autour de vous", "Stations around you"),
            granted.getValue("location"),
        ) {
            request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            PermissionRow(
                Icons.Rounded.MyLocation, t("Localisation « Toujours »", "Location “All the time”"), t("Alerte en voiture, widget", "In-car alert, widget"),
                granted.getValue("background"),
            ) {
                if (granted.getValue("location")) request.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                else openSettings.launch(AppPermissions.appSettingsIntent(context))
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                Icons.Rounded.Notifications, "Notifications", t("Alertes de prix", "Price alerts"),
                granted.getValue("notifications"),
            ) { request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            PermissionRow(
                Icons.Rounded.Bluetooth, "Bluetooth", t("Détecter la voiture", "Detect the car"),
                granted.getValue("bluetooth"),
            ) { request.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }
        }
        if (context.container.updates.enabled) {
            PermissionRow(
                Icons.Rounded.InstallMobile, t("Installer les mises à jour", "Install updates"), t("Mises à jour intégrées", "Built-in updates"),
                granted.getValue("install"),
            ) { openSettings.launch(AppPermissions.installSettingsIntent(context)) }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, usage: String, granted: Boolean, onGrant: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        supportingContent = { Text(usage) },
        trailingContent = {
            if (granted) {
                Tag(t("Accordée", "Granted"), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                TextButton(onClick = onGrant) { Text(t("Autoriser", "Allow")) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(title) }
}
