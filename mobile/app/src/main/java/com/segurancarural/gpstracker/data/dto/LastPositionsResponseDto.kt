package com.segurancarural.gpstracker.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class LastPositionsResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("devices") val devices: List<DeviceDto> = emptyList(),
)
