package com.segurancarural.gpstracker.data.repository

import android.content.Context
import android.content.Intent
import com.segurancarural.gpstracker.data.dto.DeviceConfigDto
import com.segurancarural.gpstracker.data.dto.DeviceConfigResponseDto
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.service.LocationForegroundService
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.ensureSerialNumber
import com.segurancarural.gpstracker.util.saveConfigToPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

enum class SaveConfigResult {
    SUCCESS,
    OFFLINE_QUEUED,
    ERROR
}

enum class SaveConfigResult {
    SUCCESS,
    OFFLINE_QUEUED,
    ERROR
}

class DeviceConfigRepository(private val context: Context) {
    private val apiService = ApiService()

    /**
     * Fetches the saved device configurations from the backend.
     * If found, saves them locally to SharedPreferences and prompts LocationForegroundService to reload.
     */
    suspend fun loadConfigFromBackend(): Boolean = withContext(Dispatchers.IO) {
        val serialNumber = context.ensureSerialNumber()
        val url = "${ApiRoutes.DEVICE_CONFIG}?serialNumber=$serialNumber"
        AppLog.i("DeviceConfigRepository", "Fetching config for serial: $serialNumber")

        val result: ApiResult<DeviceConfigResponseDto> = apiService.get(url)
        when (result) {
            is ApiResult.Success -> {
                val response = result.data
                if (response.success && response.config != null) {
                    AppLog.i("DeviceConfigRepository", "Config loaded from backend successfully")
                    context.saveConfigToPrefs(response.config)
                    // Notify running service of config reload
                    try {
                        context.startService(
                            Intent(context, LocationForegroundService::class.java).apply {
                                action = LocationForegroundService.ACTION_RELOAD_CONFIG
                            }
                        )
                    } catch (e: Exception) {
                        AppLog.w("DeviceConfigRepository", "Could not notify service to reload config: ${e.message}")
                    }
                    true
                } else {
                    AppLog.i("DeviceConfigRepository", "No config returned for serial (device likely new or never customized)")
                    false
                }
            }
            is ApiResult.HttpError -> {
                AppLog.e("DeviceConfigRepository", "Failed to load config: HTTP ${result.code}")
                false
            }
            is ApiResult.NetworkError -> {
                AppLog.w("DeviceConfigRepository", "Network error when loading config: ${result.exception.message}")
                false
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("DeviceConfigRepository", "Unauthorized when loading config")
                false
            }
        }
    }

    /**
     * Saves the current device configuration to the backend database.
     */
    suspend fun saveConfigToBackend(config: DeviceConfigDto): SaveConfigResult = withContext(Dispatchers.IO) {
        val url = ApiRoutes.DEVICE_CONFIG
        AppLog.i("DeviceConfigRepository", "Saving config to backend: $config")

        val payload = Json.encodeToString(config)
        val result = apiService.postRaw(url, payload)
        when (result) {
            is ApiResult.Success -> {
                AppLog.i("DeviceConfigRepository", "Config successfully saved to backend")
                OfflineRequestManager.clearPending(context, "CONFIG")
                SaveConfigResult.SUCCESS
            }
            is ApiResult.HttpError -> {
                if (result.code >= 500) {
                    AppLog.e("DeviceConfigRepository", "Server error (HTTP ${result.code}) when saving config. Queueing.")
                    OfflineRequestManager.enqueue(
                        context = context,
                        serviceType = "CONFIG",
                        url = url,
                        method = "POST",
                        bodyJson = payload
                    )
                    SaveConfigResult.OFFLINE_QUEUED
                } else {
                    AppLog.e("DeviceConfigRepository", "Failed to save config: HTTP ${result.code} - ${result.message}")
                    SaveConfigResult.ERROR
                }
            }
            is ApiResult.NetworkError -> {
                AppLog.w("DeviceConfigRepository", "Network error when saving config: ${result.exception.message}")
                OfflineRequestManager.enqueue(
                    context = context,
                    serviceType = "CONFIG",
                    url = url,
                    method = "POST",
                    bodyJson = payload
                )
                SaveConfigResult.OFFLINE_QUEUED
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("DeviceConfigRepository", "Unauthorized when saving config")
                SaveConfigResult.ERROR
            }
        }
    }
}
