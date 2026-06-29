package com.segurancarural.gpstracker.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiSuccessDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("message") val message: String = "",
)

@Serializable
data class ApiErrorDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("error") val error: String = "",
    @SerialName("details") val details: String? = null,
)
