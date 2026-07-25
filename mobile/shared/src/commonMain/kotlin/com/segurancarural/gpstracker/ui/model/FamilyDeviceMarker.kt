package com.segurancarural.gpstracker.ui.model

import com.segurancarural.gpstracker.data.dto.PreviousLocationDto

data class FamilyDeviceMarker(
    val deviceId: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Double = 0.0,
    val heading: Double = 0.0,
    val markerColorHex: String,
    val markerLetter: String,
    val emergencyState: Boolean,
    val batteryLevel: Int = 0,
    val batteryCharging: Boolean = false,
    val speed: Double = 0.0,
    val appVersion: String = "1.0.0",
    val lastSeenAt: String? = null,
    val previousLocations: List<PreviousLocationDto>? = null,
)
