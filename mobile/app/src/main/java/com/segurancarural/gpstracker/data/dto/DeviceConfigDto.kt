package com.segurancarural.gpstracker.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DeviceConfigDto(
    @SerialName("serialNumber") val serialNumber: String,
    @SerialName("deviceLabel") val deviceLabel: String,
    @SerialName("markerColor") val markerColor: String,
    @SerialName("emergencyContact") val emergencyContact: String? = null,
    @SerialName("syncOnMobileData") val syncOnMobileData: Boolean,
    @SerialName("trackingIntervalMs") val trackingIntervalMs: Long,
    @SerialName("trackingDistanceM") val trackingDistanceM: Float
)

@Keep
@Serializable
data class DeviceConfigResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("config") val config: DeviceConfigDto? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("details") val details: String? = null
)
