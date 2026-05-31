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

        val result = apiService.patchRaw(ApiRoutes.FCM_TOKEN, Json.encodeToString(payload))
        return when (result) {
            is ApiResult.Success -> {
                AppLog.i("FcmTokenRepository", "FCM token uploaded successfully")
                true
            }
            is ApiResult.HttpError -> {
                AppLog.e("FcmTokenRepository", "FCM token upload failed: ${result.code}")
                false
            }
            is ApiResult.NetworkError -> {
                AppLog.w("FcmTokenRepository", "FCM token upload exception: ${result.exception.message}", result.exception)
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
