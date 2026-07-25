package com.segurancarural.gpstracker.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceConfigDto(
    @SerialName("serialNumber") val serialNumber: String,
    @SerialName("deviceLabel") val deviceLabel: String,
    @SerialName("markerColor") val markerColor: String,
    @SerialName("trackingIntervalMs") val trackingIntervalMs: Long,
    @SerialName("trackingDistanceM") val trackingDistanceM: Float,
    @SerialName("defaultMapType") val defaultMapType: String = "SATELLITE",
    @SerialName("accidentSensorSensitivity") val accidentSensorSensitivity: String = "medium",
    @SerialName("configUpdatedAt") val configUpdatedAt: Long = -1
)

@Serializable
data class AvailableDeviceDto(
    @SerialName("serial") val serial: String,
    @SerialName("name") val name: String
)

@Serializable
data class DeviceConfigResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("config") val config: DeviceConfigDto? = null,
    @SerialName("promptImport") val promptImport: Boolean? = null,
    @SerialName("availableDevices") val availableDevices: List<AvailableDeviceDto>? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("details") val details: String? = null
)
