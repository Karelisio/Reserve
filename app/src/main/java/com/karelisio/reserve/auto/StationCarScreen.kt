package com.karelisio.reserve.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.karelisio.reserve.container
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.t
import kotlinx.coroutines.launch
import java.util.Locale

/** Station details in the car, with the navigation action. */
class StationCarScreen(carContext: CarContext, private val item: RankedStation) : Screen(carContext) {

    // setTitle/setHeaderAction keep working on hosts older than Car API 7 (Header).
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val station = item.station
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
        }.getOrDefault(4)

        val rows = buildList {
            add(
                Row.Builder()
                    .setTitle(t("${item.price.fuel.label} : ${Format.price(item.effectivePrice)}", "${item.price.fuel.label}: ${Format.price(item.effectivePrice)}"))
                    .addText(
                        t(
                            "${Format.distance(item.distanceKm)} · mis à jour ${Format.age(item.price.updatedAt)}",
                            "${Format.distance(item.distanceKm)} · updated ${Format.age(item.price.updatedAt)}",
                        ),
                    )
                    .build(),
            )
            add(Row.Builder().setTitle(station.fullAddress.ifBlank { station.city }).apply {
                if (station.automate24) addText(t("Automate CB 24/24", "24/7 card pump"))
            }.build())
            station.prices.filter { it.fuel != item.price.fuel }.sortedBy { it.fuel.ordinal }.forEach { other ->
                add(
                    Row.Builder()
                        .setTitle(t("${other.fuel.label} : ${Format.price(other.price)}", "${other.fuel.label}: ${Format.price(other.price)}"))
                        .addText(Format.age(other.updatedAt))
                        .build(),
                )
            }
        }.take(limit.coerceAtLeast(2))

        val pane = Pane.Builder().apply { rows.forEach { addRow(it) } }
            .addAction(
                Action.Builder()
                    .setTitle(t("Y aller", "Go there"))
                    .apply { if (carContext.carAppApiLevel >= 4) setFlags(Action.FLAG_PRIMARY) }
                    .setOnClickListener { navigate() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(t("Favori", "Favourite"))
                    .setOnClickListener {
                        lifecycleScope.launch {
                            carContext.container.stations.addFavorite(station)
                            CarToast.makeText(carContext, t("Ajouté aux favoris", "Added to favourites"), CarToast.LENGTH_SHORT).show()
                        }
                    }
                    .build(),
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(station.displayName)
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun navigate() {
        val lat = String.format(Locale.ROOT, "%.6f", item.station.lat)
        val lon = String.format(Locale.ROOT, "%.6f", item.station.lon)
        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
            lifecycleScope.launch {
                carContext.container.stations.rememberTrip(item.station, item.price.fuel, item.effectivePrice)
            }
        } catch (e: Exception) {
            CarToast.makeText(carContext, t("Aucune application de navigation", "No navigation app"), CarToast.LENGTH_LONG).show()
        }
    }
}

/** Choice of the fuel, from the car. */
class FuelChoiceCarScreen(carContext: CarContext, private val onChosen: () -> Unit) : Screen(carContext) {

    private var selected: Fuel? = null

    init {
        lifecycleScope.launch {
            selected = carContext.container.settings.current().fuel
            invalidate()
        }
    }

    // setTitle/setHeaderAction keep working on hosts older than Car API 7 (Header).
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val fuels = Fuel.selectableEntries
        val current = selected ?: return ListTemplate.Builder().setTitle(t("Carburant", "Fuel")).setHeaderAction(Action.BACK).setLoading(true).build()
        val list = ItemList.Builder().apply {
            fuels.forEach { addItem(Row.Builder().setTitle(it.label).build()) }
            setSelectedIndex(fuels.indexOf(current).coerceAtLeast(0))
            setOnSelectedListener { index ->
                lifecycleScope.launch {
                    carContext.container.settings.setFuel(fuels[index])
                    onChosen()
                    screenManager.pop()
                }
            }
        }.build()
        return ListTemplate.Builder()
            .setTitle(t("Carburant", "Fuel"))
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
