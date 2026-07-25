package com.segurancarural.gpstracker.data.repository

import android.content.Context
import android.content.Intent
import com.segurancarural.gpstracker.data.dto.DeviceConfigResponseDto
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.service.LocationForegroundService
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.ensureSerialNumber
import com.segurancarural.gpstracker.util.saveConfigToPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


sealed class ConfigLoadResult {
    object Success : ConfigLoadResult()
    data class PromptImport(val availableDevices: List<com.segurancarural.gpstracker.data.dto.AvailableDeviceDto>) : ConfigLoadResult()
    object Error : ConfigLoadResult()
}

class AndroidDeviceConfigLoader(private val context: Context) {
    private val apiService = ApiService()

    /**
     * Fetches the saved device configurations from the backend.
     * If found, saves them locally to SharedPreferences and prompts LocationForegroundService to reload.
     */
    suspend fun loadConfigFromBackend(overrideSerial: String? = null): ConfigLoadResult = withContext(Dispatchers.IO) {
        val serialNumber = overrideSerial ?: context.ensureSerialNumber()
        val url = "${ApiRoutes.DEVICE_CONFIG}?serialNumber=$serialNumber"
        AppLog.i("AndroidDeviceConfigLoader", "Fetching config for serial: $serialNumber")

        val result: ApiResult<DeviceConfigResponseDto> = apiService.get(url)
        when (result) {
            is ApiResult.Success -> {
                val response = result.data
                if (response.success) {
                    if (response.promptImport == true && !response.availableDevices.isNullOrEmpty()) {
                        AppLog.i("AndroidDeviceConfigLoader", "Backend requested import prompt")
                        return@withContext ConfigLoadResult.PromptImport(response.availableDevices!!)
                    } else if (response.config != null) {
                        AppLog.i("AndroidDeviceConfigLoader", "Config loaded from backend successfully")
                        context.saveConfigToPrefs(response.config!!.copy(serialNumber = context.ensureSerialNumber()))
                        // Notify running service of config reload
                        try {
                            context.startService(
                                Intent(context, LocationForegroundService::class.java).apply {
                                    action = LocationForegroundService.ACTION_RELOAD_CONFIG
                                }
                            )
                        } catch (e: Exception) {
                            AppLog.w("AndroidDeviceConfigLoader", "Could not notify service to reload config: ${e.message}")
                        }
                        return@withContext ConfigLoadResult.Success
                    } else {
                        AppLog.i("AndroidDeviceConfigLoader", "No config returned for serial (device likely new or never customized)")
                        return@withContext ConfigLoadResult.Success
                    }
                } else {
                    AppLog.e("AndroidDeviceConfigLoader", "Failed to load config: ${response.error}")
                    return@withContext ConfigLoadResult.Error
                }
            }
            is ApiResult.HttpError -> {
                AppLog.e("AndroidDeviceConfigLoader", "Failed to load config: HTTP ${result.code}")
                return@withContext ConfigLoadResult.Error
            }
            is ApiResult.NetworkError -> {
                AppLog.w("AndroidDeviceConfigLoader", "Network error when loading config: ${result.exception.message}")
                return@withContext ConfigLoadResult.Error
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("AndroidDeviceConfigLoader", "Unauthorized when loading config")
                return@withContext ConfigLoadResult.Error
            }
        }
    }


}
