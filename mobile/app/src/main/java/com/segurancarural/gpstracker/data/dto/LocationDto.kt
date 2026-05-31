package com.segurancarural.gpstracker.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class LocationDto(
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
    @SerialName("speed") val speed: Double = 0.0,
    @SerialName("battery_level") val batteryLevel: Int = 0,
    @SerialName("battery_charging") val batteryCharging: Boolean = false,
    @SerialName("emergency_state") val emergencyState: Boolean = false,
)
