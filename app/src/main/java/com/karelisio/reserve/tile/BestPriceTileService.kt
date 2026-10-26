package com.karelisio.reserve.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.reserve.MainActivity
import com.karelisio.reserve.R
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.work.Surfaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile showing the best price around; a tap opens the station. */
class BestPriceTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bestStationId: Long? = null

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val data = Surfaces.bestNearby(applicationContext)
            val best = data?.best
            bestStationId = best?.station?.id
            val tile = qsTile ?: return@launch
            tile.icon = Icon.createWithResource(this@BestPriceTileService, R.drawable.ic_stat_reserve)
            if (data == null || best == null) {
                tile.label = getString(R.string.tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = t("Ouvrir Reserve", "Open Reserve")
                tile.state = Tile.STATE_INACTIVE
            } else {
                tile.label = Format.price(best.effectivePrice)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "${data.fuel.label} · ${Format.distance(best.distanceKm)}"
                }
                tile.contentDescription = t(
                    "${data.fuel.label} à ${Format.price(best.effectivePrice)}, ${best.station.displayName}",
                    "${data.fuel.label} at ${Format.price(best.effectivePrice)}, ${best.station.displayName}",
                )
                tile.state = Tile.STATE_ACTIVE
            }
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = MainActivity.stationIntent(this, bestStationId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, BestPriceTileService::class.java))
            }
        }
    }
}
