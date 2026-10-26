package com.karelisio.reserve.ui.trip

import com.karelisio.reserve.diagnostics.Diagnostics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.data.model.Fuel
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.data.model.SortMode
import com.karelisio.reserve.data.model.Station
import com.karelisio.reserve.data.prefs.Settings
import com.karelisio.reserve.data.remote.AddressApi
import com.karelisio.reserve.data.remote.AddressSuggestion
import com.karelisio.reserve.data.remote.OsrmApi
import com.karelisio.reserve.data.remote.Route
import com.karelisio.reserve.domain.Geo
import com.karelisio.reserve.domain.Pricing
import com.karelisio.reserve.domain.RankedStation
import com.karelisio.reserve.domain.Vehicle
import com.karelisio.reserve.data.local.LoyaltyCardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A station of the corridor: where it is along the route and how far off the road. */
data class TripStation(val ranked: RankedStation, val alongKm: Double, val offRouteKm: Double)

data class TripUiState(
    val query: String = "",
    val suggestions: List<AddressSuggestion> = emptyList(),
    val destination: AddressSuggestion? = null,
    val origin: LatLon? = null,
    val route: Route? = null,
    val stations: List<TripStation> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val settings: Settings = Settings(),
)

private data class TripData(
    val origin: LatLon,
    val destination: AddressSuggestion,
    val route: Route,
    val stations: List<Station>,
    val offRouteKm: Map<Long, Double>,
    val alongKm: Map<Long, Double>,
)

@OptIn(FlowPreview::class)
class TripViewModel(private val app: AppContainer) : ViewModel() {

    private val addressApi = AddressApi(app.http)
    private val osrm = OsrmApi(app.http)

    private val query = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<AddressSuggestion>>(emptyList())
    private val data = MutableStateFlow<TripData?>(null)
    private val status = MutableStateFlow<Pair<Boolean, String?>>(false to null)
    private var job: Job? = null

    private val corridorStations = combine(data, app.settings.settings, app.db.loyaltyCards().observeAll()) { data, settings, cards ->
        data?.let { rankCorridor(it, settings, cards) }.orEmpty()
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<TripUiState> = combine(
        combine(query, suggestions) { q, s -> q to s },
        data,
        status,
        app.settings.settings,
        corridorStations,
    ) { (q, s), data, (loading, error), settings, stations ->
        TripUiState(
            query = q,
            suggestions = s,
            destination = data?.destination,
            origin = data?.origin,
            route = data?.route,
            stations = stations,
            loading = loading,
            error = error,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collectLatest { q ->
                suggestions.value = if (q.length < 3) emptyList()
                else runCatching { addressApi.search(q, app.settings.current().lastLocation) }.getOrDefault(emptyList())
            }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun onNavigate(item: RankedStation) = viewModelScope.launch {
        app.stations.rememberTrip(item.station, item.price.fuel, item.effectivePrice)
    }

    fun setFuel(fuel: Fuel) = viewModelScope.launch { app.settings.setFuel(fuel) }

    fun clear() {
        job?.cancel()
        query.value = ""
        suggestions.value = emptyList()
        data.value = null
        status.value = false to null
    }

    fun choose(destination: AddressSuggestion) {
        query.value = destination.label
        suggestions.value = emptyList()
        job?.cancel()
        job = viewModelScope.launch {
            status.value = true to null
            try {
                val origin = app.location.current() ?: app.settings.current().lastLocation
                    ?: throw IllegalStateException("Position indisponible : activez la localisation.")
                val route = osrm.route(origin, destination.position)
                // Circles of 2.5 km every 3 km cover the whole 2 km corridor.
                val samples = Geo.resample(route.points, 3.0).let { pts ->
                    if (pts.size <= MAX_SAMPLES) pts else Geo.resample(route.points, route.distanceKm / MAX_SAMPLES)
                }
                val nearby = app.stations.stationsNearRoute(samples, 2.5)
                data.value = withContext(Dispatchers.Default) { corridor(origin, destination, route, nearby) }
                status.value = false to null
            } catch (e: Exception) {
                Diagnostics.log("trajet", "Calcul du trajet impossible", e)
                status.value = false to (e.message?.takeIf { it.isNotBlank() && e is IllegalStateException }
                    ?: "Impossible de calculer le trajet. Vérifiez la connexion.")
            }
        }
    }

    /** Keeps the stations within the corridor and locates them along the route. */
    private fun corridor(origin: LatLon, destination: AddressSuggestion, route: Route, nearby: List<Station>): TripData {
        val line = route.points
        val offRoute = nearby.associate { it.id to Geo.distanceToPolylineKm(LatLon(it.lat, it.lon), line) }
        val stations = nearby.filter { (offRoute[it.id] ?: Double.MAX_VALUE) <= CORRIDOR_KM }
        val cumulative = cumulativeKm(line)
        val along = stations.associate { s ->
            val p = LatLon(s.lat, s.lon)
            val nearest = line.indices.minByOrNull { Geo.distanceKm(line[it], p) } ?: 0
            s.id to cumulative.getOrElse(nearest) { 0.0 }
        }
        return TripData(origin, destination, route, stations, offRoute, along)
    }

    private fun rankCorridor(data: TripData, settings: Settings, cards: List<LoyaltyCardEntity>): List<TripStation> {
        val offRoute = data.offRouteKm
        val ranked = Pricing.rank(
            stations = data.stations,
            fuel = settings.fuel,
            origin = data.origin,
            vehicle = Vehicle(settings.consumption, settings.fillLiters),
            cards = cards,
            filters = settings.filters,
            // Leaving the route and coming back to it.
            detourOverride = { 2 * (offRoute[it.id] ?: 0.0) },
        )
        return Pricing.sort(ranked, SortMode.REAL_COST).map { item ->
            TripStation(item, data.alongKm[item.station.id] ?: 0.0, offRoute[item.station.id] ?: 0.0)
        }
    }

    private fun cumulativeKm(line: List<LatLon>): List<Double> {
        val out = ArrayList<Double>(line.size)
        var total = 0.0
        line.forEachIndexed { i, p ->
            if (i > 0) total += Geo.distanceKm(line[i - 1], p)
            out += total
        }
        return out
    }

    companion object {
        const val CORRIDOR_KM = 2.0
        private const val MAX_SAMPLES = 400
    }
}
