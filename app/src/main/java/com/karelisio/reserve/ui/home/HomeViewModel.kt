package com.karelisio.reserve.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import com.karelisio.reserve.data.model.Filters
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SearchResult
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.domain.Format
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.Trend
import com.karelisio.reserve.domain.TrendAnalyzer
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.work.Surfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val settings: Settings = Settings(),
    val result: SearchResult? = null,
    val stations: List<RankedStation> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val needsLocationPermission: Boolean = false,
    val favorites: Set<Long> = emptySet(),
    val trend: Trend? = null,
) {
    val origin: LatLon? get() = result?.center
    val best: RankedStation? get() = Pricing.best(stations)
    val priceRange: ClosedFloatingPointRange<Double>?
        get() = stations.filterNot { it.outOfStock }.map { it.effectivePrice }.takeIf { it.isNotEmpty() }
            ?.let { it.min()..it.max() }
}

private data class Transient(val loading: Boolean = false, val error: String? = null, val needsPermission: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val app: AppContainer) : ViewModel() {

    private val transient = MutableStateFlow(Transient())
    private var refreshJob: Job? = null

    private val cards = app.db.loyaltyCards().observeAll()

    /** Local average price of the selected fuel over the last days, in the user's zone. */
    private val trend = combine(app.stations.lastResult, app.settings.settings.map { it.fuel }.distinctUntilChanged()) { r, f ->
        r?.center?.let(Geo::zoneKey) to f
    }
        .distinctUntilChanged()
        .flatMapLatest { (zone, fuel) ->
            if (zone == null) flowOf(null)
            else app.db.zoneAverages()
                .observe(zone, fuel, LocalDate.now().toEpochDay() - TrendAnalyzer.WINDOW_DAYS)
                .map { TrendAnalyzer.analyze(it) }
        }

    /**
     * Ranking up to a thousand stations is done off the main thread, and only when an input
     * of the ranking changes (not on loading or error updates).
     */
    private val ranked = combine(
        app.settings.settings.distinctUntilChanged { a, b ->
            a.fuel == b.fuel && a.radiusKm == b.radiusKm && a.sort == b.sort && a.filters == b.filters &&
                a.consumption == b.consumption && a.fillLiters == b.fillLiters
        },
        app.stations.lastResult,
        cards,
    ) { settings, result, cards -> rank(settings, result, cards) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<HomeUiState> = combine(
        app.settings.settings,
        app.stations.lastResult,
        app.db.favorites().observeIds(),
        ranked,
        combine(transient, trend) { t, tr -> t to tr },
    ) { settings, result, favorites, stations, (t, trend) ->
        HomeUiState(
            settings = settings,
            result = result,
            stations = stations,
            loading = t.loading,
            error = t.error,
            needsLocationPermission = t.needsPermission,
            favorites = favorites.toSet(),
            trend = trend,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(loading = true))

    init {
        viewModelScope.launch {
            val cached = app.stations.loadCache()
            if (cached == null || System.currentTimeMillis() - cached.fetchedAt > STALE_MS) refresh()
        }
    }

    private fun rank(settings: Settings, result: SearchResult?, cards: List<LoyaltyCardEntity>): List<RankedStation> {
        result ?: return emptyList()
        val inRadius = result.stations.filter {
            Geo.distanceKm(result.center, LatLon(it.lat, it.lon)) <= settings.radiusKm + 0.05
        }
        val ranked = Pricing.rank(
            stations = inRadius,
            fuel = settings.fuel,
            origin = result.center,
            vehicle = Vehicle(settings.consumption, settings.fillLiters),
            cards = cards,
            filters = settings.filters,
        )
        return Pricing.sort(ranked, settings.sort)
    }

    fun refresh(userInitiated: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            transient.value = transient.value.copy(loading = true, error = null)
            val settings = app.settings.current()
            val located = app.location.current()
            if (located == null && !app.location.hasPermission()) {
                transient.value = Transient(needsPermission = true)
                if (settings.lastLocation == null) return@launch
            }
            val center = located ?: settings.lastLocation ?: app.stations.lastResult.value?.center
            if (center == null) {
                transient.value = Transient(error = "Position indisponible. Activez la localisation puis réessayez.")
                return@launch
            }
            if (located != null) app.settings.setLastLocation(located)
            try {
                app.stations.searchAround(center, settings.radiusKm)
                Surfaces.update(app.context)
                transient.value = Transient(needsPermission = transient.value.needsPermission)
            } catch (e: Exception) {
                val cached = app.stations.lastResult.value
                transient.value = Transient(
                    error = if (cached != null) "Hors ligne : prix du ${Format.dateTime(cached.fetchedAt)}"
                    else "Impossible de joindre data.economie.gouv.fr. Vérifiez la connexion.",
                )
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        transient.value = transient.value.copy(needsPermission = !granted)
        if (granted) refresh()
    }

    fun setFuel(fuel: Fuel) = viewModelScope.launch {
        app.settings.setFuel(fuel)
        Surfaces.update(app.context)
    }

    fun setSort(sort: SortMode) = viewModelScope.launch { app.settings.setSort(sort) }

    fun setFilters(filters: Filters) = viewModelScope.launch {
        app.settings.setFilters(filters)
        Surfaces.update(app.context)
    }

    fun setRadius(km: Int) = viewModelScope.launch {
        app.settings.setRadius(km)
        val result = app.stations.lastResult.value
        // A smaller radius is filtered locally; a larger one needs new data.
        if (result == null || km > result.radiusKm) refresh()
    }

    fun toggleFavorite(station: Station) = viewModelScope.launch {
        if (station.id in state.value.favorites) app.stations.removeFavorite(station.id)
        else app.stations.addFavorite(station)
    }

    fun onNavigate(item: RankedStation) = viewModelScope.launch {
        app.stations.rememberTrip(item.station, item.price.fuel, item.effectivePrice)
    }

    companion object {
        private const val STALE_MS = 10 * 60 * 1000L
    }
}
