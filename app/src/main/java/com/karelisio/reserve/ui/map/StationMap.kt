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
import com.karelisio.reserve.domain.Lang
import com.karelisio.reserve.domain.t
import com.karelisio.reserve.ui.theme.isDarkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/**
 * A station marker: [label] is drawn in a pill for the cheapest stations, others are dots.
 * [price] feeds the "from x" label of clusters; null (no fuel, out of stock) never counts.
 */
data class MapMarker(
    val id: Long,
    val position: LatLon,
    val label: String,
    val color: Int,
    val textColor: Int,
    val rank: Int,
    val showLabel: Boolean,
    val price: Double? = null,
)

private const val SRC_STATIONS = "stations"
private const val SRC_USER = "user"
private const val SRC_ROUTE = "route"
private const val SRC_SELECTED = "selected"
private const val LAYER_DOTS = "station-dots"
private const val LAYER_LABELS = "station-labels"
private const val LAYER_SELECTED = "station-selected"
private const val SELECTED_ICON = "selected-icon"
private const val LAYER_USER_PULSE = "user-pulse"
private const val LAYER_CLUSTERS = "station-clusters"
private const val LAYER_CLUSTER_LABELS = "station-cluster-labels"
private const val CLUSTER_ICON = "cluster-"
private const val CLUSTER_RADIUS = 56
private const val CLUSTER_MAX_ZOOM = 11

/** Price in thousandths, an integer, so the cluster minimum maps to a stable image name. */
private const val PROP_PRICE = "pm"
private const val PROP_MIN_PRICE = "min_pm"

/** Stands for "no price" in cluster minimums (way above any real price). */
private const val NO_PRICE = 1_000_000
private const val USER_COLOR = "#1A73E8"
private const val USER_RADIUS = 9f
private const val PULSE_MS = 2_000L

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
    /** Groups nearby markers when zoomed out, labelled with their lowest price (ignored along a route). */
    clusterMarkers: Boolean = false,
    /** Centers and zooms on this point when it changes (a station opened from elsewhere). */
    focus: LatLon? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = isDarkColors
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.toArgb()
    val primary = colors.primary.toArgb()
    val onPrimary = colors.onPrimary.toArgb()
    val outline = colors.outline.toArgb()
    val clusterFill = colors.primaryContainer.toArgb()
    val onClusterFill = colors.onPrimaryContainer.toArgb()
    val clustered = clusterMarkers && route.isEmpty()

    val mapView = remember {
        MapStyle.ensureInitialized(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val markerImages = remember { mutableSetOf<String>() }
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentClusterColors by rememberUpdatedState(Triple(clusterFill, onClusterFill, surface))

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
                val slop = with(density) { 14.dp.toPx() }
                val rect = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
                fun stationAt(vararg layers: String) =
                    m.queryRenderedFeatures(rect, *layers).firstOrNull()?.getStringProperty("id")?.toLongOrNull()
                // The selected pill is drawn on top, then clusters, then single stations.
                val selectedHit = stationAt(LAYER_SELECTED)
                val cluster = if (selectedHit == null && m.style?.getLayer(LAYER_CLUSTERS) != null) {
                    m.queryRenderedFeatures(rect, LAYER_CLUSTER_LABELS, LAYER_CLUSTERS).firstOrNull()
                } else null
                val hit = selectedHit ?: if (cluster == null) stationAt(LAYER_LABELS, LAYER_DOTS) else null
                when {
                    hit != null -> currentOnMarkerClick(hit)
                    cluster != null -> zoomIntoCluster(m, cluster, latLng)
                    else -> currentOnMapClick()
                }
                true
            }
            // Cluster pills are drawn on demand: the image name carries the lowest price.
            mapView.addOnStyleImageMissingListener { id ->
                val milli = if (id.startsWith(CLUSTER_ICON)) id.removePrefix(CLUSTER_ICON).toDoubleOrNull()?.toInt() else null // "1689" or "1689.0"
                val s = style
                if (milli != null && s != null) {
                    val (fill, text, outline) = currentClusterColors
                    val label = t("dès ", "from ") + String.format(Lang.locale, "%.3f", milli / 1000.0)
                    s.addImage(id, MapStyle.priceMarker(context, label, fill, text, outline, pointer = false))
                }
            }
            map = m
        }
    }

    // (Re)build the style when the theme changes.
    LaunchedEffect(map, dark, surface, primary, outline, clusterFill, onClusterFill, clustered) {
        val m = map ?: return@LaunchedEffect
        style = null
        markerImages.clear()
        m.setStyle(Style.Builder().fromJson(MapStyle.json(dark, surface))) { loaded ->
            loaded.addSource(GeoJsonSource(SRC_ROUTE))
            loaded.addSource(
                if (clustered) {
                    GeoJsonSource(
                        SRC_STATIONS,
                        GeoJsonOptions()
                            .withCluster(true)
                            .withClusterRadius(CLUSTER_RADIUS)
                            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
                            .withClusterProperty(
                                PROP_MIN_PRICE,
                                Expression.min(Expression.accumulated(), Expression.get(PROP_MIN_PRICE)),
                                Expression.coalesce(Expression.get(PROP_PRICE), Expression.literal(NO_PRICE)),
                            ),
                    )
                } else {
                    GeoJsonSource(SRC_STATIONS)
                },
            )
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
            // "You are here" in the usual blue, never in a station color, with a pulse.
            loaded.addLayer(
                CircleLayer(LAYER_USER_PULSE, SRC_USER).withProperties(
                    PropertyFactory.circleRadius(USER_RADIUS),
                    PropertyFactory.circleColor(USER_COLOR),
                    PropertyFactory.circleOpacity(0f),
                ),
            )
            loaded.addLayer(
                CircleLayer("user-halo", SRC_USER).withProperties(
                    PropertyFactory.circleRadius(20f),
                    PropertyFactory.circleColor(USER_COLOR),
                    PropertyFactory.circleOpacity(0.16f),
                    PropertyFactory.circleStrokeColor(USER_COLOR),
                    PropertyFactory.circleStrokeOpacity(0.35f),
                    PropertyFactory.circleStrokeWidth(1f),
                ),
            )
            loaded.addLayer(
                CircleLayer(LAYER_DOTS, SRC_STATIONS)
                    .withFilter(Expression.not(Expression.has("point_count")))
                    .withProperties(
                        PropertyFactory.circleRadius(6.5f),
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.circleStrokeColor(surface),
                        PropertyFactory.circleStrokeWidth(2f),
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
            if (clustered) addClusterLayers(loaded, primary, outline)
            // Above the price pills, so a station never hides the user.
            loaded.addLayer(
                CircleLayer("user-dot", SRC_USER).withProperties(
                    PropertyFactory.circleRadius(USER_RADIUS),
                    PropertyFactory.circleColor(USER_COLOR),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3.5f),
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
                    m.price?.let { put(PROP_PRICE, (it * 1000).roundToInt()) }
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

    // Soft pulse around the user (about 20 frames a second is enough for it).
    LaunchedEffect(style, userLocation != null) {
        val pulse = style?.getLayerAs<CircleLayer>(LAYER_USER_PULSE) ?: return@LaunchedEffect
        if (userLocation == null) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            val t = ((System.nanoTime() - start) / 1_000_000L % PULSE_MS).toFloat() / PULSE_MS
            pulse.setProperties(
                PropertyFactory.circleRadius(USER_RADIUS + 26f * t),
                PropertyFactory.circleOpacity(0.4f * (1f - t)),
            )
            delay(50)
        }
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

    LaunchedEffect(style, focus) {
        val m = map ?: return@LaunchedEffect
        if (style == null || focus == null) return@LaunchedEffect
        m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lon), 14.0), 600)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Grouped stations: a halo sized by their number, and a "from x" pill with the lowest price. */
private fun addClusterLayers(style: Style, primary: Int, outline: Int) {
    val minPrice = Expression.toNumber(Expression.get(PROP_MIN_PRICE))
    val hasPrice = Expression.lt(minPrice, Expression.literal(NO_PRICE))
    val tint = Expression.switchCase(hasPrice, Expression.color(primary), Expression.color(outline))
    style.addLayer(
        CircleLayer(LAYER_CLUSTERS, SRC_STATIONS)
            .withFilter(Expression.has("point_count"))
            .withProperties(
                PropertyFactory.circleRadius(
                    Expression.step(
                        Expression.get("point_count"), Expression.literal(18f),
                        Expression.stop(10, 22f), Expression.stop(50, 27f), Expression.stop(200, 32f),
                    ),
                ),
                PropertyFactory.circleColor(tint),
                PropertyFactory.circleOpacity(0.22f),
                PropertyFactory.circleStrokeColor(tint),
                PropertyFactory.circleStrokeOpacity(0.55f),
                PropertyFactory.circleStrokeWidth(1.5f),
            ),
    )
    style.addLayer(
        SymbolLayer(LAYER_CLUSTER_LABELS, SRC_STATIONS)
            .withFilter(Expression.all(Expression.has("point_count"), hasPrice))
            .withProperties(
                // No glyphs in the raster style: the label is a bitmap named after the price.
                PropertyFactory.iconImage(
                    Expression.concat(Expression.literal(CLUSTER_ICON), Expression.toString(Expression.get(PROP_MIN_PRICE))),
                ),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.symbolSortKey(minPrice),
            ),
    )
}

/** Zooms in far enough for the cluster to split, centered on it. */
private fun zoomIntoCluster(map: MapLibreMap, cluster: Feature, tapped: LatLng) {
    val current = map.cameraPosition.zoom
    val expansion = runCatching {
        map.style?.getSourceAs<GeoJsonSource>(SRC_STATIONS)?.getClusterExpansionZoom(cluster)?.toDouble()
    }.getOrNull()
    val zoom = maxOf(expansion ?: (current + 2), current + 1)
    val center = (cluster.geometry() as? Point)?.let { LatLng(it.latitude(), it.longitude()) } ?: tapped
    map.easeCamera(CameraUpdateFactory.newLatLngZoom(center, zoom), 450)
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
