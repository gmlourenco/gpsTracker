package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.util.ensureSerialNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceProfileRepository(private val context: Context) {
    private val apiService = ApiService()

    suspend fun syncProfile(deviceLabel: String, markerColorArgb: Int): Boolean =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("serialNumber", context.ensureSerialNumber())
                put("deviceLabel", deviceLabel.trim().ifEmpty { "Dispositivo" })
                put("markerColor", argbToMapLibreHex(markerColorArgb))
            }

            val result = apiService.patchRaw(ApiRoutes.DEVICE_PROFILE, Json.encodeToString(payload))
            when (result) {
                is ApiResult.Success -> {
                    AppLog.i("DeviceProfileRepository", "Profile synced to server")
                    true
                }
                is ApiResult.HttpError -> {
                    AppLog.e("DeviceProfileRepository", "Profile sync failed: ${result.code}")
                    false
                }
                is ApiResult.NetworkError -> {
                    AppLog.w("DeviceProfileRepository", "Profile sync exception: ${result.exception.message}", result.exception)
                    false
                }
                is ApiResult.Unauthorized -> {
                    AppLog.e("DeviceProfileRepository", "Unauthorized profile sync attempt")
                    false
                }
            }
        }
}
