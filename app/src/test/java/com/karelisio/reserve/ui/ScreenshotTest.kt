package com.karelisio.reserve.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.karelisio.reserve.data.local.ZoneAverageEntity
import com.karelisio.reserve.data.model.DayHours
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.FuelPrice
import com.karelisio.reserve.data.model.FuelShortage
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.TrendAnalyzer
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.ui.components.FuelSelector
import com.karelisio.reserve.ui.components.SourceFooter
import com.karelisio.reserve.ui.components.StationCard
import com.karelisio.reserve.ui.home.BestChoiceCard
import com.karelisio.reserve.ui.home.RangeCard
import com.karelisio.reserve.ui.home.TrendCard
import com.karelisio.reserve.ui.theme.ReserveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Renders the main components with sample data (no device needed). Images are written to
 * app/build/screenshots for a visual check of the light and dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun station(id: Long, brand: String, price: Double, lat: Double, ageH: Long, out: Boolean = false) = Station(
        id = id, lat = lat, lon = 2.35, address = "12 avenue de la République", city = "PARIS", postalCode = "75011",
        isHighway = false, automate24 = true, services = listOf("Lavage automatique", "Boutique alimentaire"),
        hours = listOf(DayHours(1, false, listOf("06:00–21:00"))),
        prices = listOf(FuelPrice(Fuel.GAZOLE, price, now - ageH * 3_600_000)),
        shortages = if (out) listOf(FuelShortage(Fuel.GAZOLE, definitive = false)) else emptyList(),
        brand = brand,
    )

    @Composable
    private fun Sample() {
        val stations = listOf(
            station(1, "E.Leclerc", 1.689, 48.86, 2),
            station(2, "TotalEnergies", 1.759, 48.85, 1),
            station(3, "Intermarché", 1.712, 48.90, 90),
            station(4, "Esso", 1.799, 48.857, 5, out = true),
        )
        val ranked = Pricing.sort(
            Pricing.rank(stations, Fuel.GAZOLE, LatLon(48.8566, 2.3522), Vehicle(6.5, 40.0), emptyList(), now = now),
            SortMode.REAL_COST,
        )
        val range = ranked.filterNot { it.outOfStock }.map { it.effectivePrice }.let { it.min()..it.max() }
        val trend = TrendAnalyzer.analyze(
            (6L downTo 0L).map {
                ZoneAverageEntity(LocalDate.now().toEpochDay() - it, "z", Fuel.GAZOLE, 1.80 - (6 - it) * 0.004, 1.7, 20)
            },
        )
        Column(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FuelSelector(Fuel.GAZOLE, {}, Modifier.fillMaxWidth())
            BestChoiceCard(ranked.first(), onOpen = {}, onGo = {}, modifier = Modifier.padding(horizontal = 16.dp))
            RangeCard(
                com.karelisio.reserve.domain.RangeEstimate(310.0, 20.0, 0.4, 60.0, true, 1L, now - 3 * 86_400_000L),
                "Clio", Modifier.padding(horizontal = 16.dp),
            )
            RangeCard(
                com.karelisio.reserve.domain.RangeEstimate(55.0, 3.5, 0.07, 60.0, false, 2L, now - 9 * 86_400_000L),
                null, Modifier.padding(horizontal = 16.dp),
            )
            TrendCard(trend, Fuel.GAZOLE, Modifier.padding(horizontal = 16.dp))
            ranked.drop(1).forEach { item ->
                StationCard(
                    item = item, priceRange = range, isFavorite = item.station.id == 2L, sort = SortMode.REAL_COST,
                    isBest = item == ranked.first(), onClick = {}, onToggleFavorite = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            SourceFooter()
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun listLight() {
        compose.setContent { ReserveTheme(darkTheme = false) { Sample() } }
        capture("list_light")
    }

    @Test
    fun listDarkGreen() {
        compose.setContent { ReserveTheme(darkTheme = true, dynamicColor = false) { Sample() } }
        capture("list_dark_green")
    }

    @Test
    fun listPureBlack() {
        compose.setContent { ReserveTheme(darkTheme = true, pureBlack = true) { Sample() } }
        capture("list_pure_black")
    }
}

/** Vector resources must inflate (a broken path only fails at runtime otherwise). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DrawablesTest {
    @Test
    fun vectorsInflate() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        listOf(
            com.karelisio.reserve.R.drawable.ic_shortcut_around,
            com.karelisio.reserve.R.drawable.ic_shortcut_map,
            com.karelisio.reserve.R.drawable.ic_shortcut_trip,
            com.karelisio.reserve.R.drawable.ic_shortcut_fillup,
            com.karelisio.reserve.R.drawable.ic_launcher_foreground,
            com.karelisio.reserve.R.drawable.ic_stat_reserve,
            com.karelisio.reserve.R.drawable.splash_gauge,
        ).forEach { id ->
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, id)!!
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(android.graphics.Canvas(bitmap))
        }
    }
}
