package com.seguranca.rural.ui.model

import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.util.DEFAULT_MARKER_COLOR_ARGB
import com.seguranca.rural.util.argbToMapLibreHex
import com.seguranca.rural.util.markerInitial

/** How many recent GPS points to draw on the personal route line. */
enum class MapPointLimit(val label: String, val maxPoints: Int?) {
    LAST_10("10", 10),
    LAST_100("100", 100),
    ALL("Tudo", null),
}

enum class FamilyRefreshStatus {
    Idle,
    Loading,
    Success,
    Error,
}

data class DeviceMapStyle(
    val label: String = "Dispositivo",
    val markerColorArgb: Int = DEFAULT_MARKER_COLOR_ARGB,
) {
    val markerLetter: String get() = markerInitial(label)
    val routeColorHex: String get() = argbToMapLibreHex(markerColorArgb)
    val markerColorHex: String get() = routeColorHex
}

data class FamilyDeviceMarker(
    val deviceId: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val markerColorHex: String,
    val markerLetter: String,
    val emergencyState: Boolean,
    val batteryLevel: Int = 0,
    val batteryCharging: Boolean = false,
    val speed: Double = 0.0,
    val appVersion: String = "1.0.0",
)

/** Unified map rendering model for either personal route or family markers. */
data class MapDisplayData(
    val routePoints: List<TelemetryRecord> = emptyList(),
    val primaryMarker: MapMarkerDisplay? = null,
    val familyMarkers: List<FamilyDeviceMarker> = emptyList(),
    val isFamilyMode: Boolean = false,
)

data class MapMarkerDisplay(
    val lat: Double,
    val lng: Double,
    val letter: String,
    val colorHex: String,
    val emergencyState: Boolean,
)
