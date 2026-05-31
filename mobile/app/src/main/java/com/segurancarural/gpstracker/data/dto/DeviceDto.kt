package com.segurancarural.gpstracker.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DeviceDto(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("marker_color") val markerColor: String = "#16A34A",
    @SerialName("app_version") val appVersion: String = "1.0.0",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("latestLocation") val latestLocation: LocationDto? = null,
)
