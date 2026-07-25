package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class PushTokenRepository {
    private val apiService = ApiService()

    /**
     * Uploads [token] to `PATCH /api/devices/fcm-token`.
     * Called from platform-specific FCM/APNs services.
     */
    suspend fun uploadToken(token: String, serialNumber: String): Boolean {
        val payload = buildJsonObject {
            put("serialNumber", serialNumber)
            put("fcmToken", token)
        }

        val url = ApiRoutes.FCM_TOKEN
        val bodyJson = Json.encodeToString(payload)
        val result = apiService.patchRaw(url, bodyJson)
        return when (result) {
            is ApiResult.Success -> {
                val body = result.data
                val isLogicalSuccess = try {
                    val json = Json.parseToJsonElement(body)
                    json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
                } catch (e: Exception) {
                    false
                }
                if (isLogicalSuccess) {
                    OfflineRequestManager.clearPending("FCM_TOKEN")
                    true
                } else {
                    OfflineRequestManager.enqueue(
                        serviceType = "FCM_TOKEN",
                        url = url,
                        method = "PATCH",
                        bodyJson = bodyJson
                    )
                    false
                }
            }
            is ApiResult.HttpError -> {
                if (result.code >= 500) {
                    OfflineRequestManager.enqueue(
                        serviceType = "FCM_TOKEN",
                        url = url,
                        method = "PATCH",
                        bodyJson = bodyJson
                    )
                }
                false
            }
            is ApiResult.NetworkError -> {
                OfflineRequestManager.enqueue(
                    serviceType = "FCM_TOKEN",
                    url = url,
                    method = "PATCH",
                    bodyJson = bodyJson
                )
                false
            }
            is ApiResult.Unauthorized -> {
                false
            }
        }
    }
}
