package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.ensureDeviceId
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

    private val httpClient = ApiClient.httpClient

    /**
     * Uploads [token] to `PATCH /api/devices/fcm-token`.
     * Called from [FcmService.onNewToken] and from [refreshTokenIfNeeded] on app launch.
     */
    suspend fun uploadToken(token: String): Boolean {
        val deviceId = context.ensureDeviceId()

        val payload = buildJsonObject {
            put("deviceId", deviceId)
            put("fcmToken", token)
        }

        return try {
            val response = httpClient.patch("${BuildConfig.BACKEND_BASE_URL}/api/devices/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(payload))
            }
            response.bodyAsText()
            val ok = response.status == HttpStatusCode.OK
            if (ok) {
                AppLog.i("FcmTokenRepository", "FCM token uploaded successfully")
            } else {
                AppLog.e("FcmTokenRepository", "FCM token upload failed: ${response.status}")
            }
            ok
        } catch (e: Exception) {
            AppLog.w("FcmTokenRepository", "FCM token upload exception: ${e.message}", e)
            false
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
