package com.seguranca.rural.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seguranca.rural.data.model.TelemetryRecord
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// ── Colours (shared with HomeScreen) ─────────────────────────────────────
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentBlue = Color(0xFF3B82F6)
private val SosRedHex = "#DC2626"

/** Time filter options for the map's route history view */
enum class MapTimeFilter(val label: String) {
    TODAY("Hoje"),
    LAST_24H("24h"),
    WEEKLY("Semana"),
}

private const val SOURCE_ROUTE = "route-source"
private const val LAYER_ROUTE = "route-layer"
private const val SOURCE_SOS = "sos-source"
private const val LAYER_SOS = "sos-layer"

/**
 * MapScreen — embedded MapLibre map with offline tile support.
 *
 * Features:
 *   - MapLibre GL rendered in an [AndroidView]
 *   - OpenStreetMap raster tile layer (online)
 *   - Offline .mbtiles support: place files in app storage and reference
 *     via `asset://` or `file://` scheme in the style URL
 *   - Route history rendered as a polyline via GeoJSON source
 *   - SOS marker support (Phase 2 wired)
 */
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val selectedFilter by viewModel.timeFilter.collectAsState()
    val routeHistory by viewModel.routeHistory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        // ── MapLibre Map View ──────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapLibre.getInstance(context)

                MapView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onCreate(null)
                    getMapAsync { map ->
                        map.setStyle(
                            Style.Builder().fromUri(
                                "https://demotiles.maplibre.org/style.json"
                            )
                        ) { style ->
                            // 1. Add GeoJSON Source for the route
                            style.addSource(GeoJsonSource(SOURCE_ROUTE))

                            // 2. Add LineLayer for the route polyline
                            val lineLayer = LineLayer(LAYER_ROUTE, SOURCE_ROUTE).apply {
                                setProperties(
                                    PropertyFactory.lineColor(Color.Blue.value.toInt()),
                                    PropertyFactory.lineWidth(4f),
                                    PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                                    PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND)
                                )
                            }
                            style.addLayer(lineLayer)

                            // 3. Add SOS Marker Source and Layer
                            style.addSource(GeoJsonSource(SOURCE_SOS))
                            val sosLayer = org.maplibre.android.style.layers.CircleLayer(LAYER_SOS, SOURCE_SOS).apply {
                                setProperties(
                                    PropertyFactory.circleColor(android.graphics.Color.parseColor(SosRedHex)),
                                    PropertyFactory.circleRadius(8f),
                                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                                    PropertyFactory.circleStrokeWidth(2f)
                                )
                            }
                            style.addLayer(sosLayer)
                        }

                        // Default camera position: central Portugal
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(39.8, -7.5))
                            .zoom(7.0)
                            .build()
                    }
                }
            },
            update = { mapView ->
                mapView.getMapAsync { map ->
                    map.getStyle { style ->
                        val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
                        val sosSource = style.getSourceAs<GeoJsonSource>(SOURCE_SOS)

                        if (routeSource != null && sosSource != null) {
                            if (routeHistory.isNotEmpty()) {
                                // Extract coordinates from history
                                val points = routeHistory.map { Point.fromLngLat(it.lng, it.lat) }
                                val lineString = LineString.fromLngLats(points)
                                val routeFeature = Feature.fromGeometry(lineString)

                                // Update GeoJSON route source
                                routeSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(routeFeature)))

                                // SOS Marker Logic
                                val latest = routeHistory.last()
                                if (latest.emergencyState) {
                                    val sosFeature = Feature.fromGeometry(Point.fromLngLat(latest.lng, latest.lat))
                                    sosSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(sosFeature)))
                                } else {
                                    sosSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                                }

                                // Move camera to the latest point
                                val position = CameraPosition.Builder()
                                    .target(LatLng(latest.lat, latest.lng))
                                    .zoom(14.0)
                                    .build()
                                map.easeCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
                            } else {
                                routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                                sosSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                            }
                        }
                    }
                }
            }
        )

        // ── Time filter chips (bottom overlay) ────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardDark.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                MapTimeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.timeFilter.value = filter },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 13.sp,
                                color = if (selectedFilter == filter) Color.White else TextSecondary
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            containerColor = Color.Transparent,
                        ),
                        border = null
                    )
                }
            }
        }
    }
}
