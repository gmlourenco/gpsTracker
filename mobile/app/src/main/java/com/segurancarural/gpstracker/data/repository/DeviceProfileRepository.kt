package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.ensureDeviceId
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceProfileRepository(private val context: Context) {
    private val httpClient = ApiClient.httpClient

    suspend fun syncProfile(deviceLabel: String, markerColorArgb: Int): Boolean =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("deviceId", context.ensureDeviceId())
                put("deviceLabel", deviceLabel.trim().ifEmpty { "Dispositivo" })
                put("markerColor", argbToMapLibreHex(markerColorArgb))
            }

            try {
                val response = httpClient.patch("${BuildConfig.BACKEND_BASE_URL}/api/devices/profile") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(payload))
                }
                response.bodyAsText()
                val ok = response.status == HttpStatusCode.OK
                if (ok) {
                    AppLog.i("DeviceProfileRepository", "Profile synced to server")
                } else {
                    AppLog.e("DeviceProfileRepository", "Profile sync failed: ${response.status}")
                }
                ok
            } catch (e: Exception) {
                AppLog.w("DeviceProfileRepository", "Profile sync exception: ${e.message}", e)
                false
            }
        }
}
