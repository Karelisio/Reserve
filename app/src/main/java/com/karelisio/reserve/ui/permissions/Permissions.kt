package com.karelisio.reserve.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.karelisio.reserve.container
import kotlinx.coroutines.launch

object AppPermissions {

    /** Runtime permissions asked together in the first system dialogs. */
    fun runtime(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun canInstallUpdates(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installSettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun appSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

private enum class Step { RUNTIME, BACKGROUND, INSTALL, DONE }

/**
 * Asks for every permission at launch: location, notifications and Bluetooth in the system
 * dialogs, then background location and app installs (for updates), each explained first.
 * Those two special permissions are proposed once; they stay available in the settings.
 */
@Composable
fun LaunchPermissions(onLocationResult: (Boolean) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = context.container.settings
    val updatesEnabled = context.container.updates.enabled
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(Step.RUNTIME) }
    var explain by rememberSaveable { mutableStateOf(false) }
    // Survives rotation so a system dialog already on screen is not requested twice.
    var runtimeRequested by rememberSaveable { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onLocationResult(AppPermissions.hasLocation(context))
        step = Step.BACKGROUND
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        step = Step.INSTALL
    }
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        step = Step.DONE
    }

    LaunchedEffect(step) {
        explain = false
        when (step) {
            Step.RUNTIME -> {
                val missing = AppPermissions.runtime().filterNot { AppPermissions.granted(context, it) }
                when {
                    missing.isEmpty() -> step = Step.BACKGROUND
                    !runtimeRequested -> {
                        runtimeRequested = true
                        runtimeLauncher.launch(missing.toTypedArray())
                    }
                }
            }
            Step.BACKGROUND -> {
                val needed = Build.VERSION.SDK_INT >= 29 && AppPermissions.hasLocation(context) &&
                    !AppPermissions.hasBackgroundLocation(context) && !settings.wasAsked(background = true)
                if (needed) explain = true else step = Step.INSTALL
            }
            Step.INSTALL -> {
                val needed = updatesEnabled && !AppPermissions.canInstallUpdates(context) && !settings.wasAsked(background = false)
                if (needed) explain = true else step = Step.DONE
            }
            Step.DONE -> onDone()
        }
    }

    if (!explain) return
    when (step) {
        Step.BACKGROUND -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.MyLocation, null) },
            title = { Text("Localisation « Toujours »") },
            text = {
                Text(
                    "Pour l'alerte en voiture, le widget et les alertes de prix, Reserve a besoin de votre position " +
                        "même quand l'appli est fermée. Choisissez « Toujours autoriser » à l'écran suivant.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { settings.setAsked(background = true) }
                    explain = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        step = Step.INSTALL
                    }
                }) { Text("Continuer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { settings.setAsked(background = true) }
                    step = Step.INSTALL
                }) { Text("Plus tard") }
            },
        )
        Step.INSTALL -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.InstallMobile, null) },
            title = { Text("Mises à jour intégrées") },
            text = {
                Text(
                    "Reserve se met à jour depuis ses versions publiées sur GitHub. Autorisez l'installation " +
                        "d'applications pour Reserve à l'écran suivant, puis revenez.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { settings.setAsked(background = false) }
                    explain = false
                    installLauncher.launch(AppPermissions.installSettingsIntent(context))
                }) { Text("Autoriser") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { settings.setAsked(background = false) }
                    step = Step.DONE
                }) { Text("Plus tard") }
            },
        )
        else -> Unit
    }
}
