package com.segurancarural.gpstracker.ui.model

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.util.DEFAULT_MARKER_COLOR_ARGB
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.markerInitial

/** Time-based filter for the personal route history. */
enum class MapTimeFilter(val label: String) {
    TODAY("Hoje"),
    YESTERDAY("Ontem"),
    THIS_WEEK("Semana"),
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
    val accuracy: Float,
)

enum class MapTheme(val label: String, val icon: String) {
    DARK("Escuro", "🌙"),
    LIGHT("Claro", "☀️"),
    SATELLITE("Satélite", "🛰️")
}
