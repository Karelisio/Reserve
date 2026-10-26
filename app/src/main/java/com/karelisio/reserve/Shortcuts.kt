package com.karelisio.reserve

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.karelisio.reserve.domain.t

/** Long press on the app icon: direct access to the main screens. */
object Shortcuts {
    const val EXTRA_DESTINATION = "destination"
    const val AROUND = "around"
    const val MAP = "map"
    const val TRIP = "trip"
    const val FILL_UP = "fillup"

    fun publish(context: Context) {
        val shortcuts = listOf(
            Triple(AROUND, t("Moins cher autour", "Cheapest nearby"), R.drawable.ic_shortcut_around),
            Triple(MAP, t("Carte des prix", "Price map"), R.drawable.ic_shortcut_map),
            Triple(TRIP, t("Sur mon trajet", "On my route"), R.drawable.ic_shortcut_trip),
            Triple(FILL_UP, t("Ajouter un plein", "Add a fill-up"), R.drawable.ic_shortcut_fillup),
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
