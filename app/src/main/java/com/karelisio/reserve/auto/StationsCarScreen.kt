package com.karelisio.reserve.auto

import android.Manifest
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import com.karelisio.reserve.container
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.theme.GreenLightScheme
import com.karelisio.reserve.ui.theme.PriceColors
import com.karelisio.reserve.work.Surfaces
import kotlinx.coroutines.launch

/** Cheapest stations around, on the map drawn by the car host, sorted by real cost. */
class StationsCarScreen(carContext: CarContext) : Screen(carContext) {

    private var loading = true
    private var stations: List<RankedStation> = emptyList()
    private var error: String? = null
    private var fuelLabel = ""

    init {
        lifecycleScope.launch { load(refresh = false) }
    }

    private fun hasLocation() = carContext.container.location.hasPermission()

    private suspend fun load(refresh: Boolean) {
        val app = carContext.container
        loading = true
        invalidate()
        val settings = app.settings.current()
        fuelLabel = settings.fuel.label
        val cached = app.stations.loadCache()
        val fresh = cached != null && System.currentTimeMillis() - cached.fetchedAt < 10 * 60 * 1000L
        val result = if (!refresh && fresh) cached else {
            val center = app.location.fast() ?: settings.lastLocation
            if (center == null) null
            else runCatching { app.stations.searchAround(center, settings.radiusKm).also { Surfaces.update(carContext) } }
                .getOrNull() ?: cached
        }
        error = when {
            result == null && !hasLocation() -> null
            result == null -> t("Prix indisponibles. Vérifiez la connexion du téléphone.", "Prices unavailable. Check the phone's connection.")
            else -> null
        }
        stations = if (result == null) emptyList() else {
            val ranked = Pricing.rank(
                stations = result.stations,
                fuel = settings.fuel,
                origin = result.center,
                vehicle = Vehicle(settings.consumption, settings.fillLiters),
                cards = app.db.loyaltyCards().all(),
                filters = settings.filters,
            ).filter { it.distanceKm <= settings.radiusKm + 0.05 && !it.outOfStock }
            ranked.sortedBy { it.totalCost }
        }
        loading = false
        invalidate()
    }

    // setTitle/setHeaderAction keep working on hosts older than Car API 7 (Header).
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        if (!hasLocation()) {
            return MessageTemplate.Builder(
                t("Autorisez la localisation pour trouver les stations autour de vous.", "Allow location access to find stations around you."),
            )
                .setTitle("Reserve")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle(t("Autoriser", "Allow"))
                        .setOnClickListener {
                            carContext.requestPermissions(
                                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            ) { granted, _ ->
                                if (granted.isNotEmpty()) lifecycleScope.launch { load(refresh = true) }
                                else invalidate()
                            }
                        }
                        .build(),
                )
                .build()
        }

        val builder = PlaceListMapTemplate.Builder()
            .setTitle(if (fuelLabel.isEmpty()) "Reserve" else t("$fuelLabel · moins cher", "$fuelLabel · cheapest"))
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder().setTitle(t("Carburant", "Fuel"))
                            .setOnClickListener { screenManager.push(FuelChoiceCarScreen(carContext) { lifecycleScope.launch { load(refresh = false) } }) }
                            .build(),
                    )
                    .addAction(
                        Action.Builder().setTitle(t("Actualiser", "Refresh"))
                            .setOnClickListener { lifecycleScope.launch { load(refresh = true) } }
                            .build(),
                    )
                    .build(),
            )
        if (loading) return builder.setLoading(true).build()

        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        }.getOrDefault(6)
        val shown = stations.take(limit)
        val prices = shown.map { it.effectivePrice }
        val range = if (prices.isEmpty()) null else prices.min()..prices.max()

        val items = ItemList.Builder().setNoItemsMessage(error ?: t("Aucune station dans le rayon choisi", "No stations within the chosen radius"))
        shown.forEachIndexed { index, item ->
            items.addItem(row(item, index, range))
        }
        return builder.setItemList(items.build()).build()
    }

    private fun row(item: RankedStation, index: Int, range: ClosedFloatingPointRange<Double>?): Row {
        val fraction = if (range == null || range.endInclusive - range.start < 0.0005) 0f
        else ((item.effectivePrice - range.start) / (range.endInclusive - range.start)).toFloat()
        val color = PriceColors.scale(fraction, GreenLightScheme.primary, dark = false).toArgb()
        val darkColor = PriceColors.scale(fraction, GreenLightScheme.primary, dark = true).toArgb()

        val distance = SpannableString("  ·  ${Format.age(item.price.updatedAt)}").apply {
            setSpan(
                DistanceSpan.create(
                    if (item.distanceKm < 1) Distance.create(item.distanceKm * 1000, Distance.UNIT_METERS)
                    else Distance.create(item.distanceKm, Distance.UNIT_KILOMETERS),
                ),
                0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
        val place = Place.Builder(CarLocation.create(item.station.lat, item.station.lon))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.createCustom(color, darkColor)).setLabel("${index + 1}").build())
            .build()
        return Row.Builder()
            .setTitle("${Format.price(item.effectivePrice)} · ${item.station.displayName}")
            .addText(distance)
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setOnClickListener {
                screenManager.push(StationCarScreen(carContext, item))
            }
            .build()
    }
}
