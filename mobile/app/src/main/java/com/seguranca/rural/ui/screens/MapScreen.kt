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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.style.expressions.Expression
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
    val displayData by viewModel.mapDisplay.collectAsState()
    val findFamilyEnabled by viewModel.findFamilyEnabled.collectAsState()
    val refreshStatus by viewModel.familyRefreshStatus.collectAsState()
    val selectedPointLimit by viewModel.pointLimit.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()

    var isStyleLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceStyle()
    }

    // Refresh icon spinning animation
    val rotationTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotationAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic color depending on refresh status
    val refreshButtonColor by animateColorAsState(
        targetValue = when (refreshStatus) {
            FamilyRefreshStatus.Loading -> AccentBlue
            FamilyRefreshStatus.Success -> Color(0xFF16A34A) // Green (for 1s)
            FamilyRefreshStatus.Error -> Color(0xFFDC2626)   // Red (for 5s)
            FamilyRefreshStatus.Idle -> CardDark.copy(alpha = 0.9f)
        },
        animationSpec = tween(300),
        label = "refresh_color"
    )

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
                                    PropertyFactory.circleColor(
                                        Expression.coalesce(
                                            Expression.get("color"),
                                            Expression.literal(mapStyle.markerColorHex)
                                        )
                                    ),
                                    PropertyFactory.circleRadius(16f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2.5f),
                                )
                            )
                            style.addLayer(
                                SymbolLayer(LAYER_MARKER_LABEL, SOURCE_MARKER).withProperties(
                                    PropertyFactory.textField("{label}"),
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
                            isStyleLoaded = true
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(39.8, -7.5))
                            .zoom(7.0)
                            .build()
                    }
                }
            },
            update = { mapView ->
                if (isStyleLoaded) {
                    mapView.getMapAsync { map ->
                        map.getStyle { style ->
                            updateMapLayers(style, displayData, mapStyle)
                            fitCameraToRoute(map, displayData)
                        }
                    }
                }
            }
        )

        // Sleek top control panel for FindFamily toggle and Refresh button
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔎 Localizar Família",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = findFamilyEnabled,
                        onCheckedChange = { viewModel.setFindFamilyEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceDark.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }

                if (findFamilyEnabled) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(refreshButtonColor)
                            .clickable(enabled = refreshStatus != FamilyRefreshStatus.Loading) {
                                viewModel.refreshFamilyPositions()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (refreshStatus == FamilyRefreshStatus.Loading) rotationAngle else 0f)
                        )
                    }
                }
            }
        }

        // Bottom point limit filters row (only displayed in personal route mode)
        if (!findFamilyEnabled) {
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
            }
        }

        // Dynamic empty state text displays
        if (findFamilyEnabled) {
            if (displayData.familyMarkers.isEmpty()) {
                Text(
                    text = "A carregar localizações da família...",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDark.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else {
            if (displayData.routePoints.isEmpty()) {
                Text(
                    text = "Sem histórico local — inicia o rastreio para ver a rota",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDark.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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
    displayData: MapDisplayData,
    localStyle: DeviceMapStyle,
) {
    val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val markerSource = style.getSourceAs<GeoJsonSource>(SOURCE_MARKER) ?: return
    val sosSource = style.getSourceAs<GeoJsonSource>(SOURCE_SOS) ?: return

    // 1. Route Line Layer (personal route mode only)
    if (displayData.isFamilyMode || displayData.routePoints.isEmpty()) {
        routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    } else {
        style.getLayerAs<LineLayer>(LAYER_ROUTE)?.setProperties(
            PropertyFactory.lineColor(localStyle.routeColorHex)
        )
        val points = displayData.routePoints.map { Point.fromLngLat(it.lng, it.lat) }
        val routeFeature = Feature.fromGeometry(LineString.fromLngLats(points))
        routeSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(routeFeature)))
    }

    // 2. Markers & Emergency SOS States
    val markerFeatures = mutableListOf<Feature>()
    val sosFeatures = mutableListOf<Feature>()

    if (displayData.isFamilyMode) {
        displayData.familyMarkers.forEach { marker ->
            val props = JsonObject().apply {
                addProperty("label", marker.markerLetter)
                addProperty("color", marker.markerColorHex)
            }
            markerFeatures.add(
                Feature.fromGeometry(
                    Point.fromLngLat(marker.lng, marker.lat),
                    props
                )
            )

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }
        }
    } else {
        displayData.primaryMarker?.let { marker ->
            val props = JsonObject().apply {
                addProperty("label", marker.letter)
                addProperty("color", marker.colorHex)
            }
            markerFeatures.add(
                Feature.fromGeometry(
                    Point.fromLngLat(marker.lng, marker.lat),
                    props
                )
            )

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }
        }
    }

    markerSource.setGeoJson(FeatureCollection.fromFeatures(markerFeatures.toTypedArray()))
    sosSource.setGeoJson(FeatureCollection.fromFeatures(sosFeatures.toTypedArray()))
}

private fun fitCameraToRoute(
    map: org.maplibre.android.maps.MapLibreMap,
    displayData: MapDisplayData,
) {
    if (displayData.isFamilyMode) {
        val markers = displayData.familyMarkers
        if (markers.isEmpty()) return
        if (markers.size == 1) {
            val only = markers.first()
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(only.lat, only.lng))
                        .zoom(14.0)
                        .build()
                ),
                800
            )
            return
        }
        val boundsBuilder = LatLngBounds.Builder()
        markers.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
            1000
        )
    } else {
        val routeHistory = displayData.routePoints
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
}
