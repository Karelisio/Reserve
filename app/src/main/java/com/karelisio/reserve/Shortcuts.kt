package com.karelisio.reserve

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Long press on the app icon: direct access to the main screens. */
object Shortcuts {
    const val EXTRA_DESTINATION = "destination"
    const val AROUND = "around"
    const val MAP = "map"
    const val TRIP = "trip"
    const val FILL_UP = "fillup"

    fun publish(context: Context) {
        val shortcuts = listOf(
            Triple(AROUND, "Moins cher autour", R.drawable.ic_shortcut_around),
            Triple(MAP, "Carte des prix", R.drawable.ic_shortcut_map),
            Triple(TRIP, "Sur mon trajet", R.drawable.ic_shortcut_trip),
            Triple(FILL_UP, "Ajouter un plein", R.drawable.ic_shortcut_fillup),
        ).mapIndexed { rank, (id, label, icon) ->
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setRank(rank)
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_DESTINATION, id),
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
