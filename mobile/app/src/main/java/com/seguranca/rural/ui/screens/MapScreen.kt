package com.seguranca.rural.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import com.seguranca.rural.data.model.TelemetryRecord
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentBlue = Color(0xFF3B82F6)
private val SosRedHex = "#DC2626"

/** Dark basemap — aligned with the web dashboard (Carto Dark Matter). */
private const val MAP_STYLE_URI = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private const val SOURCE_ROUTE = "route-source"
private const val LAYER_ROUTE = "route-layer"
private const val SOURCE_MARKER = "marker-source"
private const val LAYER_MARKER_CIRCLE = "marker-circle-layer"
private const val LAYER_MARKER_LABEL = "marker-label-layer"
private const val SOURCE_SOS = "sos-source"
private const val LAYER_SOS = "sos-layer"

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val selectedFilter by viewModel.timeFilter.collectAsState()
    val selectedPointLimit by viewModel.pointLimit.collectAsState()
    val routeHistory by viewModel.routeHistory.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceStyle()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onCreate(null)
                    getMapAsync { map ->
                        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URI)) { style ->
                            style.addSource(GeoJsonSource(SOURCE_ROUTE))
                            style.addLayer(
                                LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                                    PropertyFactory.lineColor(mapStyle.routeColorHex),
                                    PropertyFactory.lineWidth(4f),
                                    PropertyFactory.lineOpacity(0.85f),
                                    PropertyFactory.lineJoin("round"),
                                    PropertyFactory.lineCap("round"),
                                )
                            )

                            style.addSource(GeoJsonSource(SOURCE_MARKER))
                            style.addLayer(
                                CircleLayer(LAYER_MARKER_CIRCLE, SOURCE_MARKER).withProperties(
                                    PropertyFactory.circleColor(mapStyle.markerColorHex),
                                    PropertyFactory.circleRadius(16f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2.5f),
                                )
                            )
                            style.addLayer(
                                SymbolLayer(LAYER_MARKER_LABEL, SOURCE_MARKER).withProperties(
                                    PropertyFactory.textField("{label}"), // from GeoJSON properties
                                    PropertyFactory.textSize(14f),
                                    PropertyFactory.textColor("#FFFFFF"),
                                    PropertyFactory.textAllowOverlap(true),
                                    PropertyFactory.textIgnorePlacement(true),
                                )
                            )

                            style.addSource(GeoJsonSource(SOURCE_SOS))
                            style.addLayer(
                                CircleLayer(LAYER_SOS, SOURCE_SOS).withProperties(
                                    PropertyFactory.circleColor(SosRedHex),
                                    PropertyFactory.circleRadius(10f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2f),
                                )
                            )
                        }
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
                        updateMapLayers(style, routeHistory, mapStyle)
                        fitCameraToRoute(map, routeHistory)
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapChipRow {
                MapPointLimit.entries.forEach { limit ->
                    FilterChip(
                        selected = selectedPointLimit == limit,
                        onClick = { viewModel.pointLimit.value = limit },
                        label = {
                            Text(
                                text = limit.label,
                                fontSize = 12.sp,
                                color = if (selectedPointLimit == limit) Color.White else TextSecondary
                            )
                        },
                        modifier = Modifier.padding(horizontal = 3.dp),
                        colors = chipColors(),
                        border = null,
                    )
                }
            }
            MapChipRow {
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
                        colors = chipColors(),
                        border = null,
                    )
                }
            }
        }

        if (routeHistory.isEmpty()) {
            Text(
                text = "Sem histórico local — inicia o rastreio para ver a rota",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDark.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MapChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CardDark.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AccentBlue,
    containerColor = Color.Transparent,
)

private fun updateMapLayers(
    style: Style,
    routeHistory: List<TelemetryRecord>,
    mapStyle: DeviceMapStyle,
) {
    val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val markerSource = style.getSourceAs<GeoJsonSource>(SOURCE_MARKER) ?: return
    val sosSource = style.getSourceAs<GeoJsonSource>(SOURCE_SOS) ?: return

    style.getLayerAs<LineLayer>(LAYER_ROUTE)?.setProperties(
        PropertyFactory.lineColor(mapStyle.routeColorHex)
    )
    style.getLayerAs<CircleLayer>(LAYER_MARKER_CIRCLE)?.setProperties(
        PropertyFactory.circleColor(mapStyle.markerColorHex)
    )

    if (routeHistory.isEmpty()) {
        routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        markerSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        sosSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }

    val points = routeHistory.map { Point.fromLngLat(it.lng, it.lat) }
    val routeFeature = Feature.fromGeometry(LineString.fromLngLats(points))
    routeSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(routeFeature)))

    val latest = routeHistory.last()
    val markerProps = JsonObject().apply {
        addProperty("label", mapStyle.markerLetter)
    }
    val markerFeature = Feature.fromGeometry(
        Point.fromLngLat(latest.lng, latest.lat),
        markerProps
    )
    markerSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(markerFeature)))

    if (latest.emergencyState) {
        val sosFeature = Feature.fromGeometry(Point.fromLngLat(latest.lng, latest.lat))
        sosSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(sosFeature)))
    } else {
        sosSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
}

private fun fitCameraToRoute(
    map: org.maplibre.android.maps.MapLibreMap,
    routeHistory: List<TelemetryRecord>,
) {
    if (routeHistory.isEmpty()) return

    if (routeHistory.size == 1) {
        val only = routeHistory.first()
        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(only.lat, only.lng))
                    .zoom(15.0)
                    .build()
            ),
            800
        )
        return
    }

    val boundsBuilder = LatLngBounds.Builder()
    routeHistory.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
        1000
    )
}
