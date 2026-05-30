package com.seguranca.rural.data.repository

import com.seguranca.rural.BuildConfig
import com.seguranca.rural.data.network.ApiClient
import com.seguranca.rural.ui.screens.FamilyDeviceMarker
import com.seguranca.rural.util.AppLog
import com.seguranca.rural.util.markerInitial
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastPositionsResponse(
    val success: Boolean = false,
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class DeviceDto(
    val id: String,
    val label: String,
    @SerialName("marker_color") val markerColor: String = "#16A34A",
    @SerialName("app_version") val appVersion: String = "1.0.0",
    @SerialName("latestLocation") val latestLocation: LocationDto? = null,
)

@Serializable
data class LocationDto(
    val lat: Double,
    val lng: Double,
    val speed: Double = 0.0,
    @SerialName("battery_level") val batteryLevel: Int = 0,
    @SerialName("battery_charging") val batteryCharging: Boolean = false,
    @SerialName("emergency_state") val emergencyState: Boolean = false,
)

class FamilyPositionsRepository {
    suspend fun fetchLastPositions(): Result<List<FamilyDeviceMarker>> = withContext(Dispatchers.IO) {
        try {
            val response: LastPositionsResponse =
                ApiClient.httpClient.get("${BuildConfig.BACKEND_BASE_URL}/api/positions/last").body()
            if (!response.success) {
                return@withContext Result.failure(IllegalStateException("API returned success=false"))
            }
            val markers = response.devices.mapNotNull { device ->
                val loc = device.latestLocation ?: return@mapNotNull null
                FamilyDeviceMarker(
                    deviceId = device.id,
                    label = device.label,
                    lat = loc.lat,
                    lng = loc.lng,
                    markerColorHex = device.markerColor.uppercase(),
                    markerLetter = markerInitial(device.label),
                    emergencyState = loc.emergencyState,
                    batteryLevel = loc.batteryLevel,
                    batteryCharging = loc.batteryCharging,
                    speed = loc.speed,
                    appVersion = device.appVersion,
                )
            }
            AppLog.i("FamilyPositionsRepository", "Loaded ${markers.size} family positions")
            Result.success(markers)
        } catch (e: Exception) {
            AppLog.w("FamilyPositionsRepository", "Fetch failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
