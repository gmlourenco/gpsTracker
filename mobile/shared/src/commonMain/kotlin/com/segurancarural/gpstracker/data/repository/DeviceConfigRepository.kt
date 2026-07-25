package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.data.dto.DeviceConfigDto
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

enum class SaveConfigResult {
    SUCCESS,
    OFFLINE_QUEUED,
    ERROR
}

class DeviceConfigRepository {
    private val apiService = ApiService()

    /**
     * Saves the current device configuration to the backend database.
     */
    suspend fun saveConfigToBackend(config: DeviceConfigDto): SaveConfigResult = withContext(Dispatchers.Default) {
        val url = ApiRoutes.DEVICE_CONFIG

        val payloadArray = buildJsonArray {
            addJsonObject {
                put("configName", "serialNumber")
                put("configValue", config.serialNumber)
            }
            addJsonObject {
                put("configName", "deviceLabel")
                put("configValue", config.deviceLabel)
            }
            addJsonObject {
                put("configName", "markerColor")
                put("configValue", config.markerColor)
            }
            addJsonObject {
                put("configName", "trackingIntervalMs")
                put("configValue", config.trackingIntervalMs)
            }
            addJsonObject {
                put("configName", "trackingDistanceM")
                put("configValue", config.trackingDistanceM.toDouble())
            }
            addJsonObject {
                put("configName", "defaultMapType")
                put("configValue", config.defaultMapType)
            }
            addJsonObject {
                put("configName", "accidentSensorSensitivity")
                put("configValue", config.accidentSensorSensitivity)
            }
            addJsonObject {
                put("configName", "configUpdatedAt")
                put("configValue", config.configUpdatedAt)
            }
        }
        val payload = Json.encodeToString(payloadArray)
        val result = apiService.postRaw(url, payload)
        when (result) {
            is ApiResult.Success -> {
                OfflineRequestManager.clearPending("CONFIG")
                SaveConfigResult.SUCCESS
            }
            is ApiResult.HttpError -> {
                if (result.code >= 500) {
                    OfflineRequestManager.enqueue(
                        serviceType = "CONFIG",
                        url = url,
                        method = "POST",
                        bodyJson = payload
                    )
                    SaveConfigResult.OFFLINE_QUEUED
                } else {
                    SaveConfigResult.ERROR
                }
            }
            is ApiResult.NetworkError -> {
                OfflineRequestManager.enqueue(
                    serviceType = "CONFIG",
                    url = url,
                    method = "POST",
                    bodyJson = payload
                )
                SaveConfigResult.OFFLINE_QUEUED
            }
            is ApiResult.Unauthorized -> {
                SaveConfigResult.ERROR
            }
        }
    }
}
