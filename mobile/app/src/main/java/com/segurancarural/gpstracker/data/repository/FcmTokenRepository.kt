package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.ensureSerialNumber
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles FCM token lifecycle:
 *  - [uploadToken]         — sends a new/refreshed token to the backend
 *  - [refreshTokenIfNeeded] — called on app start to ensure backend always has the latest token
 */
class FcmTokenRepository(private val context: Context) {

    private val apiService = ApiService()

    /**
     * Uploads [token] to `PATCH /api/devices/fcm-token`.
     * Called from [FcmService.onNewToken] and from [refreshTokenIfNeeded] on app launch.
     */
    suspend fun uploadToken(token: String): Boolean {
        val serialNumber = context.ensureSerialNumber()

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
                    AppLog.i("FcmTokenRepository", "FCM token uploaded successfully")
                    OfflineRequestManager.clearPending(context, "FCM_TOKEN")
                    true
                } else {
                    AppLog.w("FcmTokenRepository", "FCM token upload response was not a logical success (possibly captive portal). Queueing.")
                    OfflineRequestManager.enqueue(
                        context = context,
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
                    AppLog.e("FcmTokenRepository", "Server error (HTTP ${result.code}) when uploading FCM token. Queueing.")
                    OfflineRequestManager.enqueue(
                        context = context,
                        serviceType = "FCM_TOKEN",
                        url = url,
                        method = "PATCH",
                        bodyJson = bodyJson
                    )
                } else {
                    AppLog.e("FcmTokenRepository", "FCM token upload failed: ${result.code} - ${result.message}")
                }
                false
            }
            is ApiResult.NetworkError -> {
                AppLog.w("FcmTokenRepository", "Network error when uploading FCM token: ${result.exception.message}. Queueing.")
                OfflineRequestManager.enqueue(
                    context = context,
                    serviceType = "FCM_TOKEN",
                    url = url,
                    method = "PATCH",
                    bodyJson = bodyJson
                )
                false
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("FcmTokenRepository", "Unauthorized FCM token upload attempt")
                false
            }
        }
    }

    /**
     * Fetches the current FCM token from Firebase and uploads it.
     * Safe to call on every app start — cheap if unchanged.
     */
    suspend fun refreshTokenIfNeeded() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            AppLog.d("FcmTokenRepository", "Current FCM token: ${token.take(20)}…")
            uploadToken(token)
        } catch (e: Exception) {
            AppLog.w("FcmTokenRepository", "Could not get FCM token: ${e.message}", e)
        }
    }
}
