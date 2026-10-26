package com.karelisio.reserve.widget

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.reserve.MainActivity
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.ui.theme.GreenDarkScheme
import com.karelisio.reserve.ui.theme.GreenLightScheme
import com.karelisio.reserve.work.BestNearby
import com.karelisio.reserve.work.Surfaces

/** Home screen widget: cheapest fuel around the user, resizable, in dynamic colors. */
class ReserveWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Surfaces.bestNearby(context)
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else fallbackColors) {
                WidgetContent(context, data)
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 48.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 200.dp)
        private val fallbackColors = ColorProviders(light = GreenLightScheme, dark = GreenDarkScheme)

        fun openStation(context: Context, stationId: Long?): Action =
            actionStartActivity(
                MainActivity.stationIntent(context, stationId).setData(Uri.parse("reserve://widget/${stationId ?: 0}")),
            )
    }
}

class ReserveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReserveWidget()
}

@Composable
private fun WidgetContent(context: Context, data: BestNearby?) {
    val size = LocalSize.current
    val best = data?.best
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(ReserveWidget.openStation(context, best?.station?.id))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            data == null || best == null -> Text(
                "Ouvrez Reserve pour charger les prix autour de vous",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            size.height < 100.dp -> Compact(data, best)
            else -> Expanded(context, data, best, showList = size.height >= 190.dp)
        }
    }
}

@Composable
private fun Compact(data: BestNearby, best: RankedStation) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        PricePill(best.effectivePrice)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text(
                data.fuel.label,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                Format.distance(best.distanceKm),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Expanded(context: Context, data: BestNearby, best: RankedStation, showList: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            "${data.fuel.label} · le moins cher autour",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            PricePill(best.effectivePrice, large = true)
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Text(
                    best.station.displayName,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    "${Format.distance(best.distanceKm)} · ${Format.age(best.price.updatedAt)}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        if (showList) {
            Spacer(GlanceModifier.height(8.dp))
            data.ranked.filter { it.station.id != best.station.id }.take(3).forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(ReserveWidget.openStation(context, item.station.id)),
                ) {
                    Text(
                        Format.price(item.effectivePrice),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        "${item.station.displayName} · ${Format.distance(item.distanceKm)}",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PricePill(price: Double, large: Boolean = false) {
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(if (large) 20.dp else 16.dp)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 5.dp),
    ) {
        Text(
            Format.price(price),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = if (large) 22.sp else 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
