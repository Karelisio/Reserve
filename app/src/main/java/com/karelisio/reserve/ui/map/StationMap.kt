package com.karelisio.reserve.ui.map

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.Gravity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.reserve.data.model.LatLon
import com.karelisio.reserve.ui.theme.isDarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/** A station marker: [label] is drawn in a pill for the cheapest stations, others are dots. */
data class MapMarker(
    val id: Long,
    val position: LatLon,
    val label: String,
    val color: Int,
    val textColor: Int,
    val rank: Int,
    val showLabel: Boolean,
)

private const val SRC_STATIONS = "stations"
private const val SRC_USER = "user"
private const val SRC_ROUTE = "route"
private const val SRC_SELECTED = "selected"
private const val LAYER_DOTS = "station-dots"
private const val LAYER_LABELS = "station-labels"
private const val LAYER_SELECTED = "station-selected"
private const val SELECTED_ICON = "selected-icon"

@SuppressLint("ClickableViewAccessibility")
@Composable
fun StationMap(
    markers: List<MapMarker>,
    userLocation: LatLon?,
    selectedId: Long?,
    onMarkerClick: (Long) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    route: List<LatLon> = emptyList(),
    /** Changing this value re-frames the camera on the markers. */
    cameraKey: Any? = Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = isDarkColors
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.toArgb()
    val primary = colors.primary.toArgb()
    val onPrimary = colors.onPrimary.toArgb()

    val mapView = remember {
        MapStyle.ensureInitialized(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val markerImages = remember { mutableSetOf<String>() }
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Walk the map down its lifecycle before releasing it.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isLogoEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                attributionGravity = Gravity.BOTTOM or Gravity.START
                compassGravity = Gravity.TOP or Gravity.END
            }
            m.addOnMapClickListener { latLng ->
                val point = m.projection.toScreenLocation(latLng)
                val slop = 14 * context.resources.displayMetrics.density
                val hit = m.queryRenderedFeatures(
                    RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop),
                    LAYER_SELECTED, LAYER_LABELS, LAYER_DOTS,
                ).firstOrNull()?.getStringProperty("id")?.toLongOrNull()
                if (hit != null) currentOnMarkerClick(hit) else currentOnMapClick()
                true
            }
            map = m
        }
    }

    // (Re)build the style when the theme changes.
    LaunchedEffect(map, dark, surface, primary) {
        val m = map ?: return@LaunchedEffect
        style = null
        markerImages.clear()
        m.setStyle(Style.Builder().fromJson(MapStyle.json(dark, surface))) { loaded ->
            loaded.addSource(GeoJsonSource(SRC_ROUTE))
            loaded.addSource(GeoJsonSource(SRC_STATIONS))
            loaded.addSource(GeoJsonSource(SRC_USER))
            loaded.addSource(GeoJsonSource(SRC_SELECTED))
            loaded.addLayer(
                LineLayer("route-casing", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor(surface),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                LineLayer("route-line", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor(primary),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                CircleLayer("user-halo", SRC_USER).withProperties(
                    PropertyFactory.circleRadius(18f),
                    PropertyFactory.circleColor(primary),
                    PropertyFactory.circleOpacity(0.18f),
                ),
            )
            loaded.addLayer(
                CircleLayer(LAYER_DOTS, SRC_STATIONS).withProperties(
                    PropertyFactory.circleRadius(6.5f),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleStrokeColor(surface),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
            loaded.addLayer(
                CircleLayer("user-dot", SRC_USER).withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor(primary),
                    PropertyFactory.circleStrokeColor(surface),
                    PropertyFactory.circleStrokeWidth(3f),
                ),
            )
            loaded.addLayer(
                SymbolLayer(LAYER_LABELS, SRC_STATIONS)
                    .withFilter(Expression.has("icon"))
                    .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconOffset(arrayOf(0f, -3f)),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.symbolSortKey(Expression.get("rank")),
                    ),
            )
            loaded.addLayer(
                SymbolLayer(LAYER_SELECTED, SRC_SELECTED).withProperties(
                    PropertyFactory.iconImage(SELECTED_ICON),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconOffset(arrayOf(0f, -3f)),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            style = loaded
        }
    }

    LaunchedEffect(style, markers) {
        val s = style ?: return@LaunchedEffect
        val wanted = markers.filter { it.showLabel }.associateBy { "price-${it.id}-${it.label}-${it.color}" }
        (markerImages - wanted.keys).forEach { s.removeImage(it); markerImages.remove(it) }
        // Draw the new price pills off the main thread, then hand them to the map at once.
        val missing = wanted.filterKeys { it !in markerImages }
        val bitmaps = withContext(Dispatchers.Default) {
            missing.mapValues { (_, m) -> MapStyle.priceMarker(context, m.label, m.color, m.textColor, surface) }
        }
        if (bitmaps.isNotEmpty()) {
            s.addImages(HashMap(bitmaps))
            markerImages += bitmaps.keys
        }
        val features = buildJsonArray {
            markers.forEach { m ->
                add(point(m.position) {
                    put("id", m.id.toString())
                    put("color", String.format(java.util.Locale.ROOT, "#%06X", m.color and 0xFFFFFF))
                    put("rank", m.rank)
                    if (m.showLabel) put("icon", "price-${m.id}-${m.label}-${m.color}")
                })
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_STATIONS)?.setGeoJson(collection(features))
    }

    LaunchedEffect(style, selectedId, markers) {
        val s = style ?: return@LaunchedEffect
        val selected = markers.firstOrNull { it.id == selectedId }
        val source = s.getSourceAs<GeoJsonSource>(SRC_SELECTED) ?: return@LaunchedEffect
        if (selected == null) {
            source.setGeoJson(collection(JsonArray(emptyList())))
        } else {
            s.addImage(SELECTED_ICON, MapStyle.priceMarker(context, selected.label, primary, onPrimary, surface, large = true))
            source.setGeoJson(collection(buildJsonArray { add(point(selected.position) { put("id", selected.id.toString()) }) }))
        }
    }

    LaunchedEffect(style, userLocation) {
        val s = style ?: return@LaunchedEffect
        val features = buildJsonArray { userLocation?.let { add(point(it) {}) } }
        s.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(collection(features))
    }

    LaunchedEffect(style, route) {
        val s = style ?: return@LaunchedEffect
        val features = buildJsonArray {
            if (route.size >= 2) add(buildJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {}
                putJsonObject("geometry") {
                    put("type", "LineString")
                    putJsonArray("coordinates") {
                        route.forEach { p -> add(buildJsonArray { add(p.lon); add(p.lat) }) }
                    }
                }
            })
        }
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(collection(features))
    }

    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(style, cameraKey, markers.isEmpty(), route.size) {
        val m = map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        val points = buildList {
            userLocation?.let { add(it) }
            if (route.isNotEmpty()) addAll(route) else addAll(markers.sortedBy { it.rank }.take(40).map { it.position })
        }
        with(density) {
            val left = contentPadding.calculateLeftPadding(layoutDirection).roundToPx() + 48.dp.roundToPx()
            val right = contentPadding.calculateRightPadding(layoutDirection).roundToPx() + 48.dp.roundToPx()
            val top = contentPadding.calculateTopPadding().roundToPx() + 72.dp.roundToPx()
            val bottom = contentPadding.calculateBottomPadding().roundToPx() + 24.dp.roundToPx()
            when {
                points.size >= 2 -> {
                    val bounds = LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                    m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom), 600)
                }
                points.size == 1 -> m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lon), 12.5), 600)
                else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(46.6, 2.4), 4.8))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun point(p: LatLon, properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
    put("type", "Feature")
    putJsonObject("properties", properties)
    putJsonObject("geometry") {
        put("type", "Point")
        putJsonArray("coordinates") { add(JsonPrimitive(p.lon)); add(JsonPrimitive(p.lat)) }
    }
}

private fun collection(features: JsonArray): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", features)
}.toString()
