package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.dto.LastPositionsResponseDto
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.markerInitial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FamilyPositionsRepository {
    private val apiService = ApiService()
    suspend fun fetchLastPositions(): Result<List<FamilyDeviceMarker>> = withContext(Dispatchers.IO) {
        val result = apiService.get<LastPositionsResponseDto>(ApiRoutes.POSITIONS_LAST)
        when (result) {
            is ApiResult.Success -> {
                val response = result.data
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
            }
            is ApiResult.HttpError -> {
                AppLog.e("FamilyPositionsRepository", "Fetch failed: ${result.code} - ${result.message}")
                Result.failure(Exception("HTTP Error: ${result.code}"))
            }
            is ApiResult.NetworkError -> {
                AppLog.w("FamilyPositionsRepository", "Fetch exception: ${result.exception.message}", result.exception)
                Result.failure(result.exception)
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("FamilyPositionsRepository", "Unauthorized fetch attempt")
                Result.failure(Exception("Unauthorized"))
            }
        }
    }
}
