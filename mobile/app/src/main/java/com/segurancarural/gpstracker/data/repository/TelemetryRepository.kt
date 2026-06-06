package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.sync.toLocationV2Json
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.deviceMarkerColorArgb
import com.segurancarural.gpstracker.util.shouldUploadOverCurrentNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TelemetryRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val dao = createAppDatabase(appContext).telemetryDao()
    private val apiService = ApiService()

    suspend fun submitLocation(record: TelemetryRecord) = withContext(Dispatchers.IO) {
        if (!shouldUploadOverCurrentNetwork(appContext)) {
            AppLog.d("TelemetryRepository", "Mobile data sync disabled — queueing locally")
            dao.insert(record.copy(synced = false))
            return@withContext
        }

        val payload = listOf(record).toLocationV2Json(argbToMapLibreHex(appContext.deviceMarkerColorArgb()))
        AppLog.i("TelemetryRepository", "Preparing to send location update...")
        AppLog.d("TelemetryRepository", "Payload: $payload")

        val result = apiService.postRaw(ApiRoutes.LOCATION_V2, payload)

        when (result) {
            is ApiResult.Success -> {
                val body = result.data
                val isLogicalSuccess = try {
                    val json = Json.parseToJsonElement(body)
                    json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
                } catch (e: Exception) {
                    false
                }
                if (isLogicalSuccess) {
                    AppLog.i("TelemetryRepository", "Location sent successfully: $body")
                    dao.insert(record.copy(synced = true))
                } else {
                    AppLog.w("TelemetryRepository", "Location push response was not a logical success (possibly captive portal): $body")
                    dao.insert(record.copy(synced = false))
                }
            }
            is ApiResult.HttpError -> {
                AppLog.e("TelemetryRepository", "Network push failed: ${result.code} — ${result.message}")
                dao.insert(record.copy(synced = false))
            }
            is ApiResult.NetworkError -> {
                AppLog.w("TelemetryRepository", "Network exception: ${result.exception.message}", result.exception)
                dao.insert(record.copy(synced = false))
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("TelemetryRepository", "Unauthorized push attempt")
                dao.insert(record.copy(synced = false))
            }
        }
    }
}
