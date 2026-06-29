package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.data.dto.LastPositionsResponseDto
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.util.KmpLogger
import com.segurancarural.gpstracker.util.markerInitial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FamilyPositionsRepository {
    private val apiService = ApiService()
    suspend fun fetchLastPositions(historyCount: Int = 10): Result<List<FamilyDeviceMarker>> = withContext(Dispatchers.Default) {
        val url = ApiRoutes.positionsLast(historyCount)
        val result = apiService.get<LastPositionsResponseDto>(url)
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
                        accuracy = loc.accuracy,
                        heading = loc.heading,
                        markerColorHex = device.markerColor.uppercase(),
                        markerLetter = markerInitial(device.label),
                        emergencyState = loc.emergencyState,
                        batteryLevel = loc.batteryLevel,
                        batteryCharging = loc.batteryCharging,
                        speed = loc.speed,
                        appVersion = device.appVersion,
                        lastSeenAt = device.lastSeenAt,
                        previousLocations = device.previousLocations
                    )
                }
                KmpLogger.i("FamilyPositionsRepository", "Loaded ${markers.size} family positions")
                Result.success(markers)
            }
            is ApiResult.HttpError -> {
                KmpLogger.e("FamilyPositionsRepository", "Fetch failed: ${result.code} - ${result.message}", null)
                Result.failure(Exception("HTTP Error: ${result.code}"))
            }
            is ApiResult.NetworkError -> {
                KmpLogger.w("FamilyPositionsRepository", "Fetch exception: ${result.exception.message}")
                Result.failure(result.exception)
            }
            is ApiResult.Unauthorized -> {
                KmpLogger.e("FamilyPositionsRepository", "Unauthorized fetch attempt", null)
                Result.failure(Exception("Unauthorized"))
            }
        }
    }
}
