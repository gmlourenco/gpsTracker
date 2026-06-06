package com.segurancarural.gpstracker.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.segurancarural.gpstracker.ui.viewmodel.MapViewModel
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.ui.model.MapTheme
import com.segurancarural.gpstracker.ui.components.FamilyBottomSheet
import com.segurancarural.gpstracker.ui.components.FamilyMemberCard
import com.segurancarural.gpstracker.ui.components.MapThemeSwitcher
import com.segurancarural.gpstracker.ui.components.FamilyControlPanel
import com.segurancarural.gpstracker.ui.components.MapPointLimitFilter
import com.segurancarural.gpstracker.ui.components.MapEmptyState
import com.segurancarural.gpstracker.util.NavigationHelper
import com.segurancarural.gpstracker.util.LAYER_MARKER_CIRCLE
import com.segurancarural.gpstracker.util.LAYER_MARKER_LABEL
import com.segurancarural.gpstracker.util.SOURCE_ROUTE
import com.segurancarural.gpstracker.util.LAYER_ROUTE
import com.segurancarural.gpstracker.util.SOURCE_MARKER
import com.segurancarural.gpstracker.util.SOURCE_SOS
import com.segurancarural.gpstracker.util.LAYER_SOS
import com.segurancarural.gpstracker.util.SosRedHex
import com.segurancarural.gpstracker.util.getSatelliteStyleJson
import com.segurancarural.gpstracker.util.updateMapLayers
import com.segurancarural.gpstracker.util.fitCameraToRoute
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource

private val SurfaceDark = Color(0xFF1A1A2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val displayData by viewModel.mapDisplay.collectAsState()
    val findFamilyEnabled by viewModel.findFamilyEnabled.collectAsState()
    val refreshStatus by viewModel.familyRefreshStatus.collectAsState()
    val selectedPointLimit by viewModel.pointLimit.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentLoadedTheme by remember { mutableStateOf<MapTheme?>(null) }
    var mapStyleInstance by remember { mutableStateOf<Style?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var selectedDevice by remember { mutableStateOf<FamilyDeviceMarker?>(null) }

    var currentTheme by remember { mutableStateOf(MapTheme.DARK) }

    // Keep active selections synchronized with the latest fetched telemetry markers
    val currentSelectedDevice = remember(displayData.familyMarkers, selectedDevice) {
        displayData.familyMarkers.find { it.deviceId == selectedDevice?.deviceId }
    }

    val familyMarkersState = rememberUpdatedState(displayData.familyMarkers)
    val findFamilyEnabledState = rememberUpdatedState(findFamilyEnabled)

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

                    // Setup custom GestureDetector to capture double taps for opening the bottom sheet
                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                            if (findFamilyEnabledState.value && familyMarkersState.value.isNotEmpty()) {
                                showBottomSheet = true
                                return true
                            }
                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }

                    getMapAsync { map ->
                        // Disable default double tap to zoom so it doesn't conflict with our custom action
                        map.uiSettings.isDoubleTapGesturesEnabled = false
                        // Disable logo and attribution overlays at the bottom left
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        // Add marker click listener to show the interactive detail overlay card
                        map.addOnMapClickListener { latLng ->
                            val pixel = map.projection.toScreenLocation(latLng)
                            val rect = android.graphics.RectF(
                                (pixel.x - 24).toFloat(),
                                (pixel.y - 24).toFloat(),
                                (pixel.x + 24).toFloat(),
                                (pixel.y + 24).toFloat()
                            )
                            val features = map.queryRenderedFeatures(rect, LAYER_MARKER_CIRCLE, LAYER_MARKER_LABEL)
                            if (features.isNotEmpty()) {
                                val clickedId = features.first().getStringProperty("deviceId")
                                if (!clickedId.isNullOrEmpty()) {
                                    val clickedMarker = familyMarkersState.value.find { it.deviceId == clickedId }
                                    if (clickedMarker != null) {
                                        selectedDevice = clickedMarker
                                        return@addOnMapClickListener true
                                    }
                                }
                            }
                            selectedDevice = null
                            false
                        }

                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(39.8, -7.5))
                            .zoom(7.0)
                            .build()
                        mapInstance = map
                    }
                }
            },
            update = { mapView ->
                // Read state values in the update lambda body to register them as recomposition dependencies
                val theme = currentTheme
                val loaded = currentLoadedTheme
                val display = displayData
                val style = mapStyle

                val map = mapInstance
                if (map != null) {
                    if (loaded != theme) {
                        currentLoadedTheme = theme
                        mapStyleInstance = null
                        val targetStyleBuilder = when (theme) {
                            MapTheme.DARK -> Style.Builder().fromUri("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json")
                            MapTheme.LIGHT -> Style.Builder().fromUri("https://basemaps.cartocdn.com/gl/positron-gl-style/style.json")
                            MapTheme.SATELLITE -> Style.Builder().fromJson(getSatelliteStyleJson())
                        }
                        map.setStyle(targetStyleBuilder) { loadedStyle ->
                            loadedStyle.addSource(GeoJsonSource(SOURCE_ROUTE))
                            loadedStyle.addLayer(
                                LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                                    PropertyFactory.lineColor(style.routeColorHex),
                                    PropertyFactory.lineWidth(4f),
                                    PropertyFactory.lineOpacity(0.85f),
                                    PropertyFactory.lineJoin("round"),
                                    PropertyFactory.lineCap("round"),
                                )
                            )

                            loadedStyle.addSource(GeoJsonSource(SOURCE_MARKER))
                            loadedStyle.addLayer(
                                CircleLayer(LAYER_MARKER_CIRCLE, SOURCE_MARKER).withProperties(
                                    PropertyFactory.circleColor(
                                        Expression.coalesce(
                                            Expression.get("color"),
                                            Expression.literal(style.markerColorHex)
                                        )
                                    ),
                                    PropertyFactory.circleRadius(16f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2.5f),
                                )
                            )
                            loadedStyle.addLayer(
                                SymbolLayer(LAYER_MARKER_LABEL, SOURCE_MARKER).withProperties(
                                    PropertyFactory.textField("{label}"),
                                    PropertyFactory.textSize(14f),
                                    PropertyFactory.textColor("#FFFFFF"),
                                    PropertyFactory.textAllowOverlap(true),
                                    PropertyFactory.textIgnorePlacement(true),
                                )
                            )

                            loadedStyle.addSource(GeoJsonSource(SOURCE_SOS))
                            loadedStyle.addLayer(
                                CircleLayer(LAYER_SOS, SOURCE_SOS).withProperties(
                                    PropertyFactory.circleColor(SosRedHex),
                                    PropertyFactory.circleRadius(10f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2f),
                                )
                            )
                            mapStyleInstance = loadedStyle
                        }
                    }

                    if (mapStyleInstance != null && currentLoadedTheme == theme) {
                        map.getStyle { activeStyle ->
                            updateMapLayers(activeStyle, display, style)
                            fitCameraToRoute(map, display)
                        }
                    }
                }
            }
        )

        // Sleek top-left map style switcher control (expanding vertically, Google Maps style)
        MapThemeSwitcher(
            currentTheme = currentTheme,
            onThemeChange = { currentTheme = it },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
        )

        // Sleek top control panel for FindFamily toggle and Refresh button (aligned to TopCenter, matching height of theme selector)
        FamilyControlPanel(
            findFamilyEnabled = findFamilyEnabled,
            onFindFamilyChange = { viewModel.setFindFamilyEnabled(it) },
            refreshStatus = refreshStatus,
            onRefreshClick = { viewModel.refreshFamilyPositions() },
            onTextClick = {
                if (!findFamilyEnabled) {
                    viewModel.setFindFamilyEnabled(true)
                }
                showBottomSheet = true
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Bottom point limit filters row (only displayed in personal route mode)
        if (!findFamilyEnabled) {
            MapPointLimitFilter(
                selectedLimit = selectedPointLimit,
                onLimitSelected = { viewModel.pointLimit.value = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }

        // Dynamic empty state text displays (centered to avoid overlap with dropdown and family box)
        MapEmptyState(
            findFamilyEnabled = findFamilyEnabled,
            isEmptyFamily = displayData.familyMarkers.isEmpty(),
            isEmptyRoute = displayData.routePoints.isEmpty(),
            modifier = Modifier.align(Alignment.Center)
        )

        // Interactive floating detail overlay card for clicked markers
        if (currentSelectedDevice != null) {
            FamilyMemberCard(
                marker = currentSelectedDevice,
                onClick = {
                    val intent = NavigationHelper.getGoogleMapsRoutePreviewIntent(currentSelectedDevice.lat, currentSelectedDevice.lng)
                    NavigationHelper.launchIntentSafely(
                        context, 
                        intent, 
                        "https://www.google.com/maps/dir/?api=1&destination=${currentSelectedDevice.lat},${currentSelectedDevice.lng}"
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            )
        }

        // Family scrollable list bottom sheet
        FamilyBottomSheet(
            showBottomSheet = showBottomSheet,
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            familyMarkers = displayData.familyMarkers,
            onMemberClick = { marker ->
                val intent = NavigationHelper.getGoogleMapsRoutePreviewIntent(marker.lat, marker.lng)
                NavigationHelper.launchIntentSafely(
                    context, 
                    intent, 
                    "https://www.google.com/maps/dir/?api=1&destination=${marker.lat},${marker.lng}"
                )
                showBottomSheet = false
            }
        )
    }
}
