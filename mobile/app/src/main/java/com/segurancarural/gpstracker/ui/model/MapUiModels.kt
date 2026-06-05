package com.segurancarural.gpstracker.ui.model

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.util.DEFAULT_MARKER_COLOR_ARGB
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.markerInitial

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
    val lastSeenAt: String? = null,
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

enum class MapTheme(val label: String, val icon: String) {
    DARK("Escuro", "🌙"),
    LIGHT("Claro", "☀️"),
    SATELLITE("Satélite", "🛰️")
}
