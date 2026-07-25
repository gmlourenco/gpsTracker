package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.ensureSerialNumber
import kotlinx.coroutines.tasks.await

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
        return PushTokenRepository().uploadToken(token, serialNumber)
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
