package com.karelisio.reserve.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.model.ThemeMode
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.components.ConnectedChoice
import com.karelisio.reserve.ui.components.rememberHaptics

@Composable
fun AppearanceSection(settings: Settings, onChange: (ThemeMode, dynamicColor: Boolean, pureBlack: Boolean) -> Unit) {
    val haptics = rememberHaptics()
    SettingsSection(t("Apparence", "Appearance")) {
        ConnectedChoice(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label },
            icon = {
                when (it) {
                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                }
            },
            onSelect = { onChange(it, settings.dynamicColor, settings.pureBlack) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                title = t("Couleurs dynamiques", "Dynamic colours"),
                caption = t("Couleurs tirées du fond d'écran ; sinon thème vert Reserve.", "Colours taken from your wallpaper; otherwise the Reserve green theme."),
                checked = settings.dynamicColor,
            ) { haptics.toggle(it); onChange(settings.themeMode, it, settings.pureBlack) }
        }
        SwitchRow(
            title = t("Noir pur en mode sombre", "Pure black in dark mode"),
            caption = t("Fond noir, idéal pour les écrans OLED.", "Black background, ideal for OLED screens."),
            checked = settings.pureBlack,
        ) { haptics.toggle(it); onChange(settings.themeMode, settings.dynamicColor, it) }
        if (AppLanguage.supported) {
            val context = LocalContext.current
            var language by remember { mutableStateOf(AppLanguage.current(context)) }
            Spacer(Modifier.height(8.dp))
            Text(t("Langue", "Language"), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            ConnectedChoice(
                options = AppLanguage.entries,
                selected = language,
                label = {
                    when (it) {
                        AppLanguage.SYSTEM -> t("Système", "System")
                        AppLanguage.FRENCH -> "Français"
                        AppLanguage.ENGLISH -> "English"
                    }
                },
                onSelect = { language = it; AppLanguage.apply(context, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
