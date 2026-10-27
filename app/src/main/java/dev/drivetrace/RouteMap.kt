package dev.drivetrace

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.android.style.expressions.Expression

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** A native geographic map. All overlays use saved coordinates, never screen-space paths. */
@Composable
fun RouteMap(frames: List<Frame>, position: Int, modifier: Modifier = Modifier, routeOffsetKm: Double? = null) {
    val segments = remember(frames, routeOffsetKm) { roadSegments(frames, routeOffsetKm) }
    fun featuresFor(parts: List<RoadSegment>): FeatureCollection {
        val features = mutableListOf<Feature>()
        var band: LoadBand? = null
        var coordinates = mutableListOf<Point>()
        fun flush() {
            if(coordinates.size >= 2) features += Feature.fromGeometry(LineString.fromLngLats(coordinates)).apply { addStringProperty("band", band?.name ?: LoadBand.UNKNOWN.name) }
        }
        parts.forEach { part ->
            if(part.band != band) { flush(); coordinates = mutableListOf(); band = part.band }
            val points = part.points.map { Point.fromLngLat(it.second, it.first) }
            coordinates.addAll(if(coordinates.isEmpty()) points else points.drop(1))
        }
        flush()
        return FeatureCollection.fromFeatures(features)
    }
    val features = remember(segments) { featuresFor(segments) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val savedState = rememberSaveable { Bundle() }
    val view = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(savedState) }
    }
    val latestFrames by rememberUpdatedState(frames)
    fun fit() {
        val points = latestFrames.map { LatLng(it.latitude, it.longitude) }.distinct()
        val current = map ?: return
        if(points.isEmpty()) return
        if(points.size == 1 || points.maxOf { it.latitude } - points.minOf { it.latitude } < 0.0005 && points.maxOf { it.longitude } - points.minOf { it.longitude } < 0.0005) {
            current.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 15.5))
        } else {
            current.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 48))
        }
    }
    fun load() {
        ready = false; error = false
        map?.setStyle(Style.Builder().fromUri(MAP_STYLE)) { style ->
            // Raise contrast of the public dark style for small phone screens.
            style.getLayerAs<BackgroundLayer>("background")?.setProperties(backgroundColor("#14181D"))
            style.getLayerAs<FillLayer>("water")?.setProperties(fillColor("#202F3C"))
            style.getLayerAs<FillLayer>("building")?.setProperties(fillColor("#2B323B"), fillOutlineColor("#353E49"))
            style.getLayerAs<FillLayer>("landuse_residential")?.setProperties(fillColor("#191F26"))
            style.getLayerAs<FillLayer>("landuse_park")?.setProperties(fillColor("#1D2B27"))
            listOf("highway_minor", "highway_major_inner", "highway_motorway_inner").forEach { id -> style.getLayerAs<LineLayer>(id)?.setProperties(lineColor("#495564")) }
            style.layers.filterIsInstance<SymbolLayer>().filter { it.id != "road_oneway" && it.id != "road_oneway_opposite" }.forEach { layer ->
                layer.setProperties(textColor("#A7B3C1"), textHaloColor("#14181D"), textHaloWidth(1f), textField(Expression.coalesce(Expression.get("name:ru"), Expression.get("name"), Expression.get("name:latin"), Expression.get("ref"))))
            }
            style.addSource(GeoJsonSource("trip-route"))
            style.addSource(GeoJsonSource("trip-progress"))
            style.addSource(GeoJsonSource("trip-head"))
            style.addSource(GeoJsonSource("trip-start"))
            style.addLayer(LineLayer("route-casing", "trip-route").withProperties(lineColor("#111316"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
            LoadBand.entries.forEach { band ->
                style.addLayer(LineLayer("route-full-${band.name}", "trip-route").withFilter(Expression.eq(Expression.get("band"), Expression.literal(band.name))).withProperties(lineColor(band.hex), lineOpacity(0.4f), lineWidth(4f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("route-traveled-${band.name}", "trip-progress").withFilter(Expression.eq(Expression.get("band"), Expression.literal(band.name))).withProperties(lineColor(band.hex), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
            }
            style.addLayer(CircleLayer("route-start", "trip-start").withProperties(circleColor("#111316"), circleRadius(4f), circleStrokeColor("#E8EDF2"), circleStrokeWidth(2f)))
            style.addLayer(CircleLayer("route-head", "trip-head").withProperties(circleColor("#97B7D2"), circleRadius(6f), circleStrokeColor("#111316"), circleStrokeWidth(3f)))
            error = false; ready = true; fit()
        }
    }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> { view.onSaveInstanceState(savedState); view.onStop() }
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onLowMemory() { view.onLowMemory() }
            override fun onTrimMemory(level: Int) { if(level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) view.onLowMemory() }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }
        context.registerComponentCallbacks(memory)
        lifecycle.addObserver(observer)
        view.addOnDidFailLoadingMapListener { error = true }
        view.getMapAsync { nativeMap ->
            map = nativeMap
            nativeMap.uiSettings.apply {
                isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                isLogoEnabled = false
                // Native attribution control includes the tile providers and OpenStreetMap.
                isAttributionEnabled = true
            }
            load()
        }
        view.setOnTouchListener { v, event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        onDispose {
            lifecycle.removeObserver(observer); context.unregisterComponentCallbacks(memory)
            view.onSaveInstanceState(savedState); view.onPause(); view.onStop(); view.onDestroy(); map = null
        }
    }
    LaunchedEffect(ready, frames, features) {
        if(!ready || frames.isEmpty()) return@LaunchedEffect
        val points = frames.map { Point.fromLngLat(it.longitude, it.latitude) }
        map?.style?.getSourceAs<GeoJsonSource>("trip-route")?.setGeoJson(features)
        map?.style?.getSourceAs<GeoJsonSource>("trip-start")?.setGeoJson(points.first())
    }
    LaunchedEffect(ready, frames, position, features) {
        if(!ready || frames.isEmpty()) return@LaunchedEffect
        val index = position.coerceIn(frames.indices)
        val points = frames.take(index + 1).map { Point.fromLngLat(it.longitude, it.latitude) }
        map?.style?.getSourceAs<GeoJsonSource>("trip-progress")?.setGeoJson(featuresFor(segments.take(index)))
        map?.style?.getSourceAs<GeoJsonSource>("trip-head")?.setGeoJson(points.last())
    }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF191D22))) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if(!ready && !error) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Accent, strokeWidth = 2.dp)
        if(error) Text("Карта недоступна. Проверить соединение и повторить", Modifier.align(Alignment.TopCenter).background(Panel).clickable { load() }.padding(12.dp), color = Accent, fontSize = 11.sp)
        FilledIconButton(onClick = { fit() }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 30.dp).size(34.dp), shape = RoundedCornerShape(4.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Panel, contentColor = Ink)) {
            Icon(Icons.Outlined.CenterFocusStrong, "Показать весь маршрут", Modifier.size(19.dp))
        }
    }
}
