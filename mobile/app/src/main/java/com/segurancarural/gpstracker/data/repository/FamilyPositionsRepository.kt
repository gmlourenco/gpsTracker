package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.markerInitial
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class LastPositionsResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("devices") val devices: List<DeviceDto> = emptyList(),
)

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
                    lastSeenAt = device.lastSeenAt,
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
