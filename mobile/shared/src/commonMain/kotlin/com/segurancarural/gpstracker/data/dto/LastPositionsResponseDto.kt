package com.segurancarural.gpstracker.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastPositionsResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("devices") val devices: List<DeviceDto> = emptyList(),
)
