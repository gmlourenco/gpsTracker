package com.segurancarural.gpstracker.data.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class AppVersionResponseDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("latestVersion") val latestVersion: String = "",
    @SerialName("minVersion") val minVersion: String = "",
    @SerialName("downloadUrl") val downloadUrl: String = "",
    @SerialName("releaseNotes") val releaseNotes: String = "",
    @SerialName("updateAvailable") val updateAvailable: Boolean = false,
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
)
